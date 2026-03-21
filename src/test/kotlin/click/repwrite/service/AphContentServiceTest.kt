package click.repwrite.service

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.boot.restclient.RestTemplateBuilder
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import java.net.URI

class AphContentServiceTest {

    private val restTemplate = mockk<RestTemplate>()
    private val restTemplateBuilder = mockk<RestTemplateBuilder> {
        every { build() } returns restTemplate
    }
    private val service = AphContentService(restTemplateBuilder)

    @Test
    fun `should scrape parliamentarians from APH search results`() {
        val url = "https://www.aph.gov.au/Senators_and_Members/Parliamentarian_Search_Results?page=1&q=&mem=1&ps=100"
        val html = """
            <div class="search-filter-results">
                <div class="row">
                    <h4 class="title">
                        <a href="/Senators_and_Members/Parliamentarian?MPID=316915">Mr Basem Abdo MP</a>
                    </h4>
                </div>
                <div class="row">
                    <h4 class="title">
                        <a href="/Senators_and_Members/Parliamentarian?MPID=R36">Hon Anthony Albanese MP</a>
                    </h4>
                </div>
            </div>
            <div class="results-pagination">
                <li class="next inactive"><span>Next</span></li>
            </div>
        """.trimIndent()

        val response = mockk<ResponseEntity<String>>()
        every { response.body } returns html

        every {
            restTemplate.exchange(
                URI.create(url),
                HttpMethod.GET,
                any<HttpEntity<Void>>(),
                String::class.java
            )
        } returns response

        val result = service.getRepresentative()

        result.size shouldBe 2
        result["Basem Abdo"] shouldBe "https://www.aph.gov.au/Senators_and_Members/Parliamentarian?MPID=316915"
        result["Anthony Albanese"] shouldBe "https://www.aph.gov.au/Senators_and_Members/Parliamentarian?MPID=R36"
    }

    @Test
    fun `should handle pagination`() {
        val url1 = "https://www.aph.gov.au/Senators_and_Members/Parliamentarian_Search_Results?page=1&q=&sen=1&ps=100"
        val url2 = "https://www.aph.gov.au/Senators_and_Members/Parliamentarian_Search_Results?page=2&q=&sen=1&ps=100"

        val html1 = """
            <div class="search-filter-results">
                <h4 class="title"><a href="/p1">Senator One</a></h4>
            </div>
            <li class="next"><a href="?page=2">Next</a></li>
        """.trimIndent()

        val html2 = """
            <div class="search-filter-results">
                <h4 class="title"><a href="/p2">Senator Two</a></h4>
            </div>
            <li class="next inactive"><span>Next</span></li>
        """.trimIndent()

        val resp1 = mockk<ResponseEntity<String>>()
        every { resp1.body } returns html1
        val resp2 = mockk<ResponseEntity<String>>()
        every { resp2.body } returns html2

        every {
            restTemplate.exchange(URI.create(url1), HttpMethod.GET, any(), String::class.java)
        } returns resp1

        every {
            restTemplate.exchange(URI.create(url2), HttpMethod.GET, any(), String::class.java)
        } returns resp2

        val result = service.getSenators()

        result.size shouldBe 2
        result["One"] shouldBe "https://www.aph.gov.au/p1"
        result["Two"] shouldBe "https://www.aph.gov.au/p2"
    }

    @Test
    fun `should fetch politician details`() {
        val url = "https://www.aph.gov.au/Senators_and_Members/Parliamentarian?MPID=298839"
        val html = """
            <div class="profile">
                <h1>Senator Penny Allman-Payne</h1>
                <div class="medium-7">
                    <h3>Senator for QLD</h3>
                    <dl class="dl--inline__result">
                        <dt>Party</dt><dd>Australian Greens</dd>
                        <dt>Chamber</dt><dd>Senate</dd>
                    </dl>
                </div>
            </div>
            <section id="t1-content-panel">
                <div class="medium-6">
                    <h3>Electorate Office </h3>
                    <strong>(Principal Office)</strong>
                    <dl class="dl--inline">
                        <dt>Telephone:</dt><dd><a href="tel:+61749720380">(07) 4972 0380</a></dd>
                    </dl>
                </div>
                <div class="medium-6">
                    <h3>Parliament Office</h3>
                    <dl class="dl--inline">
                        <dt>Telephone:</dt><dd><a href="tel:+61262773410">(02) 6277 3410</a></dd>
                    </dl>
                </div>
            </section>
            <section id="t2-content-panel">
                <dt>Email</dt>
                <dd><a href="mailto:senator.allman-payne@aph.gov.au">senator.allman-payne@aph.gov.au</a></dd>
                <dt>Social media</dt>
                <dd>
                    <a href="https://twitter.com/senatorpennyqld"><i class="fa-twitter"></i></a>
                </dd>
            </section>
        """.trimIndent()

        val response = mockk<ResponseEntity<String>>()
        every { response.body } returns html
        every {
            restTemplate.exchange(URI.create(url), HttpMethod.GET, any(), String::class.java)
        } returns response

        val politician = service.fetchPoliticianDetails(url)

        politician shouldNotBe null
        politician?.name shouldBe "Penny Allman-Payne"
        politician?.id shouldBe "298839"
        politician?.type shouldBe "Senator"
        politician?.electorate shouldBe "Queensland"
        politician?.email shouldBe "senator.allman-payne@aph.gov.au"
        politician?.phone shouldBe "0749720380"
        politician?.handle shouldBe "@senatorpennyqld"
        politician?.party shouldBe "Greens"
    }

    @Test
    fun `should return empty map on fetch failure`() {
        val url = "https://www.aph.gov.au/Senators_and_Members/Parliamentarian_Search_Results?page=1&q=&mem=1&ps=100"
        every {
            restTemplate.exchange(URI.create(url), HttpMethod.GET, any(), String::class.java)
        } throws RuntimeException("Network Error")

        val result = service.getRepresentative()

        result.isEmpty() shouldBe true
    }
}
