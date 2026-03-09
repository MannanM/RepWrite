package click.repwrite.model

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey
import java.time.Instant

@DynamoDbBean
data class CachedAppeal(
    @get:DynamoDbPartitionKey var causeId: String? = null,
    @get:DynamoDbSortKey var senatorIdInfoHash: String? = null, // Format: senatorId#infoHash
    var senatorId: String? = null,
    var infoHash: String? = null,
    var subject: String? = null,
    var body: String? = null,
    var tweet: String? = null,
    var phoneLine: String? = null,
    var createdAt: String? = Instant.now().toString(),
    var name: String? = null,
    var gender: String? = null,
    var age: Int? = null,
    var occupation: String? = null,
    var importance: String? = null
)
