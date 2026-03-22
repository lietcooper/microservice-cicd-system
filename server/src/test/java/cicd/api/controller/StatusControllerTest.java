package cicd.api.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cicd.api.dto.PipelineStatusResponse;
import cicd.api.dto.StageStatusDto;
import cicd.exception.ResourceNotFoundException;
import cicd.service.StatusService;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StatusControllerTest {

  private MockMvc mockMvc;
  private StatusService statusService;

  @BeforeEach
  void setUp() {
    statusService = mock(StatusService.class);
    StatusController controller = new StatusController();
    setField(controller, "statusService", statusService);
    mockMvc = MockMvcBuilders
        .standaloneSetup(controller).build();
  }

  @Test
  void getStatusByRepoReturnsOk() throws Exception {
    PipelineStatusResponse resp = new PipelineStatusResponse();
    resp.setPipelineName("my-pipe");
    resp.setRunNo(1);
    resp.setStatus("Running");
    resp.setStages(Collections.emptyList());

    when(statusService.getStatusByRepo(
        "https://github.com/org/repo"))
        .thenReturn(List.of(resp));

    mockMvc.perform(get("/pipelines/status")
        .param("repo", "https://github.com/org/repo"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].pipeline-name")
            .value("my-pipe"))
        .andExpect(jsonPath("$[0].status")
            .value("Running"));
  }

  @Test
  void getStatusByRepoReturnsEmptyList() throws Exception {
    when(statusService.getStatusByRepo("https://empty"))
        .thenReturn(Collections.emptyList());

    mockMvc.perform(get("/pipelines/status")
        .param("repo", "https://empty"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void getRunStatusReturnsOk() throws Exception {
    PipelineStatusResponse resp = new PipelineStatusResponse();
    resp.setPipelineName("build-pipe");
    resp.setRunNo(3);
    resp.setStatus("Success");

    StageStatusDto stage = new StageStatusDto();
    stage.setName("build");
    stage.setStatus("Success");
    resp.setStages(List.of(stage));

    when(statusService.getRunStatus("build-pipe", 3))
        .thenReturn(resp);

    mockMvc.perform(get("/pipelines/build-pipe/status")
        .param("run", "3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pipeline-name")
            .value("build-pipe"))
        .andExpect(jsonPath("$.run-no").value(3))
        .andExpect(jsonPath("$.status").value("Success"))
        .andExpect(jsonPath("$.stages[0].name")
            .value("build"));
  }

  @Test
  void getRunStatusReturns404WhenNotFound() throws Exception {
    when(statusService.getRunStatus("ghost", 1))
        .thenThrow(new ResourceNotFoundException(
            "Pipeline run not found"));

    mockMvc.perform(get("/pipelines/ghost/status")
        .param("run", "1"))
        .andExpect(status().isNotFound());
  }

  private void setField(Object target, String fieldName,
      Object value) {
    try {
      Field field =
          target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }
}
