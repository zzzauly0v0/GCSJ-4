"""
GCSJ-4 时间轴回放数据生成器

窗口: 2022-09-05 00:00 ~ 2022-09-08 00:00 (3 天, 泸定 6.8 地震 + 余震 + 次生滑坡)

生成数据:
  1. 30 个气象站 (按市州中心分布)
  2. 50 个地质传感器点 (川西高山区随机)
  3. 气象观测时序 (1h × 72h × 30 站)
  4. 地质传感器时序 (10min × 50 点 × 4 指标)
  5. 地震事件序列 (主震 + 余震 + 历史背景)
  6. 灾害事件 + 预警 (基于阈值规则离线算好, 直接灌 biz_alert/biz_disaster_event)

预警规则全在 Python 里, 后端 Java 只做查询不算逻辑。
"""
import math
import random
from datetime import datetime, timedelta, timezone

import psycopg2
import psycopg2.extras

DB_DSN = "host=localhost port=5432 dbname=gcsj user=gcsj password=gcsj123"

T0 = datetime(2022, 9, 5, 0, 0, tzinfo=timezone.utc)
T_END = datetime(2022, 9, 8, 0, 0, tzinfo=timezone.utc)
QUAKE_AT = datetime(2022, 9, 5, 4, 52, tzinfo=timezone.utc)  # 泸定主震 (UTC, 北京 12:52)
QUAKE_LON, QUAKE_LAT = 102.08, 29.59
QUAKE_REGION = "513329"  # 甘孜州泸定县

random.seed(20220905)


def gauss(mu, sigma, lo=None, hi=None):
    v = random.gauss(mu, sigma)
    if lo is not None:
        v = max(lo, v)
    if hi is not None:
        v = min(hi, v)
    return v


def haversine_km(lon1, lat1, lon2, lat2):
    R = 6371.0
    f1, f2 = math.radians(lat1), math.radians(lat2)
    dl = math.radians(lat2 - lat1)
    dlo = math.radians(lon2 - lon1)
    a = math.sin(dl / 2) ** 2 + math.cos(f1) * math.cos(f2) * math.sin(dlo / 2) ** 2
    return 2 * R * math.asin(math.sqrt(a))


# ========================= 站点生成 =========================

def gen_weather_stations(cursor) -> list[dict]:
    """每个市州中心 1 站 + 部分市加 1 站"""
    cursor.execute("""
        SELECT adcode, name, ST_X(center) lon, ST_Y(center) lat
        FROM gis.gis_admin_region WHERE level = 2 ORDER BY adcode
    """)
    cities = cursor.fetchall()
    stations = []
    for i, (adcode, name, lon, lat) in enumerate(cities):
        stations.append({
            "code": f"WS{adcode}01",
            "name": f"{name}国家气象站",
            "type": "weather",
            "region_code": adcode,
            "lon": lon,
            "lat": lat,
            "elevation": gauss(800, 400, 200, 4000),
        })
        # 川西高山区 + 暴雨易发市加 1 站
        if name in ("阿坝藏族羌族自治州", "甘孜藏族自治州", "凉山彝族自治州",
                    "雅安市", "乐山市", "绵阳市", "广元市", "成都市", "巴中市"):
            stations.append({
                "code": f"WS{adcode}02",
                "name": f"{name}郊区气象站",
                "type": "weather",
                "region_code": adcode,
                "lon": lon + gauss(0, 0.4),
                "lat": lat + gauss(0, 0.4),
                "elevation": gauss(1500, 600, 300, 4500),
            })
    return stations


