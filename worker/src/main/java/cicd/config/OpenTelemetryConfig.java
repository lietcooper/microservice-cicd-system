package cicd.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.ResourceAttributes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configures OpenTelemetry SDK for tracing and log export in the worker module. */
@Configuration
public class OpenTelemetryConfig {

  @Value("${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4317}")
  private String otlpEndpoint;

  /** Creates the OpenTelemetry SDK instance with OTLP exporter. */
  @Bean
  public OpenTelemetry openTelemetry() {
    Resource resource = Resource.getDefault().toBuilder()
        .put(ResourceAttributes.SERVICE_NAME, "cicd-worker")
        .build();

    OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
        .setEndpoint(otlpEndpoint)
        .build();

    SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
        .setResource(resource)
        .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
        .build();

    OtlpGrpcLogRecordExporter logExporter = OtlpGrpcLogRecordExporter.builder()
        .setEndpoint(otlpEndpoint)
        .build();

    SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
        .setResource(resource)
        .addLogRecordProcessor(
            BatchLogRecordProcessor.builder(logExporter).build())
        .build();

    OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .setLoggerProvider(loggerProvider)
        .setPropagators(ContextPropagators.create(
            W3CTraceContextPropagator.getInstance()))
        .buildAndRegisterGlobal();

    OpenTelemetryAppender.install(sdk);
    return sdk;
  }

  /** Creates a Tracer bean for dependency injection. */
  @Bean
  public Tracer tracer(OpenTelemetry openTelemetry) {
    return openTelemetry.getTracer("cicd-worker");
  }
}
