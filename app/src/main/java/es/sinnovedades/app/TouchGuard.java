package es.sinnovedades.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.accessibilityservice.TouchInteractionController;
import android.content.ComponentName;
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

/** Android 13+ input gate. No automatic navigation and no accessibility-focus clicks. */
final class TouchGuard implements TouchInteractionController.Callback {
    private final CoverService service;
    private final TouchInteractionController controller;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final TouchPolicy policy;
    private boolean active,wanted,closed,registered,delegationRequested,physicalDown,replaying;
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

    private boolean anotherExplorer() {
        AccessibilityManager manager=(AccessibilityManager)service.getSystemService(AccessibilityService.ACCESSIBILITY_SERVICE);
        ComponentName own=new ComponentName(service,CoverService.class);
        for (AccessibilityServiceInfo info:manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (!own.equals(ComponentName.unflattenFromString(info.getId()))
                    && (info.flags&AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE)!=0) return true;
        }
        return false;
    }

    private void applyMode() {
        if (closed) return;
        boolean next=wanted && !anotherExplorer();
        AccessibilityServiceInfo info=service.getServiceInfo();
        if (info==null) return;
        if (next && (info.getCapabilities()&AccessibilityServiceInfo.CAPABILITY_CAN_REQUEST_TOUCH_EXPLORATION)==0) {
            ServiceStatus.touch="Falta reconectar accesibilidad para activar la protección táctil.";
            next=false;
        }
        if (active!=next) {
            if (next) {
                controller.registerCallback(null,this);
                registered=true;
                info.flags|=AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
            }
            else info.flags&=~AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
            service.setServiceInfo(info);
            active=next;
            if (active) service.setTouchExplorationPassthroughRegion(Display.DEFAULT_DISPLAY,basePassthrough);
            else if (registered) { controller.unregisterCallback(this); registered=false; }
        }
        ServiceStatus.touch=active ? "Protección táctil activa." : wanted ?
            "Protección táctil no disponible; comprueba el permiso y otros servicios de exploración táctil." : "Protección táctil en espera.";
    }

    @Override public void onMotionEvent(MotionEvent event) {
        if (closed) return;
        try {
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
        ServiceStatus.touch="Protección táctil interrumpida: "+failure.getClass().getSimpleName();
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
