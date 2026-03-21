package click.repwrite.init

import click.repwrite.model.CachedAppeal
import click.repwrite.model.Politician
import click.repwrite.model.Cause
import org.slf4j.LoggerFactory
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.TableSchema
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException
import tools.jackson.databind.ObjectMapper

@Component
class DynamoDbInitializer(
    private val enhancedClient: DynamoDbEnhancedClient,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(DynamoDbInitializer::class.java)

    @EventListener(ContextRefreshedEvent::class)
    fun onApplicationEvent() {
        initPoliticiansData()
        initCausesData()
        initCachedAppealsTable()
    }

    private fun initPoliticiansData() {
        val table = enhancedClient.table("PoliticiansTable", TableSchema.fromBean(Politician::class.java))

        try {
            table.describeTable()
            logger.info("Table 'PoliticiansTable' already exists.")
        } catch (e: ResourceNotFoundException) {
            logger.info("Table 'PoliticiansTable' does not exist. Creating...")
            table.createTable()
            logger.info("Table 'PoliticiansTable' created.")
        }

        val results = table.scan().items().iterator()
        if (!results.hasNext()) {
            logger.info("Loading politician data from JSON...")
            val inputStream = DynamoDbInitializer::class.java.getResourceAsStream("/data/politicians.json")
            if (inputStream != null) {
                val senatorListType =
                    objectMapper.typeFactory.constructCollectionType(List::class.java, Politician::class.java)
                val senators: List<Politician> = objectMapper.readValue(inputStream, senatorListType)
                senators.forEach { table.putItem(it) }
                logger.info("Inserted ${senators.size} politicians.")
            } else {
                logger.error("Could not find politicians.json in classpath.")
            }
        } else {
            logger.info("Politician data already present.")
        }
    }

    private fun initCausesData() {
        try {
            val table = enhancedClient.table("CausesTable", TableSchema.fromBean(Cause::class.java))
            try {
                table.describeTable()
                logger.info("Table 'CausesTable' already exists.")
            } catch (e: ResourceNotFoundException) {
                logger.info("Table 'CausesTable' does not exist. Creating...")
                table.createTable()
                logger.info("Table 'CausesTable' created.")
            }

            val results = table.scan().items().iterator()
            if (!results.hasNext()) {
                logger.info("Loading causes data from JSON...")
                val inputStream = DynamoDbInitializer::class.java.getResourceAsStream("/data/causes.json")
                if (inputStream != null) {
                    val causeListType =
                        objectMapper.typeFactory.constructCollectionType(List::class.java, Cause::class.java)
                    val causes: List<Cause> = objectMapper.readValue(inputStream, causeListType)
                    causes.forEach { table.putItem(it) }
                    logger.info("Seeded ${causes.size} causes.")
                } else {
                    logger.error("Could not find causes.json in classpath.")
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to initialize CausesTable: ${e.message}")
        }
    }

    private fun initCachedAppealsTable() {
        try {
            val table =
                enhancedClient.table(
                    "CachedAppealsTable",
                    TableSchema.fromBean(CachedAppeal::class.java)
                )
            try {
                table.describeTable()
                logger.info("Table 'CachedAppealsTable' already exists.")
            } catch (e: ResourceNotFoundException) {
                logger.info("Table 'CachedAppealsTable' does not exist. Creating...")
                table.createTable()
                logger.info("Table 'CachedAppealsTable' created.")
            }
        } catch (e: Exception) {
            logger.error("Failed to initialize CachedAppealsTable: ${e.message}")
        }
    }
}
