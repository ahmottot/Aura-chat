package com.example.data.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

interface CompanionApiService {
    @GET
    suspend fun pairHost(
        @Url url: String, // e.g., "http://192.168.1.52:5555/pair"
        @Query("pin") pin: String
    ): PairResponse

    @GET
    suspend fun getSystemInfo(
        @Url url: String, // e.g., "http://192.168.1.52:5555/info"
        @Query("pin") pin: String
    ): SystemInfoResponse

    @GET
    suspend fun listFiles(
        @Url url: String, // e.g., "http://192.168.1.52:5555/files"
        @Query("path") path: String?,
        @Query("pin") pin: String
    ): FileListResponse

    @GET
    suspend fun searchFiles(
        @Url url: String, // e.g., "http://192.168.1.52:5555/search"
        @Query("query") query: String,
        @Query("pin") pin: String
    ): SearchResponse

    @GET
    @Streaming
    suspend fun downloadFile(
        @Url url: String, // e.g., "http://192.168.1.52:5555/download"
        @Query("path") path: String,
        @Query("pin") pin: String
    ): ResponseBody

    @GET
    suspend fun executeAction(
        @Url url: String, // e.g., "http://192.168.1.52:5555/execute"
        @Query("action_type") actionType: String,
        @Query("site_url") siteUrl: String,
        @Query("details") details: String,
        @Query("pin") pin: String
    ): ActionResponse
}

object CompanionClient {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    val service: CompanionApiService by lazy {
        Retrofit.Builder()
            .baseUrl("http://127.0.0.1/") // Dummy base url, will be overridden by @Url parameter
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(CompanionApiService::class.java)
    }
}
