package cicd.persistence.repository;

import cicd.persistence.entity.ArtifactEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository for artifact records. */
@Repository
public interface ArtifactRepository extends JpaRepository<ArtifactEntity, Long> {

  /** Finds all artifacts for a given job run. */
  List<ArtifactEntity> findByJobRunId(Long jobRunId);
}
