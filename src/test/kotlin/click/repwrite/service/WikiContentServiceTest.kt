package click.repwrite.service

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.boot.restclient.RestTemplateBuilder
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate

class WikiContentServiceTest {

    private val restTemplate = mockk<RestTemplate>()
    private val restTemplateBuilder = mockk<RestTemplateBuilder> {
        every { build() } returns restTemplate
    }
    private val service = WikiContentService(restTemplateBuilder)

    @Test
    fun `should fetch and clean wiki content`() {
        val url = "https://en.wikipedia.org/wiki/Test_Senator"
        val html = """
            <html>
                <body>
                    <div id="mw-content-text">
                        <p>Senator Test is a politician.</p>
                        <script>alert('bad');</script>
                        <div class="mw-editsection">Edit</div>
                        <p>They stand for fairness.</p>
                    </div>
                </body>
            </html>
        """.trimIndent()

        val response = mockk<ResponseEntity<String>>()
        every { response.body } returns html

        every {
            restTemplate.exchange(
                url,
                HttpMethod.GET,
                any<HttpEntity<Void>>(),
                String::class.java
            )
        } returns response

        val result = service.fetchAndCleanWikiContent(url)

        result shouldBe "Senator Test is a politician. They stand for fairness."
    }

    @Test
    fun `should return null on error`() {
        val url = "https://en.wikipedia.org/wiki/Fail"
        every {
            restTemplate.exchange(
                url,
                HttpMethod.GET,
                any<HttpEntity<Void>>(),
                String::class.java
            )
        } throws RuntimeException("Network error")

        val result = service.fetchAndCleanWikiContent(url)

        result shouldBe null
    }
}