def gen_geo_stations(cursor) -> list[dict]:
    """川西高山区 + 泸定震区随机布点"""
    # 主要分布在 阿坝/甘孜/雅安/乐山/凉山 这些高风险区县
    cursor.execute("""
        SELECT adcode, name, ST_X(center) lon, ST_Y(center) lat
        FROM gis.gis_admin_region WHERE level = 3
          AND parent_code IN ('513200','513300','511800','511100','513400')
        ORDER BY adcode
    """)
    counties = cursor.fetchall()
    stations = []
    # 泸定县周边密集布 15 个 (突出震中地质监测)
    for i in range(15):
        stations.append({
            "code": f"GS-LD-{i:02d}",
            "name": f"泸定监测点-{i:02d}",
            "type": "geo",
            "region_code": QUAKE_REGION,
            "lon": QUAKE_LON + gauss(0, 0.25),
            "lat": QUAKE_LAT + gauss(0, 0.25),
            "elevation": gauss(2500, 700, 1200, 4500),
        })
    # 其他县每县 1 个
    chosen = random.sample(counties, min(35, len(counties)))
    for i, (adcode, name, lon, lat) in enumerate(chosen):
        stations.append({
            "code": f"GS-{adcode}-01",
            "name": f"{name}地质监测点",
            "type": "geo",
            "region_code": adcode,
            "lon": lon + gauss(0, 0.1),
            "lat": lat + gauss(0, 0.1),
            "elevation": gauss(2000, 600, 800, 4500),
        })
    return stations


def insert_stations(cursor, stations):
    cursor.execute("TRUNCATE biz.biz_monitor_station RESTART IDENTITY")
    for s in stations:
        cursor.execute("""
            INSERT INTO biz.biz_monitor_station
                (code, name, type, region_code, location, elevation, install_date, status)
            VALUES (%s, %s, %s, %s,
                    ST_SetSRID(ST_MakePoint(%s, %s), 4326),
                    %s, '2020-01-01', 1)
        """, (s["code"], s["name"], s["type"], s["region_code"],
              s["lon"], s["lat"], s["elevation"]))


# ========================= 气象时序 =========================

def rainfall_profile(t: datetime, station: dict) -> float:
    """每小时雨强 mm/h: 9-5 白天弱降雨, 9-6 夜间到 9-7 凌晨暴雨, 9-7 白天减弱"""
    h = (t - T0).total_seconds() / 3600
    base = 0.0
    # 9/5 中午到下午: 1-3mm
    if 8 <= h <= 18:
        base = gauss(1.5, 0.8, 0)
    # 9/5 夜间 ~ 9/6 凌晨: 强降雨, 5-25mm
    elif 22 <= h <= 36:
        peak = math.exp(-((h - 28) ** 2) / 30) * 25
        base = peak + gauss(2, 1.5, 0)
    # 9/6 白天: 间歇性中雨
    elif 36 <= h <= 50:
        base = gauss(4, 2.5, 0)
    # 9/6 夜间又一波
    elif 50 <= h <= 60:
        peak = math.exp(-((h - 55) ** 2) / 18) * 18
        base = peak + gauss(1, 1, 0)
    # 9/7 大部分时间: 减弱
    elif 60 <= h <= 72:
        base = gauss(0.8, 0.6, 0)

    # 高山区 (海拔 > 2000) 雨强加 30%
    if station.get("elevation", 0) > 2000:
        base *= 1.3
    # 川西暴雨重点区
    if station["region_code"][:4] in ("5132", "5133", "5118", "5111"):
        base *= 1.2
    return max(0.0, base + gauss(0, 0.3))


def gen_weather_series(cursor, weather_stations):
    print("  生成气象时序 ...")
    cursor.execute("TRUNCATE biz.biz_weather_observation")
    rows = []
    # 每站维护 24 小时滚动窗口
    rolling = {s["code"]: [] for s in weather_stations}
    for hour in range(72 + 1):
        t = T0 + timedelta(hours=hour)
        for s in weather_stations:
            r1 = round(rainfall_profile(t, s), 2)
            rolling[s["code"]].append(r1)
            if len(rolling[s["code"]]) > 24:
                rolling[s["code"]].pop(0)
            r24 = round(sum(rolling[s["code"]]), 2)
            temp = round(gauss(18 - 0.005 * s["elevation"], 3, -10, 35), 1)
            hum = round(gauss(75 + (10 if r1 > 5 else 0), 8, 30, 100), 1)
            wind = round(gauss(2.5, 1.2, 0, 15), 1)
            pres = round(gauss(1010 - 0.1 * s["elevation"], 4, 600, 1030), 1)
            rows.append((s["code"], t, r1, r24, temp, hum, wind, pres))
    psycopg2.extras.execute_values(cursor, """
        INSERT INTO biz.biz_weather_observation
            (station_code, observed_at, rainfall_1h, rainfall_24h,
             temperature, humidity, wind_speed, pressure)
        VALUES %s
    """, rows, page_size=1000)
    print(f"    气象观测 {len(rows)} 行")


