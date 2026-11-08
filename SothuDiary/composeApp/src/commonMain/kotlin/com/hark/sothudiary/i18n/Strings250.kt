package com.hark.sothudiary.i18n

/** 2.5.0 strings: choose any LLM provider (issue #1). */
object S5 {
    val aiProvider = Tr("AI provider", "AI சேவை")
    val aiProviderBody = Tr(
        "Pick the AI service you have a key for. Any one is enough – a Gemini key is not required.",
        "உங்களிடம் key உள்ள AI சேவையைத் தேர்ந்தெடுங்கள். ஏதேனும் ஒன்று போதும் – Gemini key கட்டாயமில்லை."
    )
    val aiKey = Tr("{0} API key", "{0} API key")
    val aiKeyHint = Tr("Usually starts with “{0}”", "பொதுவாக “{0}” என்று தொடங்கும்")
    val getKeyAt = Tr("Get a key: {0}", "Key பெற: {0}")
    val freeTier = Tr("free tier", "இலவசம் உண்டு")
    val aiModel = Tr("Model", "மாடல்")
    val aiModelHint = Tr(
        "Auto picks from the models your key can use, as listed by {0}. Tap Choose to pick one yourself.",
        "தானியங்கி: உங்கள் key-க்கு {0} பட்டியலிடும் மாடல்களில் இருந்து தேர்வு செய்யும். நீங்களே தேர்ந்தெடுக்க “தேர்வு” அழுத்துங்கள்."
    )
    val chooseModel = Tr("Choose", "தேர்வு")
    val loadingModels = Tr("Loading models…", "மாடல்கள் ஏற்றப்படுகின்றன…")
    val noModels = Tr("Could not list models – check the key and the internet.", "மாடல்களைப் பட்டியலிட முடியவில்லை – key-யையும் இணையத்தையும் சரிபாருங்கள்.")
    val searchModels = Tr("Search models", "மாடல் தேடு")
    val aiKeyTitle = Tr("AI key (your own)", "AI key (உங்களுடையது)")
    val aiKeyBody = Tr(
        "The AI parts – the live price lookup and the land check’s web search – use the AI service you choose here, with your own key. Gemini can search Google itself; the other services read the app’s own free web search results. Every number must cite the page it came from.",
        "AI பகுதிகள் – நேரடி விலை தேடல், நில ஆய்வின் இணையத் தேடல் – நீங்கள் இங்கே தேர்ந்தெடுக்கும் AI சேவையை உங்கள் சொந்த key-யுடன் பயன்படுத்தும். Gemini தானே Google-இல் தேடும்; மற்ற சேவைகள் செயலியின் இலவச இணையத் தேடல் முடிவுகளைப் படிக்கும். ஒவ்வொரு எண்ணுக்கும் அது வந்த பக்கம் காட்டப்படும்."
    )
    val aiNeedKey = Tr("Add an AI key first (Settings).", "முதலில் AI key சேர்க்கவும் (அமைப்புகள்).")
    val inUse = Tr("In use: {0}", "பயன்பாட்டில்: {0}")
    val youtubeSave = Tr("Save YouTube key", "YouTube key சேமி")
}
