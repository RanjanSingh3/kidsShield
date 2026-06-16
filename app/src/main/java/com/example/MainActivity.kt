package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AlertLog
import com.example.data.BlockedRule
import com.example.viewmodel.ShieldViewModel
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private val viewModel: ShieldViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GuardTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    MainScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

// --- Visual Color Palette & Custom Theme ---

val SlateDark = Color(0xFF0F172A)
val CardDark = Color(0xFF1E293B)
val BorderDark = Color(0xFF334155)
val CyanGlow = Color(0xFF06B6D4)
val EmeraldShield = Color(0xFF10B981)
val CoralAlert = Color(0xFFEF4444)
val AmberWarn = Color(0xFFF59E0B)
val TextLight = Color(0xFFF1F5F9)
val TextMuted = Color(0xFF94A3B8)

@Composable
fun GuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = SlateDark,
            surface = CardDark,
            primary = CyanGlow,
            secondary = EmeraldShield,
            error = CoralAlert
        ),
        content = content
    )
}

// --- Main Shield Composable Layout ---

@Composable
fun MainScreen(
    viewModel: ShieldViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by viewModel.allSettings.collectAsStateWithLifecycle()
    val alertLogs by viewModel.alertLogs.collectAsStateWithLifecycle()
    val blockedRules by viewModel.blockedRules.collectAsStateWithLifecycle()
    val parentAuthenticated by viewModel.parentAuthenticated.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf("LIVE_LOGS") } // "LIVE_LOGS", "SANDBOX", "PARENT_SETTINGS"
    var showAuthDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SlateDark)
    ) {
        // 1. App Beautiful Header & Live Shield Controller
        ShieldHeader(
            settings = settings,
            onChildModeToggled = { enabled ->
                viewModel.setChildMode(enabled)
                // When Child Mode is toggled, also toggle custom system states
                viewModel.toggleVpn(context, enabled)
            }
        )

        // 2. Tab Navigation
        HorizontalTabRow(
            activeTab = activeTab,
            onTabSelected = { selectedTab ->
                if (selectedTab == "PARENT_SETTINGS") {
                    if (parentAuthenticated) {
                        activeTab = selectedTab
                    } else {
                        // Show PIN Auth flow
                        showAuthDialog = true
                    }
                } else {
                    activeTab = selectedTab
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Main Content Panes
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            when (activeTab) {
                "LIVE_LOGS" -> LiveLogsTab(
                    logs = alertLogs,
                    settings = settings,
                    onClearHistory = { viewModel.clearHistory() }
                )
                "SANDBOX" -> SandboxTab(
                    viewModel = viewModel,
                    blockedRules = blockedRules,
                    settings = settings
                )
                "PARENT_SETTINGS" -> ParentSettingsTab(
                    viewModel = viewModel,
                    settings = settings,
                    blockedRules = blockedRules,
                    onLockDashboard = {
                        viewModel.lockDashboard()
                        activeTab = "LIVE_LOGS"
                    }
                )
            }
        }

        // Parent Authentication Request Dialog
        if (showAuthDialog) {
            PinAuthDialog(
                onDismiss = { showAuthDialog = false },
                onAuthPassed = {
                    showAuthDialog = false
                    activeTab = "PARENT_SETTINGS"
                    Toast.makeText(context, "Access Granted", Toast.LENGTH_SHORT).show()
                },
                viewModel = viewModel
            )
        }
    }
}

// Header Component
@Composable
fun ShieldHeader(
    settings: Map<String, String>,
    onChildModeToggled: (Boolean) -> Unit
) {
    val childModeOn = settings["child_mode"] == "true"
    val vpnOn = settings["vpn_active"] == "true"
    val geminiOn = settings["gemini_scan_on"] == "true"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        SlateDark.copy(alpha = 0.95f),
                        CardDark.copy(alpha = 0.6f)
                    )
                )
            )
            .padding(18.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Spinning Shield Status Logo ring
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (childModeOn) EmeraldShield.copy(alpha = 0.15f) else CoralAlert.copy(alpha = 0.15f))
                        .border(
                            1.5.dp, 
                            if (childModeOn) EmeraldShield else CoralAlert, 
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (childModeOn) Icons.Default.Check else Icons.Default.Warning,
                        contentDescription = "Shield Indicator",
                        tint = if (childModeOn) EmeraldShield else CoralAlert,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "kidsShiield",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextLight,
                        fontFamily = FontFamily.SansSerif
                    )
                    Text(
                        text = if (childModeOn) "ACTIVE CHILD PROTECTION" else "MONITORING SUSPENDED",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (childModeOn) EmeraldShield else CoralAlert,
                        letterSpacing = 1.sp
                    )
                }

                // Main child mode switcher
                Switch(
                    checked = childModeOn,
                    onCheckedChange = onChildModeToggled,
                    modifier = Modifier.testTag("child_mode_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TextLight,
                        checkedTrackColor = EmeraldShield,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = BorderDark
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Minimalist Status Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusBadge(
                    label = "DNS VPN Shield",
                    active = childModeOn && vpnOn,
                    icon = Icons.Default.Settings
                )
                StatusBadge(
                    label = "A.I. Screen Scan",
                    active = childModeOn && geminiOn,
                    icon = Icons.Default.Star
                )
                StatusBadge(
                    label = "Text Filter",
                    active = childModeOn,
                    icon = Icons.Default.Info
                )
            }
        }
    }
}

