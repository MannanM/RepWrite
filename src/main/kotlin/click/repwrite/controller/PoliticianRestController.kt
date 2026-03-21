package click.repwrite.controller

import click.repwrite.ai.GeminiAiService
import click.repwrite.config.RepWriteProperties
import click.repwrite.model.Politician
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
class PoliticianRestController(
        private val enhancedClient: DynamoDbEnhancedClient,
        private val wikiContentService: WikiContentService,
        private val geminiAiService: GeminiAiService,
        private val repWriteProperties: RepWriteProperties,
        private val ssmClient: SsmClient,
) {

    private val logger = LoggerFactory.getLogger(PoliticianRestController::class.java)
    private val table = enhancedClient.table("PoliticiansTable", TableSchema.fromBean(Politician::class.java))

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

    @GetMapping("/politicians")
    fun getAllPoliticians(): List<Politician> {
        return table.scan().items().toList().sortedBy { it.name }
    }

    @PostMapping("/politicians/{id}")
    fun enrichPoliticianBackground(
        @PathVariable id: String,
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authHeader: String?
    ): ResponseEntity<Politician> {
        if (!isAuthorized(authHeader)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        val key = Key.builder().partitionValue(id).build()
        val politician = table.getItem(key) ?: return ResponseEntity.notFound().build()

        val wikiUrl = politician.wikiUrl
        if (wikiUrl.isNullOrBlank()) {
            return ResponseEntity.badRequest().build()
        }

        val content = wikiContentService.fetchAndCleanWikiContent(wikiUrl)
        if (content.isNullOrBlank()) {
            return ResponseEntity.internalServerError().build()
        }

        val summary = geminiAiService.summarizePoliticianBackground(content, politician)
        if (summary.isNullOrBlank()) {
            return ResponseEntity.internalServerError().build()
        }

        politician.background = summary
        table.updateItem(politician)

        return ResponseEntity.ok(politician)
    }

    @GetMapping("/states/{state}/senators")
    fun getPoliticiansByState(@PathVariable state: String): List<Politician> {
        val fullStateName = mapStateAbbreviation(state.uppercase())
        return table.scan().items()
            .filter { it.electorate?.equals(fullStateName, ignoreCase = true) == true }
            .toList()
            .sortedBy { it.name }
    }

    @GetMapping("/postcodes/{postcode}/politicians")
    fun getPoliticiansByPostcode(@PathVariable postcode: String): List<Politician> {
        val state = convertPostcodeToState(postcode) ?: return emptyList<Politician>()
        return getPoliticiansByState(state)
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
