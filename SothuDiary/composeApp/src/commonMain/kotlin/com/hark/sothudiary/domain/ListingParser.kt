package com.hark.sothudiary.domain

import com.hark.sothudiary.data.AreaUnit
import com.hark.sothudiary.data.PropertyType
import com.hark.sothudiary.data.market.MarketData
import com.hark.sothudiary.data.market.MarketDataset
import com.hark.sothudiary.data.market.MarketLocality
import kotlin.math.roundToLong

const val SQFT_PER_GROUND = 2400.0

/** What could be read from a shared caption / link preview / poster text. Every field is optional. */
data class ParsedListing(
    val title: String? = null,
    val description: String? = null,
    /** Total asking price in rupees (given, or computed from a per-unit rate × size). */
    val totalPrice: Long? = null,
    /** A per-unit rate, e.g. ₹3.5 L per cent. */
    val ratePrice: Long? = null,
    val rateUnit: AreaUnit? = null,
    val size: Double? = null,
    val unit: AreaUnit? = null,
    val type: PropertyType? = null,
    val phones: List<String> = emptyList(),
    val locality: MarketLocality? = null,
    /** "Madurai" when only the city / district is mentioned. */
    val district: String? = null,
)

/**
 * Pulls listing details out of free text in English / Tamil / Indian notation.
 * Pure Kotlin, no platform calls – see ListingParserTest.
 */
object ListingParser {

    private const val NUM = """([0-9][0-9,]*(?:\.[0-9]+)?)"""
    private const val MULT = """(crores?|cr\b|கோடி|lakhs?|lacs?|lac\b|l\b|லட்சம்|லட்ச|லட்சங்கள்|லக்ஷம்|k\b|thousand|ஆயிரம்)"""
    private val moneyRe = Regex("""(₹|rs\.?|inr|rate|price|விலை)?\s*$NUM\s*$MULT?""", RegexOption.IGNORE_CASE)
    private const val UNIT_WORDS = """(cents?|சென்ட்|செண்ட்|sq\.?\s*ft|sqft|sq\.?\s*feet|square\s*feet|ச\.?\s*அடி|சதுர\s*அடி|acres?|ஏக்கர்|grounds?|கிரவுண்ட்|கிரௌண்ட்)"""
    private val sizeRe = Regex("""$NUM\s*$UNIT_WORDS""", RegexOption.IGNORE_CASE)
    private val unitBeforeRe = Regex("""(?:^|[^0-9.\s])\s*(?:per|/|ஒரு|rate|விலை|@)?\s*$UNIT_WORDS\s*(?:rate|விலை|க்கு|:|-|=|@)?\s*(₹|rs\.?)?\s*$""", RegexOption.IGNORE_CASE)
    private val unitAfterRe = Regex("""^\s*(?:/-)?\s*(?:/|per|a|ஒரு|for\s+1|for\s+one)\s*-?\s*$UNIT_WORDS""", RegexOption.IGNORE_CASE)
    private val unitAfterTamilRe = Regex("""^\s*(?:ஒரு\s*)?$UNIT_WORDS\s*(?:க்கு|கு)""", RegexOption.IGNORE_CASE)
    private val phoneRe = Regex("""(?<![0-9])(?:\+?91[\s-]?|0)?([6-9][0-9]{4}[\s-]?[0-9]{5})(?![0-9])""")
    private val urlRe = Regex("""https?://\S+""")

    fun unitOf(word: String): AreaUnit? {
        val w = word.lowercase()
        return when {
            w.startsWith("cent") || w.contains("சென்ட்") || w.contains("செண்ட்") -> AreaUnit.CENTS
            w.startsWith("acre") || w.contains("ஏக்கர்") -> AreaUnit.ACRES
            w.startsWith("ground") || w.contains("கிர") -> null // grounds are converted to sq ft
            else -> AreaUnit.SQFT
        }
    }

    private fun isGround(word: String) = word.lowercase().startsWith("ground") || word.contains("கிர")

    private fun multiplier(m: String?): Double {
        val w = m?.lowercase()?.trim() ?: return 1.0
        return when {
            w.startsWith("cr") || w.contains("கோடி") -> 1_00_00_000.0
            w.startsWith("l") || w.contains("லட்ச") || w.contains("லக்ஷ") -> 1_00_000.0
            w == "k" || w == "thousand" || w.contains("ஆயிரம்") -> 1_000.0
            else -> 1.0
        }
    }

    data class Money(val rupees: Long, val perUnit: AreaUnit?, val perGround: Boolean, val explicit: Boolean)

