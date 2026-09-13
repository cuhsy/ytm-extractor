package youtube

import helpers.parseDateTime
import listenbrainz.ListenBrainzAdditionalInfo
import listenbrainz.ListenBrainzPayload
import listenbrainz.ListenBrainzTrackMetadata
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.time.Instant

object YTMHTMLParser {
    data class YouTubeMusicData(
        val title: String,
        val videoHref: String,
        val channel: String,
        val channelHref: String,
        val dateTime: Instant,
    ) {
        fun toListenBrainz() = ListenBrainzPayload(
            listenedAt = dateTime,
            trackMetadata = ListenBrainzTrackMetadata(
                artistName = channel,
                trackName = title,
                additionalInfo = ListenBrainzAdditionalInfo(
                    originUrl = videoHref,
                )
            )
        )
    }
    fun parse(htmlContent: String): List<YouTubeMusicData> {
        val document: Document = Jsoup.parse(htmlContent)
        val youtubeMusicDataList = mutableListOf<YouTubeMusicData>()

        val elements = document.select("div.outer-cell.mdl-cell.mdl-cell--12-col.mdl-shadow--2dp")
        for (element in elements) {
            val titleElement = element.selectFirst("p.mdl-typography--title")
            if (titleElement == null || !titleElement.text().contains("YouTube Music")) continue

            val contentCells = element.select("div.content-cell.mdl-cell--6-col.mdl-typography--body-1")
            val topElement = contentCells.firstOrNull { cell ->
                cell.selectFirst("a[href*=watch]") != null
            } ?: continue

            val anchors = topElement.select("a[href]")

            val videoElement = anchors.firstOrNull {
                it.attr("href").contains("watch?v=") && it.text().isNotBlank()
            } ?: continue

            val channelElement = anchors.firstOrNull {
                val href = it.attr("href")
                (href.contains("/channel/") || href.contains("/@") || href.contains("/user/")) &&
                    it.text().isNotBlank() && it != videoElement
            } ?: continue

            val rawText = topElement.ownText().trim()
            val cleaned = rawText.removePrefix("Watched ").trim()
            val dateTime = runCatching { parseDateTime(cleaned) }.getOrNull() ?: continue

            val videoTitle = videoElement.text().trim()
            val channelName = channelElement.text().trim().removeSuffix("- Topic").trim()

            if (videoTitle.isEmpty() || channelName.isEmpty() || videoTitle.startsWith("http")) {
                println("Skipping: title='$videoTitle' channel='$channelName'")
                continue
            }

            youtubeMusicDataList.add(
                YouTubeMusicData(
                    title = videoTitle,
                    videoHref = videoElement.attr("href").trim(),
                    channel = channelName,
                    channelHref = channelElement.attr("href").trim(),
                    dateTime = dateTime
                )
            )
        }
        return youtubeMusicDataList
    }
}