@Composable
fun StatusBadge(
    label: String,
    active: Boolean,
    icon: ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(CardDark, RoundedCornerShape(18.dp))
            .border(1.dp, if (active) CyanGlow.copy(alpha = 0.4f) else BorderDark, RoundedCornerShape(18.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (active) CyanGlow else CoralAlert)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) CyanGlow else TextMuted,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            color = if (active) TextLight else TextMuted
        )
    }
}

// Custom Horizontal Tab Component
@Composable
fun HorizontalTabRow(
    activeTab: String,
    onTabSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(CardDark, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        val tabs = listOf(
            Triple("LIVE_LOGS", "Activity Feed", Icons.Default.Info),
            Triple("SANDBOX", "Sandbox Sandbox", Icons.Default.PlayArrow),
            Triple("PARENT_SETTINGS", "Parent Panel", Icons.Default.Settings)
        )

        tabs.forEach { (key, label, icon) ->
            val isSelected = activeTab == key
            val backgroundGradient = Brush.horizontalGradient(
                colors = listOf(CyanGlow.copy(alpha = 0.25f), CyanGlow.copy(alpha = 0.05f))
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .then(
                        if (isSelected) Modifier.background(backgroundGradient)
                        else Modifier
                    )
                    .clickable { onTabSelected(key) }
                    .padding(vertical = 12.dp)
                    .testTag("tab_$key")
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isSelected) CyanGlow else TextMuted,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label.split(" ")[0], // Truncate to first word for compact mobile screens
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) TextLight else TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// --- Live Activity Feed Logs Tab ---

@Composable
fun MetricsDashboard(
    logs: List<AlertLog>,
    settings: Map<String, String>
) {
    val childModeOn = settings["child_mode"] == "true"
    val vpnOn = settings["vpn_active"] == "true"
    val geminiOn = settings["gemini_scan_on"] == "true"
    val accessibilityOn = settings["accessibility_active"] == "true"

    val totalBlocked = logs.count { it.status == "BLOCKED" }

    // Calculate dynamic security health score
    var score = 0
    if (childModeOn) score += 40
    if (vpnOn) score += 20
    if (geminiOn) score += 20
    if (accessibilityOn) score += 20

    val securityLevel = when {
        !childModeOn -> "SHIELD DISABLED"
        score >= 80 -> "ELITE HIGH-SHIELD"
        score >= 60 -> "ACTIVE PROTECTION"
        else -> "BASIC MONITOR"
    }

    val statusColor = when {
        !childModeOn -> CoralAlert
        score >= 80 -> EmeraldShield
        score >= 60 -> CyanGlow
        else -> AmberWarn
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Card 1: Shield Health Gauge
        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            modifier = Modifier
                .weight(1.2f)
                .border(1.dp, statusColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Circular Progress Gauge metric
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(46.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { 1f },
                        color = BorderDark,
                        strokeWidth = 4.5.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                    CircularProgressIndicator(
                        progress = { score.toFloat() / 100f },
                        color = statusColor,
                        strokeWidth = 4.5.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                    Text(
                        text = "${score}%",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = TextLight
                    )
                }

                Column {
                    Text(
                        text = "SHIELD STRENGTH",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = securityLevel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                    Text(
                        text = if (childModeOn) "Firewall actively monitoring" else "Enable Child Mode to start",
                        fontSize = 8.sp,
                        color = TextMuted
                    )
                }
            }
        }

        // Card 2: Intercept Stats Counter
        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            modifier = Modifier
                .weight(0.8f)
                .border(1.dp, BorderDark, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "BLOCKED LOGS",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$totalBlocked",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = if (totalBlocked > 0) CoralAlert else EmeraldShield
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "incidents",
                        fontSize = 9.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                Text(
                    text = "Total adult tags isolated",
                    fontSize = 8.sp,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
fun LiveLogsTab(
    logs: List<AlertLog>,
    settings: Map<String, String>,
    onClearHistory: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Dynamic dashboard analytics display
        MetricsDashboard(logs = logs, settings = settings)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Text(
                text = "Child Shield Event Logs",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextLight,
                modifier = Modifier.weight(1f)
            )

            if (logs.isNotEmpty()) {
                TextButton(
                    onClick = onClearHistory,
                    colors = ButtonDefaults.textButtonColors(contentColor = CoralAlert),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear logs", modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear All", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (logs.isEmpty()) {
            EmptyLogsIllustration()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(logs) { log ->
                    AlertLogCard(log = log)
                }
            }
        }
    }
}

@Composable
fun EmptyLogsIllustration() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .background(CardDark, CircleShape)
                .border(1.dp, BorderDark, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = EmeraldShield,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "No Guard Incidents Logged",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = TextLight
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Child Mode status is active. Unsafe events, URLs, or text capture captured from social applications will be logged here in real-time.",
            fontSize = 11.sp,
            color = TextMuted,
            textAlign = TextAlign.Center,
            lineHeight = 15.sp
        )
    }
}

@Composable
fun AlertLogCard(log: AlertLog) {
    val isAlert = log.status == "BLOCKED"
    val dateFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val formattedTime = dateFormat.format(Date(log.timestamp))

    Card(
        colors = CardDefaults.cardColors(containerColor = CardDark),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isAlert) CoralAlert.copy(alpha = 0.4f) else BorderDark,
                RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isAlert) CoralAlert else EmeraldShield)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = log.appName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextLight
                    )
                }

                Text(
                    text = formattedTime,
                    fontSize = 10.sp,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = log.detectedType,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isAlert) CoralAlert else EmeraldShield
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = log.contentSnippet,
                fontSize = 11.sp,
                color = TextMuted,
                lineHeight = 16.sp
            )
        }
    }
}

