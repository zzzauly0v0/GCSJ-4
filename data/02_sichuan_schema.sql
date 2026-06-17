-- =====================================================
-- GCSJ-4 四川省专题数据扩展
-- 依赖: 先执行 init.sql (含 postgis 扩展、sys/biz/gis schema、gis_layer/biz_disaster_event 等基础表)
-- 范围: 仅四川省, 数据精简, 重点体现 PostGIS 空间分析能力
-- 入库顺序:
--   1. psql -d gcsj -f data/init.sql
--   2. psql -d gcsj -f data/02_sichuan_schema.sql
--   3. python data/load_sichuan_boundary.py    (拉阿里 DataV 行政区入 gis_admin_region)
--   4. (可选) 重新执行本文件末尾的 UPDATE 回填 region_code
-- =====================================================

SET search_path TO biz, sys, gis, public, postgis;

-- =========================== 清理 (重复执行用) ============================
DROP VIEW IF EXISTS biz.v_event_by_county CASCADE;
DROP VIEW IF EXISTS biz.v_event_river_distance CASCADE;
DROP VIEW IF EXISTS biz.v_settlement_at_risk CASCADE;

DROP TABLE IF EXISTS gis.gis_settlement CASCADE;
DROP TABLE IF EXISTS gis.gis_river CASCADE;
DROP TABLE IF EXISTS gis.gis_admin_region CASCADE;

-- =========================== 行政区 ============================
-- 三级合一 (省/市/县), 通过 level + parent_code 形成树
-- boundary 由 load_sichuan_boundary.py 从阿里 DataV JSON 入库
CREATE TABLE gis.gis_admin_region (
    id          BIGSERIAL PRIMARY KEY,
    adcode      VARCHAR(12) UNIQUE NOT NULL,           -- 国标行政区划代码
    name        VARCHAR(64) NOT NULL,
    level       SMALLINT NOT NULL,                     -- 1 省 2 市/州 3 县/区
    parent_code VARCHAR(12),                           -- 上级 adcode (省的 parent_code 为 NULL)
    center      geometry(Point, 4326),                 -- 行政中心 (供前端标注)
    boundary    geometry(MultiPolygon, 4326),          -- 边界
    area_km2    NUMERIC(12,2)                          -- 入库时预算, 减少 ST_Area 调用
);
COMMENT ON TABLE gis.gis_admin_region IS '行政区划 (省/市/县三级)';
CREATE INDEX idx_admin_boundary ON gis.gis_admin_region USING GIST (boundary);
CREATE INDEX idx_admin_center   ON gis.gis_admin_region USING GIST (center);
CREATE INDEX idx_admin_level    ON gis.gis_admin_region(level);
CREATE INDEX idx_admin_parent   ON gis.gis_admin_region(parent_code);

-- =========================== 河流 ============================
CREATE TABLE gis.gis_river (
    id        BIGSERIAL PRIMARY KEY,
    name      VARCHAR(128) NOT NULL,
    grade     SMALLINT,                                 -- 1 干流 2 主要支流 3 一般
    geom      geometry(LineString, 4326) NOT NULL,
    length_km NUMERIC(10,2)
);
COMMENT ON TABLE gis.gis_river IS '主要河流 (LineString)';
CREATE INDEX idx_river_geom ON gis.gis_river USING GIST (geom);

-- =========================== 居民点 ============================
CREATE TABLE gis.gis_settlement (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(128) NOT NULL,
    type       VARCHAR(32),                             -- city/county/town
    population INT,                                     -- 人口 (万)
    geom       geometry(Point, 4326) NOT NULL
);
COMMENT ON TABLE gis.gis_settlement IS '居民点 (城市/县城/重点乡镇)';
CREATE INDEX idx_settle_geom ON gis.gis_settlement USING GIST (geom);
CREATE INDEX idx_settle_type ON gis.gis_settlement(type);

-- =========================== biz_disaster_event 扩展字段 ============================
-- 不破坏原表, 只 ADD 新字段
ALTER TABLE biz.biz_disaster_event
    ADD COLUMN IF NOT EXISTS region_code VARCHAR(12),    -- 落在哪个县 (回填)
    ADD COLUMN IF NOT EXISTS elevation   NUMERIC(7,2),   -- 高程 m (DEM 提取, 当前 seed 手填)
    ADD COLUMN IF NOT EXISTS slope_deg   NUMERIC(5,2);   -- 坡度 deg

CREATE INDEX IF NOT EXISTS idx_event_region ON biz.biz_disaster_event(region_code);

