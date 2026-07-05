import numpy as np
import pandas as pd
from scipy.spatial import KDTree
from scipy import ndimage
import os
import gc
import logging

# ====================== 日志配置 ======================
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(levelname)s - %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S"
)
logger = logging.getLogger("LandslideMonitor")

# =============================================================================
# 四川浅层滑坡全域风险监测程序（v2 — 多层级过滤 + 复合风险指数 + 空间聚类）
# 优化点：
#   1. 气象降水使用P95替代均值（捕获极端降雨事件）
#   2. 地形预过滤：坡度>=10° + 海拔500-3000m（滑坡高发带）
#   3. TRIGRS Ks参数校准 + 降水强度门控（<5mm不评估）
#   4. 复合风险指数CRI替代单一Fs阈值
#   5. 空间连通域聚类 + 最小面积过滤（剔除孤立像元）
# =============================================================================

# ====================== 1. TRIGRS岩土参数（四川浅层风化残坡积土实测区间校准） ======================
c_prime = 12.0              # 有效黏聚力 kPa（实测5~15kPa，取中间值）
phi_prime = np.radians(28.0)  # 内摩擦角22~32°，取28°
gamma_s = 19.0              # 饱和土重度 kN/m3
gamma_w = 9.81              # 水重度 kN/m3
gamma_t = 17.2              # 天然土重度 kN/m3
soil_thickness_z = 2.0      # 浅层滑坡平均土层厚度0.5~3m，统一2m

# Ks 校准说明：
#   原值 0.0006 量纲模糊（若为 m/day 则对应 ~6.9×10⁻⁹ m/s，过于致密，
#   小雨即饱和，导致大量网格 Fs 偏低）。
#   四川风化残坡积粉质黏土 Ks 典型范围 10⁻⁶~10⁻⁷ m/s 即 0.0086~0.086 m/day，
#   取 0.05 m/day（~5.8×10⁻⁷ m/s）为中间合理值。
Ks = 0.05  # m/day，饱和导水率

# 降水强度门控阈值（m/day）：有效降雨低于此值不评估，直接标记稳定
PRECIP_GATE = 0.005  # 相当于 5mm/day


def calc_KT(T):
    """温度修正系数（国标DZ/T 0449-2023）"""
    T = np.asarray(T, dtype=float)
    return np.select([T < 0, T <= 26], [0.22, 1.0 - 0.011 * T], default=0.62)


def calc_KRH(RH):
    """相对湿度修正系数（保留作为辅助特征，不参与Re计算）"""
    return np.asarray(RH, dtype=float) / 82


def calc_Kw(wind):
    """风速修正系数（保留作为辅助特征）"""
    return np.exp(-0.058 * np.asarray(wind, dtype=float))


def calc_KP(P):
    """气压修正系数（保留作为辅助特征）"""
    P = np.asarray(P, dtype=float)
    return 1 - (P - 1000) * 0.00075


def elevation_susceptibility(dem):
    """
    四川滑坡海拔敏感性函数。
    基于文献：四川浅层滑坡 70%+ 集中在 500-1500m 过渡丘陵带，
    1500-3000m 中低山次之，<500m 盆地和 >3000m 高寒带极少。
    返回 [0, 1] 权重值。
    """
    dem = np.asarray(dem, dtype=float)
    score = np.zeros_like(dem)
    score[(dem >= 500) & (dem < 1500)] = 0.9   # 过渡丘陵带，滑坡最高发
    score[(dem >= 1500) & (dem < 3000)] = 0.6   # 中低山坡，次高发
    score[dem < 500] = 0.1                       # 盆地平原，极少滑坡
    score[dem >= 3000] = 0.2                     # 高寒地带，冻融为主
    return score


