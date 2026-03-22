package cicd.persistence.repository;

import cicd.persistence.entity.PipelineRunEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for pipeline run records. */
public interface PipelineRunRepository
    extends JpaRepository<PipelineRunEntity, Long> {

  /** Returns all runs for the given pipeline, ordered by run number. */
  List<PipelineRunEntity> findByPipelineNameOrderByRunNoAsc(
      String pipelineName);

  /** Returns a specific run of a pipeline. */
  Optional<PipelineRunEntity> findByPipelineNameAndRunNo(
      String pipelineName, int runNo);

  /** Computes the next run number for a pipeline. */
  @Query("SELECT COALESCE(MAX(pr.runNo), 0) + 1 "
      + "FROM PipelineRunEntity pr "
      + "WHERE pr.pipelineName = :name")
  int nextRunNo(@Param("name") String pipelineName);

  /** Returns runs for a repo that are currently running. */
  List<PipelineRunEntity> findByGitRepoAndStatus(
      String gitRepo, cicd.persistence.entity.RunStatus status);

  /** Returns all runs for a repo ordered by run number desc. */
  List<PipelineRunEntity> findByGitRepoOrderByRunNoDesc(
      String gitRepo);
}
