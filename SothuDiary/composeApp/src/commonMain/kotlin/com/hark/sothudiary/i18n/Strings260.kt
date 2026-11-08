package com.hark.sothudiary.i18n

/** 2.6.0 strings: AI search on tap (price / land), provider-neutral wording, .sothu import. */
object S6 {
    // ---- AI search on tap (issue #1 leftovers) ----
    val aiSearchLand = Tr("Search zoning, roads & growth online (AI · {0})", "மண்டலம், சாலைகள், வளர்ச்சியை இணையத்தில் தேடு (AI · {0})")
    val aiSearchPrice = Tr("Search nearby prices online (AI · {0})", "அருகிலுள்ள விலைகளை இணையத்தில் தேடு (AI · {0})")
    val aiSearching = Tr("Searching online… (it keeps going if you leave this screen)", "இணையத்தில் தேடுகிறது… (திரையை விட்டுச் சென்றாலும் தொடரும்)")
    val aiOnTapNote = Tr(
        "Runs only when you tap it, with your own AI key. It reads public web pages; every item links to the page it came from – check before you rely on it.",
        "நீங்கள் அழுத்தும்போது மட்டும், உங்கள் சொந்த AI key-யுடன் இயங்கும். பொது இணையப் பக்கங்களைப் படிக்கும்; ஒவ்வொன்றுக்கும் அது வந்த பக்க இணைப்பு உண்டு – நம்புவதற்கு முன் சரிபாருங்கள்."
    )
    val aiNotRunYet = Tr("Not searched yet.", "இன்னும் தேடவில்லை.")
    val aiLastRun = Tr("Last searched {0}", "கடைசியாகத் தேடியது {0}")
    val needPinForAi = Tr("Set the property's map location first – the search is for its own place.", "முதலில் சொத்தின் வரைபட இடத்தை அமையுங்கள் – தேடல் அந்த இடத்துக்கே.")
    val landAiNeedKey = Tr(
        "Add an AI key (any provider – Gemini, OpenAI, Claude, OpenRouter, Groq …) to also search zoning, announced roads and growth online.",
        "மண்டலம், அறிவிக்கப்பட்ட சாலைகள், வளர்ச்சியை இணையத்தில் தேட AI key சேர்க்கவும் (எந்த சேவையும் – Gemini, OpenAI, Claude, OpenRouter, Groq …)."
    )
    val priceNeedKey = Tr(
        "Add an AI key (any provider) to also search nearby asking prices online, with sources.",
        "அருகிலுள்ள விலைகளை மூலங்களுடன் இணையத்தில் தேட AI key சேர்க்கவும் (எந்த சேவையும்)."
    )
    val addAiKey = Tr("Add AI key", "AI key சேர்")
    val lrGrowthNoKey = Tr("Needs an AI key (online search)", "AI key தேவை (இணையத் தேடல்)")
    val lrGrowthNotRun = Tr("Not searched yet – tap “Search … online (AI)” below", "இன்னும் தேடவில்லை – கீழே “இணையத்தில் தேடு (AI)” அழுத்துங்கள்")
    val rsOnlineNotRun = Tr("not searched yet – optional, on tap", "இன்னும் தேடவில்லை – விருப்பம், அழுத்தினால் மட்டும்")
    val rsOnlineNoKey = Tr("not used – no AI key (optional)", "பயன்படுத்தவில்லை – AI key இல்லை (விருப்பம்)")
    val rsOnlineFound = Tr("{0} rates within 10 km ({1})", "10 கி.மீ.க்குள் {0} விலைகள் ({1})")
    val rsOnlineFailed = Tr("failed: {0}", "தோல்வி: {0}")

