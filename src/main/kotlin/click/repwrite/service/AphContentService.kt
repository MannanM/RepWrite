package click.repwrite.service

import click.repwrite.model.Politician
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import org.springframework.boot.restclient.RestTemplateBuilder
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.net.URI

@Service
class AphContentService(restTemplateBuilder: RestTemplateBuilder) {
    private val logger = LoggerFactory.getLogger(AphContentService::class.java)
    private val restTemplate: RestTemplate = restTemplateBuilder.build()
    private val baseUrl = "https://www.aph.gov.au"
    private val searchUrl = "$baseUrl/Senators_and_Members/Parliamentarian_Search_Results"

    private val headers = HttpHeaders().apply {
        add(
            HttpHeaders.USER_AGENT,
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
        )
    }

    fun getRepresentative(): Map<String, String> {
        return fetchAllResults("mem=1")
    }

    fun getSenators(): Map<String, String> {
        return fetchAllResults("sen=1")
    }

    fun fetchPoliticianDetails(url: String): Politician? {
        val html = fetchHtml(url) ?: return null
        val doc = Jsoup.parse(html)

        val nameRaw = doc.select("div.profile h1").text().trim()
        val name = cleanName(nameRaw)
        val mpid = url.substringAfter("MPID=", "")

        val chamber = doc.select("div.profile dt:contains(Chamber) + dd").first()?.text()?.trim() ?: ""
        val type = if (chamber.contains("Senate", ignoreCase = true)) "Senator" else "Representative"

        val partyRaw = doc.select("div.profile dt:contains(Party) + dd").first()?.text()?.trim() ?: ""
        // APH sometimes includes service history in the party DD, so we take the part before any service history note.
        val party = partyRaw.substringBefore(". Served:").substringBefore(". ").trim()
        val partyNormalised = if (partyNormalisationMap.containsKey(party)) {
            partyNormalisationMap.getValue(party)
        } else {
            party
        }

        val h3 = doc.select("div.medium-7 h3").first()?.text() ?: ""
        var electorate = h3.substringAfter("for ", "").trim()
        if (type == "Senator") {
            electorate = mapStateAbbreviation(electorate)
        }

        val email = doc.select("dt:contains(Email) + dd a").text().trim()

        val electorateOffice = doc.select("section#t1-content-panel div.medium-6").toList()
            .filter { it.select("h3").text().contains("Electorate Office", ignoreCase = true) }
            .find { it.select("strong").text().contains("Principal Office", ignoreCase = true) }

        var phoneRaw = electorateOffice?.select("dt:contains(Telephone) + dd a")?.first()?.text()?.trim()

        if (phoneRaw.isNullOrBlank()) {
            val parliamentOffice = doc.select("section#t1-content-panel div.medium-6").toList()
                .find { it.select("h3").text().contains("Parliament Office", ignoreCase = true) }
            phoneRaw = parliamentOffice?.select("dt:contains(Telephone) + dd a")?.first()?.text()?.trim()
        }
        val phone = phoneRaw?.let { cleanPhone(it) }

        val twitter = doc.select("a[href*='twitter.com'], a[href*='x.com']").first()?.attr("href")
        val instagram = doc.select("a[href*='instagram.com']").first()?.attr("href")
        val bluesky = doc.select("a[href*='bsky.app']").first()?.attr("href")

        val handleRaw = (twitter ?: instagram ?: bluesky)?.trim()
        val handle = if (handleRaw != null) {
            "@" + handleRaw.removeSuffix("/").substringAfterLast("/")
        } else null

        return Politician(
            id = mpid.ifBlank { null },
            name = name.ifBlank { null },
            email = email.ifBlank { null },
            party = partyNormalised.ifBlank { null },
            electorate = electorate.ifBlank { null },
            type = type,
            phone = if (phone?.isNotBlank() == true) phone else null,
            handle = handle
        )
    }

    private fun mapStateAbbreviation(abbreviation: String): String {
        return when (abbreviation.uppercase()) {
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

    private fun cleanName(rawName: String): String {
        var name = rawName
        val prefixRegex = """^(Senator|Mr|Mrs|Ms|Miss|Hon|Dr|Rev|Sir|Dame|the)\s+""".toRegex(RegexOption.IGNORE_CASE)
        var previousName: String
        do {
            previousName = name
            name = name.replace(prefixRegex, "").trim()
        } while (name != previousName)

        val suffixRegex = """[,?\s]+(MP|CSC|OAM|KC|AM|AO)$""".toRegex(RegexOption.IGNORE_CASE)
        do {
            previousName = name
            name = name.replace(suffixRegex, "").trim().removeSuffix(",")
        } while (name != previousName)

        return name.trim()
    }

    private fun cleanPhone(rawPhone: String): String {
        return rawPhone.replace(Regex("""[\s\(\)\-]"""), "")
    }

    private fun fetchAllResults(typeParam: String): Map<String, String> {
        val resultsMap = mutableMapOf<String, String>()
        var pageNum = 1
        var hasMore = true

        while (hasMore) {
            val url = "$searchUrl?page=$pageNum&q=&$typeParam&ps=100"
            logger.info("Fetching APH $typeParam results, page $pageNum")

            val html = fetchHtml(url)
            if (html == null) {
                hasMore = false
                continue
            }

            val doc = Jsoup.parse(html)
            val links = doc.select("h4.title a")

            if (links.isEmpty()) {
                hasMore = false
                continue
            }

            links.forEach { link ->
                val nameRaw = link.text().trim()
                val name = cleanName(nameRaw)
                val path = link.attr("href")
                val fullUrl = if (path.startsWith("http")) path else "$baseUrl$path"
                resultsMap[name] = fullUrl
            }

            val nextLink = doc.select("li.next:not(.inactive) a")
            if (nextLink.isEmpty()) {
                hasMore = false
            } else {
                pageNum++
            }
        }

        logger.info("Total $typeParam found: ${resultsMap.size}")
        return resultsMap
    }

    private fun fetchHtml(url: String): String? {
        return try {
            restTemplate
                .exchange(URI.create(url), HttpMethod.GET, HttpEntity<Void>(headers), String::class.java)
                .body
        } catch (e: Exception) {
            logger.error("Failed to fetch APH content from $url: ${e.message}")
            null
        }
    }

    private val partyNormalisationMap = mapOf(
        "Liberal Party of Australia" to "Liberal",
        "Australian Labor Party" to "Labor",
        "Liberal National Party of Queensland" to "Liberal National",
        "Australian Greens" to "Greens",
        "United Australia Party" to "United Australia",
        "Country Liberal Party" to "Country Liberal",
    )
}
