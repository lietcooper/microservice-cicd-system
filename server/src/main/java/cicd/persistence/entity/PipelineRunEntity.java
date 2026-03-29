package cicd.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Stores execution data for a single pipeline run. */
@Entity
@Getter
@Setter
@Table(name = "pipeline_runs",
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"pipeline_name", "run_no"}))
public class PipelineRunEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "pipeline_name", nullable = false)
  private String pipelineName;

  @Column(name = "run_no", nullable = false)
  private int runNo;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private RunStatus status;

  @Column(name = "start_time")
  private OffsetDateTime startTime;

  @Column(name = "end_time")
  private OffsetDateTime endTime;

  @Column(name = "git_hash")
  private String gitHash;

  @Column(name = "git_branch")
  private String gitBranch;

  @Column(name = "git_repo", length = 512)
  private String gitRepo;

  @Column(name = "trace_id", length = 32)
  private String traceId;

  @OneToMany(mappedBy = "pipelineRun",
      cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("stageOrder ASC")
  private List<StageRunEntity> stageRuns = new ArrayList<>();

  /** Adds a stage run and sets up the bidirectional relationship. */
  public void addStage(StageRunEntity stage) {
    stageRuns.add(stage);
    stage.setPipelineRun(this);
  }
}
