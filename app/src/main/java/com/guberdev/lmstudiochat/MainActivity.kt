package com.guberdev.lmstudiochat

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── Themes ────────────────────────────────────────────────────────────────────

enum class AppTheme { LIGHT, DARK, CLAUDE }

val ClaudeBackground = Color(0xFFFAF9F6)
val ClaudeSurface = Color(0xFFFFFFFF)
val ClaudePrimary = Color(0xFFD97757)
val ClaudeOnBackground = Color(0xFF2C2A25)
val ClaudeUserBubble = Color(0xFFE8E5E1)

val claudeColorScheme = lightColorScheme(
    primary = ClaudePrimary,
    background = ClaudeBackground,
    surface = ClaudeSurface,
    onBackground = ClaudeOnBackground,
    onSurface = ClaudeOnBackground,
    surfaceVariant = Color(0xFFF0EBE1),
    primaryContainer = ClaudeUserBubble,
    secondaryContainer = Color.Transparent,
    onPrimaryContainer = ClaudeOnBackground,
    onSecondaryContainer = ClaudeOnBackground
)

val darkModernColorScheme = darkColorScheme(
    primary = Color(0xFF3B82F6),
    background = Color(0xFF141416),
    surface = Color(0xFF242428),
    surfaceVariant = Color(0xFF2B2B30),
    primaryContainer = Color(0xFF2C2C30),
    onPrimaryContainer = Color.White,
    secondaryContainer = Color.Transparent,
    onSecondaryContainer = Color(0xFFE0E0E0)
)

val lightModernColorScheme = lightColorScheme(
    primary = Color(0xFF007AFF),
    surfaceVariant = Color(0xFFF8F9FA),
    primaryContainer = Color(0xFFF0F0F0),
    secondaryContainer = Color.Transparent
)

// ── Activity ──────────────────────────────────────────────────────────────────

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
        setContent {
            var currentTheme by remember { mutableStateOf(AppTheme.DARK) }
            val viewModel: ChatViewModel = viewModel()
            val colorScheme = when (currentTheme) {
                AppTheme.LIGHT -> lightModernColorScheme
                AppTheme.DARK -> darkModernColorScheme
                AppTheme.CLAUDE -> claudeColorScheme
            }
            MaterialTheme(colorScheme = colorScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavigation(viewModel, currentTheme, onThemeChange = { currentTheme = it })
                }
            }
        }
    }
}

// ── Navigation ────────────────────────────────────────────────────────────────

enum class AppScreen { CONNECTIONS, CHAT }

@Composable
fun AppNavigation(viewModel: ChatViewModel, currentTheme: AppTheme, onThemeChange: (AppTheme) -> Unit) {
    var screen by remember { mutableStateOf(AppScreen.CONNECTIONS) }
    when (screen) {
        AppScreen.CONNECTIONS -> ConnectionScreen(viewModel, onStartChat = { screen = AppScreen.CHAT })
        AppScreen.CHAT -> ChatScreen(viewModel, currentTheme, onThemeChange, onBackToSettings = { screen = AppScreen.CONNECTIONS })
    }
}

