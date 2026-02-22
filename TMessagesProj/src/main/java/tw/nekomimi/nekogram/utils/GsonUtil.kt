package tw.nekomimi.nekogram.utils

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness
import com.google.gson.internal.Streams
import java.io.StringWriter

object GsonUtil {

    private val gson = GsonBuilder()
        .setStrictness(Strictness.LENIENT)
        .create()

    @JvmStatic
    fun formatObject(obj: Any?): String {
        if (obj == null) return "null"
        val json = gson.toJsonTree(obj)
        val stringWriter = StringWriter()
        val jsonWriter = gson.newJsonWriter(stringWriter)
        jsonWriter.setIndent("    ")
        Streams.write(json, jsonWriter)
        return stringWriter.toString()
    }

    @JvmStatic
    fun toJsonObject(json: String): JsonObject {
        return gson.fromJson(json, JsonObject::class.java)
    }

}
