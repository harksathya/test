package com.hark.sothudiary.i18n

import com.hark.sothudiary.AppName

/** 2.4.0 strings (simpler property screen, shared land details, status panels, share, map, score). */
object S4 {
    // ---- About: village list credit (GODL-India attribution) ----
    val villageDataCredit = Tr(
        "Village list (Tamil Nadu districts, taluks, villages): contains administrative data from the Local Government Directory (LGD), Ministry of Panchayati Raj, Government of India, used under the Government Open Data License – India (GODL-India), accessed via data.gov.in; compiled by github.com/mchittineni/india-village-finder v1.3.0. Modified: Tamil Nadu only, reformatted for the app. Not endorsed by the Government of India.",
        "கிராமப் பட்டியல் (தமிழ்நாடு மாவட்டங்கள், வட்டங்கள், கிராமங்கள்): இந்திய அரசின் பஞ்சாயத்து ராஜ் அமைச்சகத்தின் உள்ளாட்சி அடைவு (LGD) நிர்வாகத் தரவு, இந்திய அரசு திறந்த தரவு உரிமம் (GODL-India) கீழ், data.gov.in வழியாக; தொகுப்பு: github.com/mchittineni/india-village-finder v1.3.0. மாற்றம்: தமிழ்நாடு மட்டும், செயலிக்கேற்ப வடிவமைக்கப்பட்டது. இந்திய அரசின் ஒப்புதல் அல்ல."
    )
    // ---- Checks row ----
    val checks = Tr("Checks", "ஆய்வுகள்")
    val priceCheckTile = Tr("Price check", "விலை ஆய்வு")
    val landCheckTile = Tr("Land check", "நில ஆய்வு")
    val gvTile = Tr("Guideline value + duty", "வழிகாட்டி மதிப்பு + முத்திரை")
    val notChecked = Tr("Not checked · Tap to check", "சரிபார்க்கவில்லை · தொட்டுப் பாருங்கள்")
    val checking = Tr("Checking…", "சரிபார்க்கிறது…")
    val needPin = Tr("Add the map pin first", "முதலில் வரைபடப் புள்ளியைச் சேருங்கள்")
    val belowArea = Tr("{0} below area", "பகுதியை விட {0} குறைவு")
    val aboveArea = Tr("{0} above area", "பகுதியை விட {0} அதிகம்")
    val atArea = Tr("about the area rate", "பகுதி விலைக்கு நிகர்")
    val noComparables = Tr("No nearby rates yet", "அருகில் விலை விவரம் இல்லை")
    val waterRisk = Tr("Water risk: {0}", "நீர் அபாயம்: {0}")
    val riskLow = Tr("Low", "குறைவு")
    val riskMedium = Tr("Medium", "நடுத்தரம்")
    val riskHigh = Tr("High", "அதிகம்")
    val riskUnknown = Tr("Unknown", "தெரியவில்லை")
    val gvLine = Tr("GV {0} · Duty {1}", "வ.ம. {0} · முத்திரை {1}")
    val dutyOnly = Tr("Duty {0} (on price)", "முத்திரை {0} (விலையில்)")
    val partial = Tr("partial", "பகுதி")
    val sourceLabel = Tr("Source: {0}", "ஆதாரம்: {0}")

    // ---- Status panel ----
    val status = Tr("Status / நிலை", "நிலை / Status")
    val details = Tr("Details", "விவரங்கள்")
    val hideDetails = Tr("Hide details", "விவரங்களை மறை")
    val statusHint = Tr("Each source the check used. Take a screenshot of this if something fails.", "இந்த ஆய்வு பயன்படுத்திய ஒவ்வொரு ஆதாரமும். ஏதாவது தோல்வியடைந்தால் இதை ஸ்கிரீன்ஷாட் எடுங்கள்.")
    val skipped = Tr("skipped", "தவிர்க்கப்பட்டது")
    val runningFor = Tr("Running… {0} s (it keeps going if you leave this screen)", "நடக்கிறது… {0} வி (இந்தத் திரையை விட்டுச் சென்றாலும் தொடரும்)")
    val noStatusYet = Tr("No check has run yet.", "இன்னும் ஆய்வு நடக்கவில்லை.")