-- =========================== 河流 seed (四川六大水系) ============================
-- 坐标为简化路径关键节点, 仅作展示, 非测绘精度
INSERT INTO gis.gis_river (name, grade, geom) VALUES
 ('长江',   1, ST_GeomFromText('LINESTRING(104.642 28.752, 105.443 28.872, 106.260 28.700, 106.633 29.553)', 4326)),
 ('岷江',   1, ST_GeomFromText('LINESTRING(103.220 32.766, 103.591 31.473, 103.620 30.999, 104.066 30.572, 103.762 29.582, 104.642 28.752)', 4326)),
 ('大渡河', 2, ST_GeomFromText('LINESTRING(101.964 30.050, 102.230 29.910, 102.360 29.230, 103.000 29.160, 103.762 29.582)', 4326)),
 ('雅砻江', 2, ST_GeomFromText('LINESTRING(101.018 30.043, 101.717 28.310, 101.717 26.580, 101.800 26.560)', 4326)),
 ('嘉陵江', 1, ST_GeomFromText('LINESTRING(105.829 32.434, 106.083 30.795, 106.260 30.310, 106.550 29.570)', 4326)),
 ('沱江',   2, ST_GeomFromText('LINESTRING(104.398 31.127, 104.780 30.160, 104.780 29.340, 104.780 28.870, 105.443 28.872)', 4326));

-- 用 geography 算长度 (单位 m), 转 km
UPDATE gis.gis_river SET length_km = ROUND((ST_Length(geom::geography) / 1000)::numeric, 2);

-- =========================== 居民点 seed ============================
INSERT INTO gis.gis_settlement (name, type, population, geom) VALUES
 -- 主要城市
 ('成都',   'city',   2126, ST_SetSRID(ST_MakePoint(104.066, 30.572), 4326)),
 ('绵阳',   'city',    487, ST_SetSRID(ST_MakePoint(104.741, 31.464), 4326)),
 ('德阳',   'city',    345, ST_SetSRID(ST_MakePoint(104.398, 31.127), 4326)),
 ('乐山',   'city',    314, ST_SetSRID(ST_MakePoint(103.762, 29.582), 4326)),
 ('宜宾',   'city',    458, ST_SetSRID(ST_MakePoint(104.642, 28.752), 4326)),
 ('泸州',   'city',    425, ST_SetSRID(ST_MakePoint(105.443, 28.872), 4326)),
 ('南充',   'city',    560, ST_SetSRID(ST_MakePoint(106.083, 30.795), 4326)),
 ('达州',   'city',    538, ST_SetSRID(ST_MakePoint(107.502, 31.215), 4326)),
 ('广元',   'city',    230, ST_SetSRID(ST_MakePoint(105.829, 32.434), 4326)),
 ('雅安',   'city',    143, ST_SetSRID(ST_MakePoint(103.001, 29.987), 4326)),
 ('攀枝花', 'city',    121, ST_SetSRID(ST_MakePoint(101.717, 26.580), 4326)),
 ('西昌',   'city',     93, ST_SetSRID(ST_MakePoint(102.258, 27.886), 4326)),
 -- 川西高山地灾高发带的县城和重点乡镇
 ('汶川县城', 'county', 10, ST_SetSRID(ST_MakePoint(103.591, 31.473), 4326)),
 ('北川县城', 'county',  8, ST_SetSRID(ST_MakePoint(104.464, 31.831), 4326)),
 ('茂县县城', 'county', 11, ST_SetSRID(ST_MakePoint(103.853, 31.681), 4326)),
 ('理县县城', 'county',  4, ST_SetSRID(ST_MakePoint(103.166, 31.435), 4326)),
 ('松潘县城', 'county',  7, ST_SetSRID(ST_MakePoint(103.604, 32.640), 4326)),
 ('九寨沟县', 'county',  6, ST_SetSRID(ST_MakePoint(104.236, 33.262), 4326)),
 ('都江堰',   'town',   68, ST_SetSRID(ST_MakePoint(103.620, 30.999), 4326)),
 ('映秀镇',   'town',    1, ST_SetSRID(ST_MakePoint(103.487, 31.066), 4326)),
 ('泸定县城', 'county',  8, ST_SetSRID(ST_MakePoint(102.235, 29.915), 4326)),
 ('康定',     'county', 13, ST_SetSRID(ST_MakePoint(101.964, 30.050), 4326)),
 ('汉源县城', 'county', 15, ST_SetSRID(ST_MakePoint(102.679, 29.350), 4326));

-- =========================== 灾害事件 seed ============================
-- 清理 init.sql 里那条北京怀柔的样例 (跑两次脚本时不重复)
DELETE FROM biz.biz_alert            WHERE code = 'A20260518001';
DELETE FROM biz.biz_disaster_event   WHERE code = 'E20260518001';

