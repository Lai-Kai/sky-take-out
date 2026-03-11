package com.sky.config;

import com.sky.properties.QiNiuProperties;
import com.sky.utils.QiNiuUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class QiNiuConfiguration {

    @Bean
    public QiNiuUtil qiNiuUtil(QiNiuProperties qiNiuProperties) {
        log.info("初始化七牛云工具类，bucket:{}, mode:{}", qiNiuProperties.getBucketName(), qiNiuProperties.getUrlMode());
        return new QiNiuUtil(
                qiNiuProperties.getAccessKey(),
                qiNiuProperties.getSecretKey(),
                qiNiuProperties.getBucketName(),
                qiNiuProperties.getHostName(),
                qiNiuProperties.getUrlMode(),
                qiNiuProperties.getPrivateUrlExpireSeconds()
        );
    }
}