// --- High Fidelity Virtual Device Content Simulation Data ---
data class MockSocialPost(
    val id: Int,
    val authorName: String,
    val authorTag: String,
    val text: String,
    val explanation: String,
    val isRisky: Boolean,
    val type: String, // "CASINO", "DATING", "TECH", "NATURE"
    val avatarColor: Color
)

// --- Interactive Sandbox Simulator Tab ---
@Composable
fun SandboxTab(
    viewModel: ShieldViewModel,
    blockedRules: List<BlockedRule>,
    settings: Map<String, String>
) {
    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
    val simulationResult by viewModel.simulationResult.collectAsStateWithLifecycle()

    val presetPosts = remember {
        listOf(
            MockSocialPost(
                id = 1,
                authorName = "SpinVegasOnline",
                authorTag = "@vegas_jackpots",
                text = "🔥 Play online blackjack now! Instant withdrawal and casino bonuses for mature adults. Play here: slotspay.net 🃏🎰",
                explanation = "Blocked by rule word search: 'blackjack', 'casino', 'slotspay.net'",
                isRisky = true,
                type = "CASINO",
                avatarColor = Color(0xFFF59E0B)
            ),
            MockSocialPost(
                id = 2,
                authorName = "KotlinDevelopers",
                authorTag = "@kotlin_devs",
                text = "Beautiful modular programming tips in Jetpack Compose! Make sure to support responsive window size classes! 📱💻",
                explanation = "Clean educational post, allowed fully.",
                isRisky = false,
                type = "TECH",
                avatarColor = Color(0xFF06B6D4)
            ),
            MockSocialPost(
                id = 3,
                authorName = "SinglesNearMe",
                authorTag = "@dating_match",
                text = "Looking for beautiful companions? Enter unrestricted adult single chatrooms nearby with zero login constraints! 💋💋",
                explanation = "Blocked by rule word search: 'dating', 'adult', 'chatrooms'",
                isRisky = true,
                type = "DATING",
                avatarColor = Color(0xFFEF4444)
            ),
            MockSocialPost(
                id = 4,
                authorName = "MountainTrails",
                authorTag = "@nature_lovers",
                text = "Had a breathtaking sunset climb of the emerald peak today! Hiking trails connect us with nature sanity. 🌄🌲",
                explanation = "Clean wholesome hobby feed, allowed.",
                isRisky = false,
                type = "NATURE",
                avatarColor = Color(0xFF10B981)
            )
        )
    }

    var activePostIndex by remember { mutableStateOf(0) }
    val currentPost = presetPosts[activePostIndex]

    var customText by remember { mutableStateOf(currentPost.text) }
    var selectedApp by remember { mutableStateOf("kidsShiield Feed") }
    var useGemini by remember { mutableStateOf(settings["gemini_scan_on"] == "true") }

    // Laser scanning horizontal line variables
    val infiniteTransition = rememberInfiniteTransition(label = "laser_scrolling")
    val laserOffsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_offset"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text(
                text = "Child Protection Sandbox",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextLight
            )
            Text(
                text = "Simulate active app browser views to inspect how our localized constraints and AI models detect & lock violations.",
                fontSize = 11.sp,
                color = TextMuted
            )
        }

        // Row Selector of Presets
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "1. Select Simulated Feed Preset",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanGlow
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presetPosts.forEachIndexed { idx, post ->
                            val active = activePostIndex == idx
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (active) CyanGlow.copy(alpha = 0.15f) else SlateDark)
                                    .border(1.dp, if (active) CyanGlow else BorderDark, RoundedCornerShape(8.dp))
                                    .clickable {
                                        activePostIndex = idx
                                        customText = post.text
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = when(post.type) {
                                            "CASINO" -> Icons.Default.Warning
                                            "DATING" -> Icons.Default.Warning
                                            "TECH" -> Icons.Default.Build
                                            else -> Icons.Default.Place
                                        },
                                        contentDescription = null,
                                        tint = if (post.isRisky) CoralAlert else EmeraldShield,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = post.type,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (post.isRisky) CoralAlert else EmeraldShield
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Virtual Phone Simulator Device frame
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(3.2.dp, BorderDark, RoundedCornerShape(24.dp))
                    .padding(6.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.background(SlateDark)) {
                    // Phone status bar mockup
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("10:14 AM", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextLight, fontFamily = FontFamily.Monospace)
                        // Notch
                        Box(
                            modifier = Modifier
                                .width(70.dp)
                                .height(12.dp)
                                .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                                .background(BorderDark)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = EmeraldShield, modifier = Modifier.size(10.dp))
                            Icon(Icons.Default.Lock, contentDescription = null, tint = CyanGlow, modifier = Modifier.size(10.dp))
                        }
                    }

                    // Virtual App Navigation header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CardDark)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = CyanGlow, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "kidsShiield Social Feed",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = TextLight
                            )
                        }

                        Box(
                            modifier = Modifier
                                .background(if (simulationResult == "SAFE" || currentPost.isRisky == false && simulationResult == null) EmeraldShield.copy(alpha = 0.15f) else CoralAlert.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (simulationResult == "BLOCKED" || (currentPost.isRisky && simulationResult != "SAFE")) "SHIELD ACTIVE" else "CLEAN MONITORED",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                color = if (simulationResult == "BLOCKED" || (currentPost.isRisky && simulationResult != "SAFE")) CoralAlert else EmeraldShield
                            )
                        }
                    }

                    // Interactive post container
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .background(CardDark)
                            .padding(12.dp)
                    ) {
                        // Instagram-style Profile Card & image mock setup
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(currentPost.avatarColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = currentPost.authorName.take(1),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextLight
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(text = currentPost.authorName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextLight)
                                    Text(text = currentPost.authorTag, fontSize = 9.sp, color = TextMuted)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Post text
                            Text(
                                text = customText,
                                fontSize = 11.5.sp,
                                color = TextLight,
                                lineHeight = 16.sp,
                                modifier = Modifier.weight(1f)
                            )

                            // Post Bottom Action bar mockup style
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Default.Favorite, contentDescription = null, tint = CoralAlert, modifier = Modifier.size(14.dp))
                                    Icon(Icons.Default.Send, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                                }
                                Text("Feed ID: #${currentPost.id}S", fontSize = 8.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                            }
                        }

                        // Fluorescent Scanning Line while analyzing
                        if (isAnalyzing) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(0.02f)
                                    .align(Alignment.TopCenter)
                                    .offset(y = 200.dp * laserOffsetY)
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(Color.Transparent, CyanGlow, Color.Transparent)
                                        )
                                    )
                            )
                        }

                        // Elegant Glass Blur Intercept Overlay if simulations block it
                        val activeBlocked = simulationResult == "BLOCKED" || (simulationResult == null && currentPost.isRisky && settings["child_mode"] == "true")
                        if (activeBlocked) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(CardDark.copy(alpha = 0.94f))
                                    .border(1.dp, CoralAlert, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(14.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(CoralAlert.copy(alpha = 0.2f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Lock, contentDescription = null, tint = CoralAlert, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "KIDS SHIELD LOCKED",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = CoralAlert,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Unsafe text capture detected in social app. Feed was blurred on device, protecting child. Violation alerts logged to parent dashboard.",
                                        fontSize = 10.sp,
                                        color = TextMuted,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Adjustable control board below
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardDark, RoundedCornerShape(12.dp))
                    .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "2. Customize & Command Test",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyanGlow
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = customText,
                    onValueChange = { customText = it },
                    label = { Text("Simulated On-Screen Feed Text", fontSize = 11.sp) },
                    textStyle = TextStyle(fontSize = 12.sp, color = TextLight),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("simulation_post_input"),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanGlow,
                        unfocusedBorderColor = BorderDark,
                        focusedLabelColor = CyanGlow,
                        unfocusedLabelColor = TextMuted
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Action Switch Configuration
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SlateDark)
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = if (useGemini) AmberWarn else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Employ Server Gemini AI Scan", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextLight)
                        Text("Uses neural models to read complex hazardous intents.", fontSize = 9.sp, color = TextMuted)
                    }
                    Switch(
                        checked = useGemini,
                        onCheckedChange = { useGemini = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TextLight,
                            checkedTrackColor = AmberWarn,
                            uncheckedTrackColor = BorderDark
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scan Button
                Button(
                    onClick = {
                        viewModel.runInteractiveSimulation(
                            targetApp = selectedApp,
                            screenCaption = "Manual user sandbox capture verification.",
                            textToAnalyze = customText,
                            isGeminiScan = useGemini
                        )
                    },
                    enabled = !isAnalyzing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("run_sandbox_btn"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (useGemini) AmberWarn else CyanGlow,
                        disabledContainerColor = BorderDark
                    )
                ) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(
                            color = SlateDark,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scanning simulated viewport...", color = SlateDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = SlateDark, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (useGemini) "Run Server AI Screen Scan" else "Run Standard Rules Search",
                            color = SlateDark,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (simulationResult != null) {
            item {
                AnimatedVisibility(
                    visible = simulationResult != null,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    SimulationResultOverlay(
                        result = simulationResult!!,
                        selectedApp = selectedApp,
                        onDismiss = { viewModel.dismissSimulationResult() }
                    )
                }
            }
        }
    }
}

// Intercept Screen overlay card
@Composable
fun SimulationResultOverlay(
    result: String,
    selectedApp: String,
    onDismiss: () -> Unit
) {
    val passed = result == "SAFE"

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (passed) EmeraldShield.copy(alpha = 0.12f) else CoralAlert.copy(alpha = 0.12f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.5.dp,
                if (passed) EmeraldShield else CoralAlert,
                RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (passed) EmeraldShield.copy(alpha = 0.2f) else CoralAlert.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (passed) Icons.Default.Check else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (passed) EmeraldShield else CoralAlert,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (passed) "Screen Verified Clean!" else "CHILD CONTENT SHIELD BLOCK",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (passed) EmeraldShield else CoralAlert,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (passed) {
                    "This feed is suitable for child browsing. kidsShiield allowed rendering for $selectedApp."
                } else {
                    "Flagged content intercepted! Screen was temporarily blurred & locked. Alert logged successfully."
                },
                fontSize = 11.sp,
                color = TextLight,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (passed) EmeraldShield else CoralAlert
                ),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Text("Acknowledge", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SlateDark)
            }
        }
    }
}

