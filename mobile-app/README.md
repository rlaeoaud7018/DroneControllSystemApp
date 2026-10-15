# mobile-app

스마트폰으로 로그인하고, 구역/캠 목록을 조회하고, 캠을 가동/정지하고, 실시간 분석 영상을 보고, 이벤트를 처리하는 Android 앱.

드론 GPS 추적이 아니라 backend-api의 회원·구역·캠·통계·이벤트 API를 그대로 따라가는 모바일 버전 — web-dashboard와 같은 데이터를 보여준다.

## 확정된 스택 (실제 빌드 + 에뮬레이터 실행까지 검증 완료)

- Android, Java (Kotlin 아님), Gradle 9.7.1 + AGP 9.4.1, compileSdk/targetSdk 37, minSdk 24
- 네트워킹: `HttpURLConnection` 직접 사용 (Retrofit/OkHttp 같은 추가 라이브러리 없음 — 호출하는 API가 몇 개 안 되고, Android의 HttpURLConnection은 PATCH도 지원해서 충분함)
- **세션 쿠키**: `CookieHandler.setDefault(new CookieManager(...))`로 JSESSIONID를 요청 간에 자동 유지 (로그인 후 `/me`, `/zones` 등 후속 요청에 세션이 그대로 실린다 — 실제 기기 테스트로 확인)
- JSON: `org.json` (Android 내장, 별도 라이브러리 불필요)
- 라이브 영상: `WebView`에 `<img src="...">` 한 줄짜리 HTML을 `loadDataWithBaseURL`로 로드 — analysis-server의 MJPEG 스트림을 Chromium 엔진이 그대로 디코딩해서 보여줌
- 지도: `com.google.android.gms:play-services-maps` (Google Maps SDK for Android). web-dashboard와 별도의 API 키 필요 — 패키지명(`com.dronecontrol.mobile`) + 디버그 키스토어 SHA-1 지문으로 제한한 Android 전용 키를 `AndroidManifest.xml`의 `com.google.android.geo.API_KEY` 메타데이터에 넣는다. **키는 git에 올리지 않으므로** `AndroidManifest.xml`의 `YOUR_GOOGLE_MAPS_API_KEY`를 본인이 발급받은 키로 직접 바꿔서 로컬에서만 채워 넣을 것.

## 화면

- **LoginActivity**: 로그인/회원가입 탭 전환. 가입 성공 시 같은 자격증명으로 자동 로그인 시도
- **PendingApprovalActivity**: 로그인했지만 역할이 PRE_USER(미승인)이면 이 화면으로 — 로그아웃만 가능
- **MainActivity**: 구역 목록(`GET /api/v1/zones?dongId=1`) + 구역별 대표 캠 상태, pull-to-refresh, 상단에 "지도"/"이벤트"/"로그아웃" 버튼. `onResume()`마다 `GET /api/v1/users/me`로 세션 확인 → 401이면 LoginActivity로, PRE_USER면 PendingApprovalActivity로 이동
- **ZoneDetailActivity**: 현재 인원/밀집도/체류시간(5초마다 `GET /api/v1/zones/{id}/stats` 폴링), 가동/정지 버튼(`PATCH /api/v1/cameras/{id}/state`), RUNNING이면 WebView로 실시간 영상
- **EventListActivity**: `GET /api/v1/events` 목록(RecyclerView). 관리자(ADMIN/SUPER_ADMIN)로 로그인했으면 메모 입력 + "확인"/"조치완료" 버튼이 보이고 `PATCH /api/v1/events/{id}`로 처리, 일반 회원은 조회만
- **MapActivity**: Google Map 위에 구역별 마커 표시(`zones.geometry`의 lat/lng 파싱). 마커 제목=구역명, 스니펫=`GET /api/v1/zones/{id}/stats`로 가져온 최근 10분 평균 인원수. 마커를 누르면 해당 구역의 ZoneDetailActivity로 이동 (web-dashboard의 지도 화면과 같은 데이터)

로그인 → 구역목록 → 지도(마커 클릭→구역상세) → 이벤트 조회 → 조치완료 처리 → 로그아웃까지 실제 에뮬레이터에서 전부 확인 완료.

## 실행 방법

1. Android Studio로 `mobile-app` 폴더 열기 (또는 `gradle assembleDebug`로 커맨드라인 빌드)
2. 에뮬레이터 또는 실기기 연결
3. **`adb reverse tcp:8080 tcp:8080` 와 `adb reverse tcp:5050 tcp:5050` 를 반드시 먼저 실행** (아래 "에뮬레이터 네트워킹" 참고)
4. backend-api, analysis-server를 PC에서 실행 중인 상태로 앱 실행

## 에뮬레이터 네트워킹 — 10.0.2.2 대신 adb reverse를 쓰는 이유

처음엔 Android 에뮬레이터 표준 규칙대로 `10.0.2.2`(에뮬레이터에서 PC의 localhost를 가리키는 주소)를 썼는데,
실제로 테스트해보니 `SocketTimeoutException`이 발생했다. 원인은 이 PC의 활성 네트워크 프로필이 "공용(Public)"으로 잡혀 있어서,
에뮬레이터의 가상 NAT를 통해 들어오는 연결을 Windows 방화벽이 인바운드 트래픽으로 보고 막기 때문.

**방화벽 규칙을 직접 바꾸는 대신** (보안 설정 변경은 사용자가 직접 판단할 부분이라 자동으로 하지 않음), `adb reverse`로 우회했다:

```bash
adb reverse tcp:8080 tcp:8080
adb reverse tcp:5050 tcp:5050
```

이러면 에뮬레이터 안에서 `localhost:8080`, `localhost:5050`으로 접속한 게 adb 터널을 통해 PC의 같은 포트로 바로 연결된다 (방화벽 우회, 코드도 `ApiClient.BACKEND_BASE = "http://localhost:8080/backend-api"`로 더 단순해짐).

**주의**: `adb reverse`는 세션이 끊기면(에뮬레이터 재시작 등) 풀리므로, 에뮬레이터를 새로 켤 때마다 다시 실행해야 한다. 실기기로 USB 연결해서 테스트할 때도 똑같이 걸면 된다.

## 실제로 찾은 버그 / 이슈

- 10.0.2.2 방화벽 차단 — `adb reverse` 방식으로 해결
- `MainActivity.onCreate()`에서 `loadZones()`를 직접 호출하고 `onResume()`에서도 호출해서, 앱을 처음 열 때 구역 목록 API가 중복 호출되던 것 — `onCreate()` 쪽 호출 제거
- 로그인 연동 초기에는 `CookieHandler` 설정이 없어서 로그인 응답의 세션 쿠키가 다음 요청에 전달되지 않았음 — `ApiClient` 정적 초기화 블록에 `CookieManager` 등록해서 해결 (web-dashboard는 브라우저가 쿠키를 자동 관리해서 이 문제가 없었는데, 네이티브 HttpURLConnection은 직접 설정해야 한다는 걸 여기서 확인함)

## 아직 안 한 것

- 여러 구역/카메라를 동시에 보는 화면
- 이벤트 푸시 알림 (지금은 직접 "이벤트" 화면을 열어야 확인 가능, 자동 알림 없음)
- 회원정보 수정/탈퇴 화면 (API는 있는데 UI는 아직 없음)
