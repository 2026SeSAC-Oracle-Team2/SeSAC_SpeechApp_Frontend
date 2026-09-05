package com.sesac.speechapp.data.remote.api

import com.sesac.speechapp.data.remote.dto.ApiResponse
import com.sesac.speechapp.data.remote.dto.FirebaseAuthRequest
import com.sesac.speechapp.data.remote.dto.LoginResponse
import com.sesac.speechapp.data.remote.dto.TokenRefreshRequest
import com.sesac.speechapp.data.remote.dto.TokenRefreshResponse
import com.sesac.speechapp.data.remote.dto.UpdateProfileRequest
import com.sesac.speechapp.data.remote.dto.SurveyRequest
import com.sesac.speechapp.data.remote.dto.SurveyResponse
import com.sesac.speechapp.data.remote.dto.TagsResponse
import com.sesac.speechapp.data.remote.dto.UserDto
import com.sesac.speechapp.data.remote.dto.VoiceUploadResponse
import com.sesac.speechapp.data.remote.dto.session.FinishData
import com.sesac.speechapp.data.remote.dto.session.HintData
import com.sesac.speechapp.data.remote.dto.session.ListenSubmitData
import com.sesac.speechapp.data.remote.dto.session.ListenSubmitRequest
import com.sesac.speechapp.data.remote.dto.session.SessionCreateData
import com.sesac.speechapp.data.remote.dto.session.SessionReportData
import com.sesac.speechapp.data.remote.dto.session.SessionScoresData
import com.sesac.speechapp.data.remote.dto.session.StatsData
import com.sesac.speechapp.data.remote.dto.session.SessionHistoryData
import com.sesac.speechapp.data.remote.dto.session.TalkData
import com.sesac.speechapp.data.remote.dto.session.VoiceSubmitData
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    /**
     * POST /api/v1/auth/firebase
     * Firebase ID Token → 서버 JWT
     */
    @POST("api/v1/auth/firebase")
    suspend fun firebaseAuth(
        @Body request: FirebaseAuthRequest
    ): Response<ApiResponse<LoginResponse>>

    /**
     * POST /api/v1/auth/refresh
     * 응답 data = TokenRefreshResponse (accessToken camelCase — 실계약 2026-09-03)
     */
    @POST("api/v1/auth/refresh")
    suspend fun refreshToken(
        @Body request: TokenRefreshRequest
    ): Response<ApiResponse<TokenRefreshResponse>>

    /**
     * POST /api/v1/auth/logout
     */
    @POST("api/v1/auth/logout")
    suspend fun logout(): Response<ApiResponse<Void?>>

    /**
     * GET /api/v1/users/me
     */
    @GET("api/v1/users/me")
    suspend fun getMyProfile(): Response<ApiResponse<UserDto>>

    /**
     * PATCH /api/v1/users/me — 닉네임 등 프로필 수정
     */
    @PATCH("api/v1/users/me")
    suspend fun updateProfile(
        @Body request: UpdateProfileRequest
    ): Response<ApiResponse<UserDto>>

    /**
     * POST /api/v1/users/me/profile-image — 프로필 사진 업로드 (multipart)
     */
    @Multipart
    @POST("api/v1/users/me/profile-image")
    suspend fun uploadProfileImage(
        @Part file: MultipartBody.Part
    ): Response<ApiResponse<UserDto>>

    /**
     * DELETE /api/v1/users/me — 회원탈퇴
     */
    @DELETE("api/v1/users/me")
    suspend fun withdraw(): Response<ApiResponse<Void?>>

    /**
     * GET /api/v1/users/me/tags — 태그 마스터 15종 (D-6 신설, 05a v1.6 §2)
     */
    @GET("api/v1/users/me/tags")
    suspend fun getTags(): Response<ApiResponse<TagsResponse>>

    /**
     * POST /api/v1/users/me/survey — 가입 설문 접수 (D-6 신설, 05a v1.6 §2 + 06 v1.7 §5.2)
     * 산출 주체 = 서버 — 클라는 answers 원문만 전송.
     */
    @POST("api/v1/users/me/survey")
    suspend fun submitSurvey(
        @Body request: SurveyRequest
    ): Response<ApiResponse<SurveyResponse>>

    /**
     * GET /api/v1/users/me/scores — 대표점수 (D-7 3.1, 05a v1.6 §8.1 — JWT 필수)
     * null = 캐시 미산출 — 클라 폴백: 방사형 0 표시 + 안내문
     */
    @GET("api/v1/users/me/scores")
    suspend fun getMyScores(): Response<ApiResponse<SessionScoresData>>

    /**
     * GET /api/v1/users/me/stats — 홈 통계 (D-8②b, 05a §8.4 — JWT 필수)
     * streak/avgScore/deltaScore — delta null이면 증감 표시 숨김
     */
    @GET("api/v1/users/me/stats")
    suspend fun getMyStats(): Response<ApiResponse<StatsData>>

    /**
     * GET /api/v1/users/me/sessions/history — 지난 학습 카드 리스트 (D-7 3.1, 05a v1.6 §8.2 — JWT 필수)
     * 조회 조건: STATUS != COMPLETED_NO_TALK AND AQ IS NOT NULL — createdAt ISO 수신 (클라 포맷 책임)
     */
    @GET("api/v1/users/me/sessions/history")
    suspend fun getSessionHistory(): Response<ApiResponse<SessionHistoryData>>

    /**
     * GET /api/v1/sessions/{sessionId}/report — 세부 보고서 (D-7 3.1, 05a v1.6 §8.3)
     * permitAll 경로 — userId 쿼리파라미터 소유 검증 (타 유저 E0400, 중단 세션 E0404).
     * 응답 수신 시 백엔드가 REPORT_VIEWED_AT 기록 (null일 때만 — 최초 1회)
     */
    @GET("api/v1/sessions/{sessionId}/report")
    suspend fun getSessionReport(
        @Path("sessionId") sessionId: Long,
        @Query("userId") userId: Long
    ): Response<ApiResponse<SessionReportData>>

    /**
     * POST /api/v1/voice/upload
     */
    @Multipart
    @POST("api/v1/voice/upload")
    suspend fun uploadVoice(
        @Part file: MultipartBody.Part,
        @Part("userId") userId: RequestBody,
        @Part("contentType") contentType: RequestBody,
        @Part("sessionId") sessionId: RequestBody? = null
    ): Response<ApiResponse<VoiceUploadResponse>>

    // ═══════════════════════════════════════════════════════════
    // 세션 플로우 (P3-26) — 실계약: userId는 쿼리파라미터
    // ═══════════════════════════════════════════════════════════

    /**
     * 4.1 세션 생성 — 오늘의 학습 (D-7 1.4: /today 신설 — 테마 서버 랜덤, 05a v1.6 §3.1)
     * 스텁 응답 2~3초 — 로딩 화면 필수
     */
    @POST("api/v1/sessions/today")
    suspend fun createSessionToday(
        @Query("userId") userId: Long
    ): Response<ApiResponse<SessionCreateData>>

    /**
     * 4.1 세션 생성 — 테마별 학습 (D-7 1.4: /theme 신설 — thema 고정, 05a v1.6 §3.1)
     * thema: TEST|HOSPITAL|CAFE (대소문자 무관 — 소문자 허용). 이외 E0400
     */
    @POST("api/v1/sessions/theme")
    suspend fun createSessionTheme(
        @Query("userId") userId: Long,
        @Query("thema") thema: String
    ): Response<ApiResponse<SessionCreateData>>

    /**
     * 4.1 세션 생성 — 구 /v2 (하위호환 유지·미사용 — D-5 이전 데모 계약, 05a v1.6 §3.1)
     */
    @POST("api/v1/sessions/v2")
    suspend fun createSession(
        @Query("userId") userId: Long
    ): Response<ApiResponse<SessionCreateData>>

    /**
     * 4.3 LISTEN 제출 — 백엔드 자체 채점 (즉시)
     * 실계약: selected는 Int (선택지 order 1-based)
     */
    @POST("api/v1/sessions/{sessionId}/turns/{turnId}/listen")
    suspend fun submitListen(
        @Path("sessionId") sessionId: Long,
        @Path("turnId") turnId: Long,
        @Body request: ListenSubmitRequest
    ): Response<ApiResponse<ListenSubmitData>>

    /**
     * 4.3 NAMING 제출 (음성 multipart + userId 쿼리파라미터 필수)
     */
    @Multipart
    @POST("api/v1/sessions/{sessionId}/turns/{turnId}/naming")
    suspend fun submitNaming(
        @Path("sessionId") sessionId: Long,
        @Path("turnId") turnId: Long,
        @Query("userId") userId: Long,
        @Part file: MultipartBody.Part
    ): Response<ApiResponse<VoiceSubmitData>>

    /**
     * 4.3 SHADOWING 제출 (음성 multipart)
     */
    @Multipart
    @POST("api/v1/sessions/{sessionId}/turns/{turnId}/shadowing")
    suspend fun submitShadowing(
        @Path("sessionId") sessionId: Long,
        @Path("turnId") turnId: Long,
        @Query("userId") userId: Long,
        @Part file: MultipartBody.Part
    ): Response<ApiResponse<VoiceSubmitData>>

    /**
     * 4.3 SELF_TALK 제출 (음성 multipart)
     */
    @Multipart
    @POST("api/v1/sessions/{sessionId}/turns/{turnId}/selftalk")
    suspend fun submitSelfTalk(
        @Path("sessionId") sessionId: Long,
        @Path("turnId") turnId: Long,
        @Query("userId") userId: Long,
        @Part file: MultipartBody.Part
    ): Response<ApiResponse<VoiceSubmitData>>

    /**
     * 4.4 NAMING 힌트 — 의미단서(1) → 조음단서(2) 순서, 최대 2개
     */
    @POST("api/v1/sessions/{sessionId}/turns/{turnId}/hint")
    suspend fun requestHint(
        @Path("sessionId") sessionId: Long,
        @Path("turnId") turnId: Long
    ): Response<ApiResponse<HintData>>

    /**
     * 4.5 이야기 턴 — 음성 있는 제출 (2턴째부터)
     * ⚠️ 첫 호출(file 없음)은 talkFirst 사용 — @Part nullable은 "빈 multipart body"로
     * 전송돼 서버가 'Multipart body must have at least one part'로 거부한다 (실측).
     * 9번째 제출(talk-turn-limit=8 초과)은 E0401 에러 응답
     */
    @Multipart
    @POST("api/v1/sessions/{sessionId}/turns/talk")
    suspend fun talk(
        @Path("sessionId") sessionId: Long,
        @Query("userId") userId: Long,
        @Part file: MultipartBody.Part
    ): Response<ApiResponse<TalkData>>

    /**
     * 4.5 이야기 턴 — 첫 호출 (AI 첫 대사). file 파트 없이 일반 POST.
     * 백엔드 file required=false라 multipart 없이도 정상 처리됨 (curl 실측 200).
     */
    @POST("api/v1/sessions/{sessionId}/turns/talk")
    suspend fun talkFirst(
        @Path("sessionId") sessionId: Long,
        @Query("userId") userId: Long
    ): Response<ApiResponse<TalkData>>

    /**
     * 4.6 세션 종료 + 간이 보고서 — 동기 응답 (스텁 즉시)
     * v1.6 2단계 계약: talk/total은 항상 null — 세부 보고서는 GET /sessions/{id}/report (§8.3)
     */
    @POST("api/v1/sessions/{sessionId}/finish")
    suspend fun finishSession(
        @Path("sessionId") sessionId: Long,
        @Query("userId") userId: Long
    ): Response<ApiResponse<FinishData>>
}