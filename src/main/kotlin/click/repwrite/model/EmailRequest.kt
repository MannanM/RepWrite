package click.repwrite.model

data class EmailRequest(
    val causeId: String,
    val politicianId: String,
    val name: String? = null,
    val gender: String? = null,
    val age: Int? = null,
    val occupation: String? = null,
    val importance: String? = null
)
