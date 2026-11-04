package com.nichat

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.util.StringDecomposer

class StyledText private constructor(val segments: List<Segment>) {
    data class Segment(val style: Style, val text: String)

    val plain: String = segments.joinToString("") { it.text }

    fun restyle(start: Int, end: Int, transform: (Style) -> Style): StyledText {
        val result = mutableListOf<Segment>()
        var offset = 0
        for (segment in segments) {
            val segmentStart = offset
            val segmentEnd = offset + segment.text.length
            offset = segmentEnd

            if (segmentEnd <= start || segmentStart >= end) {
                result.add(segment)
                continue
            }

            val localStart = (start - segmentStart).coerceAtLeast(0)
            val localEnd = (end - segmentStart).coerceAtMost(segment.text.length)
            if (localStart > 0) result.add(Segment(segment.style, segment.text.substring(0, localStart)))
            result.add(Segment(transform(segment.style), segment.text.substring(localStart, localEnd)))
            if (localEnd < segment.text.length) result.add(Segment(segment.style, segment.text.substring(localEnd)))
        }
        return StyledText(result)
    }

    fun styleAt(index: Int): Style {
        var offset = 0
        for (segment in segments) {
            if (index < offset + segment.text.length) return segment.style
            offset += segment.text.length
        }
        return segments.lastOrNull()?.style ?: Style.EMPTY
    }

    fun toComponent(start: Int = 0, end: Int = plain.length): MutableComponent {
        val result = Component.empty()
        var offset = 0
        for (segment in segments) {
            val segmentStart = offset
            offset += segment.text.length
            val from = (start - segmentStart).coerceIn(0, segment.text.length)
            val to = (end - segmentStart).coerceIn(0, segment.text.length)
            if (from < to) {
                result.append(Component.literal(segment.text.substring(from, to)).setStyle(segment.style))
            }
        }
        return result
    }

    companion object {
        @JvmStatic
        fun of(text: FormattedText): StyledText {
            val segments = mutableListOf<Segment>()
            var currentStyle: Style? = null
            val current = StringBuilder()

            StringDecomposer.iterateFormatted(text, Style.EMPTY) { _, style, codePoint ->
                if (style != currentStyle && current.isNotEmpty()) {
                    segments.add(Segment(currentStyle!!, current.toString()))
                    current.setLength(0)
                }
                currentStyle = style
                current.appendCodePoint(codePoint)
                true
            }
            if (current.isNotEmpty()) segments.add(Segment(currentStyle!!, current.toString()))

            return StyledText(segments)
        }
    }
}
