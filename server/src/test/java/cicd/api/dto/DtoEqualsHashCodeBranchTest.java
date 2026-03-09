package cicd.api.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Targeted tests to exercise the remaining uncovered branches in equals() and hashCode()
 * across all api.dto classes.
 *
 * <p>Lombok generates null-check branches for each field in equals() and hashCode().
 * These tests specifically hit:
 * <ul>
 *   <li>Field A is null but field B is not (first-field mismatch in equals chain)</li>
 *   <li>Fields A-N match but field N+1 differs (deep into the equals chain)</li>
 *   <li>All non-null fields in hashCode() (triggers the non-null branch of each field)</li>
 * </ul>
 */
class DtoEqualsHashCodeBranchTest {

  // ── JobDto branch coverage ────────────────────────────────────────────────

  @Test
  void jobDto_hashCodeWithAllNonNullFields() {
    OffsetDateTime ts = OffsetDateTime.parse("2025-01-01T10:00:00+00:00");
    JobDto dto = new JobDto();
    dto.setName("compile");
    dto.setStatus("SUCCESS");
    dto.setStartTime(ts);
    dto.setEndTime(ts.plusSeconds(30));

    int hash = dto.hashCode();
    assertEquals(hash, dto.hashCode());
    assertNotNull(hash);
  }

  @Test
  void jobDto_equalsFirstFieldNullSecondNotNull() {
    JobDto dto1 = new JobDto();
    dto1.setName(null);
    dto1.setStatus("SUCCESS");

    JobDto dto2 = new JobDto();
    dto2.setName("compile");
    dto2.setStatus("SUCCESS");

    assertNotEquals(dto1, dto2);
  }

  @Test
  void jobDto_equalsNameMatchesButStatusDiffers() {
    JobDto dto1 = new JobDto();
    dto1.setName("compile");
    dto1.setStatus("SUCCESS");

    JobDto dto2 = new JobDto();
    dto2.setName("compile");
    dto2.setStatus("FAILED");

    assertNotEquals(dto1, dto2);
  }

  @Test
  void jobDto_equalsNameAndStatusMatchButStartTimeDiffers() {
    final OffsetDateTime ts1 = OffsetDateTime.parse("2025-01-01T10:00:00+00:00");
    final OffsetDateTime ts2 = OffsetDateTime.parse("2025-01-01T11:00:00+00:00");

    JobDto dto1 = new JobDto();
    dto1.setName("compile");
    dto1.setStatus("SUCCESS");
    dto1.setStartTime(ts1);

    JobDto dto2 = new JobDto();
    dto2.setName("compile");
    dto2.setStatus("SUCCESS");
    dto2.setStartTime(ts2);

    assertNotEquals(dto1, dto2);
  }

  @Test
  void jobDto_equalsFirstThreeMatchButEndTimeDiffers() {
    final OffsetDateTime ts = OffsetDateTime.parse("2025-01-01T10:00:00+00:00");

    JobDto dto1 = new JobDto();
    dto1.setName("compile");
    dto1.setStatus("SUCCESS");
    dto1.setStartTime(ts);
    dto1.setEndTime(ts.plusSeconds(30));

    JobDto dto2 = new JobDto();
    dto2.setName("compile");
    dto2.setStatus("SUCCESS");
    dto2.setStartTime(ts);
    dto2.setEndTime(ts.plusSeconds(60));

    assertNotEquals(dto1, dto2);
  }

  // ── StageDto branch coverage ──────────────────────────────────────────────

  @Test
  void stageDto_hashCodeWithAllNonNullFields() {
    final OffsetDateTime ts = OffsetDateTime.parse("2025-01-01T10:00:00+00:00");
    JobDto job = new JobDto();
    job.setName("compile");
    job.setStatus("SUCCESS");

    StageDto dto = new StageDto();
    dto.setName("build");
    dto.setStatus("SUCCESS");
    dto.setStartTime(ts);
    dto.setEndTime(ts.plusMinutes(2));
    dto.setJobs(List.of(job));

    int hash = dto.hashCode();
    assertEquals(hash, dto.hashCode());
  }

