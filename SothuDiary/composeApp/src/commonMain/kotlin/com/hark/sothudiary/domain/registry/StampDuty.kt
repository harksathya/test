package com.hark.sothudiary.domain.registry

import com.hark.sothudiary.data.AreaUnit
import com.hark.sothudiary.data.GvUnit
import com.hark.sothudiary.data.OfficialGuideline
import com.hark.sothudiary.data.Property
import com.hark.sothudiary.data.PropertyType
import com.hark.sothudiary.data.RegistryInfo
import com.hark.sothudiary.domain.CENTS_PER_ACRE
import com.hark.sothudiary.domain.PriceBasis
import com.hark.sothudiary.domain.SQFT_PER_CENT
import com.hark.sothudiary.domain.toSqft
import kotlin.math.roundToLong

/*
 * 2.3.1: Government guideline value (TNREGINET) and the Tamil Nadu sale-deed stamp duty / registration fee calculator.
 *
 * Rates for a sale deed (conveyance, Indian Stamp Act Schedule I Article 23 as applied in Tamil Nadu), checked 7 Nov 2026:
 *  - Stamp duty 7 % of the market value (TNREGINET shows the 5 % stamp duty + 2 % transfer duty together as "7 %").
 *    In force since 27.03.2012.
 *  - Registration fee 2 % of the same value: G.O.(Ms.) No.34, Commercial Taxes & Registration (J2) Dept., 30.03.2023,
 *    w.e.f. 01.04.2023 (it was 4 % from 08.06.2017).
 *  Source: Government of Tamil Nadu Single Window Portal, "Stamp Duty & Registration Fee Structure" (cross-verified with
 *  the TNREGINET IGR website): https://tnswp.com/DIGIGOV/StaticAttachment?AttachmentFileName=%2Fpdf%2FTax_rules%2FStamps_and_Registration_Fee.pdf
 *  (The older NIC page https://tnregipayment.gov.in/regnepay/DutyFees.jsp still shows the pre-2017 1 % fee – not used.)
 *  - "Market value" = the higher of the guideline value and the consideration (agreed price) written in the deed.
 *  - Women buyers: G.O. Ms. No.77, CT&R (J2), 29.03.2025 (TN Gazette Extraordinary No.147, w.e.f. 01.04.2025) reduces the
 *    registration fee by Re 1 per Rs 100 for a sale to a woman / only women, when the property value is up to Rs 10,00,000:
 *    https://stationeryprinting.tn.gov.in/extraordinary/2025/147_Ex_II_2_2025.pdf
 *
 * Rates change by notification, so both percentages are settings (defaults below) the user can edit.
 */
object DutyRules {
    const val STAMP_DUTY_PCT = 7.0
    const val REGISTRATION_FEE_PCT = 2.0
    /** G.O. Ms. No.77 (2025): 1 percentage point less registration fee for women buyers … */
    const val WOMEN_FEE_CONCESSION_PCT = 1.0
    /** … only when the property value is up to ₹10 lakh. */
    const val WOMEN_CONCESSION_LIMIT = 10_00_000L
    const val TNREGINET_HOME = "https://tnreginet.gov.in/portal/"
    /** The portal home in English: its "Guideline search" block (district → SRO → village → street / survey number). */
    const val TNREGINET_GUIDELINE_URL =
        "https://tnreginet.gov.in/portal/webHP?requestType=ApplicationRH&actionVal=homePage&screenId=114&UserLocaleID=en"
    const val SOURCE_NAME = "tnreginet.gov.in"
}

// ------------------------------------------------------------------ units

/** Square feet in one [GvUnit]: 1 cent = 435.6 sq ft, 1 acre = 100 cents = 43,560 sq ft, 1 sq m = 10.7639 sq ft, 1 ha = 10,000 sq m. */
fun GvUnit.sqft(): Double = when (this) {
    GvUnit.SQFT -> 1.0
    GvUnit.SQM -> SQFT_PER_SQM
    GvUnit.CENT -> SQFT_PER_CENT
    GvUnit.ACRE -> SQFT_PER_CENT * CENTS_PER_ACRE
    GvUnit.HECTARE -> SQFT_PER_SQM * 10_000.0
}

