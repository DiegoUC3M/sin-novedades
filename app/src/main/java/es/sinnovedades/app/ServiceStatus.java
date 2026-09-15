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
    static long blockedTaps,blockedSwipes,touchEvents;
    static long directTaps,replayTaps,completedReplays,cancelledTaps;
    static boolean touchReconnect;
    static String touch="El bloqueo de deslizamientos requiere Android 13 o posterior.";
    static String touchDetails="Todavía no se han comprobado las capacidades táctiles.";
    static String current="El servicio aún no se ha conectado.";
    private static String previousReport="";
    private static long lastWrite;
    private static String previousTouch="";
    private static String previousNavigation="";
    private static long lastNavigationWrite;

    static void tap(Context context,String result) {
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
            .putString("tap_result",result).putLong("tap_time",System.currentTimeMillis()).apply();
    }

    static void touch(Context context,String status,boolean inWhatsApp) {
        touch=status;
        if (!inWhatsApp) return;
        String report=status+"\n"+touchDetails;
        if (report.equals(previousTouch)) return;
        previousTouch=report;
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
            .putString("touch_report",report).putString("touch_summary",status)
            .putLong("touch_time",System.currentTimeMillis()).apply();
    }

    static void checked(String status) {
        lastInspection=SystemClock.elapsedRealtime();
        current=status;
    }

    static void whatsapp(Context context,String status,String technical) {
        whatsapp(context,status,technical,false);
    }

    static void whatsapp(Context context,String status,String technical,boolean navigation) {
        checked(status);
        String report=status+"\n"+technical;
        long now=SystemClock.elapsedRealtime();
        SharedPreferences.Editor save=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit();
        if (navigation && (!report.equals(previousNavigation) || now-lastNavigationWrite>=30000)) {
            previousNavigation=report; lastNavigationWrite=now;
            save.putString("navigation_report",report).putLong("navigation_time",System.currentTimeMillis());
        }
        // Do not write to disk on every accessibility event or poll. Opening the
        // settings app does not overwrite the last WhatsApp observation.
        if (report.equals(previousReport) && now-lastWrite<30000) { save.apply(); return; }
        previousReport=report; lastWrite=now;
        save
            .putString("summary",status).putString("report",report)
            .putLong("time",System.currentTimeMillis()).apply();
    }

    static SharedPreferences saved(Context context) {
        return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
}
