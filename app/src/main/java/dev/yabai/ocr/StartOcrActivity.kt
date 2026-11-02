package dev.yabai.ocr

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import dev.yabai.ocr.service.FloatingOcrService

class StartOcrActivity : AppCompatActivity() {

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            val intent = Intent(this, FloatingOcrService::class.java).apply {
                action = FloatingOcrService.ACTION_START
                putExtra(FloatingOcrService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(FloatingOcrService.EXTRA_RESULT_DATA, data)
            }
            try {
                ContextCompat.startForegroundService(this, intent)
            } catch (_: Throwable) {
                // ignore
            }
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (FloatingOcrService.isRunning) {
            val intent = Intent(this, FloatingOcrService::class.java).apply {
                action = FloatingOcrService.ACTION_STOP
            }
            runCatching { startService(intent) }
            finish()
        } else {
            val manager = getSystemService(MediaProjectionManager::class.java)
            if (manager != null) {
                try {
                    projectionLauncher.launch(manager.createScreenCaptureIntent())
                } catch (_: Throwable) {
                    finish()
                }
            } else {
                finish()
            }
        }
    }
}
