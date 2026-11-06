package com.hark.sothudiary.i18n

import com.hark.sothudiary.data.AreaUnit
import com.hark.sothudiary.data.DealStatus
import com.hark.sothudiary.data.DocItem
import com.hark.sothudiary.data.InteractionType
import com.hark.sothudiary.data.PropertyType
import com.hark.sothudiary.data.SourcePlatform
import com.hark.sothudiary.domain.PriceBasis
import com.hark.sothudiary.domain.pricing.Advice
import com.hark.sothudiary.domain.pricing.Verdict
import com.hark.sothudiary.domain.pricing.Confidence
import com.hark.sothudiary.domain.pricing.CompSource
import com.hark.sothudiary.data.ThemeMode

fun PropertyType.tr(): Tr = when (this) {
    PropertyType.LAND -> Tr("Land", "நிலம்")
    PropertyType.HOUSE -> Tr("House", "வீடு")
    PropertyType.FARMLAND -> Tr("Farmland", "பண்ணை நிலம்")
    PropertyType.AGRI_LAND -> Tr("Agricultural land", "விவசாய நிலம்")
    PropertyType.APARTMENT -> Tr("Apartment", "அடுக்குமாடி குடியிருப்பு")
    PropertyType.PLOT -> Tr("Plot", "மனை")
}

fun AreaUnit.tr(): Tr = when (this) {
    AreaUnit.CENTS -> Tr("cents", "சென்ட்")
    AreaUnit.ACRES -> Tr("acres", "ஏக்கர்")
    AreaUnit.SQFT -> Tr("sq ft", "சதுர அடி")
}

fun DealStatus.tr(): Tr = when (this) {
    DealStatus.NEW -> Tr("New", "புதியது")
    DealStatus.CONTACTED -> Tr("Contacted", "தொடர்பு கொண்டது")
    DealStatus.SITE_VISIT -> Tr("Site visit", "நேரில் பார்வை")
    DealStatus.NEGOTIATING -> Tr("Negotiating", "பேரம் பேசுகிறது")
    DealStatus.BOUGHT -> Tr("Bought", "வாங்கியது")
    DealStatus.DROPPED -> Tr("Dropped", "கைவிடப்பட்டது")
}

fun SourcePlatform.tr(): Tr = when (this) {
    SourcePlatform.YOUTUBE -> Tr("YouTube", "யூடியூப்")
    SourcePlatform.INSTAGRAM -> Tr("Instagram", "இன்ஸ்டாகிராம்")
    SourcePlatform.FACEBOOK -> Tr("Facebook", "ஃபேஸ்புக்")
    SourcePlatform.WHATSAPP -> Tr("WhatsApp", "வாட்ஸ்அப்")
    SourcePlatform.OTHER -> Tr("Other link", "மற்ற இணைப்பு")
    SourcePlatform.NONE -> Tr("No link", "இணைப்பு இல்லை")
}

fun DocItem.tr(): Tr = when (this) {
    DocItem.PATTA -> Tr("Patta", "பட்டா")
    DocItem.CHITTA -> Tr("Chitta", "சிட்டா")
    DocItem.EC -> Tr("EC (Encumbrance Certificate)", "வில்லங்கச் சான்றிதழ் (EC)")
    DocItem.APPROVAL -> Tr("DTCP / RERA approval", "DTCP / RERA அனுமதி")
    DocItem.ROAD_ACCESS -> Tr("Road access", "சாலை வசதி")
    DocItem.WATER -> Tr("Water", "தண்ணீர் வசதி")
    DocItem.ELECTRICITY -> Tr("Electricity (EB)", "மின்சார இணைப்பு (EB)")
    DocItem.FMB_SKETCH -> Tr("FMB sketch", "FMB வரைபடம்")
}

fun InteractionType.tr(): Tr = when (this) {
    InteractionType.CALL -> Tr("Call", "அழைப்பு")
    InteractionType.VISIT -> Tr("Visit", "நேரில் சென்றது")
    InteractionType.WHATSAPP -> Tr("WhatsApp", "வாட்ஸ்அப்")
    InteractionType.MEETING -> Tr("Meeting", "சந்திப்பு")
}

fun Verdict.tr(): Tr = when (this) {
    Verdict.SUSPICIOUS_LOW -> Tr("Suspiciously low – possible false promise", "சந்தேகமான குறைந்த விலை – பொய் வாக்குறுதியாக இருக்கலாம்")
    Verdict.GOOD -> Tr("Good price (below market)", "நல்ல விலை (சந்தையை விடக் குறைவு)")
    Verdict.FAIR -> Tr("Fair", "நியாயமான விலை")
    Verdict.HIGH -> Tr("High", "அதிக விலை")
    Verdict.TOO_HIGH -> Tr("Too high", "மிக அதிக விலை")
    Verdict.UNKNOWN -> Tr("Not enough data within 10 km", "10 கி.மீ.க்குள் போதுமான தகவல் இல்லை")
}

