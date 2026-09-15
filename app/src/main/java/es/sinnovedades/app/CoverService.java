package es.sinnovedades.app;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** An opaque button cover plus an optional Android 13+ physical-input gate. */
public final class CoverService extends AccessibilityService implements SharedPreferences.OnSharedPreferenceChangeListener {
    static final String PREFS="cover";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private WindowManager manager;
    private SharedPreferences prefs;
    private View cover;
    private int coveredColor;
    private TabDetector.Box covered;
    private boolean stopped;
    private boolean whatsappInFront;
    private boolean touchScope;
    private TabDetector.Box touchArea;
    private TabDetector.Box keyboardArea;
    private TouchGuard touchGuard;
    private TouchSnapshot touchState=TouchSnapshot.empty();
    private long nextInspection;
    private final Runnable inspect=new Runnable() {
        public void run() {
            nextInspection=0;
            try { inspectWindow(); }
            finally {
                // Retry even when there is no cover yet: the first snapshot can
                // precede WhatsApp's navigation layout or the service connection.
                if (!stopped && prefs!=null && prefs.getBoolean("enabled",true))
                    scheduleInspection(whatsappInFront ? 180 : 2000);
            }
        }
    };

    static boolean isWhatsApp(CharSequence p) {
        return "com.whatsapp".contentEquals(p==null ? "" : p) || "com.whatsapp.w4b".contentEquals(p==null ? "" : p);
    }

