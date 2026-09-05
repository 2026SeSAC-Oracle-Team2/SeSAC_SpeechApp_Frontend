package com.sesac.speechapp.data.repository

import android.content.Context
import com.sesac.speechapp.data.local.TokenManager
import com.sesac.speechapp.data.remote.RetrofitClient
import com.sesac.speechapp.data.remote.dto.ApiResponse
import com.sesac.speechapp.data.remote.dto.session.FinishData
import com.sesac.speechapp.data.remote.dto.session.HintData
import com.sesac.speechapp.data.remote.dto.session.ListenSubmitRequest
import com.sesac.speechapp.data.remote.dto.session.SessionCreateData
import com.sesac.speechapp.data.remote.dto.session.SessionHistoryData
import com.sesac.speechapp.data.remote.dto.session.SessionReportData
import com.sesac.speechapp.data.remote.dto.session.SessionScoresData
import com.sesac.speechapp.data.remote.dto.session.StatsData
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
 * P3-26 세션 플로우 Repository — 세션 플로우 전체 API + D-7 대시보드/세부 보고서 3종.
 *
 * 실계약 (2026-09-03, ~/app SessionFlowController; D-7 2026-09-06 갱신):
 *  - userId는 모든 엔드포인트에 쿼리파라미터로 전달 (TokenManager에서 조회)
 *  - 세션 생성 2종 분기: POST /sessions/today(테마 랜덤)·/theme?thema=(고정) — /v2는 하위호환 유지·미사용
 *  - LISTEN selected: Int 1-based
 *  - 음성 제출: multipart "file" part (m4a, audio/mp4)
 *  - 대시보드: /users/me/scores·/users/me/sessions/history — JWT 필수 (TokenAuthenticator 경로)
 *  - 세부 보고서: GET /sessions/{id}/report?userId= — permitAll+userId 쿼리 (JWT 불필요, 붙여도 무해)
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

    /** 4.1 세션 생성 — 오늘의 학습 (D-7 1.4: /today — 테마 서버 랜덤) */
    suspend fun createSessionToday(): SessionCreateData = withContext(Dispatchers.IO) {
        unwrap(RetrofitClient.apiService.createSessionToday(userId()))
    }

    /** 4.1 세션 생성 — 테마별 학습 (D-7 1.4: /theme?thema= — 대소문자 무관) */
    suspend fun createSessionTheme(thema: String): SessionCreateData = withContext(Dispatchers.IO) {
        unwrap(RetrofitClient.apiService.createSessionTheme(userId(), thema))
    }

    /** 4.1 세션 생성 — 구 /v2 (하위호환 유지·미사용 — 기존 메서드 시그니처 파손 금지) */
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

    /** 4.6 세션 종료 + 간이 보고서 (v1.6: talk/total 항상 null — 2단계 계약) */
    suspend fun finishSession(sessionId: Long): FinishData = withContext(Dispatchers.IO) {
        unwrap(RetrofitClient.apiService.finishSession(sessionId, userId()))
    }

    // ─── D-7 3.1 대시보드 / 세부 보고서 3종 ───────────────────────

    /**
     * 대표점수 (05a §8.1) — JWT 필수 (403 시 TokenAuthenticator 무음 refresh 경로)
     * null = 캐시 미산출 — 클라 폴백 처리
     */
    suspend fun getMyScores(): SessionScoresData = withContext(Dispatchers.IO) {
        unwrap(RetrofitClient.apiService.getMyScores())
    }

    /**
     * 홈 통계 (05a §8.4) — JWT 필수 (403 시 TokenAuthenticator 무음 refresh 경로).
     * 조회 실패 시 호출부에서 카드 "-" 폴백 (화면 깨짐 방지).
     */
    suspend fun getMyStats(): StatsData = withContext(Dispatchers.IO) {
        unwrap(RetrofitClient.apiService.getMyStats())
    }

    /**
     * 지난 학습 카드 리스트 (05a §8.2) — JWT 필수.
     * 서버 조건: STATUS != COMPLETED_NO_TALK AND AQ IS NOT NULL
     */
    suspend fun getSessionHistory(): SessionHistoryData = withContext(Dispatchers.IO) {
        unwrap(RetrofitClient.apiService.getSessionHistory())
    }

    /**
     * 세부 보고서 (05a §8.3) — permitAll+userId 쿼리 (JWT 헤더 불필요, 붙여도 무해).
     * 중단 세션 E0404 / 타 유저 E0400 — 응답 수신 시 서버가 REPORT_VIEWED_AT 기록.
     * 상세 보고서 생성 지연 시(스텁 10초) E0404 수신 가능 — 클라는 "준비 중" 안내 후 재시도.
     */
    suspend fun getSessionReport(sessionId: Long): SessionReportData = withContext(Dispatchers.IO) {
        unwrap(RetrofitClient.apiService.getSessionReport(sessionId, userId()))
    }

    private enum class VoiceKind { NAMING, SHADOWING, SELF_TALK }
}

/** 백엔드 error.code를 보존하는 예외 — 하드캡(E0401)·중단 세션(E0404) 판별 등에 사용 */
class SessionFlowException(val code: String, message: String) : Exception(message)