-- 15 条覆盖川西高山地灾带的事件 (含进行中/处置中/已结束三种状态)
INSERT INTO biz.biz_disaster_event
    (code, title, type, level, location, affected_area, occurred_at, end_at, status, description, reporter_id, elevation, slope_deg) VALUES
 ('SC20260601001','汶川县映秀镇山体滑坡','landslide',3,
  ST_SetSRID(ST_MakePoint(103.487, 31.066), 4326),
  ST_GeomFromText('POLYGON((103.483 31.062, 103.491 31.062, 103.491 31.070, 103.483 31.070, 103.483 31.062))', 4326),
  now() - interval '2 hour', NULL, 1, '映秀镇后山岩体松动, 已封闭G213国道部分路段', 2, 1320.0, 35.0),

 ('SC20260601002','北川羌族自治县擂鼓镇泥石流','debris_flow',3,
  ST_SetSRID(ST_MakePoint(104.464, 31.831), 4326),
  ST_GeomFromText('POLYGON((104.460 31.827, 104.470 31.827, 104.470 31.835, 104.460 31.835, 104.460 31.827))', 4326),
  now() - interval '5 hour', NULL, 2, '连续暴雨触发沟道泥石流, 已转移群众 320 人', 2, 985.0, 28.0),

 ('SC20260530001','茂县叠溪镇山体滑坡','landslide',4,
  ST_SetSRID(ST_MakePoint(103.690, 32.050), 4326),
  ST_GeomFromText('POLYGON((103.685 32.045, 103.695 32.045, 103.695 32.055, 103.685 32.055, 103.685 32.045))', 4326),
  now() - interval '10 day', now() - interval '6 day', 3, '高位远程滑坡, 已完成搜救并转入地质监测', 2, 2380.0, 42.0),

 ('SC20260528001','理县通化乡崩塌','collapse',2,
  ST_SetSRID(ST_MakePoint(103.166, 31.435), 4326),
  NULL, now() - interval '12 day', now() - interval '11 day', 3, '岷江沿岸岩石崩塌, 影响 G317 国道', 2, 1820.0, 50.0),

 ('SC20260525001','松潘县白羊乡泥石流','debris_flow',2,
  ST_SetSRID(ST_MakePoint(103.604, 32.640), 4326),
  ST_GeomFromText('POLYGON((103.600 32.636, 103.610 32.636, 103.610 32.644, 103.600 32.644, 103.600 32.636))', 4326),
  now() - interval '15 day', now() - interval '13 day', 3, '夏季强降雨过程, 已清淤完成', 2, 2780.0, 30.0),

 ('SC20260520001','九寨沟县漳扎镇滑坡','landslide',3,
  ST_SetSRID(ST_MakePoint(103.920, 33.270), 4326),
  ST_GeomFromText('POLYGON((103.916 33.266, 103.924 33.266, 103.924 33.274, 103.916 33.274, 103.916 33.266))', 4326),
  now() - interval '20 day', now() - interval '18 day', 3, '景区周边山体滑坡, 临时关闭部分线路', NULL, 2440.0, 38.0),

 ('SC20260601003','雅安芦山县龙门乡滑坡','landslide',2,
  ST_SetSRID(ST_MakePoint(102.930, 30.140), 4326),
  NULL, now() - interval '8 hour', NULL, 1, '小规模滑坡, 监测中', 2, 1180.0, 32.0),

 ('SC20260530002','都江堰三溪村山体滑坡','landslide',2,
  ST_SetSRID(ST_MakePoint(103.450, 30.950), 4326),
  ST_GeomFromText('POLYGON((103.446 30.946, 103.454 30.946, 103.454 30.954, 103.446 30.954, 103.446 30.946))', 4326),
  now() - interval '8 day', now() - interval '6 day', 3, '局部小型滑坡, 已加固', 2, 980.0, 25.0),

 ('SC20260518002','泸定县磨西镇滑坡','landslide',3,
  ST_SetSRID(ST_MakePoint(102.230, 29.910), 4326),
  ST_GeomFromText('POLYGON((102.226 29.906, 102.234 29.906, 102.234 29.914, 102.226 29.914, 102.226 29.906))', 4326),
  now() - interval '22 day', now() - interval '20 day', 3, '海螺沟景区周边滑坡', 2, 1560.0, 40.0),

 ('SC20260515001','康定折多山泥石流','debris_flow',2,
  ST_SetSRID(ST_MakePoint(101.860, 30.050), 4326),
  NULL, now() - interval '25 day', now() - interval '23 day', 3, '高海拔强降雨触发', 2, 4290.0, 28.0),

 ('SC20260510001','汉源县崩塌','collapse',2,
  ST_SetSRID(ST_MakePoint(102.679, 29.350), 4326),
  NULL, now() - interval '30 day', now() - interval '29 day', 3, '大渡河沿岸岩崩', NULL, 1140.0, 55.0),

 ('SC20260505001','平武县白马乡泥石流','debris_flow',3,
  ST_SetSRID(ST_MakePoint(104.530, 32.410), 4326),
  ST_GeomFromText('POLYGON((104.526 32.406, 104.534 32.406, 104.534 32.414, 104.526 32.414, 104.526 32.406))', 4326),
  now() - interval '35 day', now() - interval '32 day', 3, '岷江上游支流泥石流', 2, 1850.0, 33.0),

 ('SC20260601004','黑水县崩塌','collapse',1,
  ST_SetSRID(ST_MakePoint(102.990, 32.060), 4326),
  NULL, now() - interval '12 hour', NULL, 1, '小规模岩崩, 暂未影响人员', 2, 2540.0, 48.0),

 ('SC20260420001','雷波县山体滑坡','landslide',3,
  ST_SetSRID(ST_MakePoint(103.580, 28.280), 4326),
  ST_GeomFromText('POLYGON((103.576 28.276, 103.584 28.276, 103.584 28.284, 103.576 28.284, 103.576 28.276))', 4326),
  now() - interval '50 day', now() - interval '46 day', 3, '金沙江沿岸滑坡, 已修复道路', 2, 1320.0, 41.0),

 ('SC20260601005','阿坝县查理乡滑坡','landslide',2,
  ST_SetSRID(ST_MakePoint(101.710, 32.900), 4326),
  NULL, now() - interval '3 hour', NULL, 1, '高原牧区小规模滑坡', 2, 3420.0, 26.0);

