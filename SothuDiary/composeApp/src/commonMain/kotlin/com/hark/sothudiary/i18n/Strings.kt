package com.hark.sothudiary.i18n

/**
 * All UI strings in English and Tamil. Switched at runtime by the "த / EN" toggle.
 * Placeholders: {0}, {1}, ...
 */
object S {
    // App & navigation
    val appName = Tr("Sothu Diary", "சொத்து டைரி")
    val appTagline = Tr("Your property deal tracker", "உங்கள் சொத்து பேர டைரி")
    val tabProperties = Tr("Properties", "சொத்துகள்")
    val tabBrokers = Tr("Brokers", "தரகர்கள்")
    val tabFollowUps = Tr("Follow-ups", "பின்தொடர்வு")
    val tabGuidelines = Tr("Guideline", "வழிகாட்டி மதிப்பு")
    val tabSettings = Tr("Settings", "அமைப்புகள்")
    val back = Tr("Back", "பின்செல்")
    val selectSomething = Tr("Nothing selected", "எதுவும் தேர்ந்தெடுக்கவில்லை")
    val selectSomethingBody = Tr("Pick an item from the list to see its details here.", "விவரங்களைப் பார்க்க பட்டியலில் ஒன்றைத் தேர்ந்தெடுங்கள்.")

    // Common actions
    val add = Tr("Add", "சேர்")
    val save = Tr("Save", "சேமி")
    val cancel = Tr("Cancel", "ரத்து")
    val delete = Tr("Delete", "நீக்கு")
    val edit = Tr("Edit", "திருத்து")
    val clear = Tr("Clear", "அழி")
    val done = Tr("Done", "முடிந்தது")
    val ok = Tr("OK", "சரி")
    val call = Tr("Call", "அழை")
    val whatsapp = Tr("WhatsApp", "வாட்ஸ்அப்")
    val notFound = Tr("Not found", "கிடைக்கவில்லை")
    val sample = Tr("Sample", "மாதிரி")
    val pickDate = Tr("Pick a date", "தேதியைத் தேர்ந்தெடு")

    // Property list
    val addProperty = Tr("Add property", "சொத்து சேர்")
    val searchHint = Tr("Search title, area, broker, notes", "தலைப்பு, இடம், தரகர், குறிப்பு தேடு")
    val filters = Tr("Filters", "வடிகட்டிகள்")
    val sort = Tr("Sort", "வரிசைப்படுத்து")
    val sortNewest = Tr("Newest first", "புதியவை முதலில்")
    val sortPriceLow = Tr("Price: low to high", "விலை: குறைவு → அதிகம்")
    val sortPriceHigh = Tr("Price: high to low", "விலை: அதிகம் → குறைவு")
    val sortPerCent = Tr("Price per cent", "ஒரு சென்ட் விலை")
    val sortFollowUp = Tr("Next follow-up", "அடுத்த பின்தொடர்வு")
    val sortedBy = Tr("Sort: {0}", "வரிசை: {0}")
    val nResults = Tr("{0} properties", "{0} சொத்துகள்")
    val type = Tr("Type", "வகை")
    val status = Tr("Status", "நிலை")
    val priceVerdict = Tr("Price verdict", "விலை மதிப்பீடு")
    val priceRange = Tr("Price range", "விலை வரம்பு")
    val minPrice = Tr("Min price", "குறைந்தபட்ச விலை")
    val maxPrice = Tr("Max price", "அதிகபட்ச விலை")
    val priceInputHint = Tr("e.g. 4500000, 45L, 45 lakh, 1.2Cr", "எ.கா. 4500000, 45L, 45 லட்சம், 1.2Cr")
    val clearFilters = Tr("Clear filters", "வடிகட்டிகளை அழி")
    val noPropertiesYet = Tr("No properties yet", "இன்னும் சொத்துகள் இல்லை")
    val noPropertiesBody = Tr("Tap “Add property”, or share a YouTube / Instagram / Facebook link to Sothu Diary.", "“சொத்து சேர்” அழுத்துங்கள், அல்லது யூடியூப் / இன்ஸ்டாகிராம் / ஃபேஸ்புக் இணைப்பை சொத்து டைரிக்கு பகிருங்கள்.")
    val noMatches = Tr("No matching properties", "பொருந்தும் சொத்துகள் இல்லை")
    val noMatchesBody = Tr("Try a different search or clear the filters.", "வேறு தேடலை முயற்சிக்கவும் அல்லது வடிகட்டிகளை அழிக்கவும்.")
    val priceOnRequest = Tr("Price not set", "விலை குறிப்பிடவில்லை")
    val centsShort = Tr("cents", "சென்ட்")
    val sqftShort = Tr("sq ft", "சதுர அடி")
    val perCentSlash = Tr("/cent", "/சென்ட்")
    val perSqftSlash = Tr("/sq ft", "/ச.அடி")

