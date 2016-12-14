package brave;

/** Nanoseconds since the fixed time of reference. Possibly negative. */
// FunctionalInterface except Java language level 6
public interface Ticker {
  long tickNanos();
}
