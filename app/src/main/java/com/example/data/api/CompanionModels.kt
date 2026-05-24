package com.example.data.api

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SystemInfoResponse(
    val status: String,
    val os: String,
    val hostname: String,
    val cpu_usage: Double,
    val memory_usage: Double,
    val disk_free: String,
    val active_app: String? = null
)

@JsonClass(generateAdapter = true)
data class FileItem(
    val name: String,
    val is_dir: Boolean,
    val path: String,
    val size: Long
)

@JsonClass(generateAdapter = true)
data class FileListResponse(
    val status: String,
    val current_path: String,
    val is_root: Boolean,
    val parent: String?,
    val items: List<FileItem>
)

@JsonClass(generateAdapter = true)
data class SearchResponse(
    val status: String,
    val query: String,
    val matches: List<FileItem>
)

@JsonClass(generateAdapter = true)
data class PairResponse(
    val status: String,
    val message: String,
    val computer_name: String
)

@JsonClass(generateAdapter = true)
data class ActionResponse(
    val status: String,
    val message: String
)
