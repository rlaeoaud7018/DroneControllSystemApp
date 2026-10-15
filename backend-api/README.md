# backend-api

회원/지역/구역/캠/통계/이벤트/히스토리 REST API. **스택: Spring Boot 3.3 + MyBatis (JPA 미사용) + Maven.**

analysis-server와는 `/internal/v1/*` 로, web-dashboard·mobile-app과는 `/api/v1/*` 로 통신한다.

## 스택 결정 배경

원래는 기존 수업 방식(Servlet + 순수 JDBC + Tomcat, Eclipse)으로 만들어서 region/zone/camera/stats까지 전부 동작 검증까지 끝냈었는데,
"실무처럼 Spring Boot + MyBatis(또는 JPA)로 전부 다시 짜자"는 결정에 따라 Spring Boot + MyBatis로 전체 재작성함.
MyBatis만 쓰고 JPA는 안 씀 — `zone_stats_minute`의 러닝 평균 UPSERT 같은 복잡한 SQL은 JPA보다 MyBatis(직접 SQL)가 훨씬 자연스러움.

- 빌드: **Maven** (`pom.xml`), IntelliJ에서 바로 열면 됨
- Spring Boot 3.3.5, Java 17
- MyBatis: `mybatis-spring-boot-starter`, `@Mapper` 인터페이스에 `@Select/@Insert/@Update` 어노테이션으로 SQL 직접 작성 (XML 매퍼 파일 안 씀 — 프로젝트 규모상 어노테이션 방식이 더 간단)
- `map-underscore-to-camel-case: true` (application.yml) — DB의 snake_case 컬럼이 자동으로 camelCase 필드에 매핑됨
- DB: MySQL, `db_dronecontrol` (root/1234) — `../db/schema.sql` 그대로 사용 (변경 없음)
- JSON 처리: Spring 기본 내장 Jackson (별도 라이브러리 설치 불필요)
- 서버 포트: 8080, context-path `/backend-api` (예: `http://localhost:8080/backend-api/api/v1/regions`)

## 레이어 구조

```
com.dronecontrol.<domain>/
  <Domain>.java          평범한 POJO (getter/setter)
  <Domain>Mapper.java     @Mapper 인터페이스, SQL은 어노테이션으로 직접 작성
  <Domain>Service.java    @Service, 비즈니스 로직 (유효성 검사, 상태전이 등)
  <Domain>Controller.java @RestController, @RequestMapping
```

## 실행 방법

IntelliJ로 `backend-api` 폴더(= `pom.xml` 있는 곳)를 Open → `DroneControlApplication.java` 실행,
또는 터미널에서:
```bash
mvn spring-boot:run
```
MySQL(`db_dronecontrol`)이 로컬에서 돌고 있어야 한다 (Windows 서비스 `MySQL80`).

**Google Maps API 키 설정 필요**: 지도가 보이려면 `src/main/resources/static/index.html`의
`YOUR_GOOGLE_MAPS_API_KEY`를 본인이 발급받은 Maps JavaScript API 키로 바꿔야 한다
(키는 git에 올리지 않으므로 각자 발급해서 로컬에서만 채워 넣을 것 — HTTP 리퍼러를
`localhost`/실제 배포 도메인으로 제한해두는 걸 권장). 키가 없으면 지도 영역에 안내 문구만 뜨고
나머지 기능(카메라/통계/이벤트)은 정상 동작한다.

## 검증된 API (실제 MySQL + Spring Boot로 기동해서 동작 확인 완료)

- **region**: `GET /api/v1/regions`(`?parentId=`), `GET/{id}`, `POST`, `PATCH/{id}`
  - `GET/{id}/flows?from=ISO&to=ISO` — 구역 간 이동 조회(id는 동 레벨)
- **zone**: `GET /api/v1/zones?dongId=`, `GET/{id}`, `POST`, `PATCH/{id}`
  - `PUT/{id}/neighbors`, `GET/{id}/neighbors` — 반대 방향 자동 등록(N↔S, E↔W), 자기 자신 지정 시 400
  - `GET/{id}/stats?from=ISO&to=ISO`
  - `GET/{id}/stats/export?from=ISO&to=ISO` — 엑셀(xlsx) 다운로드
  - `GET/{id}/stats/stream` — SSE 실시간 스트림
  - `GET/{id}/playback?from=ISO&to=ISO` — 녹화 이력 + 통계 타임라인