    @Override protected void onServiceConnected() {
        stopped=false;
        manager=(WindowManager)getSystemService(WINDOW_SERVICE);
        prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
        prefs.registerOnSharedPreferenceChangeListener(this);
        if (Build.VERSION.SDK_INT>=33) {
            // Returning from a chat can reuse the same window ID. Cached nodes
            // must not tell the input gate that the old chat is still visible.
            setCacheEnabled(false);
            try { touchGuard=new TouchGuard(this); }
            catch (RuntimeException e) { ServiceStatus.touch="No se pudo iniciar la protección táctil: "+e.getClass().getSimpleName(); }
        }
        ServiceStatus.connected=true;
        ServiceStatus.checked("Servicio conectado. Esperando a WhatsApp.");
        scheduleInspection(0);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (stopped || event==null || prefs==null) return;
        CharSequence pkg=event.getPackageName();
        boolean windowChange=event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            || event.getEventType()==AccessibilityEvent.TYPE_WINDOWS_CHANGED;
        if (isWhatsApp(pkg)) {
            ServiceStatus.whatsappEvents++;
            scheduleInspection(0);
        } else if (windowChange || pkg==null) {
            // Recheck focus on any window change, including our own overlay.
            // Other applications' event text is never read.
            scheduleInspection(0);
            if (pkg!=null && !getPackageName().contentEquals(pkg)
                && event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
                removeCover();
        }
    }

    private void scheduleInspection(long delay) {
        if (stopped) return;
        long due=SystemClock.uptimeMillis()+delay;
        // Coalesce events without moving an already pending check further away.
        // Continuous content events must not postpone detection indefinitely.
        if (nextInspection!=0 && nextInspection<=due) return;
        handler.removeCallbacks(inspect);
        nextInspection=due;
        handler.postAtTime(inspect,due);
    }

    private void inspectWindow() {
        whatsappInFront=false;
        touchState=TouchSnapshot.empty();
        if (stopped || prefs==null) { removeCover(); return; }
        if (!prefs.getBoolean("enabled",true)) { touchScope=false; updateTouchMode(); inactive("Protección en pausa."); return; }
        PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);
        KeyguardManager keyguard=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        if (!power.isInteractive() || keyguard.isKeyguardLocked()) { touchScope=false; updateTouchMode(); inactive("Pantalla apagada o bloqueada."); return; }
        AccessibilityNodeInfo root=null;
        String windowDetails="";
        try {
            // A touch on our overlay must not replace the focused WhatsApp window.
            // Only the focused application's root is obtained; other app trees are not scanned.
            List<AccessibilityWindowInfo> windows=getWindows();
            String obstruction="";
            keyboardArea=null;
            StringBuilder metadata=new StringBuilder("Ventanas: ");
            try {
                for (AccessibilityWindowInfo w:windows) {
                    Rect bounds=new Rect(); w.getBoundsInScreen(bounds);
                    metadata.append(w.getType()).append(w.isFocused()?"F":"").append(w.isActive()?"A":"").append(' ');
                    if (w.getType()==AccessibilityWindowInfo.TYPE_INPUT_METHOD && !bounds.isEmpty()) {
                        obstruction="Teclado visible: cubierta retirada.";
                        keyboardArea=new TabDetector.Box(bounds.left,bounds.top,bounds.right,bounds.bottom);
                    }
                    if (w.getType()==AccessibilityWindowInfo.TYPE_SYSTEM && w.isFocused() && !bounds.isEmpty())
                        obstruction="Una ventana del sistema tiene el foco.";
                    if (w.getType()==AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY && (w.isFocused() || w.isActive())
                        && !isOwnOverlay(w))
                        obstruction="Otra cubierta de accesibilidad está activa.";
                    if (w.getType()==AccessibilityWindowInfo.TYPE_APPLICATION && w.isFocused()) {
                        if (Build.VERSION.SDK_INT>=30 && w.getDisplayId()!=0)
                            obstruction="Las pantallas externas no están admitidas.";
                        if (root!=null) root.recycle();
                        root=w.getRoot();
                    }
                }
            } finally { for (AccessibilityWindowInfo w:windows) w.recycle(); }
            windowDetails=metadata.toString();
            if (root==null) root=getRootInActiveWindow();
            if (root==null) { inactive("Android no ha entregado una ventana accesible."); return; }
            if (!isWhatsApp(root.getPackageName())) { touchScope=false; inactive("Esperando a que abras WhatsApp."); return; }
            whatsappInFront=true;
            touchScope=true;
            touchArea=box(root);
            if (!obstruction.isEmpty()) {
                removeCover(); ServiceStatus.whatsapp(this,obstruction,windowDetails); return;
            }
            TabDetector.Box screen=box(root);
            float density=getResources().getDisplayMetrics().density;
            Scan scan=scan(root,screen,density);
            TabDetector.Box found=TabDetector.detect(scan.tabs,screen,density,scan.editor);
            touchState=new TouchSnapshot(found!=null,found,screen,root.getWindowId(),String.valueOf(root.getPackageName()));
            // Record this inspection's readiness, not the previous window's state.
            updateTouchMode();
            String details="Ventana: "+screen+"; densidad: "+density+"\n"+windowDetails
                +"\n"+ServiceStatus.touch
                +"\nNodos: "+scan.visited+"; límite: "+scan.limited+"; editor: "+scan.editor
                +"\nEtiquetas: "+scan.labels+"; botones candidatos: "+scan.tabs.size()+"\n"+scan.report;
            if (found==null) {
                removeCover();
                ServiceStatus.whatsapp(this,scan.editor ? "Editor de chat visible: cubierta retirada."
                    : scan.labels==0 ? "WhatsApp detectado; no se ven etiquetas de la barra inferior."
                    : "WhatsApp detectado; la barra inferior no se ha reconocido.",details);
                return;
            }
            showCover(found);
            ServiceStatus.whatsapp(this,"Cubierta colocada sobre Novedades.",details+"\nCubierta: "+found,true);
        } catch (RuntimeException failure) {
            removeCover();
            String status="No se pudo comprobar o cubrir la ventana: "+failure.getClass().getSimpleName();
            if (whatsappInFront) ServiceStatus.whatsapp(this,status,windowDetails);
            else ServiceStatus.checked(status);
        } finally {
            if (root!=null) root.recycle();
            updateTouchMode();
        }
    }

    private void updateTouchMode() {
        if (touchGuard!=null) touchGuard.setWanted(!stopped && touchScope && prefs!=null
            && prefs.getBoolean("enabled",true) && prefs.getBoolean("gestures",true));
    }

    static final class TouchSnapshot {
        final boolean navigation;
        final TabDetector.Box target,screen;
        final int windowId;
        final String pkg;
        TouchSnapshot(boolean nav,TabDetector.Box target,TabDetector.Box screen,int windowId,String pkg) {
            navigation=nav; this.target=target; this.screen=screen; this.windowId=windowId; this.pkg=pkg;
        }
        static TouchSnapshot empty() { return new TouchSnapshot(false,null,null,-1,""); }
        boolean samePage(TouchSnapshot other) {
            return navigation && other.navigation && windowId==other.windowId && pkg.equals(other.pkg)
                && screen.same(other.screen) && target.same(other.target);
        }
    }

