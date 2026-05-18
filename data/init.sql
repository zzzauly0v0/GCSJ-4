-- =====================================================
-- GCSJ-4 气象地质灾害监测预警管理系统 - 数据库初始化脚本
-- 数据库: PostgreSQL 16 + PostGIS 3.4
-- 坐标系约定: 数据存储 EPSG:4326 (WGS84)
-- =====================================================

CREATE EXTENSION IF NOT EXISTS postgis;

-- 删除已有表(开发期间方便重置)
DROP TABLE IF EXISTS sys_operation_log CASCADE;
DROP TABLE IF EXISTS sys_dictionary CASCADE;
DROP TABLE IF EXISTS sys_user_role CASCADE;
DROP TABLE IF EXISTS sys_role_permission CASCADE;
DROP TABLE IF EXISTS sys_permission CASCADE;
DROP TABLE IF EXISTS sys_role CASCADE;
DROP TABLE IF EXISTS sys_user CASCADE;
DROP TABLE IF EXISTS sys_organization CASCADE;
DROP TABLE IF EXISTS biz_alert CASCADE;
DROP TABLE IF EXISTS biz_alert_rule CASCADE;
DROP TABLE IF EXISTS biz_emergency_plan CASCADE;
DROP TABLE IF EXISTS biz_disaster_event CASCADE;
DROP TABLE IF EXISTS biz_observation CASCADE;
DROP TABLE IF EXISTS biz_sensor CASCADE;
DROP TABLE IF EXISTS gis_layer CASCADE;

