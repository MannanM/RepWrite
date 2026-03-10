package click.repwrite.init

import click.repwrite.model.Cause
import click.repwrite.model.CachedAppeal
import click.repwrite.model.Senator
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
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

    @EventListener(ApplicationReadyEvent::class)
    fun init() {
        cleanupLegacyTables()
        initSenatorsData()
        initCausesData()
        initCachedAppealsTable()
    }

    private fun cleanupLegacyTables() {
        try {
            val table =
                    enhancedClient.table(
                            "TestDataTable",
                            TableSchema.fromBean(Senator::class.java)
                    ) // Schema doesn't matter for deletion
            try {
                table.describeTable()
                logger.info("Table 'TestDataTable' exists. Deleting...")
                table.deleteTable()
                logger.info("Table 'TestDataTable' deleted.")
            } catch (e: ResourceNotFoundException) {
                // Ignore if not found
            }
        } catch (e: Exception) {
            logger.error("Failed to cleanup legacy tables: ${e.message}")
        }
    }

    private fun initSenatorsData() {
        try {
            val table =
                    enhancedClient.table("SenatorsTable", TableSchema.fromBean(Senator::class.java))

            try {
                table.describeTable()
                logger.info("Table 'SenatorsTable' already exists.")
            } catch (e: ResourceNotFoundException) {
                logger.info("Table 'SenatorsTable' does not exist. Creating...")
                table.createTable()
                logger.info("Table 'SenatorsTable' created.")
            }

            val results = table.scan().items().iterator()
            if (!results.hasNext()) {
                logger.info("Loading senator data from JSON...")
                val inputStream = DynamoDbInitializer::class.java.getResourceAsStream("/data/senators.json")
                if (inputStream != null) {
                    val senatorListType = objectMapper.typeFactory.constructCollectionType(List::class.java, Senator::class.java)
                    val senators: List<Senator> = objectMapper.readValue(inputStream, senatorListType)
                    senators.forEach { table.putItem(it) }
                    logger.info("Inserted ${senators.size} senators.")
                } else {
                    logger.error("Could not find senators.json in classpath.")
                }
            } else {
                logger.info("Senator data already present.")
            }
        } catch (e: Exception) {
            logger.error("Failed to initialize SenatorsTable. Error: ${e.message}")
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
                    val causeListType = objectMapper.typeFactory.constructCollectionType(List::class.java, Cause::class.java)
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
