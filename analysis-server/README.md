# analysis-server

영상(라이브 RTSP 또는 더미 영상 파일)에서 사람 수·이동·밀집도를 분석하는 서버. 예정 스택: Python + Flask.

backend-api가 캠을 가동시키면(`POST /analyzer/v1/cameras`) 분석을 시작하고,
초 단위 결과를 backend-api로 보낸다(`POST /internal/v1/stats`).

## 담당 API (API명세서 기준)

- `POST /analyzer/v1/cameras` 분석 시작 (camera 정보, sourceType, sourceUri 수신)
- `DELETE /analyzer/v1/cameras/{cameraId}` 분석 정지
- `GET /analyzer/v1/cameras/{cameraId}/live` 분석 중인 캠 화면(박스 포함) MJPEG 스트림
- `GET /analyzer/v1/health` 서버 상태 확인 (Docker 헬스체크)

## 분석 로직 (vanly-001~003)

- 인원 분석: 프레임마다 사람 탐지·추적, 초 단위 인원수 + 화면 체류 시간 계산
- 이동 분석: 화면 네 변 안쪽에 판정선을 두고, 판정선을 넘어 들어오면 유입, 넘어 나가면 유출로 방향별 집계
  (화면 안에서 생기거나 사라지는 사람은 유입/유출에서 제외, 가장자리를 벗어났다 다시 들어오면 새 유입으로 중복 집계)
- 더미 캠은 가동 시 캐시가 없으면 영상 전체를 먼저 분석해 프레임별 결과와 박스 그려진 재생용 영상을 만들어두고,
  재생 때는 저장된 결과를 꺼내 씀 (진행률은 `PATCH /internal/v1/cameras/{cameraId}/status`로 backend-api에 보고)

## 확정된 스택 (PoC로 검증 완료)

- 사람 탐지: **YOLOv8s (ultralytics)**. OpenCV 내장 HOG+SVM은 먼저 테스트해봤는데 박스가 부정확해서([poc_person_count.py](poc_person_count.py) 결과) 제외. 처음엔 더 가벼운 YOLOv8n을 썼는데([poc_person_count_yolo.py](poc_person_count_yolo.py) 결과, `sample_videos/poc_detected_frame_yolo.jpg`), 시부야 교차로처럼 사람이 작고 빽빽한 영상에서는 화면 아래쪽 가까운 사람 몇 명만 잡고 나머지는 거의 다 놓쳤음 — YOLOv8s로 올리고 추론 해상도도 기본 640에서 1280으로 올리니(`INFERENCE_IMAGE_SIZE`) 같은 영상에서 프레임당 평균 탐지 인원이 4명→11명으로 늘고 화면 전체(먼 사람 포함)로 박스가 퍼짐. 참고로 YOLO는 COCO로 사전 학습된 범용 모델이라 영상별로 "학습"시키는 게 아니라, 모델 크기/추론 해상도/신뢰도 임계치를 조정하는 식으로 정확도를 올리는 것 — 극단적으로 밀집된 군중(수백 명이 겹쳐 보이는 수준)은 더 큰 모델(YOLOv8m/l)을 쓰거나 카메라 각도가 덜 가파른 영상을 고르지 않는 한 일부는 계속 놓칠 수밖에 없음.
- 추적(이동분석용 ID 유지): ultralytics 내장 `model.track()` (ByteTrack) 사용 — 별도 DeepSORT 등 추가 불필요
- 환경: torch(cpu) + ultralytics 이미 설치되어 있음, yolov8n.pt(~6MB)는 최초 실행 시 자동 다운로드됨(완료). `lap`(ByteTrack 의존 패키지, ~1.5MB)은 ultralytics가 트래커 최초 실행 시 자동으로 추가 설치함(사람이 직접 pip install 하지 않아도 됨, 참고로 기록)
- 이동분석(유입/유출): 화면을 안쪽 15% 지점 기준 중앙/가장자리(N·E·S·W)로 나누고, 트래킹 ID가 가장자리→중앙으로 넘어가면 유입, 중앙→가장자리면 유출로 집계. [poc_flow_count.py](poc_flow_count.py)로 검증 완료 (`sample_videos/poc_flow_frame.jpg` 참고)

