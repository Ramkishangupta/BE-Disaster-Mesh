package com.bitchat.android.ui.emergency

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitchat.android.features.emergency.EmergencyManager
import com.bitchat.android.features.emergency.EmergencySeverity
import com.bitchat.android.geohash.SystemLocationProvider
import com.bitchat.android.ui.ChatViewModel

/**
 * Emergency SOS Dialog for broadcasting offline disaster beacons or
 * viewing/cancelling ongoing distress alerts.
 */
@Composable
fun EmergencySOSDialog(
    viewModel: ChatViewModel,
    onDismiss: () -> Unit
) {
    val emergencyManager = viewModel.emergencyManager
    val myActiveSos by emergencyManager.myActiveSos.collectAsStateWithLifecycle()
    val responders by emergencyManager.responders.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var selectedSeverity by remember { mutableStateOf(EmergencySeverity.CRITICAL_MEDICAL) }
    var noteText by remember { mutableStateOf("") }
    var locationInfo by remember { mutableStateOf<android.location.Location?>(null) }
    val batteryPct = remember { emergencyManager.getBatteryPercentage(context) }

    LaunchedEffect(Unit) {
        val locationProvider = SystemLocationProvider(context)
        locationProvider.getLastKnownLocation { loc ->
            locationInfo = loc
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "SOS",
                            tint = Color(0xFFFF4444),
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Disaster Mesh SOS",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Telemetry summary chip row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.BatteryChargingFull,
                                contentDescription = null,
                                tint = if (batteryPct <= 20) Color(0xFFFF4444) else Color(0xFF00C851),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (batteryPct >= 0) "$batteryPct% Battery" else "Battery N/A",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LocationOn,
                                contentDescription = null,
                                tint = if (locationInfo != null) Color(0xFF33B5E5) else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (locationInfo != null) "GPS Ready" else "No GPS Fix",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (myActiveSos != null) {
                    // USER HAS AN ACTIVE SOS BROADCASTING
                    val currentSos = myActiveSos!!
                    val ackList = responders[currentSos.id] ?: emptyList()

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(2.dp, Color(0xFFFF4444), RoundedCornerShape(10.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color(0x22FF4444)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Text(
                                text = "🚨 DISTRESS BEACON ACTIVE",
                                color = Color(0xFFFF4444),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Triage: ${currentSos.severity.title}",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            if (currentSos.note.isNotEmpty()) {
                                Text(
                                    text = "\"${currentSos.note}\"",
                                    fontSize = 12.sp,
                                    color = Color.LightGray
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (ackList.isEmpty()) {
                                    "Pinging mesh network... No responders yet."
                                } else {
                                    "✓ ${ackList.size} responder(s) acknowledged:\n" +
                                            ackList.joinToString("\n") { "• @${it.responderNickname} (${it.message})" }
                                },
                                fontSize = 12.sp,
                                color = if (ackList.isEmpty()) Color(0xFFFFBB33) else Color(0xFF00C851),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            viewModel.cancelSos()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C851)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "I AM SAFE / CANCEL SOS",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                } else {
                    // USER CAN TRIGGER A NEW SOS
                    Text(
                        text = "Select Triage Severity:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    EmergencySeverity.entries.forEach { severity ->
                        val isSelected = selectedSeverity == severity
                        val cardColor = when (severity) {
                            EmergencySeverity.CRITICAL_MEDICAL -> Color(0xFFFF4444)
                            EmergencySeverity.TRAPPED_SEARCH_RESCUE -> Color(0xFFFF8800)
                            EmergencySeverity.HAZARD_WARNING -> Color(0xFFFFBB33)
                            EmergencySeverity.SUPPLIES_WATER -> Color(0xFF33B5E5)
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clickable { selectedSeverity = severity }
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) cardColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(8.dp)
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) cardColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedSeverity = severity },
                                    colors = RadioButtonDefaults.colors(selectedColor = cardColor)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = severity.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (isSelected) cardColor else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = severity.description,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Optional Note TextField
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text("Details / Note (optional)") },
                        placeholder = { Text("e.g. 2 people, 2nd floor, leg fracture") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2,
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Broadcast Button
                    Button(
                        onClick = {
                            viewModel.broadcastSos(selectedSeverity, noteText)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4444)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "BROADCAST DISTRESS SOS",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

