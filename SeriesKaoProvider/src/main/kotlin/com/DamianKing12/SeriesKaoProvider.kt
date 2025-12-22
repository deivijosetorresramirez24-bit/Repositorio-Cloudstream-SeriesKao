package com.DamianKing12

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import java.net.URLEncoder

class SeriesKaoProvider : MainAPI() {

    override var mainUrl = "https://serieskao.top"
    override var name = "SeriesKao"
    override var lang = "es"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val hasMainPage = false

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    )

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search?s=${URLEncoder.encode(query, "UTF-8")}"
        val doc = app.get(url, headers = headers).document

        return doc.select("a").filter { element ->
            val href = element.attr("href")
            href.contains("/pelicula/", ignoreCase = true) || href.contains("/serie/", ignoreCase = true)
        }.mapNotNull { el ->
            val href = el.attr("href")
            val title = el.selectFirst(".poster-card__title")?.text()?.trim() ?: return@mapNotNull null
            val poster = el.selectFirst("img")?.attr("src") ?: ""
            val year = el.selectFirst(".poster-card__year")?.text()?.toIntOrNull()

            if (href.contains("/pelicula/", ignoreCase = true)) {
                newMovieSearchResponse(title, href) {
                    this.posterUrl = poster
                    this.year = year
                }
            } else {
                newTvSeriesSearchResponse(title, href) {
                    this.posterUrl = poster
                    this.year = year
                }
            }
        }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, headers = headers).document
        val isMovie = url.contains("/pelicula/", ignoreCase = true)
        val isSerie = url.contains("/serie/", ignoreCase = true)

        if (!isMovie && !isSerie) {
            throw ErrorLoadingException("URL no válida: $url")
        }

        val title = doc.selectFirst("h1")?.text()?.trim()
            ?: doc.selectFirst(".original-title")?.text()?.trim()
            ?: throw ErrorLoadingException("No se pudo obtener el título")

        val poster = doc.selectFirst("meta[property='og:image']")?.attr("content") ?: ""
        val description = doc.selectFirst(".synopsis, .description, .plot")?.text()?.trim()
            ?: doc.select("p").firstOrNull { it.text().length > 50 }?.text()?.trim()

        if (isMovie) {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
            }
        } else {
            val episodes = doc.select("#season-tabs li a[data-tab]").mapNotNull { seasonLink ->
                val seasonId = seasonLink.attr("data-tab").substringAfter("season-").toIntOrNull() ?: return@mapNotNull null

                val seasonContent = doc.selectFirst("div.tab-content #season-${seasonId}")

                seasonContent?.select("a.episode-item")?.mapNotNull { episodeLink ->
                    val href = episodeLink.attr("href").trim()
                    val epNumText = episodeLink.selectFirst(".episode-number")?.text() ?: ""
                    val epNum = epNumText.removePrefix("E").toIntOrNull() ?: 0
                    val epTitle = episodeLink.selectFirst(".episode-title")?.text()?.trim()

                    if (href.isNotBlank() && epNum > 0) {
                        newEpisode(href) {
                            this.name = epTitle
                            this.season = seasonId
                            this.episode = epNum
                        }
                    } else null
                } ?: emptyList()
            }.flatten()

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data, headers = headers).document

        // 1️⃣ SUBTÍTULOS
        doc.select("track[kind=subtitles]").forEach { track ->
            val src = track.attr("src")
            if (src.isNotBlank()) {
                subtitleCallback(
                    newSubtitleFile(
                        track.attr("srclang") ?: "es",
                        src
                    )
                )
            }
        }

        // 2️⃣ IFRAMES (reproductores externos)
        doc.select("iframe").forEach { iframe ->
            val src = iframe.attr("src")
            if (src.isNotBlank()) {
                callback(
                    newExtractorLink(
                        name = "iframe",
                        source = "iframe",
                        url = src
                    ).apply {
                        this.referer = mainUrl
                        this.isM3u8 = false
                    }
                )
            }
        }

        // 3️⃣ MASTER.TXT (índices HLS)
        val masterScript = doc.select("script").map { it.data() }.firstOrNull { it.contains("master.txt") }
        if (masterScript != null) {
            val masterUrl = Regex("""(https?://[^"'\s]+master\.txt)""").find(masterScript)?.value
            if (masterUrl != null) {
                callback(
                    newExtractorLink(
                        name = "HLS",
                        source = "HLS",
                        url = masterUrl
                    ).apply {
                        this.referer = mainUrl
                        this.isM3u8 = true // Es índice HLS
                    }
                )
            }
        }

        // 4️⃣ SERVIDORES VAR SERVERS (retrocompatibilidad)
        val scriptElement = doc.selectFirst("script:containsData(var servers =)")
        if (scriptElement != null) {
            val serversJson = scriptElement.data().substringAfter("var servers = ").substringBefore(";").trim()
            return try {
                val servers = AppUtils.parseJson<List<ServerData>>(serversJson)
                servers.forEach { server ->
                    val cleanUrl = server.url.replace("\\/", "/")
                    callback(
                        newExtractorLink(
                            name = server.title,
                            source = server.title,
                            url = cleanUrl
                        ).apply {
                            this.quality = getQuality(server.title)
                            this.isM3u8 = cleanUrl.contains(".m3u8", ignoreCase = true)
                            this.referer = mainUrl
                        }
                    )
                }
                servers.isNotEmpty()
            } catch (e: Exception) {
                false
            }
        }

        return doc.select("iframe").isNotEmpty() || masterScript != null
    }

    private fun getQuality(name: String): Int {
        val lowerName = name.lowercase()
        return when {
            "1080" in lowerName || "fullhd" in lowerName -> Qualities.P1080.value
            "720" in lowerName || "hd" in lowerName -> Qualities.P720.value
            "480" in lowerName || "sd" in lowerName -> Qualities.P480.value
            else -> Qualities.Unknown.value
        }
    }

    data class ServerData(val id: Int, val title: String, val url: String)
}
