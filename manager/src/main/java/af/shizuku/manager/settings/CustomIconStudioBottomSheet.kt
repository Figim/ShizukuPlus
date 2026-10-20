package af.shizuku.manager.settings

import af.shizuku.manager.R
import af.shizuku.manager.home.HomeActivity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.io.File
import java.io.FileOutputStream
import android.graphics.Color as AndroidColor

class CustomIconStudioBottomSheet : BottomSheetDialogFragment() {
    private var imageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        imageUri = arguments?.getParcelable(ARG_URI)
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
                    val uri = imageUri
                    if (uri != null) {
                        StudioContent(
                            uri = uri,
                            onPinShortcut = { bitmap ->
                                pinShortcut(bitmap)
                                dismiss()
                            },
                            onCancel = { dismiss() },
                        )
                    } else {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.custom_icon_studio_pinned_error))
                        }
                    }
                }
            }
        }

    private fun pinShortcut(bitmap: Bitmap) {
        val context = requireContext()
        try {
            // Save to app files for persistence
            val iconFile = File(context.filesDir, "custom_launcher_icon.png")
            FileOutputStream(iconFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

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
                        .setShortLabel("Shizuku+")
                        .setLongLabel("Shizuku+ Launcher")
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

    companion object {
        private const val ARG_URI = "arg_image_uri"

        fun newInstance(uri: Uri): CustomIconStudioBottomSheet =
            CustomIconStudioBottomSheet().apply {
                arguments =
                    Bundle().apply {
                        putParcelable(ARG_URI, uri)
                    }
            }
    }
}

@Composable
private fun StudioContent(
    uri: Uri,
    onPinShortcut: (Bitmap) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uri) {
        kotlin
            .runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }.onSuccess { sourceBitmap = it }
    }

    var selectedShape by remember { mutableStateOf("squircle") }
    var selectedBg by remember { mutableStateOf("accent") }
    var paddingPercent by remember { mutableFloatStateOf(10f) }

    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHighest.toArgb()

    val renderedBitmap =
        remember(sourceBitmap, selectedShape, selectedBg, paddingPercent, primaryColor) {
            val src = sourceBitmap ?: return@remember null
            createModifiedIcon(
                source = src,
                shape = selectedShape,
                bgType = selectedBg,
                paddingPercent = paddingPercent,
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
        // Handle bar
        Box(
            modifier =
                Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.settings_custom_icon_studio_title),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Live Icon Preview Card
        Box(
            modifier =
                Modifier
                    .size(130.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(28.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (renderedBitmap != null) {
                Image(
                    bitmap = renderedBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

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
                    "rounded" to stringResource(R.string.custom_icon_studio_shape_rounded),
                    "oneui" to stringResource(R.string.custom_icon_studio_shape_oneui),
                )
            for ((key, label) in shapes) {
                FilterChip(
                    selected = selectedShape == key,
                    onClick = { selectedShape = key },
                    label = { Text(label, fontSize = 12.sp) },
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Background Color Selection
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
                    "transparent" to "Clear",
                    "accent" to "Accent",
                    "black" to "Black",
                    "white" to "White",
                )
            for ((key, label) in backgrounds) {
                FilterChip(
                    selected = selectedBg == key,
                    onClick = { selectedBg = key },
                    label = { Text(label, fontSize = 12.sp) },
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Padding & Scale Slider
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

        Spacer(modifier = Modifier.height(24.dp))

        // Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(android.R.string.cancel))
            }
            Button(
                onClick = {
                    renderedBitmap?.let { onPinShortcut(it) }
                },
                enabled = renderedBitmap != null,
                modifier = Modifier.weight(1.5f),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_install_24),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.custom_icon_studio_pin_shortcut))
            }
        }
    }
}

private fun createModifiedIcon(
    source: Bitmap,
    shape: String,
    bgType: String,
    paddingPercent: Float,
    primaryColor: Int,
    surfaceColor: Int,
): Bitmap {
    val size = 512
    val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())

    // 1. Draw shape background
    when (bgType) {
        "accent" -> {
            paint.color = primaryColor
            drawShapedPath(canvas, rect, shape, paint)
        }
        "black" -> {
            paint.color = AndroidColor.BLACK
            drawShapedPath(canvas, rect, shape, paint)
        }
        "white" -> {
            paint.color = AndroidColor.WHITE
            drawShapedPath(canvas, rect, shape, paint)
        }
    }

    // 2. Prepare mask for icon content
    val maskBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val maskCanvas = Canvas(maskBitmap)
    val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.WHITE }
    drawShapedPath(maskCanvas, rect, shape, maskPaint)

    // 3. Draw scaled icon content
    val inset = (size * (paddingPercent / 100f)).toInt()
    val destRect = Rect(inset, inset, size - inset, size - inset)

    val contentBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val contentCanvas = Canvas(contentBitmap)
    contentCanvas.drawBitmap(source, null, destRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))

    // 4. Apply mask to content if clear background
    if (bgType == "transparent") {
        maskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        contentCanvas.drawBitmap(maskBitmap, 0f, 0f, maskPaint)
    }

    canvas.drawBitmap(contentBitmap, 0f, 0f, null)
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
        else -> path.addRect(rect, Path.Direction.CW)
    }
    canvas.drawPath(path, paint)
}