- **camera**: `GET /api/v1/cameras?zoneId=`, `GET/{id}`, `POST`, `PATCH/{id}`
  - `PATCH/{id}/state` (`{"state":"RUNNING"}`/`"STOPPED"`), 가동 중 입력정보 변경 시 409
- **internal (analysis-server 전용, 외부 비노출)**:
  - `POST /internal/v1/stats` — 초단위 샘플 배치 → 1분 버킷 러닝 평균/합산 집계
  - `PATCH /internal/v1/cameras/{id}/status` — 상태/진행률 갱신

Servlet+JDBC 버전과 완전히 동일한 결과(러닝 평균·합산 수치 일치)로 재검증 완료. analysis-server(Flask)와의 end-to-end 연동(캠 등록→분석시작→1분 통계 누적→라이브 뷰)도 재확인함 — `BACKEND_BASE_URL` 환경변수 없이 analysis-server의 기본값(`http://localhost:8080/backend-api`)과 포트가 그대로 맞음.

**analysis-server 자동 연동 완료**: `PATCH /api/v1/cameras/{id}/state`
- `{"state":"RUNNING"}` → `AnalysisServerClient`가 `POST {analysis-server}/analyzer/v1/cameras` 호출, 성공(202)하면 캠 상태를 PREPARING으로 낙관적 반영 (이후 실제 진행률/RUNNING 전환은 analysis-server가 `/internal/v1/cameras/{id}/status`로 보고)
- `{"state":"STOPPED"}` → `DELETE {analysis-server}/analyzer/v1/cameras/{id}` 호출 후 STOPPED로 반영
- analysis-server가 꺼져 있으면 DB는 건드리지 않고 502 반환 (검증 완료)
- analysis-server 주소는 `application.yml`의 `analysis-server.base-url` (기본 `http://localhost:5050`)

## 회원 / 이벤트 / 히스토리 (신규, 전부 동작 검증 완료)

- **회원 (`com.dronecontrol.user`)**: 세션 기반 인증(Spring Security 없이 `HttpSession` 직접 사용 — 이 정도 규모엔 충분)
  - `POST /api/v1/users` 회원가입 — **첫 번째 가입자는 자동으로 SUPER_ADMIN**, 이후는 PRE_USER(미승인)로 시작
  - `POST /api/v1/auth/login`, `POST /api/v1/auth/logout`
  - `GET /api/v1/users/me` — 로그인 안 했으면 401
  - `GET /api/v1/users?role=PRE_USER` — 관리자 전용 (일반 유저가 호출하면 403)
  - `PATCH /api/v1/users/{id}` — 본인 또는 관리자만 (타인 정보 수정 시도하면 403)
  - `PATCH /api/v1/users/{id}/approve`, `/reject` — 관리자 전용, PRE_USER ↔ USER 전환
  - `PATCH /api/v1/users/{id}/password` — 비밀번호 재설정(본인은 현재 비밀번호 검증, 관리자는 타인 비밀번호 바로 초기화)
  - `PATCH /api/v1/users/{id}/role` — SUPER_ADMIN 전용, USER ↔ ADMIN 권한 전환(acman-005~006)
  - `DELETE /api/v1/users/{id}` — 본인 또는 관리자만
  - 비밀번호는 SHA-256 해시로 저장 (bcrypt 등 전용 라이브러리는 안 씀 — 보안은 지금 우선순위 아님, 나중에 필요하면 교체)
- **이벤트 (`com.dronecontrol.event`)**: 통계 집계(`StatsService.recordSample`) 직후 자동으로 밀집도를 확인해서 임계치를 넘으면 이벤트를 만든다
  - `GET /api/v1/zones/{id}/thresholds` — 구역별 임계치 조회, 관리자가 설정 안 했으면 기본값(1단계 0.01, 2단계 0.02) 반환
  - `PUT /api/v1/zones/{id}/thresholds` — 구역별 임계치 설정
  - 같은 구역에 처리 안 된(UNCONFIRMED/CONFIRMED) 이벤트가 이미 있으면 새로 안 만듦(스팸 방지), RESOLVED 처리된 뒤 다시 임계치를 넘으면 새 이벤트 생성 — 실제로 샘플 데이터로 연속 전송해서 중복 안 생기는 것, RESOLVED 후 재생성되는 것까지 확인함
  - `GET /api/v1/events?zoneId=&status=`, `PATCH /api/v1/events/{id}` (status, memo — 로그인 필요)
