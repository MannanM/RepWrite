package click.repwrite.controller

import click.repwrite.model.CachedAppeal
import click.repwrite.model.Cause
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.TableSchema

@RestController
@RequestMapping("/api/statistics")
class StatisticsRestController(
    enhancedClient: DynamoDbEnhancedClient
) {

    private val cachedAppealsTable =
        enhancedClient.table(
            "CachedAppealsTable",
            TableSchema.fromBean(CachedAppeal::class.java)
        )

    private val causesTable =
        enhancedClient.table(
            "CausesTable",
            TableSchema.fromBean(Cause::class.java)
        )

    @GetMapping
    fun getStatistics(): Map<String, Any> {
        val appeals = cachedAppealsTable.scan().items().toList()
        val totalAppeals = appeals.size

        val uniqueNames = appeals.mapNotNull { it.name }
            .filter { it.isNotBlank() }
            .distinct()
            .size

        val causeIdToCount = appeals.groupingBy { it.causeId ?: "Unknown" }.eachCount()

        val causes = causesTable.scan().items().toList().associateBy({ it.id }, { it.name })

        val issueStats = causeIdToCount.map { (id, count) ->
            mapOf(
                "causeId" to id,
                "causeName" to (causes[id] ?: "Unknown"),
                "count" to count
            )
        }.sortedByDescending { it["count"] as Int }

        return mapOf(
            "totalAppeals" to totalAppeals,
            "uniqueNames" to uniqueNames,
            "issueStats" to issueStats
        )
    }
}
