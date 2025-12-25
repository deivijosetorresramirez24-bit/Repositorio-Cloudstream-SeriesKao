package com.DamianKing12

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class SeriesKaoProvider : MainAPI() {

    override var name = "SeriesKao"
    override var mainUrl = "https://serieskao.top"
    override var supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        return runCatching {
            Log.d("SeriesKao", "Iniciando carga de links para: $data")

            val document = app.get(data).document

            val iframeUrl = document.selectFirst("iframe[src*=/embed/], iframe[src*=/v/]")?.attr("src")
                ?: return false

            Log.d("SeriesKao", "Iframe detectado: $iframeUrl")

            val iframeResponse = app.get(iframeUrl, referer = data).text

            val fileCode = Regex("file_code\\s*[:=]\\s*['\"]([^'\"]+)['\"]").find(iframeResponse)?.groupValues?.get(1)
            val hash = Regex("hash\\s*[:=]\\s*['\"]([^'\"]+)['\"]").find(iframeResponse)?.groupValues?.get(1)

            if (fileCode == null || hash == null) {
                Log.e("SeriesKao", "No se pudo obtener fileCode o hash")
                return false
            }

            app.get(
                "https://callistanise.com/dl",
                params = mapOf("op" to "view", "file_code" to fileCode, "hash" to hash),
                referer = iframeUrl
            )

            val finalUrl = Regex("file\\s*:\\s*['\"]([^'\"]+master\\.m3u8[^'\"]*)['\"]").find(iframeResponse)?.groupValues?.get(1)

            if (finalUrl != null) {
                Log.d("SeriesKao", "Link final encontrado: $finalUrl")

                // --- SOLUCIÓN A LOS ERRORES DE LA IMAGEN ---
                // Se pasan solo 4 parámetros y el resto va dentro de las llaves { }
                callback(
                    newExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = finalUrl,
                        type = ExtractorLinkType.M3U8
                    ) {
                        // Aquí dentro es donde se ponen el referer y la calidad ahora
                        this.referer = "https://callistanise.com/"
                        this.quality = Qualities.P1080.value
                    }
                )
                true
            } else {
                Log.e("SeriesKao", "No se encontró el master.m3u8 en el iframe")
                false
            }
        }.getOrElse {
            Log.e("SeriesKao", "Error fatal: ${it.message}")
            false
        }
    }
}