# ========================= 地质传感器时序 =========================

def sensor_baseline(metric: str, station: dict):
    """(baseline, sigma, normal_range)"""
    return {
        "displacement":  (0.0, 0.05, 0.5),     # mm/10min, 正常 < 0.5
        "soil_moisture": (28.0, 1.5, 35.0),    # %, 正常 < 35
        "tilt":          (0.0, 0.02, 0.3),     # 度
        "crack":         (0.0, 0.03, 0.4),     # mm
    }[metric]


def sensor_value(metric: str, t: datetime, station: dict, recent_rain: float, quake_factor: float):
    """recent_rain: 该站最近 6h 累计降雨 mm, quake_factor: 离震中近且地震后 -> 加扰动"""
    base, sigma, _ = sensor_baseline(metric, station)
    v = base + gauss(0, sigma)
    # 降雨耦合
    if metric == "soil_moisture":
        v += min(recent_rain * 0.4, 25)
    elif metric == "displacement":
        v += min(recent_rain * 0.015, 1.5)
    elif metric == "tilt":
        v += min(recent_rain * 0.005, 0.4)
    elif metric == "crack":
        v += min(recent_rain * 0.008, 0.6)
    # 地震耦合
    if quake_factor > 0:
        if metric == "displacement":
            v += quake_factor * gauss(2.5, 0.8, 0)
        elif metric == "tilt":
            v += quake_factor * gauss(0.6, 0.2, 0)
        elif metric == "crack":
            v += quake_factor * gauss(0.8, 0.3, 0)
    return round(max(0.0, v), 4)


def gen_geo_series(cursor, geo_stations):
    print("  生成地质传感器时序 ...")
    cursor.execute("TRUNCATE biz.biz_geo_sensor_reading")
    rows = []
    metrics = [("displacement", "mm"), ("soil_moisture", "%"),
               ("tilt", "°"), ("crack", "mm")]
    # 每站最近 6h 雨 (按距离最近的气象站近似 — 这里偷懒按区县均值)
    # 先把气象数据按区县 1h 雨量平均一下
    cursor.execute("""
        SELECT s.region_code, w.observed_at, AVG(w.rainfall_1h) avg_r
        FROM   biz.biz_weather_observation w
        JOIN   biz.biz_monitor_station s ON s.code = w.station_code
        WHERE  s.type = 'weather'
        GROUP BY s.region_code, w.observed_at
    """)
    rain_by_region: dict[tuple, float] = {}
    for rc, ts, r in cursor.fetchall():
        rain_by_region[(rc, ts.replace(minute=0, second=0, microsecond=0))] = float(r or 0)

    step_min = 10
    total_steps = int((T_END - T0).total_seconds() / 60 / step_min)
    for step in range(total_steps + 1):
        t = T0 + timedelta(minutes=step_min * step)
        hour_key = t.replace(minute=0, second=0, microsecond=0)
        for s in geo_stations:
            # 最近 6h 累计雨量
            rain_6h = 0.0
            for back in range(6):
                hk = hour_key - timedelta(hours=back)
                rain_6h += rain_by_region.get((s["region_code"], hk), 0.0)
            # 地震因子: 主震后 24h 内 + 距震中近 -> 大扰动
            qf = 0.0
            if t > QUAKE_AT:
                age_h = (t - QUAKE_AT).total_seconds() / 3600
                if age_h < 24:
                    decay = math.exp(-age_h / 8)
                    dist = haversine_km(s["lon"], s["lat"], QUAKE_LON, QUAKE_LAT)
                    if dist < 80:
                        qf = decay * math.exp(-dist / 30)
            for m, unit in metrics:
                v = sensor_value(m, t, s, rain_6h, qf)
                _, _, threshold = sensor_baseline(m, s)
                # 加上累积位移趋势 (位移和裂缝是单调累积的)
                if m in ("displacement", "crack") and qf > 0.3:
                    v += qf * 1.2
                anomaly = v > threshold
                rows.append((s["code"], t, m, v, unit, anomaly))
    print(f"    地质传感器 {len(rows)} 行, 批量入库 ...")
    psycopg2.extras.execute_values(cursor, """
        INSERT INTO biz.biz_geo_sensor_reading
            (station_code, observed_at, metric_type, value, unit, is_anomaly)
        VALUES %s
    """, rows, page_size=2000)


