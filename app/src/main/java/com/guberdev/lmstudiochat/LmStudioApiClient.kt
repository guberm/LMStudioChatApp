package com.guberdev.lmstudiochat

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Streaming
import java.util.concurrent.TimeUnit

data class ChatMessage(val role: String, val content: String)
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
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    var apiService: LmStudioApiService = buildRetrofit().create(LmStudioApiService::class.java)

    fun updateBaseUrl(newUrl: String, ngrokMode: Boolean = false) {
        BASE_URL = if (newUrl.endsWith("/")) newUrl else "$newUrl/"
        extraHeaders = if (ngrokMode) mapOf("ngrok-skip-browser-warning" to "true") else emptyMap()
        apiService = buildRetrofit().create(LmStudioApiService::class.java)
    }
}
