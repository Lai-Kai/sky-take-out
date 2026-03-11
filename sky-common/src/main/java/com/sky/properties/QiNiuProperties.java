package com.sky.properties;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "sky.qiniu")
@Data
public class QiNiuProperties {
    private String accessKey;
    private String secretKey;
    private String bucketName;
    private String hostName;
    /**
     * public: return public URL; private-signed: return time-limited signed URL.
     */
    private String urlMode = "public";
    /**
     * Expiration time in seconds for private signed URL.
     */
    private Long privateUrlExpireSeconds = 3600L;
}
