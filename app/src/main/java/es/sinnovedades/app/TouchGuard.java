package es.sinnovedades.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.accessibilityservice.TouchInteractionController;
import android.content.ComponentName;
import android.content.pm.ResolveInfo;
import android.graphics.Path;
import android.graphics.Region;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.view.accessibility.AccessibilityManager;
import java.util.ArrayList;
import java.util.List;

/** Android 13+ input gate. No automatic navigation and no accessibility-focus clicks. */
final class TouchGuard implements TouchInteractionController.Callback {
    private final CoverService service;
    private final TouchInteractionController controller;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final TouchPolicy policy;
    private boolean active,wanted,closed,registered,delegationRequested,physicalDown,replaying;
    private boolean receivedInput;
    private long downTime;
    private float downX,downY;
    private CoverService.TouchSnapshot initial;
    private Region basePassthrough=new Region();
    private PendingReplay pendingReplay;
    private static final class PendingReplay {
        final float x,y;
        final boolean longPress;
        final CoverService.TouchSnapshot page;
        boolean prepared,dispatched;
        Runnable timeout;
        PendingReplay(float x,float y,boolean longPress,CoverService.TouchSnapshot page) {
            this.x=(float)Math.floor(x); this.y=(float)Math.floor(y);
            this.longPress=longPress; this.page=page;
        }
    }

    TouchGuard(CoverService service) {
        this.service=service;
        policy=new TouchPolicy(ViewConfiguration.get(service).getScaledTouchSlop());
        controller=service.getTouchInteractionController(Display.DEFAULT_DISPLAY);
    }

    void setWanted(boolean value) {
        if (closed) return;
        wanted=value;
        try {
            if (!wanted && pendingReplay!=null && !pendingReplay.dispatched)
                finishReplay(pendingReplay,false,"Pulsación cancelada al salir de WhatsApp o pausar la protección.");
            updatePassthrough();
            if (!physicalDown && !replaying) applyMode();
        } catch (RuntimeException failure) { failed(failure); }
    }

    private void updatePassthrough() {
        if (closed || replaying) return;
        WindowManager manager=(WindowManager)service.getSystemService(AccessibilityService.WINDOW_SERVICE);
        WindowMetrics metrics=manager.getMaximumWindowMetrics();
        Rect bounds=metrics.getBounds();
        Insets bars=metrics.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
        Insets gestures=metrics.getWindowInsets().getInsets(WindowInsets.Type.mandatorySystemGestures());
        Region next=new Region(bounds);
        if (wanted) {
            Rect inside=new Rect(bounds.left+Math.max(bars.left,gestures.left),bounds.top+Math.max(bars.top,gestures.top),
                bounds.right-Math.max(bars.right,gestures.right),bounds.bottom-Math.max(bars.bottom,gestures.bottom));
            TabDetector.Box app=service.touchArea();
            if (app!=null && !inside.intersect(app.left,app.top,app.right,app.bottom)) inside.setEmpty();
            next.op(inside,Region.Op.DIFFERENCE);
            TabDetector.Box keyboard=service.keyboardArea();
            if (keyboard!=null) next.op(new Rect(keyboard.left,keyboard.top,keyboard.right,keyboard.bottom),Region.Op.UNION);
        }
        if (!next.equals(basePassthrough)) {
            basePassthrough=next;
            service.setTouchExplorationPassthroughRegion(Display.DEFAULT_DISPLAY,next);
        }
    }