## 실제 Flask 서버 (app.py) — backend-api 연동까지 end-to-end 검증 완료

```
analysis-server/
  app.py              Flask 앱: /analyzer/v1/cameras(POST/DELETE), /live, /health
  core/
    analyzer.py       영상 전체 분석 → 캐시 생성 (탐지+추적+유입유출+체류시간)
    cache.py          캐시 파일 입출력 (sourceUri 기준 md5 키, analysis-server/cache/*.json)
    backend_client.py backend-api /internal/v1/* 호출
  sample_videos/      테스트용 더미 영상
  cache/              분석 결과 캐시 (git에는 안 올림)
```

동작 흐름: `POST /analyzer/v1/cameras` → 캐시 없으면 전체 분석(진행률을 `PATCH /internal/v1/cameras/{id}/status`로 실시간 보고) → 캐시 생성 후 1초 간격으로 반복 재생하며 `POST /internal/v1/stats`로 전송 → backend-api가 1분 단위로 집계(`zone_stats_minute`) → `GET /api/v1/zones/{id}/stats`로 조회 가능. `GET /analyzer/v1/cameras/{id}/live`는 캐시된 박스를 원본 영상 프레임 위에 그려서 MJPEG로 보여준다.

실제 Tomcat+MySQL+Flask를 전부 띄워서 region→zone→camera→분석시작→1분 통계 누적→라이브 뷰까지 확인 완료.

**실행 환경변수**: `BACKEND_BASE_URL` (기본값 `http://localhost:8080/backend-api`) — backend-api가 다른 포트/호스트면 맞춰서 설정.

**발견했던 버그**: Python `datetime.isoformat()`에 시간대 오프셋(`+09:00`)이 붙어서 Java `LocalDateTime.parse()`가 파싱 실패 → 500 에러가 났는데 `requests`가 상태 코드를 체크 안 해서 조용히 무시되고 있었음. `datetime.now()`(naive, 오프셋 없음)로 고치고, `backend_client.py`에 상태 코드 체크를 추가해서 앞으로는 실패가 로그에 보이게 함.

**발견했던 버그 2 (라이브 뷰 끊김)**: `/live`가 통계 집계 루프(`_playback_loop`)가 1초에 한 번만 갱신하는 `playback_frame_index`를 그대로 재생 프레임으로 썼음 → 1초간 프레임이 멈춰 있다가 한 번에 점프하는 식으로 보여서 뚝뚝 끊겨 보였음. `/live` 생성기가 통계 루프와 별개로 자기만의 프레임 커서를 순차 증가시키며 읽도록 분리(박스는 그대로 캐시에서 같은 프레임 번호로 조회)해서, 일반 영상처럼 매끄럽게 재생되면서 분석 박스도 같이 갱신되도록 고침.

**발견했던 버그 3 (동시 분석 시 충돌)**: 캐시가 없는 영상을 여러 캠이 동시에 가동하면 전역 YOLO 모델 인스턴스(`get_model()`의 싱글턴) + 내장 ByteTrack 트래커를 여러 스레드가 동시에 `model.track()`으로 호출하면서 예외가 나고 캠이 ERROR로 빠졌음. `analyzer.analyze_video()` 호출 구간(최초 분석일 때만)을 `ANALYSIS_LOCK`으로 감싸서 한 번에 하나씩만 돌게 고침 — 이미 캐시된 영상의 재생/라이브 뷰는 이 락과 무관하게 동시 재생 가능.

카메라 여러 대를 동시에 RUNNING으로 띄웠을 때 라이브 썸네일이 멈추는 버그가 하나 더 있었는데, 원인은 analysis-server가 아니라 web-dashboard 쪽이라 [web-dashboard/README.md](../web-dashboard/README.md)에 기록함.

