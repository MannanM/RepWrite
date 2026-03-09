package click.repwrite

import click.repwrite.controller.CauseRestController
import click.repwrite.ai.GeminiAiService
import click.repwrite.model.Cause
import click.repwrite.model.Senator
import click.repwrite.model.CachedAppeal
import click.repwrite.model.EmailRequest
import click.repwrite.model.EmailResponse
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
import software.amazon.awssdk.enhanced.dynamodb.TableSchema
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable
import java.time.LocalDate

class CauseRestControllerTest {

    private lateinit var mockMvc: MockMvc
    private val enhancedClient = mockk<DynamoDbEnhancedClient>(relaxed = true)
    private val causesTable = mockk<DynamoDbTable<Cause>>(relaxed = true)
    private val senatorsTable = mockk<DynamoDbTable<Senator>>(relaxed = true)
    private val cachedAppealsTable = mockk<DynamoDbTable<CachedAppeal>>(relaxed = true)
    private val geminiAiService = mockk<GeminiAiService>(relaxed = true)

    private val today = LocalDate.now()
    private val activeCause = Cause("1", "Active Cause", today.plusDays(10).toString(), "Content 1")
    private val expiredCause = Cause("2", "Expired Cause", today.minusDays(10).toString(), "Content 2")

    @BeforeEach
    fun setup() {
        every { enhancedClient.table("CausesTable", any<TableSchema<Cause>>()) } returns causesTable
        every { enhancedClient.table("SenatorsTable", any<TableSchema<Senator>>()) } returns senatorsTable
        every { enhancedClient.table("CachedAppealsTable", any<TableSchema<CachedAppeal>>()) } returns cachedAppealsTable

        val mockCausePageIterable = mockk<PageIterable<Cause>>(relaxed = true)
        every { causesTable.scan() } returns mockCausePageIterable
        every { mockCausePageIterable.items() } returns SdkIterable {
            listOf(activeCause, expiredCause).iterator() as MutableIterator<Cause?>
        }

        every { causesTable.getItem(any<software.amazon.awssdk.enhanced.dynamodb.Key>()) } answers {
            val key = it.invocation.args[0] as software.amazon.awssdk.enhanced.dynamodb.Key
            if (key.partitionKeyValue().s() == "1") activeCause else null
        }

        every { cachedAppealsTable.getItem(any<software.amazon.awssdk.enhanced.dynamodb.Key>()) } returns null

        mockMvc = MockMvcBuilders.standaloneSetup(CauseRestController(enhancedClient, geminiAiService)).build()
    }

    @Test
    fun `should return active causes`() {
        mockMvc.perform(get("/api/causes?status=active"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("Active Cause"))
    }

    @Test
    fun `should return cause details`() {
        mockMvc.perform(get("/api/causes/1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Active Cause"))
    }

    @Test
    fun `should return null for non-existent cause`() {
        mockMvc.perform(get("/api/causes/999"))
            .andExpect(status().isOk)
            .andExpect(content().string(""))
    }

    @Test
    fun `should generate email via AI`() {
        val senatorId = "POC-123"
        val mockSenator = Senator(
            id = senatorId,
            name = "Senator Pocock",
            email = "senator.pocock@aph.gov.au",
            birthYear = 1970,
            party = "Independent",
            state = "ACT",
            firstYearInOffice = 2022,
            background = ""
        )
        
        every { senatorsTable.getItem(any<software.amazon.awssdk.enhanced.dynamodb.Key>()) } answers {
            val key = it.invocation.args[0] as software.amazon.awssdk.enhanced.dynamodb.Key
            if (key.partitionKeyValue().s() == senatorId) mockSenator else null
        }

        val expectedResponse = EmailResponse("senator.pocock@aph.gov.au", "AI Subject", "AI Body")
        every { geminiAiService.generateEmail(any(), any(), any()) } returns expectedResponse

        val jsonRequest = """
            {
                "causeId": "1",
                "senatorId": "$senatorId",
                "name": "John Doe",
                "gender": "Male",
                "age": 30,
                "importance": "High"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/causes/1/generate-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.subject").value("AI Subject"))
            .andExpect(jsonPath("$.body").value("AI Body"))
    }
}