    // Property detail
    val propertyDetails = Tr("Property details", "சொத்து விவரங்கள்")
    val sizeLabel = Tr("Size", "பரப்பளவு")
    val addedUpdated = Tr("Added {0} • Updated {1}", "சேர்த்தது {0} • புதுப்பித்தது {1}")
    val broker = Tr("Broker", "தரகர்")
    val noBrokerLinked = Tr("No broker linked to this property.", "இந்தச் சொத்துக்கு தரகர் இணைக்கப்படவில்லை.")
    val linkBroker = Tr("Link a broker", "தரகரை இணை")
    val waAbout = Tr("Hello, I am interested in: {0}", "வணக்கம், இந்தச் சொத்து பற்றி கேட்க விரும்புகிறேன்: {0}")
    val source = Tr("Source", "மூலம்")
    val documents = Tr("Documents checklist", "ஆவணச் சரிபார்ப்பு")
    val docsCount = Tr("{0}/{1}", "{0}/{1}")
    val keyDoc = Tr("Key – missing", "முக்கியம் – இல்லை")
    val location = Tr("Location", "இருப்பிடம்")
    val coordinates = Tr("Coordinates", "அட்ச/தீர்க்க ரேகை")
    val openMap = Tr("Open in Maps", "வரைபடத்தில் திற")
    val notes = Tr("Notes", "குறிப்புகள்")
    val timeline = Tr("Calls & visits timeline", "அழைப்பு & பார்வை வரலாறு")
    val noInteractions = Tr("No calls or visits logged yet.", "இன்னும் அழைப்பு / பார்வை பதிவு இல்லை.")
    val logInteraction = Tr("Log call", "அழைப்பைப் பதிவு செய்")
    val deletePropertyQ = Tr("Delete this property?", "இந்தச் சொத்தை நீக்கவா?")
    val deletePropertyBody = Tr("The property and its call log will be removed.", "சொத்தும் அதன் அழைப்புப் பதிவுகளும் நீக்கப்படும்.")
    val withBroker = Tr("with {0}", "{0} உடன்")
    val quoted = Tr("Quoted {0}", "சொன்ன விலை {0}")
    val quoteChanged = Tr("Quoted {0} on {1}, now {2}", "{1} அன்று {0} சொன்னார், இப்போது {2}")
    val promisesLabel = Tr("Promised", "வாக்குறுதி")
    val followUpOn = Tr("Follow up on {0}", "{0} அன்று பின்தொடர்")

