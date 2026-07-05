-- =====================================================
-- GCSJ-4 逐日逐站气象灾害判别结果 schema (biz 层)
-- 定位: biz = 分析结果 (gis = 原始空间数据源)
-- 数据来源: gis.gis_weather_daily (2020-2023 逐日观测)
-- 回填脚本: 后端 POST /api/disaster-eval/run (DisasterEvalEngine)
-- 入库顺序: 03_gis_weather_schema.sql -> 04_disaster_eval_schema.sql
--           -> load_weather_stations.py -> POST /api/disaster-eval/run
-- 说明: 结果仅入本表, 不写 biz_disaster_event / biz_alert;
--       高等级(橙/红)日通过 POST /api/disaster-eval/push?date= 推 WebSocket /topic/disasters
-- 灾种: 暴雨/高温热浪/寒潮/干旱/森林火险 五类气象灾害
-- =====================================================

SET search_path TO biz, gis, public, postgis;

DROP TABLE IF EXISTS biz.biz_disaster_eval CASCADE;

CREATE TABLE biz.biz_disaster_eval (
    id                 BIGSERIAL PRIMARY KEY,
    station_code       VARCHAR(32) NOT NULL,         -- 关联 gis.gis_weather_station.code / gis_weather_daily.station_code
    obs_date           DATE        NOT NULL,
    rainstorm_level    SMALLINT NOT NULL DEFAULT 0,  -- 暴雨 (GB/T 28592-2012)   0无 1蓝 2黄 3橙 4红
    heatwave_level     SMALLINT NOT NULL DEFAULT 0,  -- 高温热浪 (GB/T 20481-2017) 0..4
    coldwave_level     SMALLINT NOT NULL DEFAULT 0,  -- 寒潮 (GB/T 21987-2017)   0..4
    drought_level      SMALLINT NOT NULL DEFAULT 0,  -- 干旱 (GB/T 20481-2017)   0..4
    fire_risk_level    SMALLINT NOT NULL DEFAULT 0,  -- 森林火险 (LY/T 1172-95) 0..4
    comp_level         SMALLINT NOT NULL DEFAULT 0,  -- 综合等级 = 五类取最高
    comp_index         NUMERIC(4,3),                 -- 综合风险指数 = comp_level / 4.0
    UNIQUE (station_code, obs_date)
);
CREATE INDEX idx_deval_date    ON biz.biz_disaster_eval(obs_date);
CREATE INDEX idx_deval_station ON biz.biz_disaster_eval(station_code);
CREATE INDEX idx_deval_comp    ON biz.biz_disaster_eval(comp_level);
COMMENT ON TABLE biz.biz_disaster_eval IS '逐日逐站气象灾害判别结果 (五类气象灾害, 2020-2023)';
