package com.sesac.speechapp.data.repository

import android.content.Context
import com.sesac.speechapp.data.local.TokenManager
import com.sesac.speechapp.data.remote.RetrofitClient
import com.sesac.speechapp.data.remote.dto.ApiResponse
import com.sesac.speechapp.data.remote.dto.session.FinishData
import com.sesac.speechapp.data.remote.dto.session.HintData
import com.sesac.speechapp.data.remote.dto.session.ListenSubmitRequest
import com.sesac.speechapp.data.remote.dto.session.SessionCreateData
import com.sesac.speechapp.data.remote.dto.session.TalkData
import com.sesac.speechapp.data.remote.dto.session.VoiceSubmitData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import retrofit2.Response
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

/**
 * P3-26 세션 플로우 Repository — "오늘의 학습" 전체 API.
 *
 * 실계약 (2026-09-03, ~/app SessionFlowController):
 *  - userId는 모든 엔드포인트에 쿼리파라미터로 전달 (TokenManager에서 조회)
 *  - LISTEN selected: Int 1-based
 *  - 음성 제출: multipart "file" part (m4a, audio/mp4)
 */
class SessionFlowRepository(context: Context) {

    private val apiService = RetrofitClient.apiService
    private val tokenManager = TokenManager(context.applicationContext)

    private fun userId(): Long = tokenManager.getUserId().takeIf { it != -1L } ?: 0L

    /** 응답 래퍼 공통 처리: HTTP 실패/success=false → Exception(한국어 메시지) */
    private fun <T> unwrap(response: Response<ApiResponse<T>>): T {
        val body = response.body()
            ?: throw Exception("서버 응답이 비었습니다 (${response.code()})")
        if (!body.success || body.data == null) {
            throw SessionFlowException(
                body.error?.code ?: "E0000",
                body.error?.message ?: "요청 처리에 실패했습니다 (${response.code()})"
            )
        }
        return body.data
    }

    /** 4.1 세션 생성 (스텁 2~3초) */
    suspend fun createSession(): SessionCreateData = withContext(Dispatchers.IO) {
        unwrap(RetrofitClient.apiService.createSession(userId()))
    }

    /** 4.3 LISTEN 제출 (즉시 채점) */
    suspend fun submitListen(sessionId: Long, turnId: Long, selected: Int) =
        withContext(Dispatchers.IO) {
            unwrap(
                RetrofitClient.apiService.submitListen(
                    sessionId, turnId, ListenSubmitRequest(selected)
                )
            )
        }

    /** 4.3 음성 제출 공통 — 파일 multipart + userId 쿼리 */
    private suspend fun submitVoice(
        sessionId: Long,
        turnId: Long,
        file: File,
        kind: VoiceKind
    ): VoiceSubmitData = withContext(Dispatchers.IO) {
        val part = MultipartBody.Part.createFormData(
            "file", file.name,
            file.asRequestBody("audio/mp4".toMediaTypeOrNull())
        )
        val response = when (kind) {
            VoiceKind.NAMING -> RetrofitClient.apiService.submitNaming(sessionId, turnId, userId(), part)
            VoiceKind.SHADOWING -> RetrofitClient.apiService.submitShadowing(sessionId, turnId, userId(), part)
            VoiceKind.SELF_TALK -> RetrofitClient.apiService.submitSelfTalk(sessionId, turnId, userId(), part)
        }
        unwrap(response)
    }

    suspend fun submitNaming(sessionId: Long, turnId: Long, file: File) =
        submitVoice(sessionId, turnId, file, VoiceKind.NAMING)

    suspend fun submitShadowing(sessionId: Long, turnId: Long, file: File) =
        submitVoice(sessionId, turnId, file, VoiceKind.SHADOWING)

    suspend fun submitSelfTalk(sessionId: Long, turnId: Long, file: File) =
        submitVoice(sessionId, turnId, file, VoiceKind.SELF_TALK)

    /** 4.4 NAMING 힌트 */
    suspend fun requestHint(sessionId: Long, turnId: Long): HintData = withContext(Dispatchers.IO) {
        unwrap(RetrofitClient.apiService.requestHint(sessionId, turnId))
    }

    /**
     * 4.5 이야기 턴 — file=null이면 첫 호출(AI 첫 대사): 일반 POST로 전송.
     * (Retrofit @Part nullable → 빈 multipart body → 서버 400 방지)
     */
    suspend fun talk(sessionId: Long, file: File?): TalkData = withContext(Dispatchers.IO) {
        val response = file?.takeIf { it.exists() }?.let {
            val part = MultipartBody.Part.createFormData(
                "file", it.name,
                it.asRequestBody("audio/mp4".toMediaTypeOrNull())
            )
            RetrofitClient.apiService.talk(sessionId, userId(), part)
        } ?: RetrofitClient.apiService.talkFirst(sessionId, userId())
        unwrap(response)
    }

    /** 4.6 세션 종료 + 리포트 (스텁 2~3초 동기 대기) */
    suspend fun finishSession(sessionId: Long): FinishData = withContext(Dispatchers.IO) {
        unwrap(RetrofitClient.apiService.finishSession(sessionId, userId()))
    }

    private enum class VoiceKind { NAMING, SHADOWING, SELF_TALK }
}

/** 백엔드 error.code를 보존하는 예외 — 하드캡(E0401) 판별 등에 사용 */
class SessionFlowException(val code: String, message: String) : Exception(message)