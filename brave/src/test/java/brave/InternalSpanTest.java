package brave;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import zipkin.BinaryAnnotation;
import zipkin.Endpoint;

import static brave.Span.Kind.CLIENT;
import static brave.Span.Kind.SERVER;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin.Constants.CLIENT_ADDR;
import static zipkin.Constants.LOCAL_COMPONENT;
import static zipkin.Constants.SERVER_ADDR;

public class InternalSpanTest {
  List<zipkin.Span> spans = new ArrayList();
  Tracer tracer = Tracer.builder().reporter(spans::add).build();
  Endpoint localEndpoint = tracer.spanFactory().localEndpoint();
  TraceContext context = tracer.nextContext(null);

  // zipkin needs one annotation or binary annotation so that the local endpoint can be read
  @Test public void addsDefaultBinaryAnnotation() {
    InternalSpan span = tracer.spanFactory().create(context);

    span.start(1L);
    span.finish(2L, null);

    assertThat(spans.get(0).binaryAnnotations.get(0)).isEqualTo(
        BinaryAnnotation.create(LOCAL_COMPONENT, "", localEndpoint)
    );
  }

  @Test public void whenKindIsClient_addsCsCr() {
    InternalSpan span = tracer.spanFactory().create(context);

    span.kind(CLIENT);
    span.start(1L);
    span.finish(2L, null);

    assertThat(spans.get(0).annotations).extracting(a -> a.value)
        .containsExactly("cs", "cr");
  }

  @Test public void whenKindIsClient_addsSa() {
    InternalSpan span = tracer.spanFactory().create(context);

    Endpoint endpoint = Endpoint.create("server", 127 | 1);
    span.kind(CLIENT);
    span.remoteEndpoint(endpoint);
    span.start(1L);
    span.finish(2L, null);

    assertThat(spans.get(0).binaryAnnotations.get(0)).isEqualTo(
        BinaryAnnotation.address(SERVER_ADDR, endpoint)
    );
  }

  @Test public void whenKindIsServer_addsSrSs() {
    InternalSpan span = tracer.spanFactory().create(context);

    span.kind(SERVER);
    span.start(1L);
    span.finish(1L, null);

    assertThat(spans.get(0).annotations).extracting(a -> a.value)
        .containsExactly("sr", "ss");
  }

  @Test public void whenKindIsServer_addsCa() {
    InternalSpan span = tracer.spanFactory().create(context);

    Endpoint endpoint = Endpoint.create("caller", 127 | 1);
    span.kind(SERVER);
    span.remoteEndpoint(endpoint);
    span.start(1L);
    span.finish(2L, null);

    assertThat(spans.get(0).binaryAnnotations.get(0)).isEqualTo(
        BinaryAnnotation.address(CLIENT_ADDR, endpoint)
    );
  }

  @Test public void relativeTimestamp_incrementsAccordingToNanoTick() {
    AtomicLong tick = new AtomicLong();
    InternalSpan span = ((RealSpan) Tracer.builder()
        .clock(() -> 0)
        .ticker(tick::getAndIncrement)
        .build().newTrace()).internal;

    tick.set(1000); // 1 microsecond

    assertThat(span.epochMicros()).isEqualTo(1);
  }
}
