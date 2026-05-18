package com.gcsj.disaster.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.geojson.GeoJsonReader;
import org.locationtech.jts.io.geojson.GeoJsonWriter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 空间数据工具 - GeoJSON 与 JTS Geometry 互转
 * 数据存储坐标系: EPSG:4326
 */
public final class GeometryUtil {

    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private static final GeoJsonReader READER = new GeoJsonReader(FACTORY);
    private static final GeoJsonWriter WRITER = new GeoJsonWriter();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        WRITER.setEncodeCRS(false);
    }

    private GeometryUtil() {
    }

    public static Point point(double lon, double lat) {
        Point p = FACTORY.createPoint(new Coordinate(lon, lat));
        p.setSRID(4326);
        return p;
    }

    public static Geometry fromGeoJson(String geoJson) {
        try {
            Geometry g = READER.read(geoJson);
            g.setSRID(4326);
            return g;
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid GeoJSON geometry: " + e.getMessage(), e);
        }
    }

    public static String toGeoJson(Geometry geometry) {
        if (geometry == null) return null;
        return WRITER.write(geometry);
    }

    /** 包装成完整 Feature {type,geometry,properties} */
    public static Map<String, Object> toFeature(Long id, Geometry geometry, Map<String, Object> properties) {
        Map<String, Object> feature = new LinkedHashMap<>();
        feature.put("type", "Feature");
        feature.put("id", id);
        try {
            JsonNode geom = MAPPER.readTree(toGeoJson(geometry));
            feature.put("geometry", geom);
        } catch (Exception e) {
            feature.put("geometry", null);
        }
        feature.put("properties", properties);
        return feature;
    }

    public static Map<String, Object> toFeatureCollection(java.util.List<Map<String, Object>> features) {
        Map<String, Object> fc = new LinkedHashMap<>();
        fc.put("type", "FeatureCollection");
        fc.put("features", features);
        return fc;
    }
}
