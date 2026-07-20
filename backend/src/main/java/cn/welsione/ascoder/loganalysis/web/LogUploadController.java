package cn.welsione.ascoder.loganalysis.web;

import cn.welsione.ascoder.loganalysis.application.LogUploadService;
import cn.welsione.ascoder.loganalysis.domain.LogFile;
import cn.welsione.ascoder.loganalysis.domain.LogUpload;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 日志上传 REST 控制器，提供上传与查询接口供前端附件上传与状态轮询使用。
 */
@RestController
@RequestMapping("/api/log-uploads")
@RequiredArgsConstructor
public class LogUploadController {

    private final LogUploadService logUploadService;
    private final ObjectMapper objectMapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LogUploadResponse upload(@RequestParam("projectSpaceId") Long projectSpaceId,
                                    @RequestParam("file") MultipartFile file,
                                    @RequestParam(value = "createdBy", required = false) String createdBy) {
        LogUpload upload = logUploadService.upload(projectSpaceId, file, createdBy);
        List<LogFile> files = logUploadService.listFiles(upload.getId());
        return LogUploadResponse.from(upload, files, objectMapper);
    }

    @GetMapping("/{id}")
    public LogUploadResponse get(@PathVariable Long id) {
        LogUpload upload = logUploadService.get(id);
        List<LogFile> files = logUploadService.listFiles(id);
        return LogUploadResponse.from(upload, files, objectMapper);
    }

    @GetMapping
    public List<LogUploadResponse> recent(@RequestParam("projectSpaceId") Long projectSpaceId) {
        return logUploadService.recentByProjectSpace(projectSpaceId).stream()
                .map(upload -> LogUploadResponse.from(upload,
                        logUploadService.listFiles(upload.getId()), objectMapper))
                .toList();
    }
}
