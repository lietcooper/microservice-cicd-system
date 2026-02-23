package cicd.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Stores execution data for a single stage within a pipeline run. */
@Entity
@Getter
@Setter
@Table(name = "stage_runs")
public class StageRunEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "pipeline_run_id", nullable = false)
  private PipelineRunEntity pipelineRun;

  @Column(name = "stage_name", nullable = false)
  private String stageName;

  @Column(name = "stage_order", nullable = false)
  private int stageOrder;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private RunStatus status;

  @Column(name = "start_time")
  private OffsetDateTime startTime;

  @Column(name = "end_time")
  private OffsetDateTime endTime;

  @OneToMany(mappedBy = "stageRun",
      cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("id ASC")
  private List<JobRunEntity> jobRuns = new ArrayList<>();

  /** Adds a job run and sets up the bidirectional relationship. */
  public void addJob(JobRunEntity job) {
    jobRuns.add(job);
    job.setStageRun(this);
  }
}
