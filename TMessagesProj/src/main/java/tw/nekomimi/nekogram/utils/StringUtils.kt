package tw.nekomimi.nekogram.utils

import org.telegram.tgnet.TLRPC
import ws.vinta.pangu.Pangu
import kotlin.math.ceil

object StringUtils {
    private val pangu = Pangu()

    /**
     * Return a string with a maximum length of `length` characters.
     * If there are more than `length` characters, then string ends with an ellipsis ("...").
     *
     * @param text   text
     * @param length maximum length you want
     * @return Return a string with a maximum length of `length` characters.
     */
    @JvmStatic
    @Suppress("NAME_SHADOWING")
    fun ellipsis(text: String, length: Int): String {
        // The letters [iIl1] are slim enough to only count as half a character.
        var length = length
        length += ceil(text.replace("[^iIl]".toRegex(), "").length / 2.0).toInt()
        return if (text.length > length) {
            text.substring(0, length - 3) + "..."
        } else text
    }

    @JvmStatic
    fun spacingText(message: TLRPC.TL_textWithEntities): TLRPC.TL_textWithEntities {
        return TLRPC.TL_textWithEntities().apply {
            val pair = spacingText(message.text, message.entities)
            text = pair.first
            entities = pair.second
        }
    }

    @JvmStatic
    fun canUsePangu(text: String): Boolean {
        if (text.startsWith("/")) return false
        val panguText = pangu.spacingText(text)
        return panguText.length != text.length
    }

    @JvmStatic
    fun spacingText(text: String, entities: ArrayList<TLRPC.MessageEntity>?): Pair<String, ArrayList<TLRPC.MessageEntity>?> {
        if (text.startsWith("/")) return Pair(text, entities) // command
        if (entities.isNullOrEmpty()) return Pair(pangu.spacingText(text), entities)

        val panguText = pangu.spacingText(text)

        if (panguText.length == text.length) return Pair(panguText, entities) // processed or unnecessary

        var skip = 0
        for (i in text.indices) {
            if (i + skip >= panguText.length) break
            if (text[i] == panguText[i + skip]) continue

            entities.forEach {
                if (it.offset >= i + skip) { // text is after this entity
                    it.offset += 1
                } else if (it.offset + it.length >= i + skip) { // text is in this entity
                    it.length += 1
                } // text is before this entity
            }
            skip += 1
        }

        // prevent out of bound
        entities.forEach {
            if (it.offset >= panguText.length) {
                it.offset = panguText.length - 1
            }
            if (it.offset + it.length > panguText.length) {
                it.length = panguText.length - it.offset
            }
        }

        return Pair(panguText, entities)
    }
}
