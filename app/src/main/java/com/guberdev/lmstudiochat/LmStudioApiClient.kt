package com.guberdev.lmstudiochat

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Streaming
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit

data class ChatMessage(
    val role: String,
    val content: String,
    val images: List<String>? = null  // base64-encoded JPEG images for vision models
)

// Serializes ChatMessage for the API:
//   text-only  → {"role":"...", "content":"..."}
//   with images → {"role":"...", "content":[{"type":"text","text":"..."},{"type":"image_url","image_url":{"url":"data:image/jpeg;base64,..."}},…]}
private class ChatMessageApiSerializer : JsonSerializer<ChatMessage> {
    override fun serialize(src: ChatMessage, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val obj = JsonObject()
        obj.addProperty("role", src.role)
        val imgs = src.images ?: emptyList()
        if (imgs.isEmpty()) {
            obj.addProperty("content", src.content)
        } else {
            val arr = JsonArray()
            if (src.content.isNotBlank()) {
                val textPart = JsonObject()
                textPart.addProperty("type", "text")
                textPart.addProperty("text", src.content)
                arr.add(textPart)
            }
            imgs.forEach { b64 ->
                val imagePart = JsonObject()
                imagePart.addProperty("type", "image_url")
                val urlObj = JsonObject()
                urlObj.addProperty("url", "data:image/jpeg;base64,$b64")
                imagePart.add("image_url", urlObj)
                arr.add(imagePart)
            }
            obj.add("content", arr)
        }
        return obj
    }
}

data class ChatRequest(
    val model: String = "local-model",
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    val stream: Boolean = false
)
data class ChatResponse(val id: String?, val choices: List<Choice>?)
data class Choice(val index: Int, val message: ChatMessage)

data class StreamResponse(val id: String?, val choices: List<StreamChoice>?)
data class StreamChoice(val delta: StreamDelta)
data class StreamDelta(val content: String?)

data class ModelsResponse(val data: List<LmModel>)
data class LmModel(val id: String)

interface LmStudioApiService {
    @POST("chat/completions")
    suspend fun createChatCompletion(@Body request: ChatRequest): ChatResponse

    @Streaming
    @POST("chat/completions")
    suspend fun createChatCompletionStream(@Body request: ChatRequest): ResponseBody

    @GET("models")
    suspend fun getModels(): ModelsResponse
}

object LmStudioApiClient {
    var BASE_URL = "http://10.0.2.2:1234/v1/"
    private var extraHeaders: Map<String, String> = emptyMap()

    private val apiGson = GsonBuilder()
        .registerTypeAdapter(ChatMessage::class.java, ChatMessageApiSerializer())
        .create()

    private fun buildClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder().apply {
                extraHeaders.forEach { (k, v) -> header(k, v) }
            }.build()
            chain.proceed(req)
        }
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.HEADERS })
        .build()

    private fun buildRetrofit(): Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(buildClient())
        .addConverterFactory(GsonConverterFactory.create(apiGson))
        .build()

    var apiService: LmStudioApiService = buildRetrofit().create(LmStudioApiService::class.java)

    fun updateBaseUrl(newUrl: String, ngrokMode: Boolean = false) {
        BASE_URL = if (newUrl.endsWith("/")) newUrl else "$newUrl/"
        extraHeaders = if (ngrokMode) mapOf("ngrok-skip-browser-warning" to "true") else emptyMap()
        apiService = buildRetrofit().create(LmStudioApiService::class.java)
    }
}
