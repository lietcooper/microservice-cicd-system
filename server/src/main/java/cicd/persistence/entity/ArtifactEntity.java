package cicd.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Stores a single artifact record for a job run. */
@Entity
@Getter
@Setter
@Table(name = "artifacts")
public class ArtifactEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "job_run_id", nullable = false)
  private JobRunEntity jobRun;

  @Column(nullable = false, length = 500)
  private String pattern;

  @Column(name = "storage_path", nullable = false, length = 1000)
  private String storagePath;
}
