package cicd.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for TraceContextHelper.
 * Verifies inject/extract roundtrip with message header maps.
 */
class TraceContextHelperTest {

  @Test
  void injectAndExtractRoundtrip() {
    // Create a span context with known trace and span IDs
    String traceId = "0af7651916cd43dd8448eb211c80319c";
    String spanId = "b7ad6b7169203331";

    SpanContext spanContext = SpanContext.create(
        traceId, spanId, TraceFlags.getSampled(), TraceState.getDefault());
    Context parentContext = Context.root().with(Span.wrap(spanContext));

    // Inject into headers
    Map<String, Object> headers = new HashMap<>();
    parentContext.makeCurrent();
    try {
      TraceContextHelper.injectContext(headers);
    } finally {
      // Scope is auto-closed via makeCurrent, but we need the finally
      // to restore context; in test this is handled by the try block
    }

    // Verify headers contain traceparent
    assertTrue(headers.containsKey("traceparent"),
        "Headers should contain traceparent");
    String traceparent = headers.get("traceparent").toString();
    assertTrue(traceparent.contains(traceId),
        "traceparent should contain the trace ID");
    assertTrue(traceparent.contains(spanId),
        "traceparent should contain the span ID");

    // Extract from headers
    Context extracted = TraceContextHelper.extractContext(headers);
    assertNotNull(extracted);

    SpanContext extractedSpanContext =
        Span.fromContext(extracted).getSpanContext();
    assertEquals(traceId, extractedSpanContext.getTraceId());
    assertEquals(spanId, extractedSpanContext.getSpanId());
  }

  @Test
  void extractFromEmptyHeadersReturnsCurrentContext() {
    Map<String, Object> emptyHeaders = new HashMap<>();
    Context extracted = TraceContextHelper.extractContext(emptyHeaders);
    assertNotNull(extracted);

    SpanContext spanContext =
        Span.fromContext(extracted).getSpanContext();
    assertFalse(spanContext.isValid(),
        "Extracted context from empty headers should not have a valid span");
  }

  @Test
  void injectWithNoActiveSpanDoesNotFail() {
    Map<String, Object> headers = new HashMap<>();
    // No active span — should not throw
    TraceContextHelper.injectContext(headers);
    // Headers may be empty or contain an invalid traceparent; either is fine
    assertNotNull(headers);
  }
}
