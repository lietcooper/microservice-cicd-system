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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

  /** Creates the service with required dependencies. */
  public PipelineExecutorService(DockerRunner dockerRunner,
      PipelineRunRepository pipelineRunRepo,
      StageRunRepository stageRunRepo,
      JobRunRepository jobRunRepo) {
    this.dockerRunner = dockerRunner;
    this.pipelineRunRepo = pipelineRunRepo;
    this.stageRunRepo = stageRunRepo;
    this.jobRunRepo = jobRunRepo;
  }

  /** Executes a pipeline synchronously with DB persistence. */
  @Transactional
  public PipelineRunEntity executePipeline(Pipeline pipeline,
      String repoPath, String gitBranch, String gitCommit) {
    System.out.println(
        "=== Running pipeline: " + pipeline.name + " ===");

    PipelineRunEntity pipelineRun = new PipelineRunEntity();
    pipelineRun.setPipelineName(pipeline.name);
    pipelineRun.setRunNo(
        pipelineRunRepo.nextRunNo(pipeline.name));
    pipelineRun.setStatus(RunStatus.RUNNING);
    pipelineRun.setStartTime(OffsetDateTime.now());

    String actualCommit = gitCommit != null
        ? gitCommit : GitHelper.currentCommit(repoPath);
    String actualBranch = gitBranch != null
        ? gitBranch : GitHelper.currentBranch(repoPath);
    pipelineRun.setGitHash(actualCommit);
    pipelineRun.setGitBranch(actualBranch);
    pipelineRun.setGitRepo(repoPath);

    pipelineRun = pipelineRunRepo.save(pipelineRun);
    pipelineRunRepo.flush();

    ExecutionPlanner planner = new ExecutionPlanner(pipeline);
    List<ExecutionPlanner.StageExecution> plan =
        planner.computeOrder();

    boolean pipelineFailed = false;
    int stageOrder = 0;

    for (ExecutionPlanner.StageExecution stage : plan) {
      System.out.println(
          "\n--- Stage: " + stage.getStageName() + " ---");

      StageRunEntity stageRun = new StageRunEntity();
      stageRun.setPipelineRun(pipelineRun);
      stageRun.setStageName(stage.getStageName());
      stageRun.setStageOrder(stageOrder++);
      stageRun.setStatus(RunStatus.RUNNING);
      stageRun.setStartTime(OffsetDateTime.now());
      stageRun = stageRunRepo.save(stageRun);
      stageRunRepo.flush();

      boolean stageFailed = false;
      Set<String> failedRequiredJobs = new HashSet<>();

      for (Job job : stage.getJobs()) {
        // Skip jobs blocked by a failed required dependency
        boolean blocked = job.needs.stream()
            .anyMatch(failedRequiredJobs::contains);
        if (blocked) {
          JobRunEntity jobRun = new JobRunEntity();
          jobRun.setStageRun(stageRun);
          jobRun.setJobName(job.name);
          jobRun.setAllowFailure(job.allowFailure);
          jobRun.setStatus(RunStatus.FAILED);
          jobRun.setStartTime(OffsetDateTime.now());
          jobRun.setEndTime(OffsetDateTime.now());
          jobRunRepo.save(jobRun);
          jobRunRepo.flush();
          if (!job.allowFailure) {
            failedRequiredJobs.add(job.name);
          }
          System.out.println("  > Skipped job: " + job.name
              + " (blocked by failed dependency)");
          continue;
        }

        System.out.println(
            "  > Job: " + job.name + " [" + job.image + "]");

        JobRunEntity jobRun = new JobRunEntity();
        jobRun.setStageRun(stageRun);
        jobRun.setJobName(job.name);
        jobRun.setAllowFailure(job.allowFailure);
        jobRun.setStatus(RunStatus.RUNNING);
        jobRun.setStartTime(OffsetDateTime.now());
        jobRun = jobRunRepo.save(jobRun);
        jobRunRepo.flush();

        JobResult result =
            dockerRunner.runJob(job.image, job.script, repoPath);

        if (result.output() != null
            && !result.output().isBlank()) {
          result.output().lines().forEach(
              ll -> System.out.println("    " + ll));
        }

        jobRun.setEndTime(OffsetDateTime.now());
        if (result.ok()) {
          jobRun.setStatus(RunStatus.SUCCESS);
          System.out.println(
              "  v Job '" + job.name + "' passed");
        } else {
          jobRun.setStatus(RunStatus.FAILED);
          System.err.println("  x Job '" + job.name
              + "' failed (exit " + result.exitCode() + ")");
          if (!job.allowFailure) {
            failedRequiredJobs.add(job.name);
            stageFailed = true;
            pipelineFailed = true;
          }
        }
        jobRunRepo.save(jobRun);
        jobRunRepo.flush();
      }

      stageRun.setEndTime(OffsetDateTime.now());
      stageRun.setStatus(
          stageFailed ? RunStatus.FAILED : RunStatus.SUCCESS);
      stageRunRepo.save(stageRun);
      stageRunRepo.flush();

      if (pipelineFailed) {
        System.err.println("=== Pipeline FAILED ===");
        break;
      }
    }

    pipelineRun.setEndTime(OffsetDateTime.now());
    pipelineRun.setStatus(
        pipelineFailed ? RunStatus.FAILED : RunStatus.SUCCESS);
    pipelineRun = pipelineRunRepo.save(pipelineRun);
    pipelineRunRepo.flush();

    if (!pipelineFailed) {
      System.out.println("\n=== Pipeline PASSED ===");
    }

    return pipelineRun;
  }
}