    private TouchAvailability.Result availability(AccessibilityServiceInfo ownInfo) {
        AccessibilityManager manager=(AccessibilityManager)service.getSystemService(AccessibilityService.ACCESSIBILITY_SERVICE);
        ComponentName own=new ComponentName(service,CoverService.class);
        List<TouchAvailability.OtherService> services=new ArrayList<>();
        StringBuilder details=new StringBuilder("Capacidades del servicio conectado: ")
            .append(ownInfo==null ? "sin conexión" : ownInfo.getCapabilities())
            .append("; necesarias para control y pulsaciones: 34")
            .append("\nFlags del servicio: ").append(ownInfo==null ? 0 : ownInfo.flags)
            .append("; exploración táctil del sistema: ").append(manager.isTouchExplorationEnabled());
        for (AccessibilityServiceInfo info:manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            // ResolveInfo also identifies our component when getId() is absent.
            ResolveInfo resolve=info.getResolveInfo();
            ComponentName component=info.getId()==null ? null : ComponentName.unflattenFromString(info.getId());
            boolean isOwn=own.equals(component);
            if (resolve!=null && resolve.serviceInfo!=null) {
                ComponentName declared=new ComponentName(resolve.serviceInfo.packageName,resolve.serviceInfo.name);
                isOwn|=own.equals(declared);
                if (component==null) component=declared;
            }
            if (isOwn || (info.flags&AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE)==0) continue;
            int target=resolve!=null && resolve.serviceInfo!=null && resolve.serviceInfo.applicationInfo!=null
                ? resolve.serviceInfo.applicationInfo.targetSdkVersion : 0;
            String id=component==null ? "servicio sin identificador" : component.flattenToShortString();
            CharSequence label=null;
            try { if (resolve!=null) label=resolve.loadLabel(service.getPackageManager()); }
            catch (RuntimeException ignored) { /* The component still identifies the service. */ }
            String name=label==null ? id : label.toString().replace('\n',' ').replace('\r',' ');
            if (name.length()>120) name=name.substring(0,120);
            TouchAvailability.OtherService other=new TouchAvailability.OtherService(false,name,info.getCapabilities(),info.flags,target);
            services.add(other);
            details.append("\nSolicitud táctil de otro servicio: ").append(name).append(" [").append(id)
                .append("]; capacidades=").append(info.getCapabilities()).append("; flags=").append(info.flags)
                .append("; target=").append(target).append("; conflicto=").append(other.conflicts());
        }
        ServiceStatus.touchDetails=details.toString();
        return TouchAvailability.check(ownInfo!=null,ownInfo==null ? 0 : ownInfo.getCapabilities(),services);
    }

    private void applyMode() {
        if (closed) return;
        AccessibilityServiceInfo info=service.getServiceInfo();
        TouchAvailability.Result readiness=availability(info);
        ServiceStatus.touchReconnect=readiness.reconnect;
        boolean next=wanted && readiness.ready;
        if (info==null) { ServiceStatus.touch=readiness.message(wanted); return; }
        if (active!=next) {
            if (next) {
                controller.registerCallback(null,this);
                registered=true;
                receivedInput=false;
                info.flags|=AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
            }
            else info.flags&=~AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
            service.setServiceInfo(info);
            active=next;
            if (active) service.setTouchExplorationPassthroughRegion(Display.DEFAULT_DISPLAY,basePassthrough);
            else if (registered) { controller.unregisterCallback(this); registered=false; }
        }
        AccessibilityManager manager=(AccessibilityManager)service.getSystemService(AccessibilityService.ACCESSIBILITY_SERVICE);
        String status=readiness.message(wanted);
        if (active && !manager.isTouchExplorationEnabled())
            status="Control táctil solicitado; Android aún no ha activado la exploración táctil.";
        else if (active && receivedInput) status="Protección táctil activa; se reciben gestos.";
        ServiceStatus.touchDetails+="\nControlador registrado: "+registered+"; control solicitado: "+active
            +"; entrada recibida en esta activación: "+receivedInput;
        ServiceStatus.touch(service,status,wanted);
    }