    // Price check
    val priceCheck = Tr("Price check", "விலைச் சரிபார்ப்பு")
    val thisProperty = Tr("This property ({0})", "இந்தச் சொத்து ({0})")
    val guidelineValue = Tr("Guideline value ({0})", "வழிகாட்டி மதிப்பு ({0})")
    val marketMedian = Tr("Market median ({0})", "சந்தை நடுநிலை விலை ({0})")
    val comparables = Tr("Comparable listings", "ஒப்பிடக்கூடிய சொத்துகள்")
    val nListings = Tr("{0} nearby, same type", "{0} அருகில், அதே வகை")
    val vsGuideline = Tr("vs guideline value", "வழிகாட்டி மதிப்புடன் ஒப்பீடு")
    val vsMarket = Tr("vs market median", "சந்தை விலையுடன் ஒப்பீடு")
    val notSet = Tr("Not set", "அமைக்கவில்லை")
    val notEnough = Tr("Not enough data", "போதுமான தகவல் இல்லை")
    val explainNoPrice = Tr("Add the asking price and size to check the price.", "விலையைச் சரிபார்க்க கேட்கும் விலையும் பரப்பளவும் சேர்க்கவும்.")
    val explainBelowGuideline = Tr(
        "The asking price is BELOW the government guideline value. Genuine sellers rarely sell below guideline value – check documents carefully and never pay an advance before verifying.",
        "கேட்கும் விலை அரசு வழிகாட்டி மதிப்பை விடக் குறைவு. உண்மையான விற்பனையாளர்கள் வழிகாட்டி மதிப்புக்குக் கீழே விற்பது அரிது – ஆவணங்களைக் கவனமாகச் சரிபாருங்கள், சரிபார்க்கும் முன் முன்பணம் கொடுக்காதீர்கள்.",
    )
    val explainNoMarket = Tr(
        "Only {0} comparable listing(s) of the same type nearby – add at least 2 to compare with the market. Guideline value is shown if set.",
        "அருகில் அதே வகையில் {0} சொத்து(கள்) மட்டுமே உள்ளன – சந்தையுடன் ஒப்பிட குறைந்தது 2 தேவை. வழிகாட்டி மதிப்பு இருந்தால் காட்டப்படும்.",
    )
    val explainSuspicious = Tr(
        "Much cheaper than similar listings nearby (less than 70% of the market median). This can be a false promise or a problem property.",
        "அருகிலுள்ள ஒத்த சொத்துகளை விட மிகவும் மலிவு (சந்தை நடுநிலை விலையின் 70%-க்கும் குறைவு). இது பொய் வாக்குறுதியாகவோ சிக்கலான சொத்தாகவோ இருக்கலாம்.",
    )
    val explainGood = Tr("Below the market median but not suspiciously so. Verify documents and negotiate.", "சந்தை விலையை விடக் குறைவு, ஆனால் சந்தேகத்துக்குரிய அளவில் இல்லை. ஆவணங்களைச் சரிபார்த்து பேரம் பேசுங்கள்.")
    val explainFair = Tr("Within ±15% of the market median for similar listings nearby.", "அருகிலுள்ள ஒத்த சொத்துகளின் சந்தை விலையிலிருந்து ±15%-க்குள் உள்ளது.")
    val explainHigh = Tr("15–40% above the market median. There is room to negotiate.", "சந்தை விலையை விட 15–40% அதிகம். பேரம் பேச இடம் உண்டு.")
    val explainTooHigh = Tr("More than 40% above the market median.", "சந்தை விலையை விட 40%-க்கும் மேல் அதிகம்.")
    val quotedHistory = Tr("Quoted price history", "சொன்ன விலை வரலாறு")
    val quotedThenNow = Tr("Quoted {0} on {1}, now {2} ({3})", "{1} அன்று {0} சொன்னார், இப்போது {2} ({3})")
    val guidelineHint = Tr("Official guideline values for Tamil Nadu are published at tnreginet.gov.in.", "தமிழ்நாட்டின் அதிகாரப்பூர்வ வழிகாட்டி மதிப்புகள் tnreginet.gov.in தளத்தில் உள்ளன.")
    val openTnReginet = Tr("Open tnreginet.gov.in", "tnreginet.gov.in திற")

