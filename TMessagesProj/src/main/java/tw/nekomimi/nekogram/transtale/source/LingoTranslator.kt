package tw.nekomimi.nekogram.transtale.source

import android.util.Log
import android.os.SystemClock
import cn.hutool.http.HttpUtil
import org.json.JSONArray
import org.json.JSONObject
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Components.TranslateAlert2
import tw.nekomimi.nekogram.transtale.HTMLKeeper
import tw.nekomimi.nekogram.transtale.Translator

object LingoTranslator : Translator {

    private const val NAX = "LingoTranslator"

    override suspend fun doTranslate(from: String, to: String, query: String, entities: ArrayList<TLRPC.MessageEntity>): TLRPC.TL_textWithEntities {
        if (BuildVars.LOGS_ENABLED) Log.d(NAX, "doTranslate: from=$from, to=$to, query=$query")

        if (to !in listOf("zh", "en", "es", "fr", "ja", "ru")) {
            error(getString(R.string.TranslateApiUnsupported) + " " + to)
        }

        val originalText = TLRPC.TL_textWithEntities()
        originalText.text = query
        originalText.entities = entities

        val finalString = StringBuilder()

        val textToTranslate = if (entities.isNotEmpty()) HTMLKeeper.entitiesToHtml(
            query,
            entities,
            false
        ) else query

        val source = JSONArray()
        for (s in textToTranslate.split("\n")) {
            source.put(s)
        }
        if (BuildVars.LOGS_ENABLED) Log.d(NAX, "doTranslate: source JSONArray: $source")

        val requestBody = JSONObject().apply {
            put("source", source)
            put("trans_type", "${from}2$to")
            put("request_id", SystemClock.elapsedRealtime().toString())
            put("detect", true)
        }.toString()
        if (BuildVars.LOGS_ENABLED) Log.d(NAX, "doTranslate: request body: $requestBody")

        val response = HttpUtil.createPost("https://api.interpreter.caiyunai.com/v1/translator")
            .header("Content-Type", "application/json; charset=UTF-8")
            .header("X-Authorization", "token 9sdftiq37bnv410eon2l") // 白嫖
            .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 10_0 like Mac OS X) AppleWebKit/602.1.38 (KHTML, like Gecko) Version/10.0 Mobile/14A5297c Safari/602.1")
            .body(requestBody)
            .execute()

        if (BuildVars.LOGS_ENABLED) Log.d(NAX, "doTranslate: HTTP response status: ${response.status}")
        if (BuildVars.LOGS_ENABLED) Log.d(NAX, "doTranslate: HTTP response body: ${response.body()}")

        if (response.status != 200) {
            error("HTTP ${response.status} : ${response.body()}")
        }

        val target: JSONArray = JSONObject(response.body()).getJSONArray("target")
        if (BuildVars.LOGS_ENABLED) Log.d(NAX, "doTranslate: target JSONArray: $target")

        for (i in 0 until target.length()) {
            var translatedLine = target.getString(i)
            if (translatedLine == "\ud835") { // wtf
                translatedLine = ""
                if (BuildVars.LOGS_ENABLED) Log.d(NAX, "doTranslate: skip invalid character")
            }
            finalString.append(translatedLine)
            if (i != target.length() - 1) {
                finalString.append("\n")
            }
        }

        var finalText = TLRPC.TL_textWithEntities()
        if (entities.isNotEmpty()) {
            val resultPair = HTMLKeeper.htmlToEntities(finalString.toString(), entities, false)
            finalText.text = resultPair.first
            finalText.entities = resultPair.second
            finalText = TranslateAlert2.preprocess(originalText, finalText)
        } else {
            finalText.text = finalString.toString()
        }

        return finalText
    }
}