# ====================== 2. 读取气象CSV（兼容GBK/UTF8、缺测/极值清洗） ======================
def read_weather_csv(path):
    """读取原始气象日值CSV，返回清洗后的DataFrame"""
    try:
        df = pd.read_csv(path, encoding="utf-8-sig", low_memory=False)
    except UnicodeDecodeError:
        df = pd.read_csv(path, encoding="gbk", low_memory=False)

    # 缺测标记替换
    df = df.replace([999999, 999990], np.nan)
    df.rename(columns={
        "经度": "lon",
        "纬度": "lat",
        "平均气温": "temp",
        "平均相对湿度": "humidity",
        "平均气压": "pressure",
        "平均2分钟风速": "wind_speed",
        "20-20时降水量": "precip"
    }, inplace=True)

    keep_cols = ["lon", "lat", "temp", "humidity", "pressure", "wind_speed", "precip"]
    exist_cols = [c for c in keep_cols if c in df.columns]
    df = df[exist_cols]

    for c in exist_cols:
        df[c] = pd.to_numeric(df[c], errors="coerce")

    # 按站点空间均值填充缺测
    if len(df) > 10:
        df[exist_cols[2:]] = df.groupby(["lon", "lat"])[exist_cols[2:]].transform(
            lambda x: x.fillna(x.mean()))
    df = df.fillna(df.mean())

    # 气象极值物理约束清洗
    df["temp"] = np.clip(df["temp"], -20, 45)
    df["humidity"] = np.clip(df["humidity"], 0, 100)
    df["precip"] = np.clip(df["precip"], 0, 1200)
    df["wind_speed"] = np.clip(df["wind_speed"], 0, 40)
    df["pressure"] = np.clip(df["pressure"], 800, 1050)
    return df


def aggregate_weather(df_raw):
    """
    【v2新增】按站点聚合气象日值数据。
    - 降水使用 P95（95分位值），捕获极端降雨事件
    - 气温、湿度、气压、风速使用均值
    - 同时保留 precip_max 和 precip_mean 作为辅助特征
    """
    agg_funcs = {
        "temp": "mean",
        "humidity": "mean",
        "pressure": "mean",
        "wind_speed": "mean",
        "precip": lambda x: np.percentile(x, 95),  # P95 极端降水
    }
    df_agg = df_raw.groupby(["lon", "lat"]).agg(agg_funcs).reset_index()

    # 额外计算降水的均值和最大值（辅助特征，不参与插值但保留）
    precip_extra = df_raw.groupby(["lon", "lat"])["precip"].agg([
        ("precip_max", "max"),
        ("precip_mean", "mean"),
        ("precip_p95", lambda x: np.percentile(x, 95)),
    ]).reset_index()

    # 合并
    df_agg = df_agg.merge(precip_extra[["lon", "lat", "precip_max", "precip_mean"]],
                          on=["lon", "lat"], how="left")

    logger.info(f"气象站点聚合完成：{len(df_agg)} 个站点，"
                f"降水P95均值={df_agg['precip'].mean():.1f}mm, "
                f"降水P95中位数={df_agg['precip'].median():.1f}mm")
    return df_agg


# ====================== 3. 读取栅格CSV ======================
def read_raster_csv(file_path):
    df = pd.read_csv(file_path, sep=",", header=0)
    df.rename(columns={"X": "lon", "Y": "lat", "VALUE": "val"}, inplace=True)
    return df


# ====================== 4. 构建坡度KDTree ======================
def build_slope_kdtree(slope_path):
    logger.info("读取坡度文件，构建空间索引...")
    slope_df = read_raster_csv(slope_path)
    slope_coords = slope_df[["lon", "lat"]].values
    slope_values = slope_df["val"].values
    kd = KDTree(slope_coords)
    logger.info(f"坡度KDTree构建完成，总网格数：{len(slope_coords)}")
    return kd, slope_values


