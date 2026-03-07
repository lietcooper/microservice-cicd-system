package cicd.service;

import cicd.docker.DockerRunner;
import cicd.executor.ExecutionPlanner;
import cicd.executor.JobResult;
import cicd.model.Job;
import cicd.model.Pipeline;
import cicd.persistence.entity.JobRunEntity;
import cicd.persistence.entity.PipelineRunEntity;
import cicd.persistence.entity.RunStatus;
import cicd.persistence.entity.StageRunEntity;
import cicd.persistence.repository.JobRunRepository;
import cicd.persistence.repository.PipelineRunRepository;
import cicd.persistence.repository.StageRunRepository;
import cicd.util.GitHelper;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * Legacy synchronous pipeline executor. Replaced by async worker
 * via RabbitMQ. Kept for reference; not a Spring bean.
 */
public class PipelineExecutorService {

    private PipelineRunRepository pipelineRunRepo;
    private StageRunRepository stageRunRepo;
    private JobRunRepository jobRunRepo;
    private final DockerRunner dockerRunner;

    public PipelineExecutorService(DockerRunner dockerRunner,
        PipelineRunRepository pipelineRunRepo,
        StageRunRepository stageRunRepo,
        JobRunRepository jobRunRepo) {
        this.dockerRunner = dockerRunner;
        this.pipelineRunRepo = pipelineRunRepo;
        this.stageRunRepo = stageRunRepo;
        this.jobRunRepo = jobRunRepo;
    }

    @Transactional
    public PipelineRunEntity executePipeline(Pipeline pipeline, String repoPath, String gitBranch, String gitCommit) {
        System.out.println("=== Running pipeline: " + pipeline.name + " ===");

        // 1. PIPELINE START - Insert pipeline record
        PipelineRunEntity pipelineRun = new PipelineRunEntity();
        pipelineRun.setPipelineName(pipeline.name);
        pipelineRun.setRunNo(pipelineRunRepo.nextRunNo(pipeline.name));
        pipelineRun.setStatus(RunStatus.RUNNING);
        pipelineRun.setStartTime(OffsetDateTime.now());

        // Set git information
        String actualCommit = gitCommit != null ? gitCommit : GitHelper.currentCommit(repoPath);
        String actualBranch = gitBranch != null ? gitBranch : GitHelper.currentBranch(repoPath);
        pipelineRun.setGitHash(actualCommit);
        pipelineRun.setGitBranch(actualBranch);
        pipelineRun.setGitRepo(repoPath);

        pipelineRun = pipelineRunRepo.save(pipelineRun);
        pipelineRunRepo.flush(); // Ensure immediate persistence

        // Compute execution plan
        ExecutionPlanner planner = new ExecutionPlanner(pipeline);
        List<ExecutionPlanner.StageExecution> plan = planner.computeOrder();

        boolean pipelineFailed = false;
        int stageOrder = 0;

        for (ExecutionPlanner.StageExecution stage : plan) {
            System.out.println("\n--- Stage: " + stage.getStageName() + " ---");

            // 2. STAGE START - Insert stage record
            StageRunEntity stageRun = new StageRunEntity();
            stageRun.setPipelineRun(pipelineRun);
            stageRun.setStageName(stage.getStageName());
            stageRun.setStageOrder(stageOrder++);
            stageRun.setStatus(RunStatus.RUNNING);
            stageRun.setStartTime(OffsetDateTime.now());
            stageRun = stageRunRepo.save(stageRun);
            stageRunRepo.flush();

            boolean stageFailed = false;

            for (Job job : stage.getJobs()) {
                System.out.println("  > Job: " + job.name + " [" + job.image + "]");

                // 3. JOB START - Insert job record
                JobRunEntity jobRun = new JobRunEntity();
                jobRun.setStageRun(stageRun);
                jobRun.setJobName(job.name);
                jobRun.setStatus(RunStatus.RUNNING);
                jobRun.setStartTime(OffsetDateTime.now());
                jobRun = jobRunRepo.save(jobRun);
                jobRunRepo.flush();

                // Execute the job
                JobResult result = dockerRunner.runJob(job.image, job.script, repoPath);

                if (result.output != null && !result.output.isBlank()) {
                    result.output.lines().forEach(l -> System.out.println("    " + l));
                }

                // 4. JOB END - Update job record with result
                jobRun.setEndTime(OffsetDateTime.now());
                if (result.ok()) {
                    jobRun.setStatus(RunStatus.SUCCESS);
                    System.out.println("  v Job '" + job.name + "' passed");
                } else {
                    jobRun.setStatus(RunStatus.FAILED);
                    System.err.println("  x Job '" + job.name + "' failed (exit " + result.exitCode + ")");
                    stageFailed = true;
                    pipelineFailed = true;
                }
                jobRunRepo.save(jobRun);
                jobRunRepo.flush();

                // Stop executing jobs in this stage if one failed
                if (stageFailed) {
                    break;
                }
            }

            // 5. STAGE END - Update stage record
            stageRun.setEndTime(OffsetDateTime.now());
            stageRun.setStatus(stageFailed ? RunStatus.FAILED : RunStatus.SUCCESS);
            stageRunRepo.save(stageRun);
            stageRunRepo.flush();

            // Stop executing stages if pipeline has failed
            if (pipelineFailed) {
                System.err.println("=== Pipeline FAILED ===");
                break;
            }
        }

        // 6. PIPELINE END - Update pipeline record
        pipelineRun.setEndTime(OffsetDateTime.now());
        pipelineRun.setStatus(pipelineFailed ? RunStatus.FAILED : RunStatus.SUCCESS);
        pipelineRun = pipelineRunRepo.save(pipelineRun);
        pipelineRunRepo.flush();

        if (!pipelineFailed) {
            System.out.println("\n=== Pipeline PASSED ===");
        }

        return pipelineRun;
    }
}