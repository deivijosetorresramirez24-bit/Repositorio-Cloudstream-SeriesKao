package com.DamianKing12

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import org.jsoup.nodes.Element

class SeriesKaoProvider : MainAPI() {
    override var mainUrl = "https://serieskao.tv"
    override var name = "SeriesKao"
    override val hasMainPage = true
    override var lang = "es"
    override val hasQuickSearch = false

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document

        // 1️⃣ EXTRACCIÓN DE SUBTÍTULOS
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

        // 2️⃣ EXTRACCIÓN DE IFRAMES (Capa de protección)
        doc.select("iframe").forEach { iframe ->
            val src = iframe.attr("src")
            if (src.isNotBlank()) {
                callback(
                    newExtractorLink(
                        name = "Enlace Externo",
                        source = "SeriesKao",
                        url = src,
                        referer = mainUrl
                    )
                )
            }
        }

        // 3️⃣ EXTRACCIÓN DE MASTER.TXT (HLS Directo)
        val masterScript = doc.select("script").map { it.data() }.firstOrNull { it.contains("master.txt") }
        if (masterScript != null) {
            val masterUrl = Regex("""(https?://[^"'\s]+master\.txt)""").find(masterScript)?.value
            if (masterUrl != null) {
                callback(
                    newExtractorLink(
                        name = "HLS (Directo)",
                        source = "SeriesKao",
                        url = masterUrl,
                        referer = mainUrl,
                        isM3u8 = true
                    )
                )
            }
        }

        // 4️⃣ EXTRACCIÓN DE SERVIDORES DESDE SCRIPT (JSON)
        val scriptElement = doc.selectFirst("script:containsData(var servers =)")
        if (scriptElement != null) {
            val scriptData = scriptElement.data()
            val serversJson = scriptData.substringAfter("var servers = ").substringBefore(";").trim()
            try {
                val servers = parseJson<List<ServerData>>(serversJson)
                servers.forEach { server ->
                    val cleanUrl = server.url.replace("\\/", "/")
                    callback(
                        newExtractorLink(
                            name = server.title,
                            source = server.title,
                            url = cleanUrl,
                            referer = mainUrl,
                            isM3u8 = cleanUrl.contains(".m3u8", ignoreCase = true)
                        )
                    )
                }
            } catch (e: Exception) {
                // Error en el parseo de JSON, se ignora para no detener el plugin
            }
        }

        return true
    }

    // Modelo de datos para el JSON de la web
    data class ServerData(
        val title: String,
        val url: String
    )
}
