package com.hark.sothudiary.domain

/**
 * Tamil Nadu district names (English spellings + Tamil) used to spot the district
 * in a post (all 38 districts – no town list, no coordinates). Places are geocoded with Nominatim.
 */
data class KnownPlace(
    val name: String,
    val nameTa: String,
    val district: String,
    /** Other English spellings seen in posts. */
    val aliases: List<String> = emptyList(),
    val taAliases: List<String> = emptyList(),
    val isDistrict: Boolean = false,
)

object Places {
    val districts: List<KnownPlace> = listOf(
        "Ariyalur" to "அரியலூர்", "Chengalpattu" to "செங்கல்பட்டு", "Chennai" to "சென்னை", "Coimbatore" to "கோயம்புத்தூர்",
        "Cuddalore" to "கடலூர்", "Dharmapuri" to "தர்மபுரி", "Dindigul" to "திண்டுக்கல்", "Erode" to "ஈரோடு",
        "Kallakurichi" to "கள்ளக்குறிச்சி", "Kancheepuram" to "காஞ்சிபுரம்", "Kanniyakumari" to "கன்னியாகுமரி", "Karur" to "கரூர்",
        "Krishnagiri" to "கிருஷ்ணகிரி", "Madurai" to "மதுரை", "Mayiladuthurai" to "மயிலாடுதுறை", "Nagapattinam" to "நாகப்பட்டினம்",
        "Namakkal" to "நாமக்கல்", "Nilgiris" to "நீலகிரி", "Perambalur" to "பெரம்பலூர்", "Pudukkottai" to "புதுக்கோட்டை",
        "Ramanathapuram" to "ராமநாதபுரம்", "Ranipet" to "ராணிப்பேட்டை", "Salem" to "சேலம்", "Sivaganga" to "சிவகங்கை",
        "Tenkasi" to "தென்காசி", "Thanjavur" to "தஞ்சாவூர்", "Theni" to "தேனி", "Thoothukudi" to "தூத்துக்குடி",
        "Tiruchirappalli" to "திருச்சிராப்பள்ளி", "Tirunelveli" to "திருநெல்வேலி", "Tirupathur" to "திருப்பத்தூர்", "Tiruppur" to "திருப்பூர்",
        "Tiruvallur" to "திருவள்ளூர்", "Tiruvannamalai" to "திருவண்ணாமலை", "Tiruvarur" to "திருவாரூர்", "Vellore" to "வேலூர்",
        "Viluppuram" to "விழுப்புரம்", "Virudhunagar" to "விருதுநகர்",
    ).map { (en, ta) ->
        val aliases = when (en) {
            "Tirunelveli" -> listOf("Thirunelveli", "Nellai")
            "Tiruchirappalli" -> listOf("Trichy", "Tiruchi")
            "Thoothukudi" -> listOf("Tuticorin")
            "Kanniyakumari" -> listOf("Kanyakumari")
            "Kancheepuram" -> listOf("Kanchipuram")
            "Viluppuram" -> listOf("Villupuram")
            "Sivaganga" -> listOf("Sivagangai")
            else -> emptyList()
        }
        val taAliases = when (en) {
            "Tirunelveli" -> listOf("நெல்லை")
            "Tiruchirappalli" -> listOf("திருச்சி")
            else -> emptyList()
        }
        KnownPlace(en, ta, en, aliases, taAliases, isDistrict = true)
    }

    /**
     * No town / village list: the app is not tied to any area. Places other than districts are taken from the
     * text (see ListingParser.placeCandidates) and resolved with Nominatim.
     */
    val towns: List<KnownPlace> = emptyList()

    val all: List<KnownPlace> get() = towns + districts

    /** Tamil stem used for matching inflected forms: "மடத்தூர்" also matches "மடத்தூரில்", "வல்லம்" matches "வல்லத்தில்". */
    fun tamilStem(name: String): String {
        var s = name.trim()
        if (s.endsWith("ம்")) s = s.dropLast(2) // -ம் becomes -த்தில் / -த்து when inflected
        else if (s.endsWith("்")) s = s.dropLast(1) // drop the final pulli: ர் -> ர(ில்)
        return s
    }

    private fun englishKeys(p: KnownPlace) = (listOf(p.name) + p.aliases).map { it.lowercase() }

    /**
     * Every gazetteer place mentioned in [text], most specific (longest name, towns before districts) first.
     * English names match as whole words; Tamil names by their stem so case endings still match.
     */
    fun findIn(text: String): List<KnownPlace> {
        val lower = text.lowercase()
        val hits = mutableListOf<Pair<KnownPlace, Int>>()
        for (p in all) {
            var best = 0
            for (k in englishKeys(p)) {
                if (k.length < 4) continue
                val re = Regex("""(?<![a-z])${Regex.escape(k).replace(" ", "\\s*")}(?![a-z])""")
                if (re.containsMatchIn(lower)) best = maxOf(best, k.length)
            }
            for (t in listOf(p.nameTa) + p.taAliases) {
                val stem = tamilStem(t)
                if (stem.length >= 3 && text.contains(stem)) best = maxOf(best, stem.length + 1)
            }
            if (best > 0) hits += p to best
        }
        return hits.sortedWith(compareBy<Pair<KnownPlace, Int>>({ if (it.first.isDistrict) 1 else 0 }, { -it.second })).map { it.first }
    }

    fun district(name: String): KnownPlace? = districts.firstOrNull { d ->
        d.name.equals(name.trim(), ignoreCase = true) || d.aliases.any { it.equals(name.trim(), ignoreCase = true) } || d.nameTa == name.trim()
    }
}
