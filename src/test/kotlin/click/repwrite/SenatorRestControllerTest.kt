package click.repwrite

import click.repwrite.ai.GeminiAiService
import click.repwrite.config.RepWriteProperties
import click.repwrite.controller.SenatorRestController
import click.repwrite.model.Senator
import click.repwrite.service.WikiContentService
import software.amazon.awssdk.services.ssm.SsmClient
import org.springframework.http.HttpHeaders
import java.util.Base64
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import software.amazon.awssdk.core.pagination.sync.SdkIterable
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable
import software.amazon.awssdk.enhanced.dynamodb.Key
import software.amazon.awssdk.enhanced.dynamodb.TableSchema
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable

class SenatorRestControllerTest {

    private lateinit var mockMvc: MockMvc
    private val enhancedClient = mockk<DynamoDbEnhancedClient>(relaxed = true)
    private val table = mockk<DynamoDbTable<Senator>>(relaxed = true)
    private val wikiContentService = mockk<WikiContentService>()
    private val geminiAiService = mockk<GeminiAiService>()
    private val repWriteProperties = RepWriteProperties()
    private val ssmClient = mockk<SsmClient>(relaxed = true)

    @BeforeEach
    fun setup() {
        every {
            enhancedClient.table("SenatorsTable", any<TableSchema<Senator>>())
        } returns table

        val mockPageIterable = mockk<PageIterable<Senator>>(relaxed = true)

        every { table.scan() } returns mockPageIterable
        every { mockPageIterable.items() } returns SdkIterable {
            listOf(
                Senator(id = "BRAG-1", name = "Andrew Bragg", email = "senator.bragg@aph.gov.au", birthYear = 1984, party = "Liberal", state = "New South Wales", firstYearInOffice = 2019, background = ""),
                Senator(id = "WONG-1", name = "Penny Wong", email = "senator.wong@aph.gov.au", birthYear = 1968, party = "Labor", state = "South Australia", firstYearInOffice = 2002, background = "")
            ).iterator() as MutableIterator<Senator?>
        }

        repWriteProperties.password = "pass"

        mockMvc = MockMvcBuilders.standaloneSetup(
            SenatorRestController(enhancedClient, wikiContentService, geminiAiService, repWriteProperties, ssmClient)
        ).build()
    }

    @Test
    fun `should enrich senator background with correct auth`() {
        val senator = Senator(id = "BRAG-1", name = "Andrew Bragg", wikiUrl = "http://wiki.com")
        every { table.getItem(any<Key>()) } returns senator
        every { wikiContentService.fetchAndCleanWikiContent(any()) } returns "Some content"
        every { geminiAiService.summarizeSenatorBackground(any(), any()) } returns "Summarized background"
        every { table.updateItem(any<Senator>()) } returns senator

        val authHeader = "Basic " + Base64.getEncoder().encodeToString("admin:pass".toByteArray())

        mockMvc.perform(post("/api/senators/BRAG-1")
            .header(HttpHeaders.AUTHORIZATION, authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.background").value("Summarized background"))
    }

    @Test
    fun `should return 401 when enriching senator background without auth`() {
        mockMvc.perform(post("/api/senators/BRAG-1"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `should return 401 when enriching senator background with incorrect auth`() {
        val authHeader = "Basic " + Base64.getEncoder().encodeToString("admin:wrong".toByteArray())
        mockMvc.perform(post("/api/senators/BRAG-1")
            .header(HttpHeaders.AUTHORIZATION, authHeader))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `should return all senators`() {
        mockMvc.perform(get("/api/senators"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].name").value("Andrew Bragg"))
            .andExpect(jsonPath("$[1].name").value("Penny Wong"))
    }

    @Test
    fun `should return senators by state abbreviation`() {
        mockMvc.perform(get("/api/states/NSW/senators"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].name").value("Andrew Bragg"))
            .andExpect(jsonPath("$.length()").value(1))
    }

    @Test
    fun `should return senators by postcode`() {
        // 2000 is NSW
        mockMvc.perform(get("/api/postcodes/2000/senators"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("Andrew Bragg"))
    }

    @Test
    fun `should return empty list for invalid postcode`() {
        mockMvc.perform(get("/api/postcodes/99999/senators"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }
}
