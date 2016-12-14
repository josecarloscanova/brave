package brave;

import brave.internal.Nullable;
import com.google.auto.value.AutoValue;
import zipkin.Annotation;
import zipkin.BinaryAnnotation;
import zipkin.Constants;
import zipkin.Endpoint;
import zipkin.reporter.Reporter;

import static zipkin.Constants.LOCAL_COMPONENT;

/**
 * Offset-based span: Uses a single point of reference and offsets to create annotation timestamps.
 *
 * <p>This is a mutable object representing a specific span. It must be locked externally.
 *
 * <p>Method signatures are based on the zipkin 2 model eventhough it isn't out, yet.
 */
// Since this is not exposed, this class could be refactored later as needed to act in a pool
// to reduce GC churn. This would involve a different span builder, or a change to it to allow
// a clear function. Since the current design is measurably more performant than prior,
// intentionally delaying these optimizations.
final class InternalSpan implements Clock {

  @AutoValue
  abstract static class Factory {
    static Builder builder() {
      return new AutoValue_InternalSpan_Factory.Builder();
    }

    @AutoValue.Builder interface Builder {
      Builder clock(Clock clock);

      Builder ticker(Ticker ticker);

      Builder localEndpoint(Endpoint localEndpoint);

      Builder reporter(Reporter<zipkin.Span> reporter);

      Factory build();
    }

    abstract Endpoint localEndpoint();

    abstract Clock clock();

    abstract Ticker ticker();

    abstract Reporter<zipkin.Span> reporter();

    InternalSpan create(TraceContext context) {
      return new InternalSpan(this, context);
    }

    Factory() {
    }
  }

  final Endpoint localEndpoint;
  final Reporter<zipkin.Span> reporter;
  final Ticker ticker;
  final zipkin.Span.Builder span;

  // all ticks are relative to these
  final long createTimestamp;
  final long createTick;

  // fields which are added late
  long startTimestamp;
  Endpoint remoteEndpoint;

  // flags which help us know how to reassemble the span
  final boolean shared;
  Span.Kind kind;
  // Until model v2, we have to ensure at least one annotation or binary annotation exists
  boolean hasLocalEndpoint;
  boolean finished;

  InternalSpan(Factory factory, TraceContext context) {
    localEndpoint = factory.localEndpoint();
    reporter = factory.reporter();
    ticker = factory.ticker();
    createTimestamp = factory.clock().epochMicros();
    createTick = factory.ticker().tickNanos();
    shared = context.shared();
    span = zipkin.Span.builder()
        .traceIdHigh(context.traceId().hi())
        .traceId(context.traceId().lo())
        .parentId(context.parentId())
        .id(context.spanId())
        .debug(context.debug())
        .name(""); // avoid a NPE
  }

  /** gets a timestamp based on this span. */
  @Override public long epochMicros() {
    return ((ticker.tickNanos() - createTick) / 1000) + createTimestamp;
  }

  void start(long timestamp) {
    startTimestamp = timestamp;
  }

  void name(String name) {
    span.name(name);
  }

  void kind(Span.Kind kind) {
    this.kind = kind;
  }

  void annotate(long timestamp, String value) {
    span.addAnnotation(Annotation.create(timestamp, value, localEndpoint));
    hasLocalEndpoint = true;
  }

  void tag(String key, String value) {
    span.addBinaryAnnotation(BinaryAnnotation.create(key, value, localEndpoint));
    hasLocalEndpoint = true;
  }

  void remoteEndpoint(Endpoint remoteEndpoint) {
    this.remoteEndpoint = remoteEndpoint;
  }

  /** Completes and reports the span */
  void finish(@Nullable Long finishTimestamp, @Nullable Long duration) {
    if (startTimestamp == 0) throw new IllegalStateException("span was never started");
    if (finished) return;
    finished = true;

    finishTimestamp = addTimestampAndDuration(finishTimestamp, duration);
    if (kind != null) {
      String remoteEndpointType;
      String startAnnotation;
      String finishAnnotation;
      switch (kind) {
        case CLIENT:
          remoteEndpointType = Constants.SERVER_ADDR;
          startAnnotation = Constants.CLIENT_SEND;
          finishAnnotation = Constants.CLIENT_RECV;
          break;
        case SERVER:
          remoteEndpointType = Constants.CLIENT_ADDR;
          startAnnotation = Constants.SERVER_RECV;
          finishAnnotation = Constants.SERVER_SEND;
          // don't report server-side timestamp on shared-spans
          if (shared) span.timestamp(null).duration(null);
          break;
        default:
          throw new AssertionError("update kind mapping");
      }
      if (remoteEndpoint != null) {
        span.addBinaryAnnotation(BinaryAnnotation.address(remoteEndpointType, remoteEndpoint));
      }
      span.addAnnotation(Annotation.create(startTimestamp, startAnnotation, localEndpoint));
      if (finishAnnotation != null) {
        span.addAnnotation(Annotation.create(finishTimestamp, finishAnnotation, localEndpoint));
      }
      hasLocalEndpoint = true;
    }

    if (!hasLocalEndpoint) { // create a small dummy annotation
      span.addBinaryAnnotation(BinaryAnnotation.create(LOCAL_COMPONENT, "", localEndpoint));
    }

    reporter.report(span.build());
    // in some future world, we could recycle this object now
  }

  // one or the other will be null
  @Nullable Long addTimestampAndDuration(@Nullable Long finishTimestamp, @Nullable Long duration) {
    assert finishTimestamp != null || duration != null; // guard programming errors
    if (duration != null) {
      finishTimestamp = startTimestamp + duration; 
    } else {
      duration = finishTimestamp - startTimestamp;
    }
    span.timestamp(startTimestamp).duration(duration);
    return finishTimestamp;
  }
}
