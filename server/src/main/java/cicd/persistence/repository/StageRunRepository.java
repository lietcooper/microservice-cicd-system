package cicd.persistence.repository;

import cicd.persistence.entity.StageRunEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for stage run records. */
public interface StageRunRepository
    extends JpaRepository<StageRunEntity, Long> {

  /** Returns all stages for a pipeline run, ordered by stage position. */
  List<StageRunEntity> findByPipelineRunIdOrderByStageOrderAsc(
      Long pipelineRunId);

  /** Returns a specific stage within a pipeline run. */
  Optional<StageRunEntity> findByPipelineRunIdAndStageName(
      Long pipelineRunId, String stageName);
}
