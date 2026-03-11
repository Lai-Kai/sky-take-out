package com.sky.utils;

import com.google.gson.Gson;
import com.qiniu.common.QiniuException;
import com.qiniu.http.Response;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.storage.model.DefaultPutRet;
import com.qiniu.util.Auth;
import com.sky.exception.BaseException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Locale;

@Data
@AllArgsConstructor
@Slf4j
public class QiNiuUtil {

    private String accessKey;
    private String secretKey;
    private String bucketName;
    private String hostName;
    private String urlMode;
    private Long privateUrlExpireSeconds;

    /**
     * 文件上传
     *
     * @param bytes 文件字节数组
     * @param objectName 对象名
     * @return 文件访问 URL
     */
    public String upload(byte[] bytes, String objectName) {
        Configuration cfg = Configuration.create(Region.region0());
        cfg.resumableUploadAPIVersion = Configuration.ResumableUploadAPIVersion.V2;

        UploadManager uploadManager = new UploadManager(cfg);
        Auth auth = Auth.create(accessKey, secretKey);
        String upToken = auth.uploadToken(bucketName);

        try {
            Response response = uploadManager.put(bytes, objectName, upToken);
            DefaultPutRet putRet = new Gson().fromJson(response.bodyString(), DefaultPutRet.class);

            String fileUrl = buildFileUrl(putRet.key, auth);
            log.info("七牛上传成功,key:{}, mode:{}", putRet.key, normalizeMode());
            return fileUrl;
        } catch (QiniuException ex) {
            String errorMessage = ex.response == null ? ex.getMessage() : ex.response.toString();
            log.error("七牛云上传失败:{}", errorMessage, ex);
            throw new BaseException("文件上传失败");
        }
    }

    private String buildFileUrl(String key, Auth auth) {
        String mode = normalizeMode();
        if ("private-signed".equals(mode)) {
            return buildPrivateSignedUrl(key, auth);
        }
        return buildPublicUrl(key);
    }

    private String buildPublicUrl(String key) {
        return normalizeHostName() + "/" + key;
    }

    private String buildPrivateSignedUrl(String key, Auth auth) {
        long expireSeconds = privateUrlExpireSeconds == null || privateUrlExpireSeconds <= 0 ? 3600L : privateUrlExpireSeconds;
        String publicUrl = buildPublicUrl(key);
        return auth.privateDownloadUrl(publicUrl, expireSeconds);
    }

    private String normalizeMode() {
        return urlMode == null ? "public" : urlMode.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeHostName() {
        String normalizedHost = hostName == null ? "" : hostName.trim();
        if (normalizedHost.endsWith("/")) {
            normalizedHost = normalizedHost.substring(0, normalizedHost.length() - 1);
        }
        if (normalizedHost.startsWith("http://") || normalizedHost.startsWith("https://")) {
            return normalizedHost;
        }
        return "https://" + normalizedHost;
    }
}
