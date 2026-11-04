package com.nichat.translation

import com.nichat.StyledText
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent

object MessageTranslation {
    private const val MAX_PREFIX_LENGTH = 40
    private val PREFIX_SEPARATOR = Regex("""\S(: |> |» | → )""")
    private val LETTER = Regex("""\p{L}""")

    class Parts internal constructor(
        private val styled: StyledText,
        private val prefixLength: Int,
        val body: String
    ) {
        fun build(translatedBody: String, original: Component): Component {
            val bodyStyle = styled.styleAt(prefixLength).withHoverEvent(HoverEvent.ShowText(original))
            return styled.toComponent(0, prefixLength)
                .append(Component.literal(translatedBody).setStyle(bodyStyle))
        }
    }

    fun split(message: Component): Parts? {
        val styled = StyledText.of(message)
        val full = styled.plain
        val prefixLength = PREFIX_SEPARATOR.find(full)
            ?.takeIf { it.range.last < MAX_PREFIX_LENGTH }
            ?.let { it.range.last + 1 }
            ?: 0

        val rest = full.substring(prefixLength)
        val body = rest.trim()
        if (!LETTER.containsMatchIn(body)) return null

        val leadingSpaces = rest.length - rest.trimStart().length
        return Parts(styled, prefixLength + leadingSpaces, body)
    }
}