# ====================== 5. DEM+坡度融合 + 多层级地形预过滤 ======================
def merge_dem_slope(dem_path, slope_kd, slope_vals, tol=0.003):
    """
    读取DEM并匹配坡度栅格，执行多层级地形预过滤：
      第1层：坡度 >= 10°（浅层滑坡最小坡度阈值）
      第2层：海拔 500-3000m（四川滑坡高发高程带）
    """
    logger.info("读取DEM并匹配坡度栅格...")
    dem_df = read_raster_csv(dem_path)
    dem_df.rename(columns={"val": "dem"}, inplace=True)
    dem_coords = dem_df[["lon", "lat"]].values
    dists, idx = slope_kd.query(dem_coords, k=1)

    valid_mask = dists < tol
    n_before = len(dem_df)
    dem_df = dem_df[valid_mask].reset_index(drop=True)
    dem_df["slope"] = slope_vals[idx[valid_mask]]
    n_after_match = len(dem_df)
    logger.info(f"DEM-坡度匹配：{n_before} → {n_after_match} ({n_before - n_after_match} 匹配失败剔除)")

    # ---- 第1层过滤：坡度 >= 10° ----
    n_before = len(dem_df)
    dem_df = dem_df[dem_df["slope"] >= 10.0].copy()
    n_after_slope = len(dem_df)
    logger.info(f"坡度过滤 (>=10°)：{n_before} → {n_after_slope} "
                f"({n_before - n_after_slope} 平缓区剔除, {100*n_after_slope/n_before:.1f}% 保留)")

    # ---- 第2层过滤：海拔 500-3000m ----
    n_before = len(dem_df)
    dem_df = dem_df[(dem_df["dem"] >= 500) & (dem_df["dem"] <= 3000)].copy()
    n_after_elev = len(dem_df)
    logger.info(f"海拔过滤 (500-3000m)：{n_before} → {n_after_elev} "
                f"({n_before - n_after_elev} 非滑坡高发带剔除, {100*n_after_elev/n_before:.1f}% 保留)")

    logger.info(f"地形预过滤完成，有效网格总数：{len(dem_df)} "
                f"(原始匹配 {n_after_match} 的 {100*len(dem_df)/n_after_match:.1f}%)")
    return dem_df


# ====================== 6. 改进IDW插值：增加气温/降水垂直梯度校正 ======================
def idw_interpolate(terrain_df, weather_df, power=2, max_dist=1.2):
    """
    带地形梯度校正的IDW气象插值。
    降水使用P95聚合值，经垂直梯度校正后用于滑坡触发评估。
    """
    logger.info("执行带地形梯度校正的IDW气象插值...")
    site_lonlat = weather_df[["lon", "lat"]].values
    # 插值字段：temp, humidity, pressure, wind_speed, precip(P95)
    interp_cols = ["temp", "humidity", "pressure", "wind_speed", "precip"]
    site_data = weather_df[interp_cols].values
    feat_names = list(interp_cols)

    weather_kd = KDTree(site_lonlat)
    grid_lonlat = terrain_df[["lon", "lat"]].values
    grid_dem = terrain_df["dem"].values
    n_grids = len(grid_lonlat)
    n_features = len(feat_names)
    grid_res = np.zeros((n_grids, n_features))
    chunk_size = 50000
    n_sites = len(site_lonlat)
    site_mean_elev = 600.0

    for start in range(0, n_grids, chunk_size):
        end = min(start + chunk_size, n_grids)
        batch = grid_lonlat[start:end]
        batch_elev = grid_dem[start:end]
        k_nearest = min(30, n_sites)
        dists_batch, idx_batch = weather_kd.query(batch, k=k_nearest)

        for i_local in range(len(batch)):
            i_global = start + i_local
            dists = dists_batch[i_local]
            valid_mask = dists < max_dist
            if not valid_mask.any():
                base = weather_df[feat_names].mean().values
                elev_diff = batch_elev[i_local] - site_mean_elev
                temp_idx = feat_names.index("temp") if "temp" in feat_names else -1
                precip_idx = feat_names.index("precip") if "precip" in feat_names else -1
                if temp_idx != -1:
                    base[temp_idx] -= elev_diff * 0.006
                if precip_idx != -1:
                    base[precip_idx] *= (1 + elev_diff / 3000)
                grid_res[i_global] = base
                continue

            d_valid = dists[valid_mask]
            s_valid = site_data[idx_batch[i_local][valid_mask]]
            weights = 1 / (d_valid ** power)
            weights = weights / weights.sum()
            raw_interp = np.sum(s_valid * weights.reshape(-1, 1), axis=0)

            # 海拔梯度校正
            elev_diff = batch_elev[i_local] - site_mean_elev
            temp_idx = feat_names.index("temp") if "temp" in feat_names else -1
            precip_idx = feat_names.index("precip") if "precip" in feat_names else -1
            if temp_idx != -1:
                raw_interp[temp_idx] -= elev_diff * 0.006
            if precip_idx != -1:
                raw_interp[precip_idx] *= (1 + elev_diff / 3000)
            grid_res[i_global] = raw_interp

        del batch, dists_batch, idx_batch

    for j, feat in enumerate(feat_names):
        terrain_df[feat] = grid_res[:, j]

    logger.info(f"气象插值完成，网格数：{n_grids}")
    return terrain_df


