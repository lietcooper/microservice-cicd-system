package cicd.listener;

import cicd.config.RabbitMqConfig;
import cicd.messaging.JobResultMessage;
import cicd.service.StageCoordinatorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Listens for job result messages and feeds them into the
 * StageCoordinatorService for wave tracking / fan-in.
 */
@Component
public class JobResultListener {

  private static final Logger log =
      LoggerFactory.getLogger(JobResultListener.class);

  private final StageCoordinatorService coordinator;

  /** Creates a listener with the given coordinator. */
  public JobResultListener(StageCoordinatorService coordinator) {
    this.coordinator = coordinator;
  }

  /** Forwards a job result to the stage coordinator. */
  @RabbitListener(queues = RabbitMqConfig.JOB_RESULTS_QUEUE)
  public void onJobResult(JobResultMessage result) {
    MDC.put("source", "system");
    try {
      log.info("Result received: job='{}' success={} correlationId={}",
          result.getJobName(), result.isSuccess(),
          result.getCorrelationId());
      coordinator.recordResult(result.getCorrelationId(), result);
    } finally {
      MDC.remove("source");
    }
  }
}
