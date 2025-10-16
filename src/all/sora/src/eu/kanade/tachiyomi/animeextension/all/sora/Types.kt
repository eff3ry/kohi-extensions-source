package eu.kanade.tachiyomi.animeextension.all.sora

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SoraModuleJson(
    val sourceName: String? = null,
    val iconUrl: String? = null,
    val author: SoraModuleAuthor? = null,
    val version: String? = null,
    val language: String? = null,
    val streamType: String? = null,
    val quality: String? = null,
    val baseUrl: String? = null,
    val searchBaseUrl: String? = null,
    val scriptUrl: String? = null,
    val type: String? = null,
    val asyncJS: Boolean? = null,
    val streamAsyncJS: Boolean? = null,
    val softsub: Boolean? = null,
    val downloadSupport: Boolean? = null,
)

@Serializable
data class SoraModuleAuthor(
    val name: String? = null,
    val icon: String? = null,
)

/**
 * Safe parser helper: returns the parsed [SoraModuleJson] or null on error.
 */
fun parseSoraModuleJson(jsonStr: String): SoraModuleJson? {
    return try {
        val parser = Json { ignoreUnknownKeys = true }
        parser.decodeFromString<SoraModuleJson>(jsonStr)
    } catch (e: Exception) {
        null
    }
}
