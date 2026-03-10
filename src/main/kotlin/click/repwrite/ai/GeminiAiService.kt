package click.repwrite.ai

import click.repwrite.model.Cause
import click.repwrite.model.EmailRequest
import click.repwrite.model.EmailResponse
import click.repwrite.model.Senator
import java.io.IOException
import org.slf4j.LoggerFactory
import org.springframework.boot.restclient.RestTemplateBuilder
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.ssm.model.GetParameterRequest
import tools.jackson.databind.ObjectMapper

@Service
class GeminiAiService(
        restTemplateBuilder: RestTemplateBuilder,
        geminiProperties: GeminiProperties,
        ssmClient: SsmClient,
        private val objectMapper: ObjectMapper,
) {

        private val logger = LoggerFactory.getLogger(GeminiAiService::class.java)
        private val restTemplate: RestTemplate = restTemplateBuilder.build()
        private val headers = HttpHeaders()
        private val uri: String

        init {
                headers.contentType = MediaType.APPLICATION_JSON

                val apiKey =
                        geminiProperties.apiKey
                                ?: try {
                                        logger.info(
                                                "Gemini API key not found in properties, attempting to retrieve from AWS Parameter Store..."
                                        )
                                        ssmClient
                                                .getParameter(
                                                        GetParameterRequest.builder()
                                                                .name("/gemini/api-key")
                                                                .withDecryption(true)
                                                                .build()
                                                )
                                                .parameter()
                                                .value()
                                } catch (e: Exception) {
                                        logger.warn(
                                                "Failed to retrieve Gemini API key from AWS Parameter Store: ${e.message}"
                                        )
                                        null
                                }

                uri =
                        UriComponentsBuilder.fromUriString(geminiProperties.url)
                                .queryParam("key", apiKey ?: "")
                                .toUriString()
        }

        fun generateEmail(
                cause: Cause,
                senator: Senator,
                userDetails: EmailRequest
        ): EmailResponse? {
                val currentYear = java.time.LocalDate.now().year
                val prompt =
                        """
            You are a passionate Australian citizen writing to your Senator to advocate for a political cause.
            
            **Context:**
            - **Senator:** ${senator.name} (Party: ${senator.party}, Age: ${senator.birthYear?.let { currentYear - it } ?: "Unknown"}, Years in Office: ${senator.firstYearInOffice?.let { currentYear - it } ?: "Unknown"})
            ${senator.background?.takeIf { it.isNotBlank() }?.let { "- **Senator Background:** $it" } ?: ""}
            - **Political Cause:** ${cause.name}
            - **Cause Detail:** ${cause.content}
            
            **User Details (The writer):**
            ${userDetails.name?.let { "- **Name:** $it" } ?: "- **Name:** A concerned citizen"}
            ${userDetails.gender?.let { "- **Gender:** $it" } ?: ""}
            ${userDetails.age?.let { "- **Age:** $it" } ?: ""}
            ${userDetails.occupation?.let { "- **Occupation:** $it" } ?: ""}
            - **Why this is important to them:** ${userDetails.importance ?: "The user is deeply concerned about the future of the country and the impact of this issue on their community."}
            
            **Task:**
            Write a highly empassioned, respectful, and persuasive email to the Senator, a short tweet, and a one-sentence phone script.
            - **Email Tone:** Human, authentic, and urgent. Avoid sounding like a template or a robot.
            - **Email Length:** Keep it concise, **2-3 paragraphs maximum**.
            - **Tweet:** A short, impactful post for X (Twitter) including 1-2 relevant hashtags. Max 280 characters.
            - **Phone Script:** A single, clear sentence that the user can read over the phone to the Senator's office.
            - **Spelling:** Use **Australian English** (e.g., 'labour', 'organise', 'centre', 'programme').
            - **Constraints:** **Avoid using dashes** (— or -) to separate clauses; use commas or full stops instead.
            - **Personalization:** Use the Senator's background (party, experience) and the user's personal context to build a compelling narrative.
            
            **Output Format:**
            Return ONLY a valid JSON object with the following structure:
            {
              "subject": "A compelling and clear subject line",
              "body": "The full email, 2-3 paragraphs, including salutation and sign-off",
              "tweet": "The short tweet content including why it's important personally",
              "phoneLine": "The two-sentence phone script including why it's important personally"
            }
        """.trimIndent()

                return callGemini(prompt, InternalEmailResponse::class.java)?.let {
                        EmailResponse(
                                toAddress = senator.email
                                                ?: "senator.${senator.name?.replace(" ", ".")?.lowercase()}@aph.gov.au",
                                subject = it.subject,
                                body = it.body,
                                tweet = it.tweet,
                                phoneLine = it.phoneLine
                        )
                }
        }

        fun summarizeSenatorBackground(wikiContent: String, senator: Senator): String? {
                val prompt =
                        """You are an expert political researcher and strategist helping to craft personalised advocacy appeals.

        **Senator Information (Already Known - DO NOT REPEAT):**
        - Name: ${senator.name}
        - Party: ${senator.party}
        - State: ${senator.state}
        - Birth Year: ${senator.birthYear}
        - First Year in Office: ${senator.firstYearInOffice}

        **Wikipedia Content:**
        $wikiContent

        **Task:**
        Summarise the background of this Senator based ONLY on the Wikipedia content provided above. Your goal is to extract insights that would be highly useful for campaigners or advocates tailoring a persuasive appeal for a social cause.

        Focus on:
        1. Core Values & Factions: Their core political values, ideological leanings, and factional alignment (e.g., Labor Left, Liberal Moderates, National Right) if mentioned.
        2. Formative Career: Significant professional experience before politics (e.g., union background, corporate sector, community work) that shapes their worldview.
        3. Advocacy & Committees: Key policy interests, social causes they have championed or opposed, and relevant parliamentary committee memberships.
        4. Personal Context: Lived experiences, religious affiliations, or community ties that influence their political outlook.

        **Constraints:**
        - **DO NOT** merely repeat the basic facts listed in the "Already Known" section above.
        - Keep the summary concise, objective, and professional (2-3 short paragraphs maximum).
        - If information for a specific focus area is missing from the provided text, do not invent it.
        - Use **Australian English** spelling (e.g., summarise, standardise). Note: Always capitalise and spell the "Australian Labor Party" as "Labor".
        - Return ONLY the summary text, with no introductory, conversational, or concluding remarks.
        """.trimIndent()

                return callGemini(prompt, String::class.java)
        }

        private data class InternalEmailResponse(
                val subject: String,
                val body: String,
                val tweet: String,
                val phoneLine: String
        )

        private fun <T> callGemini(
                prompt: String,
                responseType: Class<T>,
                file: java.io.File? = null
        ): T? {
                val parts = mutableListOf(Part(text = prompt))

                if (file != null && file.exists()) {
                        try {
                                val bytes = java.nio.file.Files.readAllBytes(file.toPath())
                                val base64Data = java.util.Base64.getEncoder().encodeToString(bytes)
                                val mimeType =
                                        java.nio.file.Files.probeContentType(file.toPath())
                                                ?: "application/octet-stream"

                                parts.add(Part(inlineData = InlineData(mimeType, base64Data)))
                        } catch (e: Exception) {
                                logger.error(
                                        "Failed to read/encode file attachment for Gemini: ${e.message}",
                                        e
                                )
                        }
                }

                val requestBody = GeminiApiRequest(contents = listOf(Content(parts = parts)))
                val httpEntity = HttpEntity(requestBody, headers)

                try {
                        val response =
                                restTemplate.postForObject(
                                        uri,
                                        httpEntity,
                                        GeminiApiResponse::class.java
                                )

                        val responseJsonString =
                                response?.candidates
                                        ?.firstOrNull()
                                        ?.content
                                        ?.parts
                                        ?.firstOrNull()
                                        ?.text
                                        ?: throw IOException(
                                                "Failed to extract response text from Gemini API"
                                        )

                        val cleanedJson =
                                responseJsonString
                                        .trim()
                                        .removeSurrounding("```json\n", "\n```")
                                        .trim()

                        logger.info("Received cleaned JSON from Gemini: $cleanedJson")

                        if (responseType == String::class.java) {
                                // If the expected response is a plain string, return it directly
                                return cleanedJson as T
                        }

                        return objectMapper.readValue(cleanedJson, responseType)
                } catch (e: HttpClientErrorException) {
                        logger.error(
                                "HTTP error calling Gemini API: ${e.statusCode} ${e.responseBodyAsString}",
                                e
                        )
                } catch (e: Exception) {
                        logger.error("Failed to process Gemini request: ${e.message}", e)
                }
                return null
        }
}
