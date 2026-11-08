package com.hark.sothudiary.domain

import androidx.compose.runtime.mutableStateMapOf
import com.hark.sothudiary.data.JsonRepository
import com.hark.sothudiary.data.Property
import com.hark.sothudiary.data.nowMillis
import com.hark.sothudiary.domain.land.landCheckFor
import com.hark.sothudiary.domain.land.runLandCheck
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                val r = runLandCheck(p, settings.geminiApiKey, settings.geminiModel, typeText, prev, onPartial = { partial ->
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
}
