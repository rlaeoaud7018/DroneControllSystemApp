# 드론관제시스템 (CCTV/드론 영상 기반 관제 시스템)

촬영된(또는 녹화된) 영상을 분석해 구역별 인원수·이동·밀집도를 집계하고,
지도 기반 PC 대시보드와 모바일 앱으로 보여주는 관제 시스템.

발표 시 실제 라이브 스트리밍 대신, 녹화 영상을 `DUMMY` 캠으로 등록해
반복 재생하면서 실시간인 것처럼 보여준다 (분석 결과도 미리 캐시해둔 뒤 재생에 맞춰 내보냄).

## 모듈 구성

| 모듈 | 역할 | 비고 |
|---|---|---|
| [backend-api](backend-api/README.md) | 회원/지역/구역/캠/통계/이벤트/히스토리 REST API, DB 연동 | Spring Boot + MyBatis |
| [analysis-server](analysis-server/README.md) | 영상에서 인원수·이동·밀집도 분석 | Flask(Python), YOLOv8s+ByteTrack |
| [web-dashboard](web-dashboard/README.md) | 지도+그래프+히트맵 PC 대시보드 | |
| [mobile-app](mobile-app/README.md) | 로그인/가입, 구역·캠 조회, 가동·정지, 실시간 영상, 지도, 이벤트 처리 | Android (Gradle 빌드+에뮬레이터 실행 검증 완료) |
| [db](db/README.md) | DB 스키마 | |

## 데이터 흐름

```
[녹화 영상 파일]
      │  (DUMMY 캠 등록)
      ▼
analysis-server  ──영상 분석(인원수/이동/밀집도)──┐
      │                                           │
      │ POST /internal/v1/stats (초단위 결과)      │
      │ PATCH /internal/v1/cameras/{id}/status    │
      ▼                                           ▼
backend-api  ──집계(1분/일별)──  DB
      │
      ├── GET /api/v1/... ──→ web-dashboard (지도/그래프/히트맵)
      ├── GET /api/v1/stats/stream (SSE) ──→ web-dashboard 실시간 갱신
      └── GET /api/v1/... ──→ mobile-app (조회/이벤트 알림)
```

## 참고 문서

요구사항명세서 / 기능명세서 / API명세서는 팀 공유 자료 기준. 각 모듈 README에 관련 API만 추려서 정리해둠.

## 진행 상태

