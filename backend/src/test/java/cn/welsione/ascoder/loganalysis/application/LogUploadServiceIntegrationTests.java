package cn.welsione.ascoder.loganalysis.application;

import cn.welsione.ascoder.AbstractIntegrationTest;
import cn.welsione.ascoder.IntegrationTestDataFactory;
import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import cn.welsione.ascoder.common.exception.ValidationException;
import cn.welsione.ascoder.loganalysis.domain.LogFile;
import cn.welsione.ascoder.loganalysis.domain.LogFileParseStatus;
import cn.welsione.ascoder.loganalysis.domain.LogUpload;
import cn.welsione.ascoder.loganalysis.domain.LogUploadStatus;
import cn.welsione.ascoder.loganalysis.persistence.LogFileJpaRepository;
import cn.welsione.ascoder.loganalysis.persistence.LogUploadJpaRepository;
import cn.welsione.ascoder.repository.project.Project;
import cn.welsione.ascoder.repository.projectspace.ProjectSpace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LogUploadService 集成测试：验证日志文件上传、落盘、预处理状态流转（UPLOADING -> PARSING -> READY）、
 * zip 解压、上传失败标记、列表查询与删除清理。
 *
 * <p>LogUploadService 使用 {@code TransactionTemplate} 编程式事务，不适用 {@code @Transactional} 回滚，
 * 通过 {@code @AfterEach} 手动清理 DB 记录。文件系统使用 {@code @TempDir} 隔离。
 * 项目名称使用 {@code System.nanoTime()} 保证唯一，避免历史残留数据冲突。</p>
 */
class LogUploadServiceIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private LogUploadService service;

    @Autowired
    private LogUploadJpaRepository uploadRepository;

    @Autowired
    private LogFileJpaRepository logFileRepository;

    @Autowired
    private IntegrationTestDataFactory dataFactory;

    @TempDir
    private Path tempDir;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "storageRoot", tempDir.toString());
    }

    @AfterEach
    void tearDown() {
        logFileRepository.deleteAll();
        uploadRepository.deleteAll();
    }

    @Test
    void uploadPlainTextLogTransitionsToReady() {
        Project project = dataFactory.createProject(uniqueName("log-plain"));
        ProjectSpace space = dataFactory.createProjectSpace(project, uniqueName("space-plain"));

        String logContent = "2024-01-01 10:00:00 INFO Application started\n"
                + "2024-01-01 10:00:01 ERROR NullPointerException at com.example.Service.handle(Service.java:42)\n";
        LogUpload upload = service.upload(space.getId(),
                mockFile("app.log", logContent.getBytes()), "tester");

        assertEquals(LogUploadStatus.READY, upload.getStatus());
        assertNotNull(upload.getSummaryJson());
        assertEquals(".log", upload.getFileType());
        assertEquals("app.log", upload.getOriginalFilename());

        List<LogFile> files = service.listFiles(upload.getId());
        assertEquals(1, files.size());
        LogFile logFile = files.get(0);
        assertEquals("app.log", logFile.getDisplayName());
        assertEquals(LogFileParseStatus.PARSED, logFile.getParseStatus());
        assertEquals(2L, logFile.getLineCount());
        assertTrue(logFile.getFileSize() > 0);
    }

    @Test
    void uploadZipFileExtractsAndProcessesEntries() throws Exception {
        Project project = dataFactory.createProject(uniqueName("log-zip"));
        ProjectSpace space = dataFactory.createProjectSpace(project, uniqueName("space-zip"));

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("error.log"));
            zos.write("2024-01-01 10:00:00 ERROR Something went wrong\n".getBytes());
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("debug.log"));
            zos.write("2024-01-01 10:00:01 DEBUG Debug message\n".getBytes());
            zos.closeEntry();
        }

        LogUpload upload = service.upload(space.getId(),
                mockFile("logs.zip", baos.toByteArray()), "tester");

        assertEquals(LogUploadStatus.READY, upload.getStatus());
        assertEquals(".zip", upload.getFileType());

        List<LogFile> files = service.listFiles(upload.getId());
        assertEquals(2, files.size());
        assertTrue(files.stream().allMatch(f -> f.getParseStatus() == LogFileParseStatus.PARSED));
    }

    @Test
    void uploadThrowsWhenFileEmpty() {
        Project project = dataFactory.createProject(uniqueName("log-empty"));
        ProjectSpace space = dataFactory.createProjectSpace(project, uniqueName("space-empty"));

        assertThrows(ValidationException.class, () ->
                service.upload(space.getId(), mockFile("empty.log", new byte[0]), "tester"));
    }

    @Test
    void uploadThrowsWhenUnsupportedExtension() {
        Project project = dataFactory.createProject(uniqueName("log-unsupported"));
        ProjectSpace space = dataFactory.createProjectSpace(project, uniqueName("space-unsupported"));

        assertThrows(ValidationException.class, () ->
                service.upload(space.getId(), mockFile("data.csv", "a,b,c\n".getBytes()), "tester"));
    }

    @Test
    void uploadThrowsWhenProjectSpaceNotFound() {
        assertThrows(ResourceNotFoundException.class, () ->
                service.upload(999999L, mockFile("app.log", "content\n".getBytes()), "tester"));
    }

    @Test
    void getThrowsWhenUploadNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.get(999999L));
    }

    @Test
    void recentByProjectSpaceReturnsUploads() {
        Project project = dataFactory.createProject(uniqueName("log-list"));
        ProjectSpace space = dataFactory.createProjectSpace(project, uniqueName("space-list"));
        Project otherProject = dataFactory.createProject(uniqueName("log-list-other"));
        ProjectSpace otherSpace = dataFactory.createProjectSpace(otherProject, uniqueName("space-list-other"));

        service.upload(space.getId(), mockFile("a.log", "line\n".getBytes()), "tester");
        service.upload(space.getId(), mockFile("b.log", "line\n".getBytes()), "tester");
        service.upload(otherSpace.getId(), mockFile("c.log", "line\n".getBytes()), "tester");

        List<LogUpload> uploads = service.recentByProjectSpace(space.getId());

        assertEquals(2, uploads.size());
        assertTrue(uploads.stream().allMatch(u -> u.getProjectSpace().getId().equals(space.getId())));
    }

    @Test
    void uploadMarksFailedWhenZipHasNoLogFiles() throws Exception {
        Project project = dataFactory.createProject(uniqueName("log-zip-empty"));
        ProjectSpace space = dataFactory.createProjectSpace(project, uniqueName("space-zip-empty"));

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("readme.md"));
            zos.write("not a log".getBytes());
            zos.closeEntry();
        }

        assertThrows(ValidationException.class, () ->
                service.upload(space.getId(), mockFile("no-logs.zip", baos.toByteArray()), "tester"));

        Optional<LogUpload> failedUpload = uploadRepository.findAll().stream()
                .filter(u -> u.getProjectSpace().getId().equals(space.getId()))
                .findFirst();
        assertTrue(failedUpload.isPresent());
        assertEquals(LogUploadStatus.FAILED, failedUpload.get().getStatus());
        assertNotNull(failedUpload.get().getErrorMessage());
    }

    @Test
    void uploadSingleLogCreatesOneLogFile() {
        Project project = dataFactory.createProject(uniqueName("log-single"));
        ProjectSpace space = dataFactory.createProjectSpace(project, uniqueName("space-single"));

        LogUpload upload = service.upload(space.getId(),
                mockFile("simple.log", "just one line\n".getBytes()), "tester");

        List<LogFile> files = service.listFiles(upload.getId());
        assertEquals(1, files.size());
        assertEquals(LogUploadStatus.READY, upload.getStatus());
    }

    @Test
    void uploadPreservesExpiresAt() {
        Project project = dataFactory.createProject(uniqueName("log-expiry"));
        ProjectSpace space = dataFactory.createProjectSpace(project, uniqueName("space-expiry"));

        LogUpload upload = service.upload(space.getId(),
                mockFile("app.log", "content\n".getBytes()), "tester");

        assertNotNull(upload.getExpiresAt());
        assertTrue(upload.getExpiresAt().after(new Date()));
    }

    private org.springframework.web.multipart.MultipartFile mockFile(String filename, byte[] content) {
        return new org.springframework.mock.web.MockMultipartFile(
                "file", filename, "application/octet-stream", content);
    }

    private String uniqueName(String prefix) {
        return prefix + "-" + System.nanoTime();
    }
}
