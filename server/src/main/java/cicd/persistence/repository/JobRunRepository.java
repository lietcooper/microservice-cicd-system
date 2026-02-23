package cicd.persistence.repository;

import cicd.persistence.entity.JobRunEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for job run records. */
public interface JobRunRepository
    extends JpaRepository<JobRunEntity, Long> {

  /** Returns all jobs for a stage run. */
  List<JobRunEntity> findByStageRunId(Long stageRunId);

  /** Returns a specific job within a stage run. */
  Optional<JobRunEntity> findByStageRunIdAndJobName(
      Long stageRunId, String jobName);
}
