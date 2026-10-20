package com.example.romear.feature.social

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.util.Base64
import android.view.Gravity
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.romear.R
import com.example.romear.core.designsystem.ExperienceScreen
import com.example.romear.core.designsystem.HelloFeedback
import com.example.romear.core.designsystem.VoiceWaveformView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID

class VoiceUi(private val fragment: Fragment, private val repo: SocialRepository) {
    private var pendingPermission: (() -> Unit)? = null
    private var player: MediaPlayer? = null
    private var playingFile: File? = null
    private var playJob: Job? = null
    private var playbackOwner: LifecycleOwner? = null
    private val permission = fragment.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingPermission; pendingPermission = null
        if (granted) action?.invoke()
        else fragment.view?.let { HelloFeedback.info(it, "Microphone access is needed to record. You can enable it in app settings.") }
    }

    fun play(id: String, onFinished: () -> Unit = {}) {
        stopPlayback()
        val owner = fragment.viewLifecycleOwner
        if (playbackOwner !== owner) {
            playbackOwner = owner
            owner.lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) { stopPlayback() }
                override fun onDestroy(owner: LifecycleOwner) { stopPlayback(); playbackOwner = null }
            })
        }
        playJob = owner.lifecycleScope.launch {
            var file: File? = null
            try {
                val bytes = repo.binary("v1/voice/$id")
                val destination = withContext(Dispatchers.IO) {
                    File.createTempFile("voice-play-", ".m4a", fragment.requireContext().cacheDir).also { it.writeBytes(bytes) }
                }
                file = destination
                playingFile = destination
                player = MediaPlayer().apply {
                    setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    setDataSource(destination.absolutePath)
                    setOnPreparedListener { it.start() }
                    setOnCompletionListener { stopPlayback(); onFinished() }
                    setOnErrorListener { _, _, _ -> stopPlayback(); onFinished(); true }
                    prepareAsync()
                }
            } catch (cancelled: CancellationException) { file?.delete(); throw cancelled }
            catch (failure: Exception) { file?.delete(); fragment.view?.let { HelloFeedback.error(it, "Couldn’t play this recording. Please retry.") }; onFinished() }
        }
    }

    fun stopPlayback() {
        playJob?.cancel(); playJob = null
        player?.release(); player = null
        playingFile?.delete(); playingFile = null
    }

    fun record(conversationId: String? = null, onSaved: () -> Unit = {}) {
        stopPlayback()
        val page = ExperienceScreen(fragment)
        val ui = page.ui
        ui.header(page.content, "LET THEM HEAR YOU", if (conversationId == null) "Record your 15s voice bio" else "Record a voice message", "Say your name and your favourite weekend plan.")
        val studio = ui.column(24).apply { setBackgroundResource(R.drawable.bg_design_card); gravity = Gravity.CENTER_HORIZONTAL }
        val waveform = VoiceWaveformView(fragment.requireContext())
        studio.addView(waveform, LinearLayout.LayoutParams(-1, ui.dp(55)))
        val timer = ui.text("0:00 / 0:15", 32f, R.color.text_primary, true).apply { gravity = Gravity.CENTER }
        ui.add(studio, timer, 24)
        val microphone = ImageButton(fragment.requireContext()).apply {
            setImageResource(R.drawable.ic_mic)
            setColorFilter(context.getColor(R.color.white))
            background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(context.getColor(R.color.primary_coral)) }
            setPadding(ui.dp(22), ui.dp(22), ui.dp(22), ui.dp(22))
            contentDescription = "Start recording"
        }
        studio.addView(microphone, LinearLayout.LayoutParams(ui.dp(80), ui.dp(80)).apply { topMargin = ui.dp(24); gravity = Gravity.CENTER_HORIZONTAL })
        ui.add(page.content, studio, 20)
        val hint = ui.text("Tap to record · 15 seconds max")
        ui.add(page.content, hint, 20)
        var recorder: MediaRecorder? = null
        var file: File? = null
        var duration = 0
        var clockJob: Job? = null
        var preview: MediaPlayer? = null
        var startedAt = 0L
        var uploading = false
        val key = UUID.randomUUID().toString()
        val save = ui.button(page.actions, if (conversationId == null) "Save Voice Bio" else "Send voice message", true) { }
        save.isEnabled = false
        fun releasePreview() { preview?.release(); preview = null }
        fun stop() {
            val active = recorder ?: return
            recorder = null
            clockJob?.cancel(); clockJob = null
            duration = (SystemClock.elapsedRealtime() - startedAt).toInt().coerceAtMost(15000)
            try { active.stop() } catch (_: RuntimeException) { duration = 0; file?.delete() }
            finally { active.release() }
            microphone.contentDescription = "Record again"
            timer.text = "0:%02d / 0:15".format(duration / 1000)
            save.isEnabled = duration >= 500 && file?.exists() == true
            hint.text = if (save.isEnabled) "Ready to preview · tap the microphone to record again" else "Recording was too short. Tap to try again."
        }
        ui.button(page.content, "Preview recording") {
            if (recorder != null) stop()
            val recorded = file
            if (recorded != null && duration >= 500) {
                releasePreview()
                try {
                    preview = MediaPlayer().apply { setDataSource(recorded.absolutePath); setOnPreparedListener { it.start() }; setOnCompletionListener { releasePreview() }; prepareAsync() }
                } catch (_: Exception) { HelloFeedback.error(page.content, "Couldn’t preview this recording. Please record again.") }
            }
        }
        val owner = fragment.viewLifecycleOwner
        val observer = object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) { pendingPermission = null; stop(); releasePreview() }
        }
        owner.lifecycle.addObserver(observer)
        page.onDismiss = { pendingPermission = null; stop(); releasePreview(); file?.delete(); owner.lifecycle.removeObserver(observer) }
        fun start() {
            releasePreview(); file?.delete(); duration = 0; save.isEnabled = false
            try {
                val destination = File.createTempFile("voice-record-", ".m4a", fragment.requireContext().cacheDir)
                file = destination
                @Suppress("DEPRECATION")
                val active = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(fragment.requireContext()) else MediaRecorder()
                recorder = active
                active.setAudioSource(MediaRecorder.AudioSource.MIC)
                active.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                active.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                active.setAudioSamplingRate(22050)
                active.setAudioEncodingBitRate(32000)
                active.setMaxDuration(15000)
                active.setOutputFile(destination.absolutePath)
                active.setOnInfoListener { _, what, _ -> if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) stop() }
                active.prepare(); active.start()
                startedAt = SystemClock.elapsedRealtime()
                waveform.reset()
                microphone.contentDescription = "Stop recording"
                hint.text = "Recording… Tap to stop"
                clockJob = owner.lifecycleScope.launch {
                    while (recorder != null) {
                        val elapsed = (SystemClock.elapsedRealtime() - startedAt).toInt()
                        timer.text = "0:%02d / 0:15".format((elapsed / 1000).coerceAtMost(15))
                        waveform.amplitude(runCatching { active.maxAmplitude }.getOrDefault(0))
                        if (elapsed >= 15000) { stop(); break }
                        delay(100)
                    }
                }
            } catch (_: Exception) {
                recorder?.release(); recorder = null; file?.delete()
                HelloFeedback.error(page.content, "Couldn’t access the microphone. Please try again.")
            }
        }
        microphone.setOnClickListener {
            if (uploading) return@setOnClickListener
            if (recorder != null) stop()
            else if (ContextCompat.checkSelfPermission(fragment.requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start()
            else { pendingPermission = { start() }; permission.launch(Manifest.permission.RECORD_AUDIO) }
        }
        save.setOnClickListener {
            val recorded = file ?: return@setOnClickListener
            if (uploading || duration < 500) return@setOnClickListener
            uploading = true; save.isEnabled = false; microphone.isEnabled = false
            owner.lifecycleScope.launch {
                try {
                    val encoded = withContext(Dispatchers.IO) { require(recorded.length() in 100L..100000L) { "Record a shorter voice note" }; Base64.encodeToString(recorded.readBytes(), Base64.NO_WRAP) }
                    val path = conversationId?.let { "v1/conversations/$it/voice" } ?: "v1/me/voice"
                    repo.request(path, "POST", JSONObject().put("requestId", key).put("audioBase64", encoded).put("durationMs", duration))
                    onSaved(); page.dismiss()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { HelloFeedback.error(page.content, failure.message ?: "Couldn’t save. Your recording is ready to retry.") }
                finally { uploading = false; save.isEnabled = true; microphone.isEnabled = true }
            }
        }
        page.show()
    }
}
