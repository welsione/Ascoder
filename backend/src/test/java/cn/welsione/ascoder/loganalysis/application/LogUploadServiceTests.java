package cn.welsione.ascoder.loganalysis.application;

import cn.welsione.ascoder.common.exception.InvalidStateException;
import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import cn.welsione.ascoder.loganalysis.domain.LogUpload;
import cn.welsione.ascoder.loganalysis.domain.LogUploadStatus;
import cn.welsione.ascoder.loganalysis.persistence.LogFileJpaRepository;
import cn.welsione.ascoder.loganalysis.persistence.LogUploadJpaRepository;
import cn.welsione.ascoder.repository.projectspace.ProjectSpace;
import cn.welsione.ascoder.repository.projectspace.ProjectSpaceJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * LogUploadService 单元测试，覆盖上传聚合的领域异常契约。
 */
@ExtendWith(MockitoExtension.class)
class LogUploadServiceTests {

    @Mock
    private LogUploadJpaRepository repository;
    @Mock
    private LogFileJpaRepository logFileRepository;
    @Mock
    private ProjectSpaceJpaRepository projectSpaceRepository;
    @Mock
    private LogPreprocessService logPreprocessService;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private TransactionStatus transactionStatus;
    @Mock
    private MultipartFile multipartFile;

    @TempDir
    private Path tempDir;

    private LogUploadService service;

    @BeforeEach
    void setUp() {
        service = new LogUploadService(
                repository,
                logFileRepository,
                projectSpaceRepository,
                logPreprocessService,
                objectMapper,
                transactionTemplate
        );
        ReflectionTestUtils.setField(service, "storageRoot", tempDir.toString());
        ReflectionTestUtils.setField(service, "maxFileBytes", 1024L);
        ReflectionTestUtils.setField(service, "ttlHours", 72L);
    }

    @Test
    void getThrowsDomainNotFoundWhenUploadMissing() {
        when(repository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("日志上传不存在：404");
    }

    @Test
    void uploadMarksFailedAndThrowsDomainExceptionWhenSummaryCannotBeSerialized() throws Exception {
        runTransactionsImmediately();

        ProjectSpace projectSpace = new ProjectSpace();
        ReflectionTestUtils.setField(projectSpace, "id", 7L);
        AtomicReference<LogUpload> savedUpload = new AtomicReference<>();

        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getOriginalFilename()).thenReturn("app.log");
        when(multipartFile.getSize()).thenReturn(12L);
        when(multipartFile.getInputStream()).thenReturn(new ByteArrayInputStream("hello\nworld\n".getBytes()));
        when(projectSpaceRepository.findById(7L)).thenReturn(Optional.of(projectSpace));
        when(repository.save(any(LogUpload.class))).thenAnswer(invocation -> {
            LogUpload upload = invocation.getArgument(0);
            if (upload.getId() == null) {
                ReflectionTestUtils.setField(upload, "id", 31L);
            }
            savedUpload.set(upload);
            return upload;
        });
        when(repository.findById(31L)).thenAnswer(invocation -> Optional.ofNullable(savedUpload.get()));

        LogFileSummary summary = new LogFileSummary();
        summary.setDisplayName("app.log");
        summary.setFileSize(12L);
        summary.setLineCount(2L);
        summary.setStartedAt(new Date());
        summary.setEndedAt(new Date());
        when(logPreprocessService.preprocess(eq(null), eq("app.log"), any(Path.class))).thenReturn(summary);
        when(objectMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("boom") {
                });

        assertThatThrownBy(() -> service.upload(7L, multipartFile, "developer"))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("序列化日志摘要失败");

        assertThat(savedUpload.get().getStatus()).isEqualTo(LogUploadStatus.FAILED);
        assertThat(savedUpload.get().getErrorMessage()).contains("预处理失败：序列化日志摘要失败");
    }

    private void runTransactionsImmediately() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(transactionStatus);
        });
        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(transactionStatus);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }
}