  @Test
  void stageDto_equalsFirstFieldNullSecondNotNull() {
    StageDto dto1 = new StageDto();
    dto1.setName(null);
    dto1.setStatus("SUCCESS");

    StageDto dto2 = new StageDto();
    dto2.setName("build");
    dto2.setStatus("SUCCESS");

    assertNotEquals(dto1, dto2);
  }

  @Test
  void stageDto_equalsNameMatchesButStatusDiffers() {
    StageDto dto1 = new StageDto();
    dto1.setName("build");
    dto1.setStatus("SUCCESS");

    StageDto dto2 = new StageDto();
    dto2.setName("build");
    dto2.setStatus("FAILED");

    assertNotEquals(dto1, dto2);
  }

  @Test
  void stageDto_equalsNameStatusMatchButStartTimeDiffers() {
    final OffsetDateTime ts1 = OffsetDateTime.parse("2025-01-01T10:00:00+00:00");
    final OffsetDateTime ts2 = OffsetDateTime.parse("2025-01-01T11:00:00+00:00");

    StageDto dto1 = new StageDto();
    dto1.setName("build");
    dto1.setStatus("SUCCESS");
    dto1.setStartTime(ts1);

    StageDto dto2 = new StageDto();
    dto2.setName("build");
    dto2.setStatus("SUCCESS");
    dto2.setStartTime(ts2);

    assertNotEquals(dto1, dto2);
  }

  @Test
  void stageDto_equalsFirstThreeMatchButEndTimeDiffers() {
    OffsetDateTime ts = OffsetDateTime.parse("2025-01-01T10:00:00+00:00");

    StageDto dto1 = new StageDto();
    dto1.setName("build");
    dto1.setStatus("SUCCESS");
    dto1.setStartTime(ts);
    dto1.setEndTime(ts.plusMinutes(2));

    StageDto dto2 = new StageDto();
    dto2.setName("build");
    dto2.setStatus("SUCCESS");
    dto2.setStartTime(ts);
    dto2.setEndTime(ts.plusMinutes(5));

    assertNotEquals(dto1, dto2);
  }

  // ── RunSummaryDto branch coverage ─────────────────────────────────────────

  @Test
  void runSummaryDto_hashCodeWithAllNonNullFields() {
    OffsetDateTime ts = OffsetDateTime.parse("2025-01-01T10:00:00+00:00");
    RunSummaryDto dto = new RunSummaryDto();
    dto.setRunNo(1);
    dto.setStatus("SUCCESS");
    dto.setGitRepo("https://github.com/org/repo");
    dto.setGitBranch("main");
    dto.setGitHash("abc123");
    dto.setStartTime(ts);
    dto.setEndTime(ts.plusMinutes(5));

    int hash = dto.hashCode();
    assertEquals(hash, dto.hashCode());
  }

  @Test
  void runSummaryDto_equalsFirstFieldNullSecondNotNull() {
    RunSummaryDto dto1 = new RunSummaryDto();
    dto1.setStatus(null);
    dto1.setRunNo(1);

    RunSummaryDto dto2 = new RunSummaryDto();
    dto2.setStatus("SUCCESS");
    dto2.setRunNo(1);

    assertNotEquals(dto1, dto2);
  }

  @Test
  void runSummaryDto_equalsRunNoAndStatusMatchButGitRepoDiffers() {
    RunSummaryDto dto1 = new RunSummaryDto();
    dto1.setRunNo(1);
    dto1.setStatus("SUCCESS");
    dto1.setGitRepo("https://github.com/org/repo-a");

    RunSummaryDto dto2 = new RunSummaryDto();
    dto2.setRunNo(1);
    dto2.setStatus("SUCCESS");
    dto2.setGitRepo("https://github.com/org/repo-b");

    assertNotEquals(dto1, dto2);
  }

