# Sothu Diary (சொத்து டைரி) – v2.9.1

A personal property-deal tracker for a buyer in Madurai, Tamil Nadu. Keep every lead you see on
YouTube / Instagram / Facebook / WhatsApp, the brokers behind them, every call and promise, voice notes,
the documents you have verified – and get a detailed, explainable **price check** against Madurai market
rates and a **Go / Hold / Drop** suggestion. Tamil and English throughout (one-tap **த / EN**).

Debug APK (latest build): https://github.com/harksathya/test/releases/download/sothu-diary-latest/SothuDiary.apk

**Agents:** read and keep [HANDOFF.md](HANDOFF.md) up to date (what is done, in progress, not working, yet to start).

## What's new in v3.0.0

- **Google Drive backup / sync (Settings → Google Drive backup):** "Connect Google Drive" signs in with Google
  (Google Identity Services, scope `drive.appdata` – a hidden app folder in your own Drive; no server, the access
  token is never saved). All data (`sothu_diary.json`) plus every photo, voice note, document and your own song are
  backed up; only new files are uploaded and files no longer used are removed from Drive. "Back up now", automatic
  backup 30 s after a change, "Last backup" time. On a new phone with the same Gmail the backup is restored at once;
  when both the phone and Drive have data you choose: merge (newest property wins), use Drive, or keep phone.
  Clear Tamil + English errors (e.g. DEVELOPER_ERROR 10 lists what to check in Google Cloud console).

## What's new in v2.9.1