- **히스토리 (`com.dronecontrol.history`)**: 관리자 작업(회원 승인/미승인, 이벤트 처리)을 자동으로 기록
  - `GET /api/v1/history?adminId=&actionType=` — **최고관리자 전용** (ADMIN도 403)

작업 시작 전에 DB 스키마([../db/schema.sql](../db/schema.sql)) 확인.

## 통계 엑셀 다운로드 · SSE 실시간 스트림 · 녹화 재생 (신규, 전부 동작 검증 완료)

- **엑셀 다운로드(export-001)**: `GET /api/v1/zones/{id}/stats/export?from=&to=` — Apache POI(`poi-ooxml`)로 구역 분단위 통계를 xlsx로 생성해 다운로드. 헤더 스타일 적용, 자동 열너비, 틀고정(1행) 적용. 실제 파일 다운로드 후 openpyxl로 내용 일치까지 확인
- **SSE 실시간 스트림(sse-001)**: `GET /api/v1/zones/{id}/stats/stream` — `SseEmitter` 기반, `StatsService.recordSample()`이 분단위 통계를 갱신할 때마다 구독 중인 연결에 `stat` 이벤트로 즉시 push(폴링 불필요). 구독자가 없는 구역은 매번 DB를 추가 조회하지 않도록 구독자 존재 여부를 먼저 확인. curl로 SSE 연결을 열어둔 채 `/internal/v1/stats`를 호출해 실시간 push 확인 완료
- **녹화 재생(repfocus-001)**: `GET /api/v1/zones/{id}/playback?from=&to=` — 해당 구간의 녹화 이력(`recordings` 테이블, DUMMY 캠이 RUNNING으로 전환될 때마다 재생 시작 이력을 자동 기록)과 통계 타임라인을 함께 반환. web-dashboard에서 녹화 목록 + 인원 추이 그래프로 재생

## 비밀번호 재설정 · 관리자 간 관리 · 구역 간 이동 조회 (신규, 전부 동작 검증 완료)

- **비밀번호 재설정(acman-004)**: `PATCH /api/v1/users/{id}/password` — 본인이 변경할 땐 `currentPassword`로 기존 비밀번호를 검증한 뒤 변경, 관리자가 타인 비밀번호를 초기화할 땐 `currentPassword` 없이 `newPassword`만으로 처리. 관리자가 타인 비밀번호를 바꾸면 `work_history`에 자동 기록. curl로 본인 변경(현재 비밀번호 틀림 403/맞음 200), 관리자 초기화, 제3자의 무단 변경 시도(403) 전부 확인
- **관리자 간 관리(acman-005~006)**: `PATCH /api/v1/users/{id}/role` — SUPER_ADMIN 전용, body `{"role":"ADMIN"}` 또는 `{"role":"USER"}`로 USER↔ADMIN 권한 전환. SUPER_ADMIN 본인이나 PRE_USER는 대상에서 제외(409). 일반 회원이 호출하면 403. curl로 승격·강등·SUPER_ADMIN 보호·비관리자 차단까지 전부 확인
- **구역 간 이동 조회(stats-006)**: `GET /api/v1/regions/{id}/flows?from=&to=` — `zone_stats_minute`에 이미 쌓이고 있는 `outflow_n/e/s/w`를 `zone_neighbors`(N/E/S/W 인접 구역)와 매칭해 분단위로 `zone_flows`에 반영, 구간 합산 결과를 구역명과 함께 반환. `id`는 동(DONG) 레벨 지역. curl로 인접 구역 등록 → 샘플 전송 → `zone_flows` 적재 → API 응답 일치까지 end-to-end 확인(전용 화면은 미포함, API만 구현)

## 아직 안 한 것

- 없음 (설계된 기능은 모두 구현·검증 완료. 위 3개 기능은 API만 구현되어 있고 전용 화면은 아직 없음)