  @Test
  void runSummaryDto_equalsGitBranchDiffersWhenOtherFieldsMatch() {
    RunSummaryDto dto1 = new RunSummaryDto();
    dto1.setRunNo(1);
    dto1.setStatus("SUCCESS");
    dto1.setGitRepo("https://github.com/org/repo");
    dto1.setGitBranch("main");

    RunSummaryDto dto2 = new RunSummaryDto();
    dto2.setRunNo(1);
    dto2.setStatus("SUCCESS");
    dto2.setGitRepo("https://github.com/org/repo");
    dto2.setGitBranch("develop");

    assertNotEquals(dto1, dto2);
  }

  @Test
  void runSummaryDto_equalsGitHashDiffersWhenOtherFieldsMatch() {
    RunSummaryDto dto1 = new RunSummaryDto();
    dto1.setRunNo(1);
    dto1.setStatus("SUCCESS");
    dto1.setGitRepo("https://github.com/org/repo");
    dto1.setGitBranch("main");
    dto1.setGitHash("abc123");

    RunSummaryDto dto2 = new RunSummaryDto();
    dto2.setRunNo(1);
    dto2.setStatus("SUCCESS");
    dto2.setGitRepo("https://github.com/org/repo");
    dto2.setGitBranch("main");
    dto2.setGitHash("def456");

    assertNotEquals(dto1, dto2);
  }

  @Test
  void runSummaryDto_equalsStartTimeDiffersWhenOtherFieldsMatch() {
    final OffsetDateTime ts1 = OffsetDateTime.parse("2025-01-01T10:00:00+00:00");
    final OffsetDateTime ts2 = OffsetDateTime.parse("2025-01-01T11:00:00+00:00");

    RunSummaryDto dto1 = new RunSummaryDto();
    dto1.setRunNo(1);
    dto1.setStatus("SUCCESS");
    dto1.setGitRepo("repo");
    dto1.setGitBranch("main");
    dto1.setGitHash("abc");
    dto1.setStartTime(ts1);

    RunSummaryDto dto2 = new RunSummaryDto();
    dto2.setRunNo(1);
    dto2.setStatus("SUCCESS");
    dto2.setGitRepo("repo");
    dto2.setGitBranch("main");
    dto2.setGitHash("abc");
    dto2.setStartTime(ts2);

    assertNotEquals(dto1, dto2);
  }

  // ── PipelineRunsResponse branch coverage ─────────────────────────────────

  @Test
  void pipelineRunsResponse_hashCodeWithNonNullRuns() {
    RunSummaryDto run = new RunSummaryDto();
    run.setRunNo(1);
    run.setStatus("SUCCESS");

    PipelineRunsResponse resp = new PipelineRunsResponse();
    resp.setPipelineName("my-pipe");
    resp.setRuns(List.of(run));

    int hash = resp.hashCode();
    assertEquals(hash, resp.hashCode());
  }

  @Test
  void pipelineRunsResponse_equalsFirstFieldNullSecondNotNull() {
    PipelineRunsResponse r1 = new PipelineRunsResponse();
    r1.setPipelineName(null);

    PipelineRunsResponse r2 = new PipelineRunsResponse();
    r2.setPipelineName("pipe");

    assertNotEquals(r1, r2);
  }

  // ── PipelineRunDetailResponse branch coverage ─────────────────────────────

  @Test
  void pipelineRunDetailResponse_hashCodeWithAllNonNullFields() {
    final OffsetDateTime ts = OffsetDateTime.parse("2025-01-01T10:00:00+00:00");
    StageDto stage = new StageDto();
    stage.setName("build");
    stage.setStatus("SUCCESS");

    PipelineRunDetailResponse resp = new PipelineRunDetailResponse();
    resp.setPipelineName("pipe");
    resp.setRunNo(1);
    resp.setStatus("SUCCESS");
    resp.setGitRepo("https://github.com/org/repo");
    resp.setGitBranch("main");
    resp.setGitHash("abc123");
    resp.setStartTime(ts);
    resp.setEndTime(ts.plusMinutes(5));
    resp.setStages(List.of(stage));

    int hash = resp.hashCode();
    assertEquals(hash, resp.hashCode());
  }

