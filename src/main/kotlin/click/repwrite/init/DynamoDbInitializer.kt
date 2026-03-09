package click.repwrite.init

import click.repwrite.model.Cause
import click.repwrite.model.CachedAppeal
import click.repwrite.model.Senator
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.TableSchema
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException

@Component
class DynamoDbInitializer(private val enhancedClient: DynamoDbEnhancedClient) {

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
                logger.info("Inserting senator data...")
                val senatorData =
                        listOf(
                                Triple("Andrew Bragg", 1984, "Liberal"),
                                Triple("Slade Brockman", 1970, "Liberal"),
                                Triple("Carol Brown", 1963, "Labor"),
                                Triple("Ross Cadell", 1969, "National"),
                                Triple("Matt Canavan", 1980, "Liberal National"),
                                Triple("Michaelia Cash", 1970, "Liberal"),
                                Triple("Claire Chandler", 1990, "Liberal"),
                                Triple("Anthony Chisholm", 1978, "Labor"),
                                Triple("Raff Ciccone", 1983, "Labor"),
                                Triple("Richard Colbeck", 1958, "Liberal"),
                                Triple("Jessica Collins", 1983, "Liberal"),
                                Triple("Dorinda Cox", 1976, "Greens"),
                                Triple("Lisa Darmanin", null, "Labor"),
                                Triple("Josh Dolega", 1983, "Labor"),
                                Triple("Richard Dowling", 1983, "Labor"),
                                Triple("Jonathon Duniam", 1982, "Liberal"),
                                Triple("Don Farrell", 1954, "Labor"),
                                Triple("Mehreen Faruqi", 1963, "Greens"),
                                Triple("Katy Gallagher", 1970, "Labor"),
                                Triple("Varun Ghosh", 1985, "Labor"),
                                Triple("Nita Green", 1983, "Labor"),
                                Triple("Karen Grogan", 1967, "Labor"),
                                Triple("Pauline Hanson", 1954, "One Nation"),
                                Triple("Sarah Hanson-Young", 1981, "Greens"),
                                Triple("Sarah Henderson", 1964, "Liberal"),
                                Triple("Steph Hodgins-May", 1985, "Greens"),
                                Triple("Maria Kovacic", 1970, "Liberal"),
                                Triple("Jacqui Lambie", 1971, "Jacqui Lambie Network"),
                                Triple("Kerrynne Liddle", 1967, "Liberal"),
                                Triple("Sue Lines", 1953, "Labor"),
                                Triple("Nick McKim", 1965, "Greens"),
                                Triple("Andrew McLachlan", 1966, "Liberal"),
                                Triple("Corinne Mulholland", 1987, "Labor"),
                                Triple("Deborah O'Neill", 1961, "Labor"),
                                Triple("Matt O'Sullivan", 1978, "Liberal"),
                                Triple("James Paterson", 1987, "Liberal"),
                                Triple("Fatima Payman", 1995, "Australia's Voice"),
                                Triple("Barbara Pocock", 1955, "Greens"),
                                Triple("David Pocock", 1988, "Independent"),
                                Triple("Jacinta Nampijinpa Price", 1981, "Country Liberal"),
                                Triple("Malcolm Roberts", 1955, "One Nation"),
                                Triple("Anne Ruston", 1963, "Liberal"),
                                Triple("Paul Scarr", 1969, "Liberal National"),
                                Triple("Dave Sharma", 1975, "Liberal"),
                                Triple("Tony Sheldon", 1961, "Labor"),
                                Triple("David Shoebridge", 1971, "Greens"),
                                Triple("Dean Smith", 1969, "Liberal"),
                                Triple("Marielle Smith", 1986, "Labor"),
                                Triple("Jordon Steele-John", 1994, "Greens"),
                                Triple("Glenn Sterle", 1960, "Labor"),
                                Triple("Jana Stewart", 1987, "Labor"),
                                Triple("Lidia Thorpe", 1973, "Independent"),
                                Triple("Tammy Tyrrell", 1970, "Independent"),
                                Triple("Charlotte Walker", 2004, "Labor"),
                                Triple("Jess Walsh", 1971, "Labor"),
                                Triple("Larissa Waters", 1977, "Greens"),
                                Triple("Peter Whish-Wilson", 1968, "Greens"),
                                Triple("Penny Wong", 1968, "Labor")
                        )

