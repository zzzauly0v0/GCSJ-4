-- =====================================================
-- GCSJ-4 逐日逐站气象地质灾害判别结果 schema (biz 层)
-- 定位: biz = 分析结果 (gis = 原始空间数据源)
-- 数据来源: gis.gis_weather_daily (2020-2023 逐日观测)
-- 回填脚本: 后端 POST /api/disaster-eval/run (DisasterEvalEngine, 依据 data/算法 .md)
-- 入库顺序: 03_gis_weather_schema.sql -> 04_disaster_eval_schema.sql
--           -> load_weather_stations.py -> POST /api/disaster-eval/run
-- 说明: 结果仅入本表, 不写 biz_disaster_event / biz_alert;
--       高等级(橙/红)日通过 POST /api/disaster-eval/push?date= 推 WebSocket /topic/disasters
-- =====================================================

SET search_path TO biz, gis, public, postgis;

DROP TABLE IF EXISTS biz.biz_disaster_eval CASCADE;

CREATE TABLE biz.biz_disaster_eval (
    id                 BIGSERIAL PRIMARY KEY,
    station_code       VARCHAR(32) NOT NULL,         -- 关联 gis.gis_weather_station.code / gis_weather_daily.station_code
    obs_date           DATE        NOT NULL,
    r_eff              NUMERIC(7,2),                 -- 前期有效降雨量 (15日衰减累加 α=0.85)
    dtr                NUMERIC(5,2),                 -- 气温日较差 Tmax - Tmin
    landslide_level    SMALLINT NOT NULL DEFAULT 0,  -- 降雨滑坡   0无 1蓝 2黄 3橙 4红
    mudslide_level     SMALLINT NOT NULL DEFAULT 0,  -- 降雨泥石流 0..4
    freezethaw_level   SMALLINT NOT NULL DEFAULT 0,  -- 冻融滑坡   0..4
    collapse_level     SMALLINT NOT NULL DEFAULT 0,  -- 坡面崩塌   0..4
    comp_level         SMALLINT NOT NULL DEFAULT 0,  -- 综合等级 = 4类取最高 (算法§六)
    comp_index         NUMERIC(4,3),                 -- 综合风险指数 H (算法§五 AHP 加权)
    UNIQUE (station_code, obs_date)
);
CREATE INDEX idx_deval_date    ON biz.biz_disaster_eval(obs_date);
CREATE INDEX idx_deval_station ON biz.biz_disaster_eval(station_code);
CREATE INDEX idx_deval_comp    ON biz.biz_disaster_eval(comp_level);
COMMENT ON TABLE biz.biz_disaster_eval IS '逐日逐站气象地质灾害判别结果 (算法 .md, 2020-2023)';