    TouchSnapshot touchSnapshot() {
        inspectWindow();
        return touchState;
    }

    TabDetector.Box touchArea() { return touchArea; }
    TabDetector.Box keyboardArea() { return keyboardArea; }

    private static final class Scan {
        final List<TabDetector.Tab> tabs=new ArrayList<>();
        final StringBuilder report=new StringBuilder();
        int visited,labels;
        boolean editor,limited;
    }

    private Scan scan(AccessibilityNodeInfo root,TabDetector.Box screen,float d) {
        ArrayDeque<AccessibilityNodeInfo> queue=new ArrayDeque<>();
        queue.add(AccessibilityNodeInfo.obtain(root));
        Scan result=new Scan();
        try {
            while (!queue.isEmpty() && result.visited<2600) {
                AccessibilityNodeInfo node=queue.removeFirst();
                result.visited++;
                try {
                    // An unimportant or invisible wrapper can still expose visible
                    // descendants. Traverse children before filtering this node.
                    for (int i=0;i<node.getChildCount();i++) {
                        AccessibilityNodeInfo child=node.getChild(i);
                        if (child!=null) {
                            // Visit the footer before long chat/community lists.
                            if (box(child).bottom>=screen.bottom-260*d) queue.addFirst(child);
                            else queue.addLast(child);
                        }
                    }
                    if (!node.isVisibleToUser()) continue;
                    TabDetector.Box b=box(node);
                    if (node.isEditable() && b.bottom>screen.bottom-240*d) result.editor=true;
                    // Only inspect small labels in the navigation band. Raw text
                    // and descriptions are never retained in the diagnostic report.
                    if (b.top>=screen.bottom-260*d && b.width()>0 && b.height()>0
                        && b.width()<screen.width()*0.49 && b.height()<150*d) {
                        String kind=TabDetector.label(node.getText());
                        if (kind.isEmpty()) kind=TabDetector.label(node.getContentDescription());
                        if (!kind.isEmpty()) {
                            result.labels++;
                            TabDetector.Tab candidate=candidate(node,kind,screen,d);
                            if (candidate!=null) result.tabs.add(candidate);
                            if (result.labels<=16) {
                                result.report.append(kind).append(" etiqueta=").append(b);
                                if (candidate==null) result.report.append(" sin botón pulsable o semántico");
                                else result.report.append(" botón=").append(candidate.hit).append(" barra=")
                                    .append(candidate.bar).append(" semántica=").append(candidate.tabHint);
                                result.report.append('\n');
                            }
                        }
                    }
                } finally { node.recycle(); }
            }
            result.limited=!queue.isEmpty();
        } finally { while (!queue.isEmpty()) queue.removeFirst().recycle(); }
        return result;
    }

    private TabDetector.Tab candidate(AccessibilityNodeInfo node,String kind,TabDetector.Box screen,float d) {
        AccessibilityNodeInfo current=AccessibilityNodeInfo.obtain(node);
        TabDetector.Box hit=null,semanticHit=null,bar=null;
        boolean hint=false;
        try {
            for (int depth=0;current!=null && depth<16;depth++) {
                TabDetector.Box b=box(current);
                if (b.width()<=0 || b.height()<=0) {
                    AccessibilityNodeInfo parent=current.getParent();
                    current.recycle(); current=parent; continue;
                }
                if (b.height()>180*d || b.top<screen.bottom-260*d) break;
                String id=current.getViewIdResourceName();
                String cls=String.valueOf(current.getClassName()).toLowerCase(Locale.ROOT);
                boolean ownHint=cls.contains("tab");
                if (id!=null) {
                    id=id.toLowerCase(Locale.ROOT);
                    ownHint|=id.contains("menuitem") || id.contains("bottom_nav") || id.contains("navigation_bar")
                        || id.contains("tab_") || id.endsWith("/tabs") || id.contains("navigationitem");
                }
                hint|=ownHint;
                boolean actionable=current.isClickable()
                    || (current.getActions() & (AccessibilityNodeInfo.ACTION_CLICK|AccessibilityNodeInfo.ACTION_SELECT))!=0;
                if (b.width()<screen.width()*0.49 && b.height()<=130*d) {
                    if (actionable) hit=b;
                    // A labelled text or icon child may occupy only part of the
                    // touch target. Use semantic fallback only for a tab container.
                    if (ownHint && !cls.contains("text") && !cls.contains("image")
                        && b.height()>=36*d && b.width()>=40*d) semanticHit=b;
                }
                if (bar==null && b.width()>=screen.width()*0.7 && b.height()<=150*d && b.height()>=28*d) bar=b;
                AccessibilityNodeInfo parent=current.getParent();
                current.recycle(); current=parent;
            }
        } finally { if (current!=null) current.recycle(); }
        if (hit==null) hit=semanticHit;
        return hit==null ? null : new TabDetector.Tab(kind,hit,bar,hint);
    }

