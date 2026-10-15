"""
analysis-server Flask 앱.

DUMMY 캠만 지원한다 (이 프로젝트는 실제 드론/라이브 스트리밍을 쓰지 않기로 했음).
캠을 가동하면:
  1) 영상 캐시가 없으면 전체를 먼저 분석해서 캐시(JSON)를 만든다 (진행률을 backend-api에 보고)
  2) 캐시를 1초 간격으로 순환 재생하면서, 매초 분석 결과를 backend-api로 전송한다 (반복 재생)
  3) /analyzer/v1/cameras/{id}/live 로 현재 재생 중인 프레임을 박스와 함께 MJPEG로 보여준다
"""

import itertools
import os
import threading
import time
from datetime import datetime

import cv2
from flask import Flask, Response, jsonify, request

from core import analyzer, cache
from core.backend_client import report_camera_status, send_stats

app = Flask(__name__)

SAMPLE_VIDEO_DIR = os.path.join(os.path.dirname(__file__), "sample_videos")
LIVE_VIEW_MAX_WIDTH = 640  # 대시보드 카드/모달 표시 크기 기준. 캠 여러 대 동시 구동 시 인코딩 부하를 더 줄이려 960에서 낮춤.

# camera_id(int) -> state dict
CAMERAS = {}
LOCK = threading.Lock()

# YOLO 모델(전역 싱글턴) + 내장 ByteTrack 트래커는 스레드 안전하지 않다.
# 캐시 없는 영상을 여러 캠이 동시에 analyze_video()로 분석하면 model.track() 호출이
# 겹치면서 예외가 나므로, 최초 분석 구간만 이 락으로 한 번에 하나씩만 돌게 한다.
# (이미 캐시된 영상의 재생/라이브 뷰는 이 락과 무관하게 동시에 여러 개 가능)
ANALYSIS_LOCK = threading.Lock()


def resolve_video_path(source_uri):
    if os.path.isabs(source_uri) and os.path.exists(source_uri):
        return source_uri
    candidate = os.path.join(SAMPLE_VIDEO_DIR, source_uri)
    return candidate


def _playback_loop(camera_id, state):
    cached = state["cache"]
    per_second_stats = cached["per_second_stats"]

    if not per_second_stats:
        report_camera_status(camera_id, "ERROR", 0, "분석 결과가 비어 있습니다.")
        return

    for i in itertools.cycle(range(len(per_second_stats))):
        if state["stop_event"].is_set():
            break

        stat = per_second_stats[i]
        sample = {
            "cameraId": camera_id,
            # backend-api(Java LocalDateTime.parse)는 시간대 오프셋이 없는 순수 ISO 문자열만 받는다
            "at": datetime.now().replace(microsecond=0).isoformat(),
            "personCount": stat["personCount"],
            "in": stat["in"],
            "out": stat["out"],
            "dwellSec": stat["dwellSec"],
            "avgSpeedPxS": stat.get("avgSpeedPxS", 0),
            "moves": stat.get("moves", {}),
        }
        send_stats([sample])

        time.sleep(1)


def _start_camera(camera_id, source_uri):
    video_path = resolve_video_path(source_uri)

    state = {
        "source_uri": source_uri,
        "status": "PREPARING",
        "cache": None,
        "stop_event": threading.Event(),
    }
    with LOCK:
        CAMERAS[camera_id] = state

    report_camera_status(camera_id, "PREPARING", 0)

    try:
        if cache.has_cache(video_path):
            cached = cache.load_cache(video_path)
        else:
            def on_progress(percent):
                report_camera_status(camera_id, "PREPARING", percent)

            if not os.path.exists(video_path):
                raise RuntimeError(f"영상 파일이 없습니다: {video_path}")

            with ANALYSIS_LOCK:
                if cache.has_cache(video_path):  # 락 기다리는 동안 다른 스레드가 이미 분석했을 수 있음
                    cached = cache.load_cache(video_path)
                else:
                    cached = analyzer.analyze_video(video_path, on_progress=on_progress)
                    cache.save_cache(video_path, cached)

    except Exception as e:
        print(f"[app] 캠 {camera_id} 분석 실패: {e}")
        report_camera_status(camera_id, "ERROR", 0, str(e))
        with LOCK:
            state["status"] = "ERROR"
        return

    with LOCK:
        state["cache"] = cached
        state["video_path"] = video_path
        state["status"] = "RUNNING"

    report_camera_status(camera_id, "RUNNING", 100)

    _playback_loop(camera_id, state)

    report_camera_status(camera_id, "STOPPED", 100)
    with LOCK:
        state["status"] = "STOPPED"


@app.route("/analyzer/v1/cameras", methods=["POST"])
def start_camera():
    body = request.get_json(force=True)
    camera_id = body.get("cameraId")
    source_type = body.get("sourceType")
    source_uri = body.get("sourceUri")

    if camera_id is None or source_uri is None:
        return jsonify({"message": "cameraId, sourceUri가 필요합니다."}), 400

    if source_type != "DUMMY":
        return jsonify({"message": "이 프로젝트는 DUMMY 캠만 지원합니다."}), 400

    with LOCK:
        existing = CAMERAS.get(camera_id)
        if existing is not None and existing["status"] in ("PREPARING", "RUNNING"):
            return jsonify({"cameraId": camera_id, "accepted": True}), 202

    thread = threading.Thread(target=_start_camera, args=(camera_id, source_uri), daemon=True)
    thread.start()

    return jsonify({"cameraId": camera_id, "accepted": True}), 202


@app.route("/analyzer/v1/cameras/<int:camera_id>", methods=["DELETE"])
def stop_camera(camera_id):
    with LOCK:
        state = CAMERAS.get(camera_id)

    if state is None:
        return jsonify({"message": "분석 중이 아닌 캠입니다."}), 404

    state["stop_event"].set()

    return "", 204


@app.route("/analyzer/v1/cameras/<int:camera_id>/live")
def live_view(camera_id):
    with LOCK:
        state = CAMERAS.get(camera_id)

    if state is None or state.get("cache") is None:
        return jsonify({"message": "분석 중이 아닌 캠입니다."}), 404

    video_path = state["video_path"]
    cached = state["cache"]
    frame_count = cached["frame_count"]
    frame_boxes = cached["frame_boxes"]
    source_fps = cached.get("fps") or 25
    source_width = cached.get("frame_width") or LIVE_VIEW_MAX_WIDTH
    source_height = cached.get("frame_height") or LIVE_VIEW_MAX_WIDTH
    # 목표 출력 간격. 과도한 CPU 사용을 막기 위해 24fps로 상한을 두지만, 실제로 이
    # 간격을 못 맞추더라도(캠 여러 대 동시 구동 등) 재생 "속도"만큼은 항상 실제
    # 시간과 맞게 아래 generate()에서 경과 시간 기반으로 보정한다.
    target_interval = 1.0 / min(source_fps, 24)
    # 대시보드 카드/모달은 수백 px 폭으로만 보여주는데 4K 원본을 그대로 인코딩하면
    # 캠 여러 대를 동시에 돌릴 때 인코딩 시간이 프레임 간격보다 길어져서 재생이 끊긴다.
    # 표시 크기에 맞춰 미리 축소해서 인코딩 비용을 확 줄인다(작은 영상은 그대로 둠).
    # 가로 기준으로만 축소하면 세로로 긴(포트레이트) 영상은 축소가 거의 안 돼서
    # (예: 1080x1920 -> 960x1707, 픽셀 수는 거의 그대로) 여전히 느렸다 — 가로/세로
    # 중 더 긴 쪽을 기준으로 축소해야 실제로 인코딩 비용이 줄어든다.
    scale = min(1.0, LIVE_VIEW_MAX_WIDTH / max(source_width, source_height))
    JPEG_ENCODE_PARAMS = [int(cv2.IMWRITE_JPEG_QUALITY), 70]  # 기본 95 -> 70. 대시보드 썸네일엔 충분하고 인코딩이 눈에 띄게 빨라짐
    MAX_SKIP_SECONDS = 2.0  # 연결 직후처럼 비정상적으로 큰 공백은 2초치만 건너뛰도록 제한
    MIN_SLEEP = 0.01  # Flask 스레드가 GIL을 계속 점유해 다른 캠을 굶기지 않도록 매 루프 최소 sleep 보장
    # 4K 원본(night_plaza 등)은 건너뛸 프레임도 grab()으로 디코딩은 해야 해서 한 프레임당 비용이
    # 꽤 크다(벤치마크상 ~11ms/프레임). 캠 여러 대가 동시에 밀리면 "밀린 만큼 더 건너뛰기"가 오히려
    # 따라잡기 비용 자체를 더 키워서 그 캠만 영원히 못 따라잡는 악순환(죽음의 소용돌이)에 빠졌다 —
    # 실측상 같은 영상인데도 캠별로 2fps vs 11fps로 갈리거나 4K 캠이 0.3~0.8fps까지 떨어짐.
    # 한 번에 건너뛸 프레임 수에 상한을 둬서 이 악순환을 막는다(심한 부하 시엔 느려지되, 멈추진 않음).
    MAX_SKIP_FRAMES = 10

    def generate_from_frame_cache():
        # analyze_video()가 분석 시점에 미리 축소+박스합성까지 끝낸 JPEG를 프레임별로
        # 디스크에 저장해뒀다(core/analyzer.py). 여기서는 그 파일을 그대로 읽어 보내기만
        # 하면 되므로 원본 디코딩/리사이즈/박스그리기/인코딩 비용이 전혀 들지 않는다 —
        # 4K 원본도 가벼운 영상과 동일하게 빠르다. 비용이 거의 없어서(단순 파일 읽기)
        # MAX_SKIP_FRAMES 같은 상한도 필요 없이 경과 시간만큼 바로 건너뛸 수 있다.
        frame_index = 0
        prev_tick = time.monotonic()
        while True:
            with LOCK:
                if camera_id not in CAMERAS or CAMERAS[camera_id]["stop_event"].is_set():
                    break

            now = time.monotonic()
            elapsed = min(now - prev_tick, MAX_SKIP_SECONDS)
            prev_tick = now
            step = max(1, round(elapsed * source_fps))
            frame_index = (frame_index + step) % frame_count

            try:
                with open(cache.frame_path(video_path, frame_index), "rb") as f:
                    jpeg_bytes = f.read()
                yield (b"--frame\r\nContent-Type: image/jpeg\r\n\r\n" + jpeg_bytes + b"\r\n")
            except FileNotFoundError:
                pass

            sleep_for = target_interval - (time.monotonic() - now)
            time.sleep(max(sleep_for, MIN_SLEEP))

    def generate():
        # 통계 집계 루프(1초마다 프레임을 점프)와는 별개로, 이 생성기는 자기만의
        # 프레임 커서를 순차 증가시키며 읽는다 -> 일반 영상 재생처럼 매끄럽게 보이고,
        # 박스는 캐시된 frame_boxes에서 같은 프레임 번호로 조회해 그대로 겹쳐 그린다.
        cap = cv2.VideoCapture(video_path)
        frame_index = 0
        prev_tick = time.monotonic()
        try:
            while True:
                with LOCK:
                    if camera_id not in CAMERAS or CAMERAS[camera_id]["stop_event"].is_set():
                        break

                # 고정된 프레임 수만큼이 아니라, "직전 프레임을 내보낸 뒤 실제로 얼마나
                # 시간이 흘렀는지"를 기준으로 건너뛸 프레임 수를 매번 다시 계산한다.
                # 캠 여러 대 때문에 인코딩이 밀리더라도(= elapsed가 커지더라도) 그만큼
                # 더 많이 건너뛰어 영상 속 시간을 실제 시간에 맞추므로, 화면은 거칠어질
                # 수 있어도 재생 "속도"만큼은 항상 정확하다 — 예전엔 고정 step만 써서
                # 서버가 밀리면 그대로 슬로모션이 되는 버그가 있었다.
                now = time.monotonic()
                elapsed = min(now - prev_tick, MAX_SKIP_SECONDS)
                prev_tick = now
                step = min(max(1, round(elapsed * source_fps)), MAX_SKIP_FRAMES)

                # 건너뛸 프레임은 grab()만 해서(디코딩은 하지만 색변환은 생략) 읽는 비용을 줄인다.
                ok = True
                for _ in range(step - 1):
                    ok = cap.grab()
                    frame_index += 1
                    if not ok or frame_index >= frame_count:
                        break

                if ok:
                    ok, frame = cap.read()
                    frame_index += 1

                if not ok:
                    cap.set(cv2.CAP_PROP_POS_FRAMES, 0)
                    frame_index = 0
                    prev_tick = time.monotonic()
                    continue

                shown_index = frame_index - 1

                if scale < 1.0:
                    frame = cv2.resize(frame, None, fx=scale, fy=scale, interpolation=cv2.INTER_AREA)

                boxes = frame_boxes.get(str(shown_index), [])
                for x1, y1, x2, y2, track_id in boxes:
                    x1, y1, x2, y2 = x1 * scale, y1 * scale, x2 * scale, y2 * scale
                    cv2.rectangle(frame, (int(x1), int(y1)), (int(x2), int(y2)), (0, 255, 0), 2)
                    cv2.putText(frame, f"id:{track_id}", (int(x1), max(0, int(y1) - 5)),
                                cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 255, 0), 1)

                ok, jpeg = cv2.imencode(".jpg", frame, JPEG_ENCODE_PARAMS)
                if ok:
                    yield (b"--frame\r\nContent-Type: image/jpeg\r\n\r\n" + jpeg.tobytes() + b"\r\n")

                if frame_index >= frame_count:
                    frame_index = 0
                    cap.set(cv2.CAP_PROP_POS_FRAMES, 0)

                # 목표 간격(24fps 기준)에서 이번 프레임 처리에 걸린 시간을 뺀 만큼만 쉰다.
                # 처리가 그보다 오래 걸렸어도(캠 여러 대로 밀림) 최소 시간은 반드시 sleep한다 —
                # Flask dev server는 스레드 기반이라 GIL을 공유하는데, cv2 작업(resize/encode)이
                # 끝난 뒤 전혀 sleep하지 않는 스레드는 GIL을 계속 점유해 다른 캠 스레드를 굶겨
                # 캠 간 처리량이 극단적으로 불균등해지는 문제가 있었다(같은 영상을 쓰는 캠끼리도
                # 2fps vs 11fps로 벌어짐). 최소 sleep으로 강제로 GIL을 양보해 공평하게 나눈다.
                # 밀린 시간은 다음 루프의 elapsed 계산에 반영되어 프레임을 더 건너뛰는 식으로
                # 보정되므로, 화면 매끄러움은 떨어져도 재생 "속도" 자체는 어긋나지 않는다.
                sleep_for = target_interval - (time.monotonic() - now)
                time.sleep(max(sleep_for, MIN_SLEEP))
        finally:
            cap.release()

    # 분석 시점에 미리 렌더링된 프레임 캐시가 있으면(앞으로 분석되는 영상은 전부 생김)
    # 원본 재디코딩이 필요 없는 가벼운 경로를 쓰고, 옛날에 분석돼서 프레임 캐시가 없는
    # 영상만 예전처럼 원본을 직접 디코딩하는 경로로 재생한다.
    if cache.has_frame_cache(video_path):
        chosen_generate = generate_from_frame_cache
    else:
        chosen_generate = generate

    return Response(chosen_generate(), mimetype="multipart/x-mixed-replace; boundary=frame")


@app.route("/analyzer/v1/health")
def health():
    with LOCK:
        count = len(CAMERAS)
    return jsonify({"status": "UP", "cameras": count})


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5050, threaded=True)