    @Override public void onMotionEvent(MotionEvent event) {
        if (closed) return;
        try {
            ServiceStatus.touchEvents++;
            receivedInput=true;
            if (pendingReplay!=null) {
                if (pendingReplay.dispatched) return;
                if (event.getActionMasked()==MotionEvent.ACTION_DOWN)
                    finishReplay(pendingReplay,false,"Respaldo cancelado por un nuevo toque.");
            }
            // Android can notify us of DOWN even after a passthrough region has
            // already delegated it. Never request a second state transition or
            // replay that touch; the native stream is already reaching its target.
            int state=controller.getState();
            if (state==TouchInteractionController.STATE_DELEGATING
                    || state==TouchInteractionController.STATE_TOUCH_EXPLORING) {
                if (event.getActionMasked()==MotionEvent.ACTION_DOWN) { initial=null; policy.cancel(); }
                return;
            }
            int action=event.getActionMasked();
            if (action==MotionEvent.ACTION_DOWN) {
                physicalDown=true; delegationRequested=false;
                downTime=event.getEventTime(); downX=event.getX(); downY=event.getY();
                // Always obtain a new WhatsApp snapshot, including when the last
                // event still described a chat. Never use cover!=null as input policy.
                initial=service.touchSnapshot();
                TouchPolicy.Decision result=policy.down(downX,downY,
                    initial.navigation && TouchPolicy.contains(initial.screen,downX,downY),initial.target);
                if (!active || !wanted) result=TouchPolicy.Decision.DELEGATE;
                if (result==TouchPolicy.Decision.DELEGATE) delegate();
                else if (result==TouchPolicy.Decision.BLOCK) ServiceStatus.blockedTaps++;
            } else if (action==MotionEvent.ACTION_MOVE) {
                if (delegationRequested || replaying) return;
                TouchPolicy.Decision before=policy.decision();
                for (int i=0;i<event.getHistorySize();i++) policy.move(event.getHistoricalX(i),event.getHistoricalY(i));
                TouchPolicy.Decision result=policy.move(event.getX(),event.getY());
                if (result==TouchPolicy.Decision.DELEGATE) delegate();
                else if (before!=TouchPolicy.Decision.BLOCK && result==TouchPolicy.Decision.BLOCK) ServiceStatus.blockedSwipes++;
            } else if (action==MotionEvent.ACTION_POINTER_DOWN) {
                policy.multiplePointers();
            } else if (action==MotionEvent.ACTION_UP) {
                CoverService.TouchSnapshot started=physicalDown ? initial : null;
                physicalDown=false;
                initial=null;
                if (!delegationRequested && !replaying && started!=null) {
                    CoverService.TouchSnapshot current=service.touchSnapshot();
                    TouchPolicy.Decision result=policy.up(event.getX(),event.getY(),current.target);
                    if (result==TouchPolicy.Decision.TAP && wanted && started.samePage(current)) {
                        long held=Math.max(1,event.getEventTime()-downTime);
                        boolean longPress=held>=ViewConfiguration.getLongPressTimeout();
                        UserTap.Result clicked=UserTap.perform(service,current,downX,downY,longPress);
                        if (clicked==UserTap.Result.CLICKED) {
                            ServiceStatus.directTaps++;
                            ServiceStatus.tap(service,longPress ? "Pulsación larga aceptada por el control tocado."
                                : "Pulsación simple aceptada por el control tocado.");
                        } else if (clicked==UserTap.Result.FALLBACK) replayTap(downX,downY,longPress,current);
                        else {
                            ServiceStatus.cancelledTaps++;
                            ServiceStatus.tap(service,"Pulsación cancelada: el control o la ventana han cambiado.");
                        }
                    } else if (result==TouchPolicy.Decision.TAP) {
                        ServiceStatus.cancelledTaps++;
                        ServiceStatus.tap(service,"Pulsación cancelada: la pantalla ya no coincide con la del inicio.");
                    }
                }
                if (!replaying) applyMode();
            } else if (action==MotionEvent.ACTION_CANCEL) {
                physicalDown=false; policy.cancel(); initial=null;
                if (!replaying) applyMode();
            }
        } catch (RuntimeException failure) {
            failed(failure);
        }
    }

    private void delegate() {
        if (delegationRequested) return;
        delegationRequested=true;
        initial=null;
        controller.requestDelegating();
    }

    private void replayTap(float x,float y,boolean longPress,CoverService.TouchSnapshot page) {
        PendingReplay pending=new PendingReplay(x,y,longPress,page);
        pendingReplay=pending;
        replaying=true;
        ServiceStatus.tap(service,"El control no acepta acción directa; preparando una pulsación de respaldo.");
        pending.timeout=()->finishReplay(pending,false,"Android no cerró a tiempo la interacción para preparar el respaldo.");
        handler.postDelayed(pending.timeout,2500);
        prepareReplay(pending);
    }

    private void prepareReplay(PendingReplay pending) {
        if (pendingReplay!=pending || pending.prepared || pending.dispatched || physicalDown
                || controller.getState()!=TouchInteractionController.STATE_CLEAR) return;
        // TouchExplorer can keep the physical interaction open after ACTION_UP.
        // A new DOWN in that state does not consult the passthrough region.
        if (!wanted || !pending.page.samePage(service.touchSnapshot())) {
            finishReplay(pending,false,"Respaldo cancelado: la pantalla ha cambiado."); return;
        }
        pending.prepared=true;
        Region pass=new Region(basePassthrough);
        pass.op(new Region((int)pending.x,(int)pending.y,(int)pending.x+1,(int)pending.y+1),Region.Op.UNION);
        service.setTouchExplorationPassthroughRegion(Display.DEFAULT_DISPLAY,pass);
        // Give the system's asynchronous region update a turn before injection.
        handler.postDelayed(()->dispatchReplay(pending),32);
    }

