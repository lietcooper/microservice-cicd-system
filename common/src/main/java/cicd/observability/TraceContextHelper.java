package cicd.observability;

import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import java.util.Map;

/**
 * Utility for injecting and extracting W3C TraceContext
 * into/from AMQP message headers.
 */
public final class TraceContextHelper {

  private static final W3CTraceContextPropagator PROPAGATOR =
      W3CTraceContextPropagator.getInstance();

  private static final TextMapSetter<Map<String, Object>> SETTER =
      (carrier, key, value) -> {
        if (carrier != null) {
          carrier.put(key, value);
        }
      };

  private static final TextMapGetter<Map<String, Object>> GETTER =
      new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, Object> carrier) {
          return carrier.keySet();
        }

        @Override
        public String get(Map<String, Object> carrier, String key) {
          if (carrier == null) {
            return null;
          }
          Object value = carrier.get(key);
          return value != null ? value.toString() : null;
        }
      };

  private TraceContextHelper() {
  }

  /**
   * Injects the current trace context into AMQP message headers.
   *
   * @param headers the mutable header map from MessageProperties
   */
  public static void injectContext(Map<String, Object> headers) {
    PROPAGATOR.inject(Context.current(), headers, SETTER);
  }

  /**
   * Extracts trace context from AMQP message headers.
   *
   * @param headers the header map from MessageProperties
   * @return the extracted context (or Context.current() if none found)
   */
  public static Context extractContext(Map<String, Object> headers) {
    return PROPAGATOR.extract(Context.current(), headers, GETTER);
  }
}
