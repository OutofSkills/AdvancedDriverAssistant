import android.util.Log
import com.example.advanceddrivingassistant.api.VinDecoderApi
import com.example.advanceddrivingassistant.dto.DecodeResponse
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.google.gson.GsonBuilder
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.security.MessageDigest

class VinDecoder(private val apiKey: String, private val secretKey: String) {
    private val api: VinDecoderApi

    init {
        val gson = GsonBuilder()
            .registerTypeAdapter(Any::class.java, DecodeValueDeserializer())
            .create()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.vindecoder.eu/3.2/")
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

        api = retrofit.create(VinDecoderApi::class.java)
    }

    private fun calculateControlSum(vin: String, id: String): String {
        val input = "$vin|$id|$apiKey|$secretKey"
        val sha1 = MessageDigest.getInstance("SHA-1")
        val hashBytes = sha1.digest(input.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }.substring(0, 10)
    }

    fun decodeVin(vin: String, callback: (String, String) -> Unit) {
        val formattedVin = vin.uppercase()
        val id = "decode"
        val controlSum = calculateControlSum(formattedVin, id)

        api.decodeVin(apiKey, controlSum, formattedVin).enqueue(object : Callback<DecodeResponse> {
            override fun onResponse(call: Call<DecodeResponse>, response: Response<DecodeResponse>) {
                if (response.isSuccessful) {
                    val decodeItems = response.body()?.decode
                    val make = decodeItems?.find { it.label == "Make" }?.value.toString() ?: "Unknown"
                    val model = decodeItems?.find { it.label == "Model" }?.value.toString() ?: "Unknown"
                    callback(make, model)
                } else {
                    Log.e("VinDecoder", "onResponse: ${response.code()}")
                    callback("Error", "Error")
                }
            }

            override fun onFailure(call: Call<DecodeResponse>, t: Throwable) {
                Log.e("VinDecoder", "onFailure", t)
                callback("Error", "Error")
            }
        })
    }
}
