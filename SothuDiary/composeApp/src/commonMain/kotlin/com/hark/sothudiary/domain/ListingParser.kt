package com.hark.sothudiary.domain

import com.hark.sothudiary.data.AreaUnit
import com.hark.sothudiary.data.PropertyType
import kotlin.math.roundToLong

const val SQFT_PER_GROUND = 2400.0

/** Things a post claims about the property – shown as "claimed in the post, verify" chips. */
enum class PostClaim { DTCP, RERA, PATTA, ROAD, CORNER, EB, WATER, GATED, LOAN, NEGOTIABLE, FENCED, RESIDENTIAL_AREA }

/** What could be read from a shared caption / link preview / poster text. Every field is optional. */
data class ParsedListing(
    val title: String? = null,
    val description: String? = null,
    /** Total asking price in rupees (given, or computed from a per-unit rate × size). */
    val totalPrice: Long? = null,
    /** True when [totalPrice] was computed from a per-unit rate × size. */
    val totalFromRate: Boolean = false,
    /** A per-unit rate, e.g. ₹3.5 L per cent. */
    val ratePrice: Long? = null,
    val rateUnit: AreaUnit? = null,
    val size: Double? = null,
    val unit: AreaUnit? = null,
    /** Built-up area of a house / flat in sq ft, when the post gives land and built-up separately. */
    val builtUpSqft: Double? = null,
    val type: PropertyType? = null,
    val phones: List<String> = emptyList(),
    /** A name found next to the phone number / "Contact:" (broker or owner). */
    val brokerName: String? = null,
    /** District named in the text, as a gazetteer entry. */
    val place: KnownPlace? = null,
    /** Best place name found in the text (village / town / area), for geocoding. */
    val placeText: String? = null,
    /** All place names found, best first ("Location: X", "near X", "Xஇல்", hashtags …). */
    val placeCandidates: List<String> = emptyList(),
    /** District named in the text, e.g. "Tenkasi". */
    val district: String? = null,
    val claims: Set<PostClaim> = emptySet(),
) {
    /** Query for Nominatim: the most specific place we know plus its district. */
    fun geocodeQuery(): String? = geocodeQueries().firstOrNull()

    /** Nominatim queries to try in order: each place candidate with the district (if any). */
    fun geocodeQueries(): List<String> = placeCandidates.map { name ->
        listOfNotNull(name, district?.takeIf { !name.contains(it, ignoreCase = true) }).joinToString(", ")
    }
}

/**
 * Pulls listing details out of free text in English / Tamil / Indian notation.
 * Pure Kotlin, no platform calls – see ListingParserTest.
 */
object ListingParser {

    private const val NUM = """([0-9][0-9,]*(?:\.[0-9]+)?)"""
    private const val MULT = """(crores?|cr\b|கோடி|lakhs?|laksh|lacs?|lac\b|l\b|இலட்சம்|இலட்ச|லட்சம்|லட்ச|லக்ஷம்|k\b|thousand|ஆயிரம்)"""
    private val moneyRe = Regex("""(₹|rs\.?|inr|rate|price|cost|விலை)?\s*:?\s*$NUM\s*$MULT?""", RegexOption.IGNORE_CASE)
    private const val UNIT_WORDS = """(cents?|சென்ட|செண்ட|சென்டு|sq\.?\s*ft\.?|sqft|sft|sq\.?\s*feet|square\s*feet|ச\.?\s*அடி|சதுர\s*அடி|acres?|ஏக்கர|grounds?|கிரவுண்ட்|கிரௌண்ட்)"""
    private val sizeRe = Regex("""$NUM\s*-?\s*$UNIT_WORDS""", RegexOption.IGNORE_CASE)
    private val unitBeforeRe = Regex("""(?:per|/|ஒரு|rate|விலை|@)?\s*$UNIT_WORDS[^\s0-9₹]{0,6}\s*(?:rate|price|விலை|:|-|=|@)?\s*(₹|rs\.?)?\s*$""", RegexOption.IGNORE_CASE)
    private val unitAfterRe = Regex("""^\s*(?:/-)?\s*(?:/|per|a|ஒரு|for\s+1|for\s+one)\s*-?\s*$UNIT_WORDS""", RegexOption.IGNORE_CASE)
    private val unitAfterTamilRe = Regex("""^\s*(?:ஒரு\s*)?$UNIT_WORDS[^\s0-9]{0,4}(?:க்கு|கு)""", RegexOption.IGNORE_CASE)
    private val phoneRe = Regex("""(?<![0-9])(?:\+?91[\s-]?|0)?([6-9][0-9]{4}[\s-]?[0-9]{5})(?![0-9])""")
    private val urlRe = Regex("""https?://\S+""")
    private val dimsRe = Regex("""(?<![0-9.])([1-9][0-9]{1,2}(?:\.[0-9])?)\s*(?:x|X|×|\*)\s*([1-9][0-9]{1,2}(?:\.[0-9])?)(?![0-9])""")
    private val builtUpRe = Regex("""(?:built[\s-]*up|build[\s-]*up|buildup|super\s*built|carpet|கட்டிட\s*பரப்பளவு|கட்டிடம்)\s*(?:area)?\s*[:\-–]?\s*$NUM\s*(?:sq\.?\s*ft|sqft|sft|ச\.?\s*அடி|சதுர\s*அடி)""", RegexOption.IGNORE_CASE)

