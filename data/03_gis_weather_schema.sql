-- =====================================================
-- GCSJ-4 气象站点原始数据 schema (gis 层)
-- 定位: gis = 原始空间数据源 (biz = 分析结果)
-- 数据来源: data/四川气象站点_2020.csv ~ 2023.csv (逐日, GBK 编码)
-- 入库脚本: spatial_analyse/load_weather_stations.py
-- 入库顺序:
--   psql -d gcsj -f data/init.sql
--   psql -d gcsj -f data/02_sichuan_schema.sql
--   python spatial_analyse/load_sichuan_boundary.py
--   psql -d gcsj -f data/03_gis_weather_schema.sql
--   psql -d gcsj -f data/04_disaster_eval_schema.sql
--   python spatial_analyse/load_weather_stations.py
-- =====================================================

SET search_path TO biz, sys, gis, public, postgis;

DROP TABLE IF EXISTS gis.gis_weather_daily   CASCADE;
DROP TABLE IF EXISTS gis.gis_weather_station CASCADE;

-- =========================== 气象站点元数据 ============================
-- 站点位置 + 高程 + 所在区县 (region_code 由脚本按 ST_Contains 反查回填)
CREATE TABLE gis.gis_weather_station (
    id            BIGSERIAL PRIMARY KEY,
    code          VARCHAR(32) UNIQUE NOT NULL,        -- 区站号
    name          VARCHAR(128) NOT NULL,              -- 站名
    location      geometry(Point, 4326) NOT NULL,     -- 经纬度
    elevation     NUMERIC(7,2),                       -- 测站高度 m
    region_code   VARCHAR(12),                        -- 所在区县 adcode (A2 回填)
    year_coverage VARCHAR(64),                        -- 数据覆盖年份, 如 "2020,2021,2022,2023" (A3)
    record_count  INTEGER                             -- 该站日观测记录总数 (A3)
);
COMMENT ON TABLE gis.gis_weather_station IS '气象站点元数据 (原始, 2020-2023)';
CREATE INDEX idx_wstation_loc    ON gis.gis_weather_station USING GIST (location);
CREATE INDEX idx_wstation_region ON gis.gis_weather_station(region_code);

-- =========================== 气象日观测时序 ============================
CREATE TABLE gis.gis_weather_daily (
    id            BIGSERIAL PRIMARY KEY,
    station_code  VARCHAR(32) NOT NULL,       -- 区站号 (关联 gis_weather_station.code)
    obs_date      DATE        NOT NULL,       -- 由 年/月/日 组合
    temp_avg      NUMERIC(5,2),               -- 平均气温 ℃
    temp_max      NUMERIC(5,2),               -- 最高气温 ℃
    temp_min      NUMERIC(5,2),               -- 最低气温 ℃
    rh_avg        NUMERIC(5,2),               -- 平均相对湿度 %
    rh_min        NUMERIC(5,2),               -- 最小相对湿度 %
    pressure_avg  NUMERIC(7,2),               -- 平均气压 hPa
    pressure_max  NUMERIC(7,2),               -- 最高本站气压 hPa
    pressure_min  NUMERIC(7,2),               -- 最低本站气压 hPa
    wind_avg      NUMERIC(5,2),               -- 平均 2 分钟风速 m/s
    wind_max      NUMERIC(5,2),               -- 极大风速 m/s
    rainfall      NUMERIC(7,2),               -- 20-20 时降水量 mm
    UNIQUE (station_code, obs_date)           -- 幂等: 重复跑只更新
);
COMMENT ON TABLE gis.gis_weather_daily IS '气象站点日观测时序 (原始, 2020-2023)';
CREATE INDEX idx_wdaily_date    ON gis.gis_weather_daily(obs_date);
CREATE INDEX idx_wdaily_station ON gis.gis_weather_daily(station_code);