    // Worth it
    val worthIt = Tr("Worth it?", "வாங்கலாமா?")
    val suggestionOnly = Tr("A suggestion from your data – you decide.", "உங்கள் தகவலின் அடிப்படையில் ஒரு பரிந்துரை – முடிவு உங்களுடையது.")
    val rDropped = Tr("You marked this property as dropped.", "இந்தச் சொத்தை கைவிட்டதாகக் குறித்துள்ளீர்கள்.")
    val rBought = Tr("Already bought.", "ஏற்கனவே வாங்கப்பட்டது.")
    val rSuspicious = Tr("Price looks suspiciously low – possible false promise.", "விலை சந்தேகமாகக் குறைவாக உள்ளது – பொய் வாக்குறுதியாக இருக்கலாம்.")
    val rGood = Tr("Price is below the market median ({0}).", "விலை சந்தை விலையை விடக் குறைவு ({0}).")
    val rFair = Tr("Price is fair compared to the market ({0}).", "சந்தையுடன் ஒப்பிடுகையில் விலை நியாயமானது ({0}).")
    val rHigh = Tr("Price is high ({0} vs market) – negotiate.", "விலை அதிகம் (சந்தையை விட {0}) – பேரம் பேசுங்கள்.")
    val rTooHigh = Tr("Price is far above the market ({0}).", "விலை சந்தையை விட மிக அதிகம் ({0}).")
    val rUnknown = Tr("Not enough nearby listings to judge the price.", "விலையை மதிப்பிட அருகில் போதுமான சொத்துகள் இல்லை.")
    val rDocsMissing = Tr("Key documents not yet verified: {0}.", "முக்கிய ஆவணங்கள் இன்னும் சரிபார்க்கப்படவில்லை: {0}.")
    val rDocsOk = Tr("All key documents are ticked.", "முக்கிய ஆவணங்கள் அனைத்தும் சரிபார்க்கப்பட்டன.")
    val rRaised = Tr("The broker raised the price since the first quote ({0}).", "முதல் விலையிலிருந்து தரகர் விலையை உயர்த்தியுள்ளார் ({0}).")
    val rReduced = Tr("Price has come down since the first quote ({0}).", "முதல் விலையிலிருந்து விலை குறைந்துள்ளது ({0}).")
    val rBrokerFlagged = Tr("This broker has {0} other listing(s) with suspiciously low prices.", "இந்தத் தரகரின் வேறு {0} சொத்து(கள்) சந்தேகமான குறைந்த விலையில் உள்ளன.")
    val rOverdue = Tr("{0} follow-up(s) overdue – promises not yet kept?", "{0} பின்தொடர்வு(கள்) தாமதம் – வாக்குறுதிகள் நிறைவேறவில்லையா?")

    // Property edit
    val newProperty = Tr("New property", "புதிய சொத்து")
    val editProperty = Tr("Edit property", "சொத்தைத் திருத்து")
    val sharedLinkAdded = Tr("Link added from share. Fill in the details you know – you can update later.", "பகிர்ந்த இணைப்பு சேர்க்கப்பட்டது. தெரிந்த விவரங்களை நிரப்புங்கள் – பின்னர் மாற்றலாம்.")
    val basics = Tr("Basics", "அடிப்படை விவரங்கள்")
    val title = Tr("Title", "தலைப்பு")
    val titleHint = Tr("e.g. 5 cent plot near Vilangudi bypass", "எ.கா. விளாங்குடி பைபாஸ் அருகே 5 சென்ட் மனை")
    val sizeAndPrice = Tr("Size & price", "பரப்பளவு & விலை")
    val sizeConverted = Tr("= {0} cents • {1} acres • {2} sq ft", "= {0} சென்ட் • {1} ஏக்கர் • {2} சதுர அடி")
    val askingPrice = Tr("Asking price (₹)", "கேட்கும் விலை (₹)")
    val locality = Tr("Area / locality", "பகுதி / ஊர்")
    val localityHint = Tr("Used to compare prices, e.g. Vilangudi", "விலை ஒப்பீட்டுக்குப் பயன்படும், எ.கா. விளாங்குடி")
    val taluk = Tr("Taluk", "வட்டம்")
    val district = Tr("District", "மாவட்டம்")
    val latitude = Tr("Latitude (optional)", "அட்சரேகை (விருப்பம்)")
    val longitude = Tr("Longitude (optional)", "தீர்க்கரேகை (விருப்பம்)")
    val coordsHint = Tr("With coordinates, listings within 5 km are compared even if the area name differs.", "ரேகைகள் இருந்தால், பகுதிப் பெயர் வேறாக இருந்தாலும் 5 கி.மீ.க்குள் உள்ள சொத்துகள் ஒப்பிடப்படும்.")
    val sourceLink = Tr("Source link (YouTube / Instagram / Facebook)", "மூல இணைப்பு (யூடியூப் / இன்ஸ்டாகிராம் / ஃபேஸ்புக்)")
    val detected = Tr("Detected", "கண்டறிந்தது")
    val caption = Tr("Caption / description", "விளக்கம்")
    val selectBroker = Tr("Select broker", "தரகரைத் தேர்ந்தெடு")
    val noBroker = Tr("No broker", "தரகர் இல்லை")
    val addNewBroker = Tr("+ Add new broker", "+ புதிய தரகர் சேர்")

