package af.shizuku.manager.settings

import af.shizuku.manager.R
import af.shizuku.manager.ShizukuSettings
import af.shizuku.manager.home.HomeActivity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.io.File
import java.io.FileOutputStream
import android.graphics.Color as AndroidColor

class CustomIconStudioBottomSheet : BottomSheetDialogFragment() {
    private var initialUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialUri = arguments?.getParcelable(ARG_URI)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                PreferenceComposeTheme {
                    StudioContent(
                        initialUri = initialUri,
                        onPinShortcut = { bitmap, appName ->
                            pinShortcut(bitmap, appName)
                            dismiss()
                        },
                        onSavePng = { bitmap, appName ->
                            savePngToStorage(bitmap, appName)
                        },
                        onCancel = { dismiss() },
                    )
                }
            }
        }

    private fun pinShortcut(
        bitmap: Bitmap,
        appName: String,
    ) {
        val context = requireContext()
        val finalName = if (appName.isNotBlank()) appName.trim() else "Shizuku+"
        try {
            // Save to app files for persistence
            val iconFile = File(context.filesDir, "custom_launcher_icon.png")
            FileOutputStream(iconFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            // Also synchronize custom app name across Shizuku settings
            ShizukuSettings.setCustomAppName(finalName)

            if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
                val intent =
                    Intent(context, HomeActivity::class.java).apply {
                        action = Intent.ACTION_MAIN
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                val pinShortcutInfo =
                    ShortcutInfoCompat
                        .Builder(context, "custom_shizuku_shortcut")
                        .setIcon(IconCompat.createWithBitmap(bitmap))
                        .setShortLabel(finalName)
                        .setLongLabel("$finalName Launcher")
                        .setIntent(intent)
                        .build()

                ShortcutManagerCompat.requestPinShortcut(context, pinShortcutInfo, null)
                Toast.makeText(context, R.string.custom_icon_studio_pinned_success, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, R.string.custom_icon_studio_pinned_error, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            timber.log.Timber.w(e, "Failed to pin custom icon shortcut")
            Toast.makeText(context, R.string.custom_icon_studio_pinned_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun savePngToStorage(
        bitmap: Bitmap,
        appName: String,
    ) {
        val context = requireContext()
        val safeName = appName.replace(Regex("[^a-zA-Z0-9_-]"), "_").lowercase()
        try {
            val downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "ShizukuPlus/Icons")
            if (!downloadDir.exists()) downloadDir.mkdirs()
            val file = File(downloadDir, "${safeName}_icon.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Toast.makeText(context, "${getString(R.string.custom_icon_studio_saved_success)}: ${file.name}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            timber.log.Timber.w(e, "Failed to save PNG to downloads")
            Toast.makeText(context, "Saved to app internal storage", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val ARG_URI = "arg_image_uri"

        fun newInstance(uri: Uri? = null): CustomIconStudioBottomSheet =
            CustomIconStudioBottomSheet().apply {
                if (uri != null) {
                    arguments =
                        Bundle().apply {
                            putParcelable(ARG_URI, uri)
                        }
                }
            }
    }
}

@Composable
private fun StudioContent(
    initialUri: Uri?,
    onPinShortcut: (Bitmap, String) -> Unit,
    onSavePng: (Bitmap, String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var customAppName by remember {
        mutableStateOf(ShizukuSettings.getCustomAppName(context))
    }
    var selectedPreset by remember { mutableStateOf<String?>("modern") }
    var customImageUri by remember { mutableStateOf(initialUri) }
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isMockupPreview by remember { mutableStateOf(false) }

    val galleryLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                customImageUri = uri
                selectedPreset = null
            }
        }

    // Load bitmap when preset or custom URI changes
    LaunchedEffect(selectedPreset, customImageUri) {
        val uri = customImageUri
        if (selectedPreset == null && uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }.onSuccess { sourceBitmap = it }
        } else {
            val resId =
                when (selectedPreset) {
                    "classic" -> R.drawable.ic_launcher_classic_foreground
                    "terminal" -> R.drawable.ic_terminal_24
                    "adb" -> R.drawable.ic_adb_24
                    "shield" -> R.drawable.ic_shield_24
                    "bolt" -> R.drawable.ic_bolt_24
                    "sparkle" -> R.drawable.ic_sparkle_24
                    "settings" -> R.drawable.ic_settings_suggest_24
                    "science" -> R.drawable.ic_science_24
                    else -> R.drawable.ic_launcher_foreground
                }
            sourceBitmap = drawableToBitmap(context, resId)
        }
    }

    var selectedShape by remember { mutableStateOf("squircle") }
    var selectedBg by remember { mutableStateOf("accent") }
    var paddingPercent by remember { mutableFloatStateOf(10f) }
    var nudgeX by remember { mutableFloatStateOf(0f) }
    var nudgeY by remember { mutableFloatStateOf(0f) }
    var rotationDegrees by remember { mutableFloatStateOf(0f) }
    var borderWidthDp by remember { mutableFloatStateOf(0f) }
    var selectedBorderColor by remember { mutableStateOf("none") }
    var monochromeTint by remember { mutableStateOf(false) }

    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHighest.toArgb()

    val renderedBitmap =
        remember(
            sourceBitmap,
            selectedShape,
            selectedBg,
            paddingPercent,
            nudgeX,
            nudgeY,
            rotationDegrees,
            borderWidthDp,
            selectedBorderColor,
            monochromeTint,
            primaryColor,
        ) {
            val src = sourceBitmap ?: return@remember null
            createModifiedIcon(
                source = src,
                shape = selectedShape,
                bgType = selectedBg,
                paddingPercent = paddingPercent,
                nudgeX = nudgeX,
                nudgeY = nudgeY,
                rotationDegrees = rotationDegrees,
                borderWidthDp = borderWidthDp,
                borderColorKey = selectedBorderColor,
                monochromeTint = monochromeTint,
                primaryColor = primaryColor,
                surfaceColor = surfaceColor,
            )
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Drag handle
        Box(
            modifier =
                Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_custom_icon_studio_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            FilledTonalIconToggleButton(
                checked = isMockupPreview,
                onCheckedChange = { isMockupPreview = it },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    painter = painterResource(if (isMockupPreview) R.drawable.ic_smartphone_24 else R.drawable.ic_window_24),
                    contentDescription = stringResource(R.string.custom_icon_studio_preview_mockup),
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Live Icon Preview Canvas
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .then(
                        if (isMockupPreview) {
                            Modifier.background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color(0xFF1A237E),
                                        Color(0xFF004D40),
                                        Color(0xFF0D1B2A),
                                    ),
                                ),
                            )
                        } else {
                            Modifier
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .border(1.2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(32.dp))
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (renderedBitmap != null) {
                    Image(
                        bitmap = renderedBitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(92.dp),
                    )
                    if (isMockupPreview) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = customAppName.ifBlank { "Shizuku+" },
                            style =
                                MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    shadow =
                                        androidx.compose.ui.graphics
                                            .Shadow(Color.Black, blurRadius = 6f),
                                ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // App Name Customization Field
        OutlinedTextField(
            value = customAppName,
            onValueChange = { customAppName = it },
            label = { Text(stringResource(R.string.custom_icon_studio_app_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            shape = RoundedCornerShape(16.dp),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Quick Suggestions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val suggestions = listOf("Shizuku+", "Shizuku", "S+", "Terminal", "Bridge")
            for (suggestion in suggestions) {
                SuggestionChip(
                    onClick = { customAppName = suggestion },
                    label = { Text(suggestion, fontSize = 11.sp) },
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Presets & Image Picker
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.custom_icon_studio_glyph_preset),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            )
            TextButton(onClick = { galleryLauncher.launch("image/*") }) {
                Icon(
                    painter = painterResource(R.drawable.ic_folder_24),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.custom_icon_studio_pick_gallery), fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val presets =
                listOf(
                    "modern" to "Plus",
                    "classic" to "Cat",
                    "terminal" to "CLI",
                    "adb" to "Adb",
                    "shield" to "Shield",
                    "bolt" to "Bolt",
                    "sparkle" to "AI",
                )
            for ((key, label) in presets) {
                FilterChip(
                    selected = selectedPreset == key,
                    onClick = {
                        selectedPreset = key
                        customImageUri = null
                    },
                    label = { Text(label, fontSize = 11.sp) },
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Shape Selection
        Text(
            text = stringResource(R.string.custom_icon_studio_shape),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val shapes =
                listOf(
                    "squircle" to stringResource(R.string.custom_icon_studio_shape_squircle),
                    "circle" to stringResource(R.string.custom_icon_studio_shape_circle),
                    "oneui" to stringResource(R.string.custom_icon_studio_shape_oneui),
                    "rounded" to stringResource(R.string.custom_icon_studio_shape_rounded),
                    "hexagon" to stringResource(R.string.custom_icon_studio_shape_hexagon),
                )
            for ((key, label) in shapes) {
                FilterChip(
                    selected = selectedShape == key,
                    onClick = { selectedShape = key },
                    label = { Text(label, fontSize = 11.sp) },
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Background Style
        Text(
            text = stringResource(R.string.custom_icon_studio_bg),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val backgrounds =
                listOf(
                    "accent" to "Accent",
                    "black" to "AMOLED",
                    "white" to "White",
                    "cyber" to "Cyber",
                    "sunset" to "Sunset",
                    "transparent" to "Clear",
                )
            for ((key, label) in backgrounds) {
                FilterChip(
                    selected = selectedBg == key,
                    onClick = { selectedBg = key },
                    label = { Text(label, fontSize = 11.sp) },
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Border & Accent Stroke
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.custom_icon_studio_border),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val borders = listOf("none" to "Off", "gold" to "Gold", "cyan" to "Cyan", "neon" to "Neon", "white" to "White")
                for ((bKey, bLabel) in borders) {
                    FilterChip(
                        selected = selectedBorderColor == bKey,
                        onClick = {
                            selectedBorderColor = bKey
                            if (bKey != "none" && borderWidthDp == 0f) borderWidthDp = 4f
                            if (bKey == "none") borderWidthDp = 0f
                        },
                        label = { Text(bLabel, fontSize = 10.sp) },
                    )
                }
            }
        }

        if (selectedBorderColor != "none") {
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = borderWidthDp,
                onValueChange = { borderWidthDp = it },
                valueRange = 2f..12f,
                steps = 4,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Fine Positioning (Padding & Scale, Nudge X/Y, Rotation)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.custom_icon_studio_scale),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = "${paddingPercent.toInt()}%",
                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.primary),
            )
        }
        Slider(
            value = paddingPercent,
            onValueChange = { paddingPercent = it },
            valueRange = 0f..30f,
            steps = 5,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.custom_icon_studio_rotation),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = "${rotationDegrees.toInt()}°",
                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.primary),
            )
        }
        Slider(
            value = rotationDegrees,
            onValueChange = { rotationDegrees = it },
            valueRange = -45f..45f,
            steps = 8,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Action Buttons: Save PNG & Pin to Home Screen
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = { renderedBitmap?.let { onSavePng(it, customAppName) } },
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_install_24),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Save PNG", fontSize = 12.sp)
            }
            Button(
                onClick = {
                    renderedBitmap?.let { onPinShortcut(it, customAppName) }
                },
                enabled = renderedBitmap != null,
                modifier = Modifier.weight(1.5f),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_sparkle_24),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.custom_icon_studio_pin_shortcut), fontSize = 12.sp)
            }
        }
    }
}

private fun drawableToBitmap(
    context: Context,
    resId: Int,
    size: Int = 512,
): Bitmap {
    val drawable = ContextCompat.getDrawable(context, resId) ?: return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, size, size)
    drawable.draw(canvas)
    return bitmap
}

private fun createModifiedIcon(
    source: Bitmap,
    shape: String,
    bgType: String,
    paddingPercent: Float,
    nudgeX: Float,
    nudgeY: Float,
    rotationDegrees: Float,
    borderWidthDp: Float,
    borderColorKey: String,
    monochromeTint: Boolean,
    primaryColor: Int,
    surfaceColor: Int,
): Bitmap {
    val size = 512
    val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())

    // 1. Draw shape background (solid or vibrant linear gradient)
    when (bgType) {
        "accent" -> {
            paint.shader = null
            paint.color = primaryColor
            drawShapedPath(canvas, rect, shape, paint)
        }
        "black" -> {
            paint.shader = null
            paint.color = AndroidColor.BLACK
            drawShapedPath(canvas, rect, shape, paint)
        }
        "white" -> {
            paint.shader = null
            paint.color = AndroidColor.WHITE
            drawShapedPath(canvas, rect, shape, paint)
        }
        "cyber" -> {
            paint.shader =
                LinearGradient(
                    0f,
                    0f,
                    size.toFloat(),
                    size.toFloat(),
                    intArrayOf(0xFF00E5FF.toInt(), 0xFF3F51B5.toInt()),
                    null,
                    Shader.TileMode.CLAMP,
                )
            drawShapedPath(canvas, rect, shape, paint)
            paint.shader = null
        }
        "sunset" -> {
            paint.shader =
                LinearGradient(
                    0f,
                    0f,
                    size.toFloat(),
                    size.toFloat(),
                    intArrayOf(0xFFFF4081.toInt(), 0xFFFF9800.toInt()),
                    null,
                    Shader.TileMode.CLAMP,
                )
            drawShapedPath(canvas, rect, shape, paint)
            paint.shader = null
        }
        "aurora" -> {
            paint.shader =
                LinearGradient(
                    0f,
                    0f,
                    size.toFloat(),
                    size.toFloat(),
                    intArrayOf(0xFF00BFA5.toInt(), 0xFF7C4DFF.toInt()),
                    null,
                    Shader.TileMode.CLAMP,
                )
            drawShapedPath(canvas, rect, shape, paint)
            paint.shader = null
        }
    }

    // 2. Prepare mask for icon content
    val maskBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val maskCanvas = Canvas(maskBitmap)
    val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.WHITE }
    drawShapedPath(maskCanvas, rect, shape, maskPaint)

    // 3. Draw scaled, positioned, and rotated icon content
    val inset = (size * (paddingPercent / 100f)).toInt()
    val offsetX = (size * (nudgeX / 100f)).toInt()
    val offsetY = (size * (nudgeY / 100f)).toInt()
    val destRect = Rect(inset + offsetX, inset + offsetY, (size - inset) + offsetX, (size - inset) + offsetY)

    val contentBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val contentCanvas = Canvas(contentBitmap)

    if (rotationDegrees != 0f) {
        val matrix = Matrix().apply { postRotate(rotationDegrees, size / 2f, size / 2f) }
        contentCanvas.concat(matrix)
    }

    val contentPaint =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            if (monochromeTint) {
                colorFilter = PorterDuffColorFilter(primaryColor, PorterDuff.Mode.SRC_IN)
            }
        }
    contentCanvas.drawBitmap(source, null, destRect, contentPaint)

    // 4. Apply mask to content if clear background
    if (bgType == "transparent") {
        maskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        contentCanvas.drawBitmap(maskBitmap, 0f, 0f, maskPaint)
    }

    canvas.drawBitmap(contentBitmap, 0f, 0f, null)

    // 5. Draw decorative border stroke if requested
    if (borderColorKey != "none" && borderWidthDp > 0f) {
        val strokePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = borderWidthDp * (size / 100f)
                color =
                    when (borderColorKey) {
                        "gold" -> 0xFFFFD700.toInt()
                        "cyan" -> 0xFF00E5FF.toInt()
                        "neon" -> 0xFFB388FF.toInt()
                        "white" -> AndroidColor.WHITE
                        else -> primaryColor
                    }
            }
        val strokeInset = strokePaint.strokeWidth / 2f
        val strokeRect = RectF(strokeInset, strokeInset, size - strokeInset, size - strokeInset)
        drawShapedPath(canvas, strokeRect, shape, strokePaint)
    }

    return output
}

private fun drawShapedPath(
    canvas: Canvas,
    rect: RectF,
    shape: String,
    paint: Paint,
) {
    val path = Path()
    when (shape) {
        "circle" -> path.addOval(rect, Path.Direction.CW)
        "squircle" -> path.addRoundRect(rect, 130f, 130f, Path.Direction.CW)
        "rounded" -> path.addRoundRect(rect, 70f, 70f, Path.Direction.CW)
        "oneui" -> path.addRoundRect(rect, 160f, 160f, Path.Direction.CW)
        "hexagon" -> {
            val cx = rect.centerX()
            val cy = rect.centerY()
            val r = rect.width() / 2f
            for (i in 0 until 6) {
                val angle = Math.toRadians((60 * i - 30).toDouble())
                val x = (cx + r * Math.cos(angle)).toFloat()
                val y = (cy + r * Math.sin(angle)).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
        }
        else -> path.addRect(rect, Path.Direction.CW)
    }
    canvas.drawPath(path, paint)
}
