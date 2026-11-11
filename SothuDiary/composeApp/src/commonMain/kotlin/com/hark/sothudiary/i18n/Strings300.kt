package com.hark.sothudiary.i18n

/** 3.0.0 strings: Google Drive backup / sync. */
object S30 {
    val title = Tr("Google sign-in & Drive backup", "Google உள்நுழைவு & Drive காப்புப் பிரதி")
    val body = Tr(
        "Keeps a copy of all properties, photos, voice notes and documents in your own Google Drive (a hidden app folder only this app can see). Sign in with the same Gmail on a new phone to get everything back.",
        "எல்லா சொத்துகள், படங்கள், குரல் குறிப்புகள், ஆவணங்களின் நகல் உங்கள் சொந்த Google Drive-இல் (இந்த ஆப் மட்டும் பார்க்கும் மறைந்த கோப்புறை) வைக்கப்படும். புதிய போனில் அதே Gmail-இல் உள்நுழைந்தால் அனைத்தும் திரும்ப வரும்.",
    )
    val testingNote = Tr(
        "While the app is in Google's testing mode, only the test Gmail (harksathya@gmail.com) can connect.",
        "ஆப் Google சோதனை நிலையில் இருக்கும் வரை, சோதனை Gmail (harksathya@gmail.com) மட்டுமே இணைக்க முடியும்.",
    )
    val desktop = Tr("Google Drive backup works in the Android app.", "Google Drive காப்புப் பிரதி Android ஆப்பில் மட்டும்.")
    val connect = Tr("Sign in with Google", "Google கணக்கில் உள்நுழை")
    val account = Tr("Account: {0}", "கணக்கு: {0}")
    val lastBackup = Tr("Last backup: {0}", "கடைசி காப்புப் பிரதி: {0}")
    val never = Tr("Last backup: not yet", "கடைசி காப்புப் பிரதி: இன்னும் இல்லை")
    val backupNow = Tr("Back up now", "இப்போது காப்புப் பிரதி எடு")
    val restore = Tr("Restore from Drive", "Drive-இலிருந்து மீட்டெடு")
    val disconnect = Tr("Disconnect", "துண்டி")
    val auto = Tr("Back up automatically (30 s after a change)", "தானாக காப்புப் பிரதி (மாற்றத்திற்கு 30 வி. பின்)")
    val signingIn = Tr("Signing in to Google…", "Google-இல் உள்நுழைகிறது…")
    val connected = Tr("Connected: {0}. No backup on Drive yet.", "இணைந்தது: {0}. Drive-இல் இன்னும் காப்புப் பிரதி இல்லை.")
    val foundBackup = Tr("Backup found on Drive: {0} properties.", "Drive-இல் காப்புப் பிரதி உள்ளது: {0} சொத்துகள்.")
    val backingUp = Tr("Backing up…", "காப்புப் பிரதி எடுக்கிறது…")
    val uploading = Tr("Uploading file {0} of {1}…", "கோப்பு {0}/{1} பதிவேற்றுகிறது…")
    val downloading = Tr("Downloading file {0} of {1}…", "கோப்பு {0}/{1} பதிவிறக்குகிறது…")
    val backedUp = Tr("Backed up ({0} new files).", "காப்புப் பிரதி முடிந்தது ({0} புதிய கோப்புகள்).")
    val chooseFirst = Tr(
        "Drive already has a backup. Tap \"Restore from Drive\" and choose what to keep first.",
        "Drive-இல் ஏற்கனவே காப்புப் பிரதி உள்ளது. முதலில் \"Drive-இலிருந்து மீட்டெடு\" அழுத்தி எதை வைப்பது என்று தேர்வு செய்யவும்.",
    )
    val needsSignIn = Tr("Google needs you to sign in again – tap Back up now.", "Google மீண்டும் உள்நுழையக் கேட்கிறது – இப்போது காப்புப் பிரதி எடு அழுத்தவும்.")
    val cancelled = Tr("Sign-in cancelled.", "உள்நுழைவு ரத்து செய்யப்பட்டது.")
    val netError = Tr("No internet – Google could not be reached.", "இணையம் இல்லை – Google-ஐ அணுக முடியவில்லை.")
    val authFailed = Tr("Google sign-in failed: {0}", "Google உள்நுழைவு தோல்வி: {0}")
    val devError = Tr(
        "Google refused this app (DEVELOPER_ERROR, code 10). In Google Cloud console check, in ONE project: the Android OAuth client has package com.hark.sothudiary and SHA-1 A0:3D:32:E0:1C:F5:4B:B0:60:EA:2D:43:C4:F9:CD:60:C9:74:29:0D exactly; Google Drive API is enabled; the OAuth consent screen lists harksathya@gmail.com as a test user and you picked that Gmail. Changes can take 5–30 minutes to work.",
        "Google இந்த ஆப்பை மறுத்தது (DEVELOPER_ERROR, குறியீடு 10). Google Cloud console-இல் ஒரே project-இல் சரிபார்க்கவும்: Android OAuth client-இல் package com.hark.sothudiary, SHA-1 A0:3D:32:E0:1C:F5:4B:B0:60:EA:2D:43:C4:F9:CD:60:C9:74:29:0D சரியாக உள்ளதா; Google Drive API இயக்கப்பட்டுள்ளதா; OAuth consent screen-இல் harksathya@gmail.com சோதனைப் பயனராக உள்ளதா, அந்த Gmail-ஐயே தேர்ந்தெடுத்தீர்களா. மாற்றங்கள் வேலை செய்ய 5–30 நிமிடம் ஆகலாம்.",
    )
    val forbidden = Tr(
        "Drive refused (403): {0}. Check that Google Drive API is enabled in the same Cloud project.",
        "Drive மறுத்தது (403): {0}. அதே Cloud project-இல் Google Drive API இயக்கப்பட்டுள்ளதா எனப் பார்க்கவும்.",
    )
    val failed = Tr("Drive backup failed: {0}", "Drive காப்புப் பிரதி தோல்வி: {0}")
    val chooseTitle = Tr("Phone and Drive both have data", "போனிலும் Drive-இலும் தரவு உள்ளது")
    val chooseBody = Tr(
        "Phone: {0} properties. Drive backup: {1} properties. What should be kept?",
        "போன்: {0} சொத்துகள். Drive காப்புப் பிரதி: {1} சொத்துகள். எதை வைக்க வேண்டும்?",
    )
    val merge = Tr("Merge both (newest wins)", "இரண்டையும் சேர் (புதியது வெல்லும்)")
    val useDrive = Tr("Use Drive (replace phone)", "Drive-ஐப் பயன்படுத்து (போனை மாற்று)")
    val keepPhone = Tr("Keep phone (replace Drive)", "போனை வை (Drive-ஐ மாற்று)")
    val restoreTitle = Tr("Restore from Drive?", "Drive-இலிருந்து மீட்டெடுக்கவா?")
    val restoreBody = Tr(
        "The Drive backup has {0} properties. Photos, voice notes and documents are downloaded too.",
        "Drive காப்புப் பிரதியில் {0} சொத்துகள் உள்ளன. படங்கள், குரல் குறிப்புகள், ஆவணங்களும் பதிவிறக்கப்படும்.",
    )
    val restoreDone = Tr("Restored from Drive.", "Drive-இலிருந்து மீட்டெடுக்கப்பட்டது.")
    val noBackup = Tr("No backup on Drive yet.", "Drive-இல் இன்னும் காப்புப் பிரதி இல்லை.")
}
