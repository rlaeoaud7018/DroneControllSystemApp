"""더미 영상 분석 결과 캐시 파일 입출력. sourceUri 기준으로 캐시를 공유한다."""

import hashlib
import json
import os

CACHE_DIR = os.path.join(os.path.dirname(os.path.dirname(__file__)), "cache")


def _cache_path(source_uri):
    key = hashlib.md5(source_uri.encode("utf-8")).hexdigest()
    return os.path.join(CACHE_DIR, f"{key}.json")


def has_cache(source_uri):
    return os.path.exists(_cache_path(source_uri))


def load_cache(source_uri):
    with open(_cache_path(source_uri), "r", encoding="utf-8") as f:
        return json.load(f)


def save_cache(source_uri, data):
    os.makedirs(CACHE_DIR, exist_ok=True)
    with open(_cache_path(source_uri), "w", encoding="utf-8") as f:
        json.dump(data, f)


def _frames_dir(source_uri):
    key = hashlib.md5(source_uri.encode("utf-8")).hexdigest()
    return os.path.join(CACHE_DIR, f"{key}_frames")


def frame_path(source_uri, frame_index):
    return os.path.join(_frames_dir(source_uri), f"{frame_index}.jpg")


def _frames_done_marker(source_uri):
    return os.path.join(_frames_dir(source_uri), "_done")


def has_frame_cache(source_uri):
    """라이브뷰용으로 미리 렌더링된(박스까지 합성된) 축소 프레임 JPEG들이 다 준비됐는지.
    디렉터리 존재만으로는 분석 도중 중단된 경우를 구분 못 하므로, 끝까지 다 쓴 뒤에만
    생기는 완료 마커 파일로 판단한다."""
    return os.path.exists(_frames_done_marker(source_uri))


def start_frame_cache(source_uri):
    """프레임 캐시 쓰기를 시작하기 전에 디렉터리를 준비한다."""
    frames_dir = _frames_dir(source_uri)
    os.makedirs(frames_dir, exist_ok=True)
    return frames_dir


def finish_frame_cache(source_uri):
    with open(_frames_done_marker(source_uri), "w") as f:
        f.write("done")