# ====================== 7. TRIGRS无限边坡模型（校准版） ======================
def calc_trigrs(df):
    """
    TRIGRS物理模型计算安全系数Fs（v2校准版）。
    改动：
      - Ks 校准为 0.05 m/day（风化残坡积粉质黏土）
      - 降水强度门控：Re < 5mm 直接 Fs=999（稳定，跳过孔隙水压计算）
    """
    logger.info("TRIGRS物理模型计算安全系数Fs（校准版）...")
    Rr = df["precip"].values / 1000  # mm → m
    T = df["temp"].values
    slope_rad = np.radians(df["slope"].values)

    # 有效降雨（仅温度修正，国标DZ/T 0449-2023）
    Re = Rr * calc_KT(T)

    # ---- 降水强度门控 ----
    # 有效降雨 < 5mm/day 不足以触发孔隙水压积累，直接标记为稳定
    gate_mask = Re >= PRECIP_GATE
    n_gated = (~gate_mask).sum()
    if n_gated > 0:
        logger.info(f"降水门控：{n_gated} 个网格 ({(~gate_mask).mean()*100:.1f}%) "
                    f"有效降雨 < {PRECIP_GATE*1000:.0f}mm，直接标记稳定")

    # 暂态孔隙水压水头 h = (Re/Ks) * (1 - exp(-Ks*z/Re))
    h = np.zeros_like(Re)
    mask_calc = gate_mask & (Re >= 1e-8)
    h[mask_calc] = (Re[mask_calc] / Ks) * (
        1 - np.exp(-Ks * soil_thickness_z / Re[mask_calc]))
    h = np.clip(h, 0, soil_thickness_z)

    # TRIGRS标准Fs公式
    cos_slope = np.cos(slope_rad)
    sin_slope = np.sin(slope_rad)
    numer = c_prime + (gamma_s - gamma_w * h) * (cos_slope ** 2) * np.tan(phi_prime)
    denom = gamma_t * soil_thickness_z * sin_slope * cos_slope
    Fs = np.divide(numer, denom, where=np.abs(denom) >= 1e-10,
                   out=np.full_like(numer, 999.0))

    # 降水门控网格直接 Fs=999
    Fs[~gate_mask] = 999.0

    df["Reff"] = Re * 1000  # 转回mm
    df["pore_h"] = h
    df["Fs"] = Fs

    fs_valid = Fs[Fs < 999]
    n = len(fs_valid)
    if n > 0:
        p_unstable = (fs_valid < 1.0).mean() * 100
        p_critical = ((fs_valid >= 1.0) & (fs_valid < 1.2)).mean() * 100
        p_stable = (fs_valid >= 1.2).mean() * 100
        logger.info(f"有效Fs分布（降水达标网格 {n} 个）："
                    f"不稳定{p_unstable:.1f}% | 临界{p_critical:.1f}% | 稳定{p_stable:.1f}%")

    logger.info("TRIGRS计算完成")
    return df


# ====================== 8. 复合风险指数 CRI ======================
def calc_composite_risk(df):
    """
    【v2新增】计算复合风险指数 CRI (Composite Risk Index)。
    融合 Fs、坡度、降水强度、海拔敏感性 四个维度，输出 [0,1] 评分。

    CRI = 0.40*fs_score + 0.25*slope_score + 0.20*precip_score + 0.15*elev_score

    各因子归一化到 [0,1]，越接近 1 风险越高。
    """
    logger.info("计算复合风险指数 CRI...")
    Fs = df["Fs"].values
    slope = df["slope"].values
    precip = df["precip"].values  # P95 日降水量 (mm)
    dem = df["dem"].values

    # Fs → 风险得分 (sigmoid, Fs越低→得分越高)
    # 裁剪Fs避免exp溢出（Fs=999时 exp(8000) 溢出）
    Fs_clipped = np.clip(Fs, 0.1, 50.0)
    fs_score = 1.0 / (1.0 + np.exp(8.0 * (Fs_clipped - 1.1)))
    # Fs=999 (降水门控) 强制得分=0
    fs_score[Fs >= 999] = 0.0

    # 坡度 → 风险得分 (10-40° 线性映射)
    slope_score = np.clip((slope - 10.0) / 30.0, 0, 1)

    # 降水 → 风险得分 (10-50mm 线性映射)
    precip_score = np.clip((precip - 10.0) / 40.0, 0, 1)

    # 海拔敏感性 → 风险得分
    elev_score = elevation_susceptibility(dem)

    # 加权综合
    CRI = (0.40 * fs_score + 0.25 * slope_score +
           0.20 * precip_score + 0.15 * elev_score)

    df["fs_score"] = fs_score
    df["slope_score"] = slope_score
    df["precip_score"] = precip_score
    df["elev_score"] = elev_score
    df["CRI"] = CRI

    # 统计
    cri_valid = CRI[Fs < 999]
    if len(cri_valid) > 0:
        logger.info(f"CRI统计：均值={cri_valid.mean():.4f}, "
                    f"中位数={np.median(cri_valid):.4f}, "
                    f"P90={np.percentile(cri_valid, 90):.4f}, "
                    f"P95={np.percentile(cri_valid, 95):.4f}")

    logger.info("CRI计算完成")
    return df


