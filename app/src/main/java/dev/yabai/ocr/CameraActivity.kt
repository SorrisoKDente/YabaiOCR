package dev.yabai.ocr

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import dev.yabai.ocr.service.FloatingOcrService
import java.io.File

/**
 * The bubble's camera (R46d): takes one photo and hands it to the running service.
 *
 * The picture goes to a file in the app's cache, which is the only way the platform returns a
 * full-size photo — `TakePicturePreview` answers with a thumbnail too small to read. The service
 * reads it and deletes it; nothing is ever added to the gallery.
 *
 * This is a doorway, like [StartOcrActivity]: it has no screen of its own and finishes as soon as
 * the camera app is done.
 */
class CameraActivity : AppCompatActivity() {

    private var photo: File? = null

    private val camera = registerForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val file = photo
        photo = null
        if (taken && file != null) hand(file) else file?.delete()
        finish()
    }

    private val permission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                shoot()
            } else {
                Toast.makeText(this, R.string.camera_permission_denied, Toast.LENGTH_SHORT).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A doorway is not restored: the photo it was waiting for belongs to the camera app, and the
        // service it would hand it to is the one that opened it.
        if (savedInstanceState != null || !FloatingOcrService.isRunning) {
            finish()
            return
        }
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) shoot() else permission.launch(Manifest.permission.CAMERA)
    }

    private fun shoot() {
        val directory = File(cacheDir, "photos").apply { mkdirs() }
        val file = runCatching { File.createTempFile("photo-", ".jpg", directory) }.getOrNull()
        if (file == null) {
            finish()
            return
        }
        photo = file
        val uri = FileProvider.getUriForFile(this, "$packageName.photos", file)
        // A camera that cannot be opened (in use, removed, disabled) closes the doorway, not the app.
        if (runCatching { camera.launch(uri) }.isFailure) {
            file.delete()
            finish()
        }
    }

    /** From here the file belongs to the service, which reads it and deletes it. */
    private fun hand(file: File) {
        val intent = Intent(this, FloatingOcrService::class.java).apply {
            action = FloatingOcrService.ACTION_PHOTO
            putExtra(FloatingOcrService.EXTRA_PHOTO_PATH, file.path)
        }
        // The service opened this screen, so it is already running and in the foreground.
        if (runCatching { startService(intent) }.isFailure) file.delete()
    }
}
