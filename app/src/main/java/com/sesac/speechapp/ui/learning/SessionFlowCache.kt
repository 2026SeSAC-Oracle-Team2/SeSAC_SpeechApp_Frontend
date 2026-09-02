package com.sesac.speechapp.ui.learning

import com.sesac.speechapp.data.remote.dto.session.SessionCreateData

/**
 * P3-26: 세션 플로우 화면 간 대용량 데이터 전달 캐시 (싱글턴).
 *
 * Intent Bundle에 DTO 리스트를 직렬화하지 않기 위한 임시 경유지.
 * 로딩 → 문제풀이 → (이야기) → 결과 화면에서 sessionId만 Intent로 전달하고
 * 문제 목록은 여기서 조회한다. 플로우 종료 시 clear().
 */
object SessionFlowCache {

    @Volatile
    private var sessionData: SessionCreateData? = null

    fun set(data: SessionCreateData) {
        sessionData = data
    }

    fun get(): SessionCreateData? = sessionData

    fun clear() {
        sessionData = null
    }
}