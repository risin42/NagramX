package tw.nekomimi.nekogram.utils

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness

object GsonUtil {

    private val gson = GsonBuilder()
        .setStrictness(Strictness.LENIENT)
        .create()

    @JvmStatic
    fun toJsonObject(json: String): JsonObject {
        return gson.fromJson(json, JsonObject::class.java)
    }

}
