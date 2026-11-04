package com.nichat.translation

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import java.util.Optional

object MessageTranslation {
    private const val MAX_PREFIX_LENGTH = 40
    private val PREFIX_SEPARATOR = Regex("""\S(: |> |» | → )""")
    private val LETTER = Regex("""\p{L}""")

    data class Segment(val style: Style, val text: String)

    class Parts internal constructor(
        private val segments: List<Segment>,
        private val prefixLength: Int,
        val body: String
    ) {
        fun build(translatedBody: String, original: Component): Component {
            val result = Component.empty()
            var remaining = prefixLength
            var bodyStyle = Style.EMPTY

            for (segment in segments) {
                if (remaining <= 0) {
                    bodyStyle = segment.style
                    break
                }
                val taken = segment.text.take(remaining)
                result.append(Component.literal(taken).setStyle(segment.style))
                remaining -= taken.length
                if (taken.length < segment.text.length) {
                    bodyStyle = segment.style
                    break
                }
            }

            val translatedStyle = bodyStyle.withHoverEvent(HoverEvent.ShowText(original))
            return result.append(Component.literal(translatedBody).setStyle(translatedStyle))
        }
    }

    fun split(message: Component): Parts? {
        val segments = mutableListOf<Segment>()
        message.visit(FormattedText.StyledContentConsumer<Unit> { style, text ->
            if (text.isNotEmpty()) segments.add(Segment(style, text))
            Optional.empty()
        }, Style.EMPTY)

        val full = segments.joinToString("") { it.text }
        val prefixLength = PREFIX_SEPARATOR.find(full)
            ?.takeIf { it.range.last < MAX_PREFIX_LENGTH }
            ?.let { it.range.last + 1 }
            ?: 0

        val rest = full.substring(prefixLength)
        val body = rest.trim()
        if (!LETTER.containsMatchIn(body)) return null

        val leadingSpaces = rest.length - rest.trimStart().length
        return Parts(segments, prefixLength + leadingSpaces, body)
    }
}
