package com.gcsj.disaster.gis;

/**
 * GeoServer REST 客户端接口（占位）
 *
 * 后续实现：调用 GeoServer REST API 完成工作区/数据存储/图层/样式的注册。
 * 当前所有方法在实现类中抛 BusinessException(GEOSERVER_ERROR, "未实现")。
 */
public interface GeoServerClient {

    /** 确保工作区存在，不存在则创建 */
    void ensureWorkspace(String workspace);

    /**
     * 确保 PostGIS 数据存储存在，不存在则在指定工作区下创建
     * 连接信息从 application.yml 的 spring.datasource 读
     */
    void ensureDatastore(String workspace, String datastore);

    /**
     * 把 PostGIS 中的表发布为 FeatureType 图层
     * @param workspace 工作区
     * @param datastore 数据存储
     * @param pgTable   PostGIS 表名（schema=public）
     * @param layerName 发布后的图层名（一般与 pgTable 同名）
     */
    void publishFeatureType(String workspace, String datastore, String pgTable, String layerName);

    /** 给已发布的图层绑定默认 SLD 样式 */
    void bindStyle(String workspace, String layerName, String styleName);
}
