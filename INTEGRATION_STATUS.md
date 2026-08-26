# Android ↔ Backend 통합 디버깅 현황

> 작성일: 2026-08-25  
> 브랜치: `feature/google-signin`  
> 상태: 클라이언트 Firebase 인증 성공 → 서버 로그인 API 호출 실패

---

## 1. 프로젝트 개요

| 항목 | 내용 |
|------|------|
| 프로젝트명 | SeSAC 발화 연습 (Aphasia Speech Practice) |
| Android 패키지 | `com.sesac.speechapp` |
| compileSdk / minSdk | 34 / 26 |
| 언어 | Kotlin 1.9.20 |
| 아키텍처 | MVVM + Retrofit + Firebase Auth |

---

## 2. Android 클라이언트 현재 상태

### ✅ 완료된 작업

- **전체 UI 흐름** 구현 완료 (Splash → Login → Main → Chat/Profile/History/Dashboard/Setting)
- **Firebase Google Sign-In** 통합 완료 (`google-services.json` 재설정)
- **Retrofit + OkHttp** 설정 완료
- **JWT 토큰 저장** (`SharedPreferences` via `TokenManager`)
- **CLEARTEXT HTTP 허용** (개발용 VM 연결, `usesCleartextTraffic="true"`)
- **패키지명 통일** (`com.sesac.speech` → `com.sesac.speechapp`)

### 🔄 진행 중 / 막힌 지점

| 단계 | 상태 |
|------|------|
| Google Sign-In → Firebase ID Token 획득 | ✅ 성공 |
| `POST /api/v1/auth/firebase` 전송 | ✅ 성공 (HTTP 통신 됨) |
| **서버 응답 처리** | **❌ "server login failed"** |

---

## 3. 서버 통신 상세

| 항목 | 값 |
|------|-----|
| **Base URL** | `http://132.145.95.251:8080/` (`local.properties`로 분리) |
| **엔드포인트** | `POST /api/v1/auth/firebase` |
| **요청 헤더** | `Content-Type: application/json` |
| **요청 바디** | `{"id_token": "<Firebase_ID_Token>"}` |
| **호출 위치** | `AuthRepository.serverLogin(idToken: String)` |
| **에러 발생 지점** | `if (!response.isSuccessful)` → `Exception("Server login failed: ${response.code()}")` |

---

## 4. 해결된 환경 이슈 (이력)

| 이슈 | 해결 방법 |
|------|----------|
| `google-services.json` 누락 | Firebase Console 재설정 → 파일 재배치 (`app/` 아래) |
| 패키지명 불일치 (`com.sesac.speech`) | 전체 코드/디렉토리 `com.sesac.speechapp`로 rename |
| SHA-1 지문 확인 불가 | Android Studio 내장 JDK `keytool` 직접 사용 |
| 한국어 Windows `keytool -v` 버그 | `JAVA_TOOL_OPTIONS=-Duser.language=en`으로 회피 |
| CLEARTEXT(HTTP) 차단 | `AndroidManifest.xml`에 `usesCleartextTraffic="true"` 추가 |
| `gradlew` 없음 | 빈 프로젝트에서 복사 / `keytool` 직접 사용 |
| `BuildConfig` import 누락 | `import com.sesac.speechapp.BuildConfig` 추가 |
| `ApiResponse` 중복 선언 | `VoiceUploadResponse.kt`에서 중복 클래스 제거 |

---

## 5. 서버(Spring Boot) 측 확인 필요 사항

다음 항목들을 Spring Boot 세션에서 점검해야 합니다.

- [ ] **Firebase Admin SDK 초기화** 여부 (`FirebaseApp.initializeApp()`)
- [ ] **`verifyIdToken(idToken)`** 로직 정상 작동 여부
- [ ] 최초 로그인 시 **신규 사용자 자동 생성** 처리 (`OnConflict.CREATE` 등)
- [ ] HTTP 200 응답 시 **`ApiResponse.success == true`** 반환 여부
- [ ] 서버 로그에서 `AuthRepository`가 전송한 ID Token 값 수동 검증 가능 여부
- [ ] 응답 코드: **400** (토큰 검증 실패) / **404** (엔드포인트 없음) / **500** (서버 내부 에러) 중 어떤 것인지

---

## 6. API 명세 참고

- **파일 위치**: `/workspace/SeSAC_TeamProject/baseworks/Project/SeSAC_SpeechApp_Backend/docs/API_SPEC.md`
- **관련 섹션**: `2. 인증 흐름`, `2.1 /api/v1/auth/firebase`
- **요청 DTO**: `{"id_token": "string"}`
- **성공 응답**: `{"success": true, "data": {"access_token": "...", "refresh_token": "...", "user": {...}}, "error": null}`

---

## 7. 다음 단계 (Action Items)

| 순서 | 작업 | 담당 |
|------|------|------|
| 1 | `AuthRepository`에 Request Body 로그 추가 (`Log.d`) | Android (진행 중) |
| 2 | Logcat에서 실제 전송된 `id_token` 확인 | Android |
| 3 | 동일 토큰으로 서버 측 `verifyIdToken` 수동 테스트 | Backend |
| 4 | 서버 응답 코드(HTTP status) 및 에러 메시지 교차 검증 | 통합 |
| 5 | 응답 성공 시 `ApiResponse` 구조가 명세와 일치하는지 확인 | Backend |

---

## 8. 참고 코드 위치

```
app/src/main/java/com/sesac/speechapp/
├── data/
│   ├── repository/AuthRepository.kt      # 서버 로그인 호출 지점
│   ├── remote/RetrofitClient.kt          # BASE_URL 설정
│   ├── remote/api/ApiService.kt          # 엔드포인트 정의
│   └── local/TokenManager.kt             # 토큰 저장
├── ui/login/LoginActivity.kt             # Google Sign-In 런처
└── ui/splash/SplashActivity.kt           # 자동 로그인 체크
```

---

## 9. 중요 참고사항

- **서버 URL**: `local.properties`에 `SERVER_BASE_URL=http://132.145.95.251:8080/`로 설정되어 있으며 Git 추적 대상이 아님
- **CLEARTEXT**: 현재 `AndroidManifest.xml`에서 `usesCleartextTraffic="true"`로 설정 중. 프로덕션 배포 전 반드시 **HTTPS + SSL** 적용 후 제거해야 함
- **Firebase**: `google-services.json`은 Git에 포함되지 않으며, Firebase Console의 SHA-1 등록 후 재다운로드가 필요했음