# ========================= 地震事件 =========================

def gen_earthquake_events(cursor):
    print("  生成地震事件 ...")
    cursor.execute("TRUNCATE biz.biz_earthquake_event RESTART IDENTITY")
    events = []
    # 主震
    events.append({
        "code": "EQ-2022-LUDING-MAIN",
        "occurred_at": QUAKE_AT,
        "magnitude": 6.8,
        "depth_km": 16.0,
        "lon": QUAKE_LON, "lat": QUAKE_LAT,
        "name": "甘孜州泸定县磨西镇",
        "region_code": QUAKE_REGION,
    })
    # 余震序列 (主震后 48h 内, 震级递减)
    n_after = 18
    for i in range(n_after):
        delay_h = random.expovariate(1 / 6)
        if delay_h > 48:
            continue
        mag = max(2.5, 5.5 - random.expovariate(1 / 0.8))
        events.append({
            "code": f"EQ-2022-LUDING-AS{i:02d}",
            "occurred_at": QUAKE_AT + timedelta(hours=delay_h),
            "magnitude": round(mag, 1),
            "depth_km": round(gauss(15, 4, 5, 30), 1),
            "lon": QUAKE_LON + gauss(0, 0.15),
            "lat": QUAKE_LAT + gauss(0, 0.15),
            "name": "泸定县余震",
            "region_code": QUAKE_REGION,
        })
    # 窗口期内全川零星小震 (背景活动)
    for i in range(8):
        t = T0 + timedelta(hours=random.uniform(0, 72))
        events.append({
            "code": f"EQ-BG-{i:02d}",
            "occurred_at": t,
            "magnitude": round(random.uniform(2.5, 4.0), 1),
            "depth_km": round(gauss(12, 5, 5, 30), 1),
            "lon": gauss(103.5, 1.8, 100.5, 108.5),
            "lat": gauss(30.5, 1.5, 27.5, 33.5),
            "name": "四川境内",
            "region_code": None,
        })
    for e in events:
        cursor.execute("""
            INSERT INTO biz.biz_earthquake_event
                (code, occurred_at, magnitude, depth_km, epicenter, location_name, region_code)
            VALUES (%s, %s, %s, %s,
                    ST_SetSRID(ST_MakePoint(%s, %s), 4326), %s, %s)
        """, (e["code"], e["occurred_at"], e["magnitude"], e["depth_km"],
              e["lon"], e["lat"], e["name"], e["region_code"]))
    print(f"    地震事件 {len(events)} 条 (含主震 + 余震 + 背景)")
    return events


# ========================= 灾害事件 + 预警 (规则引擎核心) =========================

def rainfall_alert_level(r1, r3, r6, r24):
    """国标 GB/T 28592 暴雨预警阈值, 取最严"""
    levels = []
    if r24 >= 200 or r3 >= 100:
        levels.append(4)  # 红
    if r24 >= 100 or r3 >= 50:
        levels.append(3)  # 橙
    if r24 >= 50 or r6 >= 50:
        levels.append(2)  # 黄
    if r24 >= 25 or r6 >= 25:
        levels.append(1)  # 蓝
    return max(levels) if levels else 0


def id_curve_triggered(rain_1h, hours_continuous):
    """Caine 1980 I-D 曲线: I = 14.82 × D^(-0.39)"""
    if hours_continuous < 1:
        return False
    threshold = 14.82 * (hours_continuous ** -0.39)
    return rain_1h >= threshold