  @Test
  void pipelineRunDetailResponse_equalsFirstFieldNullSecondNotNull() {
    PipelineRunDetailResponse r1 = new PipelineRunDetailResponse();
    r1.setPipelineName(null);
    r1.setRunNo(1);

    PipelineRunDetailResponse r2 = new PipelineRunDetailResponse();
    r2.setPipelineName("pipe");
    r2.setRunNo(1);

    assertNotEquals(r1, r2);
  }

  @Test
  void pipelineRunDetailResponse_equalsStatusDiffersWhenPipelineNameMatches() {
    PipelineRunDetailResponse r1 = new PipelineRunDetailResponse();
    r1.setPipelineName("pipe");
    r1.setRunNo(1);
    r1.setStatus("SUCCESS");

    PipelineRunDetailResponse r2 = new PipelineRunDetailResponse();
    r2.setPipelineName("pipe");
    r2.setRunNo(1);
    r2.setStatus("FAILED");

    assertNotEquals(r1, r2);
  }

  @Test
  void pipelineRunDetailResponse_equalsGitRepoDiffersWhenOtherFieldsMatch() {
    PipelineRunDetailResponse r1 = new PipelineRunDetailResponse();
    r1.setPipelineName("pipe");
    r1.setRunNo(1);
    r1.setStatus("SUCCESS");
    r1.setGitRepo("repo-a");

    PipelineRunDetailResponse r2 = new PipelineRunDetailResponse();
    r2.setPipelineName("pipe");
    r2.setRunNo(1);
    r2.setStatus("SUCCESS");
    r2.setGitRepo("repo-b");

    assertNotEquals(r1, r2);
  }

  @Test
  void pipelineRunDetailResponse_equalsGitBranchDiffersWhenOtherFieldsMatch() {
    PipelineRunDetailResponse r1 = new PipelineRunDetailResponse();
    r1.setPipelineName("pipe");
    r1.setRunNo(1);
    r1.setStatus("SUCCESS");
    r1.setGitRepo("repo");
    r1.setGitBranch("main");

    PipelineRunDetailResponse r2 = new PipelineRunDetailResponse();
    r2.setPipelineName("pipe");
    r2.setRunNo(1);
    r2.setStatus("SUCCESS");
    r2.setGitRepo("repo");
    r2.setGitBranch("develop");

    assertNotEquals(r1, r2);
  }

  @Test
  void pipelineRunDetailResponse_equalsStartTimeDiffersWhenOtherFieldsMatch() {
    final OffsetDateTime ts1 = OffsetDateTime.parse("2025-01-01T10:00:00+00:00");
    final OffsetDateTime ts2 = OffsetDateTime.parse("2025-01-01T11:00:00+00:00");

    PipelineRunDetailResponse r1 = new PipelineRunDetailResponse();
    r1.setPipelineName("pipe");
    r1.setRunNo(1);
    r1.setStatus("SUCCESS");
    r1.setGitRepo("repo");
    r1.setGitBranch("main");
    r1.setGitHash("abc");
    r1.setStartTime(ts1);

    PipelineRunDetailResponse r2 = new PipelineRunDetailResponse();
    r2.setPipelineName("pipe");
    r2.setRunNo(1);
    r2.setStatus("SUCCESS");
    r2.setGitRepo("repo");
    r2.setGitBranch("main");
    r2.setGitHash("abc");
    r2.setStartTime(ts2);

    assertNotEquals(r1, r2);
  }