// ── Connection Screen ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(viewModel: ChatViewModel, onStartChat: () -> Unit) {
    val connections by viewModel.connections.collectAsState()
    val activeConnection by viewModel.activeConnection.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    var showEditDialog by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<ConnectionProfile?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Your Hub", fontWeight = FontWeight.Bold, fontSize = 22.sp) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editingProfile = null; showEditDialog = true },
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) { Icon(Icons.Filled.Add, "Add", tint = MaterialTheme.colorScheme.onPrimary) }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(8.dp))
            Text("Compute Engines", fontWeight = FontWeight.W600, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
            Spacer(Modifier.height(16.dp))
            if (connections.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No connections saved. Tap + to deploy.", color = Color.Gray)
                }
            } else {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(connections) { profile ->
                        val isActive = profile.id == activeConnection?.id
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(24.dp)).clickable { viewModel.selectConnection(profile) },
                            elevation = CardDefaults.cardElevation(if (isActive) 8.dp else 0.dp),
                            colors = CardDefaults.cardColors(containerColor = if (isActive) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
                        ) {
                            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(48.dp).clip(RoundedCornerShape(16.dp))
                                        .background(if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Filled.Settings, null, tint = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) }
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(profile.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    Text("${profile.ip}:${profile.port}${if (profile.ngrokMode) " [ngrok]" else ""}", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                                }
                                IconButton(onClick = { editingProfile = profile; showEditDialog = true }) { Icon(Icons.Filled.Edit, "Edit", tint = Color.Gray) }
                                IconButton(onClick = { viewModel.deleteConnection(profile.id) }) { Icon(Icons.Filled.Delete, "Delete", tint = Color.Gray) }
                            }
                        }
                    }
                }
            }
            if (activeConnection != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(32.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).animateContentSize()
                ) {
                    Column(Modifier.padding(24.dp)) {
                        Text("Active: ${activeConnection?.name}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(12.dp))
                        when {
                            isLoading -> LinearProgressIndicator(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)))
                            !errorMessage.isNullOrEmpty() && availableModels.isEmpty() -> {
                                Text(errorMessage ?: "", color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = { viewModel.fetchModels() }, shape = RoundedCornerShape(12.dp)) { Text("Retry") }
                            }
                            availableModels.isNotEmpty() -> {
                                var expanded by remember { mutableStateOf(false) }
                                ExposedDropdownMenuBox(expanded, { expanded = it }) {
                                    OutlinedTextField(
                                        value = activeConnection?.selectedModel?.takeIf { it.isNotBlank() } ?: "Select Model",
                                        onValueChange = {}, readOnly = true, label = { Text("Model") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = Color.Transparent,
                                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                        )
                                    )
                                    ExposedDropdownMenu(expanded, { expanded = false }) {
                                        availableModels.forEach { model ->
                                            DropdownMenuItem(text = { Text(model.id) }, onClick = { viewModel.setModelForActiveConnection(model.id); expanded = false })
                                        }
                                    }
                                }
                                Spacer(Modifier.height(24.dp))
                                Button(
                                    onClick = onStartChat,
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    enabled = !activeConnection?.selectedModel.isNullOrEmpty()
                                ) { Text("Launch Interface", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        ConnectionEditDialog(
            profile = editingProfile,
            onDismiss = { showEditDialog = false },
            onSave = { viewModel.saveConnection(it); showEditDialog = false }
        )
    }
}

@Composable
fun ConnectionEditDialog(profile: ConnectionProfile?, onDismiss: () -> Unit, onSave: (ConnectionProfile) -> Unit) {
    var name by remember { mutableStateOf(profile?.name ?: "") }
    var ip by remember { mutableStateOf(profile?.ip ?: "") }
    var port by remember { mutableStateOf(profile?.port ?: "1234") }
    var ngrokMode by remember { mutableStateOf(profile?.ngrokMode ?: false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (profile == null) "Deploy Node" else "Configure Node", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Alias") }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = ip, onValueChange = { ip = it }, label = { Text("IP / Host") }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("Port") }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("ngrok mode", Modifier.weight(1f), fontSize = 14.sp)
                    Switch(checked = ngrokMode, onCheckedChange = { ngrokMode = it })
                }
                if (ngrokMode) Text("Adds ngrok-skip-browser-warning header", fontSize = 12.sp, color = Color.Gray)
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    profile?.copy(name = name, ip = ip, port = port, ngrokMode = ngrokMode)
                        ?: ConnectionProfile(UUID.randomUUID().toString(), name, ip, port, ngrokMode = ngrokMode)
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ── Chat Screen ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    currentTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    onBackToSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    val messages = viewModel.messages
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val activeConnection by viewModel.activeConnection.collectAsState()
    val pendingImages by viewModel.pendingImages.collectAsState()
    val listState = rememberLazyListState()

    var showHistorySheet by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showNewChatConfirm by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var clipboardHasImage by remember { mutableStateOf(false) }
    val currentSessionId by viewModel.currentSessionId.collectAsState()

    // TTS
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        tts = TextToSpeech(context) {}
        onDispose { tts?.shutdown() }
    }

    // Export
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        uri?.let {
            val content = viewModel.exportChatToMarkdown()
            context.contentResolver.openOutputStream(it)?.use { os -> os.write(content.toByteArray()) }
        }
    }

    // Image picker
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris.forEach { uri ->
            scope.launch {
                uriToBase64(context, uri)?.let { viewModel.addPendingImage(it) }
            }
        }
    }

    // Auto-scroll: fires on new messages and as streaming content grows
    LaunchedEffect(messages.size, messages.lastOrNull()?.content) {
        val targetIndex = messages.count { it.role != "system" } - 1
        if (targetIndex >= 0) listState.scrollToItem(targetIndex)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    val sessionTitle = activeConnection?.chatHistory
                        ?.firstOrNull { it.id == currentSessionId }?.title
                        ?: activeConnection?.name ?: "Chat"
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { showRenameDialog = true }
                    ) {
                        Text(sessionTitle, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(activeConnection?.selectedModel ?: "", style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = { IconButton(onClick = onBackToSettings) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { showHistorySheet = true }) { Icon(Icons.Filled.List, "History") }
                    IconButton(onClick = { showNewChatConfirm = true }) { Icon(Icons.Filled.Add, "New Chat") }
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                        DropdownMenu(showOverflowMenu, { showOverflowMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Export to .md") },
                                leadingIcon = { Icon(Icons.Filled.Share, null) },
                                onClick = { showOverflowMenu = false; exportLauncher.launch("chat-${System.currentTimeMillis()}.md") }
                            )
                            DropdownMenuItem(
                                text = { Text("Theme") },
                                leadingIcon = { Icon(Icons.Filled.Settings, null) },
                                onClick = { showOverflowMenu = false; showThemeDialog = true }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.95f))
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(32.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Column {
                    // Pending images preview strip
                    if (pendingImages.isNotEmpty()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(pendingImages) { index, base64 ->
                                PendingImageThumbnail(base64) { viewModel.removePendingImage(index) }
                            }
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                    }
                    Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        // Attach button with dropdown
                        Box {
                            IconButton(
                                onClick = {
                                    clipboardHasImage = hasClipboardImage(context)
                                    showAttachMenu = true
                                },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    Icons.Filled.AttachFile,
                                    "Attach",
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            DropdownMenu(showAttachMenu, { showAttachMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Choose from Gallery") },
                                    leadingIcon = { Icon(Icons.Filled.Image, null) },
                                    onClick = { showAttachMenu = false; imagePickerLauncher.launch("image/*") }
                                )
                                DropdownMenuItem(
                                    text = { Text("Paste from Clipboard") },
                                    leadingIcon = { Icon(Icons.Filled.ContentPaste, null) },
                                    enabled = clipboardHasImage,
                                    onClick = {
                                        showAttachMenu = false
                                        scope.launch {
                                            getClipboardImageUri(context)?.let { uri ->
                                                uriToBase64(context, uri)?.let { viewModel.addPendingImage(it) }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier.weight(1f).padding(start = 4.dp),
                            placeholder = { Text("Message LM Studio…", color = Color.Gray, fontSize = 16.sp) },
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent
                            )
                        )
                        Spacer(Modifier.width(4.dp))
                        Box(
                            modifier = Modifier.size(50.dp).clip(RoundedCornerShape(25.dp))
                                .background(if (isLoading) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                                .clickable {
                                    if (isLoading) viewModel.stopGeneration()
                                    else if (inputText.isNotBlank() || pendingImages.isNotEmpty()) {
                                        viewModel.sendMessage(inputText)
                                        inputText = ""
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoading) Icon(Icons.Filled.Close, "Stop", tint = Color.White, modifier = Modifier.size(24.dp))
                            else Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 8.dp)
        ) {
            items(messages.filter { it.role != "system" }) { message ->
                ChatBubble(message, currentTheme, tts)
            }
            if (isLoading && messages.lastOrNull()?.content?.isBlank() == true) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }
            if (errorMessage != null) {
                item {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(errorMessage ?: "", color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }
    }

    if (showHistorySheet) HistoryBottomSheet(viewModel, onDismiss = { showHistorySheet = false })
    if (showThemeDialog) ThemeDialog(currentTheme, { showThemeDialog = false }) { onThemeChange(it); showThemeDialog = false }
    if (showRenameDialog) {
        val currentTitle = activeConnection?.chatHistory
            ?.firstOrNull { it.id == currentSessionId }?.title ?: ""
        var newTitle by remember(currentTitle) { mutableStateOf(currentTitle) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Chat", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Chat name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.renameCurrentSession(newTitle.trim()); showRenameDialog = false },
                    enabled = newTitle.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") } }
        )
    }
    if (showNewChatConfirm) {
        AlertDialog(
            onDismissRequest = { showNewChatConfirm = false },
            title = { Text("New Chat") },
            text = { Text("Start a new conversation? Current chat is saved in history.") },
            confirmButton = { Button(onClick = { viewModel.startNewChat(); showNewChatConfirm = false }) { Text("New Chat") } },
            dismissButton = { TextButton(onClick = { showNewChatConfirm = false }) { Text("Cancel") } }
        )
    }
}

// ── History Bottom Sheet ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryBottomSheet(viewModel: ChatViewModel, onDismiss: () -> Unit) {
    val activeConnection by viewModel.activeConnection.collectAsState()
    val sessions = activeConnection?.chatHistory ?: emptyList()
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text("Chat History", fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(bottom = 16.dp))
            if (sessions.isEmpty()) {
                Text("No history yet.", color = Color.Gray, modifier = Modifier.padding(bottom = 32.dp))
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(sessions, key = { it.id }) { session ->
                        ListItem(
                            headlineContent = { Text(session.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(dateFormat.format(Date(session.createdAt)), color = Color.Gray, fontSize = 12.sp) },
                            trailingContent = {
                                IconButton(onClick = { viewModel.deleteSession(session.id) }) {
                                    Icon(Icons.Filled.Delete, "Delete", tint = Color.Gray)
                                }
                            },
                            modifier = Modifier.clickable { viewModel.loadSession(session); onDismiss() }
                        )
                        HorizontalDivider()
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Theme Dialog ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeDialog(currentTheme: AppTheme, onDismiss: () -> Unit, onSaveTheme: (AppTheme) -> Unit) {
    var selected by remember { mutableStateOf(currentTheme) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aesthetics", fontWeight = FontWeight.Bold) },
        text = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                AppTheme.values().forEach { theme ->
                    FilterChip(selected == theme, { selected = theme }, label = { Text(theme.name) })
                }
            }
        },
        confirmButton = { Button(onClick = { onSaveTheme(selected) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ── Chat Bubble ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(message: ChatMessage, theme: AppTheme, tts: TextToSpeech?) {
    val isUser = message.role == "user"
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    val backgroundColor = when {
        !isUser && theme == AppTheme.CLAUDE -> Color.Transparent
        isUser && theme == AppTheme.CLAUDE -> ClaudeUserBubble
        isUser -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val textColor = when {
        theme == AppTheme.CLAUDE -> ClaudeOnBackground
        isUser -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    val padding = if (!isUser && theme == AppTheme.CLAUDE) 8.dp else 16.dp
    val shape = if (isUser) RoundedCornerShape(24.dp, 24.dp, 4.dp, 24.dp) else RoundedCornerShape(24.dp, 24.dp, 24.dp, 4.dp)
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant

    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(Modifier.fillMaxWidth(0.9f), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
            Box(
                modifier = Modifier
                    .shadow(if (theme == AppTheme.CLAUDE) 0.dp else 2.dp, shape)
                    .background(backgroundColor, shape)
                    .padding(padding)
                    .combinedClickable(onClick = {}, onLongClick = { showMenu = true })
            ) {
                Column {
                    val imgs = message.images ?: emptyList()
                    imgs.forEach { base64 ->
                        val bitmap = remember(base64) {
                            try {
                                val bytes = Base64.decode(base64, Base64.DEFAULT)
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            } catch (_: Exception) { null }
                        }
                        bitmap?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "Attached image",
                                modifier = Modifier
                                    .sizeIn(maxWidth = 280.dp, maxHeight = 280.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Fit
                            )
                            if (message.content.isNotBlank()) Spacer(Modifier.height(6.dp))
                        }
                    }
                    if (!isUser && message.content.isNotBlank()) {
                        SimpleMarkdownText(message.content, textColor, codeBackground)
                    } else if (message.content.isNotBlank()) {
                        Text(message.content, fontSize = 16.sp, color = textColor, lineHeight = 24.sp)
                    }
                }
            }

            // TTS + copy row for assistant messages
            if (!isUser && message.content.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { tts?.stop(); tts?.speak(message.content, TextToSpeech.QUEUE_FLUSH, null, null) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Filled.VolumeUp, "Listen", tint = Color.Gray, modifier = Modifier.size(18.dp))
                    }
                }
            }

            DropdownMenu(showMenu, { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Copy text") },
                    onClick = {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText("Message", message.content))
                        showMenu = false
                    }
                )
            }
        }
    }
}

// ── Image helpers ─────────────────────────────────────────────────────────────

suspend fun uriToBase64(context: Context, uri: Uri, maxDimension: Int = 768): String? =
    withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val original = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            val scale = minOf(maxDimension.toFloat() / original.width, maxDimension.toFloat() / original.height, 1f)
            val scaled = if (scale < 1f)
                Bitmap.createScaledBitmap(original, (original.width * scale).toInt(), (original.height * scale).toInt(), true)
            else original
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 75, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) { null }
    }

fun hasClipboardImage(context: Context): Boolean {
    val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip ?: return false
    return (0 until clip.itemCount).any { i ->
        val item = clip.getItemAt(i)
        item.uri != null && context.contentResolver.getType(item.uri!!)?.startsWith("image/") == true
    } || clip.description.hasMimeType("image/*")
}

fun getClipboardImageUri(context: Context): Uri? {
    val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip ?: return null
    return (0 until clip.itemCount).firstNotNullOfOrNull { i ->
        val uri = clip.getItemAt(i)?.uri ?: return@firstNotNullOfOrNull null
        if (context.contentResolver.getType(uri)?.startsWith("image/") == true) uri else null
    }
}

@Composable
fun PendingImageThumbnail(base64: String, onRemove: () -> Unit) {
    val bitmap = remember(base64) {
        try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) { null }
    }
    Box(modifier = Modifier.size(64.dp)) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(Color.Gray))
        }
        Box(
            modifier = Modifier
                .size(18.dp)
                .align(Alignment.TopEnd)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(11.dp))
        }
    }
}

// ── Markdown ──────────────────────────────────────────────────────────────────

data class MdPart(val content: String, val isCode: Boolean)

fun splitCodeBlocks(text: String): List<MdPart> {
    val parts = mutableListOf<MdPart>()
    val regex = Regex("```(?:\\w*)\\n?([\\s\\S]*?)```")
    var last = 0
    regex.findAll(text).forEach { m ->
        if (m.range.first > last) parts.add(MdPart(text.substring(last, m.range.first), false))
        parts.add(MdPart(m.groupValues[1].trimEnd('\n'), true))
        last = m.range.last + 1
    }
    if (last < text.length) parts.add(MdPart(text.substring(last), false))
    return parts
}

fun parseInline(text: String): androidx.compose.ui.text.AnnotatedString = buildAnnotatedString {
    val lines = text.lines()
    lines.forEachIndexed { idx, rawLine ->
        if (idx > 0) append("\n")
        val line = rawLine.trimStart()
        when {
            line.startsWith("### ") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp)) { parseSpans(line.substring(4)) }
            line.startsWith("## ") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp)) { parseSpans(line.substring(3)) }
            line.startsWith("# ") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 19.sp)) { parseSpans(line.substring(2)) }
            line.startsWith("- ") || line.startsWith("* ") -> { append("• "); parseSpans(line.substring(2)) }
            line.matches(Regex("^\\d+\\.\\s.*")) -> parseSpans(line)
            else -> parseSpans(rawLine)
        }
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.parseSpans(t: String) {
    var i = 0
    while (i < t.length) {
        when {
            t.startsWith("**", i) -> {
                val e = t.indexOf("**", i + 2)
                if (e != -1) { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(t.substring(i + 2, e)) }; i = e + 2 }
                else { append(t[i]); i++ }
            }
            t.startsWith("__", i) -> {
                val e = t.indexOf("__", i + 2)
                if (e != -1) { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(t.substring(i + 2, e)) }; i = e + 2 }
                else { append(t[i]); i++ }
            }
            t[i] == '*' && (i + 1 >= t.length || t[i + 1] != '*') -> {
                val e = t.indexOf('*', i + 1)
                if (e != -1) { withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(t.substring(i + 1, e)) }; i = e + 1 }
                else { append(t[i]); i++ }
            }
            t[i] == '_' && (i == 0 || t[i - 1] == ' ') -> {
                val e = t.indexOf('_', i + 1)
                if (e != -1) { withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(t.substring(i + 1, e)) }; i = e + 1 }
                else { append(t[i]); i++ }
            }
            t[i] == '`' -> {
                val e = t.indexOf('`', i + 1)
                if (e != -1) { withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x22808080))) { append(t.substring(i + 1, e)) }; i = e + 1 }
                else { append(t[i]); i++ }
            }
            else -> { append(t[i]); i++ }
        }
    }
}

@Composable
fun SimpleMarkdownText(text: String, textColor: Color, codeBackground: Color, modifier: Modifier = Modifier) {
    val parts = remember(text) { splitCodeBlocks(text) }
    Column(modifier = modifier) {
        parts.forEach { part ->
            key(part.isCode, part.content.hashCode()) {
                if (part.isCode) {
                    Surface(
                        color = codeBackground.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = part.content,
                            color = textColor.copy(alpha = 0.9f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                } else if (part.content.isNotBlank()) {
                    Text(
                        text = remember(part.content) { parseInline(part.content) },
                        color = textColor,
                        fontSize = 16.sp,
                        lineHeight = 24.sp
                    )
                }
            }
        }
    }
}