    // ---- provider-neutral error texts (were "Gemini …" for every provider) ----
    val errQuota = Tr("The AI service's free limit is used up for now – try again in a few minutes.", "AI சேவையின் இலவச வரம்பு தற்போது முடிந்தது – சில நிமிடம் கழித்து முயற்சிக்கவும்.")
    val errQuotaAfter = Tr("The AI service's free limit is used up for now – try again after {0}.", "AI சேவையின் இலவச வரம்பு தற்போது முடிந்தது – {0} கழித்து முயற்சிக்கவும்.")
    val errBadKey = Tr("Your AI key was not accepted. Check it in Settings.", "உங்கள் AI key ஏற்கப்படவில்லை. அமைப்புகளில் சரிபாருங்கள்.")
    val errModel = Tr("That AI model is not available for your key. Set the model to Auto in Settings.", "அந்த AI மாடல் உங்கள் key-க்குக் கிடைக்கவில்லை. அமைப்புகளில் மாடலை “தானியங்கி” ஆக்குங்கள்.")
    val srcAi = Tr("AI estimate (with web search) for zoning, announced roads and growth – check each source page:", "மண்டலம், அறிவிக்கப்பட்ட சாலைகள், வளர்ச்சி – AI மதிப்பீடு (இணையத் தேடலுடன்) – ஒவ்வொரு மூலப் பக்கத்தையும் சரிபாருங்கள்:")
    val srcWebSearch = Tr("AI estimate from a free web search read by your AI service – check each page:", "உங்கள் AI சேவை படித்த இலவச இணையத் தேடலில் இருந்து AI மதிப்பீடு – ஒவ்வொரு பக்கத்தையும் சரிபாருங்கள்:")
    val viaSearchNote = Tr("Found with a free web search read by your AI service. Each rate links to its page.", "உங்கள் AI சேவை படித்த இலவச இணையத் தேடலில் கிடைத்தது. ஒவ்வொரு விலைக்கும் அதன் பக்க இணைப்பு உண்டு.")
    val priceRulesText = Tr(
        "The price check compares the quoted rate with your own saved listings of the same type and the rates you add (paste a listing or type a rate you saw), placed on the map: only those within 5 km are used, widening (with a warning) to a hard maximum of 10 km when fewer than 3 are found. The property's own guideline value (TNREGINET) is shown beside it. With an AI key (any provider) you can also tap “Search nearby prices online” – it runs only on your tap, and every rate it finds links to its page.",
        "விலை சரிபார்ப்பு, கேட்கப்படும் விலையை உங்கள் சேமித்த அதே வகைச் சொத்துகளுடனும் நீங்கள் சேர்க்கும் விலைகளுடனும் (விளம்பரத்தை ஒட்டவும் அல்லது பார்த்த விலையைத் தட்டச்சு செய்யவும்) வரைபடத்தில் ஒப்பிடும்: 5 கி.மீ.க்குள் உள்ளவை மட்டும்; 3-க்குக் குறைவாக இருந்தால் (எச்சரிக்கையுடன்) அதிகபட்சம் 10 கி.மீ. வரை. சொத்தின் வழிகாட்டி மதிப்பு (TNREGINET) அருகில் காட்டப்படும். AI key (எந்த சேவையும்) இருந்தால் “அருகிலுள்ள விலைகளை இணையத்தில் தேடு” அழுத்தலாம் – அழுத்தினால் மட்டும் இயங்கும், ஒவ்வொரு விலைக்கும் அதன் பக்க இணைப்பு உண்டு."
    )

    // ---- .sothu import ----
    val importFile = Tr("Import .sothu file", ".sothu கோப்பை இறக்கு")
    val importFileHint = Tr("Got a property from another phone as a .sothu file (WhatsApp, Files)? Import it here.", "வேறு போனிலிருந்து .sothu கோப்பாக சொத்து வந்ததா (WhatsApp, Files)? இங்கே இறக்குங்கள்.")
    val importReading = Tr("Reading the file…", "கோப்பைப் படிக்கிறது…")
    val importNoFile = Tr("Could not open that file.", "அந்தக் கோப்பைத் திறக்க முடியவில்லை.")
}