                senatorData.forEach { (name, birthYear, party) ->
                    val lastName = name.substringAfterLast(" ").lowercase()
                    val id = UUID.randomUUID().toString()
                    val email = "senator.${lastName}@aph.gov.au"

                    val senator =
                            Senator(
                                    id = id,
                                    name = name,
                                    email = email,
                                    birthYear = birthYear,
                                    party = party,
                                    state = determineState(name),
                                    firstYearInOffice = determineFirstYear(name),
                                    background = ""
                            )
                    table.putItem(senator)
                }
                logger.info("Senator data inserted.")
            } else {
                logger.info("Senator data already present.")
            }
        } catch (e: Exception) {
            logger.error("Failed to initialize SenatorsTable. Error: ${e.message}")
        }
    }

    private fun determineState(name: String): String =
            when (name) {
                "Andrew Bragg",
                "Ross Cadell",
                "Jessica Collins",
                "Mehreen Faruqi",
                "Maria Kovacic",
                "Deborah O'Neill",
                "Dave Sharma",
                "Tony Sheldon",
                "David Shoebridge" -> "New South Wales"
                "Slade Brockman",
                "Michaelia Cash",
                "Varun Ghosh",
                "Sue Lines",
                "Matt O'Sullivan",
                "Fatima Payman",
                "Dean Smith",
                "Jordon Steele-John",
                "Glenn Sterle" -> "Western Australia"
                "Carol Brown",
                "Claire Chandler",
                "Richard Colbeck",
                "Josh Dolega",
                "Richard Dowling",
                "Jonathon Duniam",
                "Jacqui Lambie",
                "Nick McKim",
                "Tammy Tyrrell",
                "Peter Whish-Wilson" -> "Tasmania"
                "Ross Cadell" -> "New South Wales" // Duplicate check handled by when
                "Matt Canavan",
                "Anthony Chisholm",
                "Nita Green",
                "Pauline Hanson",
                "Corinne Mulholland",
                "Malcolm Roberts",
                "Paul Scarr",
                "Larissa Waters" -> "Queensland"
                "Raff Ciccone",
                "Lisa Darmanin",
                "Sarah Henderson",
                "Steph Hodgins-May",
                "James Paterson",
                "Jana Stewart",
                "Lidia Thorpe",
                "Jess Walsh" -> "Victoria"
                "Don Farrell",
                "Karen Grogan",
                "Sarah Hanson-Young",
                "Kerrynne Liddle",
                "Andrew McLachlan",
                "Barbara Pocock",
                "Anne Ruston",
                "Marielle Smith",
                "Charlotte Walker",
                "Penny Wong" -> "South Australia"
                "Katy Gallagher", "David Pocock" -> "Australian Capital Territory"
                "Jacinta Nampijinpa Price" -> "Northern Territory"
                else -> "Unknown"
            }

    private fun determineFirstYear(name: String): Int? =
            when (name) {
                "Andrew Bragg",
                "Claire Chandler",
                "Raff Ciccone",
                "Nita Green",
                "Sarah Henderson",
                "Matt O'Sullivan",
                "Tony Sheldon",
                "Marielle Smith",
                "Jess Walsh" -> 2019
                "Slade Brockman", "Jordon Steele-John" -> 2017
                "Carol Brown", "Glenn Sterle" -> 2005
                "Ross Cadell",
                "Kerrynne Liddle",
                "Fatima Payman",
                "Barbara Pocock",
                "David Pocock",
                "Jacinta Nampijinpa Price",
                "David Shoebridge",
                "Jana Stewart",
                "Tammy Tyrrell" -> 2022
                "Matt Canavan", "Jacqui Lambie" -> 2014
                "Michaelia Cash", "Don Farrell", "Sarah Hanson-Young" -> 2008
                "Anthony Chisholm", "Jonathon Duniam", "Katy Gallagher", "James Paterson" -> 2016
                "Richard Colbeck", "Penny Wong" -> 2002
                "Jessica Collins",
                "Josh Dolega",
                "Richard Dowling",
                "Corinne Mulholland",
                "Dave Sharma",
                "Charlotte Walker" -> 2025
                "Dorinda Cox", "Karen Grogan" -> 2021
                "Lisa Darmanin", "Varun Ghosh", "Steph Hodgins-May" -> 2024
                "Pauline Hanson", "Malcolm Roberts" -> 2016
                "Maria Kovacic" -> 2023
                "Sue Lines", "Deborah O'Neill" -> 2013
                "Nick McKim" -> 2015
                "Andrew McLachlan" -> 2020
                "Dean Smith", "Anne Ruston", "Peter Whish-Wilson" -> 2012
                "Lidia Thorpe" -> 2020
                else -> null
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
                logger.info("Seeding Causes data...")
                val gasGiveaway =
                        Cause(
                                id = "1",
                                name = "The Great Gas Giveaway",
                                expiryDate = "2026-04-01",
                                content =
                                        """
                        ACT Independent Senator David Pocock is today tabling a motion proposing to establish a special senate committee to inquire into the extraordinarily low rates of revenue Australians receive from the export of our gas resources.

                        Proposed as the “Select Committee on Why Gas Companies Pay Less for Offshore Liquid Natural Gas than Australians Pay in Beer Excise”, it would examine the amount of Petroleum Resource Rent Tax (PRRT) paid on Liquified Natural Gas (LNG) and why it is so low, comparable policies in other jurisdictions like Norway and Qatar and the Australian Council of Trade Union’s proposal for a 25% tax on gas export revenue.

                        The inquiry would also examine the impact on Australian businesses and households of the increase in gas prices since 2016 and what could be done with the additional revenue generated by effectively taxing the offshore LNG industry.

                        It would comprise 2 Labor, 2 Coalition and one crossbench senator alongside Senator Pocock as Chair and report in May 2026.

                        The proposal will be put to a vote in the Senate tomorrow.

                        Senator Pocock said Australians have had enough of multinational gas companies profiting off our resources without providing a fair return.

                        “We get one chance to capture the benefits of the LNG boom and invest in the things Australians need most: housing, health, education,” Senator Pocock said.

                        “Currently we are squandering what Norway has turned into a ${'$'}3 trillion dollar sovereign wealth fund.

                        “Governments of all political persuasions are constantly telling us budgets are about priorities and asking for solutions, this proposal ticks both those boxes.

                        “I call on the major parties to stand up for what the people they’ve been elected to represent want to see and that’s big companies paying more to export our gas than Australians pay on beer excise.”
                    """.trimIndent()
                        )
                val meAwareness =
                        Cause(
                                id = "2",
                                name = "ME Awareness Month",
                                expiryDate = "2026-04-01",
                                content =
                                        """
                        March is international Myalgic Encephalomyelitis (ME) Awareness Month. 
                        
                        An estimated 250,000 Australians are currently living with Myalgic Encephalomyelitis, yet it remains one of the most underfunded and unrecognised chronic conditions in our country. Many patients are confined to their homes or beds, with their lives on hold indefinitely due to profound exhaustion and physiological dysfunction.
                        
                        Despite its prevalence and the severe disability it causes, researchers and patients continue to struggle for adequate funding, clinical recognition, and appropriate healthcare support. We are calling for increased federal research funding specifically targeted at ME and a formal recognition of the condition to improve clinical care standards across Australia.
                        
                        The time to act is now. Our fellow Australians deserve a healthcare system that recognises their suffering and invests in their future.
                    """.trimIndent()
                        )
                table.putItem(gasGiveaway)
                table.putItem(meAwareness)
                logger.info("Causes data seeded.")
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
