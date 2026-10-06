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
import gr.dkaratzas.tanrenkiroku.data.WorkoutRepository
import gr.dkaratzas.tanrenkiroku.data.parseSyncQr
import gr.dkaratzas.tanrenkiroku.data.syncToDesktop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// Main View Model for syncing to desktop purposes

private const val TAG = "TanrenSync"

sealed class SyncState {
    object Scanning : SyncState()
    data class InProgress(val step: String) : SyncState()
    data class Done(val uploaded: Int, val deleted: Int = 0) : SyncState()
    data class Failed(val message: String) : SyncState()
}

class SyncViewModel(application: Application) : AndroidViewModel(application) {

    var state by mutableStateOf<SyncState>(SyncState.Scanning)
        private set

    private val workoutsDir: File = WorkoutRepository(application).workoutsDir

    private var qrHandled = false
    private var syncJob: Job? = null

    fun onQrScanned(raw: String) {
        if (qrHandled)
            return
        qrHandled = true

        val payload = try {
            parseSyncQr(raw)
        } catch (e: IllegalArgumentException) {
            state = SyncState.Failed(e.message ?: "Invalid desktop QR code")
            return
        } catch (e: Exception) {
            // Serialization exceptions can include the QR text and its bearer token.
            Log.e(TAG, "QR parse failed: ${e::class.simpleName}")
            state = SyncState.Failed("Invalid QR code. Scan the QR code shown by TANREN Metsuke.")
            return
        }
        runSync(payload)
    }

    private fun runSync(payload: QrPayload) {
        syncJob = viewModelScope.launch {
            try {
                val result = syncToDesktop({ SyncRepository(workoutsDir, payload) }) { step ->
                    withContext(Dispatchers.Main) { state = SyncState.InProgress(step) }
                }
                state = SyncState.Done(uploaded = result.uploaded, deleted = result.deleted)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed at state: $state", e)
                val step = (state as? SyncState.InProgress)?.step ?: "Sync"
                state = SyncState.Failed(e.message?.takeIf { it.isNotBlank() }
                    ?: "$step failed (${e.javaClass.simpleName}). Refresh the desktop connection and retry.")
            }
        }
    }

    fun reset() {
        syncJob?.cancel()
        syncJob = null
        qrHandled = false
        state = SyncState.Scanning
    }
}