**발견했던 버그 5 (라이브 재생 속도가 들쭉날쭉)**: `/live`가 박스 그리기+JPEG 인코딩에 걸린 실제 시간을 고려하지 않고 매번 고정 시간만 쉬었음 — 카메라 여러 대가 동시에 무거운 프레임(사람 많음)을 처리할 때 CPU 부하에 따라 실제 프레임 간격이 들쭉날쭉해져서 재생 속도가 불안정해 보였음(사용자 표현: "배속이 이상하다"). 목표 시각을 미리 계산해두고 그 시각까지 남은 만큼만 자는 방식(`next_frame_at` 기준 프레임 페이싱)으로 바꿔서, 처리 시간이 들쭉날쭉해도 재생 속도는 일정하게 유지되도록 고침.

## 더미 영상 소스

전부 저작권 문제 없는 영상(OpenCV 기본 샘플 또는 Pexels 무료 라이선스, 상업적 이용 가능·출처 표기 불필요)만 쓴다. 16개 카메라에 6종을 나눠 배정해서(`sourceUri`만 다름) 카메라마다 다른 장면이 보이게 함 — 밀집도는 일부러 다양하게 골랐다(한산~매우 혼잡).

**이 영상 파일들은 Git LFS로 커밋돼 있다**(`.gitattributes`에 `sample_videos/*.mp4`, `*.avi` 지정 — 일반 git으로 올리기엔 커서 GitHub 100MB/파일 제한에 걸릴 수 있는 것도 있고 저장소 비대화도 피하려고 LFS를 씀). 다른 PC에서 받을 땐 `git lfs install` 후 `git clone`/`git pull`하면 자동으로 실제 영상까지 받아진다 — LFS 없이 그냥 clone하면 이 파일들이 포인터 텍스트 파일로만 보이니 주의.

