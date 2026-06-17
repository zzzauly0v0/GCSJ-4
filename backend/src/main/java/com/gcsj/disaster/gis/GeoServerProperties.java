package com.gcsj.disaster.gis;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "geoserver")
public class GeoServerProperties {
    private String url = "http://localhost:8600/geoserver";
    private String username = "admin";
    private String password = "gcsj123";
    private String defaultWorkspace = "gcsj";
    private String defaultDatastore = "gcsj-pg";
}
