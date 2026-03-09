package click.repwrite.model

data class EmailResponse(
        val toAddress: String,
        val subject: String,
        val body: String,
        val tweet: String? = null,
        val phoneLine: String? = null
)