-- =========================== 组织机构 ===========================
CREATE TABLE sys_organization (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    code VARCHAR(64) UNIQUE NOT NULL,
    parent_id BIGINT,
    sort INT DEFAULT 0,
    deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE sys_organization IS '组织机构';

-- =========================== 用户/角色/权限 =====================
CREATE TABLE sys_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(64) UNIQUE NOT NULL,
    password VARCHAR(128) NOT NULL,
    real_name VARCHAR(64),
    phone VARCHAR(32),
    email VARCHAR(128),
    avatar VARCHAR(256),
    org_id BIGINT REFERENCES sys_organization(id),
    status SMALLINT DEFAULT 1,           -- 1 启用 0 停用
    last_login_at TIMESTAMPTZ,
    deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE sys_user IS '系统用户';
CREATE INDEX idx_user_org ON sys_user(org_id);

CREATE TABLE sys_role (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    code VARCHAR(64) UNIQUE NOT NULL,
    description VARCHAR(256),
    deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE sys_role IS '角色';

CREATE TABLE sys_permission (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    code VARCHAR(128) UNIQUE NOT NULL,    -- 资源:操作  e.g. user:read
    type SMALLINT DEFAULT 1,              -- 1 菜单 2 按钮 3 接口
    parent_id BIGINT,
    path VARCHAR(256),
    icon VARCHAR(64),
    sort INT DEFAULT 0,
    deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE sys_permission IS '权限/菜单';

CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES sys_role(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE sys_role_permission (
    role_id BIGINT NOT NULL REFERENCES sys_role(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES sys_permission(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- =========================== 字典 / 操作日志 =====================
CREATE TABLE sys_dictionary (
    id BIGSERIAL PRIMARY KEY,
    type_code VARCHAR(64) NOT NULL,       -- 字典类型 e.g. disaster_type
    item_code VARCHAR(64) NOT NULL,       -- 字典项 e.g. landslide
    item_value VARCHAR(128) NOT NULL,     -- 显示值
    sort INT DEFAULT 0,
    description VARCHAR(256),
    UNIQUE (type_code, item_code)
);
COMMENT ON TABLE sys_dictionary IS '数据字典';

CREATE TABLE sys_operation_log (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    username VARCHAR(64),
    module VARCHAR(64),
    action VARCHAR(64),
    method VARCHAR(16),
    uri VARCHAR(256),
    ip VARCHAR(64),
    params TEXT,
    result_code INT,
    cost_ms BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_oplog_user ON sys_operation_log(user_id);
CREATE INDEX idx_oplog_time ON sys_operation_log(created_at);

-- =========================== GIS 图层 ============================
CREATE TABLE gis_layer (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    code VARCHAR(64) UNIQUE NOT NULL,
    type VARCHAR(32) NOT NULL,            -- vector / raster / wms / wmts / xyz
    source_url VARCHAR(512),
    workspace VARCHAR(64),
    layer_name VARCHAR(128),
    style VARCHAR(128),
    visible BOOLEAN DEFAULT TRUE,
    z_index INT DEFAULT 0,
    extent geometry(Polygon, 4326),
    description VARCHAR(512),
    deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE gis_layer IS '业务图层目录';
CREATE INDEX idx_layer_extent ON gis_layer USING GIST (extent);

-- =========================== 传感器 / 观测数据 ====================
CREATE TABLE biz_sensor (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) UNIQUE NOT NULL,
    name VARCHAR(128) NOT NULL,
    type VARCHAR(32) NOT NULL,            -- rain_gauge / displacement / soil_moisture / weather_station
    org_id BIGINT REFERENCES sys_organization(id),
    location geometry(Point, 4326) NOT NULL,
    elevation DOUBLE PRECISION,
    unit VARCHAR(16),
    install_at TIMESTAMPTZ,
    status SMALLINT DEFAULT 1,            -- 1 在线 0 离线 2 故障
    description VARCHAR(512),
    deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE biz_sensor IS '监测传感器';
CREATE INDEX idx_sensor_loc ON biz_sensor USING GIST (location);
CREATE INDEX idx_sensor_type ON biz_sensor(type);

CREATE TABLE biz_observation (
    id BIGSERIAL PRIMARY KEY,
    sensor_id BIGINT NOT NULL REFERENCES biz_sensor(id) ON DELETE CASCADE,
    indicator VARCHAR(32) NOT NULL,       -- RAINFALL_HOURLY / DISPLACEMENT_24H / SOIL_MOISTURE / TEMPERATURE / WIND_SPEED
    value DOUBLE PRECISION NOT NULL,
    unit VARCHAR(16),
    observed_at TIMESTAMPTZ NOT NULL,
    abnormal BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE biz_observation IS '观测数据(初版存PG, 扩展可迁移到时序库)';
CREATE INDEX idx_obs_sensor_time ON biz_observation(sensor_id, observed_at DESC);
CREATE INDEX idx_obs_indicator ON biz_observation(indicator);

-- =========================== 灾害事件 ============================
CREATE TABLE biz_disaster_event (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) UNIQUE NOT NULL,
    title VARCHAR(256) NOT NULL,
    type VARCHAR(32) NOT NULL,            -- landslide / mudslide / collapse / flood / debris_flow
    level SMALLINT NOT NULL,              -- 1 蓝 2 黄 3 橙 4 红
    location geometry(Point, 4326) NOT NULL,
    affected_area geometry(Polygon, 4326),
    occurred_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ,
    status SMALLINT DEFAULT 1,            -- 1 进行中 2 处置中 3 已结束
    description TEXT,
    reporter_id BIGINT REFERENCES sys_user(id),
    deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE biz_disaster_event IS '灾害事件';
CREATE INDEX idx_event_loc ON biz_disaster_event USING GIST (location);
CREATE INDEX idx_event_area ON biz_disaster_event USING GIST (affected_area);
CREATE INDEX idx_event_level ON biz_disaster_event(level);
CREATE INDEX idx_event_time ON biz_disaster_event(occurred_at DESC);

-- =========================== 预警规则 / 预警事件 ===================
CREATE TABLE biz_alert_rule (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    indicator VARCHAR(32) NOT NULL,       -- RAINFALL_HOURLY / DISPLACEMENT_24H / SOIL_MOISTURE / ...
    operator VARCHAR(8) NOT NULL,         -- > / >= / < / <=  / ==
    threshold DOUBLE PRECISION NOT NULL,
    level SMALLINT NOT NULL,              -- 1 蓝 2 黄 3 橙 4 红
    disaster_type VARCHAR(32),            -- 触发对应灾害类型 (可空, 通用规则)
    enabled BOOLEAN DEFAULT TRUE,
    description VARCHAR(256),
    deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE biz_alert_rule IS '预警规则';

CREATE TABLE biz_alert (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) UNIQUE NOT NULL,
    title VARCHAR(256) NOT NULL,
    content TEXT,
    level SMALLINT NOT NULL,              -- 1 蓝 2 黄 3 橙 4 红
    rule_id BIGINT REFERENCES biz_alert_rule(id),
    sensor_id BIGINT REFERENCES biz_sensor(id),
    event_id BIGINT REFERENCES biz_disaster_event(id),
    location geometry(Point, 4326),
    channels VARCHAR(128),                -- comma-separated: in_site,sms,email
    status SMALLINT DEFAULT 1,            -- 1 待发送 2 已发送 3 已确认 4 已关闭
    triggered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at TIMESTAMPTZ,
    confirmed_at TIMESTAMPTZ,
    deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE biz_alert IS '预警事件';
CREATE INDEX idx_alert_loc ON biz_alert USING GIST (location);
CREATE INDEX idx_alert_level ON biz_alert(level);
CREATE INDEX idx_alert_status ON biz_alert(status);

-- =========================== 应急预案 ============================
CREATE TABLE biz_emergency_plan (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) UNIQUE NOT NULL,
    name VARCHAR(256) NOT NULL,
    disaster_type VARCHAR(32),
    level SMALLINT,                       -- 应用的预警等级阈值
    content TEXT,
    file_url VARCHAR(512),
    enabled BOOLEAN DEFAULT TRUE,
    deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE biz_emergency_plan IS '应急预案';

-- =========================== 种子数据 ============================
-- 组织机构
INSERT INTO sys_organization (name, code, parent_id, sort) VALUES
 ('总指挥部', 'HQ', NULL, 1),
 ('监测中心', 'MONITOR_CENTER', 1, 2),
 ('应急中心', 'EMERGENCY_CENTER', 1, 3);

-- 角色
INSERT INTO sys_role (name, code, description) VALUES
 ('超级管理员', 'ROLE_ADMIN', '系统超级管理员'),
 ('监测员', 'ROLE_OPERATOR', '负责监测数据查看与上报'),
 ('普通用户', 'ROLE_VIEWER', '仅可查看公开数据');

-- 权限 (菜单)
INSERT INTO sys_permission (name, code, type, parent_id, path, icon, sort) VALUES
 ('监测大屏', 'dashboard:view', 1, NULL, '/dashboard', 'DataLine', 1),
 ('实时监测', 'monitor:view', 1, NULL, '/monitor', 'Monitor', 2),
 ('预警管理', 'alert:view', 1, NULL, '/alerts', 'Bell', 3),
 ('灾害事件', 'disaster:view', 1, NULL, '/disasters', 'Warning', 4),
 ('图层管理', 'layer:view', 1, NULL, '/layers', 'MapLocation', 5),
 ('应急预案', 'plan:view', 1, NULL, '/plans', 'Document', 6),
 ('系统管理', 'system:view', 1, NULL, '/system', 'Setting', 9),
 ('用户管理', 'user:view', 1, 7, '/system/users', 'User', 1),
 ('角色管理', 'role:view', 1, 7, '/system/roles', 'UserFilled', 2),
 ('权限管理', 'permission:view', 1, 7, '/system/permissions', 'Key', 3),
 ('操作日志', 'log:view', 1, 7, '/system/logs', 'List', 4);

-- 角色-权限 (admin: all, operator: 1-6, viewer: 1-4)
INSERT INTO sys_role_permission (role_id, permission_id)
 SELECT 1, id FROM sys_permission;
INSERT INTO sys_role_permission (role_id, permission_id)
 SELECT 2, id FROM sys_permission WHERE code IN ('dashboard:view','monitor:view','alert:view','disaster:view','layer:view','plan:view');
INSERT INTO sys_role_permission (role_id, permission_id)
 SELECT 3, id FROM sys_permission WHERE code IN ('dashboard:view','monitor:view','alert:view','disaster:view');

-- 用户 (密码 BCrypt of "123456": $2a$10$N0d2cLSczlW.7P/0rZ.aluRkyOxiSldS2OATEoPN8tHxfXEwlnMSm)
INSERT INTO sys_user (username, password, real_name, phone, email, org_id, status) VALUES
 ('admin', '$2a$10$N0d2cLSczlW.7P/0rZ.aluRkyOxiSldS2OATEoPN8tHxfXEwlnMSm', '系统管理员', '13800000001', 'admin@gcsj.local', 1, 1),
 ('operator', '$2a$10$N0d2cLSczlW.7P/0rZ.aluRkyOxiSldS2OATEoPN8tHxfXEwlnMSm', '监测员001', '13800000002', 'op@gcsj.local', 2, 1),
 ('viewer', '$2a$10$N0d2cLSczlW.7P/0rZ.aluRkyOxiSldS2OATEoPN8tHxfXEwlnMSm', '游客', '13800000003', 'viewer@gcsj.local', 1, 1);

INSERT INTO sys_user_role (user_id, role_id) VALUES (1,1),(2,2),(3,3);

-- 字典
INSERT INTO sys_dictionary (type_code, item_code, item_value, sort, description) VALUES
 ('disaster_type','landslide','滑坡',1,NULL),
 ('disaster_type','mudslide','泥石流',2,NULL),
 ('disaster_type','collapse','崩塌',3,NULL),
 ('disaster_type','flood','洪涝',4,NULL),
 ('disaster_type','debris_flow','碎屑流',5,NULL),
 ('alert_level','1','蓝色 (注意)',1,'#3B82F6'),
 ('alert_level','2','黄色 (警告)',2,'#FBBF24'),
 ('alert_level','3','橙色 (严重)',3,'#F97316'),
 ('alert_level','4','红色 (特别严重)',4,'#EF4444'),
 ('sensor_type','rain_gauge','雨量计',1,'mm'),
 ('sensor_type','displacement','位移计',2,'mm'),
 ('sensor_type','soil_moisture','土壤湿度',3,'%'),
 ('sensor_type','weather_station','气象站',4,'composite');

-- 图层
INSERT INTO gis_layer (name, code, type, source_url, visible, z_index, description) VALUES
 ('OSM 底图', 'base_osm', 'xyz', 'https://{a-c}.tile.openstreetmap.org/{z}/{x}/{y}.png', TRUE, 0, '开源 OSM 底图'),
 ('天地图矢量', 'base_tianditu', 'xyz', 'https://t{0-7}.tianditu.gov.cn/vec_w/wmts?...', FALSE, 0, '需要替换 tk'),
 ('传感器图层', 'biz_sensors', 'vector', '/api/sensors/geojson', TRUE, 5, '传感器点图层'),
 ('灾害事件图层', 'biz_events', 'vector', '/api/disasters/geojson', TRUE, 6, '灾害事件图层'),
 ('预警图层', 'biz_alerts', 'vector', '/api/alerts/geojson', TRUE, 7, '预警图层');

-- 传感器示例 (北京周边)
INSERT INTO biz_sensor (code, name, type, org_id, location, elevation, unit, install_at, status) VALUES
 ('S001','八达岭雨量站','rain_gauge',2,ST_SetSRID(ST_MakePoint(116.0167, 40.3589),4326),650,'mm','2025-01-01',1),
 ('S002','怀柔位移1号','displacement',2,ST_SetSRID(ST_MakePoint(116.6311, 40.3164),4326),420,'mm','2025-01-15',1),
 ('S003','延庆土壤湿度A','soil_moisture',2,ST_SetSRID(ST_MakePoint(115.9748, 40.4569),4326),550,'%','2025-02-01',1),
 ('S004','门头沟综合气象','weather_station',2,ST_SetSRID(ST_MakePoint(116.1058, 39.9408),4326),300,'composite','2025-02-10',1),
 ('S005','房山雨量站','rain_gauge',2,ST_SetSRID(ST_MakePoint(115.9931, 39.7359),4326),200,'mm','2025-03-01',1),
 ('S006','密云位移2号','displacement',2,ST_SetSRID(ST_MakePoint(116.8431, 40.3760),4326),380,'mm','2025-03-12',1);

-- 预警规则
INSERT INTO biz_alert_rule (name, indicator, operator, threshold, level, disaster_type, enabled, description) VALUES
 ('小时降雨蓝色预警','RAINFALL_HOURLY','>=',10,1,'flood',TRUE,'≥10mm/h'),
 ('小时降雨黄色预警','RAINFALL_HOURLY','>=',25,2,'flood',TRUE,'≥25mm/h'),
 ('小时降雨橙色预警','RAINFALL_HOURLY','>=',50,3,'flood',TRUE,'≥50mm/h'),
 ('小时降雨红色预警','RAINFALL_HOURLY','>=',100,4,'flood',TRUE,'≥100mm/h'),
 ('位移蓝色预警','DISPLACEMENT_24H','>=',5,1,'landslide',TRUE,'24h≥5mm'),
 ('位移黄色预警','DISPLACEMENT_24H','>=',15,2,'landslide',TRUE,'24h≥15mm'),
 ('位移橙色预警','DISPLACEMENT_24H','>=',30,3,'landslide',TRUE,'24h≥30mm'),
 ('位移红色预警','DISPLACEMENT_24H','>=',50,4,'landslide',TRUE,'24h≥50mm'),
 ('土壤湿度蓝色','SOIL_MOISTURE','>=',60,1,'mudslide',TRUE,'≥60%'),
 ('土壤湿度黄色','SOIL_MOISTURE','>=',75,2,'mudslide',TRUE,'≥75%'),
 ('土壤湿度橙色','SOIL_MOISTURE','>=',85,3,'mudslide',TRUE,'≥85%'),
 ('土壤湿度红色','SOIL_MOISTURE','>=',95,4,'mudslide',TRUE,'≥95%');

-- 一些示例观测数据
INSERT INTO biz_observation (sensor_id, indicator, value, unit, observed_at, abnormal) VALUES
 (1, 'RAINFALL_HOURLY', 8.4, 'mm', now() - interval '3 hour', false),
 (1, 'RAINFALL_HOURLY', 12.1, 'mm', now() - interval '2 hour', true),
 (1, 'RAINFALL_HOURLY', 27.5, 'mm', now() - interval '1 hour', true),
 (2, 'DISPLACEMENT_24H', 3.0, 'mm', now() - interval '2 hour', false),
 (2, 'DISPLACEMENT_24H', 6.2, 'mm', now() - interval '1 hour', true),
 (3, 'SOIL_MOISTURE', 58, '%', now() - interval '2 hour', false),
 (3, 'SOIL_MOISTURE', 71, '%', now() - interval '1 hour', true),
 (5, 'RAINFALL_HOURLY', 4.0, 'mm', now() - interval '1 hour', false);

-- 一个进行中的灾害事件
INSERT INTO biz_disaster_event (code, title, type, level, location, affected_area, occurred_at, status, description, reporter_id) VALUES
 ('E20260518001','怀柔X村山体小型滑坡','landslide',2,
  ST_SetSRID(ST_MakePoint(116.6311, 40.3164),4326),
  ST_SetSRID(ST_GeomFromText('POLYGON((116.629 40.314, 116.633 40.314, 116.633 40.318, 116.629 40.318, 116.629 40.314))'),4326),
  now() - interval '30 minute', 1, '位移监测点连续触发预警, 现场确认小规模滑坡', 2);

-- 一条已发送预警
INSERT INTO biz_alert (code, title, content, level, rule_id, sensor_id, event_id, location, channels, status, triggered_at, sent_at) VALUES
 ('A20260518001','黄色位移预警 - 怀柔位移1号','sensor S002 24h displacement=6.2mm 超过阈值5mm',2,5,2,1,
  ST_SetSRID(ST_MakePoint(116.6311, 40.3164),4326),'in_site',2, now() - interval '20 minute', now() - interval '19 minute');

-- 应急预案
INSERT INTO biz_emergency_plan (code, name, disaster_type, level, content, enabled) VALUES
 ('PLAN-LANDSLIDE-Y','滑坡黄色预警响应预案','landslide',2,'1.通知现场负责人 2.撤离100m缓冲区人员 3.每30分钟上报一次',TRUE),
 ('PLAN-FLOOD-O','洪涝橙色预警响应预案','flood',3,'1.启动排涝设备 2.封闭低洼路段 3.通知下游村镇',TRUE);

-- =====================================================
-- 完成
-- =====================================================