    // ---- Price check screen ----
    val priceNoKeyTitle = Tr("Online price lookup is off", "இணைய விலைத் தேடல் இயக்கத்தில் இல்லை")
    val priceNoKeyBody = Tr(
        "Without a Gemini key the price check compares only with your own saved listings and the guideline value. Add a free key for nearby rates with sources, or search the web yourself.",
        "ஜெமினி சாவி இல்லாமல் விலை ஆய்வு உங்கள் சேமித்த பட்டியல்களுடனும் வழிகாட்டி மதிப்புடனும் மட்டுமே ஒப்பிடும். ஆதாரங்களுடன் அருகிலுள்ள விலைகளுக்கு இலவச சாவியைச் சேர்க்கவும், அல்லது நீங்களே இணையத்தில் தேடுங்கள்.",
    )
    val searchWebYourself = Tr("Search the web", "இணையத்தில் தேடு")
    val runPriceCheck = Tr("Check price now", "இப்போது விலையைச் சரிபார்")
    val addKey = Tr("Add Gemini key", "ஜெமினி சாவி சேர்")

    // ---- Land details (shared record) ----
    val landDetails = Tr("Land details / நில விவரம்", "நில விவரம் / Land details")
    val landDetailsHint = Tr(
        "Enter once – used by the Land check, Guideline value, Price check, EC / patta links and Share.",
        "ஒருமுறை உள்ளிடுங்கள் – நில ஆய்வு, வழிகாட்டி மதிப்பு, விலை ஆய்வு, EC / பட்டா இணைப்புகள், பகிர்வு அனைத்திலும் பயன்படும்.",
    )
    val district = Tr("District", "மாவட்டம்")
    val taluk = Tr("Taluk", "வட்டம்")
    val village = Tr("Village", "கிராமம்")
    val sro = Tr("Sub-registrar office (SRO)", "சார்பதிவாளர் அலுவலகம்")
    val surveyNo = Tr("Survey no.", "புல எண்")
    val subdivision = Tr("Subdivision", "உட்பிரிவு")
    val pattaNo = Tr("Patta no.", "பட்டா எண்")
    val street = Tr("Street (for guideline value)", "தெரு (வழிகாட்டி மதிப்புக்கு)")
    val area = Tr("Area", "பரப்பு")
    val fillFromPin = Tr("Fill from map pin", "வரைபடப் புள்ளியிலிருந்து நிரப்பு")
    val filledFromPin = Tr("from map pin", "வரைபடப் புள்ளியிலிருந்து")
    val editLand = Tr("Edit land details", "நில விவரம் திருத்து")
    val landEmpty = Tr("Not entered yet", "இன்னும் உள்ளிடவில்லை")
    val officialLinks = Tr("Official sites (open in browser)", "அதிகாரப்பூர்வ தளங்கள் (உலாவியில் திறக்கும்)")
    val ecLink = Tr("EC (TNREGINET)", "வில்லங்கச் சான்று (TNREGINET)")
    val pattaLink = Tr("Patta / chitta (e-Services)", "பட்டா / சிட்டா (இ-சேவை)")
    val tngisLink = Tr("TNGIS map", "TNGIS வரைபடம்")
    val copied = Tr("Copied", "நகலெடுக்கப்பட்டது")

    // ---- Sections ----
    val brokerCalls = Tr("Broker & calls", "தரகர் & அழைப்புகள்")
    val notesSource = Tr("Notes & source", "குறிப்புகள் & ஆதாரம்")
    val docsSection = Tr("Documents", "ஆவணங்கள்")
    val locationSection = Tr("Location", "இருப்பிடம்")
    val moreSection = Tr("Folders, status & reviews", "கோப்புறைகள், நிலை & மதிப்புரைகள்")

    // ---- Score ----
    val score = Tr("Score", "மதிப்பெண்")
    val scoreTitle = Tr("Property score", "சொத்து மதிப்பெண்")
    val scoreHint = Tr("From your own checks only. Missing checks are left out, not counted as zero. Weights: Settings.",
        "உங்கள் ஆய்வுகளிலிருந்து மட்டும். செய்யாத ஆய்வுகள் கணக்கில் சேராது. எடைகள்: அமைப்புகள்.")
    val scoreNone = Tr("Run a check to get a score", "மதிப்பெண் பெற ஒரு ஆய்வைச் செய்யுங்கள்")
    val fPrice = Tr("Price vs area", "பகுதி விலையுடன் ஒப்பீடு")
    val fWater = Tr("Water / flood risk", "நீர் / வெள்ள அபாயம்")
    val fRoad = Tr("Road access", "சாலை வசதி")
    val fAmenities = Tr("Amenities nearby", "அருகிலுள்ள வசதிகள்")
    val fGuideline = Tr("Guideline-value gap", "வழிகாட்டி மதிப்பு இடைவெளி")
    val notYet = Tr("not checked", "சரிபார்க்கவில்லை")
    val scoreWeights = Tr("Score weights", "மதிப்பெண் எடைகள்")
    val scoreWeightsHint = Tr("Weights are relative; set one to 0 to ignore it.", "எடைகள் ஒப்பீட்டளவில்; 0 வைத்தால் அது கணக்கில் சேராது.")
    val resetWeights = Tr("Reset", "மீட்டமை")

    // ---- Share ----
    val share = Tr("Share", "பகிர்")
    val shareText = Tr("Share text + link", "உரை + இணைப்பைப் பகிர்")
    val shareTextHint = Tr("WhatsApp, Instagram, Facebook… – a clean summary with the map and source links.", "WhatsApp, Instagram, Facebook… – வரைபட, மூல இணைப்புகளுடன் சுருக்கம்.")
    val shareImage = Tr("Share as image", "படமாகப் பகிர்")
    val shareImageHint = Tr("A card for a post or WhatsApp status.", "பதிவு அல்லது WhatsApp ஸ்டேட்டஸுக்கான அட்டை.")
    val shareFile = Tr("Send to another phone ({0} file)", "மற்றொரு போனுக்கு அனுப்பு ({0} கோப்பு)")
    val shareFileHint = Tr("The other phone opens it in the app and can save it with all details.", "மற்ற போன் இதை ஆப்பில் திறந்து அனைத்து விவரங்களுடன் சேமிக்கலாம்.")
    val includeBroker = Tr("Include broker name & phone", "தரகர் பெயர் & எண்ணைச் சேர்")
    val includeNotes = Tr("Include my notes", "என் குறிப்புகளைச் சேர்")
    val includeChecks = Tr("Include check results", "ஆய்வு முடிவுகளைச் சேர்")
    val shareFailed = Tr("Could not make the image", "படத்தை உருவாக்க முடியவில்லை")
    val tLocation = Tr("Location", "இருப்பிடம்")
    val tMap = Tr("Map", "வரைபடம்")
    val tLand = Tr("Land", "நிலம்")
    val tChecks = Tr("Checks", "ஆய்வுகள்")
    val tSource = Tr("Original post", "மூலப் பதிவு")
    val tBroker = Tr("Broker", "தரகர்")
    val tOpenInApp = Tr("Open in {0}:", "{0} செயலியில் திற:")
    val tPrice = Tr("Price", "விலை")
    val tArea = Tr("Area", "பரப்பு")
    val importPreview = Tr("Import property", "சொத்தை இறக்கு")
    val importNotSothu = Tr("This file is not a {0} property.", "இது {0} சொத்துக் கோப்பு அல்ல.")

    // ---- Map ----
    val colorBy = Tr("Color", "நிறம்")
    val byVerdict = Tr("Verdict", "முடிவு")
    val byPrice = Tr("₹/sqft", "₹/ச.அடி")
    val byWater = Tr("Water risk", "நீர் அபாயம்")
    val byScore = Tr("Score", "மதிப்பெண்")
    val inView = Tr("{0} in view", "பார்வையில் {0}")
    val bestFirst = Tr("Best first", "சிறந்தவை முதலில்")
    val comparables = Tr("Comparables", "ஒப்பீட்டு விலைகள்")
    val budget = Tr("Budget", "பட்ஜெட்")
    val anyBudget = Tr("Any budget", "எந்த பட்ஜெட்டும்")
    val searchMapped = Tr("Search {0} mapped", "{0} வரைபடப் பதிவுகளைத் தேடு")
    val zoomToSee = Tr("Zoom in or open the list", "பெரிதாக்குங்கள் அல்லது பட்டியலைத் திறங்கள்")
    val osmCredit = Tr("© OpenStreetMap contributors (ODbL)", "© OpenStreetMap பங்களிப்பாளர்கள் (ODbL)")
    val legendNoData = Tr("not checked", "சரிபார்க்கவில்லை")

