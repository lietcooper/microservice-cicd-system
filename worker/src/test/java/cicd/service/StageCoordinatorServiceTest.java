package cicd.service;

import cicd.messaging.JobResultMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class StageCoordinatorServiceTest {

  private StageCoordinatorService coordinator;

  @BeforeEach
  void setUp() {
    coordinator = new StageCoordinatorService();
  }

  @Test
  void testRegisterAndAwaitSingleJob() {
    String corrId = "wave-1";
    coordinator.registerWave(corrId, 1);

    // Simulate result from another thread
    Thread worker = new Thread(() -> {
      JobResultMessage result = createResult("job1", true);
      coordinator.recordResult(corrId, result);
    });
    worker.start();

    StageCoordinatorService.WaveTracker tracker = coordinator.awaitWave(corrId);
    assertNotNull(tracker);
    assertTrue(tracker.allSucceeded());
    assertEquals(1, tracker.getResults().size());
    assertFalse(tracker.isTimedOut());
  }

  @Test
  void testMultipleJobsInWave() throws InterruptedException {
    String corrId = "wave-multi";
    coordinator.registerWave(corrId, 3);

    ExecutorService exec = Executors.newFixedThreadPool(3);
    for (int i = 0; i < 3; i++) {
      final int idx = i;
      exec.submit(() -> {
        JobResultMessage result = createResult("job" + idx, true);
        coordinator.recordResult(corrId, result);
      });
    }

    StageCoordinatorService.WaveTracker tracker = coordinator.awaitWave(corrId);
    assertNotNull(tracker);
    assertTrue(tracker.allSucceeded());
    assertEquals(3, tracker.getResults().size());

    exec.shutdown();
    exec.awaitTermination(5, TimeUnit.SECONDS);
  }

  @Test
  void testWaveWithFailedJob() {
    String corrId = "wave-fail";
    coordinator.registerWave(corrId, 2);

    coordinator.recordResult(corrId, createResult("job1", true));
    coordinator.recordResult(corrId, createResult("job2", false));

    StageCoordinatorService.WaveTracker tracker = coordinator.awaitWave(corrId);
    assertNotNull(tracker);
    assertFalse(tracker.allSucceeded());
    assertEquals(2, tracker.getResults().size());
  }

  @Test
  void testAwaitNonExistentWave() {
    StageCoordinatorService.WaveTracker tracker =
        coordinator.awaitWave("nonexistent");
    assertNull(tracker);
  }

  @Test
  void testRemoveWave() {
    String corrId = "wave-remove";
    coordinator.registerWave(corrId, 1);
    coordinator.recordResult(corrId, createResult("job1", true));

    coordinator.awaitWave(corrId);
    coordinator.removeWave(corrId);

    // After removal, await should return null
    assertNull(coordinator.awaitWave(corrId));
  }

  @Test
  void testRecordResultForUnknownCorrelation() {
    // Should not throw; just silently ignore
    coordinator.recordResult("unknown", createResult("job1", true));
  }

  @Test
  void testWaveTrackerAllSucceeded() {
    StageCoordinatorService.WaveTracker tracker =
        new StageCoordinatorService.WaveTracker(2);

    tracker.addResult(createResult("job1", true));
    tracker.countDown();
    tracker.addResult(createResult("job2", true));
    tracker.countDown();

    assertTrue(tracker.allSucceeded());
  }

  @Test
  void testWaveTrackerNotAllSucceeded() {
    StageCoordinatorService.WaveTracker tracker =
        new StageCoordinatorService.WaveTracker(2);

    tracker.addResult(createResult("job1", true));
    tracker.countDown();
    tracker.addResult(createResult("job2", false));
    tracker.countDown();

    assertFalse(tracker.allSucceeded());
  }

  @Test
  void testWaveTrackerTimedOut() {
    StageCoordinatorService.WaveTracker tracker =
        new StageCoordinatorService.WaveTracker(1);

    tracker.setTimedOut(true);
    // Even if we add a success result, timedOut makes allSucceeded false
    tracker.addResult(createResult("job1", true));

    assertFalse(tracker.allSucceeded());
    assertTrue(tracker.isTimedOut());
  }

  @Test
  void testConcurrentResultRecording() throws InterruptedException {
    String corrId = "wave-concurrent";
    int jobCount = 10;
    coordinator.registerWave(corrId, jobCount);

    CountDownLatch startLatch = new CountDownLatch(1);
    ExecutorService exec = Executors.newFixedThreadPool(jobCount);

    for (int i = 0; i < jobCount; i++) {
      final int idx = i;
      exec.submit(() -> {
        try {
          startLatch.await(); // All threads start at once
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        coordinator.recordResult(corrId, createResult("job" + idx, true));
      });
    }

    startLatch.countDown(); // Release all threads

    StageCoordinatorService.WaveTracker tracker = coordinator.awaitWave(corrId);
    assertNotNull(tracker);
    assertTrue(tracker.allSucceeded());
    assertEquals(jobCount, tracker.getResults().size());

    exec.shutdown();
    exec.awaitTermination(5, TimeUnit.SECONDS);
  }

  private JobResultMessage createResult(String jobName, boolean success) {
    JobResultMessage msg = new JobResultMessage();
    msg.setJobName(jobName);
    msg.setSuccess(success);
    msg.setExitCode(success ? 0 : 1);
    msg.setCorrelationId("test-corr");
    msg.setPipelineRunId(1L);
    msg.setStageRunId(1L);
    msg.setJobRunId(1L);
    return msg;
  }
}
