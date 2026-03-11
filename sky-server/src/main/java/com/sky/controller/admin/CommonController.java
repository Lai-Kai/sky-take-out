package com.sky.controller.admin;

import com.sky.exception.BaseException;
import com.sky.result.Result;
import com.sky.utils.QiNiuUtil;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/admin/common")
@Slf4j
public class CommonController {


    @Autowired
    private QiNiuUtil qiNiuUtil;

    @PostMapping("/upload")
    @ApiOperation("文件上传")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        log.info("文件上传：{}", file);

        if (file == null || file.isEmpty()) {
            throw new BaseException("上传文件不能为空");
        }

        try {
            String originalFilename = file.getOriginalFilename();
            int dotIndex = originalFilename == null ? -1 : originalFilename.lastIndexOf(".");
            String suffix = dotIndex >= 0 ? originalFilename.substring(dotIndex) : "";
            String filename = UUID.randomUUID() + suffix;
            String fileUrl = qiNiuUtil.upload(file.getBytes(), filename);
            return Result.success(fileUrl);
        } catch (IOException e) {
            log.error("读取上传文件失败", e);
            throw new BaseException("文件上传失败");
        }
    }
}
