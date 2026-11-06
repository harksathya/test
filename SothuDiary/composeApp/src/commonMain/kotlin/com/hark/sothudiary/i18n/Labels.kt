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
    Verdict.UNKNOWN -> Tr("Not enough data", "போதுமான தகவல் இல்லை")
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
}

fun PriceBasis.slashTr(): Tr = when (this) {
    PriceBasis.PER_CENT -> Tr("/cent", "/சென்ட்")
    PriceBasis.PER_SQFT -> Tr("/sq ft", "/ச.அடி")
}
