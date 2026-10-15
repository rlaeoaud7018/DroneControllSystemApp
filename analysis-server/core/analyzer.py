"""
더미 영상 하나를 처음부터 끝까지 분석해서 캐시(JSON)를 만든다.

- 사람 탐지: YOLOv8n
- 추적: ultralytics 내장 ByteTrack (model.track)
- 이동분석: 화면 안쪽 MARGIN_RATIO 지점을 판정선으로 두고
  가장자리(N/E/S/W)->중앙 전환을 유입, 중앙->가장자리 전환을 유출로 집계
- 체류시간: 중앙 진입 시각 ~ 유출 시각(초 단위 인덱스 차이)

poc_flow_count.py 에서 검증한 로직을 재사용 가능한 형태로 옮긴 것.
"""

import math
import os

import cv2
from ultralytics import YOLO

from core import cache

PERSON_CLASS_ID = 0
CONFIDENCE_THRESHOLD = 0.35
INFERENCE_IMAGE_SIZE = 1280  # 기본 640으로 추론하면 군중 속 작고 먼 사람이 뭉개져서 거의 탐지가 안 됨
MARGIN_RATIO = 0.15
MOVEMENT_THRESHOLD_PX = 2.0  # 이보다 작은 이동은 정지 상태의 잡음으로 보고 무시
DIRECTIONS = ["E", "NE", "N", "NW", "W", "SW", "S", "SE"]

# 라이브뷰용 프레임 사전 렌더링 설정. app.py의 live_view()와 반드시 같은 값을 써야
# 캐시된 프레임 크기가 대시보드 카드 표시 크기와 맞는다(둘 다 여기 값을 참조한다).
LIVE_VIEW_MAX_WIDTH = 640
LIVE_JPEG_PARAMS = [cv2.IMWRITE_JPEG_QUALITY, 70]

_model = None


def direction_of(dx, dy):
    """화면(픽셀) 기준 8방향 라벨. 실제 지리적 방위가 아니라 카메라 프레임 기준 상대 방향이다."""
    angle = math.degrees(math.atan2(-dy, dx))
    idx = round(angle / 45) % 8
    return DIRECTIONS[idx]


def get_model():
    global _model
    if _model is None:
        # nano(yolov8n)는 밀집 군중 속 작고 먼 사람을 거의 못 잡아서(화면 아래쪽 큰 사람만 탐지)
        # small(yolov8s)로 올림 — 속도는 조금 느려지지만 작은 객체 인식률이 훨씬 좋다.
        _model = YOLO("yolov8s.pt")
    return _model


def zone_of(cx, cy, width, height):
    margin_x = width * MARGIN_RATIO
    margin_y = height * MARGIN_RATIO

    if margin_x <= cx <= width - margin_x and margin_y <= cy <= height - margin_y:
        return "CENTER"

    dist_top = cy
    dist_bottom = height - cy
    dist_left = cx
    dist_right = width - cx

    closest = min(dist_top, dist_bottom, dist_left, dist_right)
    if closest == dist_top:
        return "N"
    if closest == dist_bottom:
        return "S"
    if closest == dist_left:
        return "W"
    return "E"


def analyze_video(video_path, on_progress=None):
    """
    영상을 전부 분석해서 캐시 딕셔너리를 반환한다.
    on_progress(percent:int) 콜백을 주면 진행률을 중간중간 알려준다.

    분석을 위해 매 프레임을 어차피 디코딩하므로, 같은 김에 라이브뷰용으로 축소+박스
    합성까지 끝낸 JPEG도 프레임별로 디스크에 미리 만들어둔다(core/cache.py의 프레임
    캐시). 4K 등 무거운 원본은 라이브뷰 요청마다 원본을 다시 디코딩/축소/인코딩하는
    비용이 너무 커서(캠 여러 대 동시 구동 시 캠당 1fps 미만까지 떨어짐) 실시간 재생이
    느려지는 문제가 있었는데, 분석 시점에 한 번만 렌더링해두면 라이브뷰는 그 결과
    파일을 그대로 읽어 보내기만 하면 되어 이 비용이 통째로 사라진다.
    """
    model = get_model()

    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        raise RuntimeError(f"영상을 열 수 없습니다: {video_path}")

    fps = cap.get(cv2.CAP_PROP_FPS) or 25.0
    frame_count = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))

    # 가로/세로 중 더 긴 쪽 기준으로 축소(세로로 긴 포트레이트 영상도 제대로 줄어들게).
    live_scale = min(1.0, LIVE_VIEW_MAX_WIDTH / max(width, height))
    frames_dir = cache.start_frame_cache(video_path)

    last_zone = {}        # track_id -> zone
    enter_second = {}     # track_id -> CENTER 진입한 초 인덱스
    last_position = {}    # track_id -> (cx, cy, frame_index), 속도/방향 계산용

    per_second_stats = []
    frame_boxes = {}

    current_second = -1
    second_person_count = 0
    second_in = {"N": 0, "E": 0, "S": 0, "W": 0}
    second_out = {"N": 0, "E": 0, "S": 0, "W": 0}
    second_dwell = []
    second_speeds = []
    second_moves = {d: 0 for d in DIRECTIONS}

    frame_index = 0
    last_reported_percent = -1

    while True:
        ok, frame = cap.read()
        if not ok:
            break

        this_second = int(frame_index / fps)

        if this_second != current_second:
            if current_second >= 0:
                per_second_stats.append({
                    "personCount": second_person_count,
                    "in": dict(second_in),
                    "out": dict(second_out),
                    "dwellSec": list(second_dwell),
                    "avgSpeedPxS": round(sum(second_speeds) / len(second_speeds), 2) if second_speeds else 0.0,
                    "moves": dict(second_moves),
                })
            current_second = this_second
            second_in = {"N": 0, "E": 0, "S": 0, "W": 0}
            second_out = {"N": 0, "E": 0, "S": 0, "W": 0}
            second_dwell = []
            second_speeds = []
            second_moves = {d: 0 for d in DIRECTIONS}

        result = model.track(
            frame, classes=[PERSON_CLASS_ID], conf=CONFIDENCE_THRESHOLD, imgsz=INFERENCE_IMAGE_SIZE,
            persist=True, verbose=False, tracker="bytetrack.yaml",
        )[0]

        boxes_this_frame = []

        if result.boxes.id is not None:
            xyxy = result.boxes.xyxy.cpu().numpy()
            track_ids = result.boxes.id.int().cpu().numpy()

            second_person_count = len(xyxy)

            for (x1, y1, x2, y2), track_id in zip(xyxy, track_ids):
                track_id = int(track_id)
                boxes_this_frame.append([round(float(x1), 1), round(float(y1), 1),
                                          round(float(x2), 1), round(float(y2), 1), track_id])

                cx = (x1 + x2) / 2
                cy = (y1 + y2) / 2
                zone = zone_of(cx, cy, width, height)

                prev = last_zone.get(track_id)
                if prev is not None and prev != zone:
                    if prev != "CENTER" and zone == "CENTER":
                        second_in[prev] += 1
                        enter_second[track_id] = this_second
                    elif prev == "CENTER" and zone != "CENTER":
                        second_out[zone] += 1
                        entered = enter_second.pop(track_id, None)
                        if entered is not None:
                            second_dwell.append(max(0, this_second - entered))

                last_zone[track_id] = zone

                prev_pos = last_position.get(track_id)
                if prev_pos is not None:
                    prev_cx, prev_cy, prev_frame = prev_pos
                    dx = cx - prev_cx
                    dy = cy - prev_cy
                    distance_px = math.hypot(dx, dy)
                    elapsed_sec = (frame_index - prev_frame) / fps
                    if distance_px >= MOVEMENT_THRESHOLD_PX and elapsed_sec > 0:
                        second_speeds.append(distance_px / elapsed_sec)
                        second_moves[direction_of(dx, dy)] += 1
                last_position[track_id] = (cx, cy, frame_index)

        frame_boxes[str(frame_index)] = boxes_this_frame

        # 라이브뷰와 똑같은 방식(축소 -> 박스 그리기 -> JPEG 인코딩)으로 미리 렌더링해서 저장.
        live_frame = frame
        if live_scale < 1.0:
            live_frame = cv2.resize(frame, None, fx=live_scale, fy=live_scale, interpolation=cv2.INTER_AREA)
        for x1, y1, x2, y2, track_id in boxes_this_frame:
            bx1, by1, bx2, by2 = x1 * live_scale, y1 * live_scale, x2 * live_scale, y2 * live_scale
            cv2.rectangle(live_frame, (int(bx1), int(by1)), (int(bx2), int(by2)), (0, 255, 0), 2)
            cv2.putText(live_frame, f"id:{track_id}", (int(bx1), max(0, int(by1) - 5)),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 255, 0), 1)
        ok_enc, jpeg = cv2.imencode(".jpg", live_frame, LIVE_JPEG_PARAMS)
        if ok_enc:
            with open(os.path.join(frames_dir, f"{frame_index}.jpg"), "wb") as f:
                f.write(jpeg.tobytes())

        frame_index += 1

        if on_progress and frame_count > 0:
            percent = math.floor(frame_index / frame_count * 100)
            if percent != last_reported_percent:
                on_progress(percent)
                last_reported_percent = percent

    if current_second >= 0:
        per_second_stats.append({
            "personCount": second_person_count,
            "in": dict(second_in),
            "out": dict(second_out),
            "dwellSec": list(second_dwell),
            "avgSpeedPxS": round(sum(second_speeds) / len(second_speeds), 2) if second_speeds else 0.0,
            "moves": dict(second_moves),
        })

    cap.release()
    cache.finish_frame_cache(video_path)

    if on_progress:
        on_progress(100)

    return {
        "fps": fps,
        "frame_count": frame_index,
        "frame_width": width,
        "frame_height": height,
        "margin_ratio": MARGIN_RATIO,
        "per_second_stats": per_second_stats,
        "frame_boxes": frame_boxes,
    }
