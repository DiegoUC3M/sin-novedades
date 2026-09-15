package es.sinnovedades.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

/** Local technical state only. Never stores text from messages or accessibility trees. */
final class ServiceStatus {
    static final String PREFS="diagnostics";
    // Activity and service run on the main thread in the same process. A dead process
    // cannot leave a persistent "connected" flag behind.
    static boolean connected;
    static long lastInspection;
    static long whatsappEvents;
    static long blockedTaps,blockedSwipes;
    static String touch="El bloqueo de deslizamientos requiere Android 13 o posterior.";
    static String current="El servicio aún no se ha conectado.";
    private static String previousReport="";
    private static long lastWrite;

    static void checked(String status) {
        lastInspection=SystemClock.elapsedRealtime();
        current=status;
    }

    static void whatsapp(Context context,String status,String technical) {
        checked(status);
        String report=status+"\n"+technical;
        long now=SystemClock.elapsedRealtime();
        // Do not write to disk on every accessibility event or poll. Opening the
        // settings app does not overwrite the last WhatsApp observation.
        if (report.equals(previousReport) && now-lastWrite<30000) return;
        previousReport=report; lastWrite=now;
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
            .putString("summary",status).putString("report",report)
            .putLong("time",System.currentTimeMillis()).apply();
    }

    static SharedPreferences saved(Context context) {
        return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
}
