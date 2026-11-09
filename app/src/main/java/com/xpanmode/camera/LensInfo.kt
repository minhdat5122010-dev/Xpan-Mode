package com.xpanmode.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import java.util.Locale

data class LensInfo(
    val id: String,
    val logicalId: String,
    val label: String,
    val facing: Int,
    val focalLengths: List<Float>,
    val aperture: List<Float>,
    val pixelArray: String,
    val activeArray: String,
    val minFocusDistance: Float?,
    val hardwareLevel: String,
    val capabilities: List<String>,
    val physical: Boolean
)

/** Reads every camera ID exposed by the Android camera HAL, then expands logical cameras
 * into their physical camera IDs where the device/OS exposes them. */
object LensInfoReader {
    fun read(manager: CameraManager): List<LensInfo> {
        val out = linkedMapOf<String, LensInfo>()
        for (id in manager.cameraIdList) {
            try {
                val c = manager.getCameraCharacteristics(id)
                add(out, id, id, c, false)
                if (Build.VERSION.SDK_INT >= 28) {
                    c.physicalCameraIds.forEach { physicalId ->
                        try {
                            val pc = manager.getCameraCharacteristics(physicalId)
                            add(out, physicalId, id, pc, true)
                        } catch (_: Exception) { }
                    }
                }
            } catch (_: Exception) { }
        }
        return out.values.sortedWith(compareBy<LensInfo> { it.facing }.thenBy { it.focalLengths.minOrNull() ?: 0f })
    }

    private fun add(out: MutableMap<String, LensInfo>, id: String, logicalId: String,
                    c: CameraCharacteristics, physical: Boolean) {
        val facing = c.get(CameraCharacteristics.LENS_FACING) ?: -1
        val focal = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.toList().orEmpty()
        val apertures = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.toList().orEmpty()
        val pixels = c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)?.let { "${it.width} × ${it.height}" } ?: "Không công bố"
        val active = c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)?.let { "${it.width()} × ${it.height()}" } ?: "Không công bố"
        val focus = c.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
        val level = when (c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
            else -> "UNKNOWN"
        }
        val caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.mapNotNull {
            when (it) {
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW -> "RAW"
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR -> "MANUAL_SENSOR"
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING -> "MANUAL_POST_PROCESSING"
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA -> "LOGICAL_MULTI_CAMERA"
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE -> "BURST"
                else -> null
            }
        }.orEmpty()
        val lens = when {
            facing != CameraCharacteristics.LENS_FACING_BACK -> "Camera $id"
            focal.isEmpty() -> "Camera sau $id"
            else -> {
                val f = focal.minOrNull() ?: 0f
                val type = when {
                    f < 2.5f -> "Siêu rộng"
                    f < 5.5f -> "Chính"
                    else -> "Tele"
                }
                "$type · ${f.format1()} mm"
            }
        }
        out[id] = LensInfo(id, logicalId, lens, facing, focal, apertures, pixels, active,
            focus, level, caps, physical)
    }
    private fun Float.format1() = String.format(Locale.US, "%.1f", this)
}
