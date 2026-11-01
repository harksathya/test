# குறள் கதைகள் — Kural Kathaigal

A native Android app (Kotlin + Jetpack Compose) that tells the **first 10 kurals of Thirukkural**
(அதிகாரம் 1 — கடவுள் வாழ்த்து) as short illustrated stories in Tamil.
The UI follows the *Read.io – Mobile Book Reading App* design on Dribbble.

## Screens
- **முகப்பு (Home)** – search, category tabs, featured "continue reading" banner, popular carousel, full list of 10 kurals
- **விவரம் (Detail)** – cover, tags, the kural with transliteration, meaning, synopsis, *படிக்கத் தொடங்கு* button
- **வாசிப்பு (Reader)** – the kural, the story with an illustration, meaning and moral, prev/next, bookmark, font size, page `n / 10`
- **சேமித்தவை (Saved)** – bookmarked stories (stored on device)

## Build
- Android Studio (Koala or newer) → *Open* this folder → Run, or
- `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- Every push to `main` also builds the APK in **GitHub Actions** (Actions tab → latest run → *KuralKathaigal-debug-apk*).

Requirements: JDK 17, Android SDK 34. minSdk 24.

## Structure
```
app/src/main/java/com/hark/kuralkathaigal/
  MainActivity.kt          navigation + bottom bar
  data/Kurals.kt           the 10 kurals, meanings and stories
  data/Library.kt          bookmarks, last read, font size (SharedPreferences)
  ui/theme/Theme.kt        Read.io palette (coral #FF5A36 on soft grey)
  ui/components/           cover art, chips, round icon buttons
  ui/screens/              Home, Detail, Reader, Saved, About
```

Kural text: Project Madurai Unicode edition.