  @Test
  void pipelineRunDetailResponse_equalsStagesDifferWhenOtherFieldsMatch() {
    final OffsetDateTime ts = OffsetDateTime.parse("2025-01-01T10:00:00+00:00");
    StageDto stage1 = new StageDto();
    stage1.setName("build");

    StageDto stage2 = new StageDto();
    stage2.setName("test");

    PipelineRunDetailResponse r1 = new PipelineRunDetailResponse();
    r1.setPipelineName("pipe");
    r1.setRunNo(1);
    r1.setStatus("SUCCESS");
    r1.setGitRepo("repo");
    r1.setGitBranch("main");
    r1.setGitHash("abc");
    r1.setStartTime(ts);
    r1.setEndTime(ts.plusMinutes(5));
    r1.setStages(List.of(stage1));

    PipelineRunDetailResponse r2 = new PipelineRunDetailResponse();
    r2.setPipelineName("pipe");
    r2.setRunNo(1);
    r2.setStatus("SUCCESS");
    r2.setGitRepo("repo");
    r2.setGitBranch("main");
    r2.setGitHash("abc");
    r2.setStartTime(ts);
    r2.setEndTime(ts.plusMinutes(5));
    r2.setStages(List.of(stage2));

    assertNotEquals(r1, r2);
  }

  // ── ExecutePipelineRequest branch coverage ────────────────────────────────

  @Test
  void executePipelineRequest_hashCodeWithAllNonNullFields() {
    ExecutePipelineRequest req = new ExecutePipelineRequest();
    req.setPipelineName("release");
    req.setPipelineYaml("pipeline:\n  name: release\n");
    req.setBranch("main");
    req.setCommit("deadbeef");
    req.setRepoUrl("https://github.com/user/repo");

    int hash = req.hashCode();
    assertEquals(hash, req.hashCode());
  }

  @Test
  void executePipelineRequest_equalsFirstFieldNullSecondNotNull() {
    ExecutePipelineRequest req1 = new ExecutePipelineRequest();
    req1.setPipelineName(null);
    req1.setBranch("main");

    ExecutePipelineRequest req2 = new ExecutePipelineRequest();
    req2.setPipelineName("pipe");
    req2.setBranch("main");

    assertNotEquals(req1, req2);
  }

  @Test
  void executePipelineRequest_equalsPipelineNameMatchesButYamlDiffers() {
    ExecutePipelineRequest req1 = new ExecutePipelineRequest();
    req1.setPipelineName("pipe");
    req1.setPipelineYaml("yaml-a");

    ExecutePipelineRequest req2 = new ExecutePipelineRequest();
    req2.setPipelineName("pipe");
    req2.setPipelineYaml("yaml-b");

    assertNotEquals(req1, req2);
  }

  @Test
  void executePipelineRequest_equalsBranchDiffersWhenOtherFieldsMatch() {
    ExecutePipelineRequest req1 = new ExecutePipelineRequest();
    req1.setPipelineName("pipe");
    req1.setPipelineYaml("yaml");
    req1.setBranch("main");

    ExecutePipelineRequest req2 = new ExecutePipelineRequest();
    req2.setPipelineName("pipe");
    req2.setPipelineYaml("yaml");
    req2.setBranch("develop");

    assertNotEquals(req1, req2);
  }

  @Test
  void executePipelineRequest_equalsCommitDiffersWhenOtherFieldsMatch() {
    ExecutePipelineRequest req1 = new ExecutePipelineRequest();
    req1.setPipelineName("pipe");
    req1.setPipelineYaml("yaml");
    req1.setBranch("main");
    req1.setCommit("abc123");

    ExecutePipelineRequest req2 = new ExecutePipelineRequest();
    req2.setPipelineName("pipe");
    req2.setPipelineYaml("yaml");
    req2.setBranch("main");
    req2.setCommit("def456");

    assertNotEquals(req1, req2);
  }

  // ── PipelineRunsResponse canEqual and remaining branches ─────────────────

  @Test
  void pipelineRunsResponse_subclassCanEqualReturnsFalse() {
    PipelineRunsResponse base = new PipelineRunsResponse();
    base.setPipelineName("pipe");

    // Anonymous subclass that returns false for canEqual
    PipelineRunsResponse sub = new PipelineRunsResponse() {
      @Override
      public boolean canEqual(Object other) {
        return false;
      }
    };
    sub.setPipelineName("pipe");

    // base.equals(sub) should be false because sub.canEqual(base) returns false
    assertNotEquals(base, sub);
  }

