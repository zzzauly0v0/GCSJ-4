-- =====================================================
-- GCSJ-4 气象地质灾害监测预警管理系统 - 数据库初始化脚本
-- 数据库: PostgreSQL 16 + PostGIS 3.4
-- 坐标系约定: 数据存储 EPSG:4326 (WGS84)
-- =====================================================

-- 创建独立的 PostGIS 核心函数 Schema，不装美国数据，防止污染 public
CREATE SCHEMA IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis SCHEMA postgis;

-- 核心业务模式
CREATE SCHEMA IF NOT EXISTS sys;  -- 系统基础、权限、日志
CREATE SCHEMA IF NOT EXISTS biz;  -- 灾害事件、预警、灾害判别结果
CREATE SCHEMA IF NOT EXISTS gis;  -- 空间地图图层

-- 设置数据库默认的寻址路径 (非常重要！这决定了你后端代码能否直接用表名)
-- 以后找表顺位：biz -> sys -> gis -> public -> postgis空间函数首先
ALTER DATABASE gcsj SET search_path TO biz, sys, gis, public, postgis;
-- 确保当前执行 SQL 的会话也立刻生效
SET search_path TO biz, sys, gis, public, postgis;

-- 系统模块
DROP TABLE IF EXISTS sys.sys_operation_log CASCADE;
DROP TABLE IF EXISTS sys.sys_dictionary CASCADE;
DROP TABLE IF EXISTS sys.sys_user_role CASCADE;
DROP TABLE IF EXISTS sys.sys_role_permission CASCADE;
DROP TABLE IF EXISTS sys.sys_permission CASCADE;
DROP TABLE IF EXISTS sys.sys_role CASCADE;
DROP TABLE IF EXISTS sys.sys_user CASCADE;

-- 业务模块
DROP TABLE IF EXISTS biz.biz_alert CASCADE;
DROP TABLE IF EXISTS biz.biz_disaster_event CASCADE;

-- 地图模块
DROP TABLE IF EXISTS gis.gis_layer CASCADE;


-- =========================== 用户/角色/权限 =====================
CREATE TABLE sys.sys_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(64) UNIQUE NOT NULL,
    password VARCHAR(128) NOT NULL,
    real_name VARCHAR(64),
    phone VARCHAR(32),
    email VARCHAR(128),
    avatar VARCHAR(256),
    status SMALLINT DEFAULT 1,           -- 1 启用 0 停用
    last_login_at TIMESTAMPTZ,
    deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE sys.sys_user IS '系统用户';

