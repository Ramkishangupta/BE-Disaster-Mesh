package com.bitchat.android.ui.emergency

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitchat.android.features.emergency.EmergencyBeacon
import com.bitchat.android.features.emergency.EmergencyManager
import com.bitchat.android.features.emergency.EmergencySeverity
import com.bitchat.android.geohash.SystemLocationProvider
import com.bitchat.android.ui.ChatViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * Top alert banner showing active distress alerts across the BLE mesh,
 * or status of the user's ongoing SOS broadcast.
 */
@Composable
fun EmergencyAlertBanner(
    viewModel: ChatViewModel,
    onOpenSosDialog: () -> Unit,
    onOpenSimulator: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val emergencyManager = viewModel.emergencyManager
    val activeBeacons by emergencyManager.activeBeacons.collectAsStateWithLifecycle()
    val myActiveSos by emergencyManager.myActiveSos.collectAsStateWithLifecycle()
    val responders by emergencyManager.responders.collectAsStateWithLifecycle()
    val respondedBeaconIds by emergencyManager.respondedBeaconIds.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var myLocation by remember { mutableStateOf<android.location.Location?>(null) }

    LaunchedEffect(Unit) {
        val locationProvider = SystemLocationProvider(context)
        locationProvider.getLastKnownLocation { loc ->
            myLocation = loc
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 1. My Own Outgoing Active SOS Status Strip
        myActiveSos?.let { mySos ->
            val ackList = responders[mySos.id] ?: emptyList()
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, Color(0xFFFF4444), RoundedCornerShape(8.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0x33FF4444)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "Active SOS",
                            tint = Color(0xFFFF4444),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "🚨 DISASTER SOS BROADCASTING",
                                color = Color(0xFFFF4444),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            val responderText = if (ackList.isEmpty()) {
                                "Waiting for nearby responders..."
                            } else {
                                "✓ ${ackList.size} responder(s) incoming: " +
                                        ackList.joinToString(", ") { it.responderNickname }
                            }
                            Text(
                                text = responderText,
                                color = if (ackList.isEmpty()) Color(0xFFFFBB33) else Color(0xFF00C851),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = onOpenSimulator,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = "Simulate",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = { viewModel.cancelSos() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C851)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = "I am Safe",
                                color = Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 2. Incoming Distress Beacons from Nearby Mesh Peers
        activeBeacons.forEach { beacon ->
            val ackList = responders[beacon.id] ?: emptyList()
            val hasResponded = respondedBeaconIds.contains(beacon.id)

            val distanceMeters = remember(myLocation, beacon.latitude, beacon.longitude) {
                emergencyManager.calculateDistanceMeters(
                    myLocation?.latitude,
                    myLocation?.longitude,
                    beacon.latitude,
                    beacon.longitude
                )
            }
            val distanceStr = emergencyManager.formatDistance(distanceMeters)

            IncomingSosCard(
                beacon = beacon,
                distanceStr = distanceStr,
                ackListSize = ackList.size,
                hasResponded = hasResponded,
                onRespond = {
                    viewModel.respondToSos(beacon)
                }
            )
        }
    }
}

@Composable
private fun IncomingSosCard(
    beacon: EmergencyBeacon,
    distanceStr: String,
    ackListSize: Int,
    hasResponded: Boolean,
    onRespond: () -> Unit
) {
    val borderColor = when (beacon.severity) {
        EmergencySeverity.CRITICAL_MEDICAL -> Color(0xFFFF4444)
        EmergencySeverity.TRAPPED_SEARCH_RESCUE -> Color(0xFFFF8800)
        EmergencySeverity.HAZARD_WARNING -> Color(0xFFFFBB33)
        EmergencySeverity.SUPPLIES_WATER -> Color(0xFF33B5E5)
    }

    val timeFormatted = remember(beacon.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(beacon.timestamp))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, borderColor, RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0x22111111)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            // Header Row: Severity badge, sender, time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = borderColor,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = " " + beacon.severity.title.uppercase() + " ",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "@${beacon.senderNickname}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Text(
                    text = timeFormatted,
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Metadata Row: Distance, Battery, GPS coords
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📍 $distanceStr",
                    color = Color(0xFF33B5E5),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                if (beacon.batteryPercent >= 0) {
                    Text(
                        text = "⚡ ${beacon.batteryPercent}% batt",
                        color = if (beacon.batteryPercent <= 20) Color(0xFFFF4444) else Color(0xFF00C851),
                        fontSize = 11.sp
                    )
                }
                if (beacon.latitude != null && beacon.longitude != null) {
                    Text(
                        text = "(${String.format("%.4f", beacon.latitude)}, ${String.format("%.4f", beacon.longitude)})",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                }
            }

            // Note (if any)
            if (beacon.note.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "\"${beacon.note}\"",
                    color = Color(0xFFEEEEEE),
                    fontSize = 12.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Footer: Responder count and Action Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val responderText = if (ackListSize > 0) {
                    "✓ $ackListSize responder(s) en route"
                } else {
                    "No responders yet"
                }
                Text(
                    text = responderText,
                    color = if (ackListSize > 0) Color(0xFF00C851) else Color(0xFFFFBB33),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )

                if (hasResponded) {
                    Surface(
                        color = Color(0x3300C851),
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00C851))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = Color(0xFF00C851),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Responded",
                                color = Color(0xFF00C851),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = onRespond,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4444)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Handshake,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "I am Responding",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

