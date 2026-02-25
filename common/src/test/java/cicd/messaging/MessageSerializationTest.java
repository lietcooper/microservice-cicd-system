package cicd.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class MessageSerializationTest {

  private ObjectMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
  }

  @Test
  void testPipelineExecuteMessageRoundTrip() throws Exception {
    PipelineExecuteMessage original = new PipelineExecuteMessage(
        42L, "default", 5, "pipeline:\n  name: default",
        "/repo", "main", "abc123");

    String json = mapper.writeValueAsString(original);
    PipelineExecuteMessage deserialized = mapper.readValue(
        json, PipelineExecuteMessage.class);

    assertEquals(42L, deserialized.getPipelineRunId());
    assertEquals("default", deserialized.getPipelineName());
    assertEquals(5, deserialized.getRunNo());
    assertEquals("pipeline:\n  name: default", deserialized.getPipelineYaml());
    assertEquals("/repo", deserialized.getRepoPath());
    assertEquals("main", deserialized.getGitBranch());
    assertEquals("abc123", deserialized.getGitCommit());
  }

  @Test
  void testPipelineExecuteMessageDefaultConstructor() throws Exception {
    PipelineExecuteMessage msg = new PipelineExecuteMessage();
    msg.setPipelineRunId(1L);
    msg.setPipelineName("test");
    msg.setRunNo(1);
    msg.setPipelineYaml("yaml");
    msg.setRepoPath("/path");
    msg.setGitBranch("dev");
    msg.setGitCommit("def456");

    String json = mapper.writeValueAsString(msg);
    assertTrue(json.contains("\"pipelineName\":\"test\""));
    assertTrue(json.contains("\"runNo\":1"));
  }

  @Test
  void testJobExecuteMessageRoundTrip() throws Exception {
    JobExecuteMessage original = new JobExecuteMessage();
    original.setPipelineRunId(1L);
    original.setStageRunId(2L);
    original.setJobRunId(3L);
    original.setCorrelationId("corr-123");
    original.setJobName("compile");
    original.setStageName("build");
    original.setPipelineName("default");
    original.setImage("gradle:jdk21");
    original.setScripts(Arrays.asList("gradle build", "gradle test"));
    original.setRepoPath("/repo");
    original.setTotalJobsInWave(3);

    String json = mapper.writeValueAsString(original);
    JobExecuteMessage deserialized = mapper.readValue(
        json, JobExecuteMessage.class);

    assertEquals(1L, deserialized.getPipelineRunId());
    assertEquals(2L, deserialized.getStageRunId());
    assertEquals(3L, deserialized.getJobRunId());
    assertEquals("corr-123", deserialized.getCorrelationId());
    assertEquals("compile", deserialized.getJobName());
    assertEquals("build", deserialized.getStageName());
    assertEquals("default", deserialized.getPipelineName());
    assertEquals("gradle:jdk21", deserialized.getImage());
    assertEquals(2, deserialized.getScripts().size());
    assertEquals("gradle build", deserialized.getScripts().get(0));
    assertEquals("gradle test", deserialized.getScripts().get(1));
    assertEquals("/repo", deserialized.getRepoPath());
    assertEquals(3, deserialized.getTotalJobsInWave());
  }

  @Test
  void testJobResultMessageRoundTrip() throws Exception {
    JobResultMessage original = new JobResultMessage();
    original.setPipelineRunId(1L);
    original.setStageRunId(2L);
    original.setJobRunId(3L);
    original.setCorrelationId("corr-456");
    original.setJobName("compile");
    original.setStageName("build");
    original.setPipelineName("default");
    original.setSuccess(true);
    original.setExitCode(0);
    original.setOutput("BUILD SUCCESSFUL");

    String json = mapper.writeValueAsString(original);
    JobResultMessage deserialized = mapper.readValue(
        json, JobResultMessage.class);

    assertEquals(1L, deserialized.getPipelineRunId());
    assertEquals(2L, deserialized.getStageRunId());
    assertEquals(3L, deserialized.getJobRunId());
    assertEquals("corr-456", deserialized.getCorrelationId());
    assertEquals("compile", deserialized.getJobName());
    assertEquals("build", deserialized.getStageName());
    assertEquals("default", deserialized.getPipelineName());
    assertTrue(deserialized.isSuccess());
    assertEquals(0, deserialized.getExitCode());
    assertEquals("BUILD SUCCESSFUL", deserialized.getOutput());
  }

  @Test
  void testJobResultMessageFailure() throws Exception {
    JobResultMessage original = new JobResultMessage();
    original.setSuccess(false);
    original.setExitCode(1);
    original.setOutput("COMPILATION FAILED");

    String json = mapper.writeValueAsString(original);
    JobResultMessage deserialized = mapper.readValue(
        json, JobResultMessage.class);

    assertFalse(deserialized.isSuccess());
    assertEquals(1, deserialized.getExitCode());
    assertEquals("COMPILATION FAILED", deserialized.getOutput());
  }

  @Test
  void testStatusEventMessageRoundTrip() throws Exception {
    StatusEventMessage original = new StatusEventMessage();
    original.setEventType("pipeline.started");
    original.setPipelineRunId(1L);
    original.setPipelineName("default");
    original.setRunNo(3);
    original.setStageName(null);
    original.setJobName(null);
    original.setStatus("RUNNING");
    original.setTimestamp(OffsetDateTime.now());
    original.setMessage("Pipeline started");

    String json = mapper.writeValueAsString(original);
    StatusEventMessage deserialized = mapper.readValue(
        json, StatusEventMessage.class);

    assertEquals("pipeline.started", deserialized.getEventType());
    assertEquals(1L, deserialized.getPipelineRunId());
    assertEquals("default", deserialized.getPipelineName());
    assertEquals(3, deserialized.getRunNo());
    assertNull(deserialized.getStageName());
    assertNull(deserialized.getJobName());
    assertEquals("RUNNING", deserialized.getStatus());
    assertNotNull(deserialized.getTimestamp());
    assertEquals("Pipeline started", deserialized.getMessage());
  }

  @Test
  void testStatusEventMessageWithStageAndJob() throws Exception {
    StatusEventMessage original = new StatusEventMessage();
    original.setEventType("job.completed");
    original.setPipelineRunId(1L);
    original.setPipelineName("default");
    original.setRunNo(1);
    original.setStageName("build");
    original.setJobName("compile");
    original.setStatus("SUCCESS");
    original.setTimestamp(OffsetDateTime.now());
    original.setMessage("Job 'compile' succeeded");

    String json = mapper.writeValueAsString(original);
    StatusEventMessage deserialized = mapper.readValue(
        json, StatusEventMessage.class);

    assertEquals("job.completed", deserialized.getEventType());
    assertEquals("build", deserialized.getStageName());
    assertEquals("compile", deserialized.getJobName());
    assertEquals("SUCCESS", deserialized.getStatus());
  }

  @Test
  void testIgnoresUnknownFields() throws Exception {
    // Simulate receiving a message with extra fields
    String json = "{\"pipelineRunId\":1,\"pipelineName\":\"test\","
        + "\"unknownField\":\"value\",\"runNo\":1}";

    PipelineExecuteMessage msg = mapper.readValue(
        json, PipelineExecuteMessage.class);

    assertEquals(1L, msg.getPipelineRunId());
    assertEquals("test", msg.getPipelineName());
    assertEquals(1, msg.getRunNo());
  }

  @Test
  void testNullFieldsSerialization() throws Exception {
    JobResultMessage original = new JobResultMessage();
    original.setSuccess(false);
    original.setExitCode(127);
    // output, correlationId etc. left as null

    String json = mapper.writeValueAsString(original);
    JobResultMessage deserialized = mapper.readValue(
        json, JobResultMessage.class);

    assertFalse(deserialized.isSuccess());
    assertEquals(127, deserialized.getExitCode());
    assertNull(deserialized.getOutput());
    assertNull(deserialized.getCorrelationId());
  }
}