    private void dispatchReplay(PendingReplay pending) {
        if (closed || pendingReplay!=pending) return;
        try {
            handler.removeCallbacks(pending.timeout);
            if (physicalDown || controller.getState()!=TouchInteractionController.STATE_CLEAR
                    || !wanted || !pending.page.samePage(service.touchSnapshot())) {
                finishReplay(pending,false,"Respaldo cancelado antes de transmitir: ha cambiado la interacción."); return;
            }
            pending.dispatched=true;
            Path path=new Path(); path.moveTo(pending.x,pending.y);
            long duration=pending.longPress ? ViewConfiguration.getLongPressTimeout()+80L : ViewConfiguration.getTapTimeout();
            GestureDescription gesture=new GestureDescription.Builder().addStroke(
                new GestureDescription.StrokeDescription(path,0,duration)).build();
            AccessibilityService.GestureResultCallback callback=new AccessibilityService.GestureResultCallback() {
                @Override public void onCompleted(GestureDescription g) { finishReplay(pending,true,"Gesto de pulsación de respaldo completado."); }
                @Override public void onCancelled(GestureDescription g) { finishReplay(pending,false,"Android canceló el gesto de respaldo."); }
            };
            ServiceStatus.replayTaps++;
            if (!service.dispatchGesture(gesture,callback,handler)) {
                finishReplay(pending,false,"Android rechazó la solicitud del gesto de respaldo.");
            } else {
                // Waiting for the physical stream to close must not consume the
                // injection's own timeout, especially for long presses.
                pending.timeout=()->finishReplay(pending,false,"No llegó la confirmación del gesto de respaldo.");
                handler.postDelayed(pending.timeout,duration+2000);
            }
        } catch (RuntimeException failure) { failed(failure); }
    }

    private void finishReplay(PendingReplay pending,boolean completed,String result) {
        // Late callbacks from a previous gesture must never finish a newer one.
        if (pendingReplay!=pending) return;
        pendingReplay=null;
        replaying=false;
        if (completed) ServiceStatus.completedReplays++; else ServiceStatus.cancelledTaps++;
        ServiceStatus.tap(service,result);
        handler.removeCallbacksAndMessages(null);
        try {
            service.setTouchExplorationPassthroughRegion(Display.DEFAULT_DISPLAY,basePassthrough);
            updatePassthrough();
            applyMode();
        } catch (RuntimeException failure) { failed(failure); }
    }

    @Override public void onStateChanged(int state) {
        if (state==TouchInteractionController.STATE_CLEAR) {
            physicalDown=false; delegationRequested=false;
            initial=null; policy.cancel();
            try {
                if (pendingReplay!=null) prepareReplay(pendingReplay);
                else if (!replaying) applyMode();
            } catch (RuntimeException failure) { failed(failure); }
        }
    }

    private void failed(RuntimeException failure) {
        ServiceStatus.touchReconnect=true;
        ServiceStatus.touch(service,"Protección táctil interrumpida: "+failure.getClass().getSimpleName()
            +". Desactiva y vuelve a activar Sin Novedades en Accesibilidad.",wanted);
        close();
    }

    void close() {
        if (closed) return;
        closed=true; wanted=false; active=false;
        pendingReplay=null; replaying=false;
        handler.removeCallbacksAndMessages(null);
        try {
            AccessibilityServiceInfo info=service.getServiceInfo();
            if (info!=null) {
                info.flags&=~AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
                service.setServiceInfo(info);
            }
        } catch (RuntimeException ignored) { service.disableSelf(); }
        try { service.setTouchExplorationPassthroughRegion(Display.DEFAULT_DISPLAY,new Region()); }
        catch (RuntimeException ignored) {}
        if (registered) {
            try { controller.unregisterCallback(this); } catch (RuntimeException ignored) {}
            registered=false;
        }
    }
}
