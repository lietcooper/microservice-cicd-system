package cicd.service;

import cicd.messaging.JobResultMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Coordinates wave-level fan-out/fan-in for parallel job execution.
 * Each wave is tracked by a correlationId.
 */
@org.springframework.stereotype.Service("workerStageCoordinatorService")
public class StageCoordinatorService {

  private static final long WAVE_TIMEOUT_MINUTES = 30;

  private final ConcurrentHashMap<String, WaveTracker> waveTrackers =
      new ConcurrentHashMap<>();

  /**
   * Registers a wave for tracking. Must be called before publishing jobs.
   *
   * @param correlationId unique ID for this wave
   * @param jobCount number of jobs in this wave
   */
  public void registerWave(String correlationId, int jobCount) {
    waveTrackers.put(correlationId, new WaveTracker(jobCount));
  }

  /**
   * Records a job result for a wave. Called by the result listener.
   *
   * @param correlationId the wave this result belongs to
   * @param result the job result message
   */
  public void recordResult(String correlationId,
      JobResultMessage result) {
    WaveTracker tracker = waveTrackers.get(correlationId);
    if (tracker != null) {
      tracker.addResult(result);
      tracker.countDown();
    }
  }

  /**
   * Blocks until all jobs in the wave complete or timeout.
   *
   * @param correlationId the wave to wait for
   * @return the wave tracker with all results, or null if not found
   */
  public WaveTracker awaitWave(String correlationId) {
    WaveTracker tracker = waveTrackers.get(correlationId);
    if (tracker == null) {
      return null;
    }
    try {
      boolean completed = tracker.await(
          WAVE_TIMEOUT_MINUTES, TimeUnit.MINUTES);
      if (!completed) {
        tracker.setTimedOut(true);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      tracker.setTimedOut(true);
    }
    return tracker;
  }

  /** Removes and cleans up a completed wave tracker. */
  public void removeWave(String correlationId) {
    waveTrackers.remove(correlationId);
  }

  /**
   * Tracks the results and completion of a single wave of parallel jobs.
   */
  public static class WaveTracker {

    private final CountDownLatch latch;
    private final List<JobResultMessage> results;
    private volatile boolean timedOut;

    /** Creates a tracker expecting the given number of jobs. */
    public WaveTracker(int jobCount) {
      this.latch = new CountDownLatch(jobCount);
      this.results = new ArrayList<>();
      this.timedOut = false;
    }

    /** Decrements the remaining job count by one. */
    public void countDown() {
      latch.countDown();
    }

    /**
     * Awaits completion up to the given timeout.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit
     * @return true if all jobs completed in time
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean await(long timeout, TimeUnit unit)
        throws InterruptedException {
      return latch.await(timeout, unit);
    }

    /** Adds a job result to this wave tracker. */
    public synchronized void addResult(
        JobResultMessage result) {
      results.add(result);
    }

    /** Returns a copy of all recorded results. */
    public synchronized List<JobResultMessage> getResults() {
      return new ArrayList<>(results);
    }

    /** Returns whether this wave timed out. */
    public boolean isTimedOut() {
      return timedOut;
    }

    /** Sets whether this wave timed out. */
    public void setTimedOut(boolean timedOut) {
      this.timedOut = timedOut;
    }

    /** Returns true if all jobs succeeded and no timeout occurred. */
    public boolean allSucceeded() {
      return !timedOut
          && results.stream().allMatch(JobResultMessage::isSuccess);
    }
  }
}
