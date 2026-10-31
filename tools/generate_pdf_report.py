import os
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.patches as patches
from reportlab.lib.pagesizes import letter
from reportlab.lib import colors
from reportlab.lib.units import inch
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, Image, Table, TableStyle, PageBreak, KeepTogether, HRFlowable
)
from reportlab.pdfgen import canvas

os.makedirs('docs/diagrams', exist_ok=True)

# ==========================================
# 1. GENERATE DIAGRAM 1: LAYERED ARCHITECTURE
# ==========================================
def generate_architecture_diagram():
    fig, ax = plt.subplots(figsize=(10, 6), dpi=300)
    fig.patch.set_facecolor('#0B0E14')
    ax.set_facecolor('#0B0E14')
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 100)
    ax.axis('off')

    layers = [
        ("PRESENTATION LAYER (Jetpack Compose)", 
         "ChatScreen  •  ChatHeader (SOS / Radar)  •  EmergencySOSDialog (Triage)  •  EmergencyAlertBanner  •  MeshSimulatorSheet",
         84, "#1E3A8A", "#60A5FA", 12),
        ("STATE & COORDINATION LAYER", 
         "ChatViewModel  •  ChatState  •  EmergencyManager (Singleton: GPS, Morse Vibrator, 20s Periodic Rebroadcast)",
         65, "#0369A1", "#38BDF8", 12),
        ("MESH RELAY & DAEMON LAYER", 
         "MeshForegroundService (Background Wakelock)  •  BluetoothMeshService  •  PacketProcessor  •  PacketRelayManager (Priority Flooding)",
         45, "#0F766E", "#2DD4BF", 13),
        ("BINARY WIRE PROTOCOL LAYER", 
         "BinaryProtocol.kt (Opcode 0x30: EMERGENCY_BEACON, 0x31: EMERGENCY_ACK)  •  EmergencyModel.kt (Compact Big-Endian Byte Serializer)",
         26, "#7E22CE", "#C084FC", 12),
        ("PHYSICAL HARDWARE & OS SUBSYSTEM", 
         "Android BLE Stack (Advertising & Scanning, GATT Server/Client)  •  Hardware GPS Provider  •  Android Vibrator Motor",
         8, "#B91C1C", "#F87171", 12)
    ]

    for title, subtitle, y, border_col, text_col, h in layers:
        rect = patches.FancyBboxPatch((5, y), 90, h, boxstyle="round,pad=1.2",
                                      facecolor='#131A26', edgecolor=border_col, linewidth=2)
        ax.add_patch(rect)
        ax.text(8, y + h - 4, title, fontsize=10, fontweight='bold', color=text_col, fontfamily='sans-serif')
        ax.text(8, y + 2.5, subtitle, fontsize=8, color='#E2E8F0', fontfamily='sans-serif')

    # Connecting arrows
    arrow_props = dict(facecolor='#38BDF8', edgecolor='none', width=1.5, headwidth=6, headlength=6)
    for y in [84, 65, 45, 26]:
        ax.annotate('', xy=(50, y), xytext=(50, y + 3), arrowprops=arrow_props)

    plt.tight_layout()
    path = 'docs/diagrams/arch_layers.png'
    plt.savefig(path, bbox_inches='tight', facecolor=fig.get_facecolor(), edgecolor='none')
    plt.close()
    return path