-- =========================== 预警事件 seed ============================
-- 给 4 个进行中事件挂上预警
INSERT INTO biz.biz_alert
    (code, title, content, level, event_id, source, location, channels, status, triggered_at) VALUES
 ('SCA20260601001','汶川映秀滑坡橙色预警','映秀镇后山滑坡持续发展, 请疏散 1km 缓冲区内人员, 关闭 G213 国道', 3,
  (SELECT id FROM biz.biz_disaster_event WHERE code='SC20260601001'),
  'event',
  ST_SetSRID(ST_MakePoint(103.487, 31.066), 4326),
  'in_site,sms', 2, now() - interval '1 hour'),

 ('SCA20260601002','北川擂鼓泥石流橙色预警','沟道泥石流持续, 请下游村镇高度警戒', 3,
  (SELECT id FROM biz.biz_disaster_event WHERE code='SC20260601002'),
  'event',
  ST_SetSRID(ST_MakePoint(104.464, 31.831), 4326),
  'in_site,sms,email', 2, now() - interval '4 hour'),

 ('SCA20260601003','雅安芦山滑坡黄色预警','局部滑坡, 监测加密', 2,
  (SELECT id FROM biz.biz_disaster_event WHERE code='SC20260601003'),
  'event',
  ST_SetSRID(ST_MakePoint(102.930, 30.140), 4326),
  'in_site', 2, now() - interval '6 hour'),

 ('SCA20260601004','黑水县崩塌蓝色预警','发现岩体松动迹象, 已布设监测点', 1,
  (SELECT id FROM biz.biz_disaster_event WHERE code='SC20260601004'),
  'event',
  ST_SetSRID(ST_MakePoint(102.990, 32.060), 4326),
  'in_site', 1, now() - interval '10 hour');

-- =========================== 应急预案 seed (四川特定) ============================
INSERT INTO biz.biz_emergency_plan (code, name, disaster_type, level, content, enabled) VALUES
 ('PLAN-SC-LANDSLIDE-O','川西高山滑坡橙色响应预案','landslide',3,
  '1.立即封闭灾点 1km 半径道路 2.疏散影响范围内居民 3.通知应急、消防、武警 4.启动专家组现场会商 5.每 15 分钟上报',TRUE),
 ('PLAN-SC-DEBRIS-R','泥石流红色响应预案','debris_flow',4,
  '1.封闭沟口 5km 路段 2.疏散下游居民点 3.调集大型清淤机械 4.设置警戒线与监测仪 5.连续上报至灾害解除',TRUE),
 ('PLAN-SC-COLLAPSE-Y','岩崩黄色响应预案','collapse',2,
  '1.设警戒标识 2.封闭直接威胁路段 3.加密人工巡查 4.必要时主动清除危岩',TRUE)