  @Test
  void pipelineRunsResponse_equalsRunsNullVsNonNull() {
    PipelineRunsResponse r1 = new PipelineRunsResponse();
    r1.setPipelineName("pipe");
    r1.setRuns(null);

    PipelineRunsResponse r2 = new PipelineRunsResponse();
    r2.setPipelineName("pipe");
    r2.setRuns(List.of(new RunSummaryDto()));

    assertNotEquals(r1, r2);
    // Also test reverse direction
    assertNotEquals(r2, r1);
  }

  // ── JobDto canEqual ───────────────────────────────────────────────────────

  @Test
  void jobDto_subclassCanEqualReturnsFalse() {
    JobDto base = new JobDto();
    base.setName("compile");
    base.setStatus("SUCCESS");

    JobDto sub = new JobDto() {
      @Override
      public boolean canEqual(Object other) {
        return false;
      }
    };
    sub.setName("compile");
    sub.setStatus("SUCCESS");

    assertNotEquals(base, sub);
  }

  @Test
  void jobDto_equalsNonNullNameVsNullOtherSide() {
    JobDto dto1 = new JobDto();
    dto1.setName("compile");

    JobDto dto2 = new JobDto();
    dto2.setName(null);

    assertNotEquals(dto1, dto2);
  }

  // ── StageDto canEqual ─────────────────────────────────────────────────────

  @Test
  void stageDto_subclassCanEqualReturnsFalse() {
    StageDto base = new StageDto();
    base.setName("build");
    base.setStatus("SUCCESS");

    StageDto sub = new StageDto() {
      @Override
      public boolean canEqual(Object other) {
        return false;
      }
    };
    sub.setName("build");
    sub.setStatus("SUCCESS");

    assertNotEquals(base, sub);
  }

  @Test
  void stageDto_equalsNonNullNameVsNullOtherSide() {
    StageDto dto1 = new StageDto();
    dto1.setName("build");

    StageDto dto2 = new StageDto();
    dto2.setName(null);

    assertNotEquals(dto1, dto2);
  }

  // ── RunSummaryDto canEqual ────────────────────────────────────────────────

  @Test
  void runSummaryDto_subclassCanEqualReturnsFalse() {
    RunSummaryDto base = new RunSummaryDto();
    base.setRunNo(1);
    base.setStatus("SUCCESS");

    RunSummaryDto sub = new RunSummaryDto() {
      @Override
      public boolean canEqual(Object other) {
        return false;
      }
    };
    sub.setRunNo(1);
    sub.setStatus("SUCCESS");

    assertNotEquals(base, sub);
  }

  @Test
  void runSummaryDto_equalsNonNullStatusVsNullOtherSide() {
    RunSummaryDto dto1 = new RunSummaryDto();
    dto1.setStatus("SUCCESS");

    RunSummaryDto dto2 = new RunSummaryDto();
    dto2.setStatus(null);

    assertNotEquals(dto1, dto2);
  }

  // ── PipelineRunDetailResponse canEqual ────────────────────────────────────

  @Test
  void pipelineRunDetailResponse_subclassCanEqualReturnsFalse() {
    PipelineRunDetailResponse base = new PipelineRunDetailResponse();
    base.setPipelineName("pipe");
    base.setRunNo(1);

    PipelineRunDetailResponse sub = new PipelineRunDetailResponse() {
      @Override
      public boolean canEqual(Object other) {
        return false;
      }
    };
    sub.setPipelineName("pipe");
    sub.setRunNo(1);

    assertNotEquals(base, sub);
  }

  @Test
  void pipelineRunDetailResponse_equalsNonNullPipelineNameVsNullOtherSide() {
    PipelineRunDetailResponse r1 = new PipelineRunDetailResponse();
    r1.setPipelineName("pipe");

    PipelineRunDetailResponse r2 = new PipelineRunDetailResponse();
    r2.setPipelineName(null);

    assertNotEquals(r1, r2);
  }

