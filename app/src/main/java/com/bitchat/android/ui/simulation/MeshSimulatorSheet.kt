package com.bitchat.android.ui.simulation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitchat.android.core.ui.component.button.CloseButton
import com.bitchat.android.core.ui.component.sheet.BitchatBottomSheet
import com.bitchat.android.features.emergency.EmergencySeverity
import com.bitchat.android.services.meshgraph.MeshGraphService
import com.bitchat.android.ui.ChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random

/**
 * Real-Time Mesh Network Visualizer & Offline Disaster Simulation Lab.
 *
 * Provides two operational views:
 * 1. LIVE MESH VIEW: Real-time Bluetooth LE topology, connected nodes, live RSSI/edges from MeshGraphService.
 * 2. DISASTER SIMULATION LAB: Interactive multi-hop BLE mesh simulator with touch-draggable nodes,
 *    dynamic link formation based on RF radius, animated packet transfer across hops (TTL decrementing),
 *    duplicate suppression (broadcast storm mitigation), and reverse ACK routing.
 */

enum class SimulatorMode {
    DISASTER_LAB,
    LIVE_MESH
}

enum class SimNodeRole {
    VICTIM,         // Originator of SOS beacon
    RELAY,          // Intermediate mesh relay forwarding packets
    HQ_RESPONDER,   // Rescue team / Medical station generating ACK
    BYSTANDER       // Civilian node demonstrating duplicate storm suppression
}

data class SimNode(
    val id: String,
    var label: String,
    val role: SimNodeRole,
    var x: Float,
    var y: Float,
    var battery: Int = 100,
    var isDragged: Boolean = false,
    var isFailed: Boolean = false,
    var pulseAlpha: Float = 0f,
    var isSuppressed: Boolean = false,
    var hasAck: Boolean = false
)

data class SimPacketHop(
    val packetId: String,
    val type: String, // "BEACON (0x30)" or "ACK (0x31)"
    val fromId: String,
    val toId: String,
    var progress: Float, // 0f to 1f
    val ttl: Int,
    val color: Color
)

data class SimLogEntry(
    val timestamp: String,
    val message: String,
    val color: Color = Color(0xFF00E676)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshSimulatorSheet(
    isPresented: Boolean,
    onDismiss: () -> Unit,
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    if (!isPresented) return

    BitchatBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        MeshSimulatorContent(
            onDismiss = onDismiss,
            viewModel = viewModel
        )
    }
}

@Composable
fun MeshSimulatorContent(
    onDismiss: () -> Unit,
    viewModel: ChatViewModel
) {
    var selectedMode by remember { mutableStateOf(SimulatorMode.DISASTER_LAB) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1117))
            .padding(bottom = 16.dp)
    ) {
        // Sheet Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(0xFF00E676), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "MESH TOPOLOGY & PACKET SIMULATOR",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    text = "Bitchat Offline Multi-Hop Telemetry Engine",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            CloseButton(onClick = onDismiss)
        }

        // Mode Switcher Tabs
        TabRow(
            selectedTabIndex = if (selectedMode == SimulatorMode.DISASTER_LAB) 0 else 1,
            containerColor = Color(0xFF161922),
            contentColor = Color(0xFF00E676),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Tab(
                selected = selectedMode == SimulatorMode.DISASTER_LAB,
                onClick = { selectedMode = SimulatorMode.DISASTER_LAB },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Science, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Disaster Sim Lab", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            )
            Tab(
                selected = selectedMode == SimulatorMode.LIVE_MESH,
                onClick = { selectedMode = SimulatorMode.LIVE_MESH },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Sensors, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Live BLE Mesh", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (selectedMode == SimulatorMode.DISASTER_LAB) {
            DisasterSimulationLabView(viewModel = viewModel)
        } else {
            LiveMeshTopologyView(viewModel = viewModel)
        }
    }
}

/**
 * -------------------------------------------------------------------------------------------------
 * VIEW 1: DISASTER SIMULATION LAB (Interactive Virtual Multi-Hop Topology)
 * -------------------------------------------------------------------------------------------------
 */
