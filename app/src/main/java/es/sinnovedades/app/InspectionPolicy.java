package es.sinnovedades.app;

/** Scheduling only; no physical touch interception. */
public final class InspectionPolicy {
    private InspectionPolicy() {}
    public static final long FOREGROUND_RETRY_MS=250;
    public static long due(long now,long lastStarted,long delay,boolean urgent) {
        return urgent ? now+delay : Math.max(now+delay,lastStarted+80);
    }
}
