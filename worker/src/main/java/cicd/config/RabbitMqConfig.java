package cicd.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * RabbitMQ configuration for the Worker module.
 * Declares the same exchanges/queues as the server to ensure topology exists.
 */
@Configuration
public class RabbitMqConfig {

  // Exchange names
  public static final String PIPELINE_EXCHANGE = "cicd.pipeline.direct";
  public static final String JOB_EXCHANGE = "cicd.job.direct";
  public static final String JOB_RESULTS_EXCHANGE = "cicd.job-results.direct";
  public static final String STATUS_EXCHANGE = "cicd.status.direct";
  public static final String EVENTS_EXCHANGE = "cicd.events.topic";
  public static final String DLX_EXCHANGE = "cicd.dlx";

  // Queue names
  public static final String PIPELINE_EXECUTE_QUEUE = "cicd.pipeline.execute";
  public static final String JOB_EXECUTE_QUEUE = "cicd.job.execute";
  public static final String JOB_RESULTS_QUEUE = "cicd.job.results";
  public static final String STATUS_UPDATE_QUEUE = "cicd.status.update";
  public static final String EVENTS_QUEUE = "cicd.events";
  public static final String DEAD_LETTER_QUEUE = "cicd.dead-letters";

  // Routing keys
  public static final String PIPELINE_EXECUTE_KEY = "pipeline.execute";
  public static final String JOB_EXECUTE_KEY = "job.execute";
  public static final String JOB_RESULT_KEY = "job.result";
  public static final String STATUS_UPDATE_KEY = "status.update";

  // --- Exchanges ---

  @Bean
  public DirectExchange pipelineExchange() {
    return new DirectExchange(PIPELINE_EXCHANGE, true, false);
  }

  @Bean
  public DirectExchange jobExchange() {
    return new DirectExchange(JOB_EXCHANGE, true, false);
  }

  @Bean
  public DirectExchange jobResultsExchange() {
    return new DirectExchange(JOB_RESULTS_EXCHANGE, true, false);
  }

  @Bean
  public DirectExchange statusExchange() {
    return new DirectExchange(STATUS_EXCHANGE, true, false);
  }

  @Bean
  public TopicExchange eventsExchange() {
    return new TopicExchange(EVENTS_EXCHANGE, true, false);
  }

  @Bean
  public DirectExchange dlxExchange() {
    return new DirectExchange(DLX_EXCHANGE, true, false);
  }

  // --- Queues ---

  @Bean
  public Queue pipelineExecuteQueue() {
    return QueueBuilder.durable(PIPELINE_EXECUTE_QUEUE)
        .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
        .build();
  }

  @Bean
  public Queue jobExecuteQueue() {
    return QueueBuilder.durable(JOB_EXECUTE_QUEUE)
        .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
        .build();
  }

  @Bean
  public Queue jobResultsQueue() {
    return QueueBuilder.durable(JOB_RESULTS_QUEUE).build();
  }

  @Bean
  public Queue statusUpdateQueue() {
    return QueueBuilder.durable(STATUS_UPDATE_QUEUE)
        .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
        .build();
  }

  @Bean
  public Queue eventsQueue() {
    return QueueBuilder.durable(EVENTS_QUEUE).build();
  }

  @Bean
  public Queue deadLetterQueue() {
    return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
  }

  // --- Bindings ---

  @Bean
  public Binding pipelineExecuteBinding() {
    return BindingBuilder.bind(pipelineExecuteQueue())
        .to(pipelineExchange()).with(PIPELINE_EXECUTE_KEY);
  }

  @Bean
  public Binding jobExecuteBinding() {
    return BindingBuilder.bind(jobExecuteQueue())
        .to(jobExchange()).with(JOB_EXECUTE_KEY);
  }

  @Bean
  public Binding jobResultsBinding() {
    return BindingBuilder.bind(jobResultsQueue())
        .to(jobResultsExchange()).with(JOB_RESULT_KEY);
  }

  @Bean
  public Binding statusUpdateBinding() {
    return BindingBuilder.bind(statusUpdateQueue())
        .to(statusExchange()).with(STATUS_UPDATE_KEY);
  }

  @Bean
  public Binding eventsBinding() {
    return BindingBuilder.bind(eventsQueue())
        .to(eventsExchange()).with("#");
  }

  @Bean
  public Binding deadLetterBinding() {
    return BindingBuilder.bind(deadLetterQueue())
        .to(dlxExchange()).with("#");
  }

  // --- Message converter ---

  @Bean
  public MessageConverter jsonMessageConverter() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    return new Jackson2JsonMessageConverter(mapper);
  }

  @Bean
  @Primary
  public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
      MessageConverter jsonMessageConverter) {
    RabbitTemplate template = new RabbitTemplate(connectionFactory);
    template.setMessageConverter(jsonMessageConverter);
    return template;
  }

  @Bean
  public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
      ConnectionFactory connectionFactory,
      MessageConverter jsonMessageConverter) {
    SimpleRabbitListenerContainerFactory factory =
        new SimpleRabbitListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    factory.setMessageConverter(jsonMessageConverter);
    factory.setPrefetchCount(1);
    return factory;
  }
}

