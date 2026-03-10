package click.repwrite.ai

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.restclient.RestTemplateBuilder
import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.ssm.model.GetParameterRequest
import software.amazon.awssdk.services.ssm.model.GetParameterResponse
import software.amazon.awssdk.services.ssm.model.Parameter
import click.repwrite.model.Senator
import tools.jackson.databind.ObjectMapper

class GeminiAiServiceTest {

    private val restTemplateBuilder = mockk<RestTemplateBuilder>(relaxed = true)
    private val ssmClient = mockk<SsmClient>(relaxed = true)
    private val objectMapper = mockk<ObjectMapper>(relaxed = true)

    @BeforeEach
    fun setUp() {
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `should use apiKey from properties if present`() {
        val properties = GeminiProperties(url = "http://test.com", apiKey = null)
        val response = GetParameterResponse.builder()
            .parameter(Parameter.builder().value("secret-key").build())
            .build()
        every { ssmClient.getParameter(any<GetParameterRequest>()) } returns response
        val service = GeminiAiService(restTemplateBuilder, properties, ssmClient, objectMapper)

        // Use reflection to check private uri field
        val uriField = service.javaClass.getDeclaredField("uri")
        uriField.isAccessible = true
        val uri = uriField.get(service) as String

        uri shouldBe "http://test.com?key=secret-key"
    }

    @Test
    fun `should fallback to AWS Parameter Store if apiKey is null`() {
        val properties = GeminiProperties(url = "http://test.com", apiKey = null)

        val response = GetParameterResponse.builder()
            .parameter(Parameter.builder().value("aws-key").build())
            .build()
        every { ssmClient.getParameter(any<GetParameterRequest>()) } returns response

        val service = GeminiAiService(restTemplateBuilder, properties, ssmClient, objectMapper)

        val uriField = service.javaClass.getDeclaredField("uri")
        uriField.isAccessible = true
        val uri = uriField.get(service) as String

        uri shouldBe "http://test.com?key=aws-key"
    }

    @Test
    fun `should call Gemini to summarize senator background`() {
        val properties = GeminiProperties(url = "http://test.com", apiKey = null)
        every { ssmClient.getParameter(any<GetParameterRequest>()) } throws RuntimeException("AWS Error")
        val service =
                GeminiAiService(restTemplateBuilder, properties, ssmClient, objectMapper)
        val senator =
                Senator(
                        id = "1",
                        name = "Test Senator",
                        party = "Labor",
                        state = "NSW",
                        birthYear = 1970,
                        firstYearInOffice = 2010
                )
        val wikiContent = "Some long wikipedia text about the senator."

        // Mock RestTemplate postForObject
        val mockResponse = mockk<GeminiApiResponse>()
        val mockCandidate = mockk<Candidate>()
        val mockContent = mockk<Content>()
        val mockPart = mockk<Part>()

        every {
            restTemplateBuilder.build()
                    .postForObject(any<String>(), any(), GeminiApiResponse::class.java)
        } returns mockResponse
        every { mockResponse.candidates } returns listOf(mockCandidate)
        every { mockCandidate.content } returns mockContent
        every { mockContent.parts } returns listOf(mockPart)
        every { mockPart.text } returns "```json\nThis is a summary.\n```"
        every { objectMapper.readValue("This is a summary.", String::class.java) } returns
                "This is a summary."

        val summary = service.summarizeSenatorBackground(wikiContent, senator)

        summary shouldBe "This is a summary."
    }
}
