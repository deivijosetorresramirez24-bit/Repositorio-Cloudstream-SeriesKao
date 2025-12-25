package com.DamianKing12

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class SeriesKaoProvider : MainAPI() {

    override var name = "SeriesKao"
    override var mainUrl = "https://serieskao.tv"
    override var supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        return runCatching {

            val document = app.get(data).document
            val iframeUrl = document.selectFirst("iframe[src*=/embed/]")?.attr("src")
                ?: return false

            val iframeResponse = app.get(
                iframeUrl,
                referer = data
            ).text

            val fileCode = Regex("file_code\\s*:\\s*['\"]([^'\"]+)['\"]")
                .find(iframeResponse)?.groupValues?.get(1)
                ?: return false

            val hash = Regex("hash\\s*:\\s*['\"]([^'\"]+)['\"]")
                .find(iframeResponse)?.groupValues?.get(1)
                ?: return false

            val token = Regex("t=([^&'\"]+)")
                .find(iframeResponse)?.groupValues?.get(1)
                ?: return false

            val session = Regex("s=(\\d+)")
                .find(iframeResponse)?.groupValues?.get(1)
                ?: return false

            val folderId = Regex("/(\\d{5})/$fileCode")
                .find(iframeResponse)
                ?.groupValues
                ?.get(1)
                ?: "06438"

            // Handshake obligatorio
            app.get(
                "https://callistanise.com/dl",
                params = mapOf(
                    "op" to "view",
                    "file_code" to fileCode,
                    "hash" to hash,
                    "embed" to "1"
                ),
                referer = iframeUrl
            )

            val finalUrl =
                "https://hgc0uswxhnn8.acek-cdn.com/hls2/01/$folderId/${fileCode}_,l,n,h,.urlset/master.m3u8?t=$token&s=$session"

            callback(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = finalUrl
                ) {
                    referer = "https://callistanise.com/"
                    quality = Qualities.P1080.value
                    isM3u8 = true
                }
            )

            true
        }.getOrDefault(false)
    }
}