  // ── ExecutePipelineRequest canEqual ───────────────────────────────────────

  @Test
  void executePipelineRequest_subclassCanEqualReturnsFalse() {
    ExecutePipelineRequest base = new ExecutePipelineRequest();
    base.setPipelineName("pipe");
    base.setBranch("main");

    ExecutePipelineRequest sub = new ExecutePipelineRequest() {
      @Override
      public boolean canEqual(Object other) {
        return false;
      }
    };
    sub.setPipelineName("pipe");
    sub.setBranch("main");

    assertNotEquals(base, sub);
  }

  @Test
  void executePipelineRequest_equalsNonNullBranchVsNullOtherSide() {
    ExecutePipelineRequest req1 = new ExecutePipelineRequest();
    req1.setPipelineName("pipe");
    req1.setBranch("main");

    ExecutePipelineRequest req2 = new ExecutePipelineRequest();
    req2.setPipelineName("pipe");
    req2.setBranch(null);

    assertNotEquals(req1, req2);
  }

  @Test
  void executePipelineRequest_equalsRepoPathDiffersWhenOtherFieldsMatch() {
    ExecutePipelineRequest req1 = new ExecutePipelineRequest();
    req1.setPipelineName("pipe");
    req1.setPipelineYaml("yaml");
    req1.setBranch("main");
    req1.setCommit("abc");
    req1.setRepoUrl("https://github.com/user/repo-a");

    ExecutePipelineRequest req2 = new ExecutePipelineRequest();
    req2.setPipelineName("pipe");
    req2.setPipelineYaml("yaml");
    req2.setBranch("main");
    req2.setCommit("abc");
    req2.setRepoUrl("https://github.com/user/repo-b");

    assertNotEquals(req1, req2);
  }

  // ── ExecutePipelineResponse branch coverage ───────────────────────────────

  @Test
  void executePipelineResponse_hashCodeWithAllNonNullFields() {
    OffsetDateTime ts = OffsetDateTime.parse("2025-01-01T10:00:00+00:00");

    ExecutePipelineResponse resp = new ExecutePipelineResponse();
    resp.setRunId(1L);
    resp.setPipelineName("pipe");
    resp.setRunNo(1);
    resp.setStartTime(ts);
    resp.setEndTime(ts.plusMinutes(5));
    resp.setGitBranch("main");
    resp.setGitHash("abc123");
    resp.setMessage("Done");

    int hash = resp.hashCode();
    assertEquals(hash, resp.hashCode());
  }

  @Test
  void executePipelineResponse_equalsFirstFieldNullSecondNotNull() {
    ExecutePipelineResponse r1 = new ExecutePipelineResponse();
    r1.setRunId(null);
    r1.setPipelineName("pipe");

    ExecutePipelineResponse r2 = new ExecutePipelineResponse();
    r2.setRunId(1L);
    r2.setPipelineName("pipe");

    assertNotEquals(r1, r2);
  }

  @Test
  void executePipelineResponse_equalsPipelineNameDiffersWhenRunIdMatches() {
    ExecutePipelineResponse r1 = new ExecutePipelineResponse();
    r1.setRunId(1L);
    r1.setPipelineName("pipe-a");

    ExecutePipelineResponse r2 = new ExecutePipelineResponse();
    r2.setRunId(1L);
    r2.setPipelineName("pipe-b");

    assertNotEquals(r1, r2);
  }

  @Test
  void executePipelineResponse_equalsStartTimeDiffersWhenOtherFieldsMatch() {
    final OffsetDateTime ts1 = OffsetDateTime.parse("2025-01-01T10:00:00+00:00");
    final OffsetDateTime ts2 = OffsetDateTime.parse("2025-01-01T11:00:00+00:00");

    ExecutePipelineResponse r1 = new ExecutePipelineResponse();
    r1.setRunId(1L);
    r1.setPipelineName("pipe");
    r1.setRunNo(1);
    r1.setStartTime(ts1);

    ExecutePipelineResponse r2 = new ExecutePipelineResponse();
    r2.setRunId(1L);
    r2.setPipelineName("pipe");
    r2.setRunNo(1);
    r2.setStartTime(ts2);

    assertNotEquals(r1, r2);
  }

