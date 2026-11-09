package com.xpanmode.camera

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.*
import android.widget.*
import android.graphics.Rect
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.Build
import java.io.OutputStream
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var cameraManager: CameraManager
    private lateinit var preview: TextureView
    private lateinit var root: FrameLayout
    private lateinit var lensText: TextView
    private lateinit var statusText: TextView
    private lateinit var brandLabel: TextView
    private lateinit var shutterView: View
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var selectedId: String? = null
    private var lenses: List<LensInfo> = emptyList()
    private var classic = true
    private var captureInProgress = false
    private val executor = Executors.newSingleThreadExecutor()
    private var selectedFilter = Filter.NATURAL
    private var xpanCrop = true

    private enum class Filter(val title: String) {
        NATURAL("Hasselblad Natural"), CLASSIC("Leica Classic"), VIVID("Vivid"), BRIGHT("ZEISS Clear")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 41)
        } else setup()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 41 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) setup()
        else Toast.makeText(this, "Cần quyền camera để chụp ảnh", Toast.LENGTH_LONG).show()
    }

    private fun setup() {
        lenses = LensInfoReader.read(cameraManager).filter { it.facing == CameraCharacteristics.LENS_FACING_BACK }
        selectedId = lenses.firstOrNull { !it.physical }?.id ?: lenses.firstOrNull()?.id
        buildUi()
        preview.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) = openSelectedCamera()
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean { closeCamera(); return true }
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
        }
    }

    private fun buildUi() {
        root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(8, 9, 11)) }
        preview = TextureView(this)
        root.addView(preview, FrameLayout.LayoutParams(-1, -1))
        val overlay = FrameLayout(this)
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))

        val toggle = textButton("◈") { classic = !classic; refreshUi() }
        add(overlay, toggle, 18, 14, 54, 48, Gravity.TOP or Gravity.LEFT)
        brandLabel = TextView(this).apply { text = if (classic) "H A S S E L B L A D" else "XPAN  /  MODE"; setTextColor(Color.WHITE); textSize = 13f; letterSpacing = .18f; gravity = Gravity.CENTER }
        add(overlay, brandLabel, 0, 12, 220, 44, Gravity.TOP or Gravity.CENTER_HORIZONTAL)

        lensText = TextView(this).apply { setTextColor(Color.WHITE); textSize = 12f; setPadding(12, 8, 12, 8); setBackgroundColor(0x88000000.toInt()) }
        add(overlay, lensText, 18, 0, 230, 42, Gravity.BOTTOM or Gravity.LEFT)
        statusText = TextView(this).apply { setTextColor(0xFFE8E2D8.toInt()); textSize = 10f; setPadding(12, 8, 12, 8); setBackgroundColor(0x77000000) }
        add(overlay, statusText, 18, 60, 240, 42, Gravity.TOP or Gravity.LEFT)

        shutterView = View(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(if (classic) 0xFFE8E7E4.toInt() else 0xFFE77D2C.toInt())
                setStroke(4, if (classic) 0xFF777777.toInt() else Color.WHITE)
            }
            setOnClickListener { capture() }
        }
        add(overlay, shutterView, 26, 0, 72, 72, Gravity.RIGHT or Gravity.CENTER_VERTICAL)

        val rightStack = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(6, 8, 6, 8); setBackgroundColor(0x55000000) }
        val info = smallButton("ⓘ") { showLensDetails() }
        val flip = smallButton("↻") { cycleLens() }
        val crop = smallButton("2.39:1") { xpanCrop = !xpanCrop; Toast.makeText(this, if (xpanCrop) "X-Pan crop bật" else "Ảnh toàn khung", Toast.LENGTH_SHORT).show() }
        rightStack.addView(info); rightStack.addView(flip); rightStack.addView(crop)
        add(overlay, rightStack, 0, 0, 54, 166, Gravity.RIGHT or Gravity.TOP)

        val filterRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(6, 4, 6, 4); setBackgroundColor(0x77000000) }
        Filter.values().forEach { f -> filterRow.addView(smallButton(f.title) { selectedFilter = f; refreshUi() }) }
        add(overlay, filterRow, 0, 0, 320, 48, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)

        val gallery = smallButton("▣") { Toast.makeText(this, "Ảnh được lưu trong DCIM/Xpan-Mode", Toast.LENGTH_SHORT).show() }
        add(overlay, gallery, 18, 0, 48, 42, Gravity.BOTTOM or Gravity.RIGHT)
        setContentView(root)
        refreshUi()
    }

    private fun refreshUi() {
        if (!::lensText.isInitialized) return
        val current = lenses.firstOrNull { it.id == selectedId }
        lensText.text = "${current?.label ?: "Camera"}   ·   X-PAN"
        statusText.text = "${selectedFilter.title}  •  ${if (xpanCrop) "2.39:1" else "FULL FRAME"}  •  JPEG HQ"
        brandLabel.text = if (classic) "H A S S E L B L A D" else "XPAN  /  MODE"
        shutterView.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(if (classic) 0xFFE8E7E4.toInt() else 0xFFE77D2C.toInt())
            setStroke(4, if (classic) 0xFF777777.toInt() else Color.WHITE)
        }
    }

    private fun textButton(label: String, action: () -> Unit) = TextView(this).apply {
        text = label; textSize = 23f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
        background = android.graphics.drawable.GradientDrawable().apply { setColor(0x88000000.toInt()); cornerRadius = 16f }
        setOnClickListener { action() }
    }
    private fun smallButton(label: String, action: () -> Unit) = TextView(this).apply {
        text = label; textSize = 12f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); setPadding(8, 10, 8, 10)
        setOnClickListener { action() }
    }
    private fun add(parent: FrameLayout, child: View, left: Int, top: Int, width: Int, height: Int, gravity: Int) {
        val lp = FrameLayout.LayoutParams(if (width == 0) -1 else width.dp(), if (height == 0) -1 else height.dp(), gravity)
        lp.leftMargin = left.dp(); lp.topMargin = top.dp(); parent.addView(child, lp)
    }
    private fun Int.dp() = (this * resources.displayMetrics.density).toInt()

    private fun cycleLens() {
        if (lenses.isEmpty()) return
        val idx = lenses.indexOfFirst { it.id == selectedId }
        selectedId = lenses[(idx + 1).mod(lenses.size)].id
        refreshUi(); openSelectedCamera()
    }

    private fun openSelectedCamera() {
        val id = selectedId ?: return
        if (!preview.isAvailable) return
        closeCamera()
        val selectedLens = lenses.firstOrNull { it.id == id }
        val openId = selectedLens?.logicalId ?: id
        try {
            cameraManager.openCamera(openId, executor, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) { camera = device; createPreviewSession() }
                override fun onDisconnected(device: CameraDevice) { device.close(); camera = null }
                override fun onError(device: CameraDevice, error: Int) { device.close(); camera = null; runOnUiThread { Toast.makeText(this@MainActivity, "Camera $id không mở được", Toast.LENGTH_SHORT).show() } }
            })
        } catch (e: Exception) { Toast.makeText(this, "Không thể mở camera: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    private fun createPreviewSession() {
        val texture = preview.surfaceTexture ?: return
        val lens = lenses.firstOrNull { it.id == selectedId }
        val cameraForSizes = lens?.logicalId ?: device.id
        val size = choosePreviewSize(cameraForSizes)
        texture.setDefaultBufferSize(size.first, size.second)
        val surface = Surface(texture)
        try {
            reader?.close()
            val jpeg = chooseJpegSize(cameraForSizes)
            reader = ImageReader.newInstance(jpeg.first, jpeg.second, android.graphics.ImageFormat.JPEG, 2)
            val imageSurface = reader!!.surface
            val callback = object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) {
                    session = s
                    val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        addTarget(surface); set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    }.build()
                    try { s.setRepeatingRequest(request, null, null) } catch (_: Exception) { }
                }
                override fun onConfigureFailed(s: CameraCaptureSession) { runOnUiThread { Toast.makeText(this@MainActivity, "Cấu hình preview thất bại", Toast.LENGTH_SHORT).show() } }
            }
            if (Build.VERSION.SDK_INT >= 28 && lens?.physical == true && lens.logicalId != lens.id) {
                try {
                    val previewOut = OutputConfiguration(surface).apply { setPhysicalCameraId(lens.id) }
                    val jpegOut = OutputConfiguration(imageSurface).apply { setPhysicalCameraId(lens.id) }
                    val config = SessionConfiguration(SessionConfiguration.SESSION_REGULAR,
                        listOf(previewOut, jpegOut), executor, callback)
                    device.createCaptureSession(config)
                } catch (_: Exception) {
                    device.createCaptureSession(listOf(surface, imageSurface), callback, null)
                }
            } else {
                device.createCaptureSession(listOf(surface, imageSurface), callback, null)
            }
        } catch (e: Exception) { runOnUiThread { Toast.makeText(this, "Camera session: ${e.message}", Toast.LENGTH_LONG).show() } }
    }

    private fun choosePreviewSize(id: String): Pair<Int, Int> {
        val c = cameraManager.getCameraCharacteristics(id)
        val sizes = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(SurfaceTexture::class.java).orEmpty()
        val s = sizes.filter { it.width >= 1280 }.minByOrNull { kotlin.math.abs(it.width.toFloat() / it.height - 1.78f) } ?: sizes.firstOrNull()
        return (s?.width ?: 1920) to (s?.height ?: 1080)
    }
    private fun chooseJpegSize(id: String): Pair<Int, Int> {
        val c = cameraManager.getCameraCharacteristics(id)
        val sizes = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(android.graphics.ImageFormat.JPEG).orEmpty()
        val s = sizes.maxByOrNull { it.width.toLong() * it.height } ?: android.util.Size(4000, 3000)
        return s.width to s.height
    }

    private fun capture() {
        if (captureInProgress) return
        val device = camera ?: return
        val s = session ?: return
        val r = reader ?: return
        captureInProgress = true
        r.setOnImageAvailableListener({ source ->
            val image = source.acquireNextImage() ?: return@setOnImageAvailableListener
            try {
                val bytes = ByteArray(image.planes[0].buffer.remaining()).also { image.planes[0].buffer.get(it) }
                val output = renderOutput(bytes)
                val originalUri = if (xpanCrop || selectedFilter != Filter.NATURAL) saveJpeg(bytes, "_ORIGINAL") else null
                val uri = saveJpeg(output, "_XPAN")
                runOnUiThread {
                    val message = if (uri != null) {
                        if (originalUri != null) "Đã lưu ảnh X-Pan và ảnh gốc đầy đủ" else "Đã lưu ảnh Xpan-Mode · JPEG HQ"
                    } else "Không lưu được ảnh"
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Lỗi lưu ảnh: ${e.message}", Toast.LENGTH_LONG).show() }
            } finally { image.close(); captureInProgress = false }
        }, executor)
        try {
            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(r.surface)
                set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.JPEG_QUALITY, 100.toByte())
                set(CaptureRequest.JPEG_ORIENTATION, 0)
            }.build()
            s.capture(request, object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
                    captureInProgress = false
                    runOnUiThread { Toast.makeText(this@MainActivity, "Chụp ảnh thất bại", Toast.LENGTH_SHORT).show() }
                }
            }, executor)
        } catch (e: Exception) { captureInProgress = false; Toast.makeText(this, "Không chụp được: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    /** X-Pan crop and restrained color rendering are applied only after full-resolution capture.
     * The sensor JPEG is decoded at its original dimensions; no preview-sized frame is saved. */
    private fun renderOutput(bytes: ByteArray): ByteArray {
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
        val target = if (xpanCrop) {
            val ratio = 2.39f
            val current = source.width.toFloat() / source.height
            if (current > ratio) {
                val w = (source.height * ratio).toInt()
                Bitmap.createBitmap(source, (source.width - w) / 2, 0, w, source.height)
            } else {
                val h = (source.width / ratio).toInt()
                Bitmap.createBitmap(source, 0, (source.height - h) / 2, source.width, h)
            }
        } else source
        val matrix = when (selectedFilter) {
            Filter.NATURAL -> ColorMatrix().apply { setSaturation(1.02f) }
            Filter.CLASSIC -> ColorMatrix(floatArrayOf(
                1.10f, 0f, 0f, 0f, 3f, 0f, 1.04f, 0f, 0f, 1f,
                0f, 0f, 0.94f, 0f, 2f, 0f, 0f, 0f, 1f, 0f))
            Filter.VIVID -> ColorMatrix().apply { setSaturation(1.22f) }
            Filter.BRIGHT -> ColorMatrix(floatArrayOf(
                1.04f, 0f, 0f, 0f, 8f, 0f, 1.04f, 0f, 0f, 8f,
                0f, 0f, 1.04f, 0f, 8f, 0f, 0f, 0f, 1f, 0f)).apply { postConcat(ColorMatrix().apply { setSaturation(0.96f) }) }
        }
        val rendered = Bitmap.createBitmap(target.width, target.height, Bitmap.Config.ARGB_8888)
        Canvas(rendered).drawBitmap(target, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix) })
        val stream = java.io.ByteArrayOutputStream()
        rendered.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        if (rendered !== target && rendered !== source) rendered.recycle()
        if (target !== source) target.recycle()
        source.recycle()
        return stream.toByteArray()
    }

    private fun saveJpeg(bytes: ByteArray, suffix: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "XPAN_${System.currentTimeMillis()}${suffix}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/Xpan-Mode")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
        contentResolver.update(uri, values, null, null)
        return uri
    }

    private fun showLensDetails() {
        val details = lenses.joinToString("\n\n") { l ->
            "${l.label}\nID: ${l.id}  |  Logical: ${l.logicalId}${if (l.physical) "  |  PHYSICAL" else ""}\nTiêu cự: ${l.focalLengths.joinToString { "%.1f mm".format(it) }.ifBlank { "Không công bố" }}\nKhẩu độ: ${l.aperture.joinToString { "f/$it" }.ifBlank { "Không công bố" }}\nCảm biến: ${l.pixelArray}  |  Active: ${l.activeArray}\nLấy nét gần nhất: ${l.minFocusDistance ?: "Không công bố"}\nHardware: ${l.hardwareLevel}  |  ${l.capabilities.joinToString()}"
        }.ifBlank { "Không tìm thấy camera sau nào được hệ thống công bố." }
        ScrollView(this).apply {
            addView(TextView(this@MainActivity).apply { text = details; textSize = 13f; setTextColor(Color.WHITE); setPadding(24, 24, 24, 24) })
        }.also { view ->
            android.app.AlertDialog.Builder(this).setTitle("Thông tin ống kính · Camera2").setView(view)
                .setPositiveButton("Đóng", null).show()
        }
    }

    private fun closeCamera() {
        try { session?.close() } catch (_: Exception) { }
        session = null
        try { camera?.close() } catch (_: Exception) { }
        camera = null
        try { reader?.close() } catch (_: Exception) { }
        reader = null
    }
    override fun onPause() { closeCamera(); super.onPause() }
    override fun onResume() { super.onResume(); if (::preview.isInitialized && preview.isAvailable && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) openSelectedCamera() }
    override fun onDestroy() { closeCamera(); executor.shutdown(); super.onDestroy() }
}
