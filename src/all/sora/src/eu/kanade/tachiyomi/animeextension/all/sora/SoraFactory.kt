package eu.kanade.tachiyomi.animeextension.all.sora

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory

class SoraFactory : AnimeSourceFactory {
    override fun createSources(): List<AnimeSource> {
        val firstSora = Sora("1")
        val extraCount = firstSora.preferences
            .getString(Sora.EXTRA_SOURCES_COUNT_KEY, Sora.EXTRA_SOURCES_COUNT_DEFAULT)!!
            .toInt()

        return buildList(extraCount) {
            add(firstSora)
            for (i in 2..extraCount) {
                add(Sora("$i"))
            }
        }
    }
}
