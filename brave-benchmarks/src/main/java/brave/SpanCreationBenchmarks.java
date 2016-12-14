package brave;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.LocalTracer;
import com.twitter.zipkin.gen.Endpoint;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import zipkin.Constants;
import zipkin.TraceKeys;
import zipkin.reporter.Reporter;

import static brave.Span.Kind.CLIENT;

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Group)
public class SpanCreationBenchmarks {

  Tracer tracer;
  Brave brave;

  @Setup(Level.Trial)
  public void setup() {
    // real everything except reporting
    tracer = Tracer.builder()
        .reporter(Reporter.NOOP)
        .build();
    brave = new Brave.Builder()
        .reporter(Reporter.NOOP)
        .build();
  }

  @Benchmark @Group("no_contention") @GroupThreads(1)
  public LocalTracer no_contention_simpleRootSpan_brave3() {
    return simpleRootSpan_brave3();
  }

  @Benchmark @Group("mild_contention") @GroupThreads(2)
  public LocalTracer mild_contention_simpleRootSpan_brave3() {
    return simpleRootSpan_brave3();
  }

  @Benchmark @Group("high_contention") @GroupThreads(8)
  public LocalTracer high_contention_simpleRootSpan_brave3() {
    return simpleRootSpan_brave3();
  }

  @Benchmark @Group("no_contention") @GroupThreads(1)
  public ClientTracer no_contention_elaborateSpan_brave3() {
    return elaborateSpan_brave3();
  }

  @Benchmark @Group("mild_contention") @GroupThreads(2)
  public ClientTracer mild_contention_elaborateSpan_brave3() {
    return elaborateSpan_brave3();
  }

  @Benchmark @Group("high_contention") @GroupThreads(8)
  public ClientTracer high_contention_elaborateSpan_brave3() {
    return elaborateSpan_brave3();
  }

  @Benchmark @Group("no_contention") @GroupThreads(1)
  public Span no_contention_simpleRootSpan_brave4() {
    return simpleRootSpan_brave4();
  }

  @Benchmark @Group("mild_contention") @GroupThreads(2)
  public Span mild_contention_simpleRootSpan_brave4() {
    return simpleRootSpan_brave4();
  }

  @Benchmark @Group("high_contention") @GroupThreads(8)
  public Span high_contention_simpleRootSpan_brave4() {
    return simpleRootSpan_brave4();
  }

  @Benchmark @Group("no_contention") @GroupThreads(1)
  public Span no_contention_elaborateSpan_brave4() {
    return elaborateSpan_brave4();
  }

  @Benchmark @Group("mild_contention") @GroupThreads(2)
  public Span mild_contention_elaborateSpan_brave4() {
    return elaborateSpan_brave4();
  }

  @Benchmark @Group("high_contention") @GroupThreads(8)
  public Span high_contention_elaborateSpan_brave4() {
    return elaborateSpan_brave4();
  }

  // simplest usage possible
  private LocalTracer simpleRootSpan_brave3() {
    LocalTracer tracer = brave.localTracer();
    tracer.startNewSpan("codec", "encode");
    try {
      return tracer; // pretend we are doing codec work
    } finally {
      tracer.finishSpan();
    }
  }

  private ClientTracer elaborateSpan_brave3() {
    ClientTracer tracer = brave.clientTracer();

    tracer.startNewSpan("get");
    tracer.submitBinaryAnnotation("clnt/finagle.version", "6.36.0");
    tracer.submitBinaryAnnotation(TraceKeys.HTTP_PATH, "/api");

    tracer.setClientSent(Endpoint.builder() // implicit start
        .serviceName("backend")
        .ipv4(127 << 24 | 1)
        .port(8080).build());
    tracer.submitAnnotation(Constants.WIRE_SEND);
    tracer.submitAnnotation(Constants.WIRE_RECV);
    tracer.setClientReceived(); // implicit finish

    return tracer;
  }

  // simplest usage possible
  private Span simpleRootSpan_brave4() {
    try (Span span = tracer.newTrace().name("encode").start()) {
      // pretend we are doing codec work
      return span; // to satisfy the signature
    }
  }

  private Span elaborateSpan_brave4() {
    Span span = tracer.newTrace().kind(CLIENT).name("get");

    span.tag("clnt/finagle.version", "6.36.0");
    span.tag(TraceKeys.HTTP_PATH, "/api");
    span.remoteEndpoint(zipkin.Endpoint.builder()
        .serviceName("backend")
        .ipv4(127 << 24 | 1)
        .port(8080).build());

    span.start();
    span.annotate(Constants.WIRE_SEND);
    span.annotate(Constants.WIRE_RECV);
    span.finish();

    return span;
  }

  // TODO: add comparisons for joinSpan (ex server collaborates with client-originated span
}
