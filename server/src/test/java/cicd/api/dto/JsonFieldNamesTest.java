package cicd.api.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify JSON field names match the spec:
 * run-no, start, end, status, git-hash, git-branch, git-repo
 */
public class JsonFieldNamesTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void testRunSummaryDtoFieldNames() throws Exception {
        RunSummaryDto dto = new RunSummaryDto();
        dto.setRunNo(1);
        dto.setStatus("SUCCESS");
        dto.setGitRepo("/workspace");
        dto.setGitBranch("main");
        dto.setGitHash("abc123def");
        dto.setStartTime(OffsetDateTime.now());
        dto.setEndTime(OffsetDateTime.now());

        String json = objectMapper.writeValueAsString(dto);

        // Verify spec-compliant field names
        assertTrue(json.contains("\"run-no\":"), "Field 'run-no' missing");
        assertTrue(json.contains("\"git-repo\":"), "Field 'git-repo' missing");
        assertTrue(json.contains("\"git-branch\":"), "Field 'git-branch' missing");
        assertTrue(json.contains("\"git-hash\":"), "Field 'git-hash' missing");
        assertTrue(json.contains("\"start\":"), "Field 'start' missing");
        assertTrue(json.contains("\"end\":"), "Field 'end' missing");
        assertTrue(json.contains("\"status\":"), "Field 'status' missing");

        // Verify incorrect names are NOT present
        assertFalse(json.contains("\"runNo\":"), "Should use 'run-no' not 'runNo'");
        assertFalse(json.contains("\"gitRepo\":"), "Should use 'git-repo' not 'gitRepo'");
        assertFalse(json.contains("\"gitBranch\":"), "Should use 'git-branch' not 'gitBranch'");
        assertFalse(json.contains("\"gitHash\":"), "Should use 'git-hash' not 'gitHash'");
        assertFalse(json.contains("\"startTime\":"), "Should use 'start' not 'startTime'");
        assertFalse(json.contains("\"endTime\":"), "Should use 'end' not 'endTime'");
    }

    @Test
    void testPipelineRunDetailResponseFieldNames() throws Exception {
        PipelineRunDetailResponse dto = new PipelineRunDetailResponse();
        dto.setPipelineName("test-pipeline");
        dto.setRunNo(5);
        dto.setStatus("RUNNING");
        dto.setGitRepo("/workspace");
        dto.setGitBranch("feature-branch");
        dto.setGitHash("xyz789");
        dto.setStartTime(OffsetDateTime.now());
        dto.setEndTime(null); // Still running

        String json = objectMapper.writeValueAsString(dto);

        // Verify spec-compliant field names
        assertTrue(json.contains("\"run-no\":"), "Field 'run-no' missing");
        assertTrue(json.contains("\"git-repo\":"), "Field 'git-repo' missing");
        assertTrue(json.contains("\"git-branch\":"), "Field 'git-branch' missing");
        assertTrue(json.contains("\"git-hash\":"), "Field 'git-hash' missing");
        assertTrue(json.contains("\"start\":"), "Field 'start' missing");
        // end can be null, but field name should still be correct if present

        assertFalse(json.contains("\"runNo\":"), "Should use 'run-no' not 'runNo'");
        assertFalse(json.contains("\"gitRepo\":"), "Should use 'git-repo' not 'gitRepo'");
        assertFalse(json.contains("\"gitBranch\":"), "Should use 'git-branch' not 'gitBranch'");
        assertFalse(json.contains("\"gitHash\":"), "Should use 'git-hash' not 'gitHash'");
    }

    @Test
    void testStageDtoFieldNames() throws Exception {
        StageDto dto = new StageDto();
        dto.setName("build");
        dto.setStatus("SUCCESS");
        dto.setStartTime(OffsetDateTime.now());
        dto.setEndTime(OffsetDateTime.now());

        String json = objectMapper.writeValueAsString(dto);

        assertTrue(json.contains("\"start\":"), "Field 'start' missing");
        assertTrue(json.contains("\"end\":"), "Field 'end' missing");
        assertFalse(json.contains("\"startTime\":"), "Should use 'start' not 'startTime'");
        assertFalse(json.contains("\"endTime\":"), "Should use 'end' not 'endTime'");
    }

    @Test
    void testJobDtoFieldNames() throws Exception {
        JobDto dto = new JobDto();
        dto.setName("compile");
        dto.setStatus("FAILED");
        dto.setStartTime(OffsetDateTime.now());
        dto.setEndTime(OffsetDateTime.now());

        String json = objectMapper.writeValueAsString(dto);

        assertTrue(json.contains("\"start\":"), "Field 'start' missing");
        assertTrue(json.contains("\"end\":"), "Field 'end' missing");
        assertFalse(json.contains("\"startTime\":"), "Should use 'start' not 'startTime'");
        assertFalse(json.contains("\"endTime\":"), "Should use 'end' not 'endTime'");
    }
}