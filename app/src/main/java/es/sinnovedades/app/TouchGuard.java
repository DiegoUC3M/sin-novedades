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

    TouchGuard(CoverService service) {
        this.service=service;
        policy=new TouchPolicy(ViewConfiguration.get(service).getScaledTouchSlop());
        controller=service.getTouchInteractionController(Display.DEFAULT_DISPLAY);
    }

    void setWanted(boolean value) {
        if (closed) return;
        wanted=value;
        try {
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
            // Android can notify us of DOWN even after a passthrough region has
            // already delegated it. Never request a second state transition or
            // replay that touch; the native stream is already reaching its target.
            int state=controller.getState();
            if (state==TouchInteractionController.STATE_DELEGATING
                    || state==TouchInteractionController.STATE_TOUCH_EXPLORING) return;
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
                physicalDown=false;
                if (!delegationRequested && !replaying && initial!=null) {
                    CoverService.TouchSnapshot current=service.touchSnapshot();
                    TouchPolicy.Decision result=policy.up(event.getX(),event.getY(),current.target);
                    if (result==TouchPolicy.Decision.TAP && wanted && initial.samePage(current)) {
                        long held=Math.max(1,event.getEventTime()-downTime);
                        replayTap(downX,downY,held>=ViewConfiguration.getLongPressTimeout());
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
        controller.requestDelegating();
    }

    private void replayTap(float x,float y,boolean longPress) {
        // GestureDescription rounds its samples to integer pixels. Quantize the
        // path to the original pixel so rounding cannot miss the one-pixel
        // passthrough region (or cross a neighbouring tab's integer boundary).
        x=(float)Math.floor(x); y=(float)Math.floor(y);
        // Only the exact touch the user just made is replayed. The temporary
        // passthrough is one pixel at an allowed position, never the whole display.
        replaying=true;
        Region pass=new Region(basePassthrough);
        pass.op(new Region((int)x,(int)y,(int)x+1,(int)y+1),Region.Op.UNION);
        service.setTouchExplorationPassthroughRegion(Display.DEFAULT_DISPLAY,pass);
        Path path=new Path(); path.moveTo(x,y);
        long duration=longPress ? ViewConfiguration.getLongPressTimeout()+80L : 1L;
        GestureDescription gesture=new GestureDescription.Builder().addStroke(
            new GestureDescription.StrokeDescription(path,0,duration)).build();
        AccessibilityService.GestureResultCallback callback=new AccessibilityService.GestureResultCallback() {
            @Override public void onCompleted(GestureDescription g) { finishReplay(); }
            @Override public void onCancelled(GestureDescription g) { finishReplay(); }
        };
        handler.postDelayed(this::finishReplay,duration+1000);
        if (!service.dispatchGesture(gesture,callback,handler)) finishReplay();
    }

    private void finishReplay() {
        if (!replaying) return;
        replaying=false;
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
            if (!replaying) {
                try { applyMode(); } catch (RuntimeException failure) { failed(failure); }
            }
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
