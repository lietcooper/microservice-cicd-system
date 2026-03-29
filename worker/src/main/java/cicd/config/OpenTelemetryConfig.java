package cicd.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.ResourceAttributes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configures OpenTelemetry SDK for tracing in the worker module. */
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

    return OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .buildAndRegisterGlobal();
  }

  /** Creates a Tracer bean for dependency injection. */
  @Bean
  public Tracer tracer(OpenTelemetry openTelemetry) {
    return openTelemetry.getTracer("cicd-worker");
  }
}
