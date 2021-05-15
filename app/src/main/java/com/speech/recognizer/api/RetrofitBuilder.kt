package com.speech.recognizer.api

import android.util.Log
import com.speech.recognizer.BuildConfig
import com.speech.recognizer.ModelResult
import com.speech.recognizer.log
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONException
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

object RetrofitBuilder {

    private val logInterceptor = HttpLoggingInterceptor(object: HttpLoggingInterceptor.Logger {
        override fun log(message: String) {
            Log.i("ffnet", message)
        }
    }).apply {
        level = HttpLoggingInterceptor.Level.BODY
    }


    private val okHttpClient = OkHttpClient().newBuilder()
        .readTimeout(60, TimeUnit.SECONDS)
//        .addInterceptor(logInterceptor)
        .build()

    private fun getRetrofit() = Retrofit.Builder()
//        .baseUrl("http://192.168.10.4:5000")
        .baseUrl(BuildConfig.BASE_API_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .client(okHttpClient)
        .build()

    private val service: ApiService = getRetrofit().create(ApiService::class.java)

    fun predict(audio: File, callback: (result: Result<ModelResult>) -> Unit) {

//        val body = audio.asRequestBody("audio/mpeg".toMediaType())
        val body = audio.inputStream().readBytes().toRequestBody("audio/*".toMediaType())

        // Define callback to handle retrofit response
        val cb = object: Callback<ModelResult> {

            override fun onResponse(call: Call<ModelResult>, response: Response<ModelResult>) {

                var result: Result<ModelResult> = Result.failure(Exception("Server returned error with status: ${response.code()}"))
                if(response.isSuccessful) {
                    result = Result.failure(Exception("Successful response but no value returned by server"))
                    response.body()?.let { res ->
                        if(res.result.isNullOrEmpty()) {
                            // If there is valid value for error then replace the default exception
                            res.error?.let { result = Result.failure(Exception(it)) }
                            // Otherwise we already have default Exception setup
                        } else {
                            // If result is not null or empty then server returned a valid result to show
                            result = Result.success(res)
                        }
                    }
                } else if(response.errorBody() != null) {
                    try {
                        log("Ree ${response.errorBody()!!.string()}")
                        val obj = JSONObject(response.errorBody()!!.string())
                        val error = obj.getString("error")
                        val e = Exception(error)
                        result = Result.failure(e)
                    } catch (e: JSONException) {
                        Log.w("ffnet", "Couldn't parse error json")
                        e.printStackTrace()
                    } catch (ne: NullPointerException) {
                        Log.w("ffnet", "Error body is null")
                    }
                }
                callback.invoke(result)
            }

            override fun onFailure(call: Call<ModelResult>, t: Throwable) {
                callback(Result.failure(t))
            }

        }

        // Call the function with audio file and insert the callback that we defined
        service.predict(
            MultipartBody.Part.createFormData("audio", audio.name, body)
        ).enqueue(cb)
    }
}