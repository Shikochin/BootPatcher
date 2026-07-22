package dev.shiko.bootpatcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BootPatcherTheme {
                val model: PatcherViewModel = viewModel()
                val state by model.state.collectAsStateWithLifecycle()
                BootPatcherApp(
                    state = state,
                    onBootSelected = model::selectBoot,
                    onKernelSelected = model::selectKernel,
                    onOutputDirectorySelected = model::selectOutputDirectory,
                    onOutputFileNameChanged = model::updateOutputFileName,
                    onOutputFileNameCommit = model::normalizeOutputFileName,
                    onPatch = model::patch,
                    onDismissResult = model::dismissResult,
                )
            }
        }
    }
}

private enum class AppScreen {
    PATCHER,
    SETTINGS,
}

@Composable
private fun BootPatcherApp(
    state: PatcherUiState,
    onBootSelected: (Uri) -> Unit,
    onKernelSelected: (Uri) -> Unit,
    onOutputDirectorySelected: (Uri) -> Unit,
    onOutputFileNameChanged: (String) -> Unit,
    onOutputFileNameCommit: () -> Unit,
    onPatch: () -> Unit,
    onDismissResult: () -> Unit,
) {
    var screen by rememberSaveable { mutableStateOf(AppScreen.PATCHER) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsProgress = remember {
        Animatable(if (screen == AppScreen.SETTINGS) 0f else 1f)
    }

    val bootPicker = rememberLauncherForActivityResult(DocumentsUiOpenDocument()) { uri ->
        uri?.let(onBootSelected)
    }
    val kernelPicker = rememberLauncherForActivityResult(DocumentsUiOpenDocument()) { uri ->
        uri?.let(onKernelSelected)
    }
    val directoryPicker = rememberLauncherForActivityResult(DocumentsUiOpenTree()) { uri ->
        uri?.let(onOutputDirectorySelected)
    }

    fun openSettings() {
        if (screen == AppScreen.SETTINGS) return
        screen = AppScreen.SETTINGS
        scope.launch {
            settingsProgress.snapTo(1f)
            settingsProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = PAGE_TRANSITION_DURATION_MS,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
    }

    fun closeSettings() {
        if (screen != AppScreen.SETTINGS) return
        scope.launch {
            settingsProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = PAGE_TRANSITION_DURATION_MS,
                    easing = FastOutSlowInEasing,
                ),
            )
            onOutputFileNameCommit()
            screen = AppScreen.PATCHER
        }
    }

    PredictiveBackHandler(enabled = screen == AppScreen.SETTINGS) { progress ->
        try {
            progress.collect { backEvent ->
                settingsProgress.snapTo(backEvent.progress * PREDICTIVE_BACK_DRAG_LIMIT)
            }
            settingsProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = PREDICTIVE_BACK_COMPLETION_DURATION_MS,
                    easing = FastOutSlowInEasing,
                ),
            )
            onOutputFileNameCommit()
            screen = AppScreen.PATCHER
        } catch (_: CancellationException) {
            scope.launch {
                settingsProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val baseProgress = if (screen == AppScreen.SETTINGS) {
                        settingsProgress.value
                    } else {
                        1f
                    }
                    translationX = -size.width * 0.06f * (1f - baseProgress)
                },
        ) {
            PatcherScreen(
                state = state,
                onOpenSettings = ::openSettings,
                onPickBoot = {
                    launchDocumentsUi(context, Intent.ACTION_OPEN_DOCUMENT) {
                        bootPicker.launch(arrayOf("application/octet-stream", "*/*"))
                    }
                },
                onPickKernel = {
                    launchDocumentsUi(context, Intent.ACTION_OPEN_DOCUMENT) {
                        kernelPicker.launch(
                            arrayOf("application/zip", "application/octet-stream", "*/*"),
                        )
                    }
                },
                onPatch = onPatch,
                onDismissResult = onDismissResult,
            )
        }

        if (screen == AppScreen.SETTINGS) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = size.width * settingsProgress.value
                    },
            ) {
                SettingsScreen(
                    state = state,
                    onBack = ::closeSettings,
                    onPickDirectory = {
                        launchDocumentsUi(context, Intent.ACTION_OPEN_DOCUMENT_TREE) {
                            directoryPicker.launch(state.outputDirectory?.uri)
                        }
                    },
                    onOutputFileNameChanged = onOutputFileNameChanged,
                    onOutputFileNameCommit = onOutputFileNameCommit,
                )
            }
        }
    }
}