# ==========================================
# 2. GENERATE DIAGRAM 2: MULTI-HOP MESH TOPOLOGY
# ==========================================
def generate_topology_diagram():
    fig, ax = plt.subplots(figsize=(10, 5), dpi=300)
    fig.patch.set_facecolor('#0B0E14')
    ax.set_facecolor('#0B0E14')
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 60)
    ax.axis('off')

    # Grid lines
    for x in range(10, 100, 20):
        ax.axvline(x, color='#141C2B', linewidth=1, linestyle=':')

    # Radio Range Circles
    circle_victim = patches.Circle((15, 30), 16, edgecolor='#EF4444', facecolor='#EF4444', alpha=0.1, linestyle='--', linewidth=1.5)
    circle_relay1 = patches.Circle((38, 42), 16, edgecolor='#F59E0B', facecolor='#F59E0B', alpha=0.08, linestyle='--', linewidth=1.5)
    circle_relay2 = patches.Circle((62, 40), 16, edgecolor='#F59E0B', facecolor='#F59E0B', alpha=0.08, linestyle='--', linewidth=1.5)
    circle_hq     = patches.Circle((85, 30), 16, edgecolor='#10B981', facecolor='#10B981', alpha=0.1, linestyle='--', linewidth=1.5)
    ax.add_patch(circle_victim)
    ax.add_patch(circle_relay1)
    ax.add_patch(circle_relay2)
    ax.add_patch(circle_hq)

    # Forward Links
    ax.plot([15, 38], [30, 42], color='#EF4444', linewidth=2.5, zorder=2)
    ax.text(24, 38, "Hop 1 (TTL 7->6)", fontsize=8, color='#FCA5A5', fontweight='bold', rotation=25)

    ax.plot([38, 62], [42, 40], color='#F59E0B', linewidth=2.5, zorder=2)
    ax.text(47, 43, "Hop 2 (TTL 6->5)", fontsize=8, color='#FCD34D', fontweight='bold')

    ax.plot([62, 85], [40, 30], color='#10B981', linewidth=2.5, zorder=2)
    ax.text(71, 37, "Hop 3 (TTL 5->4)", fontsize=8, color='#6EE7B7', fontweight='bold', rotation=-22)

    # Pruned Link to Civilian
    ax.plot([38, 38], [42, 14], color='#38BDF8', linewidth=1.8, linestyle='--', zorder=2)
    ax.text(40, 26, "Duplicate Fan-out\n(Storm Pruned!)", fontsize=7.5, color='#38BDF8')

    # Reverse ACK curved arrow
    ack_curve = patches.FancyArrowPatch((85, 26), (15, 26), connectionstyle="arc3,rad=-0.35",
                                        color='#10B981', linewidth=2, linestyle='--', arrowstyle='->',
                                        mutation_scale=15)
    ax.add_patch(ack_curve)
    ax.text(50, 11, "<= REVERSE ACK ROUTE: EMERGENCY_ACK (ETA ~ 4 mins) <=", fontsize=9, color='#34D399',
            fontweight='bold', ha='center')

    # Nodes
    nodes = [
        ((15, 30), "#EF4444", "VICTIM\n(Trapped)", "Bat: 21%\nCRITICAL"),
        ((38, 42), "#F59E0B", "RELAY 1", "Civilian A\nBat: 88%"),
        ((62, 40), "#F59E0B", "RELAY 2", "Civilian B\nBat: 74%"),
        ((85, 30), "#10B981", "RESCUE HQ\n(Doctor)", "Disaster Base\nOnline"),
        ((38, 14), "#38BDF8", "BYSTANDER", "Duplicate\nDropped")
    ]

    for pos, col, name, sub in nodes:
        circle = patches.Circle(pos, 3.8, facecolor=col, edgecolor='#FFFFFF', linewidth=2, zorder=4)
        ax.add_patch(circle)
        ax.text(pos[0], pos[1], name, fontsize=7.5, fontweight='bold', color='#FFFFFF', ha='center', va='center', zorder=5)
        ax.text(pos[0], pos[1] - 5.5, sub, fontsize=7, color='#CBD5E1', ha='center', va='top')

    plt.tight_layout()
    path = 'docs/diagrams/mesh_topology.png'
    plt.savefig(path, bbox_inches='tight', facecolor=fig.get_facecolor(), edgecolor='none')
    plt.close()
    return path

