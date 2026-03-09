package click.repwrite.model

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey

@DynamoDbBean
data class Senator(
    @get:DynamoDbPartitionKey var id: String? = null,
    var name: String? = null,
    var email: String? = null,
    var birthYear: Int? = null,
    var party: String? = null,
    var state: String? = null,
    var firstYearInOffice: Int? = null,
    var background: String? = null
)