private const val PAGE_TRANSITION_DURATION_MS = 320
private const val PREDICTIVE_BACK_COMPLETION_DURATION_MS = 180
private const val PREDICTIVE_BACK_DRAG_LIMIT = 0.88f

private fun launchDocumentsUi(context: Context, action: String, launch: () -> Unit) {
    if (isDocumentsUiAvailable(context, action)) {
        launch()
    } else {
        Toast.makeText(context, R.string.documents_ui_unavailable, Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun PatcherScreen(
    state: PatcherUiState,
    onOpenSettings: () -> Unit,
    onPickBoot: () -> Unit,
    onPickKernel: () -> Unit,
    onPatch: () -> Unit,
    onDismissResult: () -> Unit,
) {
    PageContainer {
        AppHeader(onSettings = onOpenSettings)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        FileStep(
            number = 1,
            label = stringResource(R.string.boot_image),
            emptyLabel = stringResource(R.string.boot_image_empty),
            file = state.boot,
            enabled = state.operation != OperationState.PROCESSING,
            icon = { Icon(Icons.AutoMirrored.Rounded.InsertDriveFile, contentDescription = null) },
            onClick = onPickBoot,
        )
        FileStep(
            number = 2,
            label = stringResource(R.string.kernel_image),
            emptyLabel = stringResource(R.string.kernel_image_empty),
            file = state.kernel,
            enabled = state.operation != OperationState.PROCESSING,
            icon = { Icon(Icons.Rounded.Memory, contentDescription = null) },
            onClick = onPickKernel,
        )

        ActionArea(
            state = state,
            outputFileName = normalizeOutputFileName(state.outputFileName),
            onPatch = onPatch,
            onDismissResult = onDismissResult,
        )
    }
}

@Composable
private fun SettingsScreen(
    state: PatcherUiState,
    onBack: () -> Unit,
    onPickDirectory: () -> Unit,
    onOutputFileNameChanged: (String) -> Unit,
    onOutputFileNameCommit: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val focusManager = LocalFocusManager.current
    PageContainer {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.settings),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SettingsSectionTitle(stringResource(R.string.output_settings))
        SettingsFolderRow(
            directory = state.outputDirectory,
            enabled = state.operation != OperationState.PROCESSING,
            onClick = onPickDirectory,
        )
        OutlinedTextField(
            value = state.outputFileName,
            onValueChange = onOutputFileNameChanged,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.operation != OperationState.PROCESSING,
            singleLine = true,
            label = { Text(stringResource(R.string.output_file_name)) },
            supportingText = {
                Text(
                    stringResource(
                        R.string.final_output_name,
                        normalizeOutputFileName(state.outputFileName),
                    ),
                )
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                onOutputFileNameCommit()
                focusManager.clearFocus()
            }),
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingsSectionTitle(stringResource(R.string.about))
        Row(verticalAlignment = Alignment.Top) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Info, contentDescription = null)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.about_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = { uriHandler.openUri("https://github.com/topjohnwu/Magisk/tree/v30.7") },
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                ) {
                    Text(stringResource(R.string.magisk_source))
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PageContainer(content: @Composable ColumnScope.() -> Unit) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 680.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    content = content,
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun AppHeader(onSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(52.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_launcher_monochrome),
                contentDescription = null,
                modifier = Modifier.size(52.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = stringResource(R.string.app_name),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
        )
        IconButton(onClick = onSettings) {
            Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings))
        }
    }
}

