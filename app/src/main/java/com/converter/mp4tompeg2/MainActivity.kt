package com.converter.mp4tompeg2

import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var btnSelect: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView

    private val pickVideo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { startConversion(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnSelect = findViewById(R.id.btnSelectVideo)
        progressBar = findViewById(R.id.progressBar)
        tvStatus = findViewById(R.id.tvStatus)

        btnSelect.setOnClickListener {
            pickVideo.launch("video/mp4")
        }
    }

    private fun startConversion(uri: Uri) {
        btnSelect.isEnabled = false
        progressBar.visibility = View.VISIBLE
        tvStatus.text = "Preparando video..."

        Thread {
            try {
                val inputFile = File(cacheDir, "input_temp.mp4")
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(inputFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val outputFileName = "Estereo_${System.currentTimeMillis()}.mpg"
                val tempOutputFile = File(cacheDir, outputFileName)

                runOnUiThread {
                    tvStatus.text = "Convirtiendo a MPEG-2 (480p) + MP2 (320kbps)...\nEsto puede tardar unos minutos."
                }

                val cmd = "-y -i \"${inputFile.absolutePath}\" -vf \"scale=-2:480\" -c:v mpeg2video -q:v 4 -c:a mp2 -b:a 320k -ar 44100 \"${tempOutputFile.absolutePath}\""

                val session = FFmpegKit.execute(cmd)

                if (ReturnCode.isSuccess(session.returnCode)) {
                    saveToDownloads(tempOutputFile, outputFileName)
                    runOnUiThread {
                        tvStatus.text = "¡Listo! Guardado en Descargas como:\n$outputFileName"
                    }
                } else {
                    runOnUiThread {
                        tvStatus.text = "Error al convertir el video."
                    }
                }

                inputFile.delete()
                tempOutputFile.delete()

            } catch (e: Exception) {
                runOnUiThread {
                    tvStatus.text = "Error: ${e.message}"
                }
            } finally {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    btnSelect.isEnabled = true
                }
            }
        }.start()
    }

    private fun saveToDownloads(sourceFile: File, fileName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            uri?.let {
                contentResolver.openOutputStream(it)?.use { out ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(out)
                    }
                }
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val destFile = File(downloadsDir, fileName)
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(destFile).use { out ->
                    input.copyTo(out)
                }
            }
        }
    }
}