def landslide_risk(r24, soil_moisture_max, displacement_max):
    """组合规则: 滑坡风险等级 0-4"""
    score = 0
    if r24 >= 50: score += 1
    if r24 >= 100: score += 1
    if soil_moisture_max >= 35: score += 1
    if soil_moisture_max >= 45: score += 1
    if displacement_max >= 0.5: score += 1
    if displacement_max >= 1.5: score += 1
    return min(4, score)


def gen_disasters_and_alerts(cursor, weather_stations, geo_stations, quake_events):
    print("  规则引擎计算预警 ...")
    cursor.execute("DELETE FROM biz.biz_alert WHERE source IN ('rule_rainfall','rule_landslide','rule_quake_secondary','rule_id_curve')")
    cursor.execute("DELETE FROM biz.biz_disaster_event WHERE code LIKE 'AUTO-%'")

    # 按区县-小时 聚合气象 (取最大雨强代表该区县)
    cursor.execute("""
        SELECT s.region_code, r.name AS region_name, ST_X(r.center) lon, ST_Y(r.center) lat,
               w.observed_at, MAX(w.rainfall_1h) r1, MAX(w.rainfall_24h) r24
        FROM   biz.biz_weather_observation w
        JOIN   biz.biz_monitor_station s ON s.code = w.station_code AND s.type='weather'
        JOIN   gis.gis_admin_region r ON r.adcode = s.region_code
        GROUP BY s.region_code, r.name, r.center, w.observed_at
        ORDER BY w.observed_at, s.region_code
    """)
    region_hourly = cursor.fetchall()

    # 滚动窗口算 3h/6h 累计
    region_hist: dict[str, list[tuple]] = {}
    rainfall_alerts = []
    last_alert_lvl: dict[str, int] = {}  # 区县降级控制, 同一区县短时间不重复

    for region_code, region_name, lon, lat, ts, r1, r24 in region_hourly:
        hist = region_hist.setdefault(region_code, [])
        hist.append((ts, float(r1 or 0), float(r24 or 0)))
        recent = [x for x in hist if (ts - x[0]).total_seconds() <= 6 * 3600]
        r3 = sum(x[1] for x in recent[-3:])
        r6 = sum(x[1] for x in recent[-6:])

        lvl = rainfall_alert_level(float(r1 or 0), r3, r6, float(r24 or 0))
        if lvl > 0 and lvl > last_alert_lvl.get(region_code, 0):
            rainfall_alerts.append({
                "ts": ts,
                "region": region_code,
                "region_name": region_name,
                "lon": lon, "lat": lat,
                "level": lvl,
                "r1": r1, "r3": r3, "r6": r6, "r24": r24,
            })
            last_alert_lvl[region_code] = lvl

    print(f"    暴雨预警 {len(rainfall_alerts)} 条")

    # 按 站 取地质传感器最大值 (滑坡风险输入)
    cursor.execute("""
        WITH agg AS (
            SELECT g.station_code,
                   date_trunc('hour', g.observed_at) AS hour_ts,
                   g.metric_type,
                   MAX(g.value) AS max_v
            FROM biz.biz_geo_sensor_reading g
            GROUP BY g.station_code, hour_ts, g.metric_type
        )
        SELECT a.station_code, a.hour_ts,
               MAX(CASE WHEN metric_type='soil_moisture' THEN max_v END) sm,
               MAX(CASE WHEN metric_type='displacement'  THEN max_v END) disp,
               s.region_code, s.name AS station_name,
               ST_X(s.location) lon, ST_Y(s.location) lat,
               r.name AS region_name
        FROM   agg a
        JOIN   biz.biz_monitor_station s ON s.code = a.station_code AND s.type='geo'
        LEFT JOIN gis.gis_admin_region r ON r.adcode = s.region_code
        GROUP BY a.station_code, a.hour_ts, s.region_code, s.name, s.location, r.name
        ORDER BY a.hour_ts, a.station_code
    """)
    geo_hourly = cursor.fetchall()

    # 站点对应的当前 24h 雨 (用站点所在区县 r24 近似)
    rain_idx = {(rc, ts.replace(minute=0, second=0, microsecond=0)): float(r24 or 0)
                for rc, _, _, _, ts, _, r24 in region_hourly}

    landslide_alerts = []
    last_lvl_station: dict[str, int] = {}
    for station_code, hour_ts, sm, disp, region_code, station_name, lon, lat, region_name in geo_hourly:
        if region_code is None:
            continue
        r24 = rain_idx.get((region_code, hour_ts), 0.0)
        risk = landslide_risk(r24, float(sm or 0), float(disp or 0))
        if risk >= 2 and risk > last_lvl_station.get(station_code, 0):
            landslide_alerts.append({
                "ts": hour_ts,
                "region": region_code,
                "region_name": region_name,
                "station": station_code,
                "station_name": station_name,
                "lon": lon, "lat": lat,
                "level": risk,
                "r24": r24,
                "sm": sm, "disp": disp,
            })
            last_lvl_station[station_code] = risk
    print(f"    滑坡预警 {len(landslide_alerts)} 条")

    # 地震次生 — 主震后立即触发
    quake_alerts = []
    for q in quake_events:
        if q["magnitude"] >= 5.0:
            lvl = 4 if q["magnitude"] >= 6.5 else (3 if q["magnitude"] >= 6.0 else 2)
            quake_alerts.append({
                "ts": q["occurred_at"],
                "region": q["region_code"],
                "lon": q["lon"], "lat": q["lat"],
                "level": lvl,
                "magnitude": q["magnitude"],
                "name": q["name"],
            })
    print(f"    地震次生预警 {len(quake_alerts)} 条")

    # 把预警和事件写库
    event_id_for_alert: list[tuple[int, dict, str]] = []

    # 1. 暴雨 -> 灾害事件 + 预警 (取每个区县最严的一次)
    grouped: dict[str, dict] = {}
    for a in rainfall_alerts:
        cur = grouped.get(a["region"])
        if not cur or a["level"] > cur["level"]:
            grouped[a["region"]] = a
    for a in grouped.values():
        cursor.execute("""
            INSERT INTO biz.biz_disaster_event
                (code, title, type, level, location, occurred_at, status, region_code, description)
            VALUES (%s, %s, '暴雨', %s,
                    ST_SetSRID(ST_MakePoint(%s, %s), 4326),
                    %s, 1, %s, %s)
            RETURNING id
        """, (
            f"AUTO-RAIN-{a['region']}-{a['ts']:%Y%m%d%H%M}",
            f"{a['region_name']}暴雨",
            a["level"], a["lon"], a["lat"], a["ts"], a["region"],
            f"24h 累计降雨 {a['r24']:.1f}mm, 3h 累计 {a['r3']:.1f}mm",
        ))
        eid = cursor.fetchone()[0]
        event_id_for_alert.append((eid, a, "rule_rainfall"))

    # 2. 滑坡 -> 灾害事件 + 预警 (每个站取最严)
    grouped_ls: dict[str, dict] = {}
    for a in landslide_alerts:
        cur = grouped_ls.get(a["station"])
        if not cur or a["level"] > cur["level"]:
            grouped_ls[a["station"]] = a
    for a in grouped_ls.values():
        cursor.execute("""
            INSERT INTO biz.biz_disaster_event
                (code, title, type, level, location, occurred_at, status, region_code, description)
            VALUES (%s, %s, '滑坡', %s,
                    ST_SetSRID(ST_MakePoint(%s, %s), 4326),
                    %s, 1, %s, %s)
            RETURNING id
        """, (
            f"AUTO-LS-{a['station']}-{a['ts']:%Y%m%d%H%M}",
            f"{a['station_name']}滑坡风险",
            a["level"], a["lon"], a["lat"], a["ts"], a["region"],
            f"24h 雨量 {a['r24']:.1f}mm | 含水率 {a['sm']:.1f}% | 位移 {a['disp']:.2f}mm",
        ))
        eid = cursor.fetchone()[0]
        event_id_for_alert.append((eid, a, "rule_landslide"))

    # 3. 地震次生
    for a in quake_alerts:
        cursor.execute("""
            INSERT INTO biz.biz_disaster_event
                (code, title, type, level, location, occurred_at, status, region_code, description)
            VALUES (%s, %s, '地震次生', %s,
                    ST_SetSRID(ST_MakePoint(%s, %s), 4326),
                    %s, 1, %s, %s)
            RETURNING id
        """, (
            f"AUTO-EQ-{a['ts']:%Y%m%d%H%M%S}",
            f"M{a['magnitude']} 地震次生灾害",
            a["level"], a["lon"], a["lat"], a["ts"], a["region"],
            f"震源深度 16km, 震中{a['name']}, 周边 50km 内地质灾害风险升级",
        ))
        eid = cursor.fetchone()[0]
        event_id_for_alert.append((eid, a, "rule_quake_secondary"))

    # 写预警 (同步事件)
    type_label = {1: "蓝色", 2: "黄色", 3: "橙色", 4: "红色"}
    for eid, a, src in event_id_for_alert:
        title = f"{type_label[a['level']]}预警 - "
        if src == "rule_rainfall":
            title += f"{a['region_name']}暴雨"
            content = f"24h 累计 {a['r24']:.1f}mm, 1h 雨强 {a['r1']:.1f}mm/h"
        elif src == "rule_landslide":
            title += f"{a['station_name']}滑坡"
            content = f"含水率 {a['sm']:.1f}%, 位移 {a['disp']:.2f}mm, 24h 雨量 {a['r24']:.1f}mm"
        else:
            title += f"M{a['magnitude']} 地震次生"
            content = f"主震 + 周边滑坡崩塌风险, 持续监测中"

        cursor.execute("""
            INSERT INTO biz.biz_alert
                (code, title, content, level, event_id, source, location, channels,
                 status, triggered_at)
            VALUES (%s, %s, %s, %s, %s, %s,
                    ST_SetSRID(ST_MakePoint(%s, %s), 4326),
                    'station,sms', 1, %s)
        """, (
            f"ALERT-{eid}",
            title, content, a["level"], eid, src,
            a["lon"], a["lat"], a["ts"],
        ))

    print(f"  ✓ 灾害事件 + 预警 共 {len(event_id_for_alert)} 条")


