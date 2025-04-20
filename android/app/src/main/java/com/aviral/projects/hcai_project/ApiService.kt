package com.aviral.projects.hcai_project

import com.aviral.projects.hcai_project.data.model.AnalysisResponse
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ApiService {
    @Multipart
    @POST("analyze")
    suspend fun analyzeAudio(
        @Part file: MultipartBody.Part
    ): Response<AnalysisResponse>
}