/** 1 m = 3.28084 ft (exactly 1 / 0.3048) → 1 sq m = 10.7639104 sq ft. */
const val SQFT_PER_SQM = 1.0 / (0.3048 * 0.3048)

/** An area in [from] expressed in [to]. */
fun convertArea(v: Double, from: GvUnit, to: GvUnit): Double = v * from.sqft() / to.sqft()

/** A rate (₹ per [from]) expressed per [to]: ₹ per sq ft × 435.6 = ₹ per cent. */
fun convertGvRate(rate: Double, from: GvUnit, to: GvUnit): Double = rate * to.sqft() / from.sqft()

fun AreaUnit.toGvUnit(): GvUnit = when (this) {
    AreaUnit.CENTS -> GvUnit.CENT
    AreaUnit.ACRES -> GvUnit.ACRE
    AreaUnit.SQFT -> GvUnit.SQFT
}

fun PriceBasis.toGvUnit(): GvUnit = when (this) {
    PriceBasis.PER_CENT -> GvUnit.CENT
    PriceBasis.PER_SQFT -> GvUnit.SQFT
    PriceBasis.PER_ACRE -> GvUnit.ACRE
}

// ------------------------------------------------------------------ calculator

data class DutyRates(val stampPct: Double = DutyRules.STAMP_DUTY_PCT, val regPct: Double = DutyRules.REGISTRATION_FEE_PCT)

data class DutyBreakdown(
    /** Guideline value of the property: rate × area (₹), null without a guideline value / area. */
    val guidelineValue: Long?,
    /** Agreed / asking price (₹), null when not set. */
    val marketValue: Long?,
    /** (market − guideline) / guideline × 100: positive = asking above the guideline value. */
    val gapPct: Double?,
    /** The value duty is charged on: the higher of the two. */
    val dutyBase: Long,
    val baseIsGuideline: Boolean,
    val stampPct: Double,
    val regPct: Double,
    val stampDuty: Long,
    val registrationFee: Long,
    /** The women-buyer concession (G.O. Ms. No.77, 2025) was applied to the fee. */
    val womenConcession: Boolean = false,
) {
    val total: Long get() = stampDuty + registrationFee
}

/**
 * Stamp duty and registration fee for a Tamil Nadu sale deed: both on the higher of [guidelineValue] and [agreedPrice].
 * Amounts are rounded to the rupee. Null when neither value is known.
 * [womenBuyers]: every buyer is a woman → 1 point less fee when the value is up to ₹10 lakh.
 */
fun saleDeedDuty(guidelineValue: Long?, agreedPrice: Long?, rates: DutyRates = DutyRates(), womenBuyers: Boolean = false): DutyBreakdown? {
    val gv = guidelineValue?.takeIf { it > 0 }
    val mv = agreedPrice?.takeIf { it > 0 }
    if (gv == null && mv == null) return null
    val base = maxOf(gv ?: 0L, mv ?: 0L)
    val concession = womenBuyers && base <= DutyRules.WOMEN_CONCESSION_LIMIT
    val regPct = if (concession) (rates.regPct - DutyRules.WOMEN_FEE_CONCESSION_PCT).coerceAtLeast(0.0) else rates.regPct
    return DutyBreakdown(
        guidelineValue = gv,
        marketValue = mv,
        gapPct = if (gv != null && mv != null) (mv - gv).toDouble() / gv * 100.0 else null,
        dutyBase = base,
        baseIsGuideline = gv != null && gv >= (mv ?: 0L),
        stampPct = rates.stampPct,
        regPct = regPct,
        stampDuty = pctOf(base, rates.stampPct),
        registrationFee = pctOf(base, regPct),
        womenConcession = concession,
    )
}

fun pctOf(amount: Long, pct: Double): Long = (amount.toDouble() * pct / 100.0).roundToLong()

/** Guideline rate × area, with the area in any unit (₹). */
fun guidelineTotal(rate: Double, rateUnit: GvUnit, area: Double, areaUnit: GvUnit): Long? {
    if (rate <= 0 || area <= 0 || rate.isNaN() || area.isNaN()) return null
    return (convertGvRate(rate, rateUnit, areaUnit) * area).roundToLong()
}

/**
 * The land area the guideline value applies to, in sq ft: the user's own land area (house plot, UDS of a flat) when
 * given, else the property's stated size.
 */
fun guidelineAreaSqft(p: Property): Double? =
    p.registry.landAreaSqft?.takeIf { it > 0 } ?: toSqft(p.size, p.unit).takeIf { it > 0 }

/** True for built-up types where the registration value also has a building part (PWD rates) not covered here. */
fun PropertyType.hasBuilding(): Boolean = this == PropertyType.HOUSE || this == PropertyType.APARTMENT

/** The whole calculation for a property (null when there is neither a guideline value nor a price). */
fun propertyDuty(p: Property, rates: DutyRates): DutyBreakdown? {
    val g = p.registry.guideline
    val area = guidelineAreaSqft(p)
    val gv = if (g != null && area != null) guidelineTotal(g.value, g.unit, area, GvUnit.SQFT) else null
    return saleDeedDuty(gv, p.askingPrice.takeIf { it > 0 }, rates, p.registry.womenBuyers)
}

// ------------------------------------------------------------------ reading a captured value

data class ParsedGuideline(val value: Double, val unit: GvUnit?)

private val unitWords: List<Pair<Regex, GvUnit>> = listOf(
    Regex("""(?i)(sq\.?\s*f(ee)?t|sq\.?\s*ft|sqft|square\s*f(ee|oo)t|ச\.?\s*அடி|சதுர\s*அடி)""") to GvUnit.SQFT,
    Regex("""(?i)(sq\.?\s*m(etre|eter|tr|t)?s?\b|sqm|square\s*met|ச\.?\s*மீ|சதுர\s*மீ)""") to GvUnit.SQM,
    Regex("""(?i)(hectare|hect|\bha\b|ஹெக்டேர்|ஹெ\.)""") to GvUnit.HECTARE,
    Regex("""(?i)(acre|ஏக்கர்)""") to GvUnit.ACRE,
    Regex("""(?i)(\bcents?\b|சென்ட்|செண்ட்)""") to GvUnit.CENT,
)

private val numberRe = Regex("""(?<![\d.])(\d{1,3}(?:,\d{2,3})+(?:\.\d+)?|\d+(?:\.\d+)?)(?![\d])""")

/** The unit named in [text] (sq.ft / sq.m / hectare / acre / cent, English or Tamil), or null. */
fun unitIn(text: String): GvUnit? = unitWords.mapNotNull { (re, u) -> re.find(text)?.let { it.range.first to u } }.minByOrNull { it.first }?.second

/**
 * Reads a guideline value the user copied or the page showed, e.g. "Rs. 1,200 per Sq.Ft", "₹ 3,50,000 / Hectare",
 * "2400 ச.அடி", "1,20,000 per cent". Picks the largest rupee amount on the line (a street row also holds small numbers such
 * as the survey or serial number) and the unit named on it. Null when there is no number.
 */
fun parseGuidelineText(text: String?): ParsedGuideline? {
    val t = text?.trim().orEmpty()
    if (t.isBlank()) return null
    val rupee = Regex("""(?i)(₹|(?<![a-z])rs\.?|(?<![a-z])inr|ரூ\.?)\s*([\d,]+(?:\.\d+)?)""").findAll(t).mapNotNull { m ->
        m.groupValues[2].replace(",", "").toDoubleOrNull()
    }.toList()
    val all = numberRe.findAll(t).mapNotNull { it.value.replace(",", "").toDoubleOrNull() }.toList()
    val v = (rupee.ifEmpty { all }).filter { it > 0 }.maxOrNull() ?: return null
    return ParsedGuideline(v, unitIn(t))
}

// ------------------------------------------------------------------ place for the TNREGINET form

/** District / SRO / village / street / survey to pre-fill on the TNREGINET form, from what is saved or the address. */
data class GvPlace(val district: String, val sro: String, val village: String, val street: String, val survey: String)

private fun cleanDistrict(s: String) = s.replace(Regex("""(?i)\s*district$"""), "").replace(Regex("""\s*மாவட்டம்$"""), "").trim()

