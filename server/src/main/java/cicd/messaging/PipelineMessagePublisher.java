package cicd.messaging;

import cicd.config.RabbitMqConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes pipeline execution requests to RabbitMQ.
 */
@Component
public class PipelineMessagePublisher {

  private static final Logger log =
      LoggerFactory.getLogger(PipelineMessagePublisher.class);

  private final RabbitTemplate rabbitTemplate;

  /** Creates a publisher with the given template. */
  public PipelineMessagePublisher(RabbitTemplate rabbitTemplate) {
    this.rabbitTemplate = rabbitTemplate;
  }

  /**
   * Publishes a pipeline execution message to the pipeline exchange.
   */
  public void publishPipelineExecute(PipelineExecuteMessage message) {
    log.info("Publishing pipeline execute message for '{}'",
        message.getPipelineName());
    rabbitTemplate.convertAndSend(
        RabbitMqConfig.PIPELINE_EXCHANGE,
        RabbitMqConfig.PIPELINE_EXECUTE_KEY,
        message
    );
  }
}
