package com.aqualevel.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Colours ────────────────────────────────────────────────────────────────
private val Primary   = Color(0xFF1CB0F6)
private val Navy      = Color(0xFF0A1628)
private val Success   = Color(0xFF58CC02)
private val BgGrad    = Brush.verticalGradient(listOf(Color(0xFFF0F7FF), Color(0xFFFFFFFF)))

@Composable
fun PairDeviceScreen(
    onPairSuccess: () -> Unit,
    viewModel: PairDeviceViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var selectedTab   by remember { mutableIntStateOf(0) }  // 0 = Scan QR, 1 = Enter Code
    var tankName      by remember { mutableStateOf("") }
    var manualCode    by remember { mutableStateOf("") }
    var showScanner   by remember { mutableStateOf(false) }

    // Deep-link / scan success navigates immediately
    LaunchedEffect(uiState) {
        if (uiState is PairUiState.Success) onPairSuccess()
    }

    // ── Full-screen scanner mode ──────────────────────────────────────────────
    if (showScanner) {
        QrScannerScreen(
            onCodeScanned = { rawUrl ->
                showScanner = false
                viewModel.onQrScanned(rawUrl)
            }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgGrad)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── Header ────────────────────────────────────────────────────────
            Text("Add AquaLevel Device", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Navy)
            Spacer(Modifier.height(6.dp))
            Text(
                "Scan the QR code on your ESP32\nor enter the code manually",
                fontSize = 14.sp, color = Color(0xFF777777), textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))

            // ── Tab Row ───────────────────────────────────────────────────────
            TabRow(
                selectedTabIndex    = selectedTab,
                containerColor      = Color.White,
                contentColor        = Primary,
                indicator           = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color    = Primary
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .border(1.dp, Color(0xFFE5E5E5), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick  = { selectedTab = 0; viewModel.reset() },
                    icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    text = { Text("Scan QR", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick  = { selectedTab = 1; viewModel.reset() },
                    icon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    text = { Text("Enter Code", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                )
            }

            // ── Tab Content ───────────────────────────────────────────────────
            Card(
                shape  = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState  = selectedTab,
                    transitionSpec = {
                        (fadeIn() + slideInVertically()).togetherWith(fadeOut() + slideOutVertically())
                    },
                    label = "tab_content"
                ) { tab ->
                    when (tab) {
                        0 -> ScanTab(
                            uiState    = uiState,
                            tankName   = tankName,
                            onTankName = { tankName = it },
                            onScan     = { showScanner = true },
                            onConfirm  = { viewModel.pairDevice(tankName, onPairSuccess) },
                            onReset    = { viewModel.reset() },
                            onQrImageSelected = { uri -> viewModel.onQrImageSelected(context, uri) }
                        )
                        else -> ManualTab(
                            uiState    = uiState,
                            manualCode = manualCode,
                            tankName   = tankName,
                            onCode     = { manualCode = it },
                            onTankName = { tankName = it },
                            onLookup   = { viewModel.onManualDeviceId(manualCode) },
                            onConfirm  = { viewModel.pairDevice(tankName, onPairSuccess) },
                            onReset    = { viewModel.reset() }
                        )
                    }
                }
            }

            // ── Error message ─────────────────────────────────────────────────
            AnimatedVisibility(visible = uiState is PairUiState.Error) {
                val msg = (uiState as? PairUiState.Error)?.message ?: ""
                Spacer(Modifier.height(16.dp))
                Card(
                    shape  = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text     = msg,
                        color    = Color(0xFFCC0000),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

// ── Scan QR Tab ───────────────────────────────────────────────────────────────
@Composable
private fun ScanTab(
    uiState:    PairUiState,
    tankName:   String,
    onTankName: (String) -> Unit,
    onScan:     () -> Unit,
    onConfirm:  () -> Unit,
    onReset:    () -> Unit,
    onQrImageSelected: (android.net.Uri) -> Unit
) {
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) onQrImageSelected(uri)
    }

    Column(
        modifier = Modifier.padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (uiState) {
            is PairUiState.Idle, is PairUiState.Error -> {
                // ── Prompt to scan ────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEAF7FF))
                        .clickable { onScan() },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.QrCodeScanner,
                            contentDescription = "Scan",
                            tint     = Primary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Tap to scan", color = Primary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    "Point your camera at the QR label\nstuck on the ESP32 device.",
                    textAlign = TextAlign.Center,
                    fontSize  = 13.sp,
                    color     = Color(0xFF555555)
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick  = onScan,
                    colors   = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape    = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.Default.QrCodeScanner, null, tint = Color.White)
                    Text("  Open Camera Scanner", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick  = { galleryLauncher.launch("image/*") },
                    colors   = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape    = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .border(1.dp, Primary, RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.Default.PhotoLibrary, null, tint = Primary)
                    Text("  Upload QR Image", color = Primary, fontWeight = FontWeight.Bold)
                }
            }

            is PairUiState.Loading -> {
                Spacer(Modifier.height(24.dp))
                CircularProgressIndicator(color = Primary)
                Spacer(Modifier.height(12.dp))
                Text("Looking up device…", color = Color(0xFF777777), fontSize = 13.sp)
                Spacer(Modifier.height(24.dp))
            }

            is PairUiState.DeviceFound -> DeviceFoundCard(
                device   = uiState.device,
                tankName = tankName,
                onTankName = onTankName,
                onConfirm  = onConfirm,
                onReset    = onReset
            )

            is PairUiState.Success -> SuccessBanner()
        }
    }
}

// ── Manual Entry Tab ──────────────────────────────────────────────────────────
@Composable
private fun ManualTab(
    uiState:    PairUiState,
    manualCode: String,
    tankName:   String,
    onCode:     (String) -> Unit,
    onTankName: (String) -> Unit,
    onLookup:   () -> Unit,
    onConfirm:  () -> Unit,
    onReset:    () -> Unit
) {
    Column(modifier = Modifier.padding(20.dp)) {
        when (uiState) {
            is PairUiState.Idle, is PairUiState.Error -> {
                Text("Device ID", fontWeight = FontWeight.SemiBold, color = Navy, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value         = manualCode,
                    onValueChange = { onCode(it.uppercase()) },
                    placeholder   = { Text("AQL-XXXXXXXX", color = Color(0xFFAAAAAA)) },
                    shape         = RoundedCornerShape(12.dp),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Primary,
                        unfocusedBorderColor = Color(0xFFE5E5E5)
                    ),
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Find this code on the label on your ESP32,\nor check the Arduino Serial Monitor output.",
                    fontSize = 12.sp, color = Color(0xFF777777)
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick  = onLookup,
                    colors   = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape    = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled  = manualCode.length >= 12
                ) {
                    Text("Find Device", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            is PairUiState.Loading -> {
                Spacer(Modifier.height(24.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
                Spacer(Modifier.height(12.dp))
                Text("Looking up device…", color = Color(0xFF777777), fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
            }

            is PairUiState.DeviceFound -> DeviceFoundCard(
                device     = uiState.device,
                tankName   = tankName,
                onTankName = onTankName,
                onConfirm  = onConfirm,
                onReset    = onReset
            )

            is PairUiState.Success -> SuccessBanner()
        }
    }
}

// ── Shared sub-composables ────────────────────────────────────────────────────

@Composable
private fun DeviceFoundCard(
    device:    Device,
    tankName:  String,
    onTankName: (String) -> Unit,
    onConfirm: () -> Unit,
    onReset:   () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "found_scale"
    )

    Column(
        modifier = Modifier.scale(scale),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Status indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFEAFFF0))
                .padding(12.dp)
        ) {
            Icon(Icons.Default.CheckCircle, null, tint = Success, modifier = Modifier.size(24.dp))
            Column {
                Text("Device Found!", color = Success, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(device.id, color = Color(0xFF555555), fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.Wifi, null, tint = if (device.status == "online") Success else Color.Gray,
                modifier = Modifier.size(20.dp))
        }

        Spacer(Modifier.height(16.dp))

        // Tank name input
        Text("Name this tank", fontWeight = FontWeight.SemiBold, color = Navy, fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value         = tankName,
            onValueChange = onTankName,
            label         = { Text("e.g. Overhead Tank, Ground Tank…") },
            shape         = RoundedCornerShape(12.dp),
            colors        = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = Primary,
                unfocusedBorderColor = Color(0xFFE5E5E5)
            ),
            singleLine    = true,
            modifier      = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(20.dp))

        Button(
            onClick  = onConfirm,
            colors   = ButtonDefaults.buttonColors(containerColor = Primary),
            shape    = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled  = tankName.isNotBlank()
        ) {
            Text("Confirm & Link Tank", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        Spacer(Modifier.height(12.dp))

        Text(
            "Scan a different code",
            color    = Primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable { onReset() }
        )
    }
}

@Composable
private fun SuccessBanner() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 24.dp)
    ) {
        Icon(Icons.Default.CheckCircle, null, tint = Success, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(12.dp))
        Text("Tank Linked!", color = Success, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Text("Navigating to dashboard…", color = Color(0xFF777777), fontSize = 13.sp)
    }
}