    fun unitOf(word: String): AreaUnit? {
        val w = word.lowercase()
        return when {
            w.startsWith("cent") || w.contains("சென்ட") || w.contains("செண்ட") || w.contains("சென்டு") -> AreaUnit.CENTS
            w.startsWith("acre") || w.contains("ஏக்கர") -> AreaUnit.ACRES
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

    data class Money(val rupees: Long, val perUnit: AreaUnit?, val perGround: Boolean, val explicit: Boolean, val at: Int = 0)

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
            // A number directly followed by a unit word is a size, not a price ("5 cents").
            val afterRaw = text.substring(m.range.last + 1, minOf(text.length, m.range.last + 14))
            if (mult == null && Regex("""^\s*-?\s*$UNIT_WORDS""", RegexOption.IGNORE_CASE).containsMatchIn(afterRaw)) continue
            val rupees = (num * multiplier(mult)).roundToLong()
            if (rupees < 1_000) continue
            // Is this a rate per cent / sq ft / acre / ground?
            val before = text.substring(maxOf(0, m.range.first - 28), m.range.first)
            val after = text.substring(m.range.last + 1, minOf(text.length, m.range.last + 30))
            val beforeLine = before.substringAfterLast('\n')
            // "1450 sqft, price 65 lakhs": a unit right after a number is the size, not a "per unit" rate.
            val beforeUnit = unitBeforeRe.find(beforeLine)
                ?.takeUnless { u -> Regex("""[0-9]\s*-?\s*$""").containsMatchIn(beforeLine.substring(0, u.range.first + (u.value.length - u.value.trimStart().length))) }
                ?.groupValues?.get(1)
            val afterUnit = unitAfterRe.find(after)?.groupValues?.get(1) ?: unitAfterTamilRe.find(after)?.groupValues?.get(1)
            val unitWord = afterUnit ?: beforeUnit
            out += Money(
                rupees = rupees,
                perUnit = unitWord?.let { unitOf(it) ?: AreaUnit.SQFT },
                perGround = unitWord != null && isGround(unitWord),
                explicit = mult != null || hasCurrency,
                at = m.range.first,
            )
        }
        return out
    }

    fun findSize(text: String): Pair<Double, AreaUnit>? {
        val builtUpStarts = builtUpRe.findAll(text).map { it.range }.toList()
        for (m in sizeRe.findAll(text)) {
            val num = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: continue
            if (num <= 0) continue
            // "₹3 L per cent" style rates are not sizes.
            val before = text.substring(maxOf(0, m.range.first - 12), m.range.first)
            if (before.contains('₹') || Regex("""(?i)(rs\.?|per|விலை)\s*:?\s*$""").containsMatchIn(before)) continue
            if (builtUpStarts.any { m.range.first in it }) continue
            // "1.5 km" style distances are not matched by UNIT_WORDS, but "/sqft" after a price is: skip "2,000 per sq ft".
            val after = text.substring(m.range.last + 1, minOf(text.length, m.range.last + 8))
            if (Regex("""^\s*(க்கு|கு)""").containsMatchIn(after)) continue
            val word = m.groupValues[2]
            return if (isGround(word)) num * SQFT_PER_GROUND to AreaUnit.SQFT else num to (unitOf(word) ?: AreaUnit.SQFT)
        }
        // Plot dimensions "40 x 50" / "24 × 45" -> sq ft
        val d = dimsRe.find(text)
        if (d != null) {
            val a = d.groupValues[1].toDoubleOrNull()
            val b = d.groupValues[2].toDoubleOrNull()
            if (a != null && b != null && a in 10.0..300.0 && b in 10.0..300.0) return a * b to AreaUnit.SQFT
        }
        return null
    }

