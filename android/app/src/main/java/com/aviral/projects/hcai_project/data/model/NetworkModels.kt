package com.aviral.projects.hcai_project.data.model

data class UploadResponse(
    val message: String,
    val filename: String
)

data class ProcessRequest(
    val filename: String
)

data class ProcessResponse(
    val audio_analysis: AudioAnalysis,
    val text_analysis: TextAnalysis
)

data class AudioAnalysis(
    val is_deepfake: Boolean,
    val deepfake_confidence: Float
)

data class TextAnalysis(
    val is_scam: Boolean,
    val scam_confidence: Float,
    val transcribed_text: String
)

data class AudioAnalysisState(
    val isRecording: Boolean = false,
    val showAnalyzeButton: Boolean = false,
    val isLoading: Boolean = false,
    val result: AnalysisResult? = null,
    val error: String? = null
)


data class AnalysisResult(
    val isScam: Boolean,
    val isDeepfake: Boolean,
    val scamConfidence: Float,
    val deepfakeConfidence: Float,
    val transcribedText: String
)

data class AnalysisResponse(
    val transcription: String,
    val deepfake: DeepfakeResult,
    val scam: ScamResult,
    val error: String?
)

data class DeepfakeResult(
    val result: String,
    val confidence: Double
)

data class ScamResult(
    val result: String,
    val confidence: Double
)