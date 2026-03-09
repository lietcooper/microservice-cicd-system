package cicd.messaging;

import cicd.config.RabbitMqConfig;
import cicd.messaging.PipelineExecuteMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes pipeline execution requests to RabbitMQ.
 */
@Component
public class PipelineMessagePublisher {

  private final RabbitTemplate rabbitTemplate;

  public PipelineMessagePublisher(RabbitTemplate rabbitTemplate) {
    this.rabbitTemplate = rabbitTemplate;
  }

  /**
   * Publishes a pipeline execution message to the pipeline exchange.
   */
  public void publishPipelineExecute(PipelineExecuteMessage message) {
    rabbitTemplate.convertAndSend(
        RabbitMqConfig.PIPELINE_EXCHANGE,
        RabbitMqConfig.PIPELINE_EXECUTE_KEY,
        message
    );
  }
}
