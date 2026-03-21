package click.repwrite.model

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey

@DynamoDbBean
data class Politician(
    @get:DynamoDbPartitionKey var id: String? = null,
    var name: String? = null,
    var email: String? = null,
    var birthYear: Int? = null,
    var party: String? = null,
    var electorate: String? = null,
    var type: String? = null, // "Senator" or "Representative"
    var firstYearInOffice: Int? = null,
    var background: String? = null,
    var phone: String? = null,
    var handle: String? = null,
    var wikiUrl: String? = null
)
