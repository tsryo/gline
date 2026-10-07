package com.gline

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("gline", Context.MODE_PRIVATE)

    var input by mutableStateOf("")
    var progress by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var result by mutableStateOf<Conversion?>(null)
        private set

    /** Null means "use the mode in the link". */
    var modeChoice by mutableStateOf(prefs.getString(KEY_MODE, null)?.let { runCatching { TravelMode.valueOf(it) }.getOrNull() })
        private set
    var engine by mutableStateOf(
        prefs.getString(KEY_ENGINE, null)?.let { runCatching { RoutingEngine.valueOf(it) }.getOrNull() } ?: RoutingEngine.OSM,
    )
        private set
    var googleKey by mutableStateOf(prefs.getString(KEY_GOOGLE, "").orEmpty())
        private set

    /** Set while the converter waits for the UI to ask for location access. */
    var locationRequest by mutableStateOf<CompletableDeferred<Boolean>?>(null)
        private set

    private val converter = Converter(Locator(app)) {
        CompletableDeferred<Boolean>().also { locationRequest = it }.await()
    }
    private var job: Job? = null

    fun onShared(text: String) {
        input = text
        convert()
    }

    fun convert() {
        job?.cancel()
        result = null
        error = null
        job = viewModelScope.launch {
            try {
                val key = googleKey.trim()
                val useEngine = if (engine == RoutingEngine.GOOGLE && key.isEmpty()) RoutingEngine.OSM else engine
                result = converter.convert(input, modeChoice, useEngine, key) { progress = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ConversionException) {
                error = e.message
            } catch (e: Exception) {
                error = "Something went wrong: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                progress = null
            }
        }
    }

    fun onLocationPermissionResult(granted: Boolean) {
        locationRequest?.complete(granted)
        locationRequest = null
    }

    fun chooseMode(mode: TravelMode?) {
        modeChoice = mode
        prefs.edit { putString(KEY_MODE, mode?.name) }
        redoIfShown()
    }

    fun chooseEngine(choice: RoutingEngine) {
        engine = choice
        prefs.edit { putString(KEY_ENGINE, choice.name) }
        redoIfShown()
    }

    /** Changing an option after converting re-runs the conversion with it. */
    private fun redoIfShown() {
        if (input.isNotBlank() && (result != null || error != null)) convert()
    }

    fun updateGoogleKey(key: String) {
        googleKey = key
        prefs.edit { putString(KEY_GOOGLE, key.trim()) }
    }

    private companion object {
        const val KEY_MODE = "mode"
        const val KEY_ENGINE = "engine"
        const val KEY_GOOGLE = "google_key"
    }
}
