package click.repwrite.controller

import click.repwrite.ai.GeminiAiService
import click.repwrite.model.CachedAppeal
import click.repwrite.model.Cause
import click.repwrite.model.EmailRequest
import click.repwrite.model.EmailResponse
import click.repwrite.model.Senator
import java.security.MessageDigest
import java.time.LocalDate
import org.springframework.web.bind.annotation.*
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.TableSchema

@RestController
@RequestMapping("/api/causes")
class CauseRestController(
        enhancedClient: DynamoDbEnhancedClient,
        private val geminiAiService: GeminiAiService
) {

    private val causesTable =
            enhancedClient.table("CausesTable", TableSchema.fromBean(Cause::class.java))
    private val senatorsTable =
            enhancedClient.table("SenatorsTable", TableSchema.fromBean(Senator::class.java))

    private val cachedAppealsTable =
            enhancedClient.table(
                    "CachedAppealsTable",
                    TableSchema.fromBean(CachedAppeal::class.java)
            )

    @GetMapping
    fun getCauses(@RequestParam(required = false) status: String?): List<Map<String, String?>> {
        val allCauses = causesTable.scan().items().toList()

        return if (status == "active") {
            val today = LocalDate.now().toString()
            allCauses.filter { it.expiryDate != null && it.expiryDate!! >= today }.map {
                mapOf("id" to it.id, "name" to it.name)
            }
        } else {
            allCauses.map { mapOf("id" to it.id, "name" to it.name) }
        }
    }

    @GetMapping("/{id}")
    fun getCause(@PathVariable id: String): Cause? {
        return causesTable.getItem(
                software.amazon.awssdk.enhanced.dynamodb.Key.builder().partitionValue(id).build()
        )
    }

    @PostMapping("/{id}/generate-email")
    fun generateEmail(@PathVariable id: String, @RequestBody request: EmailRequest): EmailResponse {
        val cause = getCause(id) ?: throw IllegalArgumentException("Cause not found")
        val senator =
                senatorsTable.getItem(
                        software.amazon.awssdk.enhanced.dynamodb.Key.builder()
                                .partitionValue(request.senatorId)
                                .build()
                )
                        ?: throw IllegalArgumentException("Senator not found")

        val infoHash = request.generateInfoHash()
        val sortKey = "${request.senatorId}#$infoHash"

        // 1. Try to find exact match in cache
        val cached =
                cachedAppealsTable.getItem(
                        software.amazon.awssdk.enhanced.dynamodb.Key.builder()
                                .partitionValue(id)
                                .sortValue(sortKey)
                                .build()
                )
        if (cached != null) {
            return EmailResponse(
                    toAddress = senator.email
                                    ?: "senator.${senator.name?.replace(" ", ".")?.lowercase()}@aph.gov.au",
                    subject = cached.subject ?: "",
                    body = cached.body ?: "",
                    tweet = cached.tweet,
                    phoneLine = cached.phoneLine
            )
        }

        return try {
            val aiResponse = geminiAiService.generateEmail(cause, senator, request)
            if (aiResponse != null) {
                // 2. Persist to cache
                val cachedAppeal =
                        CachedAppeal(
                                causeId = id,
                                senatorIdInfoHash = sortKey,
                                senatorId = request.senatorId,
                                infoHash = infoHash,
                                subject = aiResponse.subject,
                                body = aiResponse.body,
                                tweet = aiResponse.tweet,
                                phoneLine = aiResponse.phoneLine,
                                name = request.name,
                                gender = request.gender,
                                age = request.age,
                                occupation = request.occupation,
                                importance = request.importance
                        )
                cachedAppealsTable.putItem(cachedAppeal)
                aiResponse
            } else {
                throw IllegalStateException("AI returned null response")
            }
        } catch (e: Exception) {
            // 3. Fallback to "anonymous" cache if AI fails and this wasn't already an anonymous request
            if (infoHash != "anonymous") {
                val anonymousSortKey = "${request.senatorId}#anonymous"
                val fallback =
                        cachedAppealsTable.getItem(
                                software.amazon.awssdk.enhanced.dynamodb.Key.builder()
                                        .partitionValue(id)
                                        .sortValue(anonymousSortKey)
                                        .build()
                        )
                if (fallback != null) {
                    return EmailResponse(
                            toAddress = senator.email
                                            ?: "senator.${senator.name?.replace(" ", ".")?.lowercase()}@aph.gov.au",
                            subject = fallback.subject ?: "",
                            body = fallback.body ?: "",
                            tweet = fallback.tweet,
                            phoneLine = fallback.phoneLine
                    )
                }
            }
            throw IllegalStateException(
                    "Failed to generate email via AI and no fallback available",
                    e
            )
        }
    }

    private fun EmailRequest.generateInfoHash(): String {
        if (isAnonymous()) return "anonymous"
        
        val input = "${name}|${gender}|${age}|${occupation}|${importance}"
        return MessageDigest.getInstance("SHA-256")
                .digest(input.toByteArray())
                .joinToString("") { "%02x".format(it) }
    }

    private fun EmailRequest.isAnonymous(): Boolean {
        return name.isNullOrBlank() &&
                gender.isNullOrBlank() &&
                age == null &&
                occupation.isNullOrBlank() &&
                importance.isNullOrBlank()
    }
}
