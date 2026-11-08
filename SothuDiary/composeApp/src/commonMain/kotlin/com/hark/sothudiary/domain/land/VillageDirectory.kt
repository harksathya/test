package com.hark.sothudiary.domain.land

import com.hark.sothudiary.data.LandRecord
import com.hark.sothudiary.domain.registry.districtSpellings

/**
 * 2.4.0: Tamil Nadu district → taluk → village names for the Land details pickers, bundled with the app (no network).
 * Source: Local Government Directory (LGD) via data.gov.in, Government Open Data License – India (GODL), as compiled by
 * github.com/mchittineni/india-village-finder v1.3.0 (38 districts, 317 taluks / sub-districts, ~16,000 villages).
 * File format (res/raw/tn_villages.txt): "District|Taluk|village;village;…", "#" lines are comments.
 */
class VillageDirectory(text: String) {
    /** district -> taluk -> villages */
    val tree: Map<String, Map<String, List<String>>>

    init {
        val m = LinkedHashMap<String, LinkedHashMap<String, List<String>>>()
        text.lineSequence().forEach { line ->
            if (line.isBlank() || line.startsWith("#")) return@forEach
            val parts = line.split('|')
            if (parts.size < 3) return@forEach
            m.getOrPut(parts[0].trim()) { LinkedHashMap() }[parts[1].trim()] = parts[2].split(';').map { it.trim() }.filter { it.isNotBlank() }
        }
        tree = m
    }

    val districts: List<String> get() = tree.keys.sorted()

    /** The directory's own spelling of a district typed or geocoded another way ("Tirunelveli District", "தென்காசி"). */
    fun district(name: String): String? {
        val n = norm(name.replace(Regex("""(?i)\s*district$"""), ""))
        if (n.isBlank()) return null
        tree.keys.firstOrNull { norm(it) == n }?.let { return it }
        val spellings = districtSpellings(name).map(::norm)
        return tree.keys.firstOrNull { k -> norm(k) in spellings || spellings.any { s -> s.length >= 5 && (norm(k).startsWith(s) || s.startsWith(norm(k))) } }
            ?: tree.keys.firstOrNull { norm(it).removePrefix("the") == n.removePrefix("the") }
    }

    fun taluks(district: String): List<String> = district(district)?.let { tree[it]?.keys?.sorted() }.orEmpty()

    /** The directory's spelling of a taluk in [district] ("Shencottai" -> "Shenkottai" by sound-alike match). */
    fun taluk(district: String, name: String): String? {
        val d = district(district) ?: return null
        val n = norm(name)
        if (n.isBlank()) return null
        val ts = tree[d]?.keys.orEmpty()
        return ts.firstOrNull { norm(it) == n } ?: ts.firstOrNull { loose(it) == loose(name) }
    }

    fun villages(district: String, taluk: String): List<String> {
        val d = district(district) ?: return emptyList()
        val t = taluk(d, taluk)
        return if (t != null) tree[d]?.get(t).orEmpty() else tree[d]?.values?.flatten()?.distinct()?.sorted().orEmpty()
    }

    /** Taluk of a village in a district (when the user picks the village first). */
    fun talukOf(district: String, village: String): String? {
        val d = district(district) ?: return null
        val v = loose(village)
        return tree[d]?.entries?.firstOrNull { (_, vs) -> vs.any { loose(it) == v } }?.key
    }

    /** Names in [options] that start with or contain [query] (sound-alike spellings too), best first. */
    fun filter(options: List<String>, query: String, max: Int = 60): List<String> {
        val q = norm(query)
        if (q.isBlank()) return options.take(max)
        val lq = loose(query)
        return options.filter { norm(it).contains(q) || loose(it).contains(lq) }
            .sortedWith(compareBy({ if (norm(it).startsWith(q) || loose(it).startsWith(lq)) 0 else 1 }, { it.lowercase() }))
            .take(max)
    }

