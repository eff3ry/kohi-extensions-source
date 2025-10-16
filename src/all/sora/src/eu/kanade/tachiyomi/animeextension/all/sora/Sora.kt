package eu.kanade.tachiyomi.animeextension.all.sora

import android.app.Application
import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.MalformedURLException
import java.net.URL
import java.security.MessageDigest

class Sora(private val suffix: String) : AnimeCatalogueSource, ConfigurableAnimeSource {

    internal val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val displayName by lazy { preferences.getString("display_name", suffix)!!.ifBlank { suffix } }

    override val name by lazy { "Sora ($displayName)" }
    override val lang = "all"
    override val supportsLatest = false

    val versionId = 1

    // Use a JSON parser that ignores unknown keys so extra fields won't crash decoding
    private val jsonParser: Json = Json { ignoreUnknownKeys = true }

    override val id by lazy {
        val key = "sora ($suffix)/$lang/$versionId"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        return@lazy (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
    }

    override fun toString(): String {
        return "Sora ($suffix)" + if (displayName.isBlank() || displayName == suffix) "" else " ($displayName)"
    }
    private val client = okhttp3.OkHttpClient()

    // ==================================== Popular =======================================

    @Deprecated("Rx-based API is deprecated in favor of suspend functions")
    override fun fetchPopularAnime(page: Int): Observable<AnimesPage> {
        return Observable.just(AnimesPage(emptyList(), false))
    }

    override suspend fun getPopularAnime(page: Int): AnimesPage {
        return AnimesPage(emptyList(), false)
    }

    // ==================================== Latest ======================================
    // Unused
    @Deprecated("Rx-based API is deprecated in favor of suspend functions")
    override fun fetchLatestUpdates(page: Int): Observable<AnimesPage> {
        return Observable.just(AnimesPage(emptyList(), false))
    }

    override suspend fun getLatestUpdates(page: Int): AnimesPage {
        return AnimesPage(emptyList(), false)
    }

    // ==================================== Search ======================================
    @Deprecated("Rx-based API is deprecated in favor of suspend functions")
    override fun fetchSearchAnime(page: Int, query: String, filters: AnimeFilterList): Observable<AnimesPage> {
        return Observable.just(AnimesPage(emptyList(), false))
    }

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        return super.getSearchAnime(page, query, filters)
    }

    // =================================== Details ======================================

    @Deprecated("Rx-based API is deprecated in favor of suspend functions")
    override fun fetchAnimeDetails(anime: SAnime): Observable<SAnime> {
        return Observable.just(anime)
    }

    override suspend fun getAnimeDetails(anime: SAnime): SAnime {
        return anime
    }

    // =================================== Episodes =====================================