    private static TabDetector.Box box(AccessibilityNodeInfo node) {
        Rect r=new Rect(); node.getBoundsInScreen(r);
        return new TabDetector.Box(r.left,r.top,r.right,r.bottom);
    }

    private boolean isOwnOverlay(AccessibilityWindowInfo window) {
        if (cover==null) return false;
        AccessibilityNodeInfo node=cover.createAccessibilityNodeInfo();
        if (node==null) return false;
        try { return window.getId()==node.getWindowId(); }
        finally { node.recycle(); }
    }

    private int color() {
        String mode=prefs.getString("color","auto");
        if (mode.equals("light")) return Color.WHITE;
        if (mode.equals("dark")) return Color.rgb(11,16,20);
        if (mode.startsWith("#")) {
            try { return Color.parseColor(mode)|0xff000000; } catch (IllegalArgumentException ignored) {}
        }
        boolean dark=(getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        return dark ? Color.rgb(11,16,20) : Color.WHITE;
    }

    private void showCover(TabDetector.Box b) {
        boolean first=cover==null;
        if (first) {
            cover=new View(this);
            cover.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            cover.setFilterTouchesWhenObscured(false);
            cover.setOnTouchListener((v,e)->true);
        }
        int shade=color();
        if (first || shade!=coveredColor) {
            cover.setBackgroundColor(shade);
            coveredColor=shade;
        }
        if (first || covered==null || !covered.same(b)) {
            WindowManager.LayoutParams lp=new WindowManager.LayoutParams(b.width(),b.height(),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE);
            lp.gravity=Gravity.TOP|Gravity.LEFT;
            lp.x=b.left; lp.y=b.top;
            lp.setTitle("Sin Novedades: cubierta del botón");
            if (Build.VERSION.SDK_INT>=30) {
                lp.setFitInsetsTypes(0);
                lp.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            }
            if (first) manager.addView(cover,lp); else manager.updateViewLayout(cover,lp);
            covered=b;
        }
    }

    private void removeCover() {
        if (cover!=null && manager!=null) {
            try { manager.removeViewImmediate(cover); } catch (RuntimeException ignored) {}
        }
        cover=null; covered=null;
    }

    private void inactive(String status) { removeCover(); ServiceStatus.checked(status); }

    @Override public void onSharedPreferenceChanged(SharedPreferences p,String key) {
        if ("enabled".equals(key) || "color".equals(key) || "gestures".equals(key)) scheduleInspection(0);
    }
    @Override public void onConfigurationChanged(Configuration c) { super.onConfigurationChanged(c); removeCover(); scheduleInspection(120); }
    @Override public void onInterrupt() { inactive("Android ha interrumpido el servicio."); }
    @Override public boolean onUnbind(Intent intent) { shutdown(); return super.onUnbind(intent); }
    @Override public void onDestroy() { shutdown(); super.onDestroy(); }
    private void shutdown() {
        stopped=true; handler.removeCallbacksAndMessages(null); removeCover();
        if (touchGuard!=null) { touchGuard.close(); touchGuard=null; }
        nextInspection=0;
        ServiceStatus.connected=false;
        ServiceStatus.checked("El servicio se ha desconectado.");
        if (prefs!=null) prefs.unregisterOnSharedPreferenceChangeListener(this);
    }
}
