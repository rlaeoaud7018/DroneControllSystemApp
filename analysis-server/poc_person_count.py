"""
analysis-server 최소 PoC.

목적: OpenCV 기본 내장 HOG 사람 탐지기로 영상에서 사람 수를 셀 수 있는지 확인.
외부 모델 파일(YOLO 가중치 등)을 추가로 받지 않고, opencv-python에 내장된
HOGDescriptor + getDefaultPeopleDetector()만으로 동작한다 (다운로드 불필요).

실행: python poc_person_count.py
"""

import os

import cv2

VIDEO_PATH = os.path.join(os.path.dirname(__file__), "sample_videos", "vtest.avi")
OUTPUT_FRAME_PATH = os.path.join(os.path.dirname(__file__), "sample_videos", "poc_detected_frame.jpg")


def main():
    hog = cv2.HOGDescriptor()
    hog.setSVMDetector(cv2.HOGDescriptor_getDefaultPeopleDetector())

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
        # 매 5프레임마다만 분석 (HOG가 느려서 전체 프레임을 다 돌리면 PoC치고 오래 걸림)
        if frame_index % 5 != 0:
            continue

        resized = cv2.resize(frame, (640, 360))
        boxes, weights = hog.detectMultiScale(
            resized, winStride=(8, 8), padding=(8, 8), scale=1.05
        )

        counts.append(len(boxes))

        if not saved_sample_frame and len(boxes) > 0:
            for (x, y, w, h) in boxes:
                cv2.rectangle(resized, (x, y), (x + w, y + h), (0, 255, 0), 2)
            cv2.putText(
                resized, f"person count: {len(boxes)}", (10, 25),
                cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0, 255, 0), 2,
            )
            cv2.imwrite(OUTPUT_FRAME_PATH, resized)
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
