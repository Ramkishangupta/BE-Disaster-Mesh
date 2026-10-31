package com.bitchat.android.features.emergency

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Triage severity categories for emergency disaster mesh beacons.
 */
enum class EmergencySeverity(val code: Byte, val title: String, val description: String) {
    CRITICAL_MEDICAL(0x01.toByte(), "Critical Medical", "Severe trauma, injury, or medical crisis requiring immediate care"),
    TRAPPED_SEARCH_RESCUE(0x02.toByte(), "Trapped / Rescue", "Trapped under debris, in vehicle, flood, or structure"),
    HAZARD_WARNING(0x03.toByte(), "Hazard / Danger", "Fire, gas leak, hazardous material, or imminent danger"),
    SUPPLIES_WATER(0x04.toByte(), "Supplies / Safe", "Safe but stranded, need food, clean water, or shelter");

    companion object {
        fun fromCode(code: Byte): EmergencySeverity {
            return entries.firstOrNull { it.code == code } ?: CRITICAL_MEDICAL
        }
    }
}

/**
 * Offline Disaster SOS Beacon payload transmitted across multi-hop Bluetooth LE mesh.
 */
data class EmergencyBeacon(
    val id: String = UUID.randomUUID().toString().uppercase(),
    val senderPeerID: String,
    val senderNickname: String,
    val severity: EmergencySeverity,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val batteryPercent: Int = -1,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String = "",
    val active: Boolean = true
) {
    fun encode(): ByteArray {
        val idBytes = id.toByteArray(Charsets.UTF_8).take(64).toByteArray()
        val nickBytes = senderNickname.toByteArray(Charsets.UTF_8).take(64).toByteArray()
        val noteBytes = note.toByteArray(Charsets.UTF_8).take(512).toByteArray()

        val hasLocation = latitude != null && longitude != null
        var flags: Byte = 0
        if (hasLocation) flags = (flags.toInt() or 0x01).toByte()
        if (active) flags = (flags.toInt() or 0x02).toByte()
        if (accuracyMeters != null) flags = (flags.toInt() or 0x04).toByte()

        val capacity = 1 + 1 + 1 + 8 + 1 + idBytes.size + 1 + nickBytes.size + 2 + noteBytes.size +
                (if (hasLocation) 16 else 0) + (if (accuracyMeters != null) 4 else 0)
        val buffer = ByteBuffer.allocate(capacity).apply { order(ByteOrder.BIG_ENDIAN) }

        buffer.put(flags)
        buffer.put(severity.code)
        buffer.put(batteryPercent.coerceIn(-1, 100).toByte())
        buffer.putLong(timestamp)

        buffer.put(idBytes.size.toByte())
        buffer.put(idBytes)

        buffer.put(nickBytes.size.toByte())
        buffer.put(nickBytes)

        buffer.putShort(noteBytes.size.toShort())
        buffer.put(noteBytes)

        if (hasLocation) {
            buffer.putDouble(latitude!!)
            buffer.putDouble(longitude!!)
        }
        if (accuracyMeters != null) {
            buffer.putFloat(accuracyMeters)
        }

        return buffer.array()
    }

    companion object {
        fun decode(bytes: ByteArray, senderPeerID: String): EmergencyBeacon? {
            return try {
                if (bytes.size < 13) return null
                val buffer = ByteBuffer.wrap(bytes).apply { order(ByteOrder.BIG_ENDIAN) }

                val flags = buffer.get()
                val hasLocation = (flags.toInt() and 0x01) != 0
                val active = (flags.toInt() and 0x02) != 0
                val hasAccuracy = (flags.toInt() and 0x04) != 0

                val severity = EmergencySeverity.fromCode(buffer.get())
                val batteryPercent = buffer.get().toInt()
                val timestamp = buffer.getLong()

                val idLen = buffer.get().toInt() and 0xFF
                if (buffer.remaining() < idLen) return null
                val idBytes = ByteArray(idLen)
                buffer.get(idBytes)
                val id = String(idBytes, Charsets.UTF_8)

                val nickLen = buffer.get().toInt() and 0xFF
                if (buffer.remaining() < nickLen) return null
                val nickBytes = ByteArray(nickLen)
                buffer.get(nickBytes)
                val senderNickname = String(nickBytes, Charsets.UTF_8)

                val noteLen = buffer.getShort().toInt() and 0xFFFF
                if (buffer.remaining() < noteLen) return null
                val noteBytes = ByteArray(noteLen)
                buffer.get(noteBytes)
                val note = String(noteBytes, Charsets.UTF_8)

                var lat: Double? = null
                var lon: Double? = null
                if (hasLocation && buffer.remaining() >= 16) {
                    lat = buffer.getDouble()
                    lon = buffer.getDouble()
                }

                var accuracy: Float? = null
                if (hasAccuracy && buffer.remaining() >= 4) {
                    accuracy = buffer.getFloat()
                }

                EmergencyBeacon(
                    id = id,
                    senderPeerID = senderPeerID,
                    senderNickname = senderNickname,
                    severity = severity,
                    latitude = lat,
                    longitude = lon,
                    accuracyMeters = accuracy,
                    batteryPercent = batteryPercent,
                    timestamp = timestamp,
                    note = note,
                    active = active
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Responder acknowledgment packet confirming that help/relief is on the way.
 */
data class EmergencyAck(
    val beaconId: String,
    val responderPeerID: String,
    val responderNickname: String,
    val responderLatitude: Double? = null,
    val responderLongitude: Double? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val message: String = "Help is on the way!"
) {
    fun encode(): ByteArray {
        val beaconIdBytes = beaconId.toByteArray(Charsets.UTF_8).take(64).toByteArray()
        val nickBytes = responderNickname.toByteArray(Charsets.UTF_8).take(64).toByteArray()
        val msgBytes = message.toByteArray(Charsets.UTF_8).take(256).toByteArray()

        val hasLocation = responderLatitude != null && responderLongitude != null
        var flags: Byte = 0
        if (hasLocation) flags = (flags.toInt() or 0x01).toByte()

        val capacity = 1 + 8 + 1 + beaconIdBytes.size + 1 + nickBytes.size + 2 + msgBytes.size +
                (if (hasLocation) 16 else 0)
        val buffer = ByteBuffer.allocate(capacity).apply { order(ByteOrder.BIG_ENDIAN) }

        buffer.put(flags)
        buffer.putLong(timestamp)

        buffer.put(beaconIdBytes.size.toByte())
        buffer.put(beaconIdBytes)

        buffer.put(nickBytes.size.toByte())
        buffer.put(nickBytes)

        buffer.putShort(msgBytes.size.toShort())
        buffer.put(msgBytes)

        if (hasLocation) {
            buffer.putDouble(responderLatitude!!)
            buffer.putDouble(responderLongitude!!)
        }

        return buffer.array()
    }

    companion object {
        fun decode(bytes: ByteArray, responderPeerID: String): EmergencyAck? {
            return try {
                if (bytes.size < 11) return null
                val buffer = ByteBuffer.wrap(bytes).apply { order(ByteOrder.BIG_ENDIAN) }

                val flags = buffer.get()
                val hasLocation = (flags.toInt() and 0x01) != 0
                val timestamp = buffer.getLong()

                val beaconIdLen = buffer.get().toInt() and 0xFF
                if (buffer.remaining() < beaconIdLen) return null
                val beaconIdBytes = ByteArray(beaconIdLen)
                buffer.get(beaconIdBytes)
                val beaconId = String(beaconIdBytes, Charsets.UTF_8)

                val nickLen = buffer.get().toInt() and 0xFF
                if (buffer.remaining() < nickLen) return null
                val nickBytes = ByteArray(nickLen)
                buffer.get(nickBytes)
                val responderNickname = String(nickBytes, Charsets.UTF_8)

                val msgLen = buffer.getShort().toInt() and 0xFFFF
                if (buffer.remaining() < msgLen) return null
                val msgBytes = ByteArray(msgLen)
                buffer.get(msgBytes)
                val message = String(msgBytes, Charsets.UTF_8)

                var lat: Double? = null
                var lon: Double? = null
                if (hasLocation && buffer.remaining() >= 16) {
                    lat = buffer.getDouble()
                    lon = buffer.getDouble()
                }

                EmergencyAck(
                    beaconId = beaconId,
                    responderPeerID = responderPeerID,
                    responderNickname = responderNickname,
                    responderLatitude = lat,
                    responderLongitude = lon,
                    timestamp = timestamp,
                    message = message
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