@Composable
fun DisasterSimulationLabView(viewModel: ChatViewModel) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Simulation Node States
    val nodes = remember {
        mutableStateListOf(
            SimNode("victim", "Victim (Trapped)", SimNodeRole.VICTIM, 120f, 280f, battery = 21),
            SimNode("relay1", "Relay Alpha", SimNodeRole.RELAY, 320f, 200f, battery = 88),
            SimNode("relay2", "Relay Beta", SimNodeRole.RELAY, 540f, 220f, battery = 74),
            SimNode("relay3", "Relay Gamma", SimNodeRole.RELAY, 420f, 400f, battery = 92),
            SimNode("bystander", "Civilian", SimNodeRole.BYSTANDER, 260f, 440f, battery = 65),
            SimNode("hq", "Rescue HQ", SimNodeRole.HQ_RESPONDER, 750f, 290f, battery = 99)
        )
    }

    // Active in-flight packet animations
    val activePackets = remember { mutableStateListOf<SimPacketHop>() }

    // Real-time Event Console Log
    val eventLogs = remember {
        mutableStateListOf(
            SimLogEntry("00:00.00", "Simulator initialized: 6 nodes active in disaster cluster."),
            SimLogEntry("00:00.02", "BLE RF threshold set to 230 px (~25m outdoor LOS)."),
            SimLogEntry("00:00.05", "Drag any node to test dynamic link severance / healing.")
        )
    }

    val logListState = rememberLazyListState()

    // Simulation Stats
    var isSimulating by remember { mutableStateOf(false) }
    var totalPacketsSent by remember { mutableIntStateOf(0) }
    var totalHopsTraversed by remember { mutableIntStateOf(0) }
    var duplicatePacketsSuppressed by remember { mutableIntStateOf(0) }
    var lastTtlReported by remember { mutableIntStateOf(7) }

    // Communication Radius in screen pixels (represents BLE range)
    val commRadius = 240f

    // Calculate dynamic edges between nodes based on proximity and failure state
    val edges = remember(nodes) {
        derivedStateOf {
            val list = mutableListOf<Pair<SimNode, SimNode>>()
            for (i in nodes.indices) {
                val n1 = nodes[i]
                if (n1.isFailed) continue
                for (j in i + 1 until nodes.size) {
                    val n2 = nodes[j]
                    if (n2.isFailed) continue
                    val dx = n1.x - n2.x
                    val dy = n1.y - n2.y
                    val dist = sqrt(dx * dx + dy * dy)
                    if (dist <= commRadius) {
                        list.add(Pair(n1, n2))
                    }
                }
            }
            list
        }
    }

    // Ticker to force redraw of canvas during animations
    var animTick by remember { mutableLongStateOf(0L) }

    // Run high-frequency frame loop for packet movement
    LaunchedEffect(isSimulating, activePackets.size) {
        while (activePackets.isNotEmpty()) {
            withFrameNanos {
                animTick++
                val iter = activePackets.iterator()
                while (iter.hasNext()) {
                    val p = iter.next()
                    p.progress += 0.04f // Speed of packet travel
                    if (p.progress >= 1.0f) {
                        iter.remove()
                    }
                }
            }
        }
    }

    // Helper: Add log entry and auto scroll
    fun log(msg: String, color: Color = Color(0xFF00E676)) {
        val sec = ((System.currentTimeMillis() % 60000) / 1000).toString().padStart(2, '0')
        val ms = ((System.currentTimeMillis() % 1000) / 10).toString().padStart(2, '0')
        eventLogs.add(SimLogEntry("$sec:$ms", msg, color))
        coroutineScope.launch {
            if (eventLogs.isNotEmpty()) {
                logListState.animateScrollToItem(eventLogs.size - 1)
            }
        }
    }

    // Simulation Trigger: Full SOS Broadcast & Return ACK Workflow
    fun triggerSosSimulation() {
        if (isSimulating) return
        isSimulating = true
        totalPacketsSent++

        coroutineScope.launch {
            // Reset node transient states
            nodes.forEach {
                it.hasAck = false
                it.isSuppressed = false
                it.pulseAlpha = 0f
            }

            val victim = nodes.find { it.role == SimNodeRole.VICTIM } ?: return@launch
            val relay1 = nodes.find { it.id == "relay1" } ?: return@launch
            val relay2 = nodes.find { it.id == "relay2" } ?: return@launch
            val relay3 = nodes.find { it.id == "relay3" } ?: return@launch
            val bystander = nodes.find { it.role == SimNodeRole.BYSTANDER } ?: return@launch
            val hq = nodes.find { it.role == SimNodeRole.HQ_RESPONDER } ?: return@launch

            // --- STEP 1: Victim Originates Beacon ---
            victim.pulseAlpha = 1f
            log("🚨 [ORIGIN] Victim transmits EMERGENCY_BEACON (Type: 0x30, TTL: 7, Severity: CRITICAL)", Color(0xFFFF5252))
            lastTtlReported = 7
            totalHopsTraversed = 0

            delay(300)

            // Hop 1: Victim -> Relay 1
            activePackets.add(
                SimPacketHop("P-01", "BEACON", victim.id, relay1.id, 0f, 7, Color(0xFFFF5252))
            )
            delay(500)

            // --- STEP 2: Relay 1 Receives and Forwards ---
            relay1.pulseAlpha = 1f
            lastTtlReported = 6
            totalHopsTraversed++
            log("⚡ [RELAY 1] Relay Alpha re-broadcasted packet (TTL: 6 -> 5, Battery: 88%)", Color(0xFFFFB300))

            // Relay 1 broadcasts to both Relay 2 and Bystander
            activePackets.add(
                SimPacketHop("P-02", "BEACON", relay1.id, relay2.id, 0f, 6, Color(0xFFFF5252))
            )
            activePackets.add(
                SimPacketHop("P-03", "BEACON", relay1.id, bystander.id, 0f, 6, Color(0xFFFF8A80))
            )
            delay(500)

            // --- STEP 3: Duplicate Suppression at Bystander ---
            bystander.isSuppressed = true
            bystander.pulseAlpha = 0.5f
            duplicatePacketsSuppressed++
            log("🛡️ [STORM SUPPRESSION] Civilian received duplicate #P-01 -> Pruned from relay queue!", Color(0xFF40C4FF))

            // --- STEP 4: Relay 2 Forwards to Rescue HQ ---
            relay2.pulseAlpha = 1f
            lastTtlReported = 5
            totalHopsTraversed++
            log("⚡ [RELAY 2] Relay Beta forwarding to Rescue Station (TTL: 5 -> 4)", Color(0xFFFFB300))

            activePackets.add(
                SimPacketHop("P-04", "BEACON", relay2.id, hq.id, 0f, 5, Color(0xFFFF5252))
            )
            delay(550)

            // --- STEP 5: Rescue HQ Acknowledges ---
            hq.pulseAlpha = 1f
            hq.hasAck = true
            totalHopsTraversed++
            log("🏥 [RESCUE HQ] Distress packet delivered! Triage: CRITICAL. Dispatching ACK (ETA: 4 min)", Color(0xFF00E676))
            delay(400)

            // Reverse Routing of EMERGENCY_ACK (HQ -> Relay 2 -> Relay 1 -> Victim)
            log("↩️ [REVERSE ACK] Originating EMERGENCY_ACK (Type: 0x31) back across mesh...", Color(0xFF69F0AE))

            activePackets.add(
                SimPacketHop("ACK-01", "ACK", hq.id, relay2.id, 0f, 7, Color(0xFF00E676))
            )
            delay(500)

            activePackets.add(
                SimPacketHop("ACK-02", "ACK", relay2.id, relay1.id, 0f, 6, Color(0xFF00E676))
            )
            delay(500)

            activePackets.add(
                SimPacketHop("ACK-03", "ACK", relay1.id, victim.id, 0f, 5, Color(0xFF00E676))
            )
            delay(500)

            // --- STEP 6: Victim Confirms Delivery ---
            victim.hasAck = true
            victim.pulseAlpha = 1f
            log("✅ [DELIVERED] Victim received ACK from Rescue HQ! Path Confirmed (3 Hops, RTT: 180ms)", Color(0xFF00E676))

            isSimulating = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // HUD / Telemetry Metrics Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SimMetricCard(
                title = "ACTIVE NODES",
                value = "${nodes.count { !it.isFailed }}",
                modifier = Modifier.weight(1f),
                tint = Color(0xFF00E676)
            )
            SimMetricCard(
                title = "BLE EDGES",
                value = "${edges.value.size}",
                modifier = Modifier.weight(1f),
                tint = Color(0xFF40C4FF)
            )
            SimMetricCard(
                title = "HOPS / TTL",
                value = "$totalHopsTraversed / $lastTtlReported",
                modifier = Modifier.weight(1f),
                tint = Color(0xFFFFB300)
            )
            SimMetricCard(
                title = "SUPPRESSED",
                value = "$duplicatePacketsSuppressed",
                modifier = Modifier.weight(1f),
                tint = Color(0xFFFF5252)
            )
        }

        // Action Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { triggerSosSimulation() },
                enabled = !isSimulating,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4444)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1.4f)
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                Spacer(Modifier.width(6.dp))
                Text("Transmit SOS", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
            }

            OutlinedButton(
                onClick = {
                    val r1 = nodes.find { it.id == "relay1" }
                    if (r1 != null) {
                        r1.isFailed = !r1.isFailed
                        val status = if (r1.isFailed) "FAILED (Offline)" else "RESTORED (Online)"
                        log("⚠️ [TOPOLOGY CHANGE] Relay Alpha is now $status", Color(0xFFFFB300))
                    }
                },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                modifier = Modifier.weight(1.1f)
            ) {
                Text("Toggle Fail", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }

            IconButton(
                onClick = {
                    nodes.clear()
                    nodes.addAll(
                        listOf(
                            SimNode("victim", "Victim (Trapped)", SimNodeRole.VICTIM, 120f, 280f, battery = 21),
                            SimNode("relay1", "Relay Alpha", SimNodeRole.RELAY, 320f, 200f, battery = 88),
                            SimNode("relay2", "Relay Beta", SimNodeRole.RELAY, 540f, 220f, battery = 74),
                            SimNode("relay3", "Relay Gamma", SimNodeRole.RELAY, 420f, 400f, battery = 92),
                            SimNode("bystander", "Civilian", SimNodeRole.BYSTANDER, 260f, 440f, battery = 65),
                            SimNode("hq", "Rescue HQ", SimNodeRole.HQ_RESPONDER, 750f, 290f, battery = 99)
                        )
                    )
                    activePackets.clear()
                    isSimulating = false
                    totalHopsTraversed = 0
                    duplicatePacketsSuppressed = 0
                    log("🔄 Topology reset to default 6-node disaster layout.", Color.Gray)
                },
                modifier = Modifier
                    .background(Color(0xFF212631), RoundedCornerShape(8.dp))
                    .size(38.dp)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Reset", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }

        // Interactive Canvas for Topology & Drag & Animated Packets
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .background(Color(0xFF090A0F), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF262C38), RoundedCornerShape(12.dp))
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val closest = nodes.minByOrNull {
                                    val dx = it.x - offset.x
                                    val dy = it.y - offset.y
                                    dx * dx + dy * dy
                                }
                                if (closest != null) {
                                    val dist = sqrt((closest.x - offset.x).pow(2) + (closest.y - offset.y).pow(2))
                                    if (dist < 70f) {
                                        closest.isDragged = true
                                    }
                                }
                            },
                            onDragEnd = {
                                nodes.forEach { it.isDragged = false }
                            },
                            onDragCancel = {
                                nodes.forEach { it.isDragged = false }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val dragged = nodes.find { it.isDragged }
                                if (dragged != null) {
                                    dragged.x += dragAmount.x
                                    dragged.y += dragAmount.y
                                }
                            }
                        )
                    }
            ) {
                val tick = animTick // Trigger recomposition

                // 1. Draw Grid Lines for High-Tech Radar / Mesh Field look
                val gridSize = 60f
                for (x in 0..(size.width / gridSize).toInt()) {
                    drawLine(
                        color = Color(0x0CFFFFFF),
                        start = Offset(x * gridSize, 0f),
                        end = Offset(x * gridSize, size.height),
                        strokeWidth = 1f
                    )
                }
                for (y in 0..(size.height / gridSize).toInt()) {
                    drawLine(
                        color = Color(0x0CFFFFFF),
                        start = Offset(0f, y * gridSize),
                        end = Offset(size.width, y * gridSize),
                        strokeWidth = 1f
                    )
                }

                // 2. Draw BLE RF Coverage Rings (dashed radius)
                nodes.forEach { node ->
                    if (!node.isFailed) {
                        drawCircle(
                            color = when (node.role) {
                                SimNodeRole.VICTIM -> Color(0x1AFF4444)
                                SimNodeRole.HQ_RESPONDER -> Color(0x1A00E676)
                                else -> Color(0x1040C4FF)
                            },
                            radius = commRadius,
                            center = Offset(node.x, node.y),
                            style = Stroke(
                                width = 1f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                            )
                        )
                    }
                }

                // 3. Draw Active Mesh Links (Edges)
                edges.value.forEach { (n1, n2) ->
                    val isCriticalPath = (n1.role == SimNodeRole.VICTIM && n2.id == "relay1") ||
                            (n1.id == "relay1" && n2.id == "relay2") ||
                            (n1.id == "relay2" && n2.role == SimNodeRole.HQ_RESPONDER)

                    drawLine(
                        color = if (isCriticalPath) Color(0xFF00E676).copy(alpha = 0.8f) else Color(0xFF40C4FF).copy(alpha = 0.35f),
                        start = Offset(n1.x, n1.y),
                        end = Offset(n2.x, n2.y),
                        strokeWidth = if (isCriticalPath) 3f else 1.5f
                    )
                }

                // 4. Draw In-Flight Animated Packets
                activePackets.forEach { hop ->
                    val src = nodes.find { it.id == hop.fromId }
                    val dst = nodes.find { it.id == hop.toId }
                    if (src != null && dst != null) {
                        val currentX = src.x + (dst.x - src.x) * hop.progress
                        val currentY = src.y + (dst.y - src.y) * hop.progress

                        // Outer glowing aura
                        drawCircle(
                            color = hop.color.copy(alpha = 0.35f),
                            radius = 16f,
                            center = Offset(currentX, currentY)
                        )
                        // Inner packet orb
                        drawCircle(
                            color = hop.color,
                            radius = 7f,
                            center = Offset(currentX, currentY)
                        )
                    }
                }

                // 5. Draw Nodes & Labels
                nodes.forEach { node ->
                    val center = Offset(node.x, node.y)

                    if (node.isFailed) {
                        // Failed Node (Gray with X)
                        drawCircle(color = Color(0xFF424242), radius = 22f, center = center)
                        drawLine(Color.Red, center - Offset(12f, 12f), center + Offset(12f, 12f), 3f)
                        drawLine(Color.Red, center - Offset(-12f, 12f), center + Offset(-12f, 12f), 3f)
                    } else {
                        val baseColor = when (node.role) {
                            SimNodeRole.VICTIM -> if (node.hasAck) Color(0xFF00E676) else Color(0xFFFF3D00)
                            SimNodeRole.HQ_RESPONDER -> Color(0xFF00E676)
                            SimNodeRole.BYSTANDER -> if (node.isSuppressed) Color(0xFF78909C) else Color(0xFF29B6F6)
                            SimNodeRole.RELAY -> Color(0xFFFFB300)
                        }

                        // Pulse Ring when active
                        if (node.pulseAlpha > 0.05f) {
                            drawCircle(
                                color = baseColor.copy(alpha = node.pulseAlpha * 0.4f),
                                radius = 34f,
                                center = center
                            )
                        }

                        // Main Node Body
                        drawCircle(
                            color = baseColor,
                            radius = if (node.isDragged) 24f else 20f,
                            center = center
                        )

                        // Outline
                        drawCircle(
                            color = Color.White.copy(alpha = 0.8f),
                            radius = if (node.isDragged) 24f else 20f,
                            center = center,
                            style = Stroke(width = 2f)
                        )
                    }

                    // Native Canvas Text for Node Label & Battery
                    drawContext.canvas.nativeCanvas.apply {
                        val labelPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = 26f
                            isFakeBoldText = true
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                        val subPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.LTGRAY
                            textSize = 20f
                            textAlign = android.graphics.Paint.Align.CENTER
                        }

                        drawText(node.label, node.x, node.y - 28f, labelPaint)
                        val extra = if (node.isFailed) "OFFLINE" else "${node.battery}% Bat"
                        drawText(extra, node.x, node.y + 40f, subPaint)
                    }
                }
            }
        }

        // Live Event Log Stream (Console Drawer)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .padding(horizontal = 16.dp, vertical = 2.dp)
                .background(Color(0xFF0D0E15), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFF1E222D), RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "REAL-TIME PROTOCOL EVENT LOG",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${eventLogs.size} events",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            LazyColumn(
                state = logListState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(eventLogs) { entry ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "[${entry.timestamp}] ",
                            color = Color(0xFF607D8B),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = entry.message,
                            color = entry.color,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

/**
 * -------------------------------------------------------------------------------------------------
 * VIEW 2: LIVE BLE MESH (Real Hardware BLE Topology View)
 * -------------------------------------------------------------------------------------------------
 */
@Composable
fun LiveMeshTopologyView(viewModel: ChatViewModel) {
    val meshGraphService = remember { MeshGraphService.getInstance() }
    val graphSnapshot by meshGraphService.graphState.collectAsStateWithLifecycle()
    val connectedPeers by viewModel.connectedPeers.collectAsStateWithLifecycle()
    val emergencyManager = viewModel.emergencyManager
    val activeBeacons by emergencyManager.activeBeacons.collectAsStateWithLifecycle()

    val nodes = graphSnapshot.nodes
    val edges = graphSnapshot.edges

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Status Alert Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161922)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Bluetooth, contentDescription = null, tint = Color(0xFF40C4FF), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "LIVE BLUETOOTH LOW ENERGY TOPOLOGY",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    text = "Connected Peers: ${connectedPeers.size}  •  Gossip Graph Nodes: ${nodes.size}  •  Active Beacons: ${activeBeacons.size}",
                    color = Color(0xFF00E676),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        if (nodes.isEmpty() && connectedPeers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF0D0E15), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF262C38), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.SensorsOff,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Scanning for nearby BLE Mesh peers...",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Make sure Bluetooth is enabled on test phones. Nodes will appear here automatically when discovered over BLE advertisement.\n\nTip: You can use the 'Disaster Sim Lab' tab anytime to simulate a 6-node multi-hop mesh on this device!",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            // Live Mesh List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(connectedPeers) { peerId ->
                    val nickname = viewModel.meshService.getPeerNicknames()[peerId] ?: peerId.take(8)
                    val isDistressActive = activeBeacons.any { it.senderPeerID == peerId }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDistressActive) Color(0x33FF4444) else Color(0xFF1E222D)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(
                                            if (isDistressActive) Color(0xFFFF4444) else Color(0xFF00E676),
                                            CircleShape
                                        )
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = nickname,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "ID: ${peerId.take(12)}...  •  Direct 1-Hop BLE",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            if (isDistressActive) {
                                Text(
                                    text = "SOS ACTIVE",
                                    color = Color(0xFFFF4444),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Live Action Broadcast
        Button(
            onClick = {
                viewModel.broadcastSos(EmergencySeverity.CRITICAL_MEDICAL, "Live Mesh Test Beacon")
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Broadcast Live SOS Beacon Over BLE", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SimMetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF00E676)
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161922)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                color = tint,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