# ====================== 9. 空间聚类过滤 ======================
def spatial_cluster_filter(df, cri_threshold=0.40, min_cluster_size=6):
    """
    【v2新增】空间连通域聚类 + 最小面积过滤。

    步骤：
      1. 标记 CRI >= cri_threshold 的网格为风险网格
      2. 使用 8-邻域连通域标记 (scipy.ndimage.label)
      3. 剔除像元数 < min_cluster_size 的孤立聚类
      4. 为保留的聚类分配 cluster_id

    参数：
      cri_threshold: CRI 阈值，>= 此值视为风险网格
      min_cluster_size: 最小聚类像元数（默认 6，约 0.15 km^2 @ 300m分辨率）

    返回：
      df: 新增 cluster_id 列（0=非风险/被过滤，>0=有效聚类编号）
      cluster_summary: 聚类摘要 DataFrame
    """
    logger.info(f"空间聚类分析 (CRI>={cri_threshold}, 最小聚类面积={min_cluster_size}像元)...")

    # 标记风险网格
    risk_mask = (df["CRI"].values >= cri_threshold) & (df["Fs"].values < 999)
    n_risk_before = risk_mask.sum()
    logger.info(f"CRI阈值过滤前风险网格：{n_risk_before}")

    if n_risk_before == 0:
        logger.warning("无风险网格通过CRI阈值，跳过聚类")
        df["cluster_id"] = 0
        return df, pd.DataFrame()

    # 构建稀疏网格的连通域标记
    # 由于网格点不一定是规则格网，采用基于坐标距离的连通性判断
    # 8-邻域等价于：两网格经纬度差 <= 分辨率 * sqrt(2)
    # DEM分辨率约 0.003° → 对角线距离 ≈ 0.0042°
    lons = df["lon"].values
    lats = df["lat"].values
    risk_indices = np.where(risk_mask)[0]
    n_total = len(df)

    # 为风险网格构建 KDTree 用于邻域搜索
    risk_coords = np.column_stack([lons[risk_indices], lats[risk_indices]])
    if len(risk_coords) == 0:
        df["cluster_id"] = 0
        return df, pd.DataFrame()

    risk_tree = KDTree(risk_coords)
    # 搜索半径：约 1.5 倍网格分辨率 (考虑对角邻域)
    search_radius = 0.005  # ~500m，覆盖 8-邻域

    # 使用并查集做连通域合并
    parent = np.arange(len(risk_indices))

    def find(x):
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    def union(x, y):
        rx, ry = find(x), find(y)
        if rx != ry:
            parent[rx] = ry

    # 批量邻域搜索
    chunk = 10000
    for start in range(0, len(risk_indices), chunk):
        end = min(start + chunk, len(risk_indices))
        batch = risk_coords[start:end]
        pairs = risk_tree.query_ball_point(batch, r=search_radius, return_sorted=False)
        for i_local, neighbors in enumerate(pairs):
            i_global = start + i_local
            for nb in neighbors:
                if nb != i_global:
                    union(i_global, nb)

    # 统计各连通域
    root_to_idx = {}
    cluster_sizes = {}
    for i in range(len(risk_indices)):
        r = find(i)
        if r not in root_to_idx:
            root_to_idx[r] = len(root_to_idx) + 1  # cluster_id 从1开始
        cid = root_to_idx[r]
        cluster_sizes[cid] = cluster_sizes.get(cid, 0) + 1

    # 过滤小聚类
    valid_clusters = {cid for cid, sz in cluster_sizes.items()
                      if sz >= min_cluster_size}

    # 构建 cluster_id 数组
    cluster_id_arr = np.zeros(n_total, dtype=int)
    id_map = {}  # risk_indices index → cluster_id
    for i in range(len(risk_indices)):
        r = find(i)
        cid = root_to_idx[r]
        if cid in valid_clusters:
            id_map[i] = cid

    for i_local, cid in id_map.items():
        cluster_id_arr[risk_indices[i_local]] = cid

    df["cluster_id"] = cluster_id_arr

    n_risk_after = len(id_map)
    n_clusters = len(valid_clusters)
    logger.info(f"空间聚类完成：{n_risk_before} → {n_risk_after} 风险网格 "
                f"({n_clusters} 个有效聚类, "
                f"{len(root_to_idx) - n_clusters} 个孤立聚类被剔除)")

    # 构建聚类摘要
    if n_clusters > 0:
        summaries = []
        for cid in sorted(valid_clusters):
            mask_c = df["cluster_id"].values == cid
            n_cells = mask_c.sum()
            avg_cri = df.loc[mask_c, "CRI"].mean()
            avg_fs = df.loc[mask_c, "Fs"].mean()
            avg_slope = df.loc[mask_c, "slope"].mean()
            avg_precip = df.loc[mask_c, "precip"].mean()
            center_lon = df.loc[mask_c, "lon"].mean()
            center_lat = df.loc[mask_c, "lat"].mean()
            min_lon, max_lon = df.loc[mask_c, "lon"].min(), df.loc[mask_c, "lon"].max()
            min_lat, max_lat = df.loc[mask_c, "lat"].min(), df.loc[mask_c, "lat"].max()
            area_km2 = n_cells * (0.003 * 111000) ** 2 / 1e6  # 粗略面积估算
            summaries.append({
                "cluster_id": cid,
                "n_cells": n_cells,
                "area_km2": round(area_km2, 3),
                "center_lon": round(center_lon, 4),
                "center_lat": round(center_lat, 4),
                "lon_range": f"{min_lon:.4f}-{max_lon:.4f}",
                "lat_range": f"{min_lat:.4f}-{max_lat:.4f}",
                "avg_CRI": round(avg_cri, 4),
                "avg_Fs": round(avg_fs, 4),
                "avg_slope": round(avg_slope, 1),
                "avg_precip_mm": round(avg_precip, 1),
            })
        cluster_summary = pd.DataFrame(summaries)
        logger.info(f"聚类摘要：面积范围 {cluster_summary['area_km2'].min():.3f} ~ "
                    f"{cluster_summary['area_km2'].max():.3f} km^2, "
                    f"平均CRI范围 {cluster_summary['avg_CRI'].min():.4f} ~ "
                    f"{cluster_summary['avg_CRI'].max():.4f}")
    else:
        cluster_summary = pd.DataFrame()

    return df, cluster_summary