// --- Parent settings Tab ---
@Composable
fun ParentSettingsTab(
    viewModel: ShieldViewModel,
    settings: Map<String, String>,
    blockedRules: List<BlockedRule>,
    onLockDashboard: () -> Unit
) {
    val context = LocalContext.current
    var blockInput by remember { mutableStateOf("") }
    var selectRuleType by remember { mutableStateOf("KEYWORD") } // "KEYWORD" or "URL"

    var newPinInput by remember { mutableStateOf("") }
    var textTimeSlider by remember { mutableStateOf((settings["screen_time_limit_mins"] ?: "90").toFloat()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Parent Dashboard Controls", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextLight)
                    Text(text = "Adjust rules, system constraints, password locks, and limits.", fontSize = 11.sp, color = TextMuted)
                }

                IconButton(
                    onClick = onLockDashboard,
                    modifier = Modifier
                        .size(36.dp)
                        .background(CardDark, CircleShape)
                        .border(1.dp, BorderDark, CircleShape)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = "Lock panel", tint = CyanGlow, modifier = Modifier.size(16.dp))
                }
            }
        }

        // Section: Gemini AI Control Panel
        item {
            val geminiActive = settings["gemini_scan_on"] == "true"
            val childModeActive = settings["child_mode"] == "true"
            val isEngineRunning = geminiActive && childModeActive

            // Infinite pulsing animation for the active indicator dot
            val infiniteTransition = rememberInfiniteTransition(label = "pulse_animation")
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse_alpha"
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(
                            colors = if (isEngineRunning) {
                                listOf(CyanGlow.copy(alpha = 0.7f), BorderDark)
                            } else {
                                listOf(BorderDark, BorderDark)
                            }
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Header with title and engine status label
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = if (isEngineRunning) CyanGlow else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Gemini AI Core Protection",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextLight
                            )
                        }

                        // Engine Status Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isEngineRunning) EmeraldShield.copy(alpha = 0.12f)
                                    else CoralAlert.copy(alpha = 0.12f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isEngineRunning) EmeraldShield.copy(alpha = 0.5f)
                                    else CoralAlert.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isEngineRunning) EmeraldShield.copy(alpha = pulseAlpha)
                                            else CoralAlert
                                        )
                                )
                                Text(
                                    text = if (isEngineRunning) "ACTIVE" else "STANDBY",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (isEngineRunning) EmeraldShield else CoralAlert
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Real-time viewport analyzer. Automatically scans simulated feeds and active viewports, triggering parent-defined blocks and neural guardrails.",
                        fontSize = 11.sp,
                        color = TextMuted,
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Toggle row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SlateDark, RoundedCornerShape(8.dp))
                            .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
                            .clickable {
                                viewModel.setGeminiScanning(!geminiActive)
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Gemini Screen Scanning",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextLight
                            )
                            Text(
                                text = if (geminiActive) "AI scanning on screen text is enabled" else "AI scanning is offline (Offline logic active)",
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }

                        Switch(
                            checked = geminiActive,
                            onCheckedChange = { viewModel.setGeminiScanning(it) },
                            modifier = Modifier.testTag("gemini_ai_control_toggle"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = TextLight,
                                checkedTrackColor = CyanGlow,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = BorderDark
                            )
                        )
                    }
                }
            }
        }

        // Section: Add Block constraints
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = "Unsafe Domain & Word Blocks", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyanGlow)
                    Text(text = "Add specific keywords or web urls to block entirely.", fontSize = 9.sp, color = TextMuted)

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("KEYWORD", "URL").forEach { type ->
                            val active = selectRuleType == type
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (active) CyanGlow.copy(alpha = 0.15f) else SlateDark)
                                    .border(1.dp, if (active) CyanGlow else BorderDark, RoundedCornerShape(6.dp))
                                    .clickable { selectRuleType = type }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (type == "URL") "URL / Web Domain" else "Keyword / Phrase",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (active) CyanGlow else TextMuted
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = blockInput,
                            onValueChange = { blockInput = it },
                            placeholder = { Text(if (selectRuleType == "URL") "e.g. gambling-site.com" else "e.g. dating", fontSize = 11.sp) },
                            textStyle = TextStyle(fontSize = 12.sp, color = TextLight),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("block_rule_input"),
                            shape = RoundedCornerShape(6.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanGlow,
                                unfocusedBorderColor = BorderDark
                            )
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Button(
                            onClick = {
                                if (blockInput.isNotBlank()) {
                                    viewModel.addBlockRule(blockInput, selectRuleType)
                                    blockInput = ""
                                    Toast.makeText(context, "Shield rule appended.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CyanGlow),
                            modifier = Modifier
                                .height(44.dp)
                                .testTag("add_rule_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add rule", tint = SlateDark, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(text = "Currently Active Rules", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextLight)

                    if (blockedRules.isEmpty()) {
                        Text(text = "No custom rules configured yet.", fontSize = 10.sp, color = TextMuted, modifier = Modifier.padding(top = 4.dp))
                    } else {
                        // Display active rules flow list flow
                        Spacer(modifier = Modifier.height(6.dp))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            blockedRules.forEach { rule ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SlateDark, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (rule.ruleType == "URL") CyanGlow.copy(alpha = 0.2f) else AmberWarn.copy(alpha = 0.2f),
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = rule.ruleType,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (rule.ruleType == "URL") CyanGlow else AmberWarn
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Text(
                                        text = rule.value,
                                        fontSize = 11.sp,
                                        color = TextLight,
                                        modifier = Modifier.weight(1f)
                                    )

                                    IconButton(
                                        onClick = { viewModel.removeBlockRule(rule.id, rule.value) },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove rule", tint = CoralAlert, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section: General Parental Limits
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = "Screen Constraint & Controls", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyanGlow)

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Daily App Time Limit: ${textTimeSlider.toInt()} Minutes",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextLight
                    )

                    Slider(
                        value = textTimeSlider,
                        onValueChange = {
                            textTimeSlider = it
                            viewModel.updateScreenTime(it.toInt())
                        },
                        valueRange = 15f..180f,
                        steps = 11,
                        colors = SliderDefaults.colors(
                            thumbColor = CyanGlow,
                            activeTrackColor = CyanGlow,
                            inactiveTrackColor = BorderDark
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Screen Time progress
                    val usedTime = (settings["screen_time_used_mins"] ?: "0").toInt()
                    LinearProgressIndicator(
                        progress = { usedTime.toFloat() / textTimeSlider },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (usedTime > textTimeSlider) CoralAlert else CyanGlow,
                        trackColor = BorderDark
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Used: $usedTime Mins", fontSize = 10.sp, color = TextMuted)
                        Text(text = "Remaining: ${maxOf(0, textTimeSlider.toInt() - usedTime)} Mins", fontSize = 10.sp, color = TextMuted)
                    }
                }
            }
        }

        // Section: Administrative Configs
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = "Administrative Security", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyanGlow)

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = newPinInput,
                            onValueChange = { newPinInput = it },
                            placeholder = { Text("Set New Parent PIN", fontSize = 11.sp) },
                            textStyle = TextStyle(fontSize = 12.sp, color = TextLight),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("new_pin_input"),
                            shape = RoundedCornerShape(6.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanGlow,
                                unfocusedBorderColor = BorderDark
                            )
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                if (newPinInput.length >= 4) {
                                    viewModel.updatePin(newPinInput)
                                    newPinInput = ""
                                    Toast.makeText(context, "PIN code updated.", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "PIN must be 4+ digits.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CyanGlow),
                            modifier = Modifier.testTag("save_pin_btn")
                        ) {
                            Text("Update", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SlateDark)
                        }
                    }
                }
            }
        }
    }
}

