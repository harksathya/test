package com.hark.sothudiary.domain.docs

import com.hark.sothudiary.data.DocAnalysis
import com.hark.sothudiary.data.DocFlag
import com.hark.sothudiary.data.DocKind
import com.hark.sothudiary.data.DocReading
import com.hark.sothudiary.data.EcEntry
import com.hark.sothudiary.data.FlagLevel
import com.hark.sothudiary.data.LandRecord
import com.hark.sothudiary.data.OwnershipLink
import com.hark.sothudiary.domain.social.arr
import com.hark.sothudiary.domain.social.get
import com.hark.sothudiary.domain.social.parseJsonOrNull
import com.hark.sothudiary.domain.social.str
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * 2.8.0: documents of a property (EC, patta, chitta, adangal, sale deed) read by the user's own AI provider, then
 * checked HERE with fixed rules: the ownership chain for the property's survey / patta number, and red flags.
 *
 * The AI only transcribes what is printed (every EC entry, each document's holders / survey numbers / extents). The
 * chain and most flags are computed by [buildAnalysis] in common code, so they are repeatable and tested (V280Test);
 * flags the AI itself raises are kept separately, marked as AI flags.
 */
object DocRules {
    /** Banks and lawyers usually ask for an EC of at least 30 years. */
    const val MIN_EC_YEARS = 30
    /** An EC older than this (days) should be taken again just before registration. */
    const val FRESH_EC_DAYS = 30
    /** A sale below this share of the previous sale's value is flagged (possible under-valuation). */
    const val VALUE_DROP_RATIO = 0.5
    /** Whole request to the AI (all files) at most this size – Gemini's inline limit is 20 MB. */
    const val MAX_TOTAL_BYTES = 18_000_000
}

/** Nature of an EC entry, as a code (the AI picks one; [natureFromText] is the fallback from the printed words). */
object Nature {
    const val SALE = "SALE"
    const val GIFT = "GIFT"
    const val SETTLEMENT = "SETTLEMENT"
    const val PARTITION = "PARTITION"
    const val WILL = "WILL"
    const val EXCHANGE = "EXCHANGE"
    const val RELEASE = "RELEASE"
    const val MORTGAGE = "MORTGAGE"
    const val MORTGAGE_RELEASE = "MORTGAGE_RELEASE"
    const val AGREEMENT = "AGREEMENT"
    const val POWER = "POWER"
    const val POWER_CANCEL = "POWER_CANCEL"
    const val LEASE = "LEASE"
    const val ATTACHMENT = "ATTACHMENT"
    const val COURT = "COURT"
    const val CANCELLATION = "CANCELLATION"
    const val OTHER = "OTHER"

    /** Natures that pass ownership to the claimant(s). */
    val transfers = setOf(SALE, GIFT, SETTLEMENT, PARTITION, WILL, EXCHANGE, RELEASE)
    val all = transfers + setOf(MORTGAGE, MORTGAGE_RELEASE, AGREEMENT, POWER, POWER_CANCEL, LEASE, ATTACHMENT, COURT, CANCELLATION, OTHER)
}

/** Nature code from the printed nature of an EC entry (English or Tamil). Order matters: "release of mortgage" first. */
fun natureFromText(text: String): String {
    val t = text.lowercase()
    fun has(vararg w: String) = w.any { it in t }
    return when {
        has("receipt", "discharge", "reconveyance", "ரசீது", "மீட்பு", "redemption") -> Nature.MORTGAGE_RELEASE
        has("release of mortgage", "mortgage release") -> Nature.MORTGAGE_RELEASE
        has("cancellation of power", "revocation", "power cancel", "அதிகார ரத்து", "பவர் ரத்து") -> Nature.POWER_CANCEL
        has("cancel", "ரத்து") -> Nature.CANCELLATION
        has("mortgage", "அடமான", "அடைமான", "hypothec") -> Nature.MORTGAGE
        has("attachment", "ஜப்தி", "lis pendens", "injunction") -> Nature.ATTACHMENT
        has("court", "decree", "நீதிமன்", "தீர்ப்பு") -> Nature.COURT
        has("power of attorney", "power", "பவர்", "அதிகார ஆவண", "முகவர்") -> Nature.POWER
        has("agreement", "ஒப்பந்த", "உடன்படிக்கை") -> Nature.AGREEMENT
        has("lease", "குத்தகை") -> Nature.LEASE
        has("settlement", "செட்டில்", "தான செட்டில்") -> Nature.SETTLEMENT
        has("gift", "தானம்", "தான") -> Nature.GIFT
        has("partition", "பாகப்பிரிவினை", "பாகம்") -> Nature.PARTITION
        has("will", "உயில்") -> Nature.WILL
        has("exchange", "பரிவர்த்தனை") -> Nature.EXCHANGE
        has("release", "விடுதலை") -> Nature.RELEASE
        has("sale", "conveyance", "கிரய", "விற்பனை", "கிரையம்") -> Nature.SALE
        else -> Nature.OTHER
    }
}

// ------------------------------------------------------------------------------------------------ names and numbers

/** Words of a name that identify a person: initials, titles and relation words are dropped. */
fun nameTokens(name: String): Set<String> {
    val stop = setOf("s/o", "d/o", "w/o", "son", "daughter", "wife", "of", "late", "mr", "mrs", "smt", "shri", "sri", "thiru", "tmt",
        "திரு", "திருமதி", "செல்வி", "மகன்", "மகள்", "மனைவி", "க/பெ", "த/பெ", "து/பெ", "கணவர்", "தந்தை", "காலம்சென்ற", "and", "மற்றும்")
    return name.lowercase()
        .replace(Regex("[.,;:()\\[\\]{}'\"`-]"), " ")
        .split(Regex("\\s+"))
        .map { it.trim() }
        .filter { it.length >= 3 && it !in stop && !it.contains('/') && it.any { c -> c.isLetter() } }
        .toSet()
}

/** True when two lists of names share a person (any identifying word in common). */
fun samePeople(a: List<String>, b: List<String>): Boolean {
    val ta = a.flatMap { nameTokens(it) }.toSet()
    val tb = b.flatMap { nameTokens(it) }.toSet()
    return ta.isNotEmpty() && tb.isNotEmpty() && ta.any { x -> tb.any { y -> x == y || (x.length >= 5 && y.length >= 5 && (x.startsWith(y) || y.startsWith(x))) } }
}

/** "123/4B", "S.F.No. 123/4 B", "123-4B" -> "123/4B" (for comparing survey numbers). */
fun normSurvey(s: String): String =
    s.uppercase().replace(Regex("S\\.?\\s*F\\.?\\s*(NO\\.?)?|SURVEY\\s*(NO\\.?)?|புல\\s*எண்|R\\.?\\s*S\\.?\\s*(NO\\.?)?"), "")
        .replace('-', '/').replace(Regex("\\s+"), "").trim('.', ':', ' ')

/** Survey "123" matches "123/4B" and "123/4B" matches "123/4B1"? Only by prefix on a "/" boundary or exactly. */
fun surveyMatches(target: String, other: String): Boolean {
    val a = normSurvey(target)
    val b = normSurvey(other)
    if (a.isBlank() || b.isBlank()) return false
    if (a == b) return true
    val (short, long) = if (a.length <= b.length) a to b else b to a
    if (!long.startsWith(short)) return false
    val next = long[short.length]
    // "123" ~ "123/4B" (subdivisions of 123); "123/4" ~ "123/4B" (a lettered part of 4); never "123" ~ "1234".
    return next == '/' || (next.isLetter() && short.contains('/'))
}

/** "910/2010" / "Doc No. 0910" -> "910" (the document number without its year). */
fun docNumber(s: String): String = Regex("\\d+").find(s)?.value?.trimStart('0').orEmpty()

/** Year of an entry: its own year, else a 4-digit year in its date text. */
fun EcEntry.yearOrNull(): Int? = year ?: Regex("(19|20)\\d{2}").find(date)?.value?.toIntOrNull()

/** Sort key: year, then the date text, then the document number. */
private fun EcEntry.sortKey(): String = (yearOrNull() ?: 0).toString().padStart(4, '0') + "|" + isoDate(date) + "|" + docNo.padStart(8, '0')

/** "12-03-2015" / "12/03/2015" / "2015-03-12" -> "2015-03-12" (for sorting); "" when not a date. */
fun isoDate(d: String): String {
    Regex("(\\d{4})-(\\d{1,2})-(\\d{1,2})").find(d)?.let { m -> return m.groupValues[1] + "-" + m.groupValues[2].padStart(2, '0') + "-" + m.groupValues[3].padStart(2, '0') }
    Regex("(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{4})").find(d)?.let { m -> return m.groupValues[3] + "-" + m.groupValues[2].padStart(2, '0') + "-" + m.groupValues[1].padStart(2, '0') }
    return ""
}

// ------------------------------------------------------------------------------------------------ the AI's answer

/** Pulls the JSON object out of an AI answer (which may wrap it in ```json fences or add words around it). */
fun extractJsonObject(text: String): String? {
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    return if (start >= 0 && end > start) text.substring(start, end + 1) else null
}

private fun JsonElement?.strList(): List<String> = when {
    this == null -> emptyList()
    this is JsonPrimitive -> listOfNotNull(this.str()?.takeIf { it.isNotBlank() })
    else -> arr().mapNotNull { it.str()?.trim()?.takeIf { s -> s.isNotBlank() } }
}

private fun JsonElement?.longValue(): Long? {
    val p = this as? JsonPrimitive ?: return null
    p.longOrNull?.let { return it }
    p.doubleOrNull?.let { return it.toLong() }
    // "Rs. 12,50,000" / "₹12,50,000.00"
    val num = p.content.replace(Regex("(?i)rs\\.?|inr|₹|ரூ\\.?"), "").filter { it.isDigit() || it == '.' }.trim('.')
    val digits = num.substringBefore('.')
    return digits.takeIf { it.isNotEmpty() && it.length <= 13 }?.toLongOrNull()
}

private fun JsonElement?.intValue(): Int? = longValue()?.toInt()

/** What the AI read from the documents (before the rules run). */
data class AiReading(
    val entries: List<EcEntry>,
    val docs: List<DocReading>,
    val ecFrom: String,
    val ecTo: String,
    val aiFlags: List<DocFlag>,
    val summaryEn: String,
    val summaryTa: String,
)

/** Parses the AI's JSON ([analysisPrompt] describes it). Null when the answer has no usable JSON. */
fun parseAiReading(text: String): AiReading? {
    val json = extractJsonObject(text) ?: return null
    val root = parseJsonOrNull(json)
    if (root["entries"] == null && root["documents"] == null) return null
    val entries = root["entries"].arr().map { e ->
        val natureText = e["nature"].str().orEmpty()
        val code = e["natureCode"].str()?.uppercase()?.takeIf { it in Nature.all } ?: natureFromText(natureText)
        EcEntry(
            serial = e["serial"].str().orEmpty(),
            docNo = e["docNo"].str().orEmpty(),
            year = e["year"].intValue(),
            date = e["date"].str().orEmpty(),
            nature = natureText,
            natureCode = code,
            executants = e["executants"].strList(),
            claimants = e["claimants"].strList(),
            value = e["value"].longValue(),
            surveyNos = e["surveyNos"].strList(),
            extent = e["extent"].str().orEmpty(),
            viaPowerAgent = (e["viaPowerAgent"] as? JsonPrimitive)?.booleanOrNull == true,
            refDocNo = e["refDocNo"].str().orEmpty(),
            remarks = e["remarks"].str().orEmpty(),
        )
    }
    val docs = root["documents"].arr().map { d ->
        DocReading(
            kind = runCatching { DocKind.valueOf(d["kind"].str().orEmpty().uppercase()) }.getOrDefault(DocKind.OTHER),
            holders = d["holders"].strList(),
            pattaNo = d["pattaNo"].str().orEmpty(),
            surveyNos = d["surveyNos"].strList(),
            extent = d["extent"].str().orEmpty(),
            landClass = d["landClass"].str().orEmpty(),
            village = d["village"].str().orEmpty(),
            docNo = d["docNo"].str().orEmpty(),
            date = d["date"].str().orEmpty(),
            value = d["value"].longValue(),
            notes = d["notes"].str().orEmpty(),
        )
    }
    val flags = root["flags"].arr().mapNotNull { f ->
        val en = f["en"].str().orEmpty()
        val ta = f["ta"].str().orEmpty()
        if (en.isBlank() && ta.isBlank()) null
        else DocFlag(
            code = "AI_" + (f["code"].str()?.uppercase()?.filter { it.isLetterOrDigit() || it == '_' }?.take(30) ?: "NOTE"),
            level = runCatching { FlagLevel.valueOf(f["level"].str().orEmpty().uppercase()) }.getOrDefault(FlagLevel.MEDIUM),
            en = en.ifBlank { ta }, ta = ta.ifBlank { en },
            ref = f["ref"].str().orEmpty(), byAi = true,
        )
    }
    return AiReading(
        entries, docs,
        ecFrom = root["ecFrom"].str().orEmpty(), ecTo = root["ecTo"].str().orEmpty(),
        aiFlags = flags,
        summaryEn = root["summaryEn"].str().orEmpty(), summaryTa = root["summaryTa"].str().orEmpty(),
    )
}

// ------------------------------------------------------------------------------------------------ chain and flags

/** Does this EC entry concern the property (its survey number), when the property's survey number is known? */
fun EcEntry.concerns(land: LandRecord): Boolean {
    val target = land.surveyFull.ifBlank { land.surveyNo }
    if (target.isBlank() || surveyNos.isEmpty()) return true
    return surveyNos.any { surveyMatches(target, it) || surveyMatches(land.surveyNo, it) }
}

/** The ownership chain: transfers of this property, oldest first. */
fun ownershipChain(entries: List<EcEntry>, land: LandRecord): List<OwnershipLink> =
    entries.filter { it.natureCode in Nature.transfers && it.concerns(land) }
        .sortedBy { it.sortKey() }
        .map { e ->
            OwnershipLink(
                docNo = e.docNo, year = e.yearOrNull(), date = e.date, nature = e.natureCode,
                from = e.executants, to = e.claimants, value = e.value, viaPowerAgent = e.viaPowerAgent, surveyNos = e.surveyNos,
            )
        }

private fun docRef(docNo: String, year: Int?): String = listOfNotNull(docNo.ifBlank { null }, year?.toString()).joinToString("/")

/**
 * The fixed checks (each flag in English and Tamil). [now] is "yyyy-mm-dd" (today), [hasEc] whether an EC was uploaded.
 */
fun ruleFlags(
    entries: List<EcEntry>, chain: List<OwnershipLink>, docs: List<DocReading>, land: LandRecord,
    ecFrom: String, ecTo: String, hasEc: Boolean, now: String,
): List<DocFlag> {
    val out = mutableListOf<DocFlag>()
    fun flag(code: String, level: FlagLevel, en: String, ta: String, ref: String = "") { out += DocFlag(code, level, en, ta, ref) }
    val mine = entries.filter { it.concerns(land) }.sortedBy { it.sortKey() }

    if (!hasEc) flag("NO_EC", FlagLevel.HIGH,
        "No EC uploaded. Get the EC (Encumbrance Certificate) for at least 30 years from tnreginet.gov.in before paying an advance.",
        "EC பதிவேற்றப்படவில்லை. முன்பணம் கொடுக்கும் முன் குறைந்தது 30 ஆண்டுகளுக்கான EC (வில்லங்கச் சான்று)-ஐ tnreginet.gov.in-இல் பெறுங்கள்.")
    else if (entries.isEmpty()) flag("EC_UNREADABLE", FlagLevel.MEDIUM,
        "No entries could be read from the EC. Upload a clearer copy (all pages), or the EC may say “no encumbrance” – check it yourself.",
        "EC-இலிருந்து எந்தப் பதிவையும் படிக்க முடியவில்லை. தெளிவான நகலைப் (எல்லாப் பக்கங்களும்) பதிவேற்றுங்கள்; அல்லது EC “வில்லங்கம் இல்லை” எனக் கூறலாம் – நீங்களே சரிபாருங்கள்.")

    // Chain continuity: each seller must be a buyer of an earlier transfer.
    for (i in 1 until chain.size) {
        val prev = chain[i - 1]
        val cur = chain[i]
        val earlierOwners = chain.take(i).flatMap { it.to }
        if (cur.from.isNotEmpty() && earlierOwners.isNotEmpty() && !samePeople(cur.from, earlierOwners)) {
            val ref = docRef(cur.docNo, cur.year)
            flag("CHAIN_BREAK", FlagLevel.HIGH,
                "Break in the ownership chain: in $ref the seller (${cur.from.joinToString(", ")}) is not a buyer of any earlier deed (last buyer: ${prev.to.joinToString(", ")}). Ask how they got the land (inheritance, legal heir certificate, earlier deed).",
                "உரிமைச் சங்கிலியில் இடைவெளி: $ref-இல் விற்பவர் (${cur.from.joinToString(", ")}) முந்தைய எந்த ஆவணத்திலும் வாங்கியவர் இல்லை (கடைசியாக வாங்கியவர்: ${prev.to.joinToString(", ")}). நிலம் எப்படி வந்தது (வாரிசு, வாரிசுச் சான்று, முந்தைய ஆவணம்) எனக் கேளுங்கள்.", ref)
        }
    }

    // Mortgages without a later release.
    mine.filter { it.natureCode == Nature.MORTGAGE }.forEach { m ->
        val released = mine.any { r ->
            r.natureCode == Nature.MORTGAGE_RELEASE && r.sortKey() >= m.sortKey() &&
                ((m.docNo.isNotBlank() && r.refDocNo.isNotBlank() && docNumber(r.refDocNo) == docNumber(m.docNo)) ||
                    samePeople(r.executants + r.claimants, m.executants + m.claimants))
        }
        if (!released) {
            val ref = docRef(m.docNo, m.yearOrNull())
            flag("OPEN_MORTGAGE", FlagLevel.HIGH,
                "Mortgage $ref (${m.claimants.joinToString(", ").ifBlank { "lender not read" }}) has no release in the EC. Ask for the loan closure / receipt deed, or have it cleared at registration.",
                "அடமானம் $ref (${m.claimants.joinToString(", ").ifBlank { "கடன் கொடுத்தவர் படிக்கப்படவில்லை" }}) EC-இல் மீட்கப்படவில்லை. கடன் முடிவு / ரசீது ஆவணம் கேளுங்கள், அல்லது பதிவின்போது தீர்க்கச் சொல்லுங்கள்.", ref)
        }
    }

    mine.filter { it.natureCode == Nature.ATTACHMENT || it.natureCode == Nature.COURT }.forEach { e ->
        val ref = docRef(e.docNo, e.yearOrNull())
        flag("COURT_OR_ATTACHMENT", FlagLevel.HIGH,
            "Court order / attachment in the EC ($ref: ${e.nature}). Do not pay until a lawyer confirms it is closed.",
            "EC-இல் நீதிமன்ற உத்தரவு / ஜப்தி ($ref: ${e.nature}). அது முடிந்துவிட்டதை வழக்கறிஞர் உறுதிசெய்யும் வரை பணம் கொடுக்காதீர்கள்.", ref)
    }

    mine.filter { it.natureCode == Nature.CANCELLATION }.forEach { e ->
        val ref = docRef(e.docNo, e.yearOrNull())
        flag("CANCELLED_DEED", FlagLevel.HIGH,
            "A deed was cancelled ($ref). Find out which deed and why – a one-sided cancellation of a sale is not valid and often means a dispute.",
            "ஒரு ஆவணம் ரத்து செய்யப்பட்டுள்ளது ($ref). எந்த ஆவணம், ஏன் எனக் கண்டறியுங்கள் – ஒருதலைப்பட்ச கிரய ரத்து செல்லாது; பெரும்பாலும் தகராறு இருக்கும்.", ref)
    }

    // Sales signed by a power agent.
    chain.filter { it.viaPowerAgent }.forEach { c ->
        val ref = docRef(c.docNo, c.year)
        flag("POWER_SALE", FlagLevel.MEDIUM,
            "Sale $ref was signed by a power agent. Check the power deed was registered, not cancelled before $ref, and the owner was alive on that date.",
            "கிரயம் $ref பவர் முகவரால் கையெழுத்திடப்பட்டது. பவர் ஆவணம் பதிவு செய்யப்பட்டதா, $ref-க்கு முன் ரத்து செய்யப்படவில்லையா, அன்று உரிமையாளர் உயிருடன் இருந்தாரா எனச் சரிபாருங்கள்.", ref)
    }
    val lastTransfer = chain.lastOrNull()
    mine.filter { it.natureCode == Nature.POWER }.forEach { p ->
        val cancelled = mine.any { it.natureCode == Nature.POWER_CANCEL && it.sortKey() >= p.sortKey() }
        val afterLastSale = lastTransfer == null || p.sortKey() >= (entries.firstOrNull { it.docNo == lastTransfer.docNo && it.natureCode in Nature.transfers }?.sortKey() ?: "")
        if (!cancelled && afterLastSale) {
            val ref = docRef(p.docNo, p.yearOrNull())
            flag("OPEN_POWER", FlagLevel.MEDIUM,
                "A power of attorney ($ref, to ${p.claimants.joinToString(", ").ifBlank { "agent not read" }}) is still open. The agent could also sell – get it cancelled or deal with the owner directly.",
                "பவர் ஆவணம் ($ref, முகவர்: ${p.claimants.joinToString(", ").ifBlank { "படிக்கப்படவில்லை" }}) இன்னும் நடைமுறையில் உள்ளது. முகவரும் விற்கலாம் – ரத்து செய்யச் சொல்லுங்கள் அல்லது நேரடியாக உரிமையாளரிடம் பேசுங்கள்.", ref)
        }
    }
    mine.filter { it.natureCode == Nature.AGREEMENT }.forEach { a ->
        val closed = mine.any { (it.natureCode in Nature.transfers || it.natureCode == Nature.CANCELLATION) && it.sortKey() > a.sortKey() }
        if (!closed) {
            val ref = docRef(a.docNo, a.yearOrNull())
            flag("OPEN_AGREEMENT", FlagLevel.MEDIUM,
                "A sale agreement ($ref, with ${a.claimants.joinToString(", ").ifBlank { "buyer not read" }}) has no sale or cancellation after it. Someone else may have a claim – ask for its cancellation deed.",
                "கிரய ஒப்பந்தம் ($ref, ${a.claimants.joinToString(", ").ifBlank { "வாங்குபவர் படிக்கப்படவில்லை" }}) பின் கிரயமோ ரத்தோ இல்லை. வேறொருவருக்கு உரிமை இருக்கலாம் – ஒப்பந்த ரத்து ஆவணம் கேளுங்கள்.", ref)
        }
    }

    // Value drop between consecutive sales.
    val sales = chain.filter { it.nature == Nature.SALE && (it.value ?: 0) > 0 }
    for (i in 1 until sales.size) {
        val a = sales[i - 1]
        val b = sales[i]
        if ((b.value ?: 0).toDouble() < (a.value ?: 0) * DocRules.VALUE_DROP_RATIO) {
            val ref = docRef(b.docNo, b.year)
            flag("VALUE_DROP", FlagLevel.LOW,
                "Sale value fell from ₹${a.value} (${docRef(a.docNo, a.year)}) to ₹${b.value} ($ref). Possible under-valuation – check it was registered at least at the guideline value.",
                "கிரய மதிப்பு ₹${a.value} (${docRef(a.docNo, a.year)})-இலிருந்து ₹${b.value} ($ref)-ஆகக் குறைந்தது. குறைமதிப்பீடு இருக்கலாம் – குறைந்தது வழிகாட்டி மதிப்பில் பதிவானதா எனச் சரிபாருங்கள்.", ref)
        }
    }

    // EC period and freshness.
    val fromY = Regex("(19|20)\\d{2}").find(ecFrom)?.value?.toIntOrNull()
    val toY = Regex("(19|20)\\d{2}").find(ecTo)?.value?.toIntOrNull()
    if (hasEc && fromY != null && toY != null && toY - fromY < DocRules.MIN_EC_YEARS) flag("EC_SHORT", FlagLevel.MEDIUM,
        "The EC covers only ${toY - fromY} years ($ecFrom – $ecTo). Get it for at least ${DocRules.MIN_EC_YEARS} years.",
        "EC ${toY - fromY} ஆண்டுகளை மட்டுமே உள்ளடக்குகிறது ($ecFrom – $ecTo). குறைந்தது ${DocRules.MIN_EC_YEARS} ஆண்டுகளுக்குப் பெறுங்கள்.")
    val toIso = isoDate(ecTo)
    if (hasEc && toIso.isNotBlank() && now.length >= 10 && daysBetween(toIso, now) > DocRules.FRESH_EC_DAYS) flag("EC_OLD", FlagLevel.LOW,
        "The EC ends on $ecTo. Take a fresh EC just before you pay, so a new loan or sale is not missed.",
        "EC $ecTo அன்று முடிகிறது. புதிய கடனோ கிரயமோ தவறாமல் இருக்க, பணம் கொடுக்கும் முன் புதிய EC எடுங்கள்.")

    // Patta / chitta / adangal against the chain and the land record.
    val revenue = docs.filter { it.kind == DocKind.PATTA || it.kind == DocKind.CHITTA || it.kind == DocKind.ADANGAL }
    val holders = revenue.flatMap { it.holders }
    if (lastTransfer != null && holders.isNotEmpty() && !samePeople(holders, lastTransfer.to)) flag("PATTA_NOT_SELLER", FlagLevel.HIGH,
        "Patta / chitta is in the name of ${holders.distinct().joinToString(", ")}, but the last owner in the EC is ${lastTransfer.to.joinToString(", ")}. The patta should be transferred to the seller before the sale.",
        "பட்டா / சிட்டா ${holders.distinct().joinToString(", ")} பெயரில் உள்ளது; EC-இல் கடைசி உரிமையாளர் ${lastTransfer.to.joinToString(", ")}. கிரயத்துக்கு முன் பட்டா விற்பவர் பெயருக்கு மாற வேண்டும்.")
    val pattaNos = revenue.map { it.pattaNo.filter { c -> c.isLetterOrDigit() } }.filter { it.isNotBlank() }.distinct()
    val myPatta = land.pattaNo.filter { it.isLetterOrDigit() }
    if (myPatta.isNotBlank() && pattaNos.isNotEmpty() && myPatta !in pattaNos) flag("PATTA_NO_MISMATCH", FlagLevel.MEDIUM,
        "Patta number in the documents (${pattaNos.joinToString(", ")}) is not the one in Land details ($myPatta).",
        "ஆவணங்களில் உள்ள பட்டா எண் (${pattaNos.joinToString(", ")}) நில விவரத்தில் உள்ளது ($myPatta) அல்ல.")
    val target = land.surveyFull.ifBlank { land.surveyNo }
    if (target.isNotBlank()) {
        docs.filter { it.surveyNos.isNotEmpty() && it.surveyNos.none { s -> surveyMatches(target, s) || surveyMatches(land.surveyNo, s) } }.forEach { d ->
            flag("SURVEY_MISMATCH", FlagLevel.MEDIUM,
                "${d.kind.label().first} shows survey ${d.surveyNos.joinToString(", ")}, not $target (Land details).",
                "${d.kind.label().second} புல எண் ${d.surveyNos.joinToString(", ")} காட்டுகிறது, $target (நில விவரம்) அல்ல.")
        }
        if (hasEc && entries.isNotEmpty() && entries.none { it.surveyNos.isEmpty() || it.concerns(land) }) flag("EC_OTHER_SURVEY", FlagLevel.HIGH,
            "None of the EC entries is for survey $target. This EC may be for another survey number.",
            "EC பதிவுகள் எதுவும் புல எண் $target-க்கு இல்லை. இந்த EC வேறு புல எண்ணுக்கானதாக இருக்கலாம்.")
    }
    docs.filter { it.landClass.isNotBlank() }.forEach { d ->
        val c = d.landClass.lowercase()
        if (listOf("poramboke", "புறம்போக்கு", "government", "அரசு", "temple", "கோயில்", "wakf", "வக்ஃபு", "assessed waste", "anadheenam", "அனாதீனம்").any { it in c })
            flag("GOVT_LAND", FlagLevel.HIGH,
                "${d.kind.label().first} classifies the land as “${d.landClass}”. Government / poramboke / temple land cannot be sold – do not proceed without a lawyer.",
                "${d.kind.label().second} நிலத்தை “${d.landClass}” என வகைப்படுத்துகிறது. அரசு / புறம்போக்கு / கோயில் நிலத்தை விற்க முடியாது – வழக்கறிஞர் இல்லாமல் தொடராதீர்கள்.")
    }
    val extents = docs.filter { it.extent.isNotBlank() }.map { it.kind to it.extent }
    if (extents.map { normExtent(it.second) }.filter { it.isNotBlank() }.distinct().size > 1) flag("EXTENT_DIFFERS", FlagLevel.LOW,
        "Extent differs between documents: " + extents.joinToString("; ") { "${it.first.label().first} ${it.second}" } + ". Check which is right (measure if needed).",
        "ஆவணங்களுக்கிடையே பரப்பு வேறுபடுகிறது: " + extents.joinToString("; ") { "${it.first.label().second} ${it.second}" } + ". எது சரி எனச் சரிபாருங்கள் (தேவைப்பட்டால் அளவிடுங்கள்).")
    return out
}

/** "0.05.0 Hect" / "5 cents" / "2178 sq.ft" -> a comparable text (spaces and case removed). */
fun normExtent(s: String): String = s.lowercase().replace(Regex("\\s+"), "").replace("sq.ft", "sqft").replace("ச.அடி", "sqft").trim('.')

/** Days from [a] to [b] ("yyyy-mm-dd"), 0 when either cannot be read. */
fun daysBetween(a: String, b: String): Int {
    fun days(s: String): Long? {
        val p = s.split('-').mapNotNull { it.toIntOrNull() }
        if (p.size < 3) return null
        return runCatching { kotlinx.datetime.LocalDate(p[0], p[1], p[2]).toEpochDays().toLong() }.getOrNull()
    }
    val x = days(a) ?: return 0
    val y = days(b) ?: return 0
    return (y - x).toInt()
}

/** English / Tamil name of a document kind. */
fun DocKind.label(): Pair<String, String> = when (this) {
    DocKind.EC -> "EC" to "EC (வில்லங்கச் சான்று)"
    DocKind.PATTA -> "Patta" to "பட்டா"
    DocKind.CHITTA -> "Chitta" to "சிட்டா"
    DocKind.ADANGAL -> "Adangal" to "அடங்கல்"
    DocKind.SALE_DEED -> "Sale deed" to "கிரயப் பத்திரம்"
    DocKind.OTHER -> "Other document" to "மற்ற ஆவணம்"
}

/** Everything after the AI answered: chain, rule flags + AI flags (rule flags first, worst first). */
fun buildAnalysis(
    reading: AiReading, land: LandRecord, hasEc: Boolean, now: String, analysedAt: Long, provider: String, model: String, docIds: List<String>,
): DocAnalysis {
    val chain = ownershipChain(reading.entries, land)
    val rules = ruleFlags(reading.entries, chain, reading.docs, land, reading.ecFrom, reading.ecTo, hasEc, now)
    val flags = (rules + reading.aiFlags).sortedBy { it.level.ordinal }
    return DocAnalysis(
        analysedAt = analysedAt, provider = provider, model = model, docIds = docIds,
        pattaNo = land.pattaNo, surveyNo = land.surveyFull.ifBlank { land.surveyNo },
        ecFrom = reading.ecFrom, ecTo = reading.ecTo,
        entries = reading.entries, chain = chain, docs = reading.docs, flags = flags,
        summaryEn = reading.summaryEn, summaryTa = reading.summaryTa,
    )
}

/** The instruction sent with the files. */
fun analysisPrompt(land: LandRecord, kinds: List<DocKind>, propertyTitle: String): String = buildString {
    appendLine("You are helping a buyer in Tamil Nadu check land documents before buying. The attached files are, in order: " +
        kinds.joinToString(", ") { it.label().first } + ".")
    appendLine("The property: \"$propertyTitle\". Land details given by the buyer – district: ${land.district.ifBlank { "?" }}, taluk: ${land.taluk.ifBlank { "?" }}, village: ${land.village.ifBlank { "?" }}, SRO: ${land.sro.ifBlank { "?" }}, survey number: ${land.surveyFull.ifBlank { land.surveyNo }.ifBlank { "?" }}, patta number: ${land.pattaNo.ifBlank { "?" }}.")
    appendLine("The documents may be in Tamil or English. Transcribe ONLY what is printed – never guess or invent a name, number, date or value; leave a field empty (\"\" or null) when it is not readable.")
    appendLine("Reply with ONE JSON object and nothing else, in this shape:")
    appendLine("""{
 "ecFrom": "start date of the EC search period as printed", "ecTo": "end date as printed",
 "entries": [ one object per EC entry, in the EC's order, ALL entries (also those for other survey numbers):
   {"serial":"", "docNo":"document number", "year":2015, "date":"registration date dd-mm-yyyy", "nature":"nature of document as printed",
    "natureCode":"one of SALE, GIFT, SETTLEMENT, PARTITION, WILL, EXCHANGE, RELEASE, MORTGAGE, MORTGAGE_RELEASE, AGREEMENT, POWER, POWER_CANCEL, LEASE, ATTACHMENT, COURT, CANCELLATION, OTHER",
    "executants":["names of executants / sellers / mortgagors"], "claimants":["names of claimants / buyers / mortgagees / agents"],
    "value": 1250000 (consideration / market value in rupees as a number, or null), "surveyNos":["survey numbers in this entry's schedule"],
    "extent":"extent as printed", "viaPowerAgent": true if a power agent signed for the executant, "refDocNo":"for a release / receipt / cancellation: the document number it refers to", "remarks":"" } ],
 "documents": [ one object per patta / chitta / adangal / sale deed / other file:
   {"kind":"PATTA|CHITTA|ADANGAL|SALE_DEED|EC|OTHER", "holders":["owner / patta holder names"], "pattaNo":"", "surveyNos":[""], "extent":"", "landClass":"land classification as printed (nanjai, punjai, natham, poramboke …)", "village":"", "docNo":"", "date":"", "value": null, "notes":"anything unusual, short"} ],
 "flags": [ anything else non-compliant or risky you SEE in the documents (not general advice), each:
   {"code":"SHORT_CODE", "level":"HIGH|MEDIUM|LOW", "en":"one sentence in simple English", "ta":"the same in simple Tamil", "ref":"document number / page"} ],
 "summaryEn": "2–3 sentences in simple English: who owns the land now and how it came to them",
 "summaryTa": "the same in simple Tamil"
}""")
    appendLine("Names: write them exactly as printed (Tamil names stay in Tamil script). Do not add a flag for something you cannot see.")
}
