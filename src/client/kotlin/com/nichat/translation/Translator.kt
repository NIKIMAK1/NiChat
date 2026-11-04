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
import java.util.concurrent.CompletableFuture

object Translator {
    private val logger = LoggerFactory.getLogger("nichat")
    private const val CACHE_SIZE = 512
    private const val ERROR_LOG_INTERVAL_MS = 60_000L

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    private data class CacheKey(val target: String, val text: String)

    private class Result(val translated: String?)

    private val cache = object : LinkedHashMap<CacheKey, Result>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, Result>): Boolean = size > CACHE_SIZE
    }

    @Volatile private var lastErrorLogTime = 0L

    fun translate(text: String, targetLanguage: String): CompletableFuture<String?> {
        val target = resolveTargetLanguage(targetLanguage)
        val key = CacheKey(target, text)

        synchronized(cache) {
            cache[key]?.let { return CompletableFuture.completedFuture(it.translated) }
        }

        return requestTranslation(text, target)
            .thenApply { translated ->
                synchronized(cache) { cache[key] = Result(translated) }
                translated
            }
            .exceptionally { error ->
                logError(error)
                null
            }
    }

    private fun requestTranslation(text: String, target: String): CompletableFuture<String?> {
        val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&dt=t" +
            "&tl=" + encode(target) +
            "&q=" + encode(text)
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(10))
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

    private fun logError(error: Throwable) {
        val now = System.currentTimeMillis()
        if (now - lastErrorLogTime < ERROR_LOG_INTERVAL_MS) return
        lastErrorLogTime = now
        val cause = generateSequence(error) { it.cause }.last()
        logger.warn("Translation failed: {}: {}", cause.javaClass.simpleName, cause.message)
    }
}