    @Deprecated("Rx-based API is deprecated in favor of suspend functions")
    override fun fetchEpisodeList(anime: SAnime): Observable<List<SEpisode>> {
        return Observable.just(emptyList())
    }

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        return emptyList()
    }

    // ==================================== Videos ======================================

    @Deprecated("Rx-based API is deprecated in favor of suspend functions")
    override fun fetchVideoList(episode: SEpisode): Observable<List<Video>> {
        return Observable.just(emptyList())
    }

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        return emptyList()
    }

    // =================================== Filters ======================================
    override fun getFilterList(): AnimeFilterList {
        return AnimeFilterList()
    }
    // =========================================== Utils ============================================

    private fun fetchUrl(url: String, onSuccess: (String) -> Unit, onError: (Exception) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body.string()
                    if (body.isNotBlank()) {
                        withContext(Dispatchers.Main) { onSuccess(body) }
                    } else {
                        withContext(Dispatchers.Main) { onError(Exception("Response body is null")) }
                    }
                } else {
                    withContext(Dispatchers.Main) { onError(Exception("Request failed with code: ${response.code}")) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e) }
            }
        }
    }

    /**
     * Read the stored source JSON from preferences and parse it into [SoraModuleJson].
     * Returns null if no JSON is stored or parsing fails.
     */
    fun getStoredModuleJson(): SoraModuleJson? {
        val raw = preferences.getString(SOURCE_JSON_KEY, null) ?: return null
        if (raw.isBlank()) return null
        return parseSoraModuleJson(raw)
    }

    /**
     * Fetch a module JSON from [url], store it in preferences, parse it, and fetch/store its JS (if any).
     * Toasts are shown via the application context. Network operations run via [fetchUrl].
     */
    private fun fetchAndStoreModule(url: String) {
        fetchUrl(
            url = url,
            onSuccess = { body ->
                // store raw JSON
                preferences.edit().putString(SOURCE_JSON_KEY, body).apply()
                try {
                    val module = jsonParser.decodeFromString<SoraModuleJson>(body)
                    Toast.makeText(Injekt.get<Application>(), "Successfully fetched and parsed JSON", Toast.LENGTH_SHORT).show()

                    val scriptUrl = module.scriptUrl
                    if (!scriptUrl.isNullOrBlank()) {
                        fetchUrl(scriptUrl, onSuccess = { jsBody ->
                            preferences.edit().putString(SOURCE_JS_KEY, jsBody).apply()
                            Toast.makeText(Injekt.get<Application>(), "Successfully fetched JS", Toast.LENGTH_SHORT).show()
                        }, onError = { _ ->
                            Toast.makeText(Injekt.get<Application>(), "Failed to fetch JS", Toast.LENGTH_SHORT).show()
                        })
                    }
                } catch (_: Exception) {
                    Toast.makeText(Injekt.get<Application>(), "Failed to parse JSON", Toast.LENGTH_SHORT).show()
                }
            },
            onError = { Toast.makeText(Injekt.get<Application>(), "Failed to fetch JSON", Toast.LENGTH_SHORT).show() },
        )
    }

    // ====================================== Preferences ========================================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val extraSources = ListPreference(screen.context).apply {
            key = EXTRA_SOURCES_COUNT_KEY
            title = EXTRA_SOURCES_TITLE
            summary = EXTRA_SOURCES_SUMMARY
            entries = EXTRA_SOURCES_ENTRIES
            entryValues = EXTRA_SOURCES_ENTRIES
            setDefaultValue(EXTRA_SOURCES_COUNT_DEFAULT)
            setOnPreferenceChangeListener { _, newValue ->
                val value = (newValue as? String)?.toIntOrNull() ?: return@setOnPreferenceChangeListener false
                if (value < EXTRA_SOURCES_MIN || value > EXTRA_SOURCES_MAX) return@setOnPreferenceChangeListener false
                true
            }
        }

        val moduleSource = EditTextPreference(screen.context).apply {
            key = SOURCE_KEY
            title = SOURCE_TITLE
            dialogTitle = SOURCE_DIALOG_TITLE
            dialogMessage = SOURCE_DIALOG_MESSAGE
            summary = preferences.getString(SOURCE_KEY, SOURCE_DEFAULT) ?: ""
            setDefaultValue(SOURCE_DEFAULT)

            setOnPreferenceChangeListener { preference, newValue ->
                val value = (newValue as? String)?.trim() ?: return@setOnPreferenceChangeListener false
                if (value.isEmpty()) return@setOnPreferenceChangeListener false
                try {
                    val url = URL(value)
                    val proto = url.protocol
                    if (proto != "http" && proto != "https") return@setOnPreferenceChangeListener false
                    val lower = url.toString().lowercase()
                    val path = url.path.lowercase()
                    if (!lower.endsWith(".json") && !path.endsWith(".json")) return@setOnPreferenceChangeListener false
                } catch (_: MalformedURLException) {
                    return@setOnPreferenceChangeListener false
                }

                preferences.edit().putString(SOURCE_KEY, value).apply()
                preference.summary = value

                // attempt to fetch and store contents
                fetchAndStoreModule(value)

                true
            }
        }

        if (suffix == "1") {
            screen.addPreference(extraSources)
        }
        screen.addPreference(moduleSource)

        if (true) {
            screen.addPreference(
                EditTextPreference(screen.context).apply {
                    key = SOURCE_JSON_KEY
                    title = "Source JSON Debug"
                },
            )
            screen.addPreference(
                EditTextPreference(screen.context).apply {
                    key = SOURCE_JS_KEY
                    title = "Source JS Debug"
                },
            )
        }
    }

    companion object {
        const val SOURCE_KEY = "module_source"
        const val SOURCE_TITLE = "Source JSON URL"
        const val SOURCE_DIALOG_TITLE = "Source JSON URL"
        const val SOURCE_DIALOG_MESSAGE = "Enter a URL that points to a JSON file (must use http or https and end with .json)"
        const val SOURCE_DEFAULT = ""

        const val EXTRA_SOURCES_COUNT_KEY = "extra_sources_count"
        const val EXTRA_SOURCES_TITLE = "Number of sources"
        const val EXTRA_SOURCES_SUMMARY = "Number of Sora sources to create. There will always be at least one Sora source."
        const val EXTRA_SOURCES_COUNT_DEFAULT = "3"
        val EXTRA_SOURCES_ENTRIES = arrayOf("1", "2", "3", "4", "5")
        const val EXTRA_SOURCES_MIN = 1
        const val EXTRA_SOURCES_MAX = 5

        const val SOURCE_JSON_KEY = "source_json"
        const val SOURCE_JS_KEY = "source_js"
    }
}
