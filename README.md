# 덕담 (Duck談) — Android 클라이언트 (개발 히스토리 레포)

> LLM 기반 발화·대화 훈련 보조 서비스 **덕담**의 Android 클라이언트
> 최종 배포 버전은 [SeSAC_SpeechApp_Client_Android](https://github.com/2026SeSAC-Oracle-Team2/SeSAC_SpeechApp_Client_Android) 레포로 통합되었습니다.

SeSAC TEAM 545 — 김윤혁 문현아 서지원 손승운 우연희

---

## 🚀 시작하기

### 요구사항

- Android Studio Hedgehog (2023.1.1) 이상
- JDK 17
- Android SDK 34 (compileSdk)
- minSdk: 26 (Android 8.0)

### Firebase 설정

이 프로젝트는 **Firebase Authentication (Google Sign-In)**을 사용합니다.
`google-services.json` 파일이 **Git에 포함되어 있지 않으므로** 직접 추가해야 합니다.

#### 1. Firebase Console에서 프로젝트 설정

1. [Firebase Console](https://console.firebase.google.com) 접속
2. 프로젝트 생성 또는 기존 프로젝트 선택
3. **프로젝트 설정 → 일반**에서 **Android 앱 추가**
4. 패키지명: `com.sesac.speechapp`
5. SHA-1 지문 등록 (아래 명령어로 확인)

#### SHA-1 지문 확인 방법

Android Studio에 내장된 JDK의 `keytool`을 사용합니다.

**Windows (CMD):**
```cmd
set JAVA_TOOL_OPTIONS=-Duser.language=en -Duser.country=US

cd "C:\Program Files\Android\Android Studio\jbr\bin"

keytool -list -v -keystore "%USERPROFILE%\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android
```

> ⚠️ **한국어 Windows 사용자 참고:** `keytool` 명령어에 `-v` 옵션을 붙이면 한국어 로케일 환경에서 `IllegalFormatConversionException`이 발생할 수 있습니다. 위 명령어의 `set JAVA_TOOL_OPTIONS`로 출력 언어를 영어로 변경하면 문제를 회피할 수 있습니다.

출력 결과에서 `SHA1:` (또는 `Certificate fingerprint (SHA-1):`) 뒤의 값을 복사해서 Firebase Console에 붙여넣으세요.

**Linux/Mac:**
```bash
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
```

6. `google-services.json` 다운로드

#### 2. google-services.json 배치

다운로드한 파일을 아래 경로에 넣으세요:

```
app/
└── google-services.json   ← 여기에 파일을 넣으세요
```

> ⚠️ **절대 GitHub에 올리지 마세요.**  
> `.gitignore`에 `app/google-services.json`이 이미 등록되어 있습니다.

#### 3. Google Sign-In 활성화

Firebase Console → **Authentication → Sign-in method** → **Google** → **사용 설정** → 저장

#### 4. Gradle Sync

Android Studio에서 `File → Sync Project with Gradle Files` 실행

---

### 백엔드 서버 URL 설정

API 서버 주소는 **소스코드에 하드코딩하지 않고** `local.properties`에서 관리합니다. 이 파일은 Git에 포함되지 않으므로 IP가 외부에 노출되지 않습니다.

#### 1. local.properties 수정

프로젝트 루트의 `local.properties` 파일에 아래 줄을 추가하세요:

```properties
sdk.dir=C:\\Users\\Master\\AppData\\Local\\Android\\Sdk
# 아래 줄을 추가 (VM의 실제 IP 주소로 변경)
SERVER_BASE_URL=http://VM_IP_주소:8080/
```

**예시:**
```properties
SERVER_BASE_URL=http://146.56.42.123:8080/
```

> ⚠️ `local.properties`는 `.gitignore`에 등록되어 있어 **GitHub에 올라가지 않습니다.**

#### 2. BuildConfig 자동 생성

Gradle Sync 시 `BuildConfig.SERVER_BASE_URL`가 자동 생성됩니다.

| 환경 | 설정 방법 |
|------|----------|
| 에뮬레이터 테스트 | `SERVER_BASE_URL=http://10.0.2.2:8080/` (기본값) |
| 실제 휴대폰 테스트 | `SERVER_BASE_URL=http://VM_실제_IP:8080/` |

---

## 🛠 기술 스택

| 영역 | 라이브러리 |
|------|-----------|
| Language | Kotlin 1.9.20 |
| UI | Material Design 3, ViewBinding |
| Architecture | MVVM + LiveData |
| Navigation | Navigation Component |
| Network | Retrofit 2.9 + OkHttp 4.12 |
| Auth | Firebase Auth + Google Sign-In |
| Media | ExoPlayer (Media3) |

---

## 🌿 브랜치 전략

| 브랜치 | 설명 |
|--------|------|
| `main` | 안정 버전 — 프로젝트 종료 기준 단일 브랜치 운영 (2026-09-12) |

---

## 📁 프로젝트 구조

```
app/src/main/java/com/sesac/speechapp/
├── MainActivity.kt / SpeechApplication.kt
├── data/
│   ├── local/          # TokenManager (JWT 저장)
│   ├── model/          # 데이터 모델
│   ├── remote/
│   │   ├── api/        # Retrofit 인터페이스
│   │   ├── dto/        # API 요청/응답 DTO
│   │   └── TokenAuthenticator.kt  # 403 무음 토큰 갱신
│   └── repository/     # 데이터 레이어
└── ui/
    ├── splash/ · login/ · signup/ · survey/   # 진입 플로우
    ├── dashboard/ · learn/ · practice/        # 홈·학습 진입
    ├── learning/                              # 세션 (문항·녹음·AI대화·로딩·가이드)
    ├── detail/ · history/                     # 리포트·기록
    ├── profile/ · record/                     # 프로필·녹음 유틸
    └── chat/
```

---

## 🔑 환경변수 / Secrets

| 파일 | 설명 | Git 관리 |
|------|------|----------|
| `app/google-services.json` | Firebase 설정 | ❌ `.gitignore` |
| `local.properties` | 로컬 SDK 경로 | ❌ `.gitignore` |

---

## 📄 라이선스

SeSAC Team Project — TEAM 545