# ==========================================
# 3. GENERATE DIAGRAM 3: SEQUENCE PROTOCOL
# ==========================================
def generate_sequence_diagram():
    fig, ax = plt.subplots(figsize=(10, 5.5), dpi=300)
    fig.patch.set_facecolor('#0B0E14')
    ax.set_facecolor('#0B0E14')
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 100)
    ax.axis('off')

    lifelines = [
        (15, "Victim (Trapped)", "#EF4444"),
        (40, "Relay Phone 1", "#F59E0B"),
        (65, "Relay Phone 2", "#F59E0B"),
        (90, "Rescue HQ / Doctor", "#10B981")
    ]

    for x, label, col in lifelines:
        box = patches.FancyBboxPatch((x - 10, 88), 20, 8, boxstyle="round,pad=0.5",
                                     facecolor='#1E293B', edgecolor=col, linewidth=1.5)
        ax.add_patch(box)
        ax.text(x, 92, label, fontsize=8.5, fontweight='bold', color='#FFFFFF', ha='center', va='center')
        ax.plot([x, x], [88, 8], color='#334155', linestyle='--', linewidth=1.2, zorder=1)

    steps = [
        (15, 40, 78, "1. EMERGENCY_BEACON (Opcode 0x30, TTL=7)", "#EF4444", True),
        (40, 40, 70, "[Relay 1: TTL 7->6, Cache UUID]", "#FCD34D", False),
        (40, 65, 62, "2. Rebroadcast Beacon (TTL=6)", "#F59E0B", True),
        (65, 65, 54, "[Relay 2: TTL 6->5, Cache UUID]", "#FCD34D", False),
        (65, 90, 46, "3. Forward Beacon to Base (TTL=5)", "#F59E0B", True),
        (90, 90, 38, "[Rescue HQ: Triage Alerted! Doctor taps Respond]", "#A7F3D0", False),
        (90, 65, 30, "4. EMERGENCY_ACK (Opcode 0x31, ETA: 4min)", "#10B981", True),
        (65, 40, 22, "5. Reverse Hop ACK (TTL=6)", "#10B981", True),
        (40, 15, 14, "6. ACK Delivered to Victim! (Morse Vibration)", "#34D399", True)
    ]

    for x1, x2, y, text, col, is_arrow in steps:
        if is_arrow:
            ax.annotate('', xy=(x2, y), xytext=(x1, y),
                        arrowprops=dict(facecolor=col, edgecolor=col, width=1.5, headwidth=6, headlength=6))
            mid_x = (x1 + x2) / 2
            ax.text(mid_x, y + 2, text, fontsize=7.8, color=col, fontweight='bold', ha='center')
        else:
            rect = patches.FancyBboxPatch((x1 - 18, y - 2.5), 36, 5, boxstyle="round,pad=0.4",
                                          facecolor='#0F172A', edgecolor=col, linewidth=1)
            ax.add_patch(rect)
            ax.text(x1, y, text, fontsize=7.2, color=col, ha='center', va='center')

    plt.tight_layout()
    path = 'docs/diagrams/seq_diagram.png'
    plt.savefig(path, bbox_inches='tight', facecolor=fig.get_facecolor(), edgecolor='none')
    plt.close()
    return path

# ==========================================
# 4. REPORTLAB NUMBERED CANVAS CLASS
# ==========================================
class NumberedCanvas(canvas.Canvas):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self._saved_page_states = []

    def showPage(self):
        self._saved_page_states.append(dict(self.__dict__))
        self._startPage()

    def save(self):
        num_pages = len(self._saved_page_states)
        for state in self._saved_page_states:
            self.__dict__.update(state)
            self.draw_page_decorations(num_pages)
            super().showPage()
        super().save()

    def draw_page_decorations(self, page_count):
        self.saveState()
        self.setFont("Helvetica", 8)
        self.setFillColor(colors.HexColor("#64748B"))

        # Header (Pages > 1)
        if self._pageNumber > 1:
            self.drawString(54, letter[1] - 36, "Bitchat Android — Offline Disaster SOS Mesh & Telemetry Engine")
            self.drawRightString(letter[0] - 54, letter[1] - 36, "BE Final Year Project Report")
            self.setStrokeColor(colors.HexColor("#CBD5E1"))
            self.setLineWidth(0.5)
            self.line(54, letter[1] - 42, letter[0] - 54, letter[1] - 42)

        # Footer
        self.setStrokeColor(colors.HexColor("#CBD5E1"))
        self.setLineWidth(0.5)
        self.line(54, 45, letter[0] - 54, 45)

        self.drawString(54, 32, "Confidential — For Academic Examination & Defense Only")
        self.drawRightString(letter[0] - 54, 32, f"Page {self._pageNumber} of {page_count}")
        self.restoreState()