    fun findBuiltUp(text: String): Double? = builtUpRe.find(text)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()

    fun findType(text: String): PropertyType? {
        val t = text.lowercase()
        fun has(vararg k: String) = k.any { t.contains(it) }
        return when {
            has("house plot", "வீட்டு மனை", "வீட்டுமனை", "dtcp", "layout", "மனைப்பிரிவு", "plots for sale", "plot for sale", "residential plot", "approved plot") -> PropertyType.PLOT
            has("apartment", "flat for sale", "flats", " flat ", "bhk flat", "அடுக்குமாடி", "அபார்ட்மெண்ட்") -> PropertyType.APARTMENT
            has("villa", "individual house", "independent house", "house for sale", "home for sale", "home sale", "வீடு விற்பனைக்கு", "தனி வீடு", "duplex", "bhk house", "bhk home", "bhk", "வீடு", "veedu") -> PropertyType.HOUSE
            has("farm land", "farmland", "farm house", "பண்ணை", "தோட்டம்", "coconut", "தென்னந்தோப்பு", "தோப்பு", "mango grove", "grove", "estate") -> PropertyType.FARMLAND
            has("agricultural", "agri land", "agriculture", "விவசாய", "நஞ்சை", "புஞ்சை", "wet land", "dry land", "வயல்", "செம்மண் நிலம்") -> PropertyType.AGRI_LAND
            has("plot", "மனை", "site for sale", "plots", "பிளாட்") -> PropertyType.PLOT
            has("land", "நிலம்", "இடம் விற்பனைக்கு", "காலி இடம்") -> PropertyType.LAND
            else -> null
        }
    }

    fun findPhones(text: String): List<String> =
        phoneRe.findAll(text).map { it.groupValues[1].filter(Char::isDigit) }.filter { it.length == 10 }.distinct().toList()

    private val nameStop = setOf(
        "contact", "call", "phone", "mobile", "mob", "whatsapp", "booking", "for", "details", "more", "info", "or", "and",
        "us", "me", "now", "today", "price", "rate", "cell", "no", "number", "ph", "tel", "dm", "enquiry", "visit", "site",
        "tenkasi", "madurai", "properties", "property", "realestate", "real", "estate", "land", "plot", "sale", "sales", "website",
        "group", "join", "follow", "subscribe", "youtube", "instagram", "facebook", "email", "office", "lakhs", "lakh", "only",
    )

    /** A person's name next to the phone number or after "Contact:" / "Owner:" / "தொடர்புக்கு:". */
    fun findBrokerName(text: String): String? {
        val labelled = Regex(
            """(?:owner|contact\s*person|broker|agent|name|தொடர்புக்கு|தொடர்பு\s*கொள்ள|பெயர்|உரிமையாளர்)\s*[:\-–]+\s*([A-Za-z஀-௿][A-Za-z஀-௿. ]{2,28})""",
            RegexOption.IGNORE_CASE,
        )
        for (m in labelled.findAll(text)) {
            val cand = cleanName(m.groupValues[1])
            if (cand != null) return cand
        }
        val phone = """(?:\+?91[\s-]?)?[6-9][0-9]{4}[\s-]?[0-9]{5}"""
        Regex("""$phone\s*[-–(:]?\s*([A-Z][A-Za-z.]{1,20}(?:\s+[A-Z][A-Za-z.]{1,20})?|[஀-௿]{2,15}(?:\s?[A-Z])?)""").findAll(text).forEach { m ->
            cleanName(m.groupValues[1])?.let { return it }
        }
        Regex("""([A-Z][A-Za-z]{2,20}(?:\s+[A-Z][A-Za-z.]{0,20})?)\s*[:\-–]?\s*$phone""").findAll(text).forEach { m ->
            cleanName(m.groupValues[1])?.let { return it }
        }
        return null
    }