    // Brokers
    val brokers = Tr("Brokers", "தரகர்கள்")
    val nBrokers = Tr("{0} brokers", "{0} தரகர்கள்")
    val addBroker = Tr("Add broker", "தரகர் சேர்")
    val editBroker = Tr("Edit broker", "தரகரைத் திருத்து")
    val brokerDetails = Tr("Broker details", "தரகர் விவரங்கள்")
    val noBrokersYet = Tr("No brokers yet", "இன்னும் தரகர்கள் இல்லை")
    val noBrokersBody = Tr("Add the brokers you talk to and keep their call history in one place.", "நீங்கள் பேசும் தரகர்களைச் சேர்த்து, அவர்களின் அழைப்பு வரலாற்றை ஒரே இடத்தில் வைத்திருங்கள்.")
    val nPropertiesShort = Tr("{0} properties", "{0} சொத்துகள்")
    val lastContact = Tr("last contact {0}", "கடைசி தொடர்பு {0}")
    val propertiesFromBroker = Tr("Properties from this broker ({0})", "இந்தத் தரகரின் சொத்துகள் ({0})")
    val noPropertiesForBroker = Tr("No properties linked yet.", "இன்னும் சொத்துகள் இணைக்கப்படவில்லை.")
    val callHistory = Tr("Call history", "அழைப்பு வரலாறு")
    val deleteBrokerQ = Tr("Delete this broker?", "இந்தத் தரகரை நீக்கவா?")
    val deleteBrokerBody = Tr("Properties and calls stay, but will no longer be linked to this broker.", "சொத்துகளும் அழைப்புகளும் இருக்கும், ஆனால் இந்தத் தரகருடன் இணைக்கப்படாது.")
    val name = Tr("Name", "பெயர்")
    val phone = Tr("Phone", "தொலைபேசி")
    val whatsappNumber = Tr("WhatsApp number", "வாட்ஸ்அப் எண்")
    val whatsappHint = Tr("Leave empty if same as phone", "தொலைபேசி எண்ணே என்றால் காலியாக விடுங்கள்")
    val brokerArea = Tr("Area they work in", "அவர் வேலை செய்யும் பகுதி")
    val platform = Tr("Found on", "எங்கே கண்டது")

    // Interaction
    val editInteraction = Tr("Edit call / visit", "அழைப்பு / பார்வையைத் திருத்து")
    val whoAndWhat = Tr("Who & what", "யார் & என்ன")
    val property = Tr("Property", "சொத்து")
    val noProperty = Tr("No property", "சொத்து இல்லை")
    val date = Tr("Date", "தேதி")
    val priceAndTalk = Tr("Price & discussion", "விலை & பேச்சு")
    val quotedPrice = Tr("Price quoted this time (₹)", "இந்த முறை சொன்ன விலை (₹)")
    val currentAsking = Tr("Current asking price: {0}", "தற்போதைய கேட்கும் விலை: {0}")
    val updateAsking = Tr("Update the property's asking price to this quote", "சொத்தின் கேட்கும் விலையை இந்த விலைக்கு மாற்று")
    val summary = Tr("Summary of discussion", "பேச்சின் சுருக்கம்")
    val promises = Tr("Promises made", "கொடுத்த வாக்குறுதிகள்")
    val promisesHint = Tr("e.g. will send EC copy by Sunday", "எ.கா. ஞாயிறுக்குள் EC நகல் அனுப்புவதாகச் சொன்னார்")
    val nextFollowUp = Tr("Next follow-up", "அடுத்த பின்தொடர்வு")
    val followUpDate = Tr("Follow-up date", "பின்தொடர்வு தேதி")
    val tomorrow = Tr("Tomorrow", "நாளை")
    val in3Days = Tr("In 3 days", "3 நாளில்")
    val in1Week = Tr("In 1 week", "1 வாரத்தில்")
    val noFollowUp = Tr("No follow-up", "பின்தொடர்வு இல்லை")
    val deleteInteractionQ = Tr("Delete this entry?", "இந்தப் பதிவை நீக்கவா?")

