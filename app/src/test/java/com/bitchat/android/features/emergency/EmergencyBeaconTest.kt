package com.bitchat.android.features.emergency

import com.bitchat.android.protocol.BinaryProtocol
import com.bitchat.android.protocol.BitchatPacket
import com.bitchat.android.protocol.MessageType
import com.bitchat.android.protocol.SpecialRecipients
import org.junit.Assert.*
import org.junit.Test

class EmergencyBeaconTest {

    private val samplePeerID = "1122334455667788"
    private val responderPeerID = "8877665544332211"

    @Test
    fun `test emergency beacon with full telemetry round trips`() {
        val beacon = EmergencyBeacon(
            id = "EMERGENCY-UUID-1234",
            senderPeerID = samplePeerID,
            senderNickname = "VictimAlice",
            severity = EmergencySeverity.CRITICAL_MEDICAL,
            latitude = 12.971598,
            longitude = 77.594562,
            accuracyMeters = 8.5f,
            batteryPercent = 42,
            timestamp = 1700000000000L,
            note = "Severe bleeding, trapped in elevator",
            active = true
        )

        val encoded = beacon.encode()
        assertTrue("Encoded payload should not be empty", encoded.isNotEmpty())

        val decoded = EmergencyBeacon.decode(encoded, samplePeerID)
        assertNotNull("Decoded beacon should not be null", decoded)
        assertEquals(beacon.id, decoded!!.id)
        assertEquals(beacon.senderPeerID, decoded.senderPeerID)
        assertEquals(beacon.senderNickname, decoded.senderNickname)
        assertEquals(beacon.severity, decoded.severity)
        assertEquals(beacon.latitude!!, decoded.latitude!!, 0.00001)
        assertEquals(beacon.longitude!!, decoded.longitude!!, 0.00001)
        assertEquals(beacon.accuracyMeters!!, decoded.accuracyMeters!!, 0.01f)
        assertEquals(beacon.batteryPercent, decoded.batteryPercent)
        assertEquals(beacon.timestamp, decoded.timestamp)
        assertEquals(beacon.note, decoded.note)
        assertTrue("Beacon should be active", decoded.active)
    }

    @Test
    fun `test emergency beacon without location round trips`() {
        val beacon = EmergencyBeacon(
            id = "EMERGENCY-UUID-5678",
            senderPeerID = samplePeerID,
            senderNickname = "TrappedBob",
            severity = EmergencySeverity.TRAPPED_SEARCH_RESCUE,
            latitude = null,
            longitude = null,
            accuracyMeters = null,
            batteryPercent = 15,
            timestamp = 1700000001000L,
            note = "Basement collapsed, no GPS signal",
            active = true
        )

        val encoded = beacon.encode()
        val decoded = EmergencyBeacon.decode(encoded, samplePeerID)

        assertNotNull(decoded)
        assertNull("Latitude should be null", decoded!!.latitude)
        assertNull("Longitude should be null", decoded.longitude)
        assertNull("Accuracy should be null", decoded.accuracyMeters)
        assertEquals(15, decoded.batteryPercent)
        assertEquals(EmergencySeverity.TRAPPED_SEARCH_RESCUE, decoded.severity)
        assertEquals("Basement collapsed, no GPS signal", decoded.note)
    }

    @Test
    fun `test emergency beacon cancellation flag`() {
        val beacon = EmergencyBeacon(
            id = "EMERGENCY-UUID-CANCEL",
            senderPeerID = samplePeerID,
            senderNickname = "SafeCharlie",
            severity = EmergencySeverity.SUPPLIES_WATER,
            latitude = 25.1234,
            longitude = 80.5678,
            batteryPercent = 88,
            timestamp = 1700000002000L,
            note = "Rescued, all safe",
            active = false
        )

        val encoded = beacon.encode()
        val decoded = EmergencyBeacon.decode(encoded, samplePeerID)

        assertNotNull(decoded)
        assertFalse("Beacon active flag must be false", decoded!!.active)
    }

    @Test
    fun `test all severity categories map correctly`() {
        val severities = listOf(
            EmergencySeverity.CRITICAL_MEDICAL,
            EmergencySeverity.TRAPPED_SEARCH_RESCUE,
            EmergencySeverity.HAZARD_WARNING,
            EmergencySeverity.SUPPLIES_WATER
        )

        for (sev in severities) {
            val fromCode = EmergencySeverity.fromCode(sev.code)
            assertEquals("Severity code mapping failed for $sev", sev, fromCode)
        }
    }

    @Test
    fun `test emergency responder ack round trips`() {
        val ack = EmergencyAck(
            beaconId = "EMERGENCY-UUID-1234",
            responderPeerID = responderPeerID,
            responderNickname = "RescueMedicDan",
            responderLatitude = 12.972000,
            responderLongitude = 77.595000,
            timestamp = 1700000050000L,
            message = "En route with medical kit, 2 minutes out"
        )

        val encoded = ack.encode()
        val decoded = EmergencyAck.decode(encoded, responderPeerID)

        assertNotNull(decoded)
        assertEquals(ack.beaconId, decoded!!.beaconId)
        assertEquals(ack.responderPeerID, decoded.responderPeerID)
        assertEquals(ack.responderNickname, decoded.responderNickname)
        assertEquals(ack.responderLatitude!!, decoded.responderLatitude!!, 0.00001)
        assertEquals(ack.responderLongitude!!, decoded.responderLongitude!!, 0.00001)
        assertEquals(ack.message, decoded.message)
    }

    @Test
    fun `test emergency packet integration with binary protocol`() {
        val beacon = EmergencyBeacon(
            id = "EMERGENCY-UUID-PROTO",
            senderPeerID = samplePeerID,
            senderNickname = "Alice",
            severity = EmergencySeverity.HAZARD_WARNING,
            note = "Gas leak in block B",
            active = true
        )

        val payload = beacon.encode()
        val packet = BitchatPacket(
            version = 1u,
            type = MessageType.EMERGENCY_BEACON.value,
            senderID = ByteArray(8) { 0x11 },
            recipientID = SpecialRecipients.BROADCAST,
            timestamp = 1700000000000uL,
            payload = payload,
            ttl = 7u
        )

        val encodedPacket = BinaryProtocol.encode(packet)
        assertNotNull("BinaryProtocol should encode emergency packet", encodedPacket)

        val decodedPacket = BinaryProtocol.decode(encodedPacket!!)
        assertNotNull("BinaryProtocol should decode emergency packet", decodedPacket)
        assertEquals(MessageType.EMERGENCY_BEACON.value, decodedPacket!!.type)
        assertEquals(7u.toUByte(), decodedPacket.ttl)

        // Verify payload inside decoded packet unpacks to EmergencyBeacon
        val decodedBeacon = EmergencyBeacon.decode(decodedPacket.payload, samplePeerID)
        assertNotNull(decodedBeacon)
        assertEquals(EmergencySeverity.HAZARD_WARNING, decodedBeacon!!.severity)
        assertEquals("Gas leak in block B", decodedBeacon.note)
    }
}