  @Test
  void executePipelineResponse_equalsGitBranchDiffersWhenOtherFieldsMatch() {
    OffsetDateTime ts = OffsetDateTime.parse("2025-01-01T10:00:00+00:00");

    ExecutePipelineResponse r1 = new ExecutePipelineResponse();
    r1.setRunId(1L);
    r1.setPipelineName("pipe");
    r1.setRunNo(1);
    r1.setStartTime(ts);
    r1.setEndTime(ts.plusMinutes(5));
    r1.setGitBranch("main");

    ExecutePipelineResponse r2 = new ExecutePipelineResponse();
    r2.setRunId(1L);
    r2.setPipelineName("pipe");
    r2.setRunNo(1);
    r2.setStartTime(ts);
    r2.setEndTime(ts.plusMinutes(5));
    r2.setGitBranch("develop");

    assertNotEquals(r1, r2);
  }

  @Test
  void executePipelineResponse_equalsMessageDiffersWhenOtherFieldsMatch() {
    OffsetDateTime ts = OffsetDateTime.parse("2025-01-01T10:00:00+00:00");

    ExecutePipelineResponse r1 = new ExecutePipelineResponse();
    r1.setRunId(1L);
    r1.setPipelineName("pipe");
    r1.setRunNo(1);
    r1.setStartTime(ts);
    r1.setEndTime(ts.plusMinutes(5));
    r1.setGitBranch("main");
    r1.setGitHash("abc");
    r1.setMessage("success msg");

    ExecutePipelineResponse r2 = new ExecutePipelineResponse();
    r2.setRunId(1L);
    r2.setPipelineName("pipe");
    r2.setRunNo(1);
    r2.setStartTime(ts);
    r2.setEndTime(ts.plusMinutes(5));
    r2.setGitBranch("main");
    r2.setGitHash("abc");
    r2.setMessage("different msg");

    assertNotEquals(r1, r2);
  }

  @Test
  void executePipelineResponse_subclassCanEqualReturnsFalse() {
    ExecutePipelineResponse base = new ExecutePipelineResponse();
    base.setRunId(1L);
    base.setPipelineName("pipe");

    ExecutePipelineResponse sub = new ExecutePipelineResponse() {
      @Override
      public boolean canEqual(Object other) {
        return false;
      }
    };
    sub.setRunId(1L);
    sub.setPipelineName("pipe");

    assertNotEquals(base, sub);
  }

  @Test
  void executePipelineResponse_equalsNonNullRunIdVsNullOtherSide() {
    ExecutePipelineResponse r1 = new ExecutePipelineResponse();
    r1.setRunId(1L);

    ExecutePipelineResponse r2 = new ExecutePipelineResponse();
    r2.setRunId(null);

    assertNotEquals(r1, r2);
  }

  @Test
  void executePipelineResponse_equalsGitHashDiffersWhenOtherFieldsMatch() {
    OffsetDateTime ts = OffsetDateTime.parse("2025-01-01T10:00:00+00:00");

    ExecutePipelineResponse r1 = new ExecutePipelineResponse();
    r1.setRunId(1L);
    r1.setPipelineName("pipe");
    r1.setRunNo(1);
    r1.setStartTime(ts);
    r1.setEndTime(ts.plusMinutes(5));
    r1.setGitBranch("main");
    r1.setGitHash("abc123");

    ExecutePipelineResponse r2 = new ExecutePipelineResponse();
    r2.setRunId(1L);
    r2.setPipelineName("pipe");
    r2.setRunNo(1);
    r2.setStartTime(ts);
    r2.setEndTime(ts.plusMinutes(5));
    r2.setGitBranch("main");
    r2.setGitHash("def456");

    assertNotEquals(r1, r2);
  }
}