ON CONFLICT (code) DO NOTHING;

-- =========================== gis_layer 注册 ============================
-- 行政区/河流/居民点 → 矢量图层 (走后端 GeoJSON)
INSERT INTO gis.gis_layer (name, code, type, source_url, visible, z_index, description) VALUES
 ('四川省界',     'biz_sc_province',   'vector', '/api/regions/geojson?level=1&adcode=510000', TRUE,  20, '四川省外边界'),
 ('四川市州界',   'biz_sc_city',       'vector', '/api/regions/geojson?level=2&parent=510000', FALSE, 21, '四川 21 市州'),
 ('四川区县界',   'biz_sc_county',     'vector', '/api/regions/geojson?level=3&parent=510000', FALSE, 22, '四川区县 (按市加载)'),
 ('主要河流',     'biz_sc_rivers',     'vector', '/api/rivers/geojson',                        TRUE,  25, '四川六大水系'),
 ('居民点',       'biz_sc_settlement', 'vector', '/api/settlements/geojson',                   FALSE, 28, '城市/县城/重点乡镇')
ON CONFLICT (code) DO NOTHING;

-- =========================== 空间分析视图 (体现 PostGIS) ============================

-- 视图 1: 按县聚合灾害密度
-- 用法: SELECT * FROM biz.v_event_by_county WHERE event_count > 0;
CREATE OR REPLACE VIEW biz.v_event_by_county AS
SELECT r.adcode,
       r.name                                              AS county_name,
       r.area_km2,
       COUNT(e.id)                                          AS event_count,
       COUNT(e.id) FILTER (WHERE e.level >= 3)              AS high_level_count,
       ROUND( (COUNT(e.id)::numeric / NULLIF(r.area_km2, 0)) * 100, 4) AS density_per_100km2
FROM   gis.gis_admin_region r
LEFT   JOIN biz.biz_disaster_event e
       ON  ST_Within(e.location, r.boundary)
       AND e.deleted = FALSE
WHERE  r.level = 3
GROUP  BY r.adcode, r.name, r.area_km2;
COMMENT ON VIEW biz.v_event_by_county IS '按区县聚合灾害事件数与密度 (依赖 admin_region 数据已入库)';

-- 视图 2: 灾害点 → 最近河流距离 (米)
-- 用法: SELECT * FROM biz.v_event_river_distance ORDER BY distance_m;
CREATE OR REPLACE VIEW biz.v_event_river_distance AS
SELECT e.id           AS event_id,
       e.code         AS event_code,
       e.title,
       n.name         AS nearest_river,
       ROUND(n.distance_m::numeric, 1) AS distance_m
FROM   biz.biz_disaster_event e
CROSS  JOIN LATERAL (
    SELECT r.name, ST_Distance(e.location::geography, r.geom::geography) AS distance_m
    FROM   gis.gis_river r
    ORDER  BY e.location <-> r.geom        -- KNN 索引加速
    LIMIT  1
) n
WHERE  e.deleted = FALSE;
COMMENT ON VIEW biz.v_event_river_distance IS '每个灾害点距离最近河流的距离 (geography 米精度, 用 KNN <-> 加速)';

-- 视图 3: 进行中事件 5km 内受影响居民点
-- 用法: SELECT * FROM biz.v_settlement_at_risk WHERE event_id = ?;
CREATE OR REPLACE VIEW biz.v_settlement_at_risk AS
SELECT e.id                                                AS event_id,
       e.code                                              AS event_code,
       e.level                                             AS event_level,
       s.name                                              AS settlement_name,
       s.type                                              AS settlement_type,
       s.population,
       ROUND(ST_Distance(s.geom::geography, e.location::geography)::numeric, 1) AS distance_m
FROM   biz.biz_disaster_event e
JOIN   gis.gis_settlement s
       ON  ST_DWithin(s.geom::geography, e.location::geography, 5000)
WHERE  e.status = 1
  AND  e.deleted = FALSE;
COMMENT ON VIEW biz.v_settlement_at_risk IS '进行中事件 5km 缓冲内的居民点 (ST_DWithin 米精度)';

-- =========================== 数据落入回填 ============================
-- 灾害事件 → 所在区县 (region_code 回填)
-- 必须等 load_sichuan_boundary.py 把行政区入库后才能跑出结果, 此处先调一次 (无数据则全 NULL)
UPDATE biz.biz_disaster_event e
SET    region_code = r.adcode
FROM   gis.gis_admin_region r
WHERE  r.level = 3
  AND  ST_Within(e.location, r.boundary);

-- =====================================================
-- 完成
-- =====================================================
