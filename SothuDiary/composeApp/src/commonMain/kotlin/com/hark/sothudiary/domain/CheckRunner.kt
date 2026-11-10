package com.hark.sothudiary.domain

import androidx.compose.runtime.mutableStateMapOf
import com.hark.sothudiary.data.JsonRepository
import com.hark.sothudiary.data.Property
import com.hark.sothudiary.data.nowMillis
import com.hark.sothudiary.domain.land.landCheckFor
import com.hark.sothudiary.domain.land.runLandCheck
import com.hark.sothudiary.domain.ai.llm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.toLocalDateTime

/**
 * 2.4.0: runs the Land check in an app-wide scope (the price check is computed on the phone from the user's own data).
 *
 * Root cause fixed: both checks used to run in the screen's own coroutine scope (rememberCoroutineScope), so leaving
 * the screen – or the property screen being recomposed away while a 40–90 s lookup was running – cancelled the work and
 * nothing was saved; with `runCatching` around it the cancellation was also swallowed silently. Now a started check
 * finishes and saves even if the user goes back, and the screens only read [running] and the saved results.
 */
object CheckRunner {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** "land:<id>" -> start time (epoch ms) while running. */
    val running = mutableStateMapOf<String, Long>()

    fun isRunning(kind: String, propertyId: String) = running.containsKey("$kind:$propertyId")

    private suspend fun onMain(block: () -> Unit) = withContext(Dispatchers.Main) { block() }

