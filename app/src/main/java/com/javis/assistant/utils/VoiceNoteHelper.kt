package com.javis.assistant.utils

import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records a voice note from the microphone and sends it via WhatsApp.
 *
 * Flow:
 * 1. JAVIS says "Recording — speak now"
 * 2. Records the user's voice for up to [maxSeconds]
 * 3. User says "stop" (ChatViewModel stops recording)
 * 4. Audio saved as .m4a → shared to WhatsApp to the target contact
 */
@Singleton
class VoiceNoteHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val systemCommandHandler: SystemCommandHandler
) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var isRecording = false

    fun isRecording() = isRecording

    /**
     * Start recording a voice note.
     * Returns true if recording started successfully.
     */
    fun startRecording(): Boolean {
        if (isRecording) return false
        return try {
            val file = File(context.cacheDir, "javis_voice_note_${System.currentTimeMillis()}.m4a")
            outputFile = file

            val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioChannels(1)
            rec.setAudioSamplingRate(44100)
            rec.setAudioEncodingBitRate(128000)
            rec.setOutputFile(file.absolutePath)
            rec.prepare()
            rec.start()
            recorder = rec
            isRecording = true
            true
        } catch (e: Exception) {
            isRecording = false
            false
        }
    }

    /**
     * Stop recording and return the output file.
     */
    fun stopRecording(): File? {
        if (!isRecording) return null
        return try {
            recorder?.stop()
            recorder?.release()
            recorder = null
            isRecording = false
            outputFile
        } catch (e: Exception) {
            recorder = null
            isRecording = false
            outputFile
        }
    }

    /**
     * Send the recorded voice note to a WhatsApp contact.
     * Looks up phone number from contacts, then shares via Intent.
     */
    fun sendVoiceNoteViaWhatsApp(contactName: String, audioFile: File): Boolean {
        val contacts = systemCommandHandler.findContacts(contactName)
        val phone = contacts.firstOrNull()?.phone?.replace(Regex("[^+0-9]"), "")
            ?: return openWhatsAppWithAudio(audioFile)

        return try {
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.provider", audioFile
            )

            // Open WhatsApp to the specific contact chat (via deep link)
            // Then send audio via share intent targeted at WhatsApp
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/mp4"
                setPackage("com.whatsapp")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(shareIntent)
            true
        } catch (e: Exception) {
            openWhatsAppWithAudio(audioFile)
        }
    }

    private fun openWhatsAppWithAudio(audioFile: File): Boolean {
        return try {
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.provider", audioFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Send voice note").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            true
        } catch (e: Exception) { false }
    }

    fun cleanup() {
        try {
            recorder?.release()
            recorder = null
            isRecording = false
        } catch (_: Exception) {}
    }
}
