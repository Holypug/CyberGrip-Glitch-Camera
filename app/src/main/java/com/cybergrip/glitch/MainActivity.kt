package com.cybergrip.glitch

import android.Manifest
import android.content.ContentValues
import android.content.res.ColorStateList
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import org.json.JSONObject
import org.json.JSONArray
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import android.os.Build
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.provider.MediaStore
import android.view.Gravity
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.os.SystemClock
import kotlin.math.abs
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.io.ByteArrayOutputStream
import kotlin.math.min
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var liveImage: ImageView
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val photoExecutor = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    @Volatile private var videoMode = false
    @Volatile private var recording = false
    private var videoEncoder: GlitchVideoEncoder? = null
    private var videoFrames = 0
    private var videoQuality = 0 // 0 = 1080p, 1 = 4K (upscaled from FX preview)
    private var videoBitrateMbps = 12
    private var recordingStartedMs = 0L
    @Volatile private var videoSaving = false
    private lateinit var shutterView: TextView
    private lateinit var modeButton: TextView
    @Volatile private var liveEnabled = true
    private lateinit var status: TextView
    private var capture: ImageCapture? = null
    private val names = arrayOf("RGB Shift", "Pixel Drift", "Horizontal Tearing", "Scanlines", "Block Glitch", "Pixel Sorting", "Chromatic Noise", "Ghosting", "VHS Tracking", "TV Static", "Tape Damage", "Analog Bleed", "Interlacing", "Solarize", "Posterize", "False Color", "Thermal Sim", "Edge Detect", "Invert", "Film Grain", "Vignette", "Frame Echo")
    @Volatile private var amounts = intArrayOf(0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0)
    private var frameCount = 0
    private val accent = Color.rgb(231, 112, 48)
    private val panel = Color.rgb(17, 22, 29)
    private var shutterButton: Button? = null
    private val filmNames = arrayOf("Original", "B&W Classic", "Sepia 1970", "Vintage Film", "B&W 400", "VHS 1988", "Cross Process")
    @Volatile private var filmMode = 0
    @Volatile private var filmStrength = 75
    @Volatile private var labLevels = intArrayOf(0, 0, 0, 0)
    private val labNames = arrayOf("Film Grain", "Halation", "Bloom", "Film Fade")
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) startCamera() else status.text = "Permesso fotocamera necessario" }

    private var selectedEffect = 0
    private lateinit var mainSlider: SeekBar
    private lateinit var sliderTitle: TextView
    private lateinit var sliderValue: TextView
    private lateinit var fxStrip: LinearLayout
    private val fxButtons = mutableListOf<TextView>()
    private val fxEnabled = BooleanArray(names.size) { amounts[it] > 0 }
    private val savedLevels = IntArray(names.size) { if (amounts[it] > 0) amounts[it] else 0 }
    private val bg = Color.rgb(12, 12, 12)
    private val surface = Color.rgb(31, 31, 31)

    private fun rounded(color: Int, radius: Int, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = radius.dp().toFloat(); if (stroke != null) setStroke(1.dp(), stroke) }

    private fun label(textValue: String, size: Float, color: Int = Color.WHITE): TextView =
        TextView(this).apply { text = textValue; textSize = size; setTextColor(color); gravity = Gravity.CENTER_VERTICAL }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("cybergrip_presets", MODE_PRIVATE)
        val slotToLoad = prefs.getInt("pending_slot", -1)
        if (slotToLoad in 1..3) {
            try {
                val data = JSONObject(prefs.getString("slot_$slotToLoad", "{}") ?: "{}")
                val fx = data.optJSONArray("effects")
                if (fx != null && fx.length() == amounts.size) {
                    amounts = IntArray(amounts.size) { fx.optInt(it, 0).coerceIn(0, 100) }
                    for (i in amounts.indices) {
                        savedLevels[i] = amounts[i]
                        fxEnabled[i] = amounts[i] > 0
                    }
                }
                filmMode = data.optInt("filmMode", 0).coerceIn(0, filmNames.lastIndex)
                filmStrength = data.optInt("filmStrength", 75).coerceIn(0, 100)
                val lab = data.optJSONArray("lab")
                if (lab != null && lab.length() == labLevels.size) {
                    labLevels = IntArray(labLevels.size) { lab.optInt(it, 0).coerceIn(0, 100) }
                }
            } catch (_: Exception) { }
            prefs.edit().remove("pending_slot").apply()
        }
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(18.dp(), 8.dp(), 18.dp(), 8.dp()) }
        val branding = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        branding.addView(label("◉  CYBERGRIP", 17f).apply { setTypeface(null, Typeface.BOLD); letterSpacing = .08f })
        branding.addView(label("GLITCH CAMERA  /  FX 1.4", 10f, accent).apply { letterSpacing = .12f })
        header.addView(branding, LinearLayout.LayoutParams(0, -2, 1f))
        val liveBadge = label("● LIVE", 12f, accent)
        header.addView(liveBadge)
        root.addView(header, LinearLayout.LayoutParams(-1, 58.dp()))

        previewView = PreviewView(this)
        val previewFrame = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        previewFrame.addView(previewView, FrameLayout.LayoutParams(-1, -1))
        liveImage = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        previewFrame.addView(liveImage, FrameLayout.LayoutParams(-1, -1))
        root.addView(previewFrame, LinearLayout.LayoutParams(-1, 0, 1f))

        val infoRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(18.dp(), 0, 18.dp(), 0) }
        status = label("PHOTO  •  4:3", 11f, Color.LTGRAY)
        infoRow.addView(status, LinearLayout.LayoutParams(0, -2, 1f))
        val stackInfo = label("FX STACK  •  1 ACTIVE", 11f, accent)
        infoRow.addView(stackInfo)
        root.addView(infoRow, LinearLayout.LayoutParams(-1, 35.dp()))

        val controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12.dp(), 5.dp(), 12.dp(), 4.dp()); setBackgroundColor(surface) }
        val fxHeading = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        fxHeading.addView(label("GLITCH EFFECTS", 11f, Color.LTGRAY), LinearLayout.LayoutParams(0, -2, 1f))
        val fxArrow = label("▼", 13f, accent)
        fxHeading.addView(fxArrow)
        controls.addView(fxHeading)
        val fxBody = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        fxHeading.setPadding(0, 7.dp(), 0, 7.dp())
        fxHeading.setOnClickListener {
            val opening = fxBody.visibility != View.VISIBLE
            fxBody.visibility = if (opening) View.VISIBLE else View.GONE
            fxArrow.text = if (opening) "▲" else "▼"
        }
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        fxStrip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 6.dp(), 0, 6.dp()) }
        scroll.addView(fxStrip)
        fxBody.addView(scroll)
        val sliderRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(4.dp(), 5.dp(), 4.dp(), 0) }
        sliderTitle = label(names[selectedEffect].uppercase(), 12f)
        sliderRow.addView(sliderTitle, LinearLayout.LayoutParams(0, -2, 1f))
        sliderValue = label("0%", 12f, accent)
        sliderRow.addView(sliderValue)
        fxBody.addView(sliderRow)
        mainSlider = SeekBar(this).apply {
            max = 100; progress = amounts[selectedEffect]
            progressTintList = android.content.res.ColorStateList.valueOf(accent)
            thumbTintList = android.content.res.ColorStateList.valueOf(accent)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    savedLevels[selectedEffect] = value
                    fxEnabled[selectedEffect] = value > 0
                    updateAmount(selectedEffect, value)
                    sliderValue.text = "$value%"
                    refreshFx(stackInfo)
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        }
        fxBody.addView(mainSlider, LinearLayout.LayoutParams(-1, 38.dp()))
        controls.addView(fxBody)
        root.addView(controls)
        val filmPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12.dp(), 6.dp(), 12.dp(), 6.dp()); setBackgroundColor(bg) }
        val filmHeader = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, 7.dp(), 0, 7.dp()) }
        filmHeader.addView(label("FILM LOOKS   /   ANALOG", 11f, accent), LinearLayout.LayoutParams(0, -2, 1f))
        val filmArrow = label("▼", 13f, accent)
        filmHeader.addView(filmArrow)
        filmPanel.addView(filmHeader)
        val filmBody = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        filmHeader.setOnClickListener {
            val opening = filmBody.visibility != View.VISIBLE
            filmBody.visibility = if (opening) View.VISIBLE else View.GONE
            filmArrow.text = if (opening) "▲" else "▼"
        }
        val filmStrip = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val filmChoices = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val filmButtons = mutableListOf<TextView>()
        fun refreshFilm() {
            filmButtons.forEachIndexed { i, button ->
                button.setTextColor(if (i == filmMode) accent else Color.LTGRAY)
                button.background = rounded(if (i == filmMode) Color.rgb(80, 43, 28) else surface, 12, if (i == filmMode) accent else Color.DKGRAY)
            }
        }
        filmNames.forEachIndexed { i, name ->
            val item = label(name, 11f).apply {
                gravity = Gravity.CENTER; setPadding(12.dp(), 8.dp(), 12.dp(), 8.dp())
                setOnClickListener { filmMode = i; refreshFilm() }
            }
            filmChoices.addView(item, LinearLayout.LayoutParams(-2, 40.dp()).apply { marginEnd = 7.dp() })
            filmButtons.add(item)
        }
        filmStrip.addView(filmChoices); filmBody.addView(filmStrip); refreshFilm()
        val filmStrengthRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        filmStrengthRow.addView(label("FILM INTENSITY", 10f, Color.LTGRAY), LinearLayout.LayoutParams(0, -2, 1f))
        val filmValue = label("75%", 11f, accent); filmStrengthRow.addView(filmValue)
        filmBody.addView(filmStrengthRow)
        filmBody.addView(SeekBar(this).apply {
            max = 100; progress = filmStrength
            progressTintList = android.content.res.ColorStateList.valueOf(accent)
            thumbTintList = android.content.res.ColorStateList.valueOf(accent)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) { if (fromUser) { filmStrength = value; filmValue.text = "$value%" } }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        }, LinearLayout.LayoutParams(-1, 32.dp()))
        filmPanel.addView(filmBody)
        val labPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12.dp(), 8.dp(), 12.dp(), 8.dp()); setBackgroundColor(surface) }
        val labHeader = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 7.dp(), 0, 7.dp()) }
        labHeader.addView(label("FILM LAB   /   TEXTURE & LIGHT", 11f, accent), LinearLayout.LayoutParams(0, -2, 1f))
        val labArrow = label("▼", 13f, accent)
        labHeader.addView(labArrow)
        labPanel.addView(labHeader)
        val labBody = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        labHeader.setOnClickListener {
            val opening = labBody.visibility != View.VISIBLE
            labBody.visibility = if (opening) View.VISIBLE else View.GONE
            labArrow.text = if (opening) "▲" else "▼"
        }
        labNames.forEachIndexed { index, name ->
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            line.addView(label(name.uppercase(), 11f), LinearLayout.LayoutParams(0, -2, 1f))
            val value = label("${labLevels[index]}%", 11f, accent)
            line.addView(value)
            labBody.addView(line)
            labBody.addView(SeekBar(this).apply {
                max = 100; progress = labLevels[index]
                progressTintList = ColorStateList.valueOf(accent)
                thumbTintList = ColorStateList.valueOf(accent)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) { val updated = labLevels.copyOf(); updated[index] = progress; labLevels = updated; value.text = "$progress%" }
                    }
                    override fun onStartTrackingTouch(bar: SeekBar?) {}
                    override fun onStopTrackingTouch(bar: SeekBar?) {}
                })
            }, LinearLayout.LayoutParams(-1, 32.dp()))
        }
        labPanel.addView(labBody)
        filmPanel.addView(labPanel)
        root.addView(filmPanel)

        val presetRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12.dp(), 5.dp(), 12.dp(), 5.dp())
            setBackgroundColor(surface)
        }
        presetRow.addView(label("PRESETS", 11f, accent), LinearLayout.LayoutParams(0, -2, 1f))
        for (slot in 1..3) {
            val button = label("P$slot", 12f).apply {
                gravity = Gravity.CENTER
                setPadding(13.dp(), 10.dp(), 13.dp(), 10.dp())
                background = rounded(bg, 10, Color.DKGRAY)
                contentDescription = "Preset $slot: tocca per caricare, tieni premuto per salvare"
                setOnClickListener {
                    if (recording || videoSaving) {
                        Toast.makeText(this@MainActivity, "Ferma prima il video", Toast.LENGTH_SHORT).show()
                    } else if (prefs.contains("slot_$slot")) {
                        prefs.edit().putInt("pending_slot", slot).apply()
                        recreate()
                    } else {
                        Toast.makeText(this@MainActivity, "Preset vuoto: tieni premuto per salvare", Toast.LENGTH_SHORT).show()
                    }
                }
                setOnLongClickListener {
                    if (recording || videoSaving) {
                        Toast.makeText(this@MainActivity, "Ferma prima il video", Toast.LENGTH_SHORT).show()
                    } else {
                        val data = JSONObject()
                        data.put("effects", JSONArray(amounts.toList()))
                        data.put("filmMode", filmMode)
                        data.put("filmStrength", filmStrength)
                        data.put("lab", JSONArray(labLevels.toList()))
                        prefs.edit().putString("slot_$slot", data.toString()).apply()
                        Toast.makeText(this@MainActivity, "Preset P$slot salvato", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
            }
            presetRow.addView(button, LinearLayout.LayoutParams(-2, -2).apply { marginStart = 7.dp() })
        }
        root.addView(presetRow)

        // VIDEO LAB PRO: collapsed settings panel, unavailable while recording.
        val videoSettingsPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE; setPadding(12.dp(), 6.dp(), 12.dp(), 8.dp()); setBackgroundColor(panel) }
        val videoSettingsHeader = label("▸ VIDEO SETTINGS  /  MP4", 12f, accent).apply {
            setPadding(14.dp(), 10.dp(), 14.dp(), 10.dp())
            setOnClickListener {
                videoSettingsPanel.visibility = if (videoSettingsPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                text = if (videoSettingsPanel.visibility == View.VISIBLE) "▾ VIDEO SETTINGS  /  MP4" else "▸ VIDEO SETTINGS  /  MP4"
            }
        }
        root.addView(videoSettingsHeader)
        val qualityLabel = label("Output: 1080p  /  30 fps nominali", 12f)
        videoSettingsPanel.addView(qualityLabel)
        val qualitySelector = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("1080p • 1920×1080", "4K UHD • 3840×2160 (upscale)"))
            setSelection(videoQuality)
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (recording || videoSaving) { setSelection(videoQuality); return }
                    videoQuality = position
                    qualityLabel.text = if (position == 0) "Output: 1080p • 30 fps nominali" else "Output: 4K UHD upscale • 30 fps nominali"
                }
            }
        }
        videoSettingsPanel.addView(qualitySelector)
        videoSettingsPanel.addView(label("Bitrate H.264", 12f, accent))
        val bitrateOptions = arrayOf("12 Mbps", "20 Mbps", "30 Mbps")
        val bitrateSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, bitrateOptions)
            setSelection(0)
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (recording || videoSaving) { setSelection(listOf(12,20,30).indexOf(videoBitrateMbps)); return }
                    videoBitrateMbps = listOf(12,20,30)[position]
                }
            }
        }
        videoSettingsPanel.addView(bitrateSpinner)
        videoSettingsPanel.addView(label("Nota: effetti da preview 960×720; 4K non nativo. FPS effettivi dipendono dal carico.", 11f, Color.LTGRAY))
        root.addView(videoSettingsPanel)
        val modeRow = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(8.dp(), 3.dp(), 8.dp(), 3.dp()) }
        modeButton = label("PHOTO     /     VIDEO", 12f, accent).apply {
            gravity = Gravity.CENTER
            setPadding(14.dp(), 8.dp(), 14.dp(), 8.dp())
            setOnClickListener {
                if (!recording && !videoSaving) {
                    videoMode = !videoMode
                    text = if (videoMode) "PHOTO     /     ● VIDEO" else "● PHOTO     /     VIDEO"
                    status.text = if (videoMode) "VIDEO • ${if (videoQuality == 0) "1080p" else "4K UPSCALE"} • MP4" else "PHOTO  •  4:3"
                    shutterView.contentDescription = if (videoMode) "Avvia registrazione video" else "Scatta foto"
                }
            }
        }
        modeRow.addView(modeButton)
        root.addView(modeRow)
        val bottom = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(24.dp(), 10.dp(), 24.dp(), 14.dp()) }
        val liveToggle = Switch(this).apply {
            text = "LIVE"; textSize = 12f; setTextColor(Color.WHITE); isChecked = true
            thumbTintList = android.content.res.ColorStateList.valueOf(accent)
            setOnCheckedChangeListener { _, enabled -> liveEnabled = enabled; liveImage.visibility = if (enabled) View.VISIBLE else View.GONE }
        }
        bottom.addView(liveToggle, LinearLayout.LayoutParams(0, -2, 1f))
        val shutter = TextView(this).apply {
            text = "●"; textSize = 42f; gravity = Gravity.CENTER; setTextColor(bg)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(accent); setStroke(4.dp(), Color.WHITE) }
            contentDescription = "Scatta fotografia glitch"
            setOnClickListener { if (videoMode) toggleVideo() else takePhoto() }
        }
        shutterView = shutter
        // Landscape: compact capture control; portrait keeps the original size.
        val landscapeUi = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val shutterSize = if (landscapeUi) 56.dp() else 72.dp()
        if (landscapeUi) {
            bottom.setPadding(12.dp(), 4.dp(), 12.dp(), 4.dp())
            shutter.textSize = 32f
        }
        bottom.addView(shutter, LinearLayout.LayoutParams(shutterSize, shutterSize))
        bottom.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        root.addView(bottom)
        // In landscape the camera stays visible on the left and the controls scroll on the right.
        if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            root.removeAllViews()
            root.orientation = LinearLayout.HORIZONTAL
            val cameraColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            cameraColumn.addView(previewFrame, LinearLayout.LayoutParams(-1, 0, 1f))
            cameraColumn.addView(infoRow, LinearLayout.LayoutParams(-1, 35.dp()))
            root.addView(cameraColumn, LinearLayout.LayoutParams(0, -1, 1.8f))
            val rightColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
            val rightScroll = ScrollView(this).apply { isFillViewport = false }
            val rightContent = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            rightContent.addView(header, LinearLayout.LayoutParams(-1, 58.dp()))
            rightContent.addView(controls)
            rightContent.addView(filmPanel)
            rightContent.addView(presetRow)
            rightContent.addView(modeRow)
            rightScroll.addView(rightContent)
            rightColumn.addView(rightScroll, LinearLayout.LayoutParams(-1, 0, 1f))
            rightColumn.addView(bottom, LinearLayout.LayoutParams(-1, 66.dp()))
            root.addView(rightColumn, LinearLayout.LayoutParams(0, -1, 1f))
        }
        setContentView(root)
        // Android 15/16 enforce edge-to-edge. Keep controls clear of system bars.
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        refreshFx(stackInfo)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera() else permission.launch(Manifest.permission.CAMERA)
    }

    private fun refreshFx(stackInfo: TextView) {
        fxStrip.removeAllViews(); fxButtons.clear()
        names.forEachIndexed { index, name ->
            val selected = index == selectedEffect
            val enabled = amounts[index] > 0
            val item = TextView(this).apply {
                text = "${if (enabled) "● " else "○ "}${name.replace("Horizontal ", "") }\n${amounts[index]}%"
                textSize = 11f; gravity = Gravity.CENTER; setTextColor(if (selected) accent else Color.LTGRAY)
                setPadding(12.dp(), 9.dp(), 12.dp(), 9.dp())
                background = rounded(if (selected) Color.rgb(80, 43, 28) else bg, 12, if (selected) accent else Color.rgb(60, 60, 60))
                setOnClickListener {
                    if (selectedEffect == index) {
                        val newLevel = if (amounts[index] > 0) 0 else savedLevels[index]
                        fxEnabled[index] = newLevel > 0
                        updateAmount(index, newLevel)
                    }
                    selectedEffect = index
                    sliderTitle.text = names[index].uppercase()
                    sliderValue.text = "${amounts[index]}%"
                    mainSlider.progress = amounts[index]
                    refreshFx(stackInfo)
                }
            }
            fxStrip.addView(item, LinearLayout.LayoutParams(-2, 58.dp()).apply { marginEnd = 7.dp() })
            fxButtons.add(item)
        }
        stackInfo.text = "FX STACK  •  ${amounts.count { it > 0 }} ACTIVE"
    }
    private fun updateAmount(index: Int, value: Int) {
        amounts = amounts.copyOf().also { it[index] = value }
    }
    private fun Int.dp() = (this * resources.displayMetrics.density).toInt()
    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                provider.unbindAll()
                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(android.util.Size(960, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                analysis.setAnalyzer(analysisExecutor) { frame -> analyzeFrame(frame) }
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture, analysis)
            } catch (e: Exception) { status.text = "Errore camera: ${e.message}" }
        }, ContextCompat.getMainExecutor(this))
    }
    private fun analyzeFrame(frame: ImageProxy) {
        if ((!liveEnabled && !recording) || !busy.compareAndSet(false, true)) { frame.close(); return }
        try {
            val plane = frame.planes[0]
            val buffer = plane.buffer
            val w = frame.width; val h = frame.height
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            // RGBA_8888 planes may have padded rows: copy row by row.
            val row = ByteArray(plane.rowStride)
            val pixels = IntArray(w * h)
            for (y in 0 until h) {
                buffer.position(y * plane.rowStride)
                buffer.get(row, 0, minOf(plane.rowStride, buffer.remaining()))
                for (x in 0 until w) {
                    val i = x * plane.pixelStride
                    pixels[y*w+x] = Color.argb(row[i+3].toInt() and 255, row[i].toInt() and 255, row[i+1].toInt() and 255, row[i+2].toInt() and 255)
                }
            }
            bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
            val rotated = rotateIfNeeded(bitmap, frame.imageInfo.rotationDegrees)
            val result = applyFilmLab(applyFilm(glitch(rotated, amounts, true), filmMode, filmStrength), labLevels)
            if (recording) {
                try {
                    videoEncoder?.addFrame(result)
                    videoFrames++
                    if (videoFrames % 10 == 0) {
                        val elapsed = (SystemClock.elapsedRealtime() - recordingStartedMs) / 1000
                        runOnUiThread { status.text = "REC ●  ${elapsed}s  •  ${videoFrames} FRAMES" }
                    }
                } catch (e: Exception) {
                    recording = false
                    runOnUiThread { status.text = "Errore video: ${e.message}" }
                }
            }
            runOnUiThread { liveImage.setImageBitmap(result); busy.set(false) }
        } catch (_: Exception) { busy.set(false) }
        finally { frame.close() }
    }
    private fun toggleVideo() {
        if (videoSaving) return
        if (!recording) {
            try {
                videoEncoder = GlitchVideoEncoder(this, if (videoQuality == 0) 1920 else 3840, if (videoQuality == 0) 1080 else 2160, videoBitrateMbps * 1_000_000)
                videoEncoder!!.start()
                videoFrames = 0
                recordingStartedMs = SystemClock.elapsedRealtime()
                recording = true
                shutterView.text = "■"
                status.text = "REC ●  0s  •  ${if (videoQuality == 0) "1080p" else "4K UPSCALE"}"
            } catch (e: Exception) {
                recording = false
                status.text = "Errore avvio video: ${e.message}"
            }
        } else {
            recording = false
            shutterView.text = "●"
            status.text = "Salvataggio MP4..."
            videoSaving = true
            analysisExecutor.execute {
                try {
                    videoEncoder?.finish()
                    runOnUiThread { status.text = "Video salvato in Movies/CyberGrip" }
                } catch (e: Exception) {
                    runOnUiThread { status.text = "Errore MP4: ${e.message}" }
                } finally { videoEncoder = null; videoSaving = false }
            }
        }
    }
    private fun takePhoto() {
        val imageCapture = capture ?: return
        status.text = "Acquisizione..."
        imageCapture.takePicture(ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                try {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
                    val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("Decodifica fallita")
                    val rotated = rotateIfNeeded(original, image.imageInfo.rotationDegrees)
                    val selectedAmounts = amounts.copyOf()
                    val selectedFilm = filmMode
                    val selectedFilmStrength = filmStrength
                    val selectedLab = labLevels.copyOf()
                    photoExecutor.execute {
                        try {
                            val modified = applyFilmLab(applyFilm(glitch(rotated, selectedAmounts, false), selectedFilm, selectedFilmStrength), selectedLab)
                            save(rotated, "ORIGINAL")
                            save(modified, "GLITCH")
                            runOnUiThread { status.text = "Salvate 2 foto in Pictures/CyberGrip" }
                        } catch (e: Exception) { runOnUiThread { status.text = "Errore salvataggio: ${e.message}" } }
                    }
                } catch (e: Exception) { status.text = "Errore: ${e.message}" }
                finally { image.close() }
            }
            override fun onError(exception: ImageCaptureException) { status.text = "Scatto fallito: ${exception.message}" }
        })
    }
    private fun rotateIfNeeded(src: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return src
        val matrix = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }
    // CPU prototype: deterministic effect stack shared by preview and capture.
    // Temporal Ghosting is simulated spatially in this build; true frame history is planned for GPU version.
    private fun glitch(source: Bitmap, settings: IntArray, live: Boolean): Bitmap {
        val limit = if (live) 960 else 2200
        val factor = min(1f, limit.toFloat() / maxOf(source.width, source.height))
        val w = (source.width * factor).toInt().coerceAtLeast(1)
        val h = (source.height * factor).toInt().coerceAtLeast(1)
        val resized = if (w == source.width && h == source.height) source else Bitmap.createScaledBitmap(source, w, h, true)
        var pixels = IntArray(w * h)
        resized.getPixels(pixels, 0, w, 0, 0, w, h)
        val phase = if (live) ++frameCount else 0
        for (mode in settings.indices) {
            val amount = settings[mode]
            if (amount <= 0) continue
            val out = pixels.copyOf()
            val offset = (amount * w / 1600).coerceAtLeast(1)
            when (mode) {
                0 -> for (y in 0 until h) for (x in 0 until w) {
                    val r = pixels[y*w + (x+offset).coerceAtMost(w-1)]
                    val b = pixels[y*w + (x-offset).coerceAtLeast(0)]
                    val c = pixels[y*w+x]
                    out[y*w+x] = Color.rgb(Color.red(r), Color.green(c), Color.blue(b))
                }
                1 -> for (y in 0 until h) {
                    val drift = ((y / 13 + phase / 3) % 17 - 8) * offset / 5
                    for (x in 0 until w) out[y*w+x] = pixels[y*w+(x+drift).coerceIn(0,w-1)]
                }
                2 -> for (y in 0 until h) {
                    val shift = if ((y+phase) % 51 < 11) ((y*31+phase*7)% (offset*4+1))-offset*2 else 0
                    for (x in 0 until w) out[y*w+x] = pixels[y*w+(x+shift).coerceIn(0,w-1)]
                }
                3 -> for (y in 0 until h) if (y % 5 == 0) for (x in 0 until w) {
                    val c = pixels[y*w+x]; val f = 1f-amount*0.007f
                    out[y*w+x] = Color.rgb((Color.red(c)*f).toInt(), (Color.green(c)*f).toInt(), (Color.blue(c)*f).toInt())
                }
                4 -> {
                    val block = 10 + amount/3
                    for (by in 0 until h step block*3) for (bx in 0 until w step block*3) {
                        for (dy in 0 until min(block,h-by)) for (dx in 0 until min(block,w-bx)) {
                            out[(by+dy)*w+bx+dx] = pixels[(by+dy)*w+(bx+dx+offset*3).coerceAtMost(w-1)]
                        }
                    }
                }
                5 -> { // Lightweight row-based pixel sort: sort short high-luminance segments.
                    val segment = (8+amount/2).coerceAtMost(w)
                    for (y in 0 until h step 3) for (x in 0 until w step segment) {
                        val end = min(w, x+segment)
                        val sorted = (x until end).map { pixels[y*w+it] }.sortedBy { Color.red(it)*3+Color.green(it)*6+Color.blue(it) }
                        for (j in sorted.indices) out[y*w+x+j] = sorted[j]
                    }
                }
                6 -> for (y in 0 until h step 2) for (x in 0 until w step 2) {
                    val c = pixels[y*w+x]; val n = ((x*17+y*29+phase*13)% (amount+1))-amount/2
                    out[y*w+x] = Color.rgb((Color.red(c)+n).coerceIn(0,255), (Color.green(c)-n).coerceIn(0,255), (Color.blue(c)+n*2).coerceIn(0,255))
                }
                7 -> for (y in 0 until h) for (x in 0 until w) {
                    val c = pixels[y*w+x]; val old = pixels[y*w+(x-offset*2).coerceAtLeast(0)]
                    val blend = amount / 180f
                    out[y*w+x] = Color.rgb((Color.red(c)*(1-blend)+Color.red(old)*blend).toInt(), (Color.green(c)*(1-blend)+Color.green(old)*blend).toInt(), (Color.blue(c)*(1-blend)+Color.blue(old)*blend).toInt())
                }
                8 -> for (y in 0 until h) { val sh = if ((y+phase)%43 < 7) offset*5 else 0; for (x in 0 until w) out[y*w+x]=pixels[y*w+(x+sh).coerceIn(0,w-1)] }
                9 -> for (i in pixels.indices step 2) { val c=pixels[i]; val n=((i*71L+phase*137L)%255).toInt(); val a=amount/100f; out[i]=Color.rgb((Color.red(c)*(1-a)+n*a).toInt(),(Color.green(c)*(1-a)+n*a).toInt(),(Color.blue(c)*(1-a)+n*a).toInt()) }
                10 -> for (y in 0 until h) if ((y+phase*3)%97 < (amount/8+1)) for (x in 0 until w) out[y*w+x]=pixels[y*w+(x+offset*8).coerceIn(0,w-1)]
                11 -> for (i in pixels.indices) { val c=pixels[i]; val prev=pixels[(i-offset).coerceAtLeast(0)]; val a=amount/130f; out[i]=Color.rgb((Color.red(c)*(1-a)+Color.red(prev)*a).toInt().coerceIn(0,255),Color.green(c),(Color.blue(c)*(1-a)+Color.blue(prev)*a).toInt().coerceIn(0,255)) }
                12 -> for (y in 1 until h step 2) for (x in 0 until w) { val c=pixels[y*w+x]; val f=1f-amount/180f; out[y*w+x]=Color.rgb((Color.red(c)*f).toInt(),(Color.green(c)*f).toInt(),(Color.blue(c)*f).toInt()) }
                13 -> {
                    val blend = amount / 100f
                    for (i in pixels.indices) {
                        val c = pixels[i]
                        val red = Color.red(c)
                        val green = Color.green(c)
                        val blue = Color.blue(c)
                        val solarRed = if (red > 127) 255 - red else red
                        val solarGreen = if (green > 127) 255 - green else green
                        val solarBlue = if (blue > 127) 255 - blue else blue
                        out[i] = Color.rgb(
                            (red * (1f - blend) + solarRed * blend).toInt().coerceIn(0, 255),
                            (green * (1f - blend) + solarGreen * blend).toInt().coerceIn(0, 255),
                            (blue * (1f - blend) + solarBlue * blend).toInt().coerceIn(0, 255)
                        )
                    }
                }
                14 -> {
                    val quantStep = (2 + amount / 9).coerceAtMost(32)
                    for (i in pixels.indices) {
                        val c = pixels[i]
                        val red = (Color.red(c) / quantStep * quantStep).coerceIn(0, 255)
                        val green = (Color.green(c) / quantStep * quantStep).coerceIn(0, 255)
                        val blue = (Color.blue(c) / quantStep * quantStep).coerceIn(0, 255)
                        out[i] = Color.rgb(red, green, blue)
                    }
                }
                15 -> for (i in pixels.indices) { val c=pixels[i]; val a=amount/100f; val r=Color.red(c);val g=Color.green(c);val b=Color.blue(c); out[i]=Color.rgb((r*(1-a)+b*a).toInt(),(g*(1-a)+r*a).toInt(),(b*(1-a)+g*a).toInt()) }
                16 -> for (i in pixels.indices) { val c=pixels[i]; val l=(Color.red(c)*3+Color.green(c)*6+Color.blue(c))/10; val a=amount/100f; val rr=(l*2).coerceAtMost(255); val gg=(255-kotlin.math.abs(l-128)*2).coerceIn(0,255); val bb=(255-l*2).coerceAtLeast(0); out[i]=Color.rgb((Color.red(c)*(1-a)+rr*a).toInt(),(Color.green(c)*(1-a)+gg*a).toInt(),(Color.blue(c)*(1-a)+bb*a).toInt()) }
                17 -> for (y in 1 until h-1) for (x in 1 until w-1) { val i=y*w+x; val a=pixels[i-1];val b=pixels[i+1];val d=pixels[i-w];val e=pixels[i+w]; val v=((kotlin.math.abs(Color.red(a)-Color.red(b))+kotlin.math.abs(Color.red(d)-Color.red(e)))*amount/100).coerceIn(0,255); out[i]=Color.rgb(v,v,v) }
                18 -> for (i in pixels.indices) { val c=pixels[i];val a=amount/100f;out[i]=Color.rgb((Color.red(c)*(1-a)+(255-Color.red(c))*a).toInt(),(Color.green(c)*(1-a)+(255-Color.green(c))*a).toInt(),(Color.blue(c)*(1-a)+(255-Color.blue(c))*a).toInt()) }
                19 -> for (i in pixels.indices) { val c=pixels[i];val n=(((i*7919L+phase*104729L)%101).toInt()-50)*amount/100;out[i]=Color.rgb((Color.red(c)+n).coerceIn(0,255),(Color.green(c)+n).coerceIn(0,255),(Color.blue(c)+n).coerceIn(0,255)) }
                20 -> for (y in 0 until h) for (x in 0 until w) { val i=y*w+x; val dx=(x-w/2f)/(w/2f);val dy=(y-h/2f)/(h/2f);val f=(1f-amount/150f*(dx*dx+dy*dy)).coerceIn(0f,1f);val c=pixels[i];out[i]=Color.rgb((Color.red(c)*f).toInt(),(Color.green(c)*f).toInt(),(Color.blue(c)*f).toInt()) }
                21 -> for (y in 0 until h) for (x in 0 until w) { val i=y*w+x;val old=pixels[y*w+(x-offset*4-phase%7).coerceIn(0,w-1)];val c=pixels[i];val a=amount/200f;out[i]=Color.rgb((Color.red(c)*(1-a)+Color.red(old)*a).toInt(),(Color.green(c)*(1-a)+Color.green(old)*a).toInt(),(Color.blue(c)*(1-a)+Color.blue(old)*a).toInt()) }
            }
            pixels = out
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }
    // Film emulations: adjustable, composable with the expanded glitch effects.
    private fun applyFilm(src: Bitmap, mode: Int, strength: Int): Bitmap {
        if (mode == 0 || strength == 0) return src
        val w = src.width; val h = src.height
        val pixels = IntArray(w*h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val mix = strength / 100f
        for (i in pixels.indices) {
            val c = pixels[i]; val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
            val lum = (r*0.299f + g*0.587f + b*0.114f)
            val grain = (((i*37L + (i/w)*119L) % 29L).toInt()-14)
            var rr = r.toFloat(); var gg = g.toFloat(); var bb = b.toFloat()
            when (mode) {
                1 -> { rr = lum; gg = lum; bb = lum }
                2 -> { rr = lum*1.16f+16; gg = lum*0.98f+7; bb = lum*0.73f }
                3 -> { rr = r*0.82f+37; gg = g*0.77f+32; bb = b*0.65f+32 }
                4 -> { val v = (lum-128)*1.32f+128+grain*1.7f; rr=v; gg=v; bb=v }
                5 -> { val scan = if ((i/w)%3==0) 0.83f else 1f; rr=(r*0.88f+13+grain)*scan; gg=(g*0.84f+10)*scan; bb=(b*0.94f+17)*scan }
                6 -> { rr=r*1.12f+12; gg=g*0.97f+9; bb=b*1.10f-16 }
            }
            fun blend(a: Int, v: Float) = (a*(1f-mix)+v*mix).toInt().coerceIn(0,255)
            pixels[i] = Color.rgb(blend(r,rr),blend(g,gg),blend(b,bb))
        }
        return Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888)
    }
    // Independent film texture/light controls; zero levels preserve the source.
    private fun applyFilmLab(src: Bitmap, levels: IntArray): Bitmap {
        if (levels.all { it == 0 }) return src
        val w = src.width; val h = src.height
        val original = IntArray(w*h)
        src.getPixels(original, 0, w, 0, 0, w, h)
        val output = IntArray(original.size)
        val grain = levels[0] / 100f
        val halation = levels[1] / 100f
        val bloom = levels[2] / 100f
        val fade = levels[3] / 100f
        val phase = (SystemClock.elapsedRealtime() / 67L).toInt()
        fun luminance(c: Int): Float = Color.red(c)*0.299f + Color.green(c)*0.587f + Color.blue(c)*0.114f
        for (y in 0 until h) for (x in 0 until w) {
            val i = y*w+x; val c = original[i]
            var r = Color.red(c).toFloat(); var g = Color.green(c).toFloat(); var b = Color.blue(c).toFloat()
            if (fade > 0f) {
                val factor = 1f - fade*0.27f
                r = (r-128f)*factor+128f+fade*12f
                g = (g-128f)*factor+128f+fade*9f
                b = (b-128f)*factor+128f+fade*6f
            }
            if (halation > 0f || bloom > 0f) {
                var bright = 0f; var count = 0
                for (dy in -3..3 step 3) for (dx in -3..3 step 3) {
                    val xx = (x+dx).coerceIn(0,w-1); val yy = (y+dy).coerceIn(0,h-1)
                    bright += ((luminance(original[yy*w+xx])-170f)/85f).coerceIn(0f,1f); count++
                }
                val glow = bright/count
                r += glow*(halation*78f+bloom*48f)
                g += glow*(halation*17f+bloom*48f)
                b += glow*(bloom*48f)
            }
            if (grain > 0f) {
                val hash = (x*73856093 xor y*19349663 xor phase*83492791)
                val noise = ((hash xor (hash ushr 13)) and 255)-127
                val amount = noise*grain*0.28f
                r += amount; g += amount; b += amount
            }
            output[i] = Color.rgb(r.toInt().coerceIn(0,255),g.toInt().coerceIn(0,255),b.toInt().coerceIn(0,255))
        }
        return Bitmap.createBitmap(output,w,h,Bitmap.Config.ARGB_8888)
    }
    override fun onDestroy() {
        super.onDestroy()
        if (recording) {
            recording = false
            try { videoEncoder?.finish() } catch (_: Exception) {}
        }
        analysisExecutor.shutdown()
        photoExecutor.shutdown()
    }
    private fun save(bitmap: Bitmap, label: String) {
        val name = "${label}_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CyberGrip")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Impossibile salvare")
        try {
            contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) } ?: error("Stream non disponibile")
            values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
        } catch (e: Exception) { contentResolver.delete(uri, null, null); throw e }
    }
}