/** Short version for badges on cards. */
fun Verdict.shortTr(): Tr = when (this) {
    Verdict.SUSPICIOUS_LOW -> Tr("Suspiciously low", "சந்தேக விலை")
    Verdict.GOOD -> Tr("Good price", "நல்ல விலை")
    Verdict.FAIR -> Tr("Fair price", "நியாய விலை")
    Verdict.HIGH -> Tr("High", "அதிக விலை")
    Verdict.TOO_HIGH -> Tr("Too high", "மிக அதிகம்")
    Verdict.UNKNOWN -> Tr("No price data", "தகவல் இல்லை")
}

fun Advice.tr(): Tr = when (this) {
    Advice.GO -> Tr("Go", "தொடரலாம்")
    Advice.HOLD -> Tr("Hold", "காத்திருங்கள்")
    Advice.DROP -> Tr("Drop", "விட்டுவிடுங்கள்")
}

fun PriceBasis.perTr(): Tr = when (this) {
    PriceBasis.PER_CENT -> Tr("per cent", "ஒரு சென்ட்")
    PriceBasis.PER_SQFT -> Tr("per sq ft", "ஒரு சதுர அடி")
    PriceBasis.PER_ACRE -> Tr("per acre", "ஒரு ஏக்கர்")
}

fun PriceBasis.slashTr(): Tr = when (this) {
    PriceBasis.PER_CENT -> Tr("/cent", "/சென்ட்")
    PriceBasis.PER_SQFT -> Tr("/sq ft", "/ச.அடி")
    PriceBasis.PER_ACRE -> Tr("/acre", "/ஏக்கர்")
}

fun Confidence.tr(): Tr = when (this) {
    Confidence.HIGH -> Tr("High confidence", "அதிக நம்பகத்தன்மை")
    Confidence.MEDIUM -> Tr("Medium confidence", "நடுத்தர நம்பகத்தன்மை")
    Confidence.LOW -> Tr("Low confidence", "குறைந்த நம்பகத்தன்மை")
}

fun CompSource.tr(): Tr = when (this) {
    CompSource.YOUR_LISTING -> Tr("Your listing", "உங்கள் பதிவு")
    CompSource.MARKET -> Tr("Market data", "சந்தை தகவல்")
    CompSource.MARKET_FALLBACK -> Tr("Area average (mixed types)", "பகுதி சராசரி (கலப்பு வகை)")
    CompSource.CITY_AVERAGE -> Tr("City-wide average", "நகர சராசரி")
    CompSource.ONLINE -> Tr("Live web lookup", "நேரடி இணையத் தேடல்")
}

fun com.hark.sothudiary.domain.PostClaim.tr(): Tr = when (this) {
    com.hark.sothudiary.domain.PostClaim.DTCP -> Tr("DTCP / approved", "DTCP / அனுமதி பெற்றது")
    com.hark.sothudiary.domain.PostClaim.RERA -> Tr("RERA", "RERA பதிவு")
    com.hark.sothudiary.domain.PostClaim.PATTA -> Tr("Patta", "பட்டா")
    com.hark.sothudiary.domain.PostClaim.ROAD -> Tr("Road access", "சாலை வசதி")
    com.hark.sothudiary.domain.PostClaim.CORNER -> Tr("Corner plot", "மூலை மனை")
    com.hark.sothudiary.domain.PostClaim.EB -> Tr("Electricity (EB)", "மின்சார வசதி")
    com.hark.sothudiary.domain.PostClaim.WATER -> Tr("Water / well", "தண்ணீர் / கிணறு")
    com.hark.sothudiary.domain.PostClaim.GATED -> Tr("Gated", "பாதுகாப்பு வளாகம்")
    com.hark.sothudiary.domain.PostClaim.LOAN -> Tr("Loan / EMI", "வங்கிக் கடன் வசதி")
    com.hark.sothudiary.domain.PostClaim.NEGOTIABLE -> Tr("Negotiable", "பேரம் பேசலாம்")
    com.hark.sothudiary.domain.PostClaim.FENCED -> Tr("Fenced", "வேலி உள்ளது")
    com.hark.sothudiary.domain.PostClaim.RESIDENTIAL_AREA -> Tr("Houses around", "சுற்றிலும் வீடுகள்")
}

fun ThemeMode.tr(): Tr = when (this) {
    ThemeMode.SYSTEM -> Tr("System", "சாதன அமைப்பு")
    ThemeMode.LIGHT -> Tr("Light", "வெளிர்")
    ThemeMode.DARK -> Tr("Dark", "இருள்")
}

/** Names of the comparison groups (0 plot / land, 1 farmland, 2 house, 3 apartment). */
fun groupTr(group: Int): Tr = when (group) {
    1 -> Tr("Farmland / agri land", "விவசாய / பண்ணை நிலம்")
    2 -> Tr("House / villa", "வீடு / வில்லா")
    3 -> Tr("Apartment / flat", "அடுக்குமாடி / பிளாட்")
    else -> Tr("Plot / land", "மனை / நிலம்")
}
