package brave;

import brave.internal.Internal;
import brave.internal.Nullable;
import brave.internal.Platform;
import brave.sampler.Sampler;
import com.google.auto.value.AutoValue;
import zipkin.Endpoint;
import zipkin.reporter.Reporter;

@AutoValue
public abstract class Tracer implements Span.Factory {

  public static Builder builder() {
    return new Builder();
  }

  /** Configuration including defaults needed to send spans to a Kafka topic. */
  public static final class Builder {
    final InternalSpan.Factory.Builder spanFactoryBuilder = InternalSpan.Factory.builder()
        .clock(Platform.get())
        .reporter(Platform.get())
        .ticker(Platform.get());
    Sampler sampler = Sampler.ALWAYS_SAMPLE;
    boolean traceId128Bit = false;

    public Builder localEndpoint(Endpoint localEndpoint) {
      spanFactoryBuilder.localEndpoint(localEndpoint);
      return this;
    }

    public Builder traceId128Bit(boolean traceId128Bit) {
      this.traceId128Bit = traceId128Bit;
      return this;
    }

    public Builder sampler(Sampler sampler) {
      this.sampler = sampler;
      return this;
    }

    public Builder clock(Clock clock) {
      spanFactoryBuilder.clock(clock);
      return this;
    }

    public Builder ticker(Ticker ticker) {
      spanFactoryBuilder.ticker(ticker);
      return this;
    }

    public Builder reporter(Reporter<zipkin.Span> reporter) {
      spanFactoryBuilder.reporter(reporter);
      return this;
    }

    public Tracer build() {
      InternalSpan.Factory spanFactory;
      try {
        spanFactory = spanFactoryBuilder.build();
      } catch (IllegalStateException e) { // lazy initialize endpoint
        spanFactory = spanFactoryBuilder.localEndpoint(Platform.get().localEndpoint()).build();
      }
      return new AutoValue_Tracer(traceId128Bit, sampler, spanFactory);
    }
  }

  static {
    Internal.instance = new Internal() {
      @Override public TraceContext.Builder newTraceContextBuilder() {
        return new AutoValue_TraceContext.Builder().debug(false).shared(false);
      }
    };
  }

  abstract boolean traceId128Bit();

  abstract Sampler sampler();

  abstract InternalSpan.Factory spanFactory();

  /** Continues a span started on another host. This is only used for */
  public Span joinSpan(TraceContext context) {
    return toSpan(context);
  }

  public Span newTrace() {
    return toSpan(nextContext(null));
  }

  @Override
  public Span newSpan(@Nullable TraceContext parent) {
    if (parent != null && Boolean.FALSE.equals(parent.sampled())) {
      return new NoopSpan(parent);
    }
    return toSpan(nextContext(parent));
  }

  Span toSpan(TraceContext context) {
    if (context.sampled() == null) {
      context = context.toBuilder()
          .sampled(sampler().isSampled(context.traceId()))
          .shared(false)
          .build();
    }

    if (context.sampled()) {
      // TODO: track and use these for a brave 3 and/or finagle bridge
      // Ex lookup on the trace context so multiple apis can contribute to the same span.
      InternalSpan mutable = spanFactory().create(context);
      return new RealSpan(context, mutable);
    }
    return new NoopSpan(context);
  }

  TraceContext nextContext(@Nullable TraceContext parent) {
    long nextId = Platform.get().randomLong();
    if (parent != null) {
      return parent.toBuilder().spanId(nextId).parentId(parent.spanId()).build();
    }
    return Internal.instance.newTraceContextBuilder()
        .traceId(TraceId.create(traceId128Bit() ? Platform.get().randomLong() : 0, nextId))
        .spanId(nextId).build();
  }
}
