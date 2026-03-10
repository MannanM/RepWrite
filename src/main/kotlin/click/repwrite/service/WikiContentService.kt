package click.repwrite.service

import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import org.springframework.boot.restclient.RestTemplateBuilder
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.net.URI

@Service
class WikiContentService(restTemplateBuilder: RestTemplateBuilder) {
    private val logger = LoggerFactory.getLogger(WikiContentService::class.java)
    private val restTemplate: RestTemplate = restTemplateBuilder.build()
    private val headers = HttpHeaders().apply {
        add(
            HttpHeaders.USER_AGENT,
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
        )
    }

    fun fetchAndCleanWikiContent(url: String): String? {
        return try {
            logger.info("Fetching content from Wikipedia: $url")
            val html = restTemplate
                .exchange(URI.create(url), HttpMethod.GET, HttpEntity<Void>(headers), String::class.java)
                .body
            if (html != null) {
                val doc = Jsoup.parse(html)
                // Remove unwanted elements like scripts, styles, and navigation
                doc.select("script, style, .mw-editsection, .navbox, .reflist").remove()
                // Extract text from the main content div if it exists, otherwise use body
                val content = doc.select("#mw-content-text").first() ?: doc.body()
                content.text()
            } else {
                logger.warn("Received empty HTML from $url")
                null
            }
        } catch (e: Exception) {
            logger.error("Failed to fetch or parse Wiki content from $url: ${e.message}")
            null
        }
    }
}
