package com.hark.sothudiary.data

import com.hark.sothudiary.domain.land.parseSurveyNumber

/**
 * 2.4.0: keeps the one shared "Land details / நில விவரம்" record ([Property.land]) and the older, scattered fields in
 * step, so every screen (Land check, Guideline value, Price check, Share, the edit form) sees the same values and
 * nothing typed in one place is lost in another.
 *
 * Older fields that map onto the record:
 *  - [Property.district], [Property.taluk]                  (edit form / map address)
 *  - [Property.surveyNumber]                                  (2.3.0 survey card, "123/4B")
 *  - [RegistryInfo.district] / [RegistryInfo.sro] / [RegistryInfo.village]   (2.3.1 guideline place)
 * [Property.locality] (area name) only fills a blank village.
 */

fun cleanDistrictName(s: String): String = s.replace(Regex("""(?i)\s*district$"""), "").replace(Regex("""\s*மாவட்டம்$"""), "").trim()

/** "123/4B" -> ("123", "4B"); "88" -> ("88", ""); free text that is not a survey number stays as typed. */
fun splitSurvey(text: String): Pair<String, String> {
    val t = text.trim()
    if (t.isBlank()) return "" to ""
    val s = parseSurveyNumber(t) ?: return t to ""
    return s.number to s.subdivision
}

/** Record built from the older fields, keeping anything already in the record (used once, on upgrade / import). */
fun landFromLegacy(p: Property): LandRecord {
    val r = p.land
    val (sv, sub) = splitSurvey(p.surveyNumber)
    val auto = r.autoFilled.toMutableSet()
    val village = r.village.ifBlank {
        p.registry.village.ifBlank { p.locality.also { if (it.isNotBlank()) auto += "village" } }
    }
    return r.copy(
        district = r.district.ifBlank { p.registry.district.ifBlank { cleanDistrictName(p.district) } },
        taluk = r.taluk.ifBlank { p.taluk },
        village = village,
        sro = r.sro.ifBlank { p.registry.sro },
        surveyNo = r.surveyNo.ifBlank { sv },
        subdivision = if (r.surveyNo.isBlank()) r.subdivision.ifBlank { sub } else r.subdivision,
        autoFilled = auto,
    )
}

/** The older fields written from the record (the record wins). */
fun mirrorToLegacy(p: Property): Property {
    val l = p.land
    return p.copy(
        district = l.district,
        taluk = l.taluk,
        surveyNumber = l.surveyFull,
        registry = p.registry.copy(district = l.district, sro = l.sro, village = l.village),
    )
}

/**
 * Called for every saved property: [old] is the version before the change (null for a new one).
 * - The record changed -> the older fields follow it.
 * - Only older fields changed (an older screen or an import) -> the record takes those changes.
 */
fun reconcileLand(old: Property?, new: Property): Property {
    if (old == null) return mirrorToLegacy(new.copy(land = landFromLegacy(new)))
    if (old === new) return new
    if (new.land != old.land) return mirrorToLegacy(new)
    var l = new.land
    val auto = l.autoFilled.toMutableSet()
    if (new.district != old.district) { l = l.copy(district = cleanDistrictName(new.district)); auto -= "district" }
    if (new.taluk != old.taluk) { l = l.copy(taluk = new.taluk.trim()); auto -= "taluk" }
    if (new.surveyNumber != old.surveyNumber) {
        val (sv, sub) = splitSurvey(new.surveyNumber)
        l = l.copy(surveyNo = sv, subdivision = sub)
    }
    val rOld = old.registry
    val rNew = new.registry
    if (rNew.district != rOld.district) l = l.copy(district = cleanDistrictName(rNew.district))
    if (rNew.sro != rOld.sro) l = l.copy(sro = rNew.sro.trim())
    if (rNew.village != rOld.village) { l = l.copy(village = rNew.village.trim()); auto -= "village" }
    if (new.locality != old.locality && (l.village.isBlank() || "village" in auto) && new.locality.isNotBlank()) {
        l = l.copy(village = new.locality.trim()); auto += "village"
    }
    l = l.copy(autoFilled = auto)
    return if (l == new.land) new else mirrorToLegacy(new.copy(land = l))
}

/**
 * Fills blank district / taluk / village from a reverse-geocoded map address (never overwrites what the user typed).
 */
fun LandRecord.fillFromMap(district: String, taluk: String, village: String): LandRecord {
    val auto = autoFilled.toMutableSet()
    fun choose(cur: String, v: String, key: String): String =
        if ((cur.isBlank() || key in autoFilled) && v.isNotBlank()) { auto += key; v.trim() } else cur
    val d = choose(this.district, cleanDistrictName(district), "district")
    val t = choose(this.taluk, taluk, "taluk")
    val v = choose(this.village, village, "village")
    return copy(district = d, taluk = t, village = v, autoFilled = auto)
}