/** Saved registry fields first; blanks are filled from the map address (district, locality as the village). */
fun gvPlace(p: Property): GvPlace {
    val r = p.registry
    return GvPlace(
        district = r.district.ifBlank { cleanDistrict(p.district) },
        sro = r.sro,
        village = r.village.ifBlank { p.locality },
        street = r.street,
        survey = p.surveyNumber,
    )
}

private fun jsStr(s: String): String = buildString {
    append('"')
    s.forEach { c ->
        when (c) {
            '"' -> append("\\\""); '\\' -> append("\\\\"); '\n' -> append("\\n"); '\r' -> {}
            '<' -> append("\\u003c")
            else -> append(c)
        }
    }
    append('"')
}

/**
 * JavaScript that fills the TNREGINET "Guideline search" block one step at a time (district → SRO → village → street or
 * survey number), using the page's own controls (ids districtList / SROList / villageList / streetName / surveyRadio /
 * surveyName, as seen on the portal in 2026) and dispatching "change" so the page's own AJAX loads the next list.
 * It never changes a choice the user already made and never presses Submit. Run it again after each load; it returns
 * JSON {found, district, sro, village, text, waiting}.
 */
fun gvPrefillScript(place: GvPlace): String = """
(function(P){
 function norm(s){return (s||'').toLowerCase().replace(/[^a-z0-9\u0B80-\u0BFF]/g,'');}
 function el(ids){for(var i=0;i<ids.length;i++){var e=document.getElementById(ids[i])||document.querySelector(ids[i].charAt(0)=='['?ids[i]:'#__none');if(e)return e;}return null;}
 function chosen(s){return s&&s.selectedIndex>0&&s.value!='-1'&&s.value!='0'&&s.value!='';}
 function pick(s,want){ if(!s||!want) return false; var w=norm(want), best=null;
   for(var i=0;i<s.options.length;i++){var o=s.options[i], t=norm(o.text); if(!t||o.value=='-1'||o.value=='0'||o.value=='') continue;
     if(t==w){best=o;break;} if(!best&&Math.min(t.length,w.length)>=4&&(t.indexOf(w)>=0||w.indexOf(t)>=0)) best=o;}
   if(!best) return false; s.value=best.value; s.dispatchEvent(new Event('change',{bubbles:true}));
   try{ if(window.jQuery) window.jQuery(s).trigger('change'); }catch(e){} return true; }
 var out={found:false,district:false,sro:false,village:false,text:false,waiting:false};
 try{ document.querySelectorAll('.modal.in [data-dismiss="modal"], .modal.show [data-dismiss="modal"], .modal.in .close, .modal.show .close').forEach(function(b){try{b.click();}catch(e){}}); }catch(e){}
 var d=el(['districtList','[name="dr"]','zoneList']), s=el(['SROList']), v=el(['villageList']);
 if(!d){ return JSON.stringify(out); }
 out.found=true;
 if(!chosen(d)){ try{ d.scrollIntoView({block:'center'}); }catch(e){} out.district=pick(d,P.district); out.waiting=out.district; return JSON.stringify(out); }
 out.district=true;
 if(s&&!chosen(s)){ if(s.options.length<=1){out.waiting=true; return JSON.stringify(out);} out.sro=pick(s,P.sro); out.waiting=out.sro; if(out.sro) return JSON.stringify(out); }
 else out.sro=chosen(s);
 if(v&&!chosen(v)){ if(v.options.length<=1){out.waiting=out.sro; } else { out.village=pick(v,P.village); out.waiting=out.village; if(out.village) return JSON.stringify(out);} }
 else out.village=chosen(v);
 if(P.survey){ var r=document.getElementById('surveyRadio'); if(r&&!r.checked){ try{r.click();}catch(e){} }
   var sv=document.getElementById('surveyName'); if(sv&&!sv.value){ sv.value=P.survey; sv.dispatchEvent(new Event('input',{bubbles:true})); out.text=true; } else if(sv) out.text=true; }
 else if(P.street){ var st=document.getElementById('streetName'); if(st&&!st.value){ st.value=P.street; st.dispatchEvent(new Event('input',{bubbles:true})); out.text=true; } else if(st) out.text=true; }
 return JSON.stringify(out);
})({district:${jsStr(place.district)},sro:${jsStr(place.sro)},village:${jsStr(place.village)},street:${jsStr(place.street)},survey:${jsStr(place.survey.substringBefore("/").trim().ifBlank { place.survey })}})
"""