# ========================= 主流程 =========================

def main():
    print("[1/6] 连接数据库 ...")
    conn = psycopg2.connect(DB_DSN)
    conn.autocommit = False
    try:
        with conn.cursor() as cur:
            print("[2/6] 生成监测站 ...")
            ws = gen_weather_stations(cur)
            gs = gen_geo_stations(cur)
            insert_stations(cur, ws + gs)
            print(f"    气象站 {len(ws)} 个, 地质传感器 {len(gs)} 个")
            conn.commit()

            print("[3/6] 生成气象时序 ...")
            gen_weather_series(cur, ws)
            conn.commit()

            print("[4/6] 生成地质传感器时序 ...")
            gen_geo_series(cur, gs)
            conn.commit()

            print("[5/6] 生成地震事件 ...")
            quakes = gen_earthquake_events(cur)
            conn.commit()

            print("[6/6] 规则引擎计算灾害与预警 ...")
            gen_disasters_and_alerts(cur, ws, gs, quakes)
            conn.commit()

            cur.execute("SELECT COUNT(*) FROM biz.biz_weather_observation")
            print(f"\n=== 验收 ===")
            print(f"weather_observation: {cur.fetchone()[0]}")
            cur.execute("SELECT COUNT(*) FROM biz.biz_geo_sensor_reading")
            print(f"geo_sensor_reading : {cur.fetchone()[0]}")
            cur.execute("SELECT COUNT(*) FROM biz.biz_earthquake_event")
            print(f"earthquake_event   : {cur.fetchone()[0]}")
            cur.execute("SELECT COUNT(*) FROM biz.biz_disaster_event WHERE code LIKE 'AUTO-%'")
            print(f"disaster_event AUTO: {cur.fetchone()[0]}")
            cur.execute("SELECT COUNT(*) FROM biz.biz_alert WHERE source LIKE 'rule_%'")
            print(f"alert (rule)       : {cur.fetchone()[0]}")
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


if __name__ == "__main__":
    main()