    // ---- Guideline portal status ----
    val gvStepPage = Tr("TNREGINET page", "TNREGINET பக்கம்")
    val gvStepForm = Tr("Guideline form on the page", "பக்கத்தில் வழிகாட்டி படிவம்")
    val gvStepNotInList = Tr("“{0}” is not in the list ({1} options, e.g. {2}) – pick it on the page", "“{0}” பட்டியலில் இல்லை ({1} தேர்வுகள், எ.கா. {2}) – பக்கத்தில் தேர்ந்தெடுங்கள்")
    val gvStepWaiting = Tr("waiting for the list to load…", "பட்டியல் ஏறக் காத்திருக்கிறது…")
    val gvSavedChoice = Tr("Saved your page choices to Land details", "பக்கத்தில் தேர்ந்தவை நில விவரத்தில் சேமிக்கப்பட்டன")

    // ---- Price check: the user's own rates ----
    val rsYourRates = Tr("Rates you saw", "நீங்கள் பார்த்த விலைகள்")
    val rsAddRateHint = Tr(
        "Prices come from your own listings and the rates you add here (a listing, a broker's message, a guideline value you read). Nothing is fetched from price sites automatically – only when you tap the online search.",
        "விலைகள் உங்கள் சொந்தப் பட்டியல்களிலிருந்தும் நீங்கள் இங்கே சேர்க்கும் விலைகளிலிருந்தும் (விளம்பரம், தரகர் செய்தி, நீங்கள் படித்த வழிகாட்டி மதிப்பு) மட்டுமே. விலை தளங்களிலிருந்து எதுவும் தானாக எடுக்கப்படுவதில்லை – இணையத் தேடலை நீங்கள் அழுத்தினால் மட்டும்.",
    )
    val rsAddRate = Tr("Add a rate you saw", "நீங்கள் பார்த்த விலையைச் சேர்")
    val rsPasteHint = Tr("Paste the listing or message – the rate is read for you. Or type it.", "விளம்பரம் அல்லது செய்தியை ஒட்டுங்கள் – விலை தானாகப் படிக்கப்படும். அல்லது தட்டச்சு செய்யுங்கள்.")
    val rsPasteNow = Tr("Paste", "ஒட்டு")
    val rsNothingFound = Tr("No ₹ per sq ft / cent / acre found – type the rate below.", "₹ / ச.அடி / சென்ட் / ஏக்கர் விலை இல்லை – கீழே தட்டச்சு செய்யுங்கள்.")
    val rsOrType = Tr("Rate (₹)", "விலை (₹)")
    val rsIsGuideline = Tr("This is a guideline (government) value", "இது வழிகாட்டி (அரசு) மதிப்பு")
    val rsPlace = Tr("Place", "இடம்")
    val rsLink = Tr("Link (optional)", "இணைப்பு (விருப்பம்)")
    val rsEnteredByYou = Tr("Entered by you", "நீங்கள் உள்ளிட்டது")
    val rsSourceOwn = Tr("Your saved listings", "உங்கள் சேமித்த பட்டியல்கள்")
    val rsSourceEntered = Tr("Rates you added", "நீங்கள் சேர்த்த விலைகள்")
    val rsUsedCount = Tr("{0} used", "{0} பயன்படுத்தப்பட்டன")
    val rsGvSaved = Tr("saved", "சேமிக்கப்பட்டது")
    val rsGvMissing = Tr("not entered yet", "இன்னும் உள்ளிடவில்லை")
    val rsSourceOnline = Tr("Online price sites", "இணைய விலை தளங்கள்")
    val rsOnlineOff = Tr("not used (no automatic price data)", "பயன்படுத்தவில்லை (தானியங்கி விலை தரவு இல்லை)")
    val gvShort = Tr("guideline", "வழிகாட்டி")
    val landPasteHint = Tr("Paste patta / EC text to fill these", "இவற்றை நிரப்ப பட்டா / EC உரையை ஒட்டுங்கள்")
    val lgdCredit = Tr("Names: LGD via data.gov.in (GODL)", "பெயர்கள்: LGD, data.gov.in (GODL)")
    val useTyped = Tr("Use “{0}”", "“{0}” பயன்படுத்து")

    val appNameLine = Tr(AppName.EN, AppName.TA)
}