    // Follow-ups
    val followUps = Tr("Follow-ups", "பின்தொடர்வுகள்")
    val followUpSummary = Tr("{0} overdue • {1} today • {2} upcoming", "{0} தாமதம் • {1} இன்று • {2} வரவிருப்பவை")
    val overdue = Tr("Overdue", "தாமதமானவை")
    val today = Tr("Today", "இன்று")
    val upcoming = Tr("Upcoming", "வரவிருப்பவை")
    val daysOverdue = Tr("{0} day(s) overdue", "{0} நாள் தாமதம்")
    val inNDays = Tr("In {0} days", "{0} நாளில்")
    val noFollowUps = Tr("No pending follow-ups", "நிலுவையில் பின்தொடர்வுகள் இல்லை")
    val noFollowUpsBody = Tr("When you log a call, set a follow-up date and it will show here.", "அழைப்பைப் பதிவு செய்யும்போது பின்தொடர்வு தேதி அமைத்தால் இங்கே தெரியும்.")

    // Guideline values
    val guidelineValues = Tr("Guideline values", "வழிகாட்டி மதிப்புகள்")
    val addGuideline = Tr("Add value", "மதிப்பு சேர்")
    val editGuideline = Tr("Edit guideline value", "வழிகாட்டி மதிப்பைத் திருத்து")
    val whatIsGuideline = Tr("What is the guideline value?", "வழிகாட்டி மதிப்பு என்றால் என்ன?")
    val guidelineExplain = Tr(
        "The guideline value is the minimum value fixed by the Tamil Nadu Registration Department for each area. A price below it is a red flag. Look up the official value for the survey number / street at tnreginet.gov.in and enter it here per cent or per sq ft.",
        "வழிகாட்டி மதிப்பு என்பது ஒவ்வொரு பகுதிக்கும் தமிழ்நாடு பதிவுத் துறை நிர்ணயிக்கும் குறைந்தபட்ச மதிப்பு. அதற்குக் கீழே உள்ள விலை எச்சரிக்கை அறிகுறி. சர்வே எண் / தெருவுக்கான அதிகாரப்பூர்வ மதிப்பை tnreginet.gov.in தளத்தில் பார்த்து, சென்ட் அல்லது சதுர அடிக்கு இங்கே பதியுங்கள்.",
    )
    val noGuidelines = Tr("No guideline values yet.", "இன்னும் வழிகாட்டி மதிப்புகள் இல்லை.")
    val perCentLabel = Tr("Per cent", "ஒரு சென்ட்")
    val perSqftLabel = Tr("Per sq ft", "ஒரு சதுர அடி")
    val perCentRupees = Tr("Value per cent (₹)", "ஒரு சென்ட் மதிப்பு (₹)")
    val perSqftRupees = Tr("Value per sq ft (₹)", "ஒரு சதுர அடி மதிப்பு (₹)")
    val guidelineLocHint = Tr("Must match the property's area name, e.g. Vilangudi", "சொத்தின் பகுதிப் பெயருடன் பொருந்த வேண்டும், எ.கா. Vilangudi")
    val usedByN = Tr("Matches {0} properties", "{0} சொத்துகளுடன் பொருந்துகிறது")

