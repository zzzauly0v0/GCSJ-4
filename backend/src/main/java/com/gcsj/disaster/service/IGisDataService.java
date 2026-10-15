package com.gcsj.disaster.service;

import java.util.Map;

/**
 * 四川空间数据 - 行政区/河流/居民点的 GeoJSON 出口
 */
public interface IGisDataService {

    /**
     * 行政区 FeatureCollection
     * @param level    1 省 / 2 市 / 3 县
     * @param parent   parent_code, level=2 时通常传 510000, 可空
     * @param adcode   单点查询某行政区, 可空
     */
    Map<String, Object> regionsAsGeoJson(Short level, String parent, String adcode);

    /** 全部河流 (LineString) */
    Map<String, Object> riversAsGeoJson();

    /** 全部居民点, type 可选 (city/county/town) */
    Map<String, Object> settlementsAsGeoJson(String type);
}
