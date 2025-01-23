package tw.nekomimi.nekogram.transtale.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import tw.nekomimi.nekogram.transtale.Translator
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.*

object YouDaoTranslator : Translator {

    private val targetLanguages = listOf("ar", "de", "en", "es", "fr", "id", "it", "ja", "ko", "nl", "pt", "ru", "th", "vi", "zh-CHS", "zh-CHT")

    override suspend fun doTranslate(from: String, to: String, query: String): String {
        if (to.lowercase() !in targetLanguages.map { it.lowercase() }) {
            throw UnsupportedOperationException(LocaleController.getString(R.string.TranslateApiUnsupported) + " " + to)
        }

        return withContext(Dispatchers.IO) {
            val param = "q=" + URLEncoder.encode(query, "UTF-8") +
                    "&from=Auto" +
                    "&to=" + to
            val response = request(param)
            val jsonObject = JSONObject(response)
            if (!jsonObject.has("translation") && jsonObject.has("errorCode")) {
                throw IOException(response)
            }
            val array = jsonObject.getJSONArray("translation")
            array.getString(0)
        }
    }

    @Throws(IOException::class)
    private fun request(param: String): String {
        val httpConnectionStream: InputStream
        val downloadUrl = URL("https://aidemo.youdao.com/trans")
        val httpConnection = downloadUrl.openConnection() as HttpURLConnection
        httpConnection.addRequestProperty("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 18_1_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Mobile/15E148 Safari/605.1 NAVER(inapp; search; 2000; 12.10.4; 15PROMAX)")
        httpConnection.connectTimeout = 3000
        httpConnection.requestMethod = "POST"
        httpConnection.doOutput = true
        val dataOutputStream = DataOutputStream(httpConnection.outputStream)
        val t = param.toByteArray(Charset.defaultCharset())
        dataOutputStream.write(t)
        dataOutputStream.flush()
        dataOutputStream.close()
        httpConnection.connect()
        httpConnectionStream = if (httpConnection.responseCode != HttpURLConnection.HTTP_OK) {
            httpConnection.errorStream
        } else {
            httpConnection.inputStream
        }
        val outbuf = ByteArrayOutputStream()
        val data = ByteArray(1024 * 32)
        while (true) {
            val read = httpConnectionStream.read(data)
            if (read > 0) {
                outbuf.write(data, 0, read)
            } else if (read == -1) {
                break
            } else {
                break
            }
        }
        val result = String(outbuf.toByteArray())
        httpConnectionStream.close()
        outbuf.close()
        return result
    }
}