package xyz.nextalone.nagram.helper

import com.google.gson.Gson
import org.telegram.tgnet.TLRPC
import tw.nekomimi.nekogram.NekoConfig
import xyz.nextalone.nagram.NaConfig

data class LocalEmojiStatusData(
    var documentId: Long?, var until: Int?
)

object LocalPremiumStatusHelper {
    var loaded: Boolean = false
    var data: LocalEmojiStatusData? = null

    @JvmStatic
    fun getDocumentId(user: TLRPC.User?): Long? {
        if (!NekoConfig.localPremium.Bool()) return null
        init()
        if (user != null && user.self && data != null) {
            val u = data!!.until ?: 0
            if (u != 0 && u <= (System.currentTimeMillis() / 1000).toInt()) {
                return null
            }
            return data!!.documentId
        }
        return null
    }

    @JvmStatic
    fun init(force: Boolean = false) {
        if (loaded && !force) return
        loaded = true
        try {
            val gson = Gson()
            data = gson.fromJson(
                NaConfig.useLocalEmojiStatusData.String(),
                LocalEmojiStatusData::class.java
            )
        } catch (_: Exception) {
        }
    }

    @JvmStatic
    fun apply(status: TLRPC.EmojiStatus?) {
        if (!NekoConfig.localPremium.Bool()) return
        if (status == null || status is TLRPC.TL_emojiStatusEmpty) {
            NaConfig.useLocalEmojiStatusData.setConfigString("")
            init(true)
            return
        }
        var documentId: Long? = null
        var until: Int? = null
        when (status) {
            is TLRPC.TL_emojiStatus -> {
                documentId = status.document_id
                until = if ((status.flags and 1) != 0) status.until else null
            }

            is TLRPC.TL_emojiStatusCollectible -> {
                documentId = status.document_id
                until = if ((status.flags and 1) != 0) status.until else null
            }
        }
        val localData = LocalEmojiStatusData(documentId, until)
        NaConfig.useLocalEmojiStatusData.setConfigString(Gson().toJson(localData))
        init(true)
    }
}