@Composable
private fun FileStep(
    number: Int,
    label: String,
    emptyLabel: String,
    file: PickedFile?,
    enabled: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val borderColor by animateColorAsState(
        targetValue = if (file == null) {
            MaterialTheme.colorScheme.outlineVariant
        } else {
            MaterialTheme.colorScheme.primary
        },
        animationSpec = spring(),
        label = "file border",
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(28.dp),
                shape = CircleShape,
                color = if (file == null) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AnimatedContent(
                        targetState = file != null,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "step state",
                    ) { selected ->
                        if (selected) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        } else {
                            Text(number.toString(), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }

        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, borderColor),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 17.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) { icon() }
                }
                Spacer(Modifier.width(14.dp))
                AnimatedContent(
                    targetState = file,
                    modifier = Modifier.weight(1f),
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "selected file",
                ) { selected ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = selected?.name ?: emptyLabel,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = selected?.size?.let {
                                Formatter.formatShortFileSize(LocalContext.current, it)
                            } ?: stringResource(
                                if (selected == null) R.string.choose_file else R.string.unknown_size,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun SettingsFolderRow(
    directory: OutputDirectory?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 17.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.FolderOpen, contentDescription = null)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = directory?.name ?: stringResource(R.string.output_directory_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.output_directory),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
        }
    }
}

@Composable
private fun ActionArea(
    state: PatcherUiState,
    outputFileName: String,
    onPatch: () -> Unit,
    onDismissResult: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onPatch,
            enabled = state.canPatch,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            if (state.operation == OperationState.PROCESSING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Icon(Icons.Rounded.Save, contentDescription = null)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (state.operation == OperationState.PROCESSING) {
                    stringResource(R.string.working)
                } else {
                    stringResource(R.string.patch_and_save)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        AnimatedVisibility(
            visible = state.operation == OperationState.PROCESSING,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Text(
                text = stageLabel(state.stage),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AnimatedVisibility(
            visible = state.operation == OperationState.SUCCESS || state.operation == OperationState.ERROR,
        ) {
            val success = state.operation == OperationState.SUCCESS
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = if (success) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (success) Icons.Rounded.Check else Icons.Rounded.Close,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (success) {
                                stringResource(R.string.done, outputFileName)
                            } else {
                                stringResource(R.string.failed)
                            },
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (!success && state.error != null) {
                            Text(text = state.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    IconButton(onClick = onDismissResult) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.dismiss))
                    }
                }
            }
        }
    }
}

@Composable
private fun stageLabel(stage: PatchStage?): String = stringResource(
    when (stage) {
        PatchStage.COPYING_BOOT -> R.string.copying_boot
        PatchStage.READING_KERNEL -> R.string.reading_kernel
        PatchStage.UNPACKING -> R.string.unpacking
        PatchStage.REPLACING_KERNEL -> R.string.replacing_kernel
        PatchStage.REPACKING -> R.string.repacking
        PatchStage.SAVING -> R.string.saving
        null -> R.string.working
    },
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006C4B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF73FBC0),
    onPrimaryContainer = Color(0xFF002114),
    secondary = Color(0xFF4D6357),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFE9D9),
    onSecondaryContainer = Color(0xFF0A1F15),
    tertiary = Color(0xFF795900),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDF9A),
    onTertiaryContainer = Color(0xFF261A00),
    background = Color(0xFFF5FBF7),
    onBackground = Color(0xFF171D1A),
    surface = Color(0xFFF5FBF7),
    onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFFDCE5DF),
    onSurfaceVariant = Color(0xFF404944),
    outline = Color(0xFF707973),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF56DBA0),
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF005137),
    onPrimaryContainer = Color(0xFF73FBC0),
    secondary = Color(0xFFB3CCBE),
    onSecondary = Color(0xFF1F352A),
    secondaryContainer = Color(0xFF354B40),
    onSecondaryContainer = Color(0xFFCFE9D9),
    tertiary = Color(0xFFECC248),
    onTertiary = Color(0xFF3F2E00),
    tertiaryContainer = Color(0xFF5A4300),
    onTertiaryContainer = Color(0xFFFFDF9A),
    background = Color(0xFF0E1511),
    onBackground = Color(0xFFDEE4DF),
    surface = Color(0xFF0E1511),
    onSurface = Color(0xFFDEE4DF),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFBFC9C2),
    outline = Color(0xFF89938D),
)

@Composable
private fun BootPatcherTheme(content: @Composable () -> Unit) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val colors = if (dark) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
