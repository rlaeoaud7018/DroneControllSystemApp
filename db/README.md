# db

[schema.sql](schema.sql) — 요구사항/기능명세서 기반 DB 스키마 초안 (MySQL 8 기준).

## 테이블 요약

| 테이블 | 설명 |
|---|---|
| users | 회원 (role: PRE_USER/USER/ADMIN/SUPER_ADMIN, 비회원은 DB에 없음) |
| regions | 시/구/동 계층 지역 |
| zones | 동 안의 촬영 구역 |
| zone_neighbors | 구역의 동/서/남/북 인접 구역 |
| cameras | 구역별 캠 (LIVE/DUMMY) |
| zone_stats_minute | 1분 단위 인원수/밀집도/유입유출/체류시간/이동 속도·8방향 이동량 |
| zone_stats_daily | 일별 집계 |
| zone_flows | 구역 간 이동량 |
| event_thresholds | 구역별 혼잡 단계 임계치 |
| events | 발생 이벤트 (상태: 미확인/확인/조치완료) |
| recordings | 녹화 파일 (더미 캠 재생 소스 포함) |
| work_history | 관리자 작업 기록 |

## 아직 미정인 부분

- 실제 사용할 DB 종류(MySQL/PostgreSQL) 및 ORM(JPA 등) 확정
- `zones.geometry`, `regions`의 지도 좌표 저장 형식 (단순 TEXT vs JSON vs 공간 데이터 타입)
- 인덱스 설계 (조회 패턴 확정 후 추가)

리뷰 후 수정 필요하면 이 파일 기준으로 논의하면 됩니다.