CREATE TABLE sys.sys_role (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    code VARCHAR(64) UNIQUE NOT NULL,
    description VARCHAR(256),
    deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE sys.sys_role IS '角色';

CREATE TABLE sys.sys_permission (
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
COMMENT ON TABLE sys.sys_permission IS '权限/菜单';

CREATE TABLE sys.sys_user_role (
    user_id BIGINT NOT NULL REFERENCES sys.sys_user(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES sys.sys_role(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE sys.sys_role_permission (
    role_id BIGINT NOT NULL REFERENCES sys.sys_role(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES sys.sys_permission(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- =========================== 字典 / 操作日志 =====================
CREATE TABLE sys.sys_dictionary (
    id BIGSERIAL PRIMARY KEY,
    type_code VARCHAR(64) NOT NULL,       -- 字典类型 e.g. disaster_type
    item_code VARCHAR(64) NOT NULL,       -- 字典项 e.g. landslide
    item_value VARCHAR(128) NOT NULL,     -- 显示值
    sort INT DEFAULT 0,
    description VARCHAR(256),
    UNIQUE (type_code, item_code)
);
COMMENT ON TABLE sys.sys_dictionary IS '数据字典';

CREATE TABLE sys.sys_operation_log (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    username VARCHAR(64),
    module VARCHAR(64),               -- Controller 类名
    action VARCHAR(64),                -- Controller 方法名
    method VARCHAR(16),                -- HTTP 动词
    uri VARCHAR(256),                  -- 请求路径
    ip VARCHAR(64),
    result_code INT,                   -- 业务响应 code (0 成功 / 非 0 失败)
    cost_ms BIGINT,                    -- 处理耗时 ms
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_oplog_user ON sys.sys_operation_log(user_id);
CREATE INDEX idx_oplog_time ON sys.sys_operation_log(created_at);

-- =========================== GIS 图层 ============================
CREATE TABLE gis.gis_layer (
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
COMMENT ON TABLE gis.gis_layer IS '业务图层目录';
CREATE INDEX idx_layer_extent ON gis.gis_layer USING GIST (extent);

-- =========================== 灾害事件 ============================
CREATE TABLE biz.biz_disaster_event (
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
    reporter_id BIGINT REFERENCES sys.sys_user(id),
    deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE biz.biz_disaster_event IS '灾害事件';
CREATE INDEX idx_event_loc ON biz.biz_disaster_event USING GIST (location);
CREATE INDEX idx_event_area ON biz.biz_disaster_event USING GIST (affected_area);
CREATE INDEX idx_event_level ON biz.biz_disaster_event(level);
CREATE INDEX idx_event_time ON biz.biz_disaster_event(occurred_at DESC);

-- =========================== 预警事件 ============================
CREATE TABLE biz.biz_alert (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) UNIQUE NOT NULL,
    title VARCHAR(256) NOT NULL,
    content TEXT,
    level SMALLINT NOT NULL,              -- 1 蓝 2 黄 3 橙 4 红
    event_id BIGINT REFERENCES biz.biz_disaster_event(id),
    source VARCHAR(64),                   -- manual / event / external (来源说明, 替代旧 rule_id/sensor_id)
    location geometry(Point, 4326),
    channels VARCHAR(128),                -- comma-separated: in_site,sms,email
    status SMALLINT DEFAULT 1,            -- 1 待发送 2 已发送 3 已确认 4 已关闭
    triggered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at TIMESTAMPTZ,
    confirmed_at TIMESTAMPTZ,
    confirmed_by_id BIGINT REFERENCES sys.sys_user(id),
    deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE biz.biz_alert IS '预警事件';
CREATE INDEX idx_alert_loc ON biz.biz_alert USING GIST (location);
CREATE INDEX idx_alert_level ON biz.biz_alert(level);
CREATE INDEX idx_alert_status ON biz.biz_alert(status);
CREATE INDEX idx_alert_event ON biz.biz_alert(event_id);

-- =========================== 种子数据 ============================
-- 角色 (仅两级: 管理员 / 普通用户)
INSERT INTO sys.sys_role (name, code, description) VALUES
 ('管理员',   'ROLE_ADMIN', '系统管理员, 可访问全部功能含系统管理'),
 ('普通用户', 'ROLE_USER',  '业务用户, 可访问全部业务功能, 不含系统管理');

-- 权限 (菜单)
-- 顶级菜单 (业务功能, 普通用户全部可见)
INSERT INTO sys.sys_permission (name, code, type, parent_id, path, icon, sort) VALUES
 ('监测大屏',   'dashboard:view', 1, NULL, '/dashboard', 'DataLine',     1),
 ('AI灾害助手', 'agent:view',     1, NULL, '/agent',     'ChatDotRound', 2),
 ('灾害事件',   'disaster:view',  1, NULL, '/disasters', 'Warning',      3),
 ('预警管理',   'alert:view',     1, NULL, '/alerts',    'BellFilled',   4),
 ('空间图层',   'layer:view',     1, NULL, '/layers',    'MapLocation',  5),
 ('系统管理',   'system:view',    1, NULL, '/system',    'Setting',      6);

-- 系统管理下的子菜单 (parent_id 用子查询取, 不依赖隐式 BIGSERIAL 跳号; 仅管理员可见)
INSERT INTO sys.sys_permission (name, code, type, parent_id, path, icon, sort) VALUES
 ('用户管理', 'user:view',       1, (SELECT id FROM sys.sys_permission WHERE code='system:view'), '/system/users',        'User',       1),
 ('角色管理', 'role:view',       1, (SELECT id FROM sys.sys_permission WHERE code='system:view'), '/system/roles',        'UserFilled', 2),
 ('权限管理', 'permission:view', 1, (SELECT id FROM sys.sys_permission WHERE code='system:view'), '/system/permissions',  'Key',        3),
 ('数据字典', 'dict:view',       1, (SELECT id FROM sys.sys_permission WHERE code='system:view'), '/system/dictionaries', 'Collection', 4),
 ('操作日志', 'log:view',        1, (SELECT id FROM sys.sys_permission WHERE code='system:view'), '/system/logs',         'List',       5);

-- 角色-权限 (按 code 关联, 不依赖 BIGSERIAL 具体值)
-- ROLE_ADMIN: 全部权限
INSERT INTO sys.sys_role_permission (role_id, permission_id)
 SELECT r.id, p.id
 FROM sys.sys_role r CROSS JOIN sys.sys_permission p
 WHERE r.code = 'ROLE_ADMIN';
-- ROLE_USER: 全部业务功能 (大屏/AI助手/灾害/预警/图层), 不含系统管理及其子菜单
INSERT INTO sys.sys_role_permission (role_id, permission_id)
 SELECT r.id, p.id
 FROM sys.sys_role r CROSS JOIN sys.sys_permission p
 WHERE r.code = 'ROLE_USER'
   AND p.code IN ('dashboard:view','agent:view','disaster:view','alert:view','layer:view');

-- 用户 (密码 BCrypt of "123456":  $2a$10$.sLUmtS1msqUQYEKDdxpZeIuE3jNsR6mTvjeqTHW34G4BLCFpoi.O)
INSERT INTO sys.sys_user (username, password, real_name, phone, email, status) VALUES
 ('admin', '$2a$10$.sLUmtS1msqUQYEKDdxpZeIuE3jNsR6mTvjeqTHW34G4BLCFpoi.O', '系统管理员', '13800000001', 'admin@gcsj.local', 1),
 ('user',  '$2a$10$.sLUmtS1msqUQYEKDdxpZeIuE3jNsR6mTvjeqTHW34G4BLCFpoi.O', '普通用户',   '13800000002', 'user@gcsj.local',  1);

-- 用户-角色 (按 username / role code 关联)
INSERT INTO sys.sys_user_role (user_id, role_id)
 SELECT u.id, r.id FROM sys.sys_user u, sys.sys_role r
 WHERE (u.username = 'admin' AND r.code = 'ROLE_ADMIN')
    OR (u.username = 'user'  AND r.code = 'ROLE_USER');

-- 字典
INSERT INTO sys.sys_dictionary (type_code, item_code, item_value, sort, description) VALUES
 ('disaster_type','landslide','滑坡',1,NULL),
 ('disaster_type','debris_flow','泥石流',2,NULL),
 ('disaster_type','collapse','崩塌',3,NULL),
 ('disaster_type','flood','洪涝',4,NULL),
 ('disaster_type','rainstorm','暴雨',5,NULL),
 ('alert_level','1','蓝色 (注意)',1,'#3B82F6'),
 ('alert_level','2','黄色 (警告)',2,'#FBBF24'),
 ('alert_level','3','橙色 (严重)',3,'#F97316'),
 ('alert_level','4','红色 (特别严重)',4,'#EF4444'),
 -- 灾害事件状态 (供前端筛选下拉)
 ('event_status','1','进行中',1,NULL),
 ('event_status','2','处置中',2,NULL),
 ('event_status','3','已结束',3,NULL),
 -- 预警状态 (供前端筛选下拉)
 ('alert_status','1','待发送',1,NULL),
 ('alert_status','2','已发送',2,NULL),
 ('alert_status','3','已确认',3,NULL),
 ('alert_status','4','已关闭',4,NULL),
 -- 预警来源 (manual=手动发布 / event=由灾害事件挂起 / external=外部接入)
 ('alert_source','manual','手动发布',1,NULL),
 ('alert_source','event','灾害事件',2,NULL),
 ('alert_source','external','外部接入',3,NULL);

-- 图层注册表
-- 设计说明:
--   1. 底图层: type=xyz, code 以 base_ 开头, source_url 走 XYZ 瓦片模板. 多个底图按 z_index 叠加.
--   2. 天地图 tk 用 ${VITE_TIANDITU_KEY} 占位, 前端运行时从 import.meta.env 注入实际 key.
--   3. 业务层: type=vector, code 以 biz_ 开头, source_url 走后端 GeoJSON 接口. 前端 useMap 据此控制业务图层 visible/zIndex.
INSERT INTO gis.gis_layer (name, code, type, source_url, visible, z_index, description) VALUES
 -- 底图 (z_index 0~9, 数值越大覆盖越上)
 ('OSM 街道底图',  'base_osm',          'xyz', 'https://{a-c}.tile.openstreetmap.org/{z}/{x}/{y}.png',                                                  TRUE,  0, '默认底图: 开源 OSM'),
 ('CartoDB 暗色',  'base_carto_dark',   'xyz', 'https://{a-d}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png',                                          FALSE, 0, '暗色底图, 适合监测大屏'),
 ('天地图矢量',    'base_tianditu_vec', 'xyz', 'https://t{0-7}.tianditu.gov.cn/DataServer?T=vec_w&x={x}&y={y}&l={z}&tk=${VITE_TIANDITU_KEY}',           FALSE, 0, '天地图矢量底图, 需配 VITE_TIANDITU_KEY'),
 ('天地图注记',    'base_tianditu_cva', 'xyz', 'https://t{0-7}.tianditu.gov.cn/DataServer?T=cva_w&x={x}&y={y}&l={z}&tk=${VITE_TIANDITU_KEY}',           FALSE, 1, '天地图中文注记, 需叠加在底图之上'),
 -- 业务图层 (z_index 10+, 高于底图)
 ('灾害事件图层',  'biz_events',        'vector', '/api/disasters/geojson', TRUE, 15, '灾害事件多边形 + 中心点'),
 ('预警图层',      'biz_alerts',        'vector', '/api/alerts/geojson',    TRUE, 30, '预警事件图层 (高等级带脉冲)');

-- =====================================================
-- 完成
-- =====================================================
