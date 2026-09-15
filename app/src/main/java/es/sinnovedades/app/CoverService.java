package es.sinnovedades.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
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

/** Observes navigation and covers only the Updates button and selected page. */
public final class CoverService extends AccessibilityService implements SharedPreferences.OnSharedPreferenceChangeListener {
    static final String PREFS="cover";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private WindowManager manager;
    private SharedPreferences prefs;
    private View cover,curtain;
    private int coveredColor;
    private TabDetector.Box covered;
    private TabDetector.Box veiled;
    private boolean stopped;
    private boolean whatsappInFront;
    private long nextInspection,lastStarted,nextCacheRefresh;
    private final Runnable inspect=new Runnable() {
        public void run() {
            nextInspection=0;
            lastStarted=SystemClock.uptimeMillis();
            try { inspectWindow(); }
            finally {
                // Retry even when there is no cover yet: the first snapshot can
                // precede WhatsApp's navigation layout or the service connection.
                if (!stopped && prefs!=null && prefs.getBoolean("enabled",true))
                    scheduleInspection(whatsappInFront ? 1000 : 3000);
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
        prefs.edit().remove("gestures").apply();
        prefs.registerOnSharedPreferenceChangeListener(this);
        // Replace dynamic flags too: an APK upgrade may retain the old service
        // configuration. This version never requests or replays physical input.
        AccessibilityServiceInfo info=getServiceInfo();
        if (info!=null) {
            info.flags=AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
            setServiceInfo(info);
        }
        if (Build.VERSION.SDK_INT>=33) setCacheEnabled(true);
        ServiceStatus.prepare(this);
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
                removeOverlays();
        }
    }

    private void scheduleInspection(long delay) {
        if (stopped) return;
        long due=Math.max(SystemClock.uptimeMillis()+delay,lastStarted+80);
        // Coalesce events without moving an already pending check further away.
        // Continuous content events must not postpone detection indefinitely.
        if (nextInspection!=0 && nextInspection<=due) return;
        handler.removeCallbacks(inspect);
        nextInspection=due;
        handler.postAtTime(inspect,due);
    }

    private void inspectWindow() {
        whatsappInFront=false;
        if (stopped || prefs==null) { removeOverlays(); return; }
        if (!prefs.getBoolean("enabled",true)) { inactive("Protección en pausa."); return; }
        PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);
        KeyguardManager keyguard=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        if (!power.isInteractive() || keyguard.isKeyguardLocked()) { inactive("Pantalla apagada o bloqueada."); return; }
        AccessibilityNodeInfo root=null;
        String windowDetails="";
        try {
            // Events invalidate the cache normally. A periodic refresh also
            // catches a reused window or a tab that emits no selection event.
            long now=SystemClock.uptimeMillis();
            if (Build.VERSION.SDK_INT>=33 && now>=nextCacheRefresh) {
                clearCache(); nextCacheRefresh=now+1000;
            }
            // A touch on our overlay must not replace the focused WhatsApp window.
            // Only the focused application's root is obtained; other app trees are not scanned.
            List<AccessibilityWindowInfo> windows=getWindows();
            String obstruction="";
            StringBuilder metadata=new StringBuilder("Ventanas: ");
            try {
                for (AccessibilityWindowInfo w:windows) {
                    Rect bounds=new Rect(); w.getBoundsInScreen(bounds);
                    metadata.append(w.getType()).append(w.isFocused()?"F":"").append(w.isActive()?"A":"").append(' ');
                    if (w.getType()==AccessibilityWindowInfo.TYPE_INPUT_METHOD && !bounds.isEmpty()) {
                        obstruction="Teclado visible: cubierta retirada.";
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
            if (!isWhatsApp(root.getPackageName())) { inactive("Esperando a que abras WhatsApp."); return; }
            whatsappInFront=true;
            if (!obstruction.isEmpty()) {
                removeOverlays(); ServiceStatus.whatsapp(this,obstruction,windowDetails); return;
            }
            TabDetector.Box screen=box(root);
            float density=getResources().getDisplayMetrics().density;
            Scan scan=scan(root,screen,density);
            TabDetector.Navigation nav=TabDetector.navigation(scan.tabs,screen,density,scan.editor);
            String details="Ventana: "+screen+"; densidad: "+density+"\n"+windowDetails
                +"\nNodos: "+scan.visited+"; límite: "+scan.limited+"; editor: "+scan.editor
                +"\nEtiquetas: "+scan.labels+"; botones candidatos: "+scan.tabs.size()+"\n"+scan.report;
            if (nav==null) {
                removeOverlays();
                ServiceStatus.whatsapp(this,scan.editor ? "Editor de chat visible: cubierta retirada."
                    : scan.labels==0 ? "WhatsApp detectado; no se ven etiquetas de la barra inferior."
                    : "WhatsApp detectado; la barra inferior no se ha reconocido.",details);
                return;
            }
            if (nav.updatesOpen()) showCurtain(nav.content); else removeCurtain();
            showCover(nav.target);
            String status=nav.updatesOpen() ? "Novedades oculta con pantalla negra. Pulsa otra pestaña para salir."
                : nav.selected.isEmpty() ? "Botón cubierto; WhatsApp no indica qué pestaña está seleccionada."
                : "Botón Novedades cubierto. Gestos normales en WhatsApp.";
            ServiceStatus.whatsapp(this,status,details+"\nSelección: "+(nav.selected.isEmpty()?"desconocida":nav.selected)
                +"\nCubierta: "+nav.target+"; pantalla negra: "+(veiled==null?"no":veiled.toString()),true);
        } catch (RuntimeException failure) {
            removeOverlays();
            String status="No se pudo comprobar o cubrir la ventana: "+failure.getClass().getSimpleName();
            if (whatsappInFront) ServiceStatus.whatsapp(this,status,windowDetails);
            else ServiceStatus.checked(status);
        } finally {
            if (root!=null) root.recycle();
        }
    }

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
                    if (node.isEditable() && b.bottom>screen.bottom-240*d) { result.editor=true; break; }
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
                                    .append(candidate.bar).append(" semántica=").append(candidate.tabHint)
                                    .append(" seleccionada=").append(candidate.selected);
                                result.report.append('\n');
                            }
                        }
                    }
                } finally { node.recycle(); }
            }
            result.limited=!queue.isEmpty() && !result.editor;
        } finally { while (!queue.isEmpty()) queue.removeFirst().recycle(); }
        return result;
    }

    private TabDetector.Tab candidate(AccessibilityNodeInfo node,String kind,TabDetector.Box screen,float d) {
        AccessibilityNodeInfo current=AccessibilityNodeInfo.obtain(node);
        TabDetector.Box hit=null,semanticHit=null,bar=null;
        boolean hint=false,selected=false;
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
                    // A selected label/icon can live inside an unselected clickable
                    // tab. Do not inherit selection from the whole navigation bar.
                    AccessibilityNodeInfo.CollectionItemInfo item=current.getCollectionItemInfo();
                    selected|=current.isSelected() || current.isChecked() || (item!=null && item.isSelected())
                        || TabDetector.selectedDescription(current.getContentDescription());
                    if (Build.VERSION.SDK_INT>=30)
                        selected|=TabDetector.selectedDescription(current.getStateDescription());
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
        return hit==null ? null : new TabDetector.Tab(kind,hit,bar,hint,selected);
    }

    private static TabDetector.Box box(AccessibilityNodeInfo node) {
        Rect r=new Rect(); node.getBoundsInScreen(r);
        return new TabDetector.Box(r.left,r.top,r.right,r.bottom);
    }

    private boolean isOwnOverlay(AccessibilityWindowInfo window) {
        return ownsWindow(cover,window) || ownsWindow(curtain,window);
    }

    private boolean ownsWindow(View view,AccessibilityWindowInfo window) {
        if (view==null) return false;
        AccessibilityNodeInfo node=view.createAccessibilityNodeInfo();
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
            WindowManager.LayoutParams lp=layout(b,"Sin Novedades: cubierta del botón");
            if (first) manager.addView(cover,lp); else manager.updateViewLayout(cover,lp);
            covered=b;
        }
    }

    private void showCurtain(TabDetector.Box b) {
        boolean first=curtain==null;
        if (first) {
            curtain=new View(this);
            curtain.setBackgroundColor(Color.BLACK);
            curtain.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            // This ordinary window consumes touches only on the hidden content.
            // The footer and system navigation keep their native input path.
            curtain.setOnTouchListener((v,e)->true);
        }
        if (first || veiled==null || !veiled.same(b)) {
            WindowManager.LayoutParams lp=layout(b,"Sin Novedades: pantalla negra");
            if (first) { manager.addView(curtain,lp); ServiceStatus.curtainsShown++; }
            else manager.updateViewLayout(curtain,lp);
            veiled=b;
        }
    }

    private WindowManager.LayoutParams layout(TabDetector.Box b,String title) {
        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(b.width(),b.height(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.OPAQUE);
        lp.gravity=Gravity.TOP|Gravity.LEFT;
        lp.x=b.left; lp.y=b.top; lp.setTitle(title);
        if (Build.VERSION.SDK_INT>=30) {
            lp.setFitInsetsTypes(0);
            lp.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        }
        return lp;
    }

    private void removeCover() {
        if (cover!=null && manager!=null) {
            try { manager.removeViewImmediate(cover); } catch (RuntimeException ignored) {}
        }
        cover=null; covered=null;
    }

    private void removeCurtain() {
        if (curtain!=null && manager!=null) {
            try { manager.removeViewImmediate(curtain); } catch (RuntimeException ignored) {}
        }
        curtain=null; veiled=null;
    }

    private void removeOverlays() { removeCurtain(); removeCover(); }

    private void inactive(String status) { removeOverlays(); ServiceStatus.checked(status); }

    @Override public void onSharedPreferenceChanged(SharedPreferences p,String key) {
        if ("enabled".equals(key) || "color".equals(key)) scheduleInspection(0);
    }
    @Override public void onConfigurationChanged(Configuration c) { super.onConfigurationChanged(c); removeOverlays(); nextCacheRefresh=0; scheduleInspection(120); }
    @Override public void onInterrupt() { inactive("Android ha interrumpido el servicio."); }
    @Override public boolean onUnbind(Intent intent) { shutdown(); return super.onUnbind(intent); }
    @Override public void onDestroy() { shutdown(); super.onDestroy(); }
    private void shutdown() {
        stopped=true; handler.removeCallbacksAndMessages(null); removeOverlays();
        nextInspection=0;
        ServiceStatus.connected=false;
        ServiceStatus.checked("El servicio se ha desconectado.");
        if (prefs!=null) prefs.unregisterOnSharedPreferenceChangeListener(this);
    }
}
