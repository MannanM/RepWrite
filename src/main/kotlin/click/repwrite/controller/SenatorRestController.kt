package click.repwrite.controller

import click.repwrite.model.Senator
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.TableSchema

@RestController
@RequestMapping("/api")
class SenatorRestController(private val enhancedClient: DynamoDbEnhancedClient) {

    private val table = enhancedClient.table("SenatorsTable", TableSchema.fromBean(Senator::class.java))

    @GetMapping("/states/{state}/senators")
    fun getSenatorsByState(@PathVariable state: String): List<Senator> {
        val fullStateName = mapStateAbbreviation(state.uppercase())
        return table.scan().items()
            .filter { it.state?.equals(fullStateName, ignoreCase = true) == true }
            .sortedBy { it.name }
            .toList()
    }

    @GetMapping("/postcodes/{postcode}/senators")
    fun getSenatorsByPostcode(@PathVariable postcode: String): List<Senator> {
        val state = convertPostcodeToState(postcode) ?: return emptyList()
        return getSenatorsByState(state)
    }

    private fun mapStateAbbreviation(abbreviation: String): String {
        return when (abbreviation) {
            "NSW" -> "New South Wales"
            "VIC" -> "Victoria"
            "QLD" -> "Queensland"
            "SA" -> "South Australia"
            "WA" -> "Western Australia"
            "TAS" -> "Tasmania"
            "ACT" -> "Australian Capital Territory"
            "NT" -> "Northern Territory"
            else -> abbreviation
        }
    }

    private fun convertPostcodeToState(postcodeStr: String): String? {
        val postcode = postcodeStr.toIntOrNull() ?: return null
        return when {
            postcode in 800..999 -> "NT"
            postcode in 1000..1999 -> "NSW"
            postcode in 2000..2599 -> "NSW"
            postcode in 2600..2618 -> "ACT"
            postcode in 2619..2899 -> "NSW"
            postcode in 2900..2920 -> "ACT"
            postcode in 2921..2999 -> "NSW"
            postcode in 3000..3999 -> "VIC"
            postcode in 4000..4999 -> "QLD"
            postcode in 5000..5999 -> "SA"
            postcode in 6000..6999 -> "WA"
            postcode in 7000..7499 -> "TAS"
            postcode in 7800..7999 -> "TAS"
            postcode in 8000..8999 -> "VIC"
            postcode in 9000..9999 -> "QLD"
            else -> null
        }
    }
}