// Parent PIN Verification Dialog
@Composable
fun PinAuthDialog(
    onDismiss: () -> Unit,
    onAuthPassed: () -> Unit,
    viewModel: ShieldViewModel
) {
    var pinValue by remember { mutableStateOf("") }
    var triedCount by remember { mutableStateOf(0) }
    var pinError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    if (viewModel.verifyPin(pinValue)) {
                        onAuthPassed()
                    } else {
                        triedCount++
                        pinError = true
                        pinValue = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyanGlow)
            ) {
                Text("Confirm", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SlateDark)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = TextMuted)) {
                Text("Cancel", fontSize = 11.sp)
            }
        },
        title = {
            Text(
                text = "Parental Authorization",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextLight
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Please enter your 4-digit Parent PIN to open dash parameters.",
                    fontSize = 11.sp,
                    color = TextMuted,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = pinValue,
                    onValueChange = { if (it.length <= 6) pinValue = it },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 16.sp, color = TextLight, textAlign = TextAlign.Center),
                    modifier = Modifier
                        .width(140.dp)
                        .testTag("pin_code_input"),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanGlow,
                        unfocusedBorderColor = BorderDark
                    )
                )

                if (pinError) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Invalid PIN digit key (Try: 1234)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = CoralAlert
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Default sandbox PIN is 1234",
                    fontSize = 9.sp,
                    color = TextMuted
                )
            }
        },
        containerColor = CardDark,
        shape = RoundedCornerShape(16.dp)
    )
}