# ====================== 10. 基于CRI的风险分级 ======================
def classify_risk_by_cri(df, cluster_summary, out_path_grid, out_path_cluster,
                         output_threshold=0.50):
    """
    【v2重构】基于复合风险指数 CRI 进行风险分级。

    分级标准（论文用，全量标记）：
      - 极高风险: CRI >= 0.60
      - 高风险:   CRI >= 0.50
      - 中风险:   CRI >= 0.40
      - 低风险:   CRI < 0.40

    输出策略：
      - 主输出: output_threshold（默认0.50=高+极高）及以上网格
      - 中风险(0.40-0.50)仅统计汇报、不输出，避免结果过多
      - 如需全部中及以上，设置 output_threshold=0.40

    输出：
      1. 风险网格明细 CSV（含 CRI 分项得分 + cluster_id）
      2. 聚类摘要 CSV
    """
    logger.info("基于复合风险指数CRI进行风险分级...")

    # 同时保留基于 Fs 的 landslide_prob 字段（兼容论文中的概率汇报）
    Fs_clipped_prob = np.clip(df["Fs"].values, 0.1, 50.0)
    df["landslide_prob"] = 1.0 / (1.0 + np.exp(8.0 * (Fs_clipped_prob - 1.1)))
    df.loc[df["Fs"] >= 999, "landslide_prob"] = 0.0

    def risk_label(cri):
        if cri >= 0.60:
            return "极高风险(红橙预警)"
        elif cri >= 0.50:
            return "高风险(橙色预警)"
        elif cri >= 0.40:
            return "中风险(黄色预警)"
        else:
            return "低风险"

    df["risk_level"] = df["CRI"].apply(risk_label)

    # 输出 >= output_threshold 的网格（默认0.50：高+极高）
    df_risk = df[df["CRI"] >= output_threshold].copy()
    df_risk.to_csv(out_path_grid, index=False, encoding="utf-8-sig")
    n_output = len(df_risk)
    n_mid = (df["risk_level"] == "中风险(黄色预警)").sum()
    logger.info(f"风险网格明细输出：{out_path_grid}，共 {n_output} 个网格 "
                f"(阈值>={output_threshold}；另有 {n_mid} 中风险网格仅统计不输出)")

    # 输出聚类摘要
    if len(cluster_summary) > 0:
        cluster_summary.to_csv(out_path_cluster, index=False, encoding="utf-8-sig")
        logger.info(f"聚类摘要输出：{out_path_cluster}，共 {len(cluster_summary)} 个聚类")
    else:
        logger.info("无有效聚类输出")

    return df, df_risk