    fun findMoney(text: String): List<Money> {
        val out = mutableListOf<Money>()
        for (m in moneyRe.findAll(text)) {
            val prefix = m.groupValues[1]
            val raw = m.groupValues[2]
            val mult = m.groupValues[3].ifBlank { null }
            val num = raw.replace(",", "").toDoubleOrNull() ?: continue
            val digits = raw.count { it.isDigit() }
            // Phone numbers and plain counts are not prices.
            if (mult == null && digits >= 10 && !raw.contains(',')) continue
            val hasCurrency = prefix.isNotBlank()
            val indianCommas = raw.contains(',') && num >= 10_000
            if (mult == null && !hasCurrency && !indianCommas) continue
            val rupees = (num * multiplier(mult)).roundToLong()
            if (rupees < 1_000) continue
            // Is this a rate per cent / sq ft / acre / ground?
            val before = text.substring(maxOf(0, m.range.first - 28), m.range.first + (if (hasCurrency) 0 else 0))
            val after = text.substring(m.range.last + 1, minOf(text.length, m.range.last + 30))
            val beforeUnit = unitBeforeRe.find(before)?.groupValues?.get(1)
            val afterUnit = unitAfterRe.find(after)?.groupValues?.get(1) ?: unitAfterTamilRe.find(after)?.groupValues?.get(1)
            val unitWord = afterUnit ?: beforeUnit
            out += Money(
                rupees = rupees,
                perUnit = unitWord?.let { unitOf(it) ?: AreaUnit.SQFT },
                perGround = unitWord != null && isGround(unitWord),
                explicit = mult != null || hasCurrency,
            )
        }
        return out
    }

    fun findSize(text: String): Pair<Double, AreaUnit>? {
        for (m in sizeRe.findAll(text)) {
            // "₹3 L per cent" style rates are not sizes: skip numbers directly followed by a lakh/crore word.
            val num = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: continue
            if (num <= 0) continue
            val before = text.substring(maxOf(0, m.range.first - 3), m.range.first)
            if (before.contains('₹')) continue
            val word = m.groupValues[2]
            return if (isGround(word)) num * SQFT_PER_GROUND to AreaUnit.SQFT else num to (unitOf(word) ?: AreaUnit.SQFT)
        }
        return null
    }

    fun findType(text: String): PropertyType? {
        val t = text.lowercase()
        fun has(vararg k: String) = k.any { t.contains(it) }
        return when {
            has("house plot", "வீட்டு மனை", "dtcp", "layout", "மனைப்பிரிவு") -> PropertyType.PLOT
            has("apartment", "flat", "bhk", "அடுக்குமாடி", "குடியிருப்பு") -> PropertyType.APARTMENT
            has("villa", "individual house", "independent house", "house", "வீடு", "duplex") -> PropertyType.HOUSE
            has("farm land", "farmland", "farm house", "பண்ணை", "தோட்டம்", "coconut", "தென்னந்தோப்பு", "mango grove") -> PropertyType.FARMLAND
            has("agricultural", "agri land", "agriculture", "விவசாய", "நஞ்சை", "புஞ்சை", "wet land", "dry land") -> PropertyType.AGRI_LAND
            has("plot", "மனை", "site for sale", "plots") -> PropertyType.PLOT
            has("land", "நிலம்", "இடம் விற்பனைக்கு") -> PropertyType.LAND
            else -> null
        }
    }

    fun findPhones(text: String): List<String> =
        phoneRe.findAll(text).map { it.groupValues[1].filter(Char::isDigit) }.filter { it.length == 10 }.distinct().toList()

    /** Longest locality name (English or Tamil) of the dataset that appears in the text. */
    fun findLocality(text: String, dataset: MarketDataset): MarketLocality? {
        val flat = normalizeLocation(text)
        val lower = text.lowercase()
        var best: MarketLocality? = null
        var bestLen = 0
        for (loc in dataset.localities) {
            if (loc.lat == null || loc.name.contains("district", ignoreCase = true)) continue
            val names = listOfNotNull(loc.name.substringBefore("(").trim(), loc.nameTa?.substringBefore("(")?.trim())
            for (n in names) {
                val key = normalizeLocation(n)
                if (key.length < 4) continue
                val hit = flat.contains(key) || lower.contains(n.lowercase())
                if (hit && key.length > bestLen) {
                    best = loc
                    bestLen = key.length
                }
            }
        }
        return best
    }

    private fun cleanLine(s: String): String =
        s.replace(urlRe, "").replace(Regex("""#\S+"""), "").replace(Regex("""[\p{So}\p{Cn}]"""), "")
            .replace(Regex("""\s+"""), " ").trim(' ', '-', '|', '•', ':', '*')