- [x] DB 스키마 확정 및 생성 (`db_dronecontrol`, MySQL)
- [x] backend-api: region/zone/camera CRUD + 구역 인접 설정 + 가동/정지 상태 전환 — **Spring Boot + MyBatis로 전면 재작성 후 재검증 완료** (기존 Servlet+JDBC 버전과 동일 결과 확인)
- [x] analysis-server PoC: 사람 탐지(YOLOv8n) + 추적(ByteTrack) + 유입/유출 집계 — 전부 동작 검증 완료
- [x] analysis-server 실제 Flask 서버 + backend-api 연동 — region→zone→camera→분석시작→1분 통계 누적→라이브 뷰까지 end-to-end 검증 완료 (Spring Boot 전환 후에도 재확인)
- [x] backend-api ↔ analysis-server 양방향 연결 완료 (`PATCH /api/v1/cameras/{id}/state`가 analysis-server를 자동 호출, 꺼져있으면 502)
- [x] web-dashboard — 레퍼런스 스크린샷 3장에 맞춰 전면 재구성(탭/시구동 지역필터/드래그 리사이즈 지도+상태별 마커/카메라 카드 그리드/아코디언형 통계 사이드바), 지도는 구글 지도로 통일(Android와 같은 Maps Platform 계정 재사용). 실제 브라우저로 동작 확인 ([web-dashboard/README.md](web-dashboard/README.md))
- [x] mobile-app — 구역/캠 목록, 통계, 가동·정지, 실시간 영상(WebView). 실제 Gradle 빌드 + 에뮬레이터 설치·실행까지 검증 완료 ([mobile-app/README.md](mobile-app/README.md))
- [x] backend-api: 회원(가입/로그인/권한), 이벤트(임계치 자동 감지+중복방지), 히스토리(관리자 작업 기록) — 전부 실제 동작 검증 완료 ([backend-api/README.md](backend-api/README.md))
- [x] web-dashboard에 로그인/회원가입/승인대기 + 이벤트 탭(확인/조치완료) 연결 — 실제 브라우저로 전체 흐름 검증 완료
- [x] mobile-app에 로그인/회원가입/승인대기 + 이벤트 목록(확인/조치완료) 연결 — 실제 에뮬레이터로 전체 흐름 검증 완료 (세션 쿠키 유지 버그 찾아서 고침)
- [x] mobile-app에 지도 화면(Google Maps SDK for Android) 추가 — 구역 마커 표시, 마커 클릭 시 구역 상세로 이동. 실제 에뮬레이터로 검증 완료 (Android 전용 Maps API 키 별도 발급)
- [x] analysis-server: 이동 분석(속도·8방향) 추가 + 더미 영상 다양화 — 초상권/개인정보 문제를 피하려고 출처 불명 영상 대신 라이선스가 명확한 해외 스톡 영상을 추가로 받아 16개 카메라에 두 영상을 번갈아 배정, 라이브 뷰 끊김 버그와 동시 분석 충돌 버그도 같이 수정 ([analysis-server/README.md](analysis-server/README.md))
- [x] 통계 엑셀 다운로드(Apache POI) · SSE 실시간 스트림 · 녹화 재생(recordings 이력+타임라인) · 임계치 설정 전용 관리자 화면("관리" 탭) — 전부 실제 MySQL+Spring Boot 기동 후 curl과 Chrome 헤드리스 브라우저 클릭 테스트로 검증 완료
- [x] 구역 간 이동 조회(`zone_flows` 집계) · 비밀번호 재설정 · 관리자 간 관리 기능(acman-004~006) — 전부 실제 MySQL+Spring Boot 기동 후 curl로 end-to-end 검증 완료 (전용 화면은 없고 API만 구현) ([backend-api/README.md](backend-api/README.md))

혼자 진행하는 프로젝트라 역할 분배는 하지 않고, 아래 순서로 순차 진행한다.

**기술 스택**: backend-api는 Spring Boot + MyBatis(Maven) — "실무처럼 만들어보자"는 방향으로 중간에 Servlet+JDBC에서 전환함(기존 코드 전체 재작성, 동작은 동일하게 재검증 완료). analysis-server는 Flask(Python), web-dashboard는 순수 HTML/JS, mobile-app은 Android Java로 유지.

## 진행 순서

1. ~~backend-api: region → zones → cameras CRUD~~ (완료, 이후 Spring Boot+MyBatis로 재작성)
2. ~~analysis-server 최소 PoC~~ (완료)
3. ~~analysis-server 실제 Flask 서버 + backend-api 연동~~ (완료, [analysis-server/README.md](analysis-server/README.md) 참고)
4. ~~backend-api ↔ analysis-server 양방향 연결 마무리~~ (완료)
5. ~~web-dashboard 최소 화면~~ (완료, [web-dashboard/README.md](web-dashboard/README.md))
6. ~~mobile-app 최소 화면~~ (완료, [mobile-app/README.md](mobile-app/README.md) — 등록 당시엔 회원 기능이 없어서 로그인 화면 제외)
7. ~~회원/이벤트/히스토리~~ (완료, [backend-api/README.md](backend-api/README.md))
8. ~~web-dashboard에 로그인/이벤트 화면 연결~~ (완료, [web-dashboard/README.md](web-dashboard/README.md))
9. ~~mobile-app에 로그인/이벤트 화면 연결~~ (완료, [mobile-app/README.md](mobile-app/README.md))
10. ~~엑셀 다운로드, SSE 실시간 스트림, 녹화 재생, 임계치 설정 UI 등 세부 기능~~ (완료)
11. ~~구역 간 이동 조회, 비밀번호 재설정, 관리자 간 관리 기능~~ (완료 — 전 과정 API 구현·검증 끝)