    // Settings
    val settings = Tr("Settings", "அமைப்புகள்")
    val language = Tr("Language", "மொழி")
    val yourData = Tr("Your data", "உங்கள் தகவல்")
    val dataCounts = Tr("{0} properties • {1} brokers • {2} calls/visits • {3} guideline values", "{0} சொத்துகள் • {1} தரகர்கள் • {2} அழைப்பு/பார்வைகள் • {3} வழிகாட்டி மதிப்புகள்")
    val dataLocal = Tr("Everything is saved automatically on this device only.", "அனைத்தும் இந்தச் சாதனத்தில் மட்டும் தானாகச் சேமிக்கப்படுகிறது.")
    val exportJson = Tr("Export", "ஏற்றுமதி")
    val importJson = Tr("Import", "இறக்குமதி")
    val importQ = Tr("Replace all data?", "எல்லா தகவலையும் மாற்றவா?")
    val importBody = Tr("Importing a backup replaces everything currently in the app.", "காப்புப் பிரதியை இறக்குமதி செய்தால் தற்போதைய தகவல் அனைத்தும் மாற்றப்படும்.")
    val importDone = Tr("Backup imported", "காப்புப் பிரதி இறக்குமதி ஆனது")
    val importFailed = Tr("That file is not a Sothu Diary backup", "இது சொத்து டைரி காப்புப் பிரதி அல்ல")
    val sampleData = Tr("Sample data", "மாதிரித் தகவல்")
    val sampleDataBody = Tr(
        "The app starts with clearly labelled SAMPLE properties around Madurai so you can see how the price check works. Clear them when you start adding your own.",
        "விலைச் சரிபார்ப்பு எப்படி வேலை செய்கிறது என்று காட்ட, மதுரையைச் சுற்றி “மாதிரி” என்று குறிக்கப்பட்ட சொத்துகளுடன் ஆப் தொடங்குகிறது. உங்கள் சொத்துகளைச் சேர்க்கத் தொடங்கும்போது அவற்றை அழித்துவிடுங்கள்.",
    )
    val clearSamples = Tr("Clear sample data", "மாதிரித் தகவலை அழி")
    val loadSamples = Tr("Load sample data", "மாதிரித் தகவலை ஏற்று")
    val clearSamplesQ = Tr("Clear sample data?", "மாதிரித் தகவலை அழிக்கவா?")
    val clearSamplesBody = Tr("Only items marked Sample are removed. Your own entries stay.", "“மாதிரி” என்று குறிக்கப்பட்டவை மட்டும் நீக்கப்படும். உங்கள் பதிவுகள் அப்படியே இருக்கும்.")
    val samplesCleared = Tr("Sample data cleared", "மாதிரித் தகவல் அழிக்கப்பட்டது")
    val howPriceCheckWorks = Tr("How the price check works", "விலைச் சரிபார்ப்பு எப்படி?")
    val priceRulesText = Tr(
        "Land is compared per cent, houses and apartments per sq ft. Each property is compared with the guideline value for its area and with the median of other listings of the same type in the same area (or within 5 km if coordinates are set).\n• Below guideline value or under 70% of market → Suspiciously low\n• 70–85% → Good price\n• Within ±15% → Fair\n• 15–40% above → High\n• Over 40% above → Too high",
        "நிலம் சென்ட் கணக்கிலும், வீடு / அடுக்குமாடி சதுர அடி கணக்கிலும் ஒப்பிடப்படும். ஒவ்வொரு சொத்தும் அதன் பகுதியின் வழிகாட்டி மதிப்புடனும், அதே பகுதியில் (ரேகைகள் இருந்தால் 5 கி.மீ.க்குள்) உள்ள அதே வகைச் சொத்துகளின் நடுநிலை விலையுடனும் ஒப்பிடப்படும்.\n• வழிகாட்டி மதிப்புக்குக் கீழ் அல்லது சந்தையின் 70%-க்குக் கீழ் → சந்தேகமான குறைந்த விலை\n• 70–85% → நல்ல விலை\n• ±15%-க்குள் → நியாயமான விலை\n• 15–40% அதிகம் → அதிக விலை\n• 40%-க்கு மேல் → மிக அதிக விலை",
    )
    val about = Tr("About", "பற்றி")
    val aboutBody = Tr(
        "A personal diary for tracking land, house and plot deals: leads from YouTube / Instagram / Facebook, brokers, calls, promises, documents and a price sanity check.",
        "நிலம், வீடு, மனை பேரங்களைக் கண்காணிக்கும் தனிப்பட்ட டைரி: யூடியூப் / இன்ஸ்டாகிராம் / ஃபேஸ்புக் விளம்பரங்கள், தரகர்கள், அழைப்புகள், வாக்குறுதிகள், ஆவணங்கள், விலைச் சரிபார்ப்பு.",
    )
    val disclaimer = Tr(
        "Verdicts are only as good as the data you enter. Always verify documents with a lawyer and the sub-registrar office before paying any advance.",
        "நீங்கள் பதியும் தகவலைப் பொறுத்தே மதிப்பீடுகள் அமையும். முன்பணம் கொடுக்கும் முன் வழக்கறிஞர் மற்றும் சார்பதிவாளர் அலுவலகம் மூலம் ஆவணங்களைச் சரிபாருங்கள்.",
    )
}