    private fun cleanName(raw: String): String? {
        val words = raw.trim().trim('.', '-', ' ').split(Regex("""\s+""")).filter { it.isNotBlank() }
        val kept = words.takeWhile { w -> w.lowercase().trim('.') !in nameStop && w.none(Char::isDigit) }
        val name = kept.take(3).joinToString(" ").trim('.', ' ')
        return name.takeIf { it.length >= 3 && it.lowercase() !in nameStop }
    }

    fun findClaims(text: String): Set<PostClaim> {
        val t = text.lowercase()
        fun has(vararg k: String) = k.any { t.contains(it) }
        val out = mutableSetOf<PostClaim>()
        if (has("dtcp", "டிடிசிபி", "approved plot", "approval", "அப்ரூவல்", "அங்கீகாரம்", "அனுமதி பெற்ற")) out += PostClaim.DTCP
        if (has("rera")) out += PostClaim.RERA
        if (has("patta", "பட்டா")) out += PostClaim.PATTA
        if (has("road", "ரோடு", "சாலை", "பாதை வசதி", "அடி பாதை", "தார் ரோ", "way ", "frontage", "முகப்பு")) out += PostClaim.ROAD
        if (has("corner", "மூலை")) out += PostClaim.CORNER
        if (has(" eb", "eb ", "electricity", "மின்சார", "மின் இணைப்பு", "current connection")) out += PostClaim.EB
        if (has("water", "well", "borewell", "bore well", "கிணறு", "போர்வெல்", "குடிநீர்", "தண்ணீர்", "பாசன")) out += PostClaim.WATER
        if (has("gated", "compound wall", "கேட்டட்")) out += PostClaim.GATED
        if (has("loan", "emi", "கடன் வசதி", "லோன்")) out += PostClaim.LOAN
        if (has("negotiable", "பேச்சுவார்த்தை", "nego")) out += PostClaim.NEGOTIABLE
        if (has("fenc", "வேலி")) out += PostClaim.FENCED
        if (has("residential area", "குடியிருப்பு பகுதி", "சுற்றிலும் வீடுகள்", "houses around")) out += PostClaim.RESIDENTIAL_AREA
        return out
    }

    private val placeLabelRe = Regex(
        """(?:location|loc|place|area|village|address|இடம்|ஊர்|பகுதி|கிராமம்|முகவரி|📍)\s*[:：\-–]*\s*([^\n,|]{3,60})""",
        RegexOption.IGNORE_CASE,
    )

    /** "Location: X" / "இடம்: X" free text, cleaned of distances and hashtags. */
    fun findPlaceText(text: String): String? {
        for (m in placeLabelRe.findAll(text)) {
            val v = m.groupValues[1].replace(Regex("""#\S+"""), "").replace(Regex("""[\p{So}]"""), "")
                .replace(Regex("""\(.*?\)"""), "").trim(' ', '-', ':', '.', '•')
            if (v.length < 3 || v.any(Char::isDigit) && v.count(Char::isDigit) > 3) continue
            if (v.lowercase().startsWith("http")) continue
            return v.take(60)
        }
        return null
    }

    private val placeStop = setOf(
        "tamil nadu", "tamilnadu", "india", "main road", "bus stand", "bus stop", "railway station", "dtcp", "rera", "patta",
        "for sale", "sale", "plot", "plots", "land", "house", "villa", "flat", "acre", "acres", "cent", "cents", "road", "the", "this",
        "temple", "school", "college", "hospital", "highway", "nh", "sh", "town", "city", "village", "district", "taluk", "contact",
        "call", "whatsapp", "price", "rate", "approved", "corner", "east", "west", "north", "south", "facing", "near", "location",
        "விற்பனைக்கு", "விற்பனை", "சாலை", "மெயின்", "தெரு", "கோவில்", "கோயில்", "பள்ளி", "மனை", "நிலம்", "வீடு", "இடம்",
        "தொடர்புக்கு", "அருகில்", "அருகே", "மாவட்டம்", "தாலுகா", "கிராமம்", "நகர்", "பஸ்", "ஸ்டாண்ட்", "அழகிய", "விவசாய",
    )

