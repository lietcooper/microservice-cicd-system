package cicd.listener;

import cicd.config.RabbitMqConfig;
import cicd.messaging.JobResultMessage;
import cicd.service.StageCoordinatorService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Listens for job result messages and feeds them into the
 * StageCoordinatorService for wave tracking / fan-in.
 */
@Component
public class JobResultListener {

  private final StageCoordinatorService coordinator;

  public JobResultListener(StageCoordinatorService coordinator) {
    this.coordinator = coordinator;
  }

  @RabbitListener(queues = RabbitMqConfig.JOB_RESULTS_QUEUE)
  public void onJobResult(JobResultMessage result) {
    System.out.println("  < Result received: job='" + result.getJobName()
        + "' success=" + result.isSuccess()
        + " correlationId=" + result.getCorrelationId());
    coordinator.recordResult(result.getCorrelationId(), result);
  }
}