    companion object {
        fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
        /** Sound-alike key: "Shencottai" / "Shenkottai" / "Sengottai", "Thiruvallur" / "Tiruvallur". */
        fun loose(s: String) = norm(s).replace("th", "t").replace("zh", "l").replace("sh", "s").replace("ch", "s")
            .replace("k", "c").replace("ee", "i").replace("oo", "u").replace("aa", "a")
            .replace(Regex("(.)\\1+"), "$1")

        private var loaded: VillageDirectory? = null
        /** Set once at start-up by the platform from the bundled file. */
        fun install(text: String?) { if (text != null && loaded == null) loaded = VillageDirectory(text) }
        val instance: VillageDirectory? get() = loaded
    }
}

/**
 * 2.4.0: reads land details out of pasted patta / chitta / EC / guideline text, e.g.
 * "District: Tenkasi Taluk: Shenkottai Village: Vadakarai Melpidagai Survey No: 123/4B Patta No: 456".
 * English and Tamil labels. Only the fields found are returned (others stay blank).
 */
fun parseLandText(text: String): LandRecord {
    val t = text.replace('\u00A0', ' ')
    fun field(vararg labels: String): String {
        for (l in labels) {
            val m = Regex("""(?i)$l\s*(?:name)?\s*[:：\-–]?\s*([^\n,;:|]{2,40}?)(?=\s{2,}|\s*[,;|\n]|\s+(?:taluk|village|district|survey|sub|patta|sro|வட்டம்|கிராமம்|மாவட்டம்|புல|பட்டா)\b|$)""").find(t)
            if (m != null) return m.groupValues[1].trim().trimEnd('.')
        }
        return ""
    }
    val district = field("district", "மாவட்டம்")
    val taluk = field("taluk", "taluka", "வட்டம்")
    val village = field("revenue village", "village", "கிராமம்")
    val sro = field("sub[ -]?registrar office", "s\\.?r\\.?o\\.?", "சார்பதிவாளர் அலுவலகம்")
    val patta = Regex("""(?i)(?:patta\s*(?:no\.?|number)|பட்டா\s*எண்)\s*[:：\-]?\s*([0-9]{1,6})""").find(t)?.groupValues?.get(1).orEmpty()
    val surveyHit = Regex("""(?i)(?:s\.?\s*f\.?\s*no\.?|sy\.?\s*no\.?|survey\s*(?:no\.?|number)|புல\s*எண்)\s*[:：\-]?\s*([0-9]{1,5}\s*(?:[/\-]\s*[0-9A-Za-z]{1,6})?)""").find(t)
    var sv = ""
    var sub = ""
    if (surveyHit != null) {
        val s = parseSurveyNumber(surveyHit.groupValues[1])
        if (s != null) { sv = s.number; sub = s.subdivision }
    }
    val subOnly = Regex("""(?i)(?:sub\s*-?\s*division|subdivision|உட்பிரிவு)\s*(?:no\.?)?\s*[:：\-]?\s*([0-9A-Za-z]{1,6})""").find(t)?.groupValues?.get(1)
    if (sub.isBlank() && subOnly != null) sub = subOnly.uppercase()
    return LandRecord(district = district, taluk = taluk, village = village, sro = sro, surveyNo = sv, subdivision = sub, pattaNo = patta)
}

/** Fields of [found] fill this record where they are not blank (pasting never clears a field). */
fun LandRecord.mergedWith(found: LandRecord): LandRecord = copy(
    district = found.district.ifBlank { district }, taluk = found.taluk.ifBlank { taluk }, village = found.village.ifBlank { village },
    sro = found.sro.ifBlank { sro }, surveyNo = found.surveyNo.ifBlank { surveyNo },
    subdivision = if (found.surveyNo.isNotBlank()) found.subdivision else found.subdivision.ifBlank { subdivision },
    pattaNo = found.pattaNo.ifBlank { pattaNo },
    autoFilled = autoFilled - setOfNotNull("district".takeIf { found.district.isNotBlank() }, "taluk".takeIf { found.taluk.isNotBlank() }, "village".takeIf { found.village.isNotBlank() }),
)