    private fun cleanPlace(v0: String): String? {
        val v = v0.replace(Regex("""#"""), "").replace(Regex("""[\p{So}\p{Sk}\uFE0F\u200D]"""), "").replace(Regex("""\s+"""), " ").trim(' ', '-', ':', '.', ',', '•', '|')
        if (v.length < 3 || v.length > 40) return null
        if (v.count(Char::isDigit) > 0) return null
        if (v.lowercase() in placeStop) return null
        if (Places.districts.any { it.name.equals(v, true) || it.nameTa == v }) return null
        return v
    }

    /** Tamil locative / "near" forms → place name: பண்பொழியில் → பண்பொழி, கடையத்தில் → கடையம், ஆலங்குளம் அருகே → ஆலங்குளம். */
    fun tamilPlaceStem(w: String): String? {
        val t = w.trim()
        val stem = when {
            t.endsWith("த்தில்") -> t.removeSuffix("த்தில்") + "ம்"
            t.endsWith("யில்") -> t.removeSuffix("யில்")
            t.endsWith("வில்") -> t.removeSuffix("வில்")
            t.endsWith("ிலுள்ள") -> t.removeSuffix("ிலுள்ள") + "்"
            t.endsWith("ில்") -> t.removeSuffix("ில்") + "்"
            else -> t
        }
        return stem.takeIf { it.length >= 3 && it.all { c -> c in '\u0B80'..'\u0BFF' } }
    }

    private val nearEnRe = Regex("""\b(?:in|at|near|nr\.?|opp\.?|behind)\s+([A-Z][A-Za-z]{2,}(?:\s+[A-Z][A-Za-z]{2,}){0,2})""")
    private val nearTaRe = Regex("""([\u0B80-\u0BFF]{3,})\s*(?:அருகில்|அருகே|அருகாமையில்|பக்கத்தில்)""")
    private val locativeTaRe = Regex("""(?<![\u0B80-\u0BFF])([\u0B80-\u0BFF]{3,}(?:த்தில்|யில்|வில்|ில்))(?![\u0B80-\u0BFF])""")
    private val hashtagRe = Regex("""#([A-Za-z]{4,}|[\u0B80-\u0BFF]{3,})""")
    private val hashStop = Regex("""(?i)(sale|plot|land|house|villa|realestate|real|estate|property|properties|reels?|trending|viral|explore|investment|dtcp|approved|home|farm)""")

    /**
     * Place names in the text, best first. Not tied to any area: no gazetteer of towns – the names are
     * resolved later with Nominatim (which also understands Tamil names).
     */
    fun placeCandidates(text: String): List<String> {
        val out = LinkedHashSet<String>()
        findPlaceText(text)?.let { v ->
            // "Panpoli, Tenkasi" → "Panpoli"; Tamil locative forms are stemmed.
            v.split(',', '/', '|').map { it.trim() }.forEach { part ->
                val tp = part.split(' ').firstOrNull()?.let { w -> tamilPlaceStem(w) }
                cleanPlace(if (tp != null && part.split(' ').size == 1) tp else part)?.let(out::add)
            }
        }
        val lines = text.lines()
        // Title line first: "பண்பொழியில் 50 சென்ட் …", "5 cent plot at Panpoli"
        for (line in lines.take(3) + lines.drop(3)) {
            nearTaRe.findAll(line).forEach { m -> tamilPlaceStem(m.groupValues[1])?.let(::cleanPlace)?.let(out::add) }
            locativeTaRe.findAll(line).forEach { m -> tamilPlaceStem(m.groupValues[1])?.let(::cleanPlace)?.let(out::add) }
            nearEnRe.findAll(line).forEach { m -> cleanPlace(m.groupValues[1])?.let(out::add) }
        }
        hashtagRe.findAll(text).forEach { m ->
            val h = m.groupValues[1]
            if (!hashStop.containsMatchIn(h)) cleanPlace(h)?.let(out::add)
        }
        return out.toList().take(6)
    }

    private val noiseLine = Regex("""^(instagram|facebook|youtube|reels?|video|watch|follow|subscribe|like|share)\b.*""", RegexOption.IGNORE_CASE)

    private fun cleanLine(s: String): String =
        s.replace(urlRe, "").replace(Regex("""#\S+"""), "").replace(Regex("""@\S+"""), "")
            .replace(Regex("""[\p{So}\p{Sk}\p{Cn}\p{Cs}\uFE0F\u200D]"""), "")
            .replace(Regex("""\s+"""), " ").trim(' ', '-', '|', '•', ':', '*', '_', '~', '"', '“', '”')

