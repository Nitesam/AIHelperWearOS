package com.base.aihelperwearos.data.metodi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MetodiTheoryChunk(
    @SerialName("id")
    val id: String,
    @SerialName("page")
    val page: Int,
    @SerialName("title")
    val title: String,
    @SerialName("keywords")
    val keywords: List<String>,
    @SerialName("text")
    val text: String
)

@Serializable
data class MetodiTheoryDatabase(
    @SerialName("version")
    val version: String,
    @SerialName("last_updated")
    val lastUpdated: String,
    @SerialName("source")
    val source: String,
    @SerialName("chunks")
    val chunks: List<MetodiTheoryChunk>
)

@Serializable
data class MetodiCodeExample(
    @SerialName("id")
    val id: String,
    @SerialName("category")
    val category: String,
    @SerialName("subtype")
    val subtype: String,
    @SerialName("title")
    val title: String,
    @SerialName("sourcePath")
    val sourcePath: String,
    @SerialName("keywords")
    val keywords: List<String>,
    @SerialName("code")
    val code: String
)

@Serializable
data class MetodiCodeDatabase(
    @SerialName("version")
    val version: String,
    @SerialName("last_updated")
    val lastUpdated: String,
    @SerialName("source")
    val source: String,
    @SerialName("examples")
    val examples: List<MetodiCodeExample>
)

data class MetodiContextResult(
    val context: String?,
    val matchedIds: List<String>,
    val matchedLabels: List<String>,
    val fallbackReason: String? = null
)
