package com.DamianKing12

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app

class SeriesKaoProvider : MainAPI() {
    override var name = "SeriesKao"
    override var mainUrl = "https://serieskao.top" 
    override var supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val document = app.get(url).document
        return document.select("div.result-item").mapNotNull {
            val title = it.selectFirst("div.title a")?.text() ?: return@mapNotNull null
            val href = it.selectFirst("div.title a")?.attr("href") ?: ""
            val poster = it.selectFirst("img")?.attr("src")
            
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("SeriesKao", "--- CARGANDO ENLACES DESDE GITHUB ---")
        return runCatching {
            val document = app.get(data).document
            val iframeUrl = document.selectFirst("iframe[src*=/embed/], iframe[src*=/v/]")?.attr("src")
                ?: return false

            Log.d("SeriesKao", "Iframe: $iframeUrl")
            val iframeResponse = app.get(iframeUrl, referer = data).text

            val fileCode = Regex("file_code\\s*[:=]\\s*['\"]([^'\"]+)['\"]").find(iframeResponse)?.groupValues?.get(1)
            val hash = Regex("hash\\s*[:=]\\s*['\"]([^'\"]+)['\"]").find(iframeResponse)?.groupValues?.get(1)
            
            if (fileCode != null && hash != null) {
                app.get(
                    "https://callistanise.com/dl",
                    params = mapOf("op" to "view", "file_code" to fileCode, "hash" to hash),
                    referer = iframeUrl
                )
            }

            val finalUrl = Regex("file\\s*:\\s*['\"]([^'\"]+master\\.m3u8[^'\"]*)['\"]").find(iframeResponse)?.groupValues?.get(1)
            
            if (finalUrl != null) {
                Log.d("SeriesKao", "Link: $finalUrl")
                callback(
                    newExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = finalUrl,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = "https://callistanise.com/"
                        this.quality = Qualities.P1080.value
                    }
                )
                true
            } else {
                false
            }
        }.getOrElse { false }
    }
}
