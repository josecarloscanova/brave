package brave.internal;

import brave.Clock;
import brave.Ticker;
import brave.Tracer;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Enumeration;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jvnet.animal_sniffer.IgnoreJRERequirement;
import zipkin.Endpoint;
import zipkin.reporter.Reporter;

/**
 * Access to platform-specific features and implements a default logging reporter.
 *
 * <p>Originally designed by OkHttp team, derived from {@code okhttp3.internal.platform.Platform}
 */
public abstract class Platform implements Clock, Ticker, Reporter<zipkin.Span> {
  static final Logger logger = Logger.getLogger(Tracer.class.getName());

  private static final Platform PLATFORM = findPlatform();

  @Override public void report(zipkin.Span span) {
    if (!logger.isLoggable(Level.INFO)) return;
    if (span == null) throw new NullPointerException("span == null");
    logger.info(span.toString());
  }

  @Memoized
  public Endpoint localEndpoint() {
    Endpoint.Builder builder = Endpoint.builder().serviceName("unknown");
    try {
      Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
      while (nics.hasMoreElements()) {
        NetworkInterface nic = nics.nextElement();
        Enumeration<InetAddress> addresses = nic.getInetAddresses();
        while (addresses.hasMoreElements()) {
          InetAddress address = addresses.nextElement();
          if (address.isSiteLocalAddress()) {
            byte[] addressBytes = address.getAddress();
            if (addressBytes.length == 4) {
              builder.ipv4(ByteBuffer.wrap(addressBytes).getInt());
            } else if (addressBytes.length == 16) {
              builder.ipv6(addressBytes);
            }
            break;
          }
        }
      }
    } catch (Exception ignored) {
    }
    return builder.build();
  }

  public static Platform get() {
    return PLATFORM;
  }

  /** Attempt to match the host runtime to a capable Platform implementation. */
  private static Platform findPlatform() {

    Platform jre7 = Jre7.buildIfSupported();

    if (jre7 != null) return jre7;

    // compatible with JRE 6
    return Jre6.build();
  }

  /**
   * This class uses pseudo-random number generators to provision IDs.
   *
   * <p>This optimizes speed over full coverage of 64-bits, which is why it doesn't share a {@link
   * SecureRandom}. It will use {@link java.util.concurrent.ThreadLocalRandom} unless used in JRE 6
   * which doesn't have the class.
   */
  public abstract long randomLong();

  @Override public long tickNanos() {
    return System.nanoTime();
  }

  @Override public long epochMicros() {
    return System.currentTimeMillis() * 1000;
  }

  @AutoValue
  static abstract class Jre7 extends Platform {

    static Jre7 buildIfSupported() {
      // Find JRE 7 new methods
      try {
        Class.forName("java.util.concurrent.ThreadLocalRandom");
        return new AutoValue_Platform_Jre7();
      } catch (ClassNotFoundException e) {
        // pre JRE 7
      }
      return null;
    }

    @IgnoreJRERequirement
    @Override public long randomLong() {
      return java.util.concurrent.ThreadLocalRandom.current().nextLong();
    }
  }

  @AutoValue
  static abstract class Jre6 extends Platform {
    abstract Random prng();

    static Jre6 build() {
      return new AutoValue_Platform_Jre6(new Random(System.nanoTime()));
    }

    @Override public long randomLong() {
      return prng().nextLong();
    }
  }

  Platform() {
  }
}