# ====================== 11. 统计报告 ======================
def print_reasonableness_report(df, cluster_summary, year):
    print(f"\n{'='*60}")
    print(f"  {year} 年结果合理性检查报告 (v2 多层级过滤)")
    print(f"{'='*60}")

    # Fs 统计（仅有效计算网格）
    fs_valid = df[df["Fs"] < 999]["Fs"]
    n_fs_valid = len(fs_valid)
    n_total = len(df)
    n_gated = (df["Fs"] >= 999).sum()
    print(f"\n[网格统计]")
    print(f"总网格（地形过滤后）：{n_total}")
    print(f"降水门控跳过（<5mm）：{n_gated} ({100*n_gated/n_total:.1f}%)")
    print(f"有效Fs计算网格：{n_fs_valid} ({100*n_fs_valid/n_total:.1f}%)")

    print(f"\n[Fs安全系数分布]（仅有效计算网格）")
    if n_fs_valid > 0:
        print(f"Fs<1.0不稳定：{(fs_valid<1.0).sum()} ({(fs_valid<1.0).mean()*100:.1f}%)")
        print(f"1.0≤Fs<1.2临界：{((fs_valid>=1.0)&(fs_valid<1.2)).sum()} "
              f"({((fs_valid>=1.0)&(fs_valid<1.2)).mean()*100:.1f}%)")
        print(f"Fs≥1.2稳定：{(fs_valid>=1.2).sum()} ({(fs_valid>=1.2).mean()*100:.1f}%)")
        print(f"Fs均值={fs_valid.mean():.3f}, 中位数={np.median(fs_valid):.3f}")

    # CRI 统计
    cri_all = df["CRI"]
    cri_valid = df[df["Fs"] < 999]["CRI"]
    print(f"\n[复合风险指数CRI分布]")
    print(f"全量网格 CRI: 均值={cri_all.mean():.4f}, 中位数={np.median(cri_all):.4f}")
    if len(cri_valid) > 0:
        print(f"有效网格 CRI: 均值={cri_valid.mean():.4f}, 中位数={np.median(cri_valid):.4f}, "
              f"P95={np.percentile(cri_valid, 95):.4f}")

    # 风险等级分布
    print(f"\n[风险等级统计]")
    risk_counts = df["risk_level"].value_counts()
    print(risk_counts.to_string())

    # 滑坡概率统计
    prob = df["landslide_prob"]
    print(f"\n[滑坡概率] 均值={prob.mean():.4f}, 中位数={np.median(prob):.4f}")

    # 聚类统计
    if len(cluster_summary) > 0:
        print(f"\n[空间聚类统计]")
        print(f"有效聚类总数：{len(cluster_summary)}")
        print(f"聚类面积: 最小={cluster_summary['area_km2'].min():.3f} km^2, "
              f"最大={cluster_summary['area_km2'].max():.3f} km^2, "
              f"平均={cluster_summary['area_km2'].mean():.3f} km^2")
        print(f"总风险面积：{cluster_summary['area_km2'].sum():.1f} km^2")
        # 前5大聚类
        top5 = cluster_summary.nlargest(5, "n_cells")
        print(f"\n前5大风险聚类：")
        for _, row in top5.iterrows():
            print(f"  聚类#{row['cluster_id']}: {row['n_cells']}像元, "
                  f"{row['area_km2']:.3f}km^2, CRI={row['avg_CRI']:.4f}, "
                  f"中心({row['center_lon']:.4f}, {row['center_lat']:.4f})")

    # 输出范围（仅>=0.50 高+极高，对应输出文件）
    high_risk = df[df["CRI"] >= 0.50]
    if len(high_risk) > 0:
        print(f"\n[输出风险网格空间范围] (CRI>=0.50, 对应输出文件)")
        print(f"经度 {high_risk['lon'].min():.2f} ~ {high_risk['lon'].max():.2f}")
        print(f"纬度 {high_risk['lat'].min():.2f} ~ {high_risk['lat'].max():.2f}")
        print(f"海拔 {high_risk['dem'].min():.0f} ~ {high_risk['dem'].max():.0f}m")
        print(f"坡度 {high_risk['slope'].min():.1f} ~ {high_risk['slope'].max():.1f}°")
        print(f"CRI  {high_risk['CRI'].min():.4f} ~ {high_risk['CRI'].max():.4f}")
    print(f"{'='*60}\n")


