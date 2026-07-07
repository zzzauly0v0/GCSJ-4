-- =====================================================
-- GCSJ-4 逐日逐站灾害判别结果 schema (biz 层)
-- 定位: biz = 分析结果 (gis = 原始空间数据源)
-- 数据来源: gis.gis_weather_daily (2020-2023 逐日观测) + data/dem_sc.csv / slope_sc.csv 地形栅格
-- 回填脚本: spatial_analyse/merged_disaster_eval.py (TRIGRS+CRI 滑坡 + 暴雨/高温/干旱, 幂等 UPSERT)
-- 入库顺序: 03_gis_weather_schema.sql -> 04_disaster_eval_schema.sql
--           -> load_weather_stations.py -> python merged_disaster_eval.py
-- 说明: 结果仅入本表, 不写 biz_disaster_event / biz_alert
-- 灾种: 滑坡(TRIGRS+CRI) / 暴雨 / 高温热浪 / 干旱 四类
-- 注: 列名沿用早期语义, 实际存储见每列注释 (landslide=CRI, mudslide=暴雨, freezethaw=高温, collapse=干旱)
-- =====================================================

SET search_path TO biz, gis, public, postgis;

DROP TABLE IF EXISTS biz.biz_disaster_eval CASCADE;

CREATE TABLE biz.biz_disaster_eval (
    id                 BIGSERIAL PRIMARY KEY,
    station_code       VARCHAR(32) NOT NULL,         -- 关联 gis.gis_weather_station.code / gis_weather_daily.station_code
    obs_date           DATE        NOT NULL,
    r_eff              NUMERIC(8,2),                 -- 有效累积降雨 (mm, ALPHA 衰减窗口 WINDOW 天)
    dtr                NUMERIC(5,2),                 -- 日较差 = temp_max - temp_min (°C)
    landslide_level    SMALLINT NOT NULL DEFAULT 0,  -- 滑坡 (TRIGRS Fs + CRI 综合风险指数) 0无 1低 2中 3高 4极高
    mudslide_level     SMALLINT NOT NULL DEFAULT 0,  -- 暴雨 (GB/T 28592-2012 ≥25/50/100/250mm) 0..4
    freezethaw_level   SMALLINT NOT NULL DEFAULT 0,  -- 高温热浪 (GB/T 20481-2017 ≥33/35/37/40°C) 0..4
    collapse_level     SMALLINT NOT NULL DEFAULT 0,  -- 干旱 (干/湿季分档累计降雨阈值) 0..4
    comp_level         SMALLINT NOT NULL DEFAULT 0,  -- 综合等级 = 四类取最高
    UNIQUE (station_code, obs_date)
);
CREATE INDEX idx_deval_date    ON biz.biz_disaster_eval(obs_date);
CREATE INDEX idx_deval_station ON biz.biz_disaster_eval(station_code);
CREATE INDEX idx_deval_comp    ON biz.biz_disaster_eval(comp_level);
COMMENT ON TABLE biz.biz_disaster_eval IS '逐日逐站灾害判别结果 (滑坡/暴雨/高温/干旱 四类, 2020-2023)';
