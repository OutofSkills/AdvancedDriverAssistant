import com.google.gson.*
import java.lang.reflect.Type

class DecodeValueDeserializer : JsonDeserializer<Any> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): Any {
        return when {
            json == null -> ""
            json.isJsonArray -> json.asJsonArray.map { it.asString }
            json.isJsonPrimitive -> json.asString
            else -> ""
        }
    }
}
