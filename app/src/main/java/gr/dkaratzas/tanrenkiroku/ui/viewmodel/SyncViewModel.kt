package gr.dkaratzas.tanrenkiroku.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import gr.dkaratzas.tanrenkiroku.data.QrPayload
import gr.dkaratzas.tanrenkiroku.data.SyncRepository
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

// Main View Model for syncing to desktop purposes

private const val TAG = "TanrenSync"
private val json = Json { ignoreUnknownKeys = true }

sealed class SyncState {
    object Scanning : SyncState()
    data class InProgress(val step: String) : SyncState()
    data class Done(val uploaded: Int, val deleted: Int = 0) : SyncState()
    data class Failed(val message: String) : SyncState()
}

class SyncViewModel(application: Application) : AndroidViewModel(application) {

    var state by mutableStateOf<SyncState>(SyncState.Scanning)
        private set

    private val workoutsDir: File
        get() = getApplication<Application>().getExternalFilesDir(null)
            ?: getApplication<Application>().filesDir

    private var qrHandled = false

    fun onQrScanned(raw: String) {
        if (qrHandled)
            return
        qrHandled = true

        val payload = try {
            json.decodeFromString<QrPayload>(raw)
        } catch (e: Exception) {
            Log.e(TAG, "QR parse failed: $raw", e)
            state = SyncState.Failed("Invalid QR code: ${e.message}")
            return
        }
        runSync(payload)
    }

    private fun runSync(payload: QrPayload) {
        viewModelScope.launch {
            val repo = SyncRepository(workoutsDir, payload)
            try {
                state = SyncState.InProgress("Connecting...")
                repo.ping()

                state = SyncState.InProgress("Sending manifest...")
                val response = repo.sendManifest()

                if (response.needed.isEmpty()) {
                    state = SyncState.Done(uploaded = 0, deleted = response.deleted)
                    return@launch
                }

                response.needed.forEachIndexed { i, filename ->
                    state = SyncState.InProgress("Uploading file ${i + 1} of ${response.needed.size}...")
                    repo.uploadFile(filename)
                }

                state = SyncState.Done(uploaded = response.needed.size, deleted = response.deleted)
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed at state: $state", e)
                state = SyncState.Failed("${e::class.simpleName}: ${e.message}")
            }
        }
    }

    fun reset() {
        qrHandled = false
        state = SyncState.Scanning
    }
}
