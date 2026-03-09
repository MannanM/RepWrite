package click.repwrite.ai

import com.fasterxml.jackson.annotation.JsonProperty

// --- Internal DTOs for Gemini API Communication ---
// These data classes model the actual request and response structure of the Gemini API.
// Your service will use these to communicate with Google's backend.
// Request structure
internal data class GeminiApiRequest(
    @JsonProperty("contents") val contents: List<Content>
)

internal data class Content(
    @JsonProperty("parts") val parts: List<Part>
)

internal data class Part(
    @JsonProperty("text") val text: String? = null,
    @JsonProperty("inline_data") val inlineData: InlineData? = null
)

internal data class InlineData(
    @JsonProperty("mime_type") val mimeType: String,
    @JsonProperty("data") val data: String
)

// Response structure
internal data class GeminiApiResponse(
    @JsonProperty("candidates") val candidates: List<Candidate>?,
    @JsonProperty("promptFeedback") val promptFeedback: PromptFeedback?
)

internal data class Candidate(
    @JsonProperty("content") val content: Content
)

internal data class PromptFeedback(
    @JsonProperty("blockReason") val blockReason: String?,
    @JsonProperty("safetyRatings") val safetyRatings: List<SafetyRating>?,
)

internal data class SafetyRating(
    @JsonProperty("category") val category: String,
    @JsonProperty("probability") val probability: String,
)