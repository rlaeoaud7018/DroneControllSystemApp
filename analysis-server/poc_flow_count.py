"""
analysis-server 최소 PoC (v3) - 이동 분석(유입/유출) PoC.

기능명세서 vanly-002 기준: 화면 네 변 안쪽에 판정선을 두고, 판정선 바깥(가장자리 띠)에서
안쪽(중앙)으로 넘어오면 해당 변 방향의 유입, 안쪽에서 바깥으로 넘어가면 유출로 센다.
화면 안에서 처음 나타나거나(가장자리를 거치지 않고) 화면 안에서 사라지는 사람은 집계하지 않는다.

사람 추적은 ultralytics의 내장 트래커(ByteTrack, model.track)로 각 사람에게
프레임 간 유지되는 ID를 부여해서 구현한다 (별도 DeepSORT 등 불필요).

실행: python poc_flow_count.py
"""

import os

import cv2
from ultralytics import YOLO

VIDEO_PATH = os.path.join(os.path.dirname(__file__), "sample_videos", "vtest.avi")
OUTPUT_FRAME_PATH = os.path.join(os.path.dirname(__file__), "sample_videos", "poc_flow_frame.jpg")

PERSON_CLASS_ID = 0
CONFIDENCE_THRESHOLD = 0.4
MARGIN_RATIO = 0.15  # 화면 각 변에서 안쪽으로 15% 지점에 판정선


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


def main():
    model = YOLO("yolov8n.pt")

    cap = cv2.VideoCapture(VIDEO_PATH)
    if not cap.isOpened():
        print(f"영상을 열 수 없습니다: {VIDEO_PATH}")
        return

    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))

    last_zone = {}       # track_id -> zone
    inflow = {"N": 0, "E": 0, "S": 0, "W": 0}
    outflow = {"N": 0, "E": 0, "S": 0, "W": 0}

    frame_index = 0
    saved_sample_frame = False

    while True:
        ok, frame = cap.read()
        if not ok:
            break
        frame_index += 1

        result = model.track(
            frame, classes=[PERSON_CLASS_ID], conf=CONFIDENCE_THRESHOLD,
            persist=True, verbose=False, tracker="bytetrack.yaml",
        )[0]

        if result.boxes.id is None:
            continue

        boxes = result.boxes.xyxy.cpu().numpy()
        track_ids = result.boxes.id.int().cpu().numpy()

        for (x1, y1, x2, y2), track_id in zip(boxes, track_ids):
            cx = (x1 + x2) / 2
            cy = (y1 + y2) / 2
            zone = zone_of(cx, cy, width, height)

            prev = last_zone.get(track_id)
            if prev is not None and prev != zone:
                if prev != "CENTER" and zone == "CENTER":
                    inflow[prev] += 1
                elif prev == "CENTER" and zone != "CENTER":
                    outflow[zone] += 1

            last_zone[track_id] = zone

        if not saved_sample_frame and len(boxes) > 0:
            annotated = result.plot()
            margin_x = int(width * MARGIN_RATIO)
            margin_y = int(height * MARGIN_RATIO)
            cv2.rectangle(annotated, (margin_x, margin_y), (width - margin_x, height - margin_y), (0, 0, 255), 2)
            cv2.imwrite(OUTPUT_FRAME_PATH, annotated)
            saved_sample_frame = True

    cap.release()

    print(f"분석한 프레임 수: {frame_index}")
    print(f"유입(inflow): {inflow}")
    print(f"유출(outflow): {outflow}")
    if saved_sample_frame:
        print(f"판정선 포함 샘플 이미지 저장: {OUTPUT_FRAME_PATH}")


if __name__ == "__main__":
    main()
