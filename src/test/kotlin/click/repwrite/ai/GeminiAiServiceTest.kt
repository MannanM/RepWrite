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
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClientBuilder
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse
import tools.jackson.databind.ObjectMapper

class GeminiAiServiceTest {

    private val restTemplateBuilder = mockk<RestTemplateBuilder>(relaxed = true)
    private val objectMapper = mockk<ObjectMapper>()
    private val secretsManagerClient = mockk<SecretsManagerClient>()

    @BeforeEach
    fun setUp() {
        mockkStatic(SecretsManagerClient::class)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `should use apiKey from properties if present`() {
        val properties = GeminiProperties(url = "http://test.com", apiKey = "prop-key")

        val service =
                GeminiAiService(restTemplateBuilder, properties, secretsManagerClient, objectMapper)

        // Use reflection to check private uri field
        val uriField = service.javaClass.getDeclaredField("uri")
        uriField.isAccessible = true
        val uri = uriField.get(service) as String

        uri shouldBe "http://test.com?key=prop-key"
    }

    @Test
    fun `should fallback to AWS Secrets Manager if apiKey is null`() {
        val properties = GeminiProperties(url = "http://test.com", apiKey = null)

        val mockClient = mockk<SecretsManagerClient>()
        val mockBuilder = mockk<SecretsManagerClientBuilder>()
        val mockResponse = mockk<GetSecretValueResponse>()

        every { SecretsManagerClient.builder() } returns mockBuilder
        every { mockBuilder.region(any()) } returns mockBuilder
        every { mockBuilder.build() } returns mockClient
        every { mockClient.getSecretValue(any<GetSecretValueRequest>()) } returns mockResponse
        every { mockResponse.secretString() } returns "aws-key"

        val service = GeminiAiService(restTemplateBuilder, properties, mockClient, objectMapper)

        val uriField = service.javaClass.getDeclaredField("uri")
        uriField.isAccessible = true
        val uri = uriField.get(service) as String

        uri shouldBe "http://test.com?key=aws-key"
    }
}
