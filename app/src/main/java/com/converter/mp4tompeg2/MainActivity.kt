package com.converter.mp4tompeg2

import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
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
import java.text.Normalizer

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

    private fun getCleanFileName(uri: Uri): String {
        var rawName = "Video_${System.currentTimeMillis()}"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    val displayName = cursor.getString(nameIndex)
                    if (!displayName.isNullOrBlank()) {
                        rawName = displayName
                    }
                }
            }
        }

        val nameWithoutExt = rawName.substringBeforeLast(".")
        val normalized = Normalizer.normalize(nameWithoutExt, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        val safeName = normalized
            .replace(Regex("[^a-zA-Z0-9._-]+"), "_")
            .trim('_')

        val finalBaseName = if (safeName.isNotEmpty()) safeName else "Video_${System.currentTimeMillis()}"
        return "$finalBaseName.mpg"
    }

    private fun startConversion(uri: Uri) {
        btnSelect.isEnabled = false
        progressBar.visibility = View.VISIBLE
        tvStatus.text = "Preparando video..."

        Thread {
            try {
                val outputFileName = getCleanFileName(uri)
                val inputFile = File(cacheDir, "input_temp.mp4")
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(inputFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val tempOutputFile = File(cacheDir, outputFileName)

                runOnUiThread {
                    tvStatus.text = "Convirtiendo $outputFileName\na MPEG-2 (480p) + MP2 (320kbps)...\nEsto puede tardar unos minutos."
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
            var destFile = File(downloadsDir, fileName)
            var counter = 1
            val baseName = fileName.substringBeforeLast(".")
            while (destFile.exists()) {
                destFile = File(downloadsDir, "$baseName($counter).mpg")
                counter++
            }
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(destFile).use { out ->
                    input.copyTo(out)
                }
            }
        }
    }
}
