package com.nichat.translation

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import net.minecraft.client.Minecraft
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.ArrayDeque
import java.util.concurrent.CompletableFuture

object Translator {
    private val logger = LoggerFactory.getLogger("nichat")
    private const val CACHE_SIZE = 512
    private const val MAX_QUEUE_SIZE = 6
    private const val MAX_WAIT_NANOS = 8_000_000_000L
    private const val MIN_BACKOFF_MS = 30_000L
    private const val MAX_BACKOFF_MS = 600_000L
    private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(10)
    private val CYRILLIC_TARGETS = setOf("ru", "uk", "be", "bg", "sr", "mk", "kk", "ky", "mn", "tg")
    private val LATIN_LETTER = Regex("""\p{IsLatin}""")

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    private data class CacheKey(val target: String, val text: String)

    private class Result(val translated: String?)

    private class Job(
        val key: CacheKey,
        val future: CompletableFuture<String?>,
        val queuedAt: Long = System.nanoTime()
    )

    private val cache = object : LinkedHashMap<CacheKey, Result>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, Result>): Boolean = size > CACHE_SIZE
    }

    private val lock = Any()
    private val queue = ArrayDeque<Job>()
    private var running = false
    private var pausedUntilMs = 0L
    private var backoffMs = MIN_BACKOFF_MS

    fun translate(text: String, targetLanguage: String): CompletableFuture<String?> {
        val target = resolveTargetLanguage(targetLanguage)
        if (target.substringBefore('-').lowercase() in CYRILLIC_TARGETS && !LATIN_LETTER.containsMatchIn(text)) {
            return CompletableFuture.completedFuture(null)
        }

        val key = CacheKey(target, text)
        val future = CompletableFuture<String?>()

        synchronized(lock) {
            synchronized(cache) {
                cache[key]?.let { return CompletableFuture.completedFuture(it.translated) }
            }
            if (System.currentTimeMillis() < pausedUntilMs) {
                return CompletableFuture.completedFuture(null)
            }

            queue.addLast(Job(key, future))
            while (queue.size > MAX_QUEUE_SIZE) {
                queue.removeFirst().future.complete(null)
            }
        }

        processNext()
        return future
    }

    fun translateNow(text: String, targetLanguage: String, timeout: Duration): CompletableFuture<String?> {
        val target = targetLanguage.trim().ifEmpty { "en" }
        val key = CacheKey(target, text)
        synchronized(cache) {
            cache[key]?.let { return CompletableFuture.completedFuture(it.translated) }
        }

        return requestTranslation(text, target, timeout).thenApply { translated ->
            synchronized(cache) { cache[key] = Result(translated) }
            translated
        }
    }

    private fun processNext() {
        val job = synchronized(lock) {
            if (running) return
            var next: Job? = null
            while (next == null && queue.isNotEmpty()) {
                val candidate = queue.removeFirst()
                if (System.nanoTime() - candidate.queuedAt > MAX_WAIT_NANOS || System.currentTimeMillis() < pausedUntilMs) {
                    candidate.future.complete(null)
                } else {
                    next = candidate
                }
            }
            if (next == null) return
            running = true
            next
        }

        requestTranslation(job.key.text, job.key.target, REQUEST_TIMEOUT).whenComplete { translated, error ->
            synchronized(lock) {
                running = false
                if (error == null) {
                    backoffMs = MIN_BACKOFF_MS
                } else {
                    pausedUntilMs = System.currentTimeMillis() + backoffMs
                    logError(error, backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
                }
            }

            if (error == null) {
                synchronized(cache) { cache[job.key] = Result(translated) }
                job.future.complete(translated)
            } else {
                job.future.complete(null)
            }
            processNext()
        }
    }

    private fun requestTranslation(text: String, target: String, timeout: Duration): CompletableFuture<String?> {
        val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&dt=t" +
            "&tl=" + encode(target) +
            "&q=" + encode(text)
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(timeout)
            .GET()
            .build()

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply { response ->
            check(response.statusCode() == 200) { "HTTP ${response.statusCode()}" }
            val root = JsonParser.parseString(response.body()).asJsonArray
            val detected = root.getOrNull(2)?.takeIf { it.isJsonPrimitive }?.asString
            if (detected != null && sameLanguage(detected, target)) return@thenApply null

            val translated = (root.getOrNull(0) as? JsonArray)
                ?.joinToString("") { part -> (part as? JsonArray)?.getOrNull(0)?.takeIf { it.isJsonPrimitive }?.asString ?: "" }
                .orEmpty()
            translated.takeIf { it.isNotBlank() && it != text }
        }
    }

    private fun resolveTargetLanguage(configured: String): String {
        val custom = configured.trim()
        if (custom.isNotEmpty()) return custom

        val gameLanguage = Minecraft.getInstance().languageManager.selected.lowercase()
        return when (gameLanguage) {
            "zh_cn" -> "zh-CN"
            "zh_tw", "zh_hk" -> "zh-TW"
            else -> gameLanguage.substringBefore('_')
        }
    }

    private fun sameLanguage(detected: String, target: String): Boolean =
        detected.substringBefore('-').equals(target.substringBefore('-'), ignoreCase = true)

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun JsonArray.getOrNull(index: Int) = if (index in 0 until size()) get(index) else null

    private fun logError(error: Throwable, pauseMs: Long) {
        val cause = generateSequence(error) { it.cause }.last()
        logger.warn(
            "Translation failed: {}: {}. Pausing translation for {} s",
            cause.javaClass.simpleName,
            cause.message,
            pauseMs / 1000
        )
    }
}
