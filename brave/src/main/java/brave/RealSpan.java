package brave;

import zipkin.Endpoint;

/** This wraps the public api and guards access to a mutable span. */
final class RealSpan extends Span {

  final TraceContext context;
  final InternalSpan internal; // guarded by itself

  RealSpan(TraceContext context, InternalSpan internal) {
    this.context = context;
    this.internal = internal;
  }

  @Override public boolean isNoop() {
    return false;
  }

  @Override public TraceContext context() {
    return context;
  }

  @Override public Span start() {
    return start(internal.epochMicros());
  }

  @Override public Span start(long timestamp) {
    synchronized (internal) {
      internal.start(timestamp);
    }
    return this;
  }

  @Override public Span name(String name) {
    if (name == null) throw new NullPointerException("name == null");
    synchronized (internal) {
      internal.name(name);
    }
    return this;
  }

  @Override public Span kind(Kind kind) {
    if (kind == null) throw new NullPointerException("kind == null");
    synchronized (internal) {
      internal.kind(kind);
    }
    return this;
  }

  @Override public Span annotate(String value) {
    return annotate(internal.epochMicros(), value);
  }

  @Override public Span annotate(long timestamp, String value) {
    if (value == null) throw new NullPointerException("value == null");
    synchronized (internal) {
      internal.annotate(timestamp, value);
    }
    return this;
  }

  @Override public Span tag(String key, String value) {
    checkKeyAndValue(key, value);
    synchronized (internal) {
      internal.tag(key, value);
    }
    return this;
  }

  @Override public Span remoteEndpoint(Endpoint remoteEndpoint) {
    synchronized (internal) {
      internal.remoteEndpoint(remoteEndpoint);
    }
    return this;
  }

  @Override public void finish() {
    finish(internal.epochMicros());
  }

  @Override public void finish(long duration) {
    synchronized (internal) {
      internal.finish(null, duration);
    }
  }

  static void checkKeyAndValue(String key, String value) {
    if (key == null) throw new NullPointerException("key == null");
    if (key.isEmpty()) throw new IllegalArgumentException("key is empty");
    if (value == null) throw new NullPointerException("value == null");
  }

  @Override
  public String toString() {
    return "RealSpan(" + context() + ")";
  }
}
