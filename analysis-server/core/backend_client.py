"""backend-api의 /internal/v1/* 엔드포인트를 호출하는 얇은 클라이언트."""

import os

import requests

BACKEND_BASE_URL = os.environ.get("BACKEND_BASE_URL", "http://localhost:8080/backend-api")


def report_camera_status(camera_id, status, progress=0, error_message=None):
    url = f"{BACKEND_BASE_URL}/internal/v1/cameras/{camera_id}/status"
    body = {"status": status, "progress": progress, "errorMessage": error_message}
    try:
        resp = requests.patch(url, json=body, timeout=5)
        if resp.status_code >= 300:
            print(f"[backend_client] 상태 보고 실패 (camera={camera_id}): HTTP {resp.status_code} {resp.text}")
    except requests.RequestException as e:
        print(f"[backend_client] 상태 보고 실패 (camera={camera_id}): {e}")


def send_stats(samples):
    """samples: StatSampleDto 형태의 dict 리스트."""
    if not samples:
        return
    url = f"{BACKEND_BASE_URL}/internal/v1/stats"
    try:
        resp = requests.post(url, json=samples, timeout=5)
        if resp.status_code >= 300:
            print(f"[backend_client] 통계 전송 실패: HTTP {resp.status_code} {resp.text}")
    except requests.RequestException as e:
        print(f"[backend_client] 통계 전송 실패: {e}")
