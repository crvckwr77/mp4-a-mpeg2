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

    private val pickVideos = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            startBatchConversion(uris)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnSelect = findViewById(R.id.btnSelectVideo)
        progressBar = findViewById(R.id.progressBar)
        tvStatus = findViewById(R.id.tvStatus)

        btnSelect.text = "Seleccionar Videos MP4"
        btnSelect.setOnClickListener {
            pickVideos.launch("video/mp4")
        }
    }

    private fun startBatchConversion(uris: List<Uri>) {
        btnSelect.isEnabled = false
        progressBar.visibility = View.VISIBLE
        val total = uris.size
        tvStatus.text = "Iniciando lote de $total video(s)..."

        Thread {
            var successCount = 0
            val savedNames = mutableListOf<String>()

            for ((index, uri) in uris.withIndex()) {
                val currentNum = index + 1
                try {
                    val originalName = getCleanFileName(uri)
                    val outputFileName = "$originalName.mpg"

                    runOnUiThread {
                        tvStatus.text = "Preparando $currentNum de $total:\n$originalName.mp4"
                    }

                    val inputFile = File(cacheDir, "input_temp_$index.mp4")
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(inputFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    val tempOutputFile = File(cacheDir, outputFileName)

                    runOnUiThread {
                        tvStatus.text = "Convirtiendo $currentNum de $total:\n$outputFileName\n(MPEG-2 480p • MP2 320kbps)"
                    }

                    val cmd = "-y -i \"${inputFile.absolutePath}\" -vf \"scale=-2:480\" -c:v mpeg2video -q:v 4 -c:a mp2 -b:a 320k -ar 44100 \"${tempOutputFile.absolutePath}\""

                    val session = FFmpegKit.execute(cmd)

                    if (ReturnCode.isSuccess(session.returnCode)) {
                        saveToDownloads(tempOutputFile, outputFileName)
                        successCount++
                        savedNames.add(outputFileName)
                    }

                    inputFile.delete()
                    tempOutputFile.delete()

                } catch (e: Exception) {
                    runOnUiThread {
                        tvStatus.text = "Error en video $currentNum: ${e.message}"
                    }
                }
            }

            runOnUiThread {
                progressBar.visibility = View.GONE
                btnSelect.isEnabled = true
                if (successCount > 0) {
                    val listText = savedNames.joinToString("\n• ", prefix = "• ")
                    tvStatus.text = "¡Listo! Se guardaron $successCount de $total en Descargas:\n\n$listText"
                } else {
                    tvStatus.text = "No se pudo convertir ningún video."
                }
            }
        }.start()
    }

    private fun getCleanFileName(uri: Uri): String {
        var name = "Estereo_${System.currentTimeMillis()}"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    val rawName = cursor.getString(nameIndex)
                    if (!rawName.isNullOrBlank()) {
                        name = rawName.substringBeforeLast(".")
                    }
                }
            }
        }
        val normalized = Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        val cleaned = normalized
            .replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            .replace("_+".toRegex(), "_")
            .trim('_')

        return cleaned.ifEmpty { "Estereo_${System.currentTimeMillis()}" }
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
