package click.repwrite.ai

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "gemini")
data class GeminiProperties(
    val url: String,
    val apiKey: String? = null
)
