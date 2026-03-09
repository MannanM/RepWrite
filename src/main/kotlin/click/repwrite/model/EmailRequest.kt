package click.repwrite.model

data class EmailRequest(
    val causeId: String,
    val senatorId: String,
    val name: String? = null,
    val gender: String? = null,
    val age: Int? = null,
    val occupation: String? = null,
    val importance: String? = null
)
