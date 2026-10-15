-- 드론관제시스템 DB 스키마 초안
-- 요구사항명세서 / 기능명세서 / API명세서 기준으로 작성
-- MySQL 8 기준. 실제 사용 전 팀 리뷰 필요.

CREATE DATABASE IF NOT EXISTS db_dronecontrol
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE db_dronecontrol;

CREATE TABLE users (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    login_id          VARCHAR(50)  NOT NULL UNIQUE,
    password_encoded  VARCHAR(255) NOT NULL,
    email             VARCHAR(100) NOT NULL,
    phone             VARCHAR(20),
    name              VARCHAR(50)  NOT NULL,
    role              ENUM('PRE_USER', 'USER', 'ADMIN', 'SUPER_ADMIN') NOT NULL DEFAULT 'PRE_USER',
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
-- role 'GUEST'(비회원)는 로그인하지 않은 상태를 의미하므로 DB에 행을 만들지 않음 (perm-001~005)
-- SUPER_ADMIN은 서버 최초 실행 시 1회 자동 등록 (perm-005)

CREATE TABLE regions (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_id   BIGINT NULL,
    level       ENUM('CITY', 'GU', 'DONG') NOT NULL,
    code        VARCHAR(20) NOT NULL,
    name        VARCHAR(50) NOT NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_regions_parent FOREIGN KEY (parent_id) REFERENCES regions(id),
    UNIQUE KEY uq_region_code (code)
);

CREATE TABLE zones (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    dong_id     BIGINT NOT NULL,
    name        VARCHAR(50) NOT NULL,
    area_m2     DECIMAL(10, 2) NOT NULL,
    geometry    TEXT NOT NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_zones_dong FOREIGN KEY (dong_id) REFERENCES regions(id),
    UNIQUE KEY uq_zone_name_per_dong (dong_id, name)
);

CREATE TABLE zone_neighbors (
    zone_id     BIGINT NOT NULL,
    direction   ENUM('N', 'E', 'S', 'W') NOT NULL,
    neighbor_id BIGINT NOT NULL,
    PRIMARY KEY (zone_id, direction),
    CONSTRAINT fk_neighbor_zone FOREIGN KEY (zone_id) REFERENCES zones(id),
    CONSTRAINT fk_neighbor_target FOREIGN KEY (neighbor_id) REFERENCES zones(id)
);

CREATE TABLE cameras (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    zone_id        BIGINT NOT NULL,
    name           VARCHAR(50) NOT NULL,
    source_type    ENUM('LIVE', 'DUMMY') NOT NULL,
    source_uri     VARCHAR(255) NOT NULL,
    status         ENUM('STOPPED', 'PREPARING', 'RUNNING', 'ERROR') NOT NULL DEFAULT 'STOPPED',
    progress       TINYINT UNSIGNED NOT NULL DEFAULT 0,
    error_message  VARCHAR(255) NULL,
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_cameras_zone FOREIGN KEY (zone_id) REFERENCES zones(id)
);

-- 분석서버가 초단위로 보낸 결과를 1분 단위로 집계 (stats-003~007, stupdate-001)
CREATE TABLE zone_stats_minute (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    zone_id         BIGINT NOT NULL,
    at              DATETIME NOT NULL,
    count_avg       DECIMAL(8, 2) NOT NULL,
    count_max       INT NOT NULL,
    count_min       INT NOT NULL,
    density         DECIMAL(8, 3) NOT NULL,
    inflow_n        INT NOT NULL DEFAULT 0,
    inflow_e        INT NOT NULL DEFAULT 0,
    inflow_s        INT NOT NULL DEFAULT 0,
    inflow_w        INT NOT NULL DEFAULT 0,
    outflow_n       INT NOT NULL DEFAULT 0,
    outflow_e       INT NOT NULL DEFAULT 0,
    outflow_s       INT NOT NULL DEFAULT 0,
    outflow_w       INT NOT NULL DEFAULT 0,
    dwell_avg_sec   INT NOT NULL DEFAULT 0,
    sample_count    INT NOT NULL DEFAULT 0,
    -- 이동 분석(속도/방향). 방향은 카메라 픽셀 프레임 기준 8방향이며 실제 지리적 방위가 아니다.
    avg_speed_px_s  DECIMAL(10, 2) NOT NULL DEFAULT 0,
    move_e          INT NOT NULL DEFAULT 0,
    move_ne         INT NOT NULL DEFAULT 0,
    move_n          INT NOT NULL DEFAULT 0,
    move_nw         INT NOT NULL DEFAULT 0,
    move_w          INT NOT NULL DEFAULT 0,
    move_sw         INT NOT NULL DEFAULT 0,
    move_s          INT NOT NULL DEFAULT 0,
    move_se         INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_stats_minute_zone FOREIGN KEY (zone_id) REFERENCES zones(id),
    UNIQUE KEY uq_zone_minute (zone_id, at)
);

-- 전날 1분 통계를 구역별로 묶은 일별 통계 (stats-001 일별 집계)
CREATE TABLE zone_stats_daily (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    zone_id         BIGINT NOT NULL,
    date            DATE NOT NULL,
    count_avg       DECIMAL(8, 2) NOT NULL,
    count_max       INT NOT NULL,
    count_min       INT NOT NULL,
    density_avg     DECIMAL(8, 3) NOT NULL,
    inflow_total    INT NOT NULL DEFAULT 0,
    outflow_total   INT NOT NULL DEFAULT 0,
    dwell_avg_sec   INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_stats_daily_zone FOREIGN KEY (zone_id) REFERENCES zones(id),
    UNIQUE KEY uq_zone_date (zone_id, date)
);

-- 구역 간 이동 (stats-006). zone_stats_minute의 outflow_n/e/s/w + zone_neighbors로 분단위 집계.
CREATE TABLE zone_flows (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    from_zone_id BIGINT NOT NULL,
    to_zone_id   BIGINT NOT NULL,
    at           DATETIME NOT NULL,
    count        INT NOT NULL,
    CONSTRAINT fk_flow_from FOREIGN KEY (from_zone_id) REFERENCES zones(id),
    CONSTRAINT fk_flow_to FOREIGN KEY (to_zone_id) REFERENCES zones(id),
    UNIQUE KEY uq_flow_minute (from_zone_id, to_zone_id, at)
);

-- 혼잡 단계 임계치 설정 (evthres-001~003)
CREATE TABLE event_thresholds (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    zone_id               BIGINT NOT NULL,
    level_no              TINYINT UNSIGNED NOT NULL,
    density_threshold     DECIMAL(8, 3) NOT NULL,
    surge_threshold_pct   DECIMAL(5, 2) NULL,
    surge_compare_minutes INT NULL,
    created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_threshold_zone FOREIGN KEY (zone_id) REFERENCES zones(id),
    UNIQUE KEY uq_zone_level (zone_id, level_no)
);

-- 이벤트 (evthis, evtalert, evtbasis, evthand)
CREATE TABLE events (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    zone_id                 BIGINT NOT NULL,
    camera_id               BIGINT NULL,
    level_no                TINYINT UNSIGNED NOT NULL,
    occurred_at             DATETIME NOT NULL,
    density_at_event        DECIMAL(8, 3) NOT NULL,
    threshold_density       DECIMAL(8, 3) NOT NULL,
    threshold_surge_pct     DECIMAL(5, 2) NULL,
    status                  ENUM('UNCONFIRMED', 'CONFIRMED', 'RESOLVED') NOT NULL DEFAULT 'UNCONFIRMED',
    handled_by              BIGINT NULL,
    handled_at              DATETIME NULL,
    memo                    VARCHAR(255) NULL,
    created_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_events_zone FOREIGN KEY (zone_id) REFERENCES zones(id),
    CONSTRAINT fk_events_camera FOREIGN KEY (camera_id) REFERENCES cameras(id),
    CONSTRAINT fk_events_handler FOREIGN KEY (handled_by) REFERENCES users(id)
);

-- 녹화 파일 (repfocus-001, DUMMY 캠 재생용 원본 포함)
CREATE TABLE recordings (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    camera_id   BIGINT NOT NULL,
    started_at  DATETIME NOT NULL,
    file_path   VARCHAR(255) NOT NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_recordings_camera FOREIGN KEY (camera_id) REFERENCES cameras(id)
);

-- 관리자 작업 기록 (histre, histwr)
CREATE TABLE work_history (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_id    BIGINT NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    target      VARCHAR(100) NOT NULL,
    detail      TEXT NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_history_admin FOREIGN KEY (admin_id) REFERENCES users(id)
);
