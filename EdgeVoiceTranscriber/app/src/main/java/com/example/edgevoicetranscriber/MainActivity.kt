package com.example.edgevoicetranscriber

import android.Manifest
import android.content.pm.PackageManager
import android.media.*
import android.os.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import android.widget.*
import org.vosk.Model
import org.vosk.Recognizer
import org.json.JSONObject
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslatorOptions
import java.io.*
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var btn: Button
    private lateinit var tvTrans: TextView
    private lateinit var tvTransl: TextView
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var translator = com.google.mlkit.nl.translate.Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.HINDI)
            .setTargetLanguage(TranslateLanguage.ENGLISH)
            .build()
    )
    private var isRec = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        btn = findViewById(R.id.btnRecord)
        tvTrans = findViewById(R.id.tvTranscript)
        tvTransl = findViewById(R.id.tvTranslation)
        btn.setOnClickListener {
            if (!isRec) startRec() else stopRec()
        }
        thread { prepareModel() }
    }

    private fun prepareModel() {
        val dir = File(filesDir, "vosk-model-small-hi-in-0.22")
        if (!dir.exists()) {
            runOnUiThread { tvTrans.text = "Downloading Hindi model (~40MB)..." }
            val zip = File(filesDir, "model.zip")
            URL("https://alphacephei.com/vosk/models/vosk-model-small-hi-in-0.22.zip")
                .openStream().use { it.copyTo(zip.outputStream()) }
            unzip(zip, filesDir)
            zip.delete()
        }
        model = Model(dir.absolutePath)
        runOnUiThread { tvTrans.text = "Model ready. Tap record." }
    }

    private fun startRec() {
        if (model == null) { tvTrans.text = "Model not ready"; return }
        recognizer = Recognizer(model, 16000.0f)
        isRec = true; btn.text = "Stop"
        thread {
            val rec = AudioRecord(
                MediaRecorder.AudioSource.MIC, 16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                AudioRecord.getMinBufferSize(16000,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            )
            val buf = ShortArray(2048)
            rec.startRecording()
            while (isRec) {
                val read = rec.read(buf, 0, buf.size)
                if (read > 0) {
                    val bytes = ShortArrayToByteArray(buf, read)
                    val final = recognizer!!.acceptWaveForm(bytes, bytes.size)
                    val text = JSONObject(
                        if (final) recognizer!!.result else recognizer!!.partialResult
                    ).optString("text", "")
                    runOnUiThread { tvTrans.text = text }
                    if (final && text.isNotEmpty()) translate(text)
                }
            }
            rec.stop(); rec.release()
        }
    }

    private fun stopRec() { isRec = false; btn.text = "Start" }

    private fun translate(text: String) {
        val cond = DownloadConditions.Builder().requireWifi().build()
        translator.downloadModelIfNeeded(cond)
            .addOnSuccessListener {
                translator.translate(text).addOnSuccessListener {
                    tvTransl.text = it
                }
            }
    }

    private fun unzip(zip: File, target: File) {
        val zin = java.util.zip.ZipInputStream(FileInputStream(zip))
        var e = zin.nextEntry
        while (e != null) {
            val f = File(target, e.name)
            if (e.isDirectory) f.mkdirs()
            else {
                f.parentFile?.mkdirs()
                FileOutputStream(f).use { zin.copyTo(it) }
            }
            zin.closeEntry(); e = zin.nextEntry
        }
        zin.close()
    }

    private fun ShortArrayToByteArray(shorts: ShortArray, len: Int): ByteArray {
        val bytes = ByteArray(len * 2)
        for (i in 0 until len) {
            val v = shorts[i].toInt()
            bytes[i * 2] = (v and 0xff).toByte()
            bytes[i * 2 + 1] = ((v ushr 8) and 0xff).toByte()
        }
        return bytes
    }
}
