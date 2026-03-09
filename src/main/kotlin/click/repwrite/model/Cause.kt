package click.repwrite.model

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey

@DynamoDbBean
data class Cause(
    @get:DynamoDbPartitionKey
    var id: String? = null,
    var name: String? = null,
    var expiryDate: String? = null,
    var content: String? = null
)