# ====================== 主程序入口 ======================
if __name__ == "__main__":
    base_dir = r"C:\Users\18990\Desktop\test"
    os.chdir(base_dir)
    years = [2020, 2021, 2022, 2023]

    try:
        # ---- 地形数据仅加载一次 ----
        slope_kd, slope_vals = build_slope_kdtree("slope_sc.csv")
        df_terrain_base = merge_dem_slope("dem_sc.csv", slope_kd, slope_vals, tol=0.003)
        del slope_kd, slope_vals
        gc.collect()

        for year in years:
            logger.info(f"\n{'='*50}")
            logger.info(f"  开始处理 {year} 年")
            logger.info(f"{'='*50}")

            result_grid = f"四川滑坡风险网格_{year}.csv"
            result_cluster = f"四川滑坡风险聚类_{year}.csv"

            df_terrain = df_terrain_base.copy()

            # ---- 气象数据：读取日值 + P95聚合 ----
            weather_file = f"四川气象站点_{year}.csv"
            if not os.path.exists(weather_file):
                logger.error(f"{weather_file} 缺失，跳过{year}年")
                continue

            df_weather_raw = read_weather_csv(weather_file)
            df_weather = aggregate_weather(df_weather_raw)  # v2: P95聚合
            logger.info(f"{year}年有效气象站点：{len(df_weather)}")

            # ---- 四川边界裁剪 ----
            before = len(df_terrain)
            df_terrain = df_terrain[
                (df_terrain["lon"] >= 97.5) & (df_terrain["lon"] <= 108.5) &
                (df_terrain["lat"] >= 26.0) & (df_terrain["lat"] <= 34.5)
            ].copy()
            logger.info(f"四川边界过滤：{before} → {len(df_terrain)} 网格 "
                        f"({100*len(df_terrain)/before:.1f}%)")

            # ---- 气象插值（P95降水） ----
            df_grid = idw_interpolate(df_terrain, df_weather, power=2, max_dist=1.2)

            # ---- TRIGRS计算Fs（校准Ks+降水门控） ----
            df_grid = calc_trigrs(df_grid)

            # ---- 复合风险指数CRI ----
            df_grid = calc_composite_risk(df_grid)

            # ---- 空间聚类过滤 ----
            df_grid, cluster_summary = spatial_cluster_filter(
                df_grid, cri_threshold=0.40, min_cluster_size=6)

            # ---- 风险分级 + 输出 ----
            df_all, df_risk = classify_risk_by_cri(
                df_grid, cluster_summary, result_grid, result_cluster)

            # ---- 统计报告 ----
            print_reasonableness_report(df_all, cluster_summary, year)

            logger.info(f"{year}年处理完成")
            logger.info(f"  风险网格输出：{os.path.join(base_dir, result_grid)}")
            logger.info(f"  聚类摘要输出：{os.path.join(base_dir, result_cluster)}")

            # 内存释放
            del df_grid, df_all, df_risk, df_weather, df_weather_raw, cluster_summary
            gc.collect()

        logger.info("\n======== 全部年份运算完成！========")

    except Exception as e:
        logger.error(f"程序运行异常：{str(e)}", exc_info=True)