    private fun isMeaningful(line: String): Boolean {
        val letters = line.count { it.isLetter() }
        return letters >= 4 && !noiseLine.matches(line) && !Regex("""^[\d\s,.+-]+$""").matches(line)
    }

    /** Strips Instagram's og prefix ("3 likes, 1 comments - user on Nov 7, 2024: "…""). */
    fun stripSocialPrefix(text: String): String {
        val m = Regex("""^\s*[\d.,]+[KkMm]?\s+likes?,\s*[\d.,]+[KkMm]?\s+comments?\s*-\s*\S+\s+on\s+[^:]+:\s*["“]""").find(text)
        return if (m != null) text.substring(m.range.last + 1).trimEnd().removeSuffix(".").removeSuffix("\"").removeSuffix("”") else text
    }

    fun parse(text0: String): ParsedListing {
        if (text0.isBlank()) return ParsedListing()
        val text = stripSocialPrefix(text0)
        val lines = text.lines().map(::cleanLine).filter { it.length >= 3 }
        val titleIdx = lines.indexOfFirst(::isMeaningful)
        val title = lines.getOrNull(titleIdx)?.take(90)
        val description = lines.filterIndexed { i, l -> i != titleIdx && l.isNotBlank() }.joinToString("\n").take(2000).ifBlank { null }

        val type = findType(text)
        val size = findSize(text)
        val builtUp = findBuiltUp(text)
        val money = findMoney(text)
        val rate = money.firstOrNull { it.perUnit != null }
        // A total is an explicit amount that is not a per-unit rate; prefer the largest explicit one.
        val totals = money.filter { it.perUnit == null }
        val total = totals.filter { it.explicit }.maxByOrNull { it.rupees } ?: totals.firstOrNull()

        var totalPrice = total?.rupees
        var fromRate = false
        val rateUnit = rate?.let { if (it.perGround) AreaUnit.SQFT else it.perUnit }
        val ratePrice = rate?.let { if (it.perGround) (it.rupees / SQFT_PER_GROUND).roundToLong() else it.rupees }
        if (ratePrice != null && rateUnit != null && size != null) {
            val qty = when (rateUnit) {
                AreaUnit.CENTS -> toCents(size.first, size.second)
                AreaUnit.ACRES -> toCents(size.first, size.second) / CENTS_PER_ACRE
                AreaUnit.SQFT -> toSqft(size.first, size.second)
            }
            val computed = (ratePrice * qty).roundToLong()
            // If the "total" found is smaller than the rate itself it was probably a different number.
            if (totalPrice == null || totalPrice < ratePrice) { totalPrice = computed; fromRate = true }
        }

        val places = Places.findIn(text)
        val districtHit = places.firstOrNull { it.isDistrict }
        val lower = text.lowercase()
        val district = when {
            // "தென்காசி மாவட்டம்" / "Tenkasi district" wins over anything else.
            else -> Places.districts.firstOrNull { d ->
                lower.contains(d.name.lowercase() + " district") || lower.contains(d.name.lowercase() + " dt") ||
                    text.contains(Places.tamilStem(d.nameTa) + "ி மாவட்ட") || text.contains(d.nameTa + " மாவட்ட") ||
                    text.contains(Places.tamilStem(d.nameTa) + " மாவட்ட")
            }?.name ?: districtHit?.name
        }
        val candidates = placeCandidates(text)

        return ParsedListing(
            title = title,
            description = description,
            totalPrice = totalPrice,
            totalFromRate = fromRate,
            ratePrice = ratePrice,
            rateUnit = rateUnit,
            size = size?.first,
            unit = size?.second,
            builtUpSqft = builtUp,
            type = type,
            phones = findPhones(text),
            brokerName = findBrokerName(text),
            place = districtHit,
            placeText = candidates.firstOrNull(),
            placeCandidates = candidates,
            district = district,
            claims = findClaims(text),
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

private fun attr(tag: String, name: String): String? {
    val m = Regex("""(?:^|\s)$name\s*=\s*(?:"([^"]*)"|'([^']*)')""", RegexOption.IGNORE_CASE).find(tag) ?: return null
    return m.groupValues[1].ifEmpty { m.groupValues[2] }
}

private fun htmlDecode(s: String) = com.hark.sothudiary.domain.social.htmlDecode(s)

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