    /** Starts the land check of [p] unless one is running. */
    fun land(repo: JsonRepository, p: Property, typeText: String) {
        val k = "land:${p.id}"
        if (running.containsKey(k) || p.lat == null || p.lng == null) return
        running[k] = nowMillis()
        val settings = repo.data.settings
        scope.launch {
            try {
                // Fill blank district / taluk / village of the land record from the pin (always editable).
                val lat = p.lat
                val lng = p.lng
                val rev = runCatching { Geocoder.reverse(lat, lng) }.getOrNull()
                if (rev != null) {
                    val dir = com.hark.sothudiary.domain.land.VillageDirectory.instance
                    val d = dir?.district(rev.district) ?: rev.district
                    val t = dir?.taluk(d, rev.taluk) ?: rev.taluk
                    onMain { repo.fillLandFromMap(p.id, d, t, rev.locality) }
                }
                val prev = landCheckFor(repo.data, p.id)
                val r = runLandCheck(p, settings.llm(), typeText, prev, onPartial = { partial ->
                    onMain { repo.saveLandCheck(partial) }
                })
                if (r != null) onMain { repo.saveLandCheck(r) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Never crash the app from a background check; the Status panel shows what was saved.
            } finally {
                withContext(Dispatchers.Main) { running.remove(k) }
            }
        }
    }

    /** 2.8.0: last document-check failure per property (shown on the Documents screen), cleared on success. */
    val docErrors = mutableStateMapOf<String, String>()

    /**
     * 2.8.0: reads the property's uploaded documents with the user's AI provider and checks them ([buildAnalysis]).
     * Runs in the app-wide scope like the land check, so leaving the screen does not stop it.
     */
    /** 2.9.0: progress of reading documents ("EC · pages 3–4 of 12"), by property id. */
    val docProgress = mutableStateMapOf<String, String>()
    /** 2.9.0: last EC chat error, by property id. */
    val chatErrors = mutableStateMapOf<String, String>()

    /** 2.9.0: reads every document that has no saved text yet (page by page). Returns an error or null. */
    private suspend fun ensureDocTexts(repo: JsonRepository, pid: String, llm: com.hark.sothudiary.domain.ai.LlmConfig, platform: com.hark.sothudiary.PlatformActions): String? {
        val p = repo.data.properties.firstOrNull { it.id == pid } ?: return "property missing"
        for (d in p.documents) {
            if (p.docTexts.any { it.docId == d.id && it.text.isNotBlank() }) continue
            val label = d.kind.name.replace('_', ' ')
            val r = com.hark.sothudiary.domain.docs.transcribeDoc(llm, d, { platform.readStoredFile(it) }, { f, from, n -> platform.pdfPageImages(f, from, n) }) { done, total ->
                val line = if (total > 0) "$label · ${done + 1}–${minOf(done + com.hark.sothudiary.domain.docs.EcChatRules.PAGES_PER_CALL, total)} / $total" else label
                scope.launch { onMain { docProgress[pid] = line } }
            }
            val t = r.getOrElse { return "$label: " + (it.message ?: "error") }
            onMain { repo.saveDocText(pid, t) }
        }
        return null
    }

    private fun docPairs(repo: JsonRepository, pid: String): Pair<Property, List<Pair<com.hark.sothudiary.data.PropertyDocument, com.hark.sothudiary.data.DocText>>>? {
        val p = repo.data.properties.firstOrNull { it.id == pid } ?: return null
        return p to p.documents.mapNotNull { d -> p.docTexts.firstOrNull { it.docId == d.id }?.let { d to it } }
    }

    /**
     * 2.8.0: "Check documents" – ownership chain + red flags. 2.9.0: the documents are first read page by page and
     * saved as text ([ensureDocTexts]); the check itself is then one text-only call.
     */
    fun docs(repo: JsonRepository, p: Property, platform: com.hark.sothudiary.PlatformActions) {
        val k = "docs:${p.id}"
        if (running.containsKey(k) || p.documents.isEmpty()) return
        running[k] = nowMillis()
        docErrors.remove(p.id)
        val llm = repo.data.settings.llm()
        scope.launch {
            try {
                ensureDocTexts(repo, p.id, llm, platform)?.let { e -> onMain { docErrors[p.id] = e }; return@launch }
                val (fresh, pairs) = docPairs(repo, p.id) ?: return@launch
                if (pairs.isEmpty()) { onMain { docErrors[p.id] = "files missing" }; return@launch }
                onMain { docProgress[p.id] = "analysis" }
                val prompt = com.hark.sothudiary.domain.docs.analysisPrompt(fresh.land, pairs.map { it.first.kind }, fresh.title) +
                    "\n\nThe documents are given below as text (read from the files earlier), not as attached files.\n" +
                    com.hark.sothudiary.domain.docs.documentsBlock(pairs)
                when (val r = com.hark.sothudiary.domain.docs.askText(llm, prompt)) {
                    is com.hark.sothudiary.domain.docs.DocAiResult.Failed -> onMain { docErrors[p.id] = r.message + (if (r.model.isNotBlank()) " · ${r.model}" else "") }
                    is com.hark.sothudiary.domain.docs.DocAiResult.Ok -> {
                        val reading = com.hark.sothudiary.domain.docs.parseAiReading(r.text)
                        if (reading == null) onMain { docErrors[p.id] = "the AI answer could not be read · ${r.model}" }
                        else {
                            val today = kotlinx.datetime.Clock.System.now()
                                .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date.toString()
                            val a = com.hark.sothudiary.domain.docs.buildAnalysis(
                                reading, fresh.land, hasEc = pairs.any { it.first.kind == com.hark.sothudiary.data.DocKind.EC }, now = today,
                                analysedAt = nowMillis(), provider = llm.provider.name, model = r.model, docIds = pairs.map { it.first.id },
                            )
                            onMain { repo.saveDocAnalysis(p.id, a) }
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) { docErrors[p.id] = e.message ?: "error" }
            } finally {
                withContext(Dispatchers.Main) { running.remove(k); docProgress.remove(p.id) }
            }
        }
    }

    /** 2.9.0: one EC chat question – reads unread documents first, then answers from the saved text. */
    fun askDocs(repo: JsonRepository, p: Property, question: String, platform: com.hark.sothudiary.PlatformActions) {
        val k = "ask:${p.id}"
        val q = question.trim()
        if (running.containsKey(k) || q.isEmpty() || p.documents.isEmpty()) return
        running[k] = nowMillis()
        chatErrors.remove(p.id)
        val history = p.docChat
        repo.addDocChat(p.id, com.hark.sothudiary.data.DocChatMsg(true, q, nowMillis()))
        val llm = repo.data.settings.llm()
        scope.launch {
            try {
                ensureDocTexts(repo, p.id, llm, platform)?.let { e -> onMain { chatErrors[p.id] = e }; return@launch }
                val (fresh, pairs) = docPairs(repo, p.id) ?: return@launch
                onMain { docProgress[p.id] = "answer" }
                val prompt = com.hark.sothudiary.domain.docs.chatPrompt(fresh.land, fresh.title, pairs, history, q)
                when (val r = com.hark.sothudiary.domain.docs.askText(llm, prompt)) {
                    is com.hark.sothudiary.domain.docs.DocAiResult.Ok -> onMain { repo.addDocChat(p.id, com.hark.sothudiary.data.DocChatMsg(false, r.text, nowMillis(), r.model)) }
                    is com.hark.sothudiary.domain.docs.DocAiResult.Failed -> onMain { chatErrors[p.id] = r.message + (if (r.model.isNotBlank()) " · ${r.model}" else "") }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) { chatErrors[p.id] = e.message ?: "error" }
            } finally {
                withContext(Dispatchers.Main) { running.remove(k); docProgress.remove(p.id) }
            }
        }
    }

    /**
     * 2.6.0: "Search online with AI" on the Land check screen – zoning, announced roads and growth from the AI provider
     * chosen at setup (issue #1). Runs only on the user's tap, needs a saved check for the current pin ([base]).
     */
    fun landAi(repo: JsonRepository, p: Property, typeText: String, base: com.hark.sothudiary.data.LandCheck) {
        val k = "landai:${p.id}"
        if (running.containsKey(k)) return
        running[k] = nowMillis()
        val llm = repo.data.settings.llm()
        scope.launch {
            try {
                val r = com.hark.sothudiary.domain.land.runLandAi(p, llm, typeText, base)
                onMain {
                    // Save onto the newest check of this property (a land check may have finished meanwhile).
                    val latest = landCheckFor(repo.data, p.id)?.takeIf { it.key == base.key } ?: base
                    repo.saveLandCheck(latest.copy(ai = r.ai, aiError = r.aiError, aiRetryAt = r.aiRetryAt, aiModel = r.aiModel,
                        aiFetchedAt = r.aiFetchedAt, sources = r.sources, aiViaSearch = r.aiViaSearch, webSnippets = r.webSnippets,
                        status = latest.status.filterNot { it.source.startsWith(com.hark.sothudiary.domain.land.LandRules.AI_STATUS_PREFIX) } +
                            r.status.filter { it.source.startsWith(com.hark.sothudiary.domain.land.LandRules.AI_STATUS_PREFIX) }))
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                // The Status panel keeps the earlier lines; nothing to crash for.
            } finally {
                withContext(Dispatchers.Main) { running.remove(k) }
            }
        }
    }

    /**
     * 2.6.0: "Search online prices with AI" (Price check / Area rates): the live lookup of 2.1–2.3 with the provider
     * chosen at setup, for the property's own pin and type. Only on the user's tap (no automatic price data, 2.4.0).
     * [key] identifies the place for [running] ("price:<property id>" or an Area rates target).
     */
    fun price(repo: JsonRepository, key: String, lat: Double, lng: Double, group: Int, placeHint: String) {
        if (running.containsKey(key)) return
        running[key] = nowMillis()
        val llm = repo.data.settings.llm()
        scope.launch {
            try {
                val r = com.hark.sothudiary.domain.pricing.liveLookup(llm, lat, lng, group, placeHint)
                onMain { repo.saveLookup(r.lookup, r.rates) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                val lk = com.hark.sothudiary.domain.pricing.lookupKey(lat, lng, group)
                onMain { repo.saveLookup(com.hark.sothudiary.data.RateLookup(lk, lat, lng, group, placeHint, nowMillis(),
                    error = com.hark.sothudiary.domain.pricing.taggedError(com.hark.sothudiary.domain.pricing.LookupErrorKind.OTHER, e.message ?: e::class.simpleName.orEmpty())), emptyList()) }
            } finally {
                withContext(Dispatchers.Main) { running.remove(key) }
            }
        }
    }
}
