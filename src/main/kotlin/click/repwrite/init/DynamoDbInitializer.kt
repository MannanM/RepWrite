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
import tools.jackson.core.type.TypeReference
import java.io.InputStream

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
        val tableName = "PoliticiansTable"
        val politicianTable = enhancedClient.table(tableName, TableSchema.fromBean(Politician::class.java))

        try {
            politicianTable.createTable()
            logger.info("Table $tableName created.")

            val inputStream: InputStream? = javaClass.getResourceAsStream("/data/politicians.json")
            if (inputStream != null) {
                val politicians: List<Politician> = objectMapper.readValue(inputStream, object : TypeReference<List<Politician>>() {})
                politicians.forEach { politicianTable.putItem(it) }
                logger.info("Successfully loaded ${politicians.size} politicians from politicians.json into $tableName.")
            } else {
                logger.warn("politicians.json not found in resources.")
            }
        } catch (e: Exception) {
            if (e.message?.contains("Table already exists") == true) {
                logger.info("Table $tableName already exists.")
            } else {
                logger.error("Error initializing $tableName: ${e.message}", e)
            }
        }
    }

    private fun initCausesData() {
        val tableName = "CausesTable"
        val causeTable = enhancedClient.table(tableName, TableSchema.fromBean(Cause::class.java))

        try {
            causeTable.createTable()
            logger.info("Table $tableName created.")

            val inputStream: InputStream? = javaClass.getResourceAsStream("/data/causes.json")
            if (inputStream != null) {
                val causes: List<Cause> = objectMapper.readValue(inputStream, object : TypeReference<List<Cause>>() {})
                causes.forEach { causeTable.putItem(it) }
                logger.info("Successfully loaded ${causes.size} causes from causes.json into $tableName.")
            } else {
                logger.warn("causes.json not found in resources.")
            }
        } catch (e: Exception) {
            if (e.message?.contains("Table already exists") == true) {
                logger.info("Table $tableName already exists.")
            } else {
                logger.error("Error initializing $tableName: ${e.message}", e)
            }
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