# ==========================================
# 5. COMPILE FULL PDF DOCUMENT
# ==========================================
def build_pdf():
    print("Generating diagrams...")
    arch_img = generate_architecture_diagram()
    topo_img = generate_topology_diagram()
    seq_img  = generate_sequence_diagram()

    pdf_path = "docs/BITCHAT_DISASTER_MESH_PROJECT_REPORT.pdf"
    doc = SimpleDocTemplate(
        pdf_path,
        pagesize=letter,
        leftMargin=54,
        rightMargin=54,
        topMargin=54,
        bottomMargin=54
    )

    styles = getSampleStyleSheet()
    
    # Custom Typography Styles
    title_style = ParagraphStyle(
        'DocTitle',
        parent=styles['Heading1'],
        fontName='Helvetica-Bold',
        fontSize=22,
        leading=26,
        textColor=colors.HexColor('#0F172A'),
        spaceAfter=6
    )
    subtitle_style = ParagraphStyle(
        'DocSubtitle',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=11,
        leading=15,
        textColor=colors.HexColor('#475569'),
        spaceAfter=15
    )
    h1_style = ParagraphStyle(
        'Heading1_Custom',
        parent=styles['Heading1'],
        fontName='Helvetica-Bold',
        fontSize=14,
        leading=18,
        textColor=colors.HexColor('#0369A1'),
        spaceBefore=14,
        spaceAfter=8,
        keepWithNext=True
    )
    h2_style = ParagraphStyle(
        'Heading2_Custom',
        parent=styles['Heading2'],
        fontName='Helvetica-Bold',
        fontSize=11,
        leading=15,
        textColor=colors.HexColor('#0F172A'),
        spaceBefore=10,
        spaceAfter=4,
        keepWithNext=True
    )
    body_style = ParagraphStyle(
        'Body_Custom',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=9.5,
        leading=13.5,
        textColor=colors.HexColor('#1E293B'),
        spaceAfter=6
    )
    bullet_style = ParagraphStyle(
        'Bullet_Custom',
        parent=body_style,
        leftIndent=15,
        firstLineIndent=-10,
        spaceAfter=4
    )
    qa_q_style = ParagraphStyle(
        'QA_Question',
        parent=body_style,
        fontName='Helvetica-Bold',
        textColor=colors.HexColor('#0F766E'),
        spaceBefore=6,
        spaceAfter=2,
        keepWithNext=True
    )
    qa_a_style = ParagraphStyle(
        'QA_Answer',
        parent=body_style,
        leftIndent=10,
        spaceAfter=6
    )

    story = []

    # Title Banner
    story.append(Paragraph("Bitchat Android: Offline Disaster SOS Mesh & Triage System", title_style))
    story.append(Paragraph("<b>Final Year Bachelor of Engineering (B.E.) Project Report &amp; Technical Defense Guide</b><br/><i>Domain: Decentralized Mobile Ad-Hoc Networking (MANET) &amp; Disaster Telemetry</i>", subtitle_style))
    story.append(HRFlowable(width="100%", thickness=1.5, color=colors.HexColor('#0284C7'), spaceBefore=2, spaceAfter=12))

    # Section 1: Executive Summary
    story.append(Paragraph("1. Executive Summary &amp; Project Abstract", h1_style))
    story.append(Paragraph(
        "In major catastrophes such as earthquakes, tsunamis, flash floods, landslides, or war conflicts, centralized terrestrial telecommunications infrastructure (cellular base stations, fiber optic backhauls, power grids, and internet service providers) collapses within minutes. Traditional emergency messaging applications (WhatsApp, Telegram, Signal) become completely useless in these environments because they fundamentally depend on client-server architectures with reachable internet gateways.",
        body_style
    ))
    story.append(Paragraph(
        "<b>Our Project</b> transforms ordinary consumer Android smartphones into an <b>autonomous, decentralized, multi-hop peer-to-peer (P2P) mesh network</b> operating strictly off-grid over <b>Bluetooth Low Energy (BLE)</b>. We designed and implemented an <b>Offline Disaster SOS &amp; Emergency Mesh Triage System</b> coupled with an <b>In-App Real-Time Network Simulation and Telemetry Visualizer</b>.",
        body_style
    ))

    # Key Highlights Box
    highlights_data = [
        [Paragraph("<b>Key Project Achievements:</b><br/>"
                   "• <b>Zero-Infrastructure Multi-Hop Relay:</b> Up to 7 hops peer-to-peer routing without SIM, mobile data, or Wi-Fi routers.<br/>"
                   "• <b>Compact Binary Telemetry Protocol:</b> Big-Endian wire format packing GPS coordinates, battery %, triage severity, and notes.<br/>"
                   "• <b>Guaranteed Delivery &amp; Storm Mitigation:</b> 100% priority flooding for distress beacons, with LRU duplicate suppression to eliminate broadcast storms.<br/>"
                   "• <b>Closed-Loop Reverse Triage Acknowledgment (ACK):</b> Responders return confirmation with ETA (~4 min), triggering Morse code vibration on victim device.<br/>"
                   "• <b>Dual-Mode Real-Time Visualizer:</b> Live BLE hardware tracking + interactive 6-node disaster simulation lab.", body_style)]
    ]
    t_hl = Table(highlights_data, colWidths=[letter[0] - 108])
    t_hl.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,-1), colors.HexColor('#F0FDF4')),
        ('BOX', (0,0), (-1,-1), 1, colors.HexColor('#22C55E')),
        ('TOPPADDING', (0,0), (-1,-1), 8),
        ('BOTTOMPADDING', (0,0), (-1,-1), 8),
        ('LEFTPADDING', (0,0), (-1,-1), 10),
        ('RIGHTPADDING', (0,0), (-1,-1), 10),
    ]))
    story.append(t_hl)
    story.append(Spacer(1, 10))

    # Section 2: Problem Statement & Motivation
    story.append(Paragraph("2. Problem Statement &amp; Motivation", h1_style))
    story.append(Paragraph(
        "<b>The Critical Problem:</b> During disasters, search and rescue personnel face a 'black box' problem—they have zero situational visibility into who is trapped beneath rubble, their exact GPS coordinates, their medical triage condition, or how much battery remains on their devices. Victims panic and continuously refresh mobile radios, rapidly draining precious battery life.",
        body_style
    ))
    story.append(Paragraph(
        "<b>The Proposed Solution:</b> By leveraging the omnipresence of everyday Android smartphones, any group of devices automatically establishes a self-configuring wireless mesh. Phones act as both <b>Hosts</b> and <b>Routers</b>, forwarding critical packets across peer chains to emergency teams.",
        body_style
    ))
    story.append(Spacer(1, 8))

    # Section 3: Architecture
    story.append(Paragraph("3. System Architecture &amp; Layered Design", h1_style))
    story.append(Paragraph(
        "The project adheres to Clean Architecture with strict layer decoupling, separating the user interface, coordination services, packet routing, wire serialization, and BLE hardware interaction.",
        body_style
    ))
    story.append(Image(arch_img, width=letter[0] - 108, height=(letter[0] - 108) * 0.6))
    story.append(Spacer(1, 10))

    # Section 4: BLE Mesh Networking & Multi-Hop Relay
    story.append(Paragraph("4. BLE Mesh Networking &amp; Multi-Hop Routing Algorithm", h1_style))
    story.append(Paragraph(
        "<b>Peer Discovery:</b> Standard Bluetooth requires manual PIN pairing between devices—which is completely unfeasible during an emergency among strangers. Our app utilizes <b>Bluetooth Low Energy (BLE) Advertisements</b> with a dedicated 128-bit Service UUID. Surrounding phones continuously scan and connect automatically in the background (Dual-Mode: Peripheral GATT Server + Central GATT Client).",
        body_style
    ))
    story.append(Paragraph(
        "<b>Controlled Flooding with TTL:</b> Every beacon carries a Time-to-Live (TTL = 7). Each relay decrements TTL by 1 ($7 \\rightarrow 6 \\rightarrow 5$). When TTL reaches 0, forwarding ceases, bounding network propagation to an optimal geographic envelope.",
        body_style
    ))
    story.append(Paragraph(
        "<b>Broadcast Storm Mitigation:</b> To prevent exponential packet multiplication, every node maintains an in-memory LRU cache of recently processed beacon UUIDs. If a node receives a packet already present in its cache, it is immediately dropped without retransmission.",
        body_style
    ))
    story.append(Image(topo_img, width=letter[0] - 108, height=(letter[0] - 108) * 0.5))
    story.append(Spacer(1, 10))

    story.append(Paragraph("4.2 Protocol Life-Cycle &amp; Sequence Timeline", h2_style))
    story.append(Image(seq_img, width=letter[0] - 108, height=(letter[0] - 108) * 0.55))
    story.append(Spacer(1, 10))

    # Section 5: Protocol Wire Format
    story.append(Paragraph("5. Binary Protocol Specification (Wire Memory Layout)", h1_style))
    story.append(Paragraph(
        "To minimize radio energy expenditure and achieve maximum RF range under poor Signal-to-Noise Ratio (SNR), JSON/XML payloads were replaced with a compact 1-byte aligned <b>Big-Endian Binary Protocol</b>:",
        body_style
    ))

    table_data = [
        ["Byte Offset", "Field Name", "Size", "Type", "Description"],
        ["0x00", "Protocol Version", "1 Byte", "UInt8", "Wire version identifier (0x01)"],
        ["0x01", "Severity Code", "1 Byte", "UInt8", "0x01=Medical, 0x02=Trapped, 0x03=Hazard, 0x04=Supplies"],
        ["0x02", "Battery Level", "1 Byte", "Int8", "Victim device battery percentage (0-100%)"],
        ["0x03..0x0A", "Timestamp", "8 Bytes", "Int64", "Epoch milliseconds of distress event"],
        ["0x0B", "Telemetry Flags", "1 Byte", "Bitfield", "Bit 0: Has GPS, Bit 1: Is Active SOS, Bit 2: Has Accuracy"],
        ["0x0C", "ID Length", "1 Byte", "UInt8", "Length (L) of unique beacon UUID string"],
        ["0x0D..", "Beacon UUID", "L Bytes", "UTF-8", "Globally unique distress identifier"],
        ["..", "Sender Nickname", "Var", "UTF-8", "Victim display name (e.g., 'Alice')"],
        ["..", "Distress Note", "Var", "UTF-8", "Custom text note (e.g., '2 trapped under pillar')"],
        [".. (Opt)", "GPS Latitude", "8 Bytes", "Float64", "IEEE 754 Big-Endian double precision degrees"],
        [".. (Opt)", "GPS Longitude", "8 Bytes", "Float64", "IEEE 754 Big-Endian double precision degrees"],
        [".. (Opt)", "Accuracy Radius", "4 Bytes", "Float32", "GPS horizontal accuracy radius in meters"]
    ]
    t_proto = Table(table_data, colWidths=[65, 95, 45, 55, letter[0] - 108 - 260])
    t_proto.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,0), colors.HexColor('#0F172A')),
        ('TEXTCOLOR', (0,0), (-1,0), colors.HexColor('#38BDF8')),
        ('FONTNAME', (0,0), (-1,0), 'Helvetica-Bold'),
        ('FONTSIZE', (0,0), (-1,-1), 8),
        ('ALIGN', (0,0), (-1,-1), 'LEFT'),
        ('BOTTOMPADDING', (0,0), (-1,-1), 4),
        ('TOPPADDING', (0,0), (-1,-1), 4),
        ('ROWBACKGROUNDS', (0,1), (-1,-1), [colors.HexColor('#F8FAFC'), colors.HexColor('#FFFFFF')]),
        ('GRID', (0,0), (-1,-1), 0.5, colors.HexColor('#CBD5E1')),
    ]))
    story.append(t_proto)
    story.append(Spacer(1, 10))

    # Section 6: In-App Simulation Lab
    story.append(Paragraph("6. Real-Time In-App &amp; Web Simulator Architecture", h1_style))
    story.append(Paragraph(
        "A critical innovation for academic evaluation is our built-in <b>Mesh Simulator &amp; Packet Animator</b> (accessible via <code>/simulate</code> or top-bar Radar icon):",
        body_style
    ))
    story.append(Paragraph("• <b>Live BLE Mode:</b> Reads real-time connected peers, RSSI signals, and gossip graph state from physical devices.", bullet_style))
    story.append(Paragraph("• <b>Disaster Lab Mode:</b> Interactive 6-node virtual testbed (Victim, Relay 1, Relay 2, Relay 3, Civilian Bystander, Disaster HQ).", bullet_style))
    story.append(Paragraph("• <b>Touch &amp; Drag Physics:</b> Move nodes across screen; if distance < RF radius (25m), a link dynamically connects; moving away severs the link.", bullet_style))
    story.append(Paragraph("• <b>Hop-by-Hop Animation:</b> Glowing energy particles hop from node to node with live TTL decrementing, duplicate packet pruning, and reverse ACK return path.", bullet_style))
    story.append(Spacer(1, 8))

    # Section 7: Key Doubts Clarified
    story.append(Paragraph("7. Technical Questions &amp; System Integrity", h1_style))
    story.append(Paragraph("<b>Q: Can a newly arrived phone join the network and relay the SOS automatically?</b>", qa_q_style))
    story.append(Paragraph("<b>A: YES, 100% AUTOMATICALLY.</b> The mesh is completely zero-configuration. Any phone with the app installed automatically discovers nearby nodes via BLE background advertisements within 1–3 seconds. No account registration, phone number, or internet connection is required. The phone immediately receives active beacons via periodic 20-second rebroadcasts and starts serving as an active multi-hop relay.", qa_a_style))

    story.append(Paragraph("<b>Q: Are the original/pre-existing Bitchat functionalities still intact?</b>", qa_q_style))
    story.append(Paragraph("<b>A: YES, 100% INTACT AND FULLY PRESERVED.</b> All existing capabilities (End-to-End Encrypted Private Chat, Public &amp; Password-Protected Channels, Voice Notes, File &amp; Image Sharing, Geohash Notes, and Tor Onion Routing) remain functional. In <code>BinaryProtocol.kt</code>, existing messages operate under opcodes <code>0x01..0x05</code>, while our disaster beacons occupy dedicated opcodes <code>0x30</code> and <code>0x31</code>, running in parallel without touching legacy pipelines.", qa_a_style))
    story.append(Spacer(1, 8))

    # Section 8: Real World Applications
    story.append(Paragraph("8. Real-World Applications &amp; Deployment Scenarios", h1_style))
    story.append(Paragraph("1. <b>Natural Disaster Relief (NDRF / SDRF):</b> Rapid search and rescue triage across collapsed urban areas during earthquakes or floods.", bullet_style))
    story.append(Paragraph("2. <b>Wilderness &amp; Mountaineering:</b> Off-grid hiking groups, forest rangers, and mountaineers in cellular dead zones.", bullet_style))
    story.append(Paragraph("3. <b>Underground Metro &amp; Mine Shafts:</b> Phone-to-phone relay chain transmitting distress signals out of subterranean tunnels where cell signals cannot penetrate.", bullet_style))
    story.append(Paragraph("4. <b>Humanitarian &amp; Conflict Zones:</b> Emergency communication when commercial telecom networks are deliberately blacked out.", bullet_style))
    story.append(Spacer(1, 8))

    # Section 9: Viva Voce Defense Script
    story.append(Paragraph("9. Viva Voce Defense: Expected Examiner Questions &amp; Answers", h1_style))
    viva_qa = [
        ("Why BLE instead of Wi-Fi Direct or Classic Bluetooth?",
         "Classic Bluetooth requires manual PIN pairing which is impossible among strangers in emergencies. Wi-Fi Direct has severe battery drain. BLE supports connectionless advertisements, ultra-low battery draw (lasting days), and dual-mode concurrent peripheral/central operation essential for multi-hop mesh routing."),
        ("How do you prevent broadcast storms if hundreds of devices relay?",
         "Two-fold defense: (1) Time-to-Live (TTL=7) bounding hop propagation, and (2) Local LRU Seen-Packet Cache. If a packet UUID is already cached, it is instantly dropped, preventing loops."),
        ("What happens if a relay node moves away or turns off?",
         "The mesh is self-healing. Emergency beacons are flooded across multiple adjacent neighbors rather than a static single route. In addition, active distress beacons are rebroadcasted every 20 seconds, automatically establishing alternative paths through newly discovered neighbors."),
        ("How does the victim know responders received the SOS without internet?",
         "Through our closed-loop Emergency ACK protocol. When a responder taps 'I am Responding', their phone returns an EMERGENCY_ACK (Opcode 0x31) back across the mesh. Upon receipt, the victim's phone vibrates in Morse code ('... --- ...') and updates the screen to show responder ETA.")
    ]
    for q, a in viva_qa:
        story.append(Paragraph(f"• <b>Q: {q}</b>", qa_q_style))
        story.append(Paragraph(f"<b>Ans:</b> {a}", qa_a_style))

    # Build Document
    print(f"Compiling PDF document to {pdf_path}...")
    doc.build(story, canvasmaker=NumberedCanvas)
    print("PDF build complete!")

if __name__ == '__main__':
    build_pdf()

