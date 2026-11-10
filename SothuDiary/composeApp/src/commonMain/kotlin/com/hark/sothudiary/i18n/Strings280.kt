package com.hark.sothudiary.i18n

/** 2.8.0 strings: documents (EC, patta, chitta, adangal, sale deed) and the EC / ownership check. */
object S8 {
    val docsTitle = Tr("Documents & EC check", "ஆவணங்கள் & EC சோதனை")
    val docsTile = Tr("Documents & EC check", "ஆவணங்கள் & EC சோதனை")
    val docsNone = Tr("No documents yet – add the EC, patta, chitta, adangal or sale deed.", "இன்னும் ஆவணம் இல்லை – EC, பட்டா, சிட்டா, அடங்கல் அல்லது கிரயப் பத்திரம் சேர்க்கவும்.")
    val docsCount = Tr("{0} documents · {1}", "{0} ஆவணங்கள் · {1}")
    val notAnalysed = Tr("not checked yet", "இன்னும் சோதிக்கவில்லை")
    val flagsCount = Tr("{0} high, {1} medium flags", "{0} அதிக, {1} நடுத்தர எச்சரிக்கைகள்")
    val noFlags = Tr("no red flags found", "எச்சரிக்கை எதுவும் இல்லை")
    val addDoc = Tr("Add document (photo / PDF)", "ஆவணம் சேர் (புகைப்படம் / PDF)")
    val pickKind = Tr("Which document is it?", "இது எந்த ஆவணம்?")
    val docsHint = Tr(
        "Add every page of the EC (all pages in one PDF, or one photo per page), plus patta, chitta, adangal and the sale deed. Fill the survey and patta number in Land details first – the chain is traced for that land.",
        "EC-இன் எல்லாப் பக்கங்களையும் (ஒரே PDF, அல்லது பக்கத்துக்கு ஒரு புகைப்படம்), பட்டா, சிட்டா, அடங்கல், கிரயப் பத்திரம் ஆகியவற்றைச் சேர்க்கவும். முதலில் நில விவரத்தில் புல எண், பட்டா எண் நிரப்புங்கள் – அந்த நிலத்துக்கே சங்கிலி கண்டறியப்படும்."
    )
    val analyse = Tr("Check documents (AI · {0})", "ஆவணங்களைச் சோதி (AI · {0})")
    val analysing = Tr("Reading the documents… (1–2 minutes; it keeps going if you leave this screen)", "ஆவணங்களைப் படிக்கிறது… (1–2 நிமிடம்; திரையை விட்டுச் சென்றாலும் தொடரும்)")
    val needKey = Tr(
        "Add an AI key in Settings (Gemini's free key reads Tamil documents and PDFs well) to read the documents.",
        "ஆவணங்களைப் படிக்க அமைப்புகளில் AI key சேர்க்கவும் (Gemini இலவச key தமிழ் ஆவணங்களையும் PDF-களையும் நன்றாகப் படிக்கும்)."
    )
    val privacy = Tr(
        "The files are sent only to your own AI provider ({0}) when you tap Check. Nothing is sent anywhere else.",
        "சோதி அழுத்தும்போது மட்டும் கோப்புகள் உங்கள் சொந்த AI சேவைக்கு ({0}) அனுப்பப்படும். வேறு எங்கும் அனுப்பப்படாது."
    )
    val failed = Tr("Could not check: {0}", "சோதிக்க முடியவில்லை: {0}")
    val changedSince = Tr("Documents changed since the last check – tap Check again.", "கடைசிச் சோதனைக்குப் பின் ஆவணங்கள் மாறியுள்ளன – மீண்டும் சோதி அழுத்துங்கள்.")
    val summary = Tr("Summary", "சுருக்கம்")
    val chainTitle = Tr("Ownership chain (oldest first)", "உரிமைச் சங்கிலி (பழையது முதலில்)")
    val chainEmpty = Tr("No sale / gift / settlement for this survey number was read from the EC.", "இந்தப் புல எண்ணுக்கு கிரயம் / தானம் / செட்டில்மெண்ட் எதுவும் EC-இலிருந்து படிக்கப்படவில்லை.")
    val flagsTitle = Tr("Red flags", "எச்சரிக்கைகள்")
    val entriesTitle = Tr("All EC entries ({0})", "எல்லா EC பதிவுகளும் ({0})")
    val docsReadTitle = Tr("Read from patta / chitta / adangal / deed", "பட்டா / சிட்டா / அடங்கல் / பத்திரத்திலிருந்து படித்தவை")
    val ecPeriod = Tr("EC period: {0} – {1}", "EC காலம்: {0} – {1}")
    val forLand = Tr("For survey {0} · patta {1}", "புல எண் {0} · பட்டா {1}")
    val docNo = Tr("Doc", "ஆவண எண்")
    val seller = Tr("From", "கொடுத்தவர்")
    val buyer = Tr("To", "பெற்றவர்")
    val value = Tr("Value", "மதிப்பு")
    val viaPower = Tr("via power agent", "பவர் முகவர் மூலம்")
    val levelHigh = Tr("High", "அதிகம்")
    val levelMedium = Tr("Medium", "நடுத்தரம்")
    val levelLow = Tr("Low", "குறைவு")
    val aiFlag = Tr("seen by AI", "AI கண்டது")
    val checkedBy = Tr("Checked {0} · {1} · {2}", "சோதித்தது {0} · {1} · {2}")
    val disclaimer = Tr(
        "A first reading by AI and fixed rules, not a legal opinion. Names and numbers can be misread – compare with the documents, and have a lawyer verify the title before you pay.",
        "AI மற்றும் நிலையான விதிகளின் முதல் வாசிப்பு, சட்டக் கருத்து அல்ல. பெயர்கள், எண்கள் தவறாகப் படிக்கப்படலாம் – ஆவணங்களுடன் ஒப்பிடுங்கள்; பணம் கொடுக்கும் முன் வழக்கறிஞரிடம் உரிமையைச் சரிபார்க்கச் சொல்லுங்கள்."
    )
    val delete = Tr("Remove", "நீக்கு")
    val open = Tr("Open", "திற")
    val landFirst = Tr("Survey / patta number not set in Land details – the chain will include every survey number in the EC.", "நில விவரத்தில் புல / பட்டா எண் இல்லை – EC-இல் உள்ள எல்லாப் புல எண்களும் சங்கிலியில் சேரும்.")
}