    fun parse(text: String, dataset: MarketDataset = MarketData.dataset): ParsedListing {
        if (text.isBlank()) return ParsedListing()
        val lines = text.lines().map(::cleanLine).filter { it.length >= 3 }
        val title = lines.firstOrNull()?.take(80)
        val description = lines.drop(1).joinToString("\n").take(1500).ifBlank { null }

        val size = findSize(text)
        val money = findMoney(text)
        val rate = money.firstOrNull { it.perUnit != null }
        val total = money.filter { it.perUnit == null }.sortedByDescending { if (it.explicit) 1 else 0 }.firstOrNull()

        var totalPrice = total?.rupees
        val rateUnit = rate?.let { if (it.perGround) AreaUnit.SQFT else it.perUnit }
        val ratePrice = rate?.let { if (it.perGround) (it.rupees / SQFT_PER_GROUND).roundToLong() else it.rupees }
        if (totalPrice == null && ratePrice != null && rateUnit != null && size != null) {
            val qty = when (rateUnit) {
                AreaUnit.CENTS -> toCents(size.first, size.second)
                AreaUnit.ACRES -> toCents(size.first, size.second) / CENTS_PER_ACRE
                AreaUnit.SQFT -> toSqft(size.first, size.second)
            }
            totalPrice = (ratePrice * qty).roundToLong()
        }
        val lower = text.lowercase()
        return ParsedListing(
            title = title,
            description = description,
            totalPrice = totalPrice,
            ratePrice = ratePrice,
            rateUnit = rateUnit,
            size = size?.first,
            unit = size?.second,
            type = findType(text),
            phones = findPhones(text),
            locality = findLocality(text, dataset),
            district = if (lower.contains("madurai") || text.contains("மதுரை")) "Madurai" else null,
        )
    }
}

// ---------------- Link previews ----------------

private val ytIdRe = Regex("""(?:youtube\.com/(?:watch\?(?:.*&)?v=|shorts/|embed/|live/)|youtu\.be/)([A-Za-z0-9_-]{6,})""")

fun youtubeId(url: String): String? = ytIdRe.find(url)?.groupValues?.get(1)

/** hqdefault thumbnail for a YouTube link, without any network call. */
fun youtubeThumb(url: String): String? = youtubeId(url)?.let { "https://img.youtube.com/vi/$it/hqdefault.jpg" }

data class LinkMeta(val title: String?, val description: String?, val image: String?, val author: String?)

private val metaRe = Regex("""<meta\s+[^>]*>""", RegexOption.IGNORE_CASE)

private fun attr(tag: String, name: String): String? =
    Regex("""$name\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)

private fun htmlDecode(s: String) = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
    .replace("&#x27;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")

/** Reads og:title / og:description / og:image from an HTML page. */
fun parseOpenGraph(html: String): LinkMeta {
    val metas = mutableMapOf<String, String>()
    for (m in metaRe.findAll(html.take(400_000))) {
        val tag = m.value
        val key = (attr(tag, "property") ?: attr(tag, "name"))?.lowercase() ?: continue
        val content = attr(tag, "content") ?: continue
        metas.putIfAbsent(key, htmlDecode(content))
    }
    val title = metas["og:title"] ?: metas["twitter:title"]
        ?: Regex("""<title[^>]*>([^<]*)</title>""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)?.let(::htmlDecode)
    return LinkMeta(
        title = title?.trim()?.ifBlank { null },
        description = (metas["og:description"] ?: metas["description"] ?: metas["twitter:description"])?.trim()?.ifBlank { null },
        image = (metas["og:image"] ?: metas["twitter:image"])?.trim()?.ifBlank { null },
        author = null,
    )
}

const val BROWSER_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

/** Fetches a link preview. YouTube uses oEmbed; others the page's Open Graph tags. Null on any failure. */
suspend fun fetchLinkMeta(url: String): LinkMeta? {
    if (url.isBlank()) return null
    if (youtubeId(url) != null) {
        val body = com.hark.sothudiary.httpGet("https://www.youtube.com/oembed?format=json&url=" + urlEncode(url), BROWSER_USER_AGENT)
        val json = body?.let { runCatching { kotlinx.serialization.json.Json.parseToJsonElement(it) as? kotlinx.serialization.json.JsonObject }.getOrNull() }
        fun f(k: String) = (json?.get(k) as? kotlinx.serialization.json.JsonPrimitive)?.content
        return LinkMeta(f("title"), null, youtubeThumb(url) ?: f("thumbnail_url"), f("author_name"))
    }
    val html = com.hark.sothudiary.httpGet(url, BROWSER_USER_AGENT) ?: return null
    return parseOpenGraph(html).takeIf { it.title != null || it.description != null || it.image != null }
}
