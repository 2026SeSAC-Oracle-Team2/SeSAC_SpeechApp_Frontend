# SeSAC 발화 연습

실어증 환자 대상 AI 발화 연습 앱 (Android)

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
4. 패키지명: `com.sesac.speech`
5. SHA-1 지문 등록 (아래 명령어로 확인)
   ```bash
   # Windows (PowerShell)
   ./gradlew signingReport
   
   # Linux/Mac
   ./gradlew signingReport
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
| `main` | 안정 버전 |
| `feature/*` | 기능 개발 |

---

## 📁 프로젝트 구조

```
app/src/main/java/com/sesac/speech/
├── MainActivity.kt
├── data/
│   ├── model/          # 데이터 모델
│   ├── remote/
│   │   ├── api/        # Retrofit 인터페이스
│   │   ├── dto/        # API 요청/응답 DTO
│   │   └── websocket/  # WebSocket Manager
│   └── repository/     # 데이터 레이어
└── ui/
    ├── splash/
    ├── login/
    ├── chat/
    ├── learn/
    ├── history/
    ├── dashboard/
    └── profile/
```

---

## 🔑 환경변수 / Secrets

| 파일 | 설명 | Git 관리 |
|------|------|----------|
| `app/google-services.json` | Firebase 설정 | ❌ `.gitignore` |
| `local.properties` | 로컬 SDK 경로 | ❌ `.gitignore` |

---

## 📄 라이선스

SeSAC Team Project
