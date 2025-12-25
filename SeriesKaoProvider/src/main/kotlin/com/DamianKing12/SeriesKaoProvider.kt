package com.DamianKing12

import android.util.Log // Importante para ver los logs en Logcat
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

            Log.d("SeriesKao", "Iframe URL: $iframeUrl")

            val iframeResponse = app.get(
                iframeUrl,
                referer = data
            ).text

            // --- EXTRACCIÓN CON LOGS ---
            val fileCode = Regex("file_code\\s*:\\s*['\"]([^'\"]+)['\"]")
                .find(iframeResponse)?.groupValues?.get(1)
            Log.d("SeriesKao", "fileCode: $fileCode")

            val hash = Regex("hash\\s*:\\s*['\"]([^'\"]+)['\"]")
                .find(iframeResponse)?.groupValues?.get(1)
            Log.d("SeriesKao", "hash: $hash")

            // Ajuste de Regex para t y s (más flexibles)
            val token = Regex("t\\s*=\\s*([^&'\"]+)")
                .find(iframeResponse)?.groupValues?.get(1)
            Log.d("SeriesKao", "token: $token")

            val session = Regex("s\\s*=\\s*(\\d+)")
                .find(iframeResponse)?.groupValues?.get(1)
            Log.d("SeriesKao", "session: $session")

            // Ajuste crítico: folderId suele estar antes de file_code con un guion bajo
            val folderId = Regex("/(\\d{5})/${fileCode}").find(iframeResponse)?.groupValues?.get(1) 
                ?: "06438"
            Log.d("SeriesKao", "folderId: $folderId")

            if (fileCode == null || hash == null || token == null) {
                Log.e("SeriesKao", "Faltan datos críticos para generar el link")
                return false
            }

            // Handshake obligatorio
            Log.d("SeriesKao", "Ejecutando Handshake en /dl...")
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

            val finalUrl = "https://hgc0uswxhnn8.acek-cdn.com/hls2/01/$folderId/${fileCode}_,l,n,h,.urlset/master.m3u8?t=$token&s=$session"
            Log.d("SeriesKao", "Final URL: $finalUrl")

            callback(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = finalUrl
                ) {
                    referer = "https://callistanise.com/"
                    quality = Qualities.P1080.value
                }
            )

            true
        }.getOrElse {
            Log.e("SeriesKao", "Error en loadLinks: ${it.message}")
            false
        }
    }
}