| 파일 | 내용 | 밀집도(평균 탐지 인원) | 다운로드 링크 |
|---|---|---|---|
| `vtest.avi` | OpenCV 기본 제공 보행자 테스트 샘플 (출처 불명의 일반 영상) | ~4명 | [opencv/samples/data/vtest.avi](https://raw.githubusercontent.com/opencv/opencv/master/samples/data/vtest.avi) (바이트 수까지 로컬 파일과 일치 확인됨) |
| `night_plaza.mp4` | Pexels, 야간 광장 소수 인원 | ~18명 | ⚠️ 다운로드 당시 링크를 기록 안 해둠 — 아래 참고 |
| `stairs_crowd.mp4` | Pexels, 계단을 오르는 군중 | ~25명 | ⚠️ 링크 미기록 |
| `crosswalk_moderate_pexels.mp4` | Pexels(사진작가 Gül Işık), 항공뷰 횡단보도, 사람들이 안 겹치고 퍼져 있음 | ~67명 | ⚠️ 링크 미기록 (사진작가명은 기록됨) |
| `event_crowd.mp4` | Pexels, 행사장 단체 군중(레드/화이트 복장) | ~10명 | ⚠️ 링크 미기록 |
| `stadium_plaza.mp4` | Pexels, 경기장 앞 광장 군중 | ~86명 | ⚠️ 링크 미기록 |
| `street_market_pexels.mp4` | Pexels(사진작가 Faisal Ibne Kalam), 시장 골목 항공뷰, 사람 15~20명 분산 | ~18명 | [pexels.com/video/bustling-street-market-aerial-view-37011479](https://www.pexels.com/video/bustling-street-market-aerial-view-37011479/) |
| `plaza_crossing_pexels.mp4` | Pexels(사진작가 Shruthi Pradeep), 도심 광장 항공뷰, 영어 간판만 있고 한글 없음 확인됨 | ~60명 | [pexels.com/video/aerial-view-of-busy-urban-square-with-crowd-37439457](https://www.pexels.com/video/aerial-view-of-busy-urban-square-with-crowd-37439457/) |

**⚠️ 링크 미기록 5개**: 지금은 실제 영상 파일 자체가 Git LFS로 저장소에 포함돼 있어서 **재현(다른 PC에서 받아 쓰는 것) 자체엔 문제없다.** 다만 처음 받을 때 정확한 Pexels 페이지 URL을 적어두지 않아서(작업 중 대화가 요약·압축되며 유실됨) 원 저작자 출처 표기가 "Pexels + 설명"까지만 남아있다 — 라이선스상 출처 표기 의무는 없는 플랫폼이라 문제는 없지만, 나중에 원본 페이지를 다시 찾아야 할 일이 있으면 어려울 수 있다. **앞으로 영상을 새로 받을 땐 반드시 이 표에 다운로드 페이지 URL까지 같이 적어둘 것.**

**영상을 교체/확장한 이유**: 처음엔 Mixkit의 시부야 교차로 영상("Crowds of people cross a street junction") 하나만 썼는데, 150명 가까이 겹쳐 보이는 극밀집 장면이라 YOLOv8n/s 어느 쪽으로도 화면의 일부(평균 11명)만 잡혔음. 사람이 서로 안 겹치고 또렷하게 분리된 영상이 아니면 아무리 모델을 키워도 한계가 있다는 걸 확인하고, 사람이 적당히 퍼져서 안 겹치는 Pexels 영상들로 교체·확장함(밀도가 다른 여러 개를 받아서 평균 탐지 인원이 실제 눈에 보이는 인원수와 거의 일치하는지 샘플 프레임으로 먼저 검증한 뒤 등록). (중간에 Envato Elements 미리보기 영상도 검토했는데 워터마크가 박혀 있는 유료 체험판이라 라이선스 문제로 폐기함 — 그 중 하나는 한글 표지판이 찍혀 있어서 애초에 피하려던 한국 영상이기도 했음.)

**발견했던 버그 6 (캐시 키가 경로 구분자 때문에 어긋남)**: 영상을 analysis-server 바깥에서(Flask 서버를 거치지 않고) 미리 분석해서 캐시를 만들어두는 스크립트를 짰는데, 캐시 키가 `source_uri`(여기선 절대경로)의 MD5라서 슬래시(`/`) 경로로 만든 캐시와 실제 `app.py`가 `os.path.join`으로 만드는 백슬래시(`\`) 경로의 MD5가 서로 달라 캐시를 못 찾고 매번 처음부터 다시 분석하는 문제가 있었음. 같은 파일이라도 경로 문자열이 한 글자라도 다르면 캐시가 안 맞는다는 걸 기억해둘 것 — 스크립트로 캐시를 미리 만들 땐 `app.py`와 동일한 경로 조합 방식을 써야 한다.

## 이동 분석 (속도 · 방향) — vanly-004

`analyzer.py`가 트랙 ID별로 프레임 간 중심점 이동(`dx, dy`)을 계산해서,
- **속도**: `거리(px) / 경과시간(초)` — 카메라 보정이 없어 실제 m/s가 아니라 **픽셀/초 추정치**로만 제공 (실제 거리 환산은 카메라 캘리브레이션이 필요해 범위 밖으로 둠)
- **방향**: 8방향(E/NE/N/NW/W/SW/S/SE)으로 분류 — **카메라 화면(픽셀) 기준 상대 방향**이며 실제 지리적 방위가 아님
- **이동량**: 정지 잡음(`MOVEMENT_THRESHOLD_PX` 미만)을 제외하고 방향별로 센 이동 관측 횟수

초 단위로 집계해 `avgSpeedPxS`, `moves`(8방향 카운트)를 `POST /internal/v1/stats`에 실어 보내고, backend-api가 `zone_stats_minute`에 분 단위로 누적(속도는 평균, 방향별 카운트는 합산)한 뒤 web-dashboard 통계 패널의 "이동 분석(속도·방향)" 아코디언에서 조회 가능.

## 아직 미정 / 남은 일

- 밀집도(zone area)는 backend-api의 zones.area_m2를 analysis-server가 직접 모름 — 지금은 backend-api 쪽에서 density를 계산하도록 되어 있음(analysis-server는 personCount만 보냄). 그대로 유지.
- backend-api가 `PATCH /api/v1/cameras/{id}/state`로 RUNNING 요청받았을 때 실제로 `POST /analyzer/v1/cameras`를 호출하는 연결은 아직 없음 (지금은 analysis-server를 직접 curl로 기동시켜야 함) — web-dashboard/mobile-app 붙이기 전에 이어서 구현 필요
- 캠 정지(`DELETE /analyzer/v1/cameras/{id}`) 시 backend-api의 캠 상태를 STOPPED로 되돌리는 것도 analysis-server가 report_camera_status로 처리 중(확인 완료)
- **알려진 제약**: 영상을 처음부터 분석하는 `analyzer.analyze_video()` 구간은 중간에 취소가 안 됨 — `DELETE`로 정지시켜도 `stop_event`는 분석이 끝난 뒤(재생 루프 진입 시점)에야 확인되므로, 분석 중에 정지했다가 바로 다시 가동하면 기존 분석 스레드가 끝날 때까지는 새 스레드가 안 뜨고 그냥 202만 돌아옴(기존 스레드는 결국 끝나면서 STOPPED로 멈춤 → 그 다음 가동 요청부터 정상 동작). 영상이 길거나 사람이 많아 분석이 오래 걸릴 때 체감됨.

**발견했던 버그 7 (라이브 영상이 또 끊김 — 4K 원본을 그대로 인코딩)**: 캠을 여러 대(특히 4K 소스 여러 개) 동시에 띄우니 재생이 다시 끊겼음. `/live`가 원본 해상도 그대로 박스를 그리고 JPEG 인코딩을 했는데, 대시보드 카드/모달은 수백 px 폭으로만 보여주면서 인코딩은 3840×2160 그대로 하고 있었던 것 — 캠 여러 대가 동시에 이러면 인코딩 시간이 프레임 간격보다 길어져서 끊김. `LIVE_VIEW_MAX_WIDTH`(960px) 기준으로 프레임을 미리 축소한 뒤 박스를 그리고 인코딩하도록 고침(박스 좌표도 같은 비율로 축소).

**주의 (캐시 키는 `source_uri`를 조합한 경로 문자열의 MD5 — 조합 방식이 한 글자라도 다르면 못 찾음)**: `SAMPLE_VIDEO_DIR = os.path.join(os.path.dirname(__file__), "sample_videos")`인데, `cd analysis-server && python app.py`처럼 **상대경로로 실행하면 `__file__`도 `"app.py"`(상대경로)가 되어 `SAMPLE_VIDEO_DIR`도 `"sample_videos"`(상대경로)가 된다** — 실행 방식(상대/절대경로, 슬래시/백슬래시)이 바뀌면 같은 파일이라도 캐시 키가 달라져서 캐시를 못 찾고 매번 재분석한다. 미리 캐시를 만드는 스크립트를 짤 땐 반드시 **지금 analysis-server를 띄우는 그 명령 그대로** 같은 cwd에서 실행해 경로 조합을 똑같이 맞춰야 한다(제일 확실한 방법은 외부 스크립트로 캐시를 미리 만들지 말고 그냥 `PATCH .../state RUNNING`으로 실제 가동시켜서 캐시를 만드는 것).

**발견했던 버그 8 (재생 속도가 여전히 느림 — fps만 제한하고 프레임은 안 건너뜀)**: 버그 5(프레임 페이싱)를 고친 뒤에도 "영상이 느리다"는 지적이 계속 있었음. 원인은 출력 fps를 24로 제한하면서 **프레임은 하나도 건너뛰지 않고 전부 순서대로 보여줬던 것** — 60fps 원본이면 1초치(60장)를 24fps 속도로 보여주느라 실제로는 2.5배 느린 슬로모션이 됨. `frame_step = round(source_fps / output_fps)` 만큼 프레임을 건너뛰도록(건너뛸 프레임은 `cap.grab()`만 해서 디코딩은 하되 색변환은 생략) 고쳐서, "영상 속 1초"가 "실제 1초"와 같아지도록 함.

**"대전역 영상이 뒤로 간다"는 신고 — 코드 버그는 못 찾았고, 영상을 교체함**: `stairs_crowd.mp4`(계단 군중)에서 사람들이 뒤로 걷는 것처럼 보인다는 지적을 받고, 실제 서버가 내보내는 raw MJPEG 스트림을 직접 떠서(curl로 바이트 그대로 캡처 → JPEG SOI/EOI로 프레임 분리) 트랙 ID 순서를 확인함 — ID가 시간 순으로 커지고(분석 때 사람이 늦게 등장할수록 ID가 커짐), `zone_stats_minute`의 방향 집계(`moveN`이 `moveS`보다 압도적으로 큼)와도 일치해서 **서빙 순서 자체는 정방향이 맞음**을 재확인함. 다만 이 영상은 인원이 너무 빽빽하고 일부는 반대 방향으로도 움직이는 혼잡한 계단이라, 사람 눈으로 방향을 판단하기 어려운 영상이었던 것으로 보임(코드가 맞아도 콘텐츠 자체가 헷갈림). 논쟁 대신 이미 여러 번 정방향 재생이 명확히 검증된 `crosswalk_moderate_pexels.mp4`로 대전역 영상을 교체함.

**발견했던 버그 9 (그래도 재생이 느림 — 고정 step이 실제 밀린 정도를 반영 못 함)**: 버그 8에서 `frame_step`을 원본/출력 fps 비율로 고정값 계산했는데, 캠 7대가 동시에 돌면서 실제 처리 속도가 목표(24fps)에 크게 못 미칠 때도(실측 약 6fps) 건너뛰는 프레임 수는 그대로라 여전히 느리게 재생됐음. **"직전 프레임을 내보낸 뒤 실제로 얼마나 시간이 흘렀는지"를 매 루프 측정해서, 그 경과 시간만큼의 영상 프레임 수를 건너뛰도록** 고침 — 처리가 밀릴수록(`elapsed` 커짐) 더 많이 건너뛰어 영상 속 시간과 실제 시간이 항상 맞게 함(화면 매끄러움은 떨어질 수 있어도 속도는 어긋나지 않음). 캠 7대 동시 구동 상태에서 11초짜리 영상을 10초간 캡처해보니 영상이 실제로 거의 한 바퀴(10초어치) 진행된 것을 raw 스트림 프레임 비교로 확인함.

**발견했던 버그 10 (캠 7대를 "동시에" 검증했더니 일부가 거의 멈춤 — 세로 영상 인코딩 + GIL 불공평 + 따라잡기 악순환)**: 캠을 하나씩만 테스트할 땐 괜찮았는데, 7대를 진짜 동시에 raw MJPEG 바이트로 캡처해서 프레임 수를 세보니(curl로 6초씩 동시 캡처 → SOI/EOI로 프레임 분리) 일부 캠이 초당 1프레임도 안 나오는 수준으로 멈춰 있었음. 원인이 3가지 겹쳐 있었음:
  1) `LIVE_VIEW_MAX_WIDTH / source_width`로만 축소 비율을 계산해서, 세로로 긴(포트레이트) `crosswalk_moderate_pexels.mp4`(1080×1920)는 960×1707로 거의 안 줄어든 채 인코딩되고 있었음 → 가로/세로 중 **더 긴 쪽 기준**으로 축소하도록 고치고 `LIVE_VIEW_MAX_WIDTH`도 960→640으로 낮추고 JPEG 품질도 95→70으로 낮춤.
  2) 처리가 목표 간격보다 오래 걸리면(`sleep_for <= 0`) 전혀 쉬지 않고 바로 다음 프레임으로 넘어갔는데, Flask 개발 서버는 스레드 기반이라 GIL을 공유함 — sleep을 안 하는 스레드가 GIL을 계속 점유해서 다른 캠 스레드를 굶김. 같은 영상(`crosswalk_moderate_pexels.mp4`)을 쓰는 캠끼리도 2fps vs 11fps로 극단적으로 갈리는 걸로 확인함. **매 루프 최소 10ms는 반드시 sleep**하도록 고쳐서 GIL을 강제로 양보하게 함.
  3) 4K 원본(`night_plaza.mp4`, `stadium_plaza.mp4`, `event_crowd.mp4`)은 벤치마크상 단독으로는 ~24fps 처리가 가능했지만, 건너뛸 프레임도 `cap.grab()`으로 디코딩은 해야 해서 한 프레임당 비용이 큼(4K 60프레임 grab에 ~0.69초). 밀린 만큼(elapsed 기준) 더 많이 건너뛰려다 보니 "따라잡기 비용이 따라잡아야 할 양보다 커서 영원히 못 따라잡는" 악순환에 빠져 0.3~0.8fps까지 떨어짐. **한 번에 건너뛸 프레임 수를 10으로 상한**(`MAX_SKIP_FRAMES`)을 둬서 이 악순환을 끊음 — 부하가 심하면 느려지되 멈추지는 않게 함.
  고친 뒤 7대 동시 캡처 재검증: 가벼운 영상(crosswalk×3, vtest)은 8~14fps, 무거운 4K 영상(night_plaza, stadium_plaza, event_crowd)은 2.3~3.0fps로 모두 꾸준히 전진함(가장 느린 cam4도 연속 14프레임 해시가 전부 달라 멈춰있지 않음을 확인). 7대 동시에 진짜 매끄러운 4K 실시간은 이 머신 CPU 한계상 불가능하지만, 더 이상 어떤 캠도 멈추거나 거의 0fps로 떨어지진 않음.

**발견했던 버그 11 (브라우저에서 실제로 보니 캠 1대만 영원히 안 뜸 — 호스트당 동시 연결 6개 제한)**: curl로 raw 바이트를 검증해서 "버그 10"을 고친 뒤, 사용자 요청대로 실제 대시보드를 브라우저로 열어서 눈으로 재확인하다가 발견함. RUNNING 캠 7개 중 `한밭수목원`(vtest.avi) 카드만 화면이 까맣게 멈춰 있었는데, 같은 URL을 curl로 직접 캡처하면 정상적으로 29프레임이 다 나왔음 — **백엔드/analysis-server 문제가 아니라 브라우저 쪽 문제**라는 뜻. 실제 원인: 크롬 등 브라우저는 "같은 호스트(origin)"로 동시에 열 수 있는 연결을 6개로 제한하는데, MJPEG 라이브뷰는 한 번 열리면 끝나지 않는 연결이라 캠 7대가 전부 `http://localhost:5050`을 쓰면 7번째 `<img>`는 영원히 빈 슬롯을 못 받아 `pending` 상태로 멈춤(`img.complete`가 `false`, `naturalWidth/Height`가 0으로 확인됨). `web-dashboard/.../js/app.js`에서 캠 id에 따라 `localhost`/`127.0.0.1`을 번갈아 쓰도록(`analysisServerBaseFor()`) 고쳐서 브라우저가 서로 다른 origin으로 인식하게 함 — 호스트 2개 × 6개 = 12개 슬롯으로 늘어남(둘 다 같은 로컬 서버를 가리키므로 기능 차이 없음). IPv6 루프백(`[::1]`)도 3번째 호스트로 써보려 했으나 analysis-server가 `0.0.0.0`(IPv4 전용)에만 바인딩돼 있어 연결이 안 돼서 제외함. **교훈: raw 바이트/프레임 수 검증은 analysis-server 자체의 정확성은 증명하지만 브라우저에서 실제로 보이는지는 별개 문제다 — "눈으로 직접 확인"이 반드시 필요한 이유.**
