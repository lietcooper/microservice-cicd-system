package cicd.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cicd.api.dto.ExecutePipelineRequest;
import cicd.messaging.PipelineMessagePublisher;
import cicd.persistence.entity.PipelineRunEntity;
import cicd.persistence.entity.RunStatus;
import cicd.persistence.repository.PipelineRunRepository;
import cicd.service.GitRepositoryService;
import cicd.service.WorkspaceArchiveService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PipelineExecutionControllerTest {

    private MockMvc mockMvc;
    private PipelineRunRepository pipelineRunRepo;
    private PipelineMessagePublisher messagePublisher;
    private GitRepositoryService gitService;
    private WorkspaceArchiveService workspaceArchiveService;
    private ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        pipelineRunRepo = mock(PipelineRunRepository.class);
        messagePublisher = mock(PipelineMessagePublisher.class);
        gitService = mock(GitRepositoryService.class);
        workspaceArchiveService = mock(WorkspaceArchiveService.class);

        PipelineExecutionController controller = new PipelineExecutionController();
        // Manually inject mocks since we are not using @SpringBootTest for faster unit tests
        java.lang.reflect.Field repoField = getField(controller, "pipelineRunRepo");
        setField(controller, repoField, pipelineRunRepo);
        java.lang.reflect.Field publisherField = getField(controller, "messagePublisher");
        setField(controller, publisherField, messagePublisher);
        java.lang.reflect.Field gitField = getField(controller, "gitService");
        setField(controller, gitField, gitService);
        java.lang.reflect.Field archiveField = getField(controller, "workspaceArchiveService");
        setField(controller, archiveField, workspaceArchiveService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private java.lang.reflect.Field getField(Object target, String name) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    private void setField(Object target, java.lang.reflect.Field field, Object value) {
        try {
            field.set(target, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void executePipelineWithYamlContentSucceeds() throws Exception {
        String yaml = """
                pipeline:
                  name: test-pipeline
                stages:
                  - build
                compile:
                  stage: build
                  image: alpine
                  script: echo hi
                """;

        ExecutePipelineRequest request = new ExecutePipelineRequest();
        request.setRepoUrl("https://github.com/user/repo");
        request.setPipelineYaml(yaml);

        Path snapshotPath = tempDir.resolve("snapshot");
        Files.createDirectories(snapshotPath);
        when(gitService.createSnapshot(anyString(), any(), any())).thenReturn(snapshotPath);
        when(gitService.getActualBranchName(any())).thenReturn("main");
        when(gitService.getActualCommitHash(any())).thenReturn("abc1234");
        when(workspaceArchiveService.createArchive(any())).thenReturn(new byte[] {1, 2});

        PipelineRunEntity entity = new PipelineRunEntity();
        entity.setId(1L);
        entity.setPipelineName("test-pipeline");
        entity.setRunNo(1);
        entity.setStatus(RunStatus.PENDING);
        entity.setStartTime(OffsetDateTime.now());
        entity.setGitBranch("main");
        entity.setGitHash("abc1234");

        when(pipelineRunRepo.nextRunNo(anyString())).thenReturn(1);
        when(pipelineRunRepo.save(any(PipelineRunEntity.class))).thenReturn(entity);

        mockMvc.perform(post("/api/pipelines/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.pipelineName").value("test-pipeline"))
                .andExpect(jsonPath("$.runNo").value(1));

        verify(gitService).createSnapshot(eq("https://github.com/user/repo"), any(), any());
        verify(messagePublisher).publishPipelineExecute(any());
    }

    @Test
    void executePipelineWithNameSucceeds() throws Exception {
        // Prepare a snapshot with a pipeline file
        Path snapshotPath = tempDir.resolve("repo-snapshot");
        Files.createDirectories(snapshotPath.resolve(".pipelines"));
        String yaml = """
                pipeline:
                  name: my-pipeline
                stages:
                  - build
                compile:
                  stage: build
                  image: alpine
                  script: echo ok
                """;
        Files.writeString(snapshotPath.resolve(".pipelines/default.yaml"), yaml);

        ExecutePipelineRequest request = new ExecutePipelineRequest();
        request.setRepoUrl("https://github.com/user/repo");
        request.setPipelineName("my-pipeline");

        when(gitService.createSnapshot(anyString(), any(), any())).thenReturn(snapshotPath);
        when(gitService.getActualBranchName(any())).thenReturn("main");
        when(gitService.getActualCommitHash(any())).thenReturn("deadbeef");
        when(workspaceArchiveService.createArchive(any())).thenReturn(new byte[] {1, 2});

        PipelineRunEntity entity = new PipelineRunEntity();
        entity.setId(1L);
        entity.setPipelineName("my-pipeline");
        entity.setRunNo(1);
        entity.setStatus(RunStatus.PENDING);
        entity.setStartTime(OffsetDateTime.now());
        entity.setGitBranch("main");
        entity.setGitHash("deadbeef");

        when(pipelineRunRepo.nextRunNo(anyString())).thenReturn(1);
        when(pipelineRunRepo.save(any(PipelineRunEntity.class))).thenReturn(entity);

        mockMvc.perform(post("/api/pipelines/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.pipelineName").value("my-pipeline"));

        verify(gitService).createSnapshot(eq("https://github.com/user/repo"), any(), any());
        verify(messagePublisher).publishPipelineExecute(any());
    }

    @Test
    void executePipelineWithoutRepoUrlFails() throws Exception {
        ExecutePipelineRequest request = new ExecutePipelineRequest();
        request.setPipelineName("test");

        mockMvc.perform(post("/api/pipelines/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
