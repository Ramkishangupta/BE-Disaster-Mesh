package com.bitchat.android.features.emergency

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.bitchat.android.geohash.SystemLocationProvider
import com.bitchat.android.mesh.BluetoothMeshService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages disaster SOS beacons, active distress states, telemetry gathering,
 * periodic mesh re-broadcasting, and responder acknowledgments.
 */
class EmergencyManager private constructor() {

    companion object {
        private const val TAG = "EmergencyManager"
        val shared by lazy { EmergencyManager() }

        // Morse code pattern for S-O-S: ... --- ...
        private val SOS_VIBRATION_PATTERN = longArrayOf(
            0,
            150, 100, 150, 100, 150, // S (...)
            200,
            400, 100, 400, 100, 400, // O (---)
            200,
            150, 100, 150, 100, 150  // S (...)
        )

        // Two solid pulses when responder arrives/acknowledges
        private val ACK_VIBRATION_PATTERN = longArrayOf(0, 300, 150, 500)
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var periodicBroadcastJob: Job? = null

    private val _activeBeacons = MutableStateFlow<List<EmergencyBeacon>>(emptyList())
    val activeBeacons: StateFlow<List<EmergencyBeacon>> = _activeBeacons.asStateFlow()

    private val _myActiveSos = MutableStateFlow<EmergencyBeacon?>(null)
    val myActiveSos: StateFlow<EmergencyBeacon?> = _myActiveSos.asStateFlow()

    private val _responders = MutableStateFlow<Map<String, List<EmergencyAck>>>(emptyMap())
    val responders: StateFlow<Map<String, List<EmergencyAck>>> = _responders.asStateFlow()

    private val _respondedBeaconIds = MutableStateFlow<Set<String>>(emptySet())
    val respondedBeaconIds: StateFlow<Set<String>> = _respondedBeaconIds.asStateFlow()

    // Deduplication cache: beaconId -> last processed timestamp
    private val processedBeaconTimestamps = ConcurrentHashMap<String, Long>()

    /**
     * Broadcast an emergency distress beacon across the Bluetooth LE mesh.
     */
    fun broadcastSos(
        context: Context,
        meshService: BluetoothMeshService?,
        myNickname: String,
        severity: EmergencySeverity,
        note: String,
        onComplete: (EmergencyBeacon) -> Unit = {}
    ) {
        val myPeerID = meshService?.myPeerID ?: "unknown"
        val batteryPct = getBatteryPercentage(context)

        // Query last known GPS coordinates
        val locationProvider = SystemLocationProvider(context)
        locationProvider.getLastKnownLocation { location ->
            val beacon = EmergencyBeacon(
                senderPeerID = myPeerID,
                senderNickname = myNickname,
                severity = severity,
                latitude = location?.latitude,
                longitude = location?.longitude,
                accuracyMeters = location?.accuracy,
                batteryPercent = batteryPct,
                timestamp = System.currentTimeMillis(),
                note = note.trim(),
                active = true
            )

            _myActiveSos.value = beacon
            Log.i(TAG, "🚨 Broadcasting SOS Beacon: severity=${severity.title}, battery=$batteryPct%, loc=${location?.latitude},${location?.longitude}")

            meshService?.broadcastEmergencyBeacon(beacon)

            // Start periodic rebroadcast (every 20s) to overcome dynamic mesh churn
            periodicBroadcastJob?.cancel()
            periodicBroadcastJob = scope.launch {
                while (isActive && _myActiveSos.value?.active == true) {
                    delay(20_000)
                    if (_myActiveSos.value?.active == true) {
                        Log.d(TAG, "Re-broadcasting active emergency beacon: ${beacon.id}")
                        meshService?.broadcastEmergencyBeacon(_myActiveSos.value ?: beacon)
                    }
                }
            }

            // Trigger immediate local SOS vibration pattern
            triggerVibration(context, SOS_VIBRATION_PATTERN)
            onComplete(beacon)
        }
    }

    /**
     * Cancel an active emergency distress broadcast (mark "I am Safe").
     */
    fun cancelSos(context: Context, meshService: BluetoothMeshService?, myNickname: String) {
        val current = _myActiveSos.value ?: return
        periodicBroadcastJob?.cancel()
        periodicBroadcastJob = null

        val cancelBeacon = current.copy(
            active = false,
            timestamp = System.currentTimeMillis(),
            note = "Distress resolved - I am safe"
        )

        _myActiveSos.value = null
        meshService?.broadcastEmergencyBeacon(cancelBeacon)
        Log.i(TAG, "✅ Emergency beacon cancelled: ${cancelBeacon.id}")
    }

    /**
     * Send responder acknowledgment packet back to the victim.
     */
    fun respondToEmergency(
        context: Context,
        meshService: BluetoothMeshService?,
        beacon: EmergencyBeacon,
        myNickname: String,
        message: String = "Help is on the way!"
    ) {
        val myPeerID = meshService?.myPeerID ?: return
        val locationProvider = SystemLocationProvider(context)

        locationProvider.getLastKnownLocation { location ->
            val ack = EmergencyAck(
                beaconId = beacon.id,
                responderPeerID = myPeerID,
                responderNickname = myNickname,
                responderLatitude = location?.latitude,
                responderLongitude = location?.longitude,
                timestamp = System.currentTimeMillis(),
                message = message
            )

            meshService.sendEmergencyAck(ack, beacon.senderPeerID)

            // Mark as responded locally
            val updated = _respondedBeaconIds.value.toMutableSet()
            updated.add(beacon.id)
            _respondedBeaconIds.value = updated

            Log.i(TAG, "🤝 Responded to beacon ${beacon.id} sent by ${beacon.senderNickname}")
        }
    }

    /**
     * Ingest an incoming emergency beacon from the mesh network.
     */
    fun handleIncomingBeacon(context: Context, beacon: EmergencyBeacon, myPeerID: String) {
        // Skip own beacons
        if (beacon.senderPeerID == myPeerID) return

        val now = System.currentTimeMillis()
        val lastSeen = processedBeaconTimestamps[beacon.id] ?: 0L

        // Deduplicate rapid rebroadcasts within 5 seconds unless active status changed
        if (now - lastSeen < 5000 && beacon.active) {
            return
        }
        processedBeaconTimestamps[beacon.id] = now

        if (!beacon.active) {
            // Remove beacon as distress is resolved
            _activeBeacons.value = _activeBeacons.value.filter { it.id != beacon.id }
            Log.i(TAG, "🟢 Emergency resolved for ${beacon.senderNickname}")
            return
        }

        // Add or update active beacon
        val currentList = _activeBeacons.value.filter { it.id != beacon.id }.toMutableList()
        currentList.add(0, beacon) // Place newest on top
        _activeBeacons.value = currentList

        Log.w(TAG, "🚨 INCOMING DISTRESS ALERT: ${beacon.severity.title} from ${beacon.senderNickname} (${beacon.batteryPercent}% batt)")

        // Trigger urgent alert vibration
        triggerVibration(context, SOS_VIBRATION_PATTERN)
    }

    /**
     * Ingest incoming responder acknowledgment.
     */
    fun handleIncomingAck(context: Context, ack: EmergencyAck) {
        val map = _responders.value.toMutableMap()
        val list = map[ack.beaconId]?.toMutableList() ?: mutableListOf()
        if (list.none { it.responderPeerID == ack.responderPeerID }) {
            list.add(ack)
            map[ack.beaconId] = list
            _responders.value = map

            Log.i(TAG, "✅ Responder confirmed for beacon ${ack.beaconId}: ${ack.responderNickname}")

            // If this is for my active SOS, alert victim with positive feedback vibration
            if (_myActiveSos.value?.id == ack.beaconId) {
                triggerVibration(context, ACK_VIBRATION_PATTERN)
            }
        }
    }

    /**
     * Calculate approximate distance in meters between two GPS coordinates.
     */
    fun calculateDistanceMeters(
        fromLat: Double?,
        fromLon: Double?,
        toLat: Double?,
        toLon: Double?
    ): Float? {
        if (fromLat == null || fromLon == null || toLat == null || toLon == null) return null
        return try {
            val results = FloatArray(1)
            Location.distanceBetween(fromLat, fromLon, toLat, toLon, results)
            results[0]
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Human-readable distance formatting.
     */
    fun formatDistance(meters: Float?): String {
        if (meters == null) return "Unknown distance"
        return if (meters < 1000) {
            "~${meters.toInt()} m away"
        } else {
            "~${String.format("%.1f", meters / 1000f)} km away"
        }
    }

    /**
     * Read current device battery percentage (0-100%).
     */
    fun getBatteryPercentage(context: Context): Int {
        return try {
            val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
                context.registerReceiver(null, filter)
            }
            val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) {
                ((level / scale.toFloat()) * 100).toInt()
            } else {
                -1
            }
        } catch (e: Exception) {
            -1
        }
    }

    /**
     * Trigger haptic vibration pattern.
     */
    private fun triggerVibration(context: Context, pattern: LongArray) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createWaveform(pattern, -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, -1)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to vibrate: ${e.message}")
        }
    }
}