/**
 * JavaScript that reads the guideline result the user is looking at: the selected text (if any) and every table row /
 * line that carries a number with a rupee or unit word. Returns JSON {sel, rows:[...], url, title}. Nothing is guessed:
 * the user picks the row (or pastes) and it is saved with the page address and the date.
 */
const val GV_CAPTURE_JS = """
(function(){
 function txt(e){return ((e&&(e.innerText||e.textContent))||'').replace(/\s+/g,' ').trim();}
 var out={sel:'',rows:[],url:location.href,title:document.title};
 try{ out.sel=(window.getSelection?String(window.getSelection()):'').replace(/\s+/g,' ').trim().substring(0,400); }catch(e){}
 var seen={};
 function add(t){ if(!t||t.length<3||t.length>400||seen[t]) return; if(!/\d/.test(t)) return;
   if(!/(₹|rs\.?|ரூ|sq|ச\.|சதுர|hect|ஹெக்|acre|ஏக்கர்|cent|சென்ட்|guide|வழிகாட்டி|value|மதிப்பு)/i.test(t)) return;
   seen[t]=1; out.rows.push(t); }
 try{
  var docs=[document]; document.querySelectorAll('iframe').forEach(function(f){ try{ if(f.contentDocument) docs.push(f.contentDocument);}catch(e){} });
  docs.forEach(function(doc){
   doc.querySelectorAll('table tr').forEach(function(tr){ if(out.rows.length<40) add(txt(tr)); });
   doc.querySelectorAll('.modal.in, .modal.show, [role="dialog"], .table-responsive, #guidelineResult, .result').forEach(function(b){
     txt(b).split(/\n+/).forEach(function(l){ if(out.rows.length<40) add(l.trim()); }); });
  });
 }catch(e){}
 return JSON.stringify(out);
})()
"""

/** Rows of [GV_CAPTURE_JS]'s result that hold a readable value, with the value read from each (best first: unit named). */
fun captureCandidates(sel: String, rows: List<String>): List<Pair<String, ParsedGuideline>> =
    (listOf(sel) + rows).filter { it.isNotBlank() }.distinct()
        .mapNotNull { r -> parseGuidelineText(r)?.let { r to it } }
        .sortedByDescending { if (it.second.unit != null) 1 else 0 }

/** A guideline value as saved from the WebView, a manual entry or (never shown as official) an estimate. */
fun officialGuideline(value: Double, unit: GvUnit, method: String, sourceUrl: String, rawText: String, effectiveFrom: String, now: Long): OfficialGuideline =
    OfficialGuideline(
        value = value, unit = unit, method = method,
        source = if (method == OfficialGuideline.METHOD_MANUAL) "" else DutyRules.SOURCE_NAME,
        sourceUrl = sourceUrl, rawText = rawText.take(400), effectiveFrom = effectiveFrom.trim(), capturedAt = now,
    )

/** The registry info with blank fields filled from [gvPlace] (used when the user opens the form for the first time). */
fun RegistryInfo.withPlace(place: GvPlace): RegistryInfo = copy(
    district = district.ifBlank { place.district }, village = village.ifBlank { place.village },
)

/**
 * The property's own guideline value as the price check's guideline, in [basis] – plots, land and farmland only (for a
 * house or flat the land guideline value is not comparable with a built-up price). Null when none is saved.
 */
fun ownGuidelineInfo(p: Property, basis: PriceBasis): com.hark.sothudiary.domain.pricing.GuidelineInfo? {
    if (p.type.hasBuilding()) return null
    val g = p.registry.guideline ?: return null
    val v = convertGvRate(g.value, g.unit, basis.toGvUnit()).takeIf { it > 0 && !it.isNaN() && !it.isInfinite() } ?: return null
    val label = listOf(p.registry.street, p.registry.village.ifBlank { p.locality }).filter { it.isNotBlank() }.joinToString(", ").ifBlank { p.title }
    return com.hark.sothudiary.domain.pricing.GuidelineInfo(
        value = v, min = null, max = null, label = label, fromUser = true,
        sourceName = g.source, sourceUrl = g.sourceUrl, asOf = g.effectiveFrom,
    )
}
