"""
analysis-server 최소 PoC (v2) - YOLOv8n 버전.

HOG+SVM(poc_person_count.py)은 박스 품질이 나빠서, Ultralytics YOLOv8n으로 교체해 다시 확인한다.
최초 실행 시 yolov8n.pt(~6MB)를 Ultralytics 공식 저장소에서 자동으로 받는다.

실행: python poc_person_count_yolo.py
"""

import os

import cv2
from ultralytics import YOLO

VIDEO_PATH = os.path.join(os.path.dirname(__file__), "sample_videos", "vtest.avi")
OUTPUT_FRAME_PATH = os.path.join(os.path.dirname(__file__), "sample_videos", "poc_detected_frame_yolo.jpg")

PERSON_CLASS_ID = 0  # COCO 데이터셋 기준 'person' 클래스
CONFIDENCE_THRESHOLD = 0.4


def main():
    model = YOLO("yolov8n.pt")

    cap = cv2.VideoCapture(VIDEO_PATH)
    if not cap.isOpened():
        print(f"영상을 열 수 없습니다: {VIDEO_PATH}")
        return

    frame_index = 0
    counts = []
    saved_sample_frame = False

    while True:
        ok, frame = cap.read()
        if not ok:
            break

        frame_index += 1
        if frame_index % 5 != 0:
            continue

        result = model.predict(frame, classes=[PERSON_CLASS_ID], conf=CONFIDENCE_THRESHOLD, verbose=False)[0]
        person_count = len(result.boxes)
        counts.append(person_count)

        if not saved_sample_frame and person_count > 0:
            annotated = result.plot()
            cv2.putText(
                annotated, f"person count: {person_count}", (10, 25),
                cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0, 255, 0), 2,
            )
            cv2.imwrite(OUTPUT_FRAME_PATH, annotated)
            saved_sample_frame = True

    cap.release()

    if not counts:
        print("프레임을 하나도 읽지 못했습니다.")
        return

    print(f"분석한 프레임 수: {len(counts)}")
    print(f"프레임당 평균 인원수: {sum(counts) / len(counts):.2f}")
    print(f"프레임당 최대 인원수: {max(counts)}")
    print(f"사람이 1명이라도 감지된 프레임 비율: {sum(1 for c in counts if c > 0) / len(counts) * 100:.1f}%")
    if saved_sample_frame:
        print(f"탐지 결과 샘플 이미지 저장: {OUTPUT_FRAME_PATH}")


if __name__ == "__main__":
    main()
