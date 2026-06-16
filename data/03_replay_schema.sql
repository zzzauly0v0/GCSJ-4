-- =====================================================
-- GCSJ-4 时间轴回放数据 schema
-- 设计原则: 数据多, 计算少 — 预警/事件由 Python 离线算好直接灌入现有 biz_alert/biz_disaster_event
-- 新增表只做时序数据载体, 不需要 Java 规则引擎
-- 入库顺序:
--   psql -d gcsj -f data/03_replay_schema.sql
--   python spatial_analyse/replay_data_gen.py
-- =====================================================

SET search_path TO biz, gis, public, postgis;

-- 清理重建
DROP TABLE IF EXISTS biz.biz_geo_sensor_reading CASCADE;
DROP TABLE IF EXISTS biz.biz_weather_observation CASCADE;
DROP TABLE IF EXISTS biz.biz_earthquake_event CASCADE;
DROP TABLE IF EXISTS biz.biz_monitor_station CASCADE;

-- =========================== 监测站元数据 ============================
-- 一张表同时承载气象站和地质传感器点 (用 type 区分)
CREATE TABLE biz.biz_monitor_station (
    id           BIGSERIAL PRIMARY KEY,
    code         VARCHAR(32) UNIQUE NOT NULL,
    name         VARCHAR(128) NOT NULL,
    type         VARCHAR(16) NOT NULL,            -- weather / geo
    region_code  VARCHAR(12),                     -- 所在区县 adcode
    location     geometry(Point, 4326) NOT NULL,
    elevation    NUMERIC(7,2),
    install_date DATE,
    status       SMALLINT DEFAULT 1               -- 1 在线 / 0 离线
);
CREATE INDEX idx_station_type   ON biz.biz_monitor_station(type);
CREATE INDEX idx_station_region ON biz.biz_monitor_station(region_code);
CREATE INDEX idx_station_loc    ON biz.biz_monitor_station USING GIST(location);

-- =========================== 气象观测时序 ============================
-- 1 小时一条, 30 站 × 72 小时 ≈ 2160 行
CREATE TABLE biz.biz_weather_observation (
    id            BIGSERIAL PRIMARY KEY,
    station_code  VARCHAR(32) NOT NULL,
    observed_at   TIMESTAMPTZ NOT NULL,
    rainfall_1h   NUMERIC(6,2),                   -- mm/h
    rainfall_24h  NUMERIC(7,2),                   -- 滚动 24h 累计
    temperature   NUMERIC(5,2),
    humidity      NUMERIC(5,2),
    wind_speed    NUMERIC(5,2),
    pressure      NUMERIC(7,2)
);
CREATE INDEX idx_weather_time    ON biz.biz_weather_observation(observed_at DESC);
CREATE INDEX idx_weather_station ON biz.biz_weather_observation(station_code);
COMMENT ON TABLE biz.biz_weather_observation IS '气象观测时序 (回放)';

-- =========================== 地质传感器时序 ============================
-- 10 分钟一条, 50 点 × 4 指标 × 432 步 ≈ 86400 行
CREATE TABLE biz.biz_geo_sensor_reading (
    id            BIGSERIAL PRIMARY KEY,
    station_code  VARCHAR(32) NOT NULL,
    observed_at   TIMESTAMPTZ NOT NULL,
    metric_type   VARCHAR(24) NOT NULL,           -- displacement / soil_moisture / tilt / crack
    value         NUMERIC(10,4) NOT NULL,
    unit          VARCHAR(16),
    is_anomaly    BOOLEAN DEFAULT FALSE
);
CREATE INDEX idx_sensor_time    ON biz.biz_geo_sensor_reading(observed_at DESC);
CREATE INDEX idx_sensor_station ON biz.biz_geo_sensor_reading(station_code);
CREATE INDEX idx_sensor_metric  ON biz.biz_geo_sensor_reading(metric_type);
COMMENT ON TABLE biz.biz_geo_sensor_reading IS '地质传感器时序 (回放)';

-- =========================== 地震事件 ============================
CREATE TABLE biz.biz_earthquake_event (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(64) UNIQUE NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    magnitude   NUMERIC(3,1) NOT NULL,
    depth_km    NUMERIC(5,1),
    epicenter   geometry(Point, 4326) NOT NULL,
    location_name VARCHAR(128),
    region_code VARCHAR(12)
);
CREATE INDEX idx_quake_time   ON biz.biz_earthquake_event(occurred_at DESC);
CREATE INDEX idx_quake_loc    ON biz.biz_earthquake_event USING GIST(epicenter);
CREATE INDEX idx_quake_region ON biz.biz_earthquake_event(region_code);
COMMENT ON TABLE biz.biz_earthquake_event IS '地震事件 (回放)';

-- =========================== 视图: 站点 + 行政区 ============================
CREATE OR REPLACE VIEW biz.v_station_with_region AS
SELECT s.id, s.code, s.name, s.type, s.location,
       s.region_code, r.name AS region_name
FROM   biz.biz_monitor_station s
LEFT JOIN gis.gis_admin_region r ON r.adcode = s.region_code;
