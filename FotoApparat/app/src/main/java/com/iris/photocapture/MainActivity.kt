package com.iris.photocapture

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.CompoundButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import io.fotoapparat.Fotoapparat
import io.fotoapparat.configuration.CameraConfiguration
import io.fotoapparat.configuration.UpdateConfiguration
import io.fotoapparat.log.logcat
import io.fotoapparat.parameter.Flash
import io.fotoapparat.parameter.Zoom
import io.fotoapparat.result.transformer.scaled
import io.fotoapparat.selector.*
import kotlinx.android.synthetic.main.activity_main.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private val permissionsDelegate = PermissionsDelegate(this)

    private var permissionsGranted: Boolean = false
    private var activeCamera: Camera = Camera.Back

    private lateinit var fotoapparat: Fotoapparat
    private lateinit var cameraZoom: Zoom.VariableZoom
    private var bitmapGroup: ArrayList<String> = ArrayList<String>()
    private var i: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        permissionsGranted = permissionsDelegate.hasCameraPermission()

        if (permissionsGranted) {
            cameraView.visibility = View.VISIBLE
        } else {
            permissionsDelegate.requestCameraPermission()
        }

        fotoapparat = Fotoapparat(
            context = this,
            view = cameraView,
            focusView = focusView,
            logger = logcat(),
            lensPosition = activeCamera.lensPosition,
            cameraConfiguration = activeCamera.configuration,
            cameraErrorCallback = { Log.e(LOGGING_TAG, "Camera error: ", it) }
        )

        capture onClick takePicture()
        switchCamera onClick changeCamera()
        torchSwitch onCheckedChanged toggleFlash()
        procesar onClick { iniciarProcesamiento() }
    }

    private fun localSaveInApp(fileName: String, bmp: Bitmap) {
        val stream = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 90, stream)
        val file: String = fileName
        val data: ByteArray = stream.toByteArray()
        val fileOutputStream: FileOutputStream
        try {
            fileOutputStream = openFileOutput(file, Context.MODE_PRIVATE)
            fileOutputStream.write(data)
            i++
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun iniciarProcesamiento() {
        val intent = Intent(this, activity_Procesando::class.java).apply {
            putExtra("bitmaps", bitmapGroup)
        }
        startActivity(intent)
    }

    private fun takePicture(): () -> Unit = {
        val photoResult = fotoapparat
            .autoFocus()
            .takePicture()

        photoResult
            .saveToFile(
                File(
                    getExternalFilesDir("photos"),
                    "photo.jpg"
                )
            )

        photoResult
            .toBitmap(scaled(scaleFactor = 0.25f))
            .whenAvailable { photo ->
                photo
                    ?.let {
                        Log.i(LOGGING_TAG, "New photo captured. Bitmap length: ${it.bitmap.byteCount}")

                        val imageView = findViewById<ImageView>(R.id.result)

                        imageView.setImageBitmap(it.bitmap)
                        imageView.rotation = (-it.rotationDegrees).toFloat()
                        // it.bitmap.recycle()
                    }
                    ?: Log.e(LOGGING_TAG, "Couldn't capture photo.")
            }
        photoResult
            .toBitmap(scaled(scaleFactor = 0.25f))
            .whenAvailable { photo ->
                photo
                    ?.let {
                        Log.i(LOGGING_TAG, "New photo added to ArrayList. Bitmap length: ${it.bitmap.byteCount}")
                        val name = "IMG_$i"
                        val bitmapRotated: Bitmap = girarBitmap(it.bitmap)
                        it.bitmap.recycle()
                        localSaveInApp(name, bitmapRotated)
                        bitmapGroup.add(name)
                        habilitarBotonProcesado()
                        bitmapRotated.recycle()
                    }
                    ?: Log.e(LOGGING_TAG, "Couldn't add photo.")
            }
    }

    private fun girarBitmap(bitmapOrg: Bitmap): Bitmap {
        val width = bitmapOrg.width
        val height = bitmapOrg.height
        val matrix = Matrix()

        matrix.postRotate(90F)

        val scaledBitmap = Bitmap.createScaledBitmap(bitmapOrg, width, height, true)

        val rotatedBitmap = Bitmap.createBitmap(
            scaledBitmap,
            0,
            0,
            scaledBitmap.width,
            scaledBitmap.height,
            matrix,
            true
        )
        return rotatedBitmap
    }

    private fun habilitarBotonProcesado() {
        procesar.isEnabled = true
    }

    private fun changeCamera(): () -> Unit = {
        activeCamera = when (activeCamera) {
            Camera.Front -> Camera.Back
            Camera.Back -> Camera.Front
        }

        fotoapparat.switchTo(
            lensPosition = activeCamera.lensPosition,
            cameraConfiguration = activeCamera.configuration
        )

        adjustViewsVisibility()

        torchSwitch.isChecked = false

        Log.i(LOGGING_TAG, "New camera position: ${if (activeCamera is Camera.Back) "back" else "front"}")
    }

    private fun toggleFlash(): (CompoundButton, Boolean) -> Unit = { _, isChecked ->
        fotoapparat.updateConfiguration(
            UpdateConfiguration(
                flashMode = if (isChecked) {
                    firstAvailable(
                        torch(),
                        off()
                    )
                } else {
                    off()
                }
            )
        )

        Log.i(LOGGING_TAG, "Flash is now ${if (isChecked) "on" else "off"}")
    }

    override fun onStart() {
        super.onStart()
        if (permissionsGranted) {
            fotoapparat.start()
            adjustViewsVisibility()
        }
    }

    override fun onStop() {
        super.onStop()
        if (permissionsGranted) {
            fotoapparat.stop()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (permissionsDelegate.resultGranted(requestCode, permissions, grantResults)) {
            permissionsGranted = true
            fotoapparat.start()
            adjustViewsVisibility()
            cameraView.visibility = View.VISIBLE
        }
    }

    private fun adjustViewsVisibility() {
        fotoapparat.getCapabilities()
            .whenAvailable { capabilities ->
                capabilities
                    ?.let {
                        (it.zoom as? Zoom.VariableZoom)
                            ?.let { zoom -> setupZoom(zoom) }
                            ?: run { zoomSeekBar.visibility = View.GONE }

                        torchSwitch.visibility = if (it.flashModes.contains(Flash.Torch)) View.VISIBLE else View.GONE
                    }
                    ?: Log.e(LOGGING_TAG, "Couldn't obtain capabilities.")
            }

        switchCamera.visibility = if (fotoapparat.isAvailable(front())) View.VISIBLE else View.GONE
    }

    private fun setupZoom(zoom: Zoom.VariableZoom) {
        zoomSeekBar.max = zoom.maxZoom
        cameraZoom = zoom
        zoomSeekBar.visibility = View.VISIBLE
        zoomSeekBar onProgressChanged { updateZoom(zoomSeekBar.progress) }
        updateZoom(0)
    }

    private fun updateZoom(progress: Int) {
        fotoapparat.setZoom(progress.toFloat() / zoomSeekBar.max)
        val value = cameraZoom.zoomRatios[progress]
        val roundedValue = ((value.toFloat()) / 10).roundToInt().toFloat() / 10
        zoomLvl.text = String.format("%.1f ×", roundedValue)
    }
}

private const val LOGGING_TAG = "IRIS3D"

private sealed class Camera(
    val lensPosition: LensPositionSelector,
    val configuration: CameraConfiguration
) {

    object Back : Camera(
        lensPosition = back(),
        configuration = CameraConfiguration(
            previewResolution = firstAvailable(
                wideRatio(highestResolution()),
                standardRatio(highestResolution())
            ),
            previewFpsRange = highestFps(),
            flashMode = off(),
            focusMode = firstAvailable(
                continuousFocusPicture(),
                autoFocus()
            ),
            frameProcessor = {
                // Do something with the preview frame
            }
        )
    )

    object Front : Camera(
        lensPosition = front(),
        configuration = CameraConfiguration(
            previewResolution = firstAvailable(
                wideRatio(highestResolution()),
                standardRatio(highestResolution())
            ),
            previewFpsRange = highestFps(),
            flashMode = off(),
            focusMode = firstAvailable(
                fixed(),
                autoFocus()
            )
        )
    )
}
