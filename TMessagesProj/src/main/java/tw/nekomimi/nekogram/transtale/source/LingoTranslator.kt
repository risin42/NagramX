package tw.nekomimi.nekogram.transtale.source

import android.util.Log
import android.os.SystemClock
import cn.hutool.http.HttpUtil
import org.json.JSONArray
import org.json.JSONObject
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import tw.nekomimi.nekogram.transtale.Translator
import tw.nekomimi.nekogram.utils.applyUserAgent

object LingoTranslator : Translator {

    private const val NAX = "nu.gpu.nagram_LingoTranslator"

    override suspend fun doTranslate(from: String, to: String, query: String): String {
        if (BuildVars.LOGS_ENABLED) Log.d(NAX, "doTranslate: from=$from, to=$to, query=$query")

        if (to !in listOf("zh", "en", "es", "fr", "ja", "ru")) {
            error(getString(R.string.TranslateApiUnsupported) + " " + to)
        }

        val source = JSONArray()
        for (s in query.split("\n")) {
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
            .applyUserAgent()
            .body(requestBody)
            .execute()

        if (BuildVars.LOGS_ENABLED) Log.d(NAX, "doTranslate: HTTP response status: ${response.status}")
        if (BuildVars.LOGS_ENABLED) Log.d(NAX, "doTranslate: HTTP response body: ${response.body()}")

        if (response.status != 200) {
            error("HTTP ${response.status} : ${response.body()}")
        }

        val target: JSONArray = JSONObject(response.body()).getJSONArray("target")
        if (BuildVars.LOGS_ENABLED) Log.d(NAX, "doTranslate: target JSONArray: $target")

        val result = StringBuilder()
        for (i in 0 until target.length()) {
            var translatedLine = target.getString(i)
            if (translatedLine == "\ud835") { // wtf
                translatedLine = ""
                if (BuildVars.LOGS_ENABLED) Log.d(NAX, "doTranslate: skip invalid character")
            }
            result.append(translatedLine)
            if (i != target.length() - 1) {
                result.append("\n")
            }
        }

        val resultString = result.toString()
        if (BuildVars.LOGS_ENABLED) Log.d(NAX, "doTranslate: result string: $resultString")

        return resultString
    }
}
