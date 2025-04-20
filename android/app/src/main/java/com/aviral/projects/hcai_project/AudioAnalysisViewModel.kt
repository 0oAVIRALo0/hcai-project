package com.aviral.projects.hcai_project

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aviral.projects.hcai_project.data.model.AnalysisResult
import com.aviral.projects.hcai_project.data.model.AudioAnalysisState
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class AudioAnalysisViewModel : ViewModel() {
    // Add these variables to track the audio file
    private val _uiState = mutableStateOf(AudioAnalysisState())
    val uiState: State<AudioAnalysisState> = _uiState

    private var _audioFile: File? = null
    private var context: Context? = null

    fun setContext(context: Context) {
        this.context = context
    }

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun toggleRecording() {
        if (uiState.value.isRecording) stopRecording()
        else startRecording()
    }

    private fun startRecording() {
        val context = context ?: return
        try {
            outputFile = File.createTempFile("recording_", ".mp3", context.cacheDir)
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile?.absolutePath)
                prepare()
                start()
            }
            _uiState.value = _uiState.value.copy(isRecording = true)
        } catch (e: Exception) {
            handleError("Recording failed: ${e.localizedMessage}")
            mediaRecorder?.release()
            mediaRecorder = null
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            handleError("Stop failed: ${e.localizedMessage}")
        } finally {
            mediaRecorder?.release()
            mediaRecorder = null
            _audioFile = outputFile
            _uiState.value = _uiState.value.copy(
                isRecording = false,
                showAnalyzeButton = true
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaRecorder?.release()
    }

    fun handleFileUri(uri: Uri) {
        val context = context ?: return
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val file = File.createTempFile("audio_", ".wav", context.cacheDir)

            inputStream?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }

            // Update state after successful file selection
            _uiState.value = _uiState.value.copy(
                showAnalyzeButton = true,
                error = null
            )
            _audioFile = file

        } catch (e: Exception) {
            handleError("File handling failed: ${e.localizedMessage}")
        }
    }

    fun onDismissResult() {
        _uiState.value = _uiState.value.copy(
            result = null,
            error = null
        )
    }

    fun onAnalyzeClicked() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                result = null
            )

            try {
                val file = _audioFile ?: run {
                    handleError("No audio file selected")
                    return@launch
                }

                val result = analyzeAudioFile(file)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    result = result
                )
            } catch (e: Exception) {
                handleError("Analysis failed: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    private suspend fun analyzeAudioFile(file: File): AnalysisResult {
        val requestFile = file.asRequestBody("audio/*".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("audio", file.name, requestFile)

        val response = ApiClient.instance.analyzeAudio(body)

        if (!response.isSuccessful) {
            throw IOException("API error: ${response.code()} - ${response.message()}")
        }

        val responseBody = response.body() ?: throw IOException("Empty response body")

        return AnalysisResult(
            isScam = responseBody.scam.result.equals("Scam", ignoreCase = true),
            isDeepfake = responseBody.deepfake.result.equals("Fake", ignoreCase = true),
            scamConfidence = responseBody.scam.confidence.toFloat(),
            deepfakeConfidence = responseBody.deepfake.confidence.toFloat(),
            transcribedText = responseBody.transcription
        )
    }

    fun handleError(message: String) {
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            error = message
        )
    }
}