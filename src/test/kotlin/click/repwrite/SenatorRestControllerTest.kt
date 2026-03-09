package click.repwrite

import click.repwrite.controller.SenatorRestController
import click.repwrite.model.Senator
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import software.amazon.awssdk.core.pagination.sync.SdkIterable
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable
import software.amazon.awssdk.enhanced.dynamodb.TableSchema

class SenatorRestControllerTest {

    private lateinit var mockMvc: MockMvc
    private val enhancedClient = mockk<DynamoDbEnhancedClient>(relaxed = true)
    private val table = mockk<DynamoDbTable<Senator>>(relaxed = true)

    @BeforeEach
    fun setup() {
        every {
            enhancedClient.table("SenatorsTable", any<TableSchema<Senator>>())
        } returns table

        val mockPageIterable =
            mockk<software.amazon.awssdk.enhanced.dynamodb.model.PageIterable<Senator>>(relaxed = true)

        every { table.scan() } returns mockPageIterable
        every { mockPageIterable.items() } returns SdkIterable {
            listOf(
                Senator(id = "WONG-1", name = "Penny Wong", email = "senator.wong@aph.gov.au", birthYear = 1968, party = "Labor", state = "South Australia", firstYearInOffice = 2002, background = ""),
                Senator(id = "BRAG-1", name = "Andrew Bragg", email = "senator.bragg@aph.gov.au", birthYear = 1984, party = "Liberal", state = "New South Wales", firstYearInOffice = 2019, background = "")
            ).iterator() as MutableIterator<Senator?>
        }

        mockMvc = MockMvcBuilders.standaloneSetup(SenatorRestController(enhancedClient)).build()
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
