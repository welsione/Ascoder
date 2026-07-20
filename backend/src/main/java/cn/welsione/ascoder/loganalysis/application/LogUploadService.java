package cn.welsione.ascoder.loganalysis.application;

import cn.welsione.ascoder.common.exception.InvalidStateException;
import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import cn.welsione.ascoder.common.exception.ValidationException;
import cn.welsione.ascoder.loganalysis.domain.LogFile;
import cn.welsione.ascoder.loganalysis.domain.LogFileParseStatus;
import cn.welsione.ascoder.loganalysis.domain.LogUpload;
import cn.welsione.ascoder.loganalysis.domain.LogUploadStatus;
import cn.welsione.ascoder.loganalysis.persistence.LogFileJpaRepository;
import cn.welsione.ascoder.loganalysis.persistence.LogUploadJpaRepository;
import cn.welsione.ascoder.repository.projectspace.ProjectSpace;
import cn.welsione.ascoder.repository.projectspace.ProjectSpaceJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 日志上传服务，负责接收用户提交的日志文件、落盘存储、调用预处理器并维护上传任务状态。
 *
 * <p>支持单个 .log/.txt 上传，或上传 .zip 由服务端解压并对内部 .log/.txt 条目逐一预处理；
 * 解压会做 zip-bomb / 路径穿越防御。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogUploadService {

    private static final Set<String> PLAIN_LOG_EXTENSIONS = Set.of(".log", ".txt");
    private static final Set<String> ARCHIVE_EXTENSIONS = Set.of(".zip");
    private static final Set<String> SUPPORTED_EXTENSIONS;
    private static final int MAX_ZIP_ENTRIES = 50;
    private static final long MAX_ZIP_TOTAL_BYTES = 512L * 1024 * 1024; // 解压后总大小上限：512MB

    static {
        Set<String> all = new java.util.HashSet<>(PLAIN_LOG_EXTENSIONS);
        all.addAll(ARCHIVE_EXTENSIONS);
        SUPPORTED_EXTENSIONS = Set.copyOf(all);
    }

    private final LogUploadJpaRepository repository;
    private final LogFileJpaRepository logFileRepository;
    private final ProjectSpaceJpaRepository projectSpaceRepository;
    private final LogPreprocessService logPreprocessService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @Value("${ascoder.log-analysis.storage-root:./data/log-analysis}")
    private String storageRoot;

    @Value("${ascoder.log-analysis.max-file-bytes:104857600}")
    private long maxFileBytes;

    @Value("${ascoder.log-analysis.ttl-hours:72}")
    private long ttlHours;

    /**
     * 接收单个日志文件上传，落盘后流式预处理并写入摘要。
     */
    public LogUpload upload(Long projectSpaceId, MultipartFile file, String createdBy) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("日志文件不能为空");
        }
        String originalFilename = sanitizeFilename(file.getOriginalFilename());
        validateExtension(originalFilename);
        if (file.getSize() > maxFileBytes) {
            throw new ValidationException("日志文件超过单文件大小上限：" + maxFileBytes + " 字节");
        }
        ProjectSpace projectSpace = projectSpaceRepository.findById(projectSpaceId)
                .orElseThrow(() -> new ResourceNotFoundException("项目空间不存在：" + projectSpaceId));

        LogUpload upload = transactionTemplate.execute(status -> {
            LogUpload entity = new LogUpload();
            entity.setProjectSpace(projectSpace);
            entity.setCreatedBy(createdBy);
            entity.setOriginalFilename(originalFilename);
            entity.setFileType(extension(originalFilename));
            entity.setFileSize(file.getSize());
            entity.setStatus(LogUploadStatus.UPLOADING);
            entity.setCreatedAt(new Date());
            entity.setExpiresAt(new Date(System.currentTimeMillis() + ttlHours * 3600_000L));
            entity.setStoredPath("");
            return repository.save(entity);
        });

        Path storedFile;
        try {
            storedFile = persistToDisk(projectSpaceId, upload.getId(), originalFilename, file);
        } catch (IOException ex) {
            log.error("保存日志文件失败，uploadId={}", upload.getId(), ex);
            markFailed(upload.getId(), "保存文件失败：" + ex.getMessage());
            throw new ValidationException("保存日志文件失败：" + ex.getMessage());
        }

        transactionTemplate.executeWithoutResult(status -> {
            LogUpload locked = findUploadOrThrow(upload.getId());
            locked.setStoredPath(storedFile.toString());
            locked.parsing();
            repository.save(locked);
        });

        try {
            String ext = extension(originalFilename);
            if (ARCHIVE_EXTENSIONS.contains(ext)) {
                List<Path> extracted = extractZip(storedFile, projectSpaceId, upload.getId());
                if (extracted.isEmpty()) {
                    throw new ValidationException("压缩包中未找到 .log/.txt 文件");
                }
                List<LogFileSummary> summaries = new ArrayList<>();
                for (Path entry : extracted) {
                    String entryName = entry.getFileName().toString();
                    summaries.add(logPreprocessService.preprocess(null, entryName, entry));
                }
                persistFilesAndComplete(upload.getId(), extracted, summaries);
            } else {
                LogFileSummary fileSummary = logPreprocessService.preprocess(null, originalFilename, storedFile);
                persistFileAndComplete(upload.getId(), storedFile, fileSummary);
            }
        } catch (ValidationException ex) {
            log.error("处理日志失败，uploadId={}", upload.getId(), ex);
            markFailed(upload.getId(), ex.getMessage());
            throw ex;
        } catch (IOException ex) {
            log.error("解压日志包失败，uploadId={}", upload.getId(), ex);
            markFailed(upload.getId(), "解压失败：" + ex.getMessage());
            throw new ValidationException("解压失败：" + ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("预处理日志失败，uploadId={}", upload.getId(), ex);
            markFailed(upload.getId(), "预处理失败：" + ex.getMessage());
            throw ex;
        }

        return findUploadOrThrow(upload.getId());
    }

    @Transactional(readOnly = true)
    public LogUpload get(Long uploadId) {
        return findUploadOrThrow(uploadId);
    }

    @Transactional(readOnly = true)
    public List<LogFile> listFiles(Long uploadId) {
        return logFileRepository.findByUpload_IdOrderByCreatedAtAsc(uploadId);
    }

    @Transactional(readOnly = true)
    public List<LogUpload> recentByProjectSpace(Long projectSpaceId) {
        return repository.findTop20ByProjectSpace_IdOrderByCreatedAtDesc(projectSpaceId);
    }

    private void persistFilesAndComplete(Long uploadId, List<Path> storedFiles, List<LogFileSummary> fileSummaries) {
        transactionTemplate.executeWithoutResult(status -> {
            LogUpload upload = findUploadOrThrow(uploadId);
            long totalSize = 0L;
            boolean limitedMode = false;
            List<LogFileSummary> persisted = new ArrayList<>();
            for (int i = 0; i < storedFiles.size(); i++) {
                Path storedFile = storedFiles.get(i);
                LogFileSummary fileSummary = fileSummaries.get(i);
                LogFile logFile = new LogFile();
                logFile.setUpload(upload);
                logFile.setDisplayName(fileSummary.getDisplayName());
                logFile.setStoredPath(storedFile.toString());
                logFile.setFileSize(fileSummary.getFileSize());
                logFile.setLineCount(fileSummary.getLineCount());
                logFile.setStartedAt(fileSummary.getStartedAt());
                logFile.setEndedAt(fileSummary.getEndedAt());
                logFile.setLimitedMode(fileSummary.isLimitedMode());
                logFile.setParseStatus(LogFileParseStatus.PARSED);
                logFile.setSummaryJson(writeJson(fileSummary));
                logFile.setCreatedAt(new Date());
                logFileRepository.save(logFile);
                fileSummary.setFileId(logFile.getId());
                totalSize += fileSummary.getFileSize();
                limitedMode = limitedMode || fileSummary.isLimitedMode();
                persisted.add(fileSummary);
            }

            LogUploadSummary uploadSummary = new LogUploadSummary();
            uploadSummary.setUploadId(uploadId);
            uploadSummary.setTotalFileSize(totalSize);
            uploadSummary.setLimitedMode(limitedMode);
            uploadSummary.setFiles(persisted);
            upload.ready(writeJson(uploadSummary));
            repository.save(upload);
        });
    }

    /**
     * 解压 zip 包到 baseDir/extracted 下，返回解压出的 .log/.txt 文件路径。
     * 防御 zip-bomb（条目数 / 总解压字节限制）与路径穿越（拒绝 .. 与绝对路径，目录限定在 extracted 之内）。
     */
    private List<Path> extractZip(Path zipFile, Long projectSpaceId, Long uploadId) throws IOException {
        Path extractDir = Paths.get(storageRoot,
                "projectSpace-" + projectSpaceId,
                "upload-" + uploadId,
                "extracted");
        Files.createDirectories(extractDir);
        Path normalizedRoot = extractDir.toAbsolutePath().normalize();

        List<Path> extracted = new ArrayList<>();
        long totalBytes = 0L;
        int entryCount = 0;
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                String rawName = entry.getName();
                if (rawName == null || rawName.isBlank()) {
                    zis.closeEntry();
                    continue;
                }
                if (rawName.contains("..") || rawName.startsWith("/") || rawName.contains(":\\") || rawName.startsWith("\\")) {
                    log.warn("拒绝可疑 zip 条目名：{}", rawName);
                    zis.closeEntry();
                    continue;
                }
                String leafName = sanitizeFilename(rawName);
                String ext = extension(leafName);
                if (!PLAIN_LOG_EXTENSIONS.contains(ext)) {
                    zis.closeEntry();
                    continue;
                }
                if (++entryCount > MAX_ZIP_ENTRIES) {
                    throw new ValidationException("压缩包条目数超过上限：" + MAX_ZIP_ENTRIES);
                }
                Path target = extractDir.resolve(leafName).toAbsolutePath().normalize();
                if (!target.startsWith(normalizedRoot)) {
                    log.warn("拒绝越界 zip 解压目标：{}", target);
                    zis.closeEntry();
                    continue;
                }

                long written = 0L;
                byte[] buffer = new byte[8192];
                try (var out = Files.newOutputStream(target)) {
                    int n;
                    while ((n = zis.read(buffer)) > 0) {
                        written += n;
                        totalBytes += n;
                        if (totalBytes > MAX_ZIP_TOTAL_BYTES) {
                            throw new ValidationException("压缩包解压后总大小超过上限：" + MAX_ZIP_TOTAL_BYTES + " 字节");
                        }
                        out.write(buffer, 0, n);
                    }
                }
                log.info("解压 zip 条目：name={}，bytes={}，uploadId={}", leafName, written, uploadId);
                extracted.add(target);
                zis.closeEntry();
            }
        }
        return extracted;
    }

    private void persistFileAndComplete(Long uploadId, Path storedFile, LogFileSummary fileSummary) {
        transactionTemplate.executeWithoutResult(status -> {
            LogUpload upload = findUploadOrThrow(uploadId);
            LogFile logFile = new LogFile();
            logFile.setUpload(upload);
            logFile.setDisplayName(fileSummary.getDisplayName());
            logFile.setStoredPath(storedFile.toString());
            logFile.setFileSize(fileSummary.getFileSize());
            logFile.setLineCount(fileSummary.getLineCount());
            logFile.setStartedAt(fileSummary.getStartedAt());
            logFile.setEndedAt(fileSummary.getEndedAt());
            logFile.setLimitedMode(fileSummary.isLimitedMode());
            logFile.setParseStatus(LogFileParseStatus.PARSED);
            logFile.setSummaryJson(writeJson(fileSummary));
            logFile.setCreatedAt(new Date());
            logFileRepository.save(logFile);
            fileSummary.setFileId(logFile.getId());

            LogUploadSummary uploadSummary = new LogUploadSummary();
            uploadSummary.setUploadId(uploadId);
            uploadSummary.setTotalFileSize(fileSummary.getFileSize());
            uploadSummary.setLimitedMode(fileSummary.isLimitedMode());
            List<LogFileSummary> files = new ArrayList<>();
            files.add(fileSummary);
            uploadSummary.setFiles(files);
            upload.ready(writeJson(uploadSummary));
            repository.save(upload);
        });
    }

    private void markFailed(Long uploadId, String message) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                repository.findById(uploadId).ifPresent(upload -> {
                    upload.fail(message);
                    repository.save(upload);
                });
            });
        } catch (RuntimeException ex) {
            log.error("更新失败状态失败，uploadId={}", uploadId, ex);
        }
    }

    private Path persistToDisk(Long projectSpaceId, Long uploadId, String originalFilename, MultipartFile file)
            throws IOException {
        Path baseDir = Paths.get(storageRoot,
                "projectSpace-" + projectSpaceId,
                "upload-" + uploadId,
                "original");
        Files.createDirectories(baseDir);
        Path target = baseDir.resolve(originalFilename);
        try (var input = file.getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    private void validateExtension(String filename) {
        String ext = extension(filename);
        if (!SUPPORTED_EXTENSIONS.contains(ext)) {
            throw new ValidationException("仅支持 .log/.txt/.zip 文件，当前类型：" + ext);
        }
    }

    private String extension(String filename) {
        int idx = filename.lastIndexOf('.');
        if (idx < 0 || idx == filename.length() - 1) {
            return "";
        }
        return filename.substring(idx).toLowerCase();
    }

    private String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("文件名不能为空");
        }
        String trimmed = name.replace('\\', '/');
        int slashIdx = trimmed.lastIndexOf('/');
        if (slashIdx >= 0) {
            trimmed = trimmed.substring(slashIdx + 1);
        }
        if (trimmed.contains("..")) {
            throw new ValidationException("非法文件名：" + name);
        }
        if (trimmed.length() > 200) {
            trimmed = trimmed.substring(trimmed.length() - 200);
        }
        return trimmed;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new InvalidStateException("序列化日志摘要失败", ex);
        }
    }

    private LogUpload findUploadOrThrow(Long uploadId) {
        return repository.findById(uploadId)
                .orElseThrow(() -> new ResourceNotFoundException("日志上传不存在：" + uploadId));
    }
}
