package cicd.api.dto;

import lombok.Data;

/** Artifact information for a job. */
@Data
public class ArtifactDto {

  private String pattern;
  private String location;
}
