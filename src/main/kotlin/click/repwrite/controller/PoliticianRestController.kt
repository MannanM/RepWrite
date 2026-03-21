package click.repwrite.controller

import click.repwrite.ai.GeminiAiService
import click.repwrite.config.RepWriteProperties
import click.repwrite.model.Politician
import click.repwrite.service.AphContentService
import click.repwrite.service.ElectorateMap
import click.repwrite.service.WikiContentService
import java.util.Base64
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

@RestController
@RequestMapping("/api")
class PoliticianRestController(
    private val enhancedClient: DynamoDbEnhancedClient,
    private val wikiContentService: WikiContentService,
    private val geminiAiService: GeminiAiService,
    private val repWriteProperties: RepWriteProperties,
    private val ssmClient: SsmClient,
    private val aphContentService: AphContentService,
) {

    private val logger = LoggerFactory.getLogger(PoliticianRestController::class.java)
    private val table =
        enhancedClient.table("PoliticiansTable", TableSchema.fromBean(Politician::class.java))

    private val adminPassword by lazy {
        val configuredPassword = repWriteProperties.password
        if (configuredPassword != null &&
            (configuredPassword != "user:pass" ||
                    System.getProperty("spring.profiles.active") != "prod")
        ) {
            configuredPassword
        } else {
            try {
                logger.info(
                    "Admin password not found or default in prod, attempting to retrieve from AWS Parameter Store..."
                )
                val response =
                    ssmClient.getParameter(
                        GetParameterRequest.builder()
                            .name("/repwrite/admin-password")
                            .withDecryption(true)
                            .build()
                    )
                response.parameter().value()
            } catch (e: Exception) {
                logger.warn(
                    "Failed to retrieve admin password from AWS Parameter Store: ${e.message}"
                )
                configuredPassword ?: "pass"
            }
        }
    }

    @GetMapping("/politicians")
    fun getAllPoliticians(): List<Politician> {
        return table.scan().items().toList().sortedWith(compareBy({ it.type }, { it.name }))
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

    @PostMapping("/senators")
    fun refreshSenators(
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authHeader: String?
    ): ResponseEntity<String> {
        if (!isAuthorized(authHeader)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        val existing = table.scan().items().toList().filter { it.type == "Senator" }
        val fetched = aphContentService.getSenators()

        return refresh(fetched, existing)
    }

    @PostMapping("/representatives")
    fun refreshRepresentatives(
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authHeader: String?
    ): ResponseEntity<String> {
        if (!isAuthorized(authHeader)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        val existing = table.scan().items().toList().filter { it.type == "Representative" }
        val fetched = aphContentService.getRepresentative()

        return refresh(fetched, existing)
    }

    private fun refresh(
        aphSenators: Map<String, String>,
        allPoliticians: List<Politician>,
    ): ResponseEntity<String> {
        var updatedCount = 0
        var addedCount = 0
        val seenIds = mutableSetOf<String>()

        aphSenators.forEach { (name, url) ->
            val fetched = aphContentService.fetchPoliticianDetails(url)
            if (fetched != null) {
                // Use cleaned name from fetched object for comparison
                val existing = allPoliticians.find { it.id == fetched.id }

                if (existing != null) {
                    // Update non-null details from fetched into existing
                    if (fetched.party != null) existing.party = fetched.party
                    if (fetched.email != null) existing.email = fetched.email
                    if (fetched.phone != null) existing.phone = fetched.phone
                    if (fetched.handle != null) existing.handle = fetched.handle
                    if (fetched.electorate != null) existing.electorate = fetched.electorate
                    if (fetched.type != null) existing.type = fetched.type

                    table.updateItem(existing)
                    fetched.id?.let { seenIds.add(it) }
                    updatedCount++
                } else {
                    logger.warn("No existing record found for politician: ${fetched.name}, adding as new record.")
                    table.putItem(fetched)
                    fetched.id?.let { seenIds.add(it) }
                    addedCount++
                }
            } else {
                logger.warn("Failed to fetch details for politician from URL: $url")
            }
        }

        allPoliticians.forEach { existing ->
            if (existing.id !in seenIds) {
                logger.warn("Existing politician no longer returned in fetch, removing inactive: ${existing.name} (${existing.id})")
                // should add a flag for inactive rather than deleting, but for now just delete
                table.deleteItem(existing)
            }
        }

        return ResponseEntity.ok("Processed senators. Updated/Re-IDed: $updatedCount, Added: $addedCount")
    }

    @GetMapping("/states/{state}/senators")
    fun getPoliticiansByState(@PathVariable state: String): List<Politician> {
        val fullStateName = mapStateAbbreviation(state.uppercase())
        return table.scan()
            .items()
            .filter { it.electorate?.equals(fullStateName, ignoreCase = true) == true }
            .toList()
            .sortedBy { it.name }
    }

    @GetMapping("/postcodes/{postcode}/politicians")
    fun getPoliticiansByPostcode(@PathVariable postcode: String): List<Politician> {
        val state = convertPostcodeToState(postcode) ?: return emptyList<Politician>()
        val fullStateName = mapStateAbbreviation(state)
        val electorate = ElectorateMap.postcodeToElectorate[postcode] ?: ""

        return table.scan()
            .items()
            .filter {
                it.electorate?.equals(fullStateName, ignoreCase = true) == true ||
                        it.electorate?.equals(electorate, ignoreCase = true) == true
            }
            .toList()
            .sortedWith(compareBy({ it.type }, { it.name }))
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
        return when (postcode) {
            in 800..999 -> "NT"
            in 1000..1999 -> "NSW"
            in 2000..2599 -> "NSW"
            in 2600..2618 -> "ACT"
            in 2619..2899 -> "NSW"
            in 2900..2920 -> "ACT"
            in 2921..2999 -> "NSW"
            in 3000..3999 -> "VIC"
            in 4000..4999 -> "QLD"
            in 5000..5999 -> "SA"
            in 6000..6999 -> "WA"
            in 7000..7499 -> "TAS"
            in 7800..7999 -> "TAS"
            in 8000..8999 -> "VIC"
            in 9000..9999 -> "QLD"
            else -> null
        }
    }
}
