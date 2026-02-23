package cicd.api.dto;

import lombok.Data;

@Data
public class ExecutePipelineRequest {
    private String pipelineName;  // Pipeline name to find in repo
    private String pipelineYaml;  // Raw YAML content (optional, if not using name)
    private String branch;        // Git branch (optional)
    private String commit;        // Git commit (optional)
    private String repoPath;      // Repository path
}