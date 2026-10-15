package com.gcsj.disaster.gis;

import com.gcsj.disaster.common.BusinessException;
import com.gcsj.disaster.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * GeoServer 客户端占位实现
 *
 * 当前阶段只暴露接口、不实际调用 GeoServer。
 * 真正接入时新建 GeoServerClientImpl（使用 RestClient/WebClient 调 /rest/* 接口），
 * 把这里的 @Component 移除即可由新实现替换。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeoServerClientStub implements GeoServerClient {

    private final GeoServerProperties properties;

    @Override
    public void ensureWorkspace(String workspace) {
        log.warn("[geoserver-stub] ensureWorkspace 未实现 ws={} url={}", workspace, properties.getUrl());
        throw new BusinessException(ErrorCode.GEOSERVER_ERROR, "GeoServer 自动发布尚未实现");
    }

    @Override
    public void ensureDatastore(String workspace, String datastore) {
        log.warn("[geoserver-stub] ensureDatastore 未实现 ws={} ds={}", workspace, datastore);
        throw new BusinessException(ErrorCode.GEOSERVER_ERROR, "GeoServer 自动发布尚未实现");
    }

    @Override
    public void publishFeatureType(String workspace, String datastore, String pgTable, String layerName) {
        log.warn("[geoserver-stub] publishFeatureType 未实现 ws={} ds={} table={} layer={}",
                workspace, datastore, pgTable, layerName);
        throw new BusinessException(ErrorCode.GEOSERVER_ERROR, "GeoServer 自动发布尚未实现");
    }

    @Override
    public void bindStyle(String workspace, String layerName, String styleName) {
        log.warn("[geoserver-stub] bindStyle 未实现 ws={} layer={} style={}", workspace, layerName, styleName);
        throw new BusinessException(ErrorCode.GEOSERVER_ERROR, "GeoServer 自动发布尚未实现");
    }
}
