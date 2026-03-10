package click.repwrite.controller

import click.repwrite.ai.GeminiAiService
import click.repwrite.config.RepWriteProperties
import click.repwrite.model.Senator
import click.repwrite.service.WikiContentService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.Key
import software.amazon.awssdk.enhanced.dynamodb.TableSchema
import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.ssm.model.GetParameterRequest
import java.util.Base64

@RestController
@RequestMapping("/api")
class SenatorRestController(
        private val enhancedClient: DynamoDbEnhancedClient,
        private val wikiContentService: WikiContentService,
        private val geminiAiService: GeminiAiService,
        private val repWriteProperties: RepWriteProperties,
        private val ssmClient: SsmClient,
) {

    private val logger = LoggerFactory.getLogger(SenatorRestController::class.java)
    private val table = enhancedClient.table("SenatorsTable", TableSchema.fromBean(Senator::class.java))

    private val adminPassword by lazy {
        val configuredPassword = repWriteProperties.password
        if (configuredPassword != null && (configuredPassword != "user:pass" || System.getProperty("spring.profiles.active") != "prod")) {
            configuredPassword
        } else {
            try {
                logger.info("Admin password not found or default in prod, attempting to retrieve from AWS Parameter Store...")
                val response = ssmClient.getParameter(
                    GetParameterRequest.builder()
                        .name("/repwrite/admin-password")
                        .withDecryption(true)
                        .build()
                )
                response.parameter().value()
            } catch (e: Exception) {
                logger.warn("Failed to retrieve admin password from AWS Parameter Store: ${e.message}")
                configuredPassword ?: "pass"
            }
        }
    }

    @GetMapping("/senators")
    fun getAllSenators(): List<Senator> {
        return table.scan().items().toList().sortedBy { it.name }
    }

    @PostMapping("/senators/{id}")
    fun enrichSenatorBackground(
        @PathVariable id: String,
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authHeader: String?
    ): ResponseEntity<Senator> {
        if (!isAuthorized(authHeader)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        val key = Key.builder().partitionValue(id).build()
        val senator = table.getItem(key) ?: return ResponseEntity.notFound().build()

        val wikiUrl = senator.wikiUrl
        if (wikiUrl.isNullOrBlank()) {
            return ResponseEntity.badRequest().build()
        }

        val content = wikiContentService.fetchAndCleanWikiContent(wikiUrl)
        if (content.isNullOrBlank()) {
            return ResponseEntity.internalServerError().build()
        }

        val summary = geminiAiService.summarizeSenatorBackground(content, senator)
        if (summary.isNullOrBlank()) {
            return ResponseEntity.internalServerError().build()
        }

        senator.background = summary
        table.updateItem(senator)

        return ResponseEntity.ok(senator)
    }

    @GetMapping("/states/{state}/senators")
    fun getSenatorsByState(@PathVariable state: String): List<Senator> {
        val fullStateName = mapStateAbbreviation(state.uppercase())
        return table.scan().items()
            .filter { it.state?.equals(fullStateName, ignoreCase = true) == true }
            .sortedBy { it.name }
            .toList()
    }

    @GetMapping("/postcodes/{postcode}/senators")
    fun getSenatorsByPostcode(@PathVariable postcode: String): List<Senator> {
        val state = convertPostcodeToState(postcode) ?: return emptyList<Senator>()
        return getSenatorsByState(state)
    }

    private fun isAuthorized(authHeader: String?): Boolean {
        if (authHeader == null || !authHeader.startsWith("Basic ")) return false
        
        return try {
            val base64Credentials = authHeader.substring("Basic ".length).trim()
            val decodedBytes: ByteArray = Base64.getDecoder().decode(base64Credentials)
            val credentials = String(decodedBytes)
            credentials == "admin:$adminPassword"
        } catch (e: Exception) {
            false
        }
    }

    private fun mapStateAbbreviation(abbreviation: String): String {
        return when (abbreviation) {
            "NSW" -> "New South Wales"
            "VIC" -> "Victoria"
            "QLD" -> "Queensland"
            "SA" -> "South Australia"
            "WA" -> "Western Australia"
            "TAS" -> "Tasmania"
            "ACT" -> "Australian Capital Territory"
            "NT" -> "Northern Territory"
            else -> abbreviation
        }
    }

    private fun convertPostcodeToState(postcodeStr: String): String? {
        val postcode = postcodeStr.toIntOrNull() ?: return null
        return when {
            postcode in 800..999 -> "NT"
            postcode in 1000..1999 -> "NSW"
            postcode in 2000..2599 -> "NSW"
            postcode in 2600..2618 -> "ACT"
            postcode in 2619..2899 -> "NSW"
            postcode in 2900..2920 -> "ACT"
            postcode in 2921..2999 -> "NSW"
            postcode in 3000..3999 -> "VIC"
            postcode in 4000..4999 -> "QLD"
            postcode in 5000..5999 -> "SA"
            postcode in 6000..6999 -> "WA"
            postcode in 7000..7499 -> "TAS"
            postcode in 7800..7999 -> "TAS"
            postcode in 8000..8999 -> "VIC"
            postcode in 9000..9999 -> "QLD"
            else -> null
        }
    }
}