- **EC chat on mobile data:** the first phone test lost the connection after 123 s ("Unable to resolve host
  generativelanguage.googleapis.com"). Now a network error is asked again after 4 s and 10 s; every 2 pages read are
  saved at once, so the next question continues from the last page instead of starting over; an "Ask again" button
  and a short hint appear after a failure. Page images are smaller (1400 px, JPEG 80) for faster upload.

## What's new in v2.9.0

- **EC chat (Ask about the EC):** on the Documents screen, ask questions like ChatGPT – a survey No., patta No., a
  name, a year, a document No., or "full analysis". Follow-up questions keep the last 10 messages; the chat is
  saved per property. Quick chips: trace the survey No., patta entries, full analysis, current owner, loans /
  court attachments, every survey No. in the EC.
- **Page-by-page reading:** a PDF is opened on the phone (Android PdfRenderer) and sent 2 pages per AI call; the
  text is saved, so later questions and "Check documents" are one fast text-only call (works with any provider).
  "Read the documents again" forgets the saved text.
- Deleting a property now deletes its document files too. The read text and chat are never shared.

## What's new in v2.8.1

- **Documents & EC check fix:** the first phone test showed only "no internet / no answer · gemini-flash-latest". The
  real reason was hidden (the call used `httpRequest`, which drops it). Now the screen shows it (timeout after N s,
  DNS, TLS …); one AI call may take 240 s (was 150 s); when the chosen model times out, is busy or out of quota, up
  to two other models from the key's list are tried, all within 6 minutes; the progress line counts the seconds.

## What's new in v2.8.0

- **Documents & EC check / ஆவணங்கள் & EC சோதனை** (property detail → Checks → *Documents & EC check*). Add the EC,
  patta, chitta, adangal and sale deed as photos or PDF (photos are scaled to 2400 px). *Check documents* sends them
  only to your own AI provider (Gemini, OpenAI, Claude, OpenRouter … – it must read images / PDF), which transcribes
  every EC entry and what each document says. The app then works out, with fixed rules (tested in `V280Test`):
  - the **ownership chain** for the survey number in Land details, oldest first: from → to, nature, year, document
    number, sale value;
  - **red flags** (High / Medium / Low, Tamil and English): break in the chain, mortgage without release, court order /
    attachment, cancelled deed, sale by power agent, open power of attorney, sale agreement with no sale after it,
    sale value drop, EC shorter than 30 years or older than 30 days, patta / chitta holder ≠ last buyer, patta or
    survey number ≠ Land details, government / poramboke / temple land, extent differing between documents – plus
    anything else the AI saw (marked *seen by AI*).
  - Results are saved with the property; the documents and the check are never included in Share / .sothu files.
  - A first reading, not a legal opinion: compare with the documents and have a lawyer verify the title.
- Code: `domain/docs/DocAnalysis.kt` (rules, prompt, parsing), `domain/docs/DocAi.kt` (image / PDF requests for each
  provider), `ui/screens/DocumentsScreen.kt`, `CheckRunner.docs`, `PlatformActions.pickDocument / readStoredFile`.

## What's new in v2.7.2

- **Fix (2.7.1 regression):** when overpass-api.de refused the app (HTTP 406, User-Agent policy), 2.7.1 stopped asking
  the other Overpass servers, so amenities, utilities, rail and protected areas came back *Unknown* (CI probe: map step
  18 s, EXTRA layers failed). Now only a rejected query (HTTP 400) stops the search; a 406 moves on to the next server.

## What's new in v2.7.1

- **Faster land check map data.** The 2.7.0 CI probe had every Overpass server fail (overpass-api.de 406, maps.mail.ru
  504, private.coffee timeout) while the OpenFreeMap tiles answered in under 1 s – yet the check waited 20 s before
  asking the tiles. Now the tiles are asked after 3 s, and a still-running Overpass query gets 4 s more (was 20 s + 5 s).
- **A rejected Overpass query (HTTP 400) is no longer retried** on every server (`overpassRetryable` is now used).
- **Two requests per Overpass server** (was one), so the water/roads/land-use and the amenities/rail groups no longer
  queue behind each other on the same server (overpass-api.de allows two slots per IP).
- **TNREGINET page help:** when tnreginet.gov.in has not loaded in 25 s (or fails), a banner offers *Reload* and *Open in
  browser* (Tamil and English).
- **Map credit** on the land check screen: © OpenStreetMap contributors (ODbL), tiles OpenFreeMap / OpenMapTiles.

## What's new in v2.7.0

- **Land check map data no longer depends on the Overpass servers alone.** The 2.6.2 CI probe still had all three free
  Overpass servers failing (overpass-api.de refusing the network with 406, the VK Maps mirror answering 504 "busy",
  private.coffee not answering), so water, roads and land use came back empty. Now, when Overpass has not answered the
  water / roads / land-use part within 20 s, the app also reads the same OpenStreetMap data from **OpenFreeMap vector
  tiles** (openfreemap.org – free, no key, no request limit; static tiles that do not get "busy"): water bodies,
  streams and wetlands, every road near the plot, main roads and roads under construction within ~5 km, and the land
  use / land cover under the pin. Overpass's answer is still used whenever it comes (it also knows proposed roads).
- About 0.5–1 MB of tiles per check, only when Overpass is slow; tiles are kept in memory for repeat checks.
- The tiles do not have power lines, proposed roads or the 10 km protected-area search, so amenities / utilities /
  rail / protected areas still come from Overpass only and stay *Unknown* when it fails.
- The Status / நிலை panel shows an "OpenFreeMap map tiles" line; the CI probe prints the tile result too.

## What's new in v2.6.2

- One Overpass server may now take up to 40 s to answer (was 25 s). The 2.6.1 CI probe showed the VK Maps mirror needing
  14–18 s for each single layer, so the combined water + roads + land-use query was always cut off at 25 s.
- The CI probe runs the land check's own map-data call first and is capped at 5 minutes (it no longer delays the APK).

## What's new in v2.6.1

- **Land check map data more reliable.** The 2.6.0 CI probe showed the free Overpass servers answering "504 busy" within
  a second or not answering at all, and the check gave up after one extra try 2 s later. Now, when every server has
  failed, it tries up to two more rounds after 3 s and 10 s (each on the next server that has not refused the app), and
  the map-data budget is 75 s instead of 45 s (the check runs in the background and shows partial results).
- The CI probe also reports overpass-api.de's /api/status and, for a group that failed on a server, each of its layers
  on their own. (overpass-api.de answered HTTP 406 to GitHub's Azure runners – that network is blocked by the operators;
  from a phone the app's identifying User-Agent is what their policy asks for. TNREGINET is not reachable from the
  runners; its guideline form ids – districtList, SROList, villageList, streetName, surveyName – were checked against
  a 2026 copy of the page and match the prefill script.)

## What's new in v2.6.0

- **AI search works with any provider (issue #1 finished).** The online price search (Price check, Area rates) and the
  land check's zoning / announced roads / growth search were still behind the 2.4.0 off-switches, so the provider chosen
  at setup was never actually used. They are now buttons – *Search nearby prices online (AI · provider)* and *Search
  zoning, roads & growth online (AI · provider)* – that run only on the user's tap (2.4.0's "nothing third-party is
  read automatically" still holds), in the app-wide CheckRunner (leaving the screen does not cancel them), and report
  every outcome in the Status panel (found / no key / quota / key refused / network). Gemini uses Google Search
  grounding; the other providers read the app's free web search results; every number must cite its page.
- Messages no longer say "Gemini key" / "free Gemini limit" when another provider is in use; "Auto" model choice puts
  premium tiers (OpenAI *-pro*, Claude Opus) last.
- **Import .sothu file / .sothu கோப்பை இறக்கு** on a new property, the property list (top bar, empty list) and Home's
  welcome card: the system file picker → the same Add / Update dialog as a received link. Opening a .sothu file from
  WhatsApp / Files (typed `application/octet-stream`) and sharing it into the app (any type, name ending in `.sothu`)
  now import it too.
- Price check Status separates "Rates you added" from rates found online.
- CI prints a live probe of every outside service (Overpass mirrors, Open-Meteo, Nominatim, web search engines,
  TNREGINET form ids, each AI provider) – `LIVE PROBE |` lines in the "Compile desktop target + run shared unit tests" step.
- ISRO Bhuvan land use stays off (its terms need NRSC's written permission for automatic use).

## What's new in v2.3.1

- **Guideline value & stamp duty / வழிகாட்டி மதிப்பு & முத்திரைத் தீர்வை** (property detail, right after the price check).
  - **Official guideline value from TNREGINET, read in the app.** *Look up on TNREGINET* opens the portal's Guideline
    search (tnreginet.gov.in, English page) in an in-app page and fills district → SRO → village → street / survey
    number one step at a time with the page's own lists (it never presses Submit and never changes a choice you made).
    After you search, *Read value* lists the rows on the page that carry a value; tap the one for your property (or
    long-press → copy → paste). The value is saved with its unit (₹ per sq ft / sq m / cent / acre / hectare), the page
    address, the date you saved it and the "in force from" date if you type it. A value you type yourself is labelled
    *Entered by you – check it on TNREGINET*. Nothing is ever estimated or invented; there is no AI lookup for it.
  - Why not a direct download: the guideline search is a JavaScript form whose lists load by AJAX (POST /portal/webHP with
    the session's cookies / CSRF token), the result is drawn in the page, a captcha guards other searches on the same
    page and there is no public API (the site also did not answer from our build servers outside India). Reading it in your own session on
    the real page is the reliable, honest route. Desktop opens the site in the browser and you type the value.
  - **Place details**: district, Sub-Registrar Office, village, street and survey number are saved with the property
    (district and village are filled from the map address – please check; the survey number is the one from the land check).
  - **Sale deed calculator**: guideline value × area, the market (asking) price, the gap between them in %, the value duty
    is charged on (the higher of the two), stamp duty 7 %, registration fee 2 % and the total, in ₹ with lakh / crore.
    A price below the guideline value is flagged (registered at the guideline value; under-valuation, Stamp Act s.47A).
    Houses / flats: optional land area (plot or UDS) – the building value (PWD rates) is not included and the card says so.
    *All buyers are women* applies the 1 % registration-fee concession up to ₹10 lakh (G.O. Ms. No.77, CT&R, 29.03.2025).
  - **Rates are settings** (card → *Edit rates*, or Settings): defaults 7 % stamp duty and 2 % registration fee for a TN
    sale deed – 2 % since 01.04.2023 by G.O.(Ms.) No.34, CT&R (J2), 30.03.2023 (TN Single Window Portal fee structure,
    cross-verified with TNREGINET). Sources are cited in `domain/registry/StampDuty.kt`.
  - The price check uses this property's own guideline value (plots, land, farmland) ahead of an area value.
- Data: new optional fields with defaults (`Property.registry` – district, SRO, village, street, guideline value with
  source / date, land area, women buyers; `AppSettings.stampDutyPct`, `registrationFeePct`) – older files load unchanged.
- Limits: the in-app form filling follows the portal's 2026 page (ids districtList / SROList / villageList / streetName /
  surveyName); if the portal changes, fill the form by hand – reading and saving the value still works.

## What's new in v2.3.0

- **Land check that does not give up when the free map server is busy.** The OpenStreetMap data is now fetched as seven
  small layers (water, roads, schools / hospitals / bus / shops, power & water supply, railway, protected areas & forest,
  land use) in parallel, each raced across the public Overpass servers (overpass.private.coffee – formerly
  overpass.kumi.systems –, maps.mail.ru and overpass-api.de; overpass.osm.ch is not used because it only holds
  Switzerland). A server that does not answer within 7 s gets a second server alongside; busy answers (429 / 504 /
  "runtime error" remarks) back off and move on; at most two requests per server; everything within one 40 s budget.
  Layers that load are shown; the ones that did not are named ("Some map layers did not load: roads") and their tiles
  say *Unknown* – and when an earlier check of the same spot had them, they are kept from it. A partial check is tried
  again on the next open (at most every 10 minutes).
- **ISRO Bhuvan land use** (free, keyless WMS GetFeatureInfo on bhuvan-vec1.nrsc.gov.in, SIS-DP v2 1:10,000 per TN
  district) at the pin and up to four corners – a second, independent source. If the water layer fails but Bhuvan shows a
  water body at the plot, Risk turns *HIGH*; if OSM shows no water but Bhuvan does, Risk is at least *CAUTION*. (Bhuvan's
  WFS is disabled and its cadastral layer draws nothing, so it gives no survey numbers.)
- **Drag the plot corners.** Corners follow the finger at once (no long-press – osmdroid's long-press drag was why
  "corners could only be undone"); the area updates while dragging. Tap a corner to select it, then *Delete corner*; small
  ◦ handles between corners add a corner there (tap or drag). Desktop: type lat / lng, tap a corner chip and *Move corner*.
- **FMB sketch overlay** (Android): pick the FMB sketch image (from eservices.tn.gov.in), then move it (N / S / E / W, 1 /
  5 / 20 m steps), size it, turn it and set its see-through level until it lines up with the map – and tap the corners over
  it. It is saved with the property.
- **Survey number (புல எண்)**: there is no open, free, keyless service that turns a map point into a TN survey number
  (TNGIS's APIs need an app name issued on approval; the GI viewer's own calls are not public), so the land check has an
  honest *Survey number* card: the place from the map, *Copy latitude, longitude*, *Open the TNGIS map (Tamil Nilam)* –
  paste the coordinates in "Goto" and tap the plot –, *Get the FMB sketch* and a field to save "123/4B" (normalised).
- **New tiles: Railway and Protected area / ESZ** (with rows and map markers): railway lines (3 km), level crossings,
  stations; a line within 50 m of the plot → *Very close* (a railway NOC is needed about 30 m from railway land –
  Indian Railways Works Manual para 827), within 200 m → noise caution. National parks, sanctuaries, tiger reserves and
  nature reserves within 10 km and forest land within 2 km: inside → *Restricted*, within 1 km of a park / sanctuary →
  likely inside its eco-sensitive zone (at least 1 km where none is notified – Supreme Court, In re T.N. Godavarman,
  3 June 2022, modified 26 April 2023), within 10 km → *Check ESZ*. Free OSM data; no official ESZ map is available as a
  free keyless service.
- **Share a property** (detail screen → share icon): a WhatsApp-friendly message with a link
  `https://harksathya.github.io/test/p/#1.…` – the property (and, if you choose, your notes, the land check and the broker)
  is compressed into the part after "#", so nothing is stored on a server. The page shows a preview, an *Open in Sothu Diary*
  button (Android intent to `sothudiary://p?d=…`, falling back to the APK download) and the APK link. In the app, a link
  you open (or share into the app from WhatsApp) asks *Add to my properties*, or – when you already have it – *Update my
  copy* (your notes, status, folders, documents and photos stay) / *Add as a new copy*.
  - App Links: the https link is declared (autoVerify) for `harksathya.github.io/test/p`, but Android only verifies
    `https://harksathya.github.io/.well-known/assetlinks.json` at the domain root, which needs a separate
    `harksathya.github.io` repository – so the link opens the page, which hands over to the app. `docs/.well-known/assetlinks.json`
    holds the statement (release key SHA-256 `F6:6D:71:31:87:1A:6C:3A:77:E0:92:42:50:E2:81:3F:27:3E:FD:38:67:A2:5D:DD:3D:64:69:BC:F6:3E:6E:1C`)
    to copy there if that repository is created.
- **Gemini key is optional on first run**: *Skip for now / இப்போதைக்கு தவிர்* on the setup card (and in the key dialog).
  Everything except the live price check and the AI parts works without a key; those show a gentle "add a key" note.
- **"Quota exhausted" fixed at the root.** Google Search grounding is free only on Gemini 2.5 Flash / Flash-Lite; on Gemini 3
  and newer it is not part of the free tier, and every grounded call answered 429 "limit: 0" – Auto picked the newest Flash
  first, so free keys always saw "quota exhausted". Now: grounded calls try 2.5 Flash / Flash-Lite first, at most 3
  grounded calls per lookup, and the quota error is read properly (per minute → retry delay, per day → until midnight
  Pacific, "limit: 0" → skip that model for a day).
  - **Free web search fallback**: when grounding is not possible, the app runs its own keyless web search (DuckDuckGo
    HTML, DuckDuckGo Lite, then Bing RSS with an unrelated-page filter) and gives the results to an ordinary Gemini call
    that may only use them: every rate / road / zoning / growth item must cite one of those exact pages or it is dropped
    (shown as "From web search"). When Gemini is out of quota too, the search results themselves are shown (title, snippet,
    page) so you can still read them. Sources are always shown.
- Data: new optional fields with defaults (`Property.surveyNumber`, `sketch`, `importedAt`; `LandCheck.osmFailedLayers`,
  `osmKeptLayers`, `layersVersion`, `lulc`, `aiViaSearch`, `webSnippets`; `RateLookup.viaSearch`, `snippets`) – older
  files load unchanged; land checks from 2.2.x show *Not part of this check – tap Refresh* on the two new tiles.
- Limits: the web search sites may refuse automated requests at times (then the fallback finds nothing and the friendly
  quota message stays); the share page needs a recent browser for its preview (the button works regardless); OSM
  railway / forest / protected-area coverage varies by area.

## What's new in v2.2.0

- **Land check / நில ஆய்வு** (property detail → *Land check*): a first screen of the plot before you sign, inspired by
  "drop a pin and see zoning, flood risk, proposed roads and data layers". Free sources only; every AI part is grounded,
  cited and labelled *AI estimate*.
  - **Plot outline**: tap the plot's corners on the map (*Draw plot corners*; drag a corner to move it; desktop: type
    lat / lng). Without corners the plot is a circle of the stated size around the pin; without a size only the pin is
    checked. The drawn area is compared with the stated size (flagged when they differ by more than 15 %).
  - **Map data layers** – one OpenStreetMap Overpass query (overpass-api.de, falls back to two public mirrors): lakes,
    tanks / kanmoi / eri, reservoirs, ponds, wetlands, rivers, canals, streams and drains (full outlines, multipolygons
    joined); highways / main roads (5 km) and roads `highway=construction|proposed`; access roads at the plot (400 m);
    schools / colleges, hospitals / clinics, bus stops / stands, markets / shops (3 km); railway stations (5 km); power
    lines, substations, transformers (1 km); water towers / works / tanks (1.5 km); the OSM landuse of the area the pin
    is in (zoning hint).
  - **Water / flood buffer**: % of the plot on a water body or within **15 m** of it (TN Combined Development and
    Building Rules 2019, Rule 27(1)), by grid sampling of the outline (~1,000 points). ≥ 10 % → **HIGH RISK** with the
    plain-language line "You'd pay for 2.4 acres but could build on about 1.8"; any overlap, water within 100 m or a
    low-lying plot (≥ 3 m below the land 500 m around, Open-Meteo / Copernicus 90 m elevation) → **CAUTION**.
  - **Six tiles** with a rating chip and a one-line reason: Boundary (never "verified" automatically – TN FMB / patta
    are not an open API: "Not verified – check FMB sketch / patta at eservices.tn.gov.in", or *Docs checked* when you
    ticked FMB sketch + patta in the documents checklist), Connectivity (main road ≤ 1 km strong, ≤ 3 km moderate; one
    step up for a new / proposed road ≤ 2 km, one step down with no mapped road at the plot), Risk, Locality (school,
    hospital, bus, shops within 3 km), Utility (power ≤ 500 m, water supply ≤ 1 km, or your own EB / water checklist
    ticks), Growth (AI estimate).
  - **AI web search** (optional, the same free Gemini key and model fallback as the price check, always grounded with
    Google Search): master-plan / DTCP / LPA zoning, announced / proposed / under-construction roads with distance, and
    the growth outlook – each item only with its source page (unsourced claims become "unknown").
  - Map overlay of the plot outline and nearby water bodies (osmdroid polygons), a Sources list, results cached for
    30 days per pin / corners / size (Refresh any time; a failed refresh keeps the earlier result), bilingual
    Tamil / English, and the friendly quota / no-internet / bad-key messages with *Details*.
  - **Worth it?** gets one extra HOLD reason only when a land check found HIGH water risk; properties without a land
    check are judged exactly as before.
- Data: new optional fields (`Property.boundary`, `AppData.landChecks`) with defaults – files from older versions load
  unchanged. The grounded Gemini call is now one shared function (`groundedGenerate`) used by the price check and the
  land check (same behaviour).
- Limits: OSM coverage is good in towns and patchy in villages (a missing tank on the map is not proof there is none);
  15 m is the TN rule distance, not a flood model; the AI's zoning / roads / growth are estimates from web pages.
  Always verify the FMB sketch, patta / chitta and A-register at eservices.tn.gov.in and the EC at tnreginet.gov.in.

## What's new in v2.1.2

- **Live price check on a free Gemini key**: a quota error (HTTP 429 / RESOURCE_EXHAUSTED) now moves on to the next model
  instead of stopping. Auto order: stable Flash (`gemini-flash-latest` first), then Flash-Lite, then previews; Pro only
  if nothing else is listed. Quota-limited models are skipped for about an hour; the API's `retryDelay` is shown as
  "try again after X". Every call stays grounded with Google Search (older 1.x models use `google_search_retrieval`);
  there is never an ungrounded call. A failed refresh keeps earlier rates.
- **One friendly bilingual error** (quota / bad key / no internet / model unavailable) with a small "Details" expander
  for the raw API text, instead of the raw text twice.
- **No self-comparison**: the property being checked (by id, its own source post, or anything within 50 m at the same
  rate) is never its own comparable – in the price check and in Area rates opened from a property.
- **Map centre fix**: maps no longer open over Central America. Cause: fitting osmdroid's bounding box to a zero-size box
  (the property and its own listing at the same spot) – `BoundingBox.increaseByScale()` turned it into a box spanning
  longitudes −165.7…−39.7 at zoom ~3.5. The camera is now computed by the app (centred on the property's pin, zoom
  9–16 to fit the comparables), and no map uses an invalid (NaN / out of range / 0,0) coordinate.
- **Soft background music** (Android): loops quietly (default 12 %) while the app is open, with a 2 s fade-in; pauses
  in the background, during voice notes and voice typing, and for calls / other audio (audio focus, ducking).
  Settings › Background music: on/off, volume, "Choose your own song" (copied into app storage). Note icon in the top
  bar to stop / play.

### Default background music – credit and licence

- Title: "rAga kIravANI" (veena alapana and tanam), excerpt 0:02–2:42, mono 64 kbps AAC (`composeApp/src/androidMain/res/raw/bgm_default.m4a`, 1.3 MB)
- Artist: L Ramakrishnan (recorded 1 January 2004)
- Source: https://commons.wikimedia.org/wiki/File:Kiravani-L_Ramakrishnan.ogg (original: https://archive.org/details/LR_kIravANI)
- Licence: Creative Commons CC0 1.0 Universal Public Domain Dedication (stated on the Commons file page)
- No copyrighted film music is bundled; users can choose their own song.

### Village list – credit and licence

- File: `composeApp/src/androidMain/res/raw/tn_villages.txt` (Tamil Nadu districts › taluks › villages)
- Compiled by https://github.com/mchittineni/india-village-finder v1.3.0 (code MIT; data GODL-India, see its `DATA_LICENSE.md`)
- Contains administrative data from the **Local Government Directory (LGD)**, Ministry of Panchayati Raj, Government of
  India, used under the **Government Open Data License – India (GODL-India)**
  (https://data.gov.in/government-open-data-license-india), accessed via the data.gov.in open-data API.
- Modified: Tamil Nadu only, reformatted to `District|Taluk|village;village;…`. Provided as is; not endorsed by the
  Government of India. Verify official names on https://lgdirectory.gov.in.

## What's new in v2.0

| Area | What it does |
|---|---|
| Clean start | No sample data. The app opens on a dashboard with a welcome / onboarding card. Upgrading from v1 deletes the records v1 had flagged `sample` (data migration v1 → v2); your own entries stay. |
| Dashboard (Home) | Hero with counts (properties, active deals, follow-ups due), quick actions (add property, log call, area rates, map), follow-ups due with one-tap call, price alerts (suspiciously low), deal pipeline by status, recently added (thumbnails), sources strip, recent calls & visits. |
| Smart share → auto-fill | Share a YouTube / Instagram / Facebook post or link, a caption, or **one or more images** (poster / screenshot) to the app. The image becomes the property photo; text is read from the caption, the link preview (YouTube oEmbed, Open Graph `og:title / og:description / og:image` with a browser User-Agent) and on-device **OCR** (ML Kit text recognition, Latin script). A pure-Kotlin parser (`domain/ListingParser.kt`, unit-tested) extracts title, description, price (₹25,00,000 · 25L · 25 lakhs · 25 லட்சம் · 1.2 Cr · கோடி), per-unit rates (“per cent ₹3.5L”, “சென்ட் 3 லட்சம்” → total = rate × size), size (cents / சென்ட் / sq ft / acres / ஏக்கர் / grounds = 2,400 sq ft), type (plot / மனை / DTCP, house / வீடு, farm land / பண்ணை, agricultural / விவசாய, apartment / flat / BHK / அடுக்குமாடி), phone numbers (+91 / 10-digit → matches an existing broker or offers to create one) and the locality (matched against the bundled Madurai localities in English and Tamil → also sets the map pin). Every auto-filled field is highlighted “Auto-filled – please check”. |
| Photos & thumbnails | Property photo (shared image or picked), link preview image or YouTube thumbnail (`img.youtube.com/vi/<id>/hqdefault.jpg`), loaded with Coil 3 (Compose Multiplatform). Image-led cards with gradients and 20 dp corners. |
| Folders & grouping | Properties tab has **List / Folders** views. Folders shows brand-coloured source tiles (YouTube, Instagram, Facebook, WhatsApp, Others) with counts and stacked thumbnails, plus your own folders (“Shortlist”, “Jaihindpuram plots”, …) – create, rename, recolour, delete, add / remove properties (from the folder or from a property's detail). The list can be grouped by **Source / Location / Type / Status** with collapsible sticky headers. |
| Real map location | OpenStreetMap via osmdroid (no API key; user agent = package name). Full-screen picker: Nominatim search (`countrycodes=in`, proper User-Agent, debounced, ≥ 1 s between requests) – e.g. “Jaihindpuram Madurai”; **Use my current location** (LocationManager, runtime permission); tap / long-press-drag the pin; reverse geocoding fills area / taluk / district; manual lat / lng entry. No location is ever defaulted. Property detail shows a mini map + **Open in Google Maps** (`geo:` intent, web fallback). **Map tab** shows all properties as pins coloured by verdict, filter by type, optional market-data localities. Desktop: text + lat / lng fallback. |
| Price check (rewritten) | Works for any location and type. Units: plot / land per cent (and sq ft), farmland / agricultural per acre (and per cent), house / apartment per sq ft. Comparables = (a) your own listings of the same type group within 3 km, widening to 5 / 10 km until there are 4, plus same-locality-name listings, and (b) the bundled market dataset: the locality named + the nearest 3–5 localities (≤ 15 km; nearest 3 regardless if none), with a related-type / city-wide fallback so there is always an answer. Shows the estimated fair range, median, number of comparables, each comparable with distance, rate, range, source, as-of date and a **low confidence** tag, the guideline value (your entry; for plots / land otherwise the dataset's village minimum – urban street values are not applied to farmland or built-up prices), % difference, verdict (Suspiciously low – possible false promise / Good / Fair / High / Too high), plain-language reasoning in Tamil / English, a range chart (Canvas) of where the quoted price sits, and a mini map of comparables. States that market figures are listing asking prices. |
| Area rates | Search any locality (English / Tamil) or any place on the map, pick a type → typical rate, fair range in all relevant units, guideline value, nearby localities, your own data nearby, map, and a browsable table of every locality with data. Usable before adding a property (“farmland rate near Jaihindpuram?”). |
| Voice notes | Per call / visit: record (MediaRecorder AAC .m4a, mic permission, timer, stop), attach an existing audio file such as the phone's call recording (copied into app storage), several notes per interaction, play / pause with progress, delete with confirmation. **Speech-to-text** button on the summary (Android SpeechRecognizer, ta-IN or en-IN – follows the app language, switchable) with graceful errors and a fallback to the system speech dialog. Audio is not included in the JSON export. |
| Reminders | Daily ~9 AM (inexact AlarmManager) notification for overdue / today's follow-ups, rescheduled after reboot / update; POST_NOTIFICATIONS asked when you first set a follow-up; toggle in Settings. |
| Professional UI | Adaptive vector icon (+ monochrome), splash (Android 12+ splash API, layer-list before), Material 3 blue / teal theme with **dark mode** (system / light / dark), consistent verdict / status colours, cards with shadows, animated navigation, confirm dialogs (delete, discard unsaved changes), input validation, Indian number formatting (₹1,23,45,678 · L · Cr), system back handling, tablet NavigationRail + two-pane. |

Everything from v1 still works: brokers (call / WhatsApp), calls & visits with quoted-price history, documents checklist, Go / Hold / Drop, search / filter / sort, guideline values, export / import JSON (v1 backups import too), share-to-app, desktop build.

## Market data

`composeApp/data/madurai_rates.json` – 57 Madurai localities with lat / lng and rates per type (plot per sq ft / per cent, apartment / house per sq ft, farmland per acre, TN guideline value, an `overall` portal average used only as a low-weight fallback), each with `source_name`, `source_url`, `as_of`, optional `confidence: low`. Market figures are **asking prices** from listing portals, not registered sale prices.
The file is compiled into `MarketDataJson.kt`; after editing the JSON run `python3 composeApp/data/gen_market_kt.py`.

## Architecture

Kotlin Multiplatform + Compose Multiplatform (Kotlin 2.0.20, Compose MP 1.7.0, AGP 8.5.2, Gradle 8.9). Libraries: kotlinx-serialization, kotlinx-datetime, coroutines, Coil 3 (+ OkHttp network), osmdroid, ML Kit text recognition.

```
composeApp/src/
  commonMain/kotlin/com/hark/sothudiary/
    App.kt                 root composable, adaptive phone / tablet layout, animated navigation
    Platform.kt            PlatformActions (+ AudioActions, LocationActions, images, OCR), expect httpGet / back handler
    data/                  Models (v2: voice notes, photos, folders, theme…), JsonRepository + migrate(), market/ dataset
    domain/                Units, Format (₹ lakh / crore), Geocoder (Nominatim), ListingParser + link previews
    domain/pricing/        estimateMarket / checkPrice (comparables, fair range, verdict) + worthIt
    i18n/                  Tr (en / ta), S (all strings), Labels
    ui/map/                expect GeoMap, LocationPickerDialog
    ui/components/         top bar, cards, PriceAnalysis (chart, comparables), Visuals (images, folder tiles), VoiceNotes
    ui/screens/            Home, Properties (list / folders / grouping), Folder view, Map, Area rates, Property detail / edit,
                           Brokers, Interaction edit, Follow-ups, Guideline values, More, Settings
  commonTest/              ListingParserTest (parser + price check)
  androidMain/             MainActivity (share text / images, permissions, pickers), AndroidPlatform, AndroidAudio,
                           AndroidLocation, AndroidImages (ML Kit OCR), Reminders (AlarmManager + receiver), GeoMap (osmdroid)
  desktopMain/             main.kt, desktop GeoMap fallback, httpGet
```

## Build

```
cd SothuDiary
./gradlew :composeApp:assembleDebug          # Android APK → composeApp/build/outputs/apk/debug/
./gradlew :composeApp:desktopTest            # shared unit tests
./gradlew :composeApp:run                    # Desktop (Windows / macOS / Linux)
```

CI: `.github/workflows/sothu-diary.yml` builds on every push touching `SothuDiary/**`, runs the shared tests
(informational), uploads the APK as an artifact and publishes it to the `sothu-diary-latest` release.

## Limitations

* OCR reads Latin script (English, numbers); ML Kit has no Tamil model, so Tamil text inside a poster image is not read (Tamil captions / link text are parsed).
* Instagram / Facebook often block link previews without login – then only the shared caption is used.
* Speech-to-text needs a speech service (Google app) and usually internet; Tamil needs the ta-IN voice pack.
* Map tiles need internet (they are cached afterwards). Nominatim is a free community service – heavy use is rate-limited.

> Verdicts are only as good as the data. Always verify documents with a lawyer and the sub-registrar office before paying any advance.
