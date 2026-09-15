package es.sinnovedades.app;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Executes a verified user tap using the touched node's own click action. */
final class UserTap {
    enum Result { CLICKED, FALLBACK, CANCELLED }
    private static final class Entry {
        final AccessibilityNodeInfo node;
        final String path;
        final int depth;
        Entry(AccessibilityNodeInfo node,String path,int depth) { this.node=node; this.path=path; this.depth=depth; }
    }

    static Result perform(CoverService service,CoverService.TouchSnapshot expected,float x,float y,boolean longPress) {
        if (!expected.samePage(service.touchSnapshot()) || !TouchPolicy.contains(expected.screen,x,y)
                || TouchPolicy.contains(expected.target,x,y)) return Result.CANCELLED;
        AccessibilityNodeInfo root=null;
        List<AccessibilityWindowInfo> windows=service.getWindows();
        try {
            for (AccessibilityWindowInfo window:windows) {
                if (window.getType()==AccessibilityWindowInfo.TYPE_APPLICATION && window.isFocused()
                        && window.getId()==expected.windowId) { root=window.getRoot(); break; }
            }
        } finally { for (AccessibilityWindowInfo window:windows) window.recycle(); }
        if (root==null) return Result.CANCELLED;
        if (!expected.pkg.contentEquals(root.getPackageName()==null ? "" : root.getPackageName())
                || !expected.screen.same(box(root))) { root.recycle(); return Result.CANCELLED; }

        int action=longPress ? AccessibilityNodeInfo.ACTION_LONG_CLICK : AccessibilityNodeInfo.ACTION_CLICK;
        ArrayDeque<Entry> pending=new ArrayDeque<>();
        pending.add(new Entry(root,"r",0));
        List<TapTarget> targets=new ArrayList<>();
        List<AccessibilityNodeInfo> nodes=new ArrayList<>();
        int visited=0;
        boolean limited=false,disabledControl=false;
        try {
            while (!pending.isEmpty() && visited++<2600) {
                Entry entry=pending.removeFirst();
                AccessibilityNodeInfo node=entry.node;
                try {
                    if (entry.depth>=40 && node.getChildCount()>0) { limited=true; break; }
                    for (int i=0;i<node.getChildCount();i++) {
                        AccessibilityNodeInfo child=node.getChild(i);
                        if (child!=null) pending.addLast(new Entry(child,entry.path+"/"+i,entry.depth+1));
                    }
                    if (!node.isVisibleToUser() || !supports(node,action)) continue;
                    TabDetector.Box bounds=box(node);
                    if (!TouchPolicy.contains(bounds,x,y)) continue;
                    if (!node.isEnabled()) { disabledControl=true; continue; }
                    if (TapTarget.safe(bounds,expected.screen,expected.target,x,y)) {
                        targets.add(new TapTarget(entry.path,bounds));
                        nodes.add(AccessibilityNodeInfo.obtain(node));
                    }
                } finally { node.recycle(); }
            }
            if (limited || !pending.isEmpty() || disabledControl) return Result.FALLBACK;
            int selected=TapTarget.choose(targets,expected.screen,expected.target,x,y);
            if (selected<0) return Result.FALLBACK;
            AccessibilityNodeInfo node=nodes.get(selected);
            if (!expected.samePage(service.touchSnapshot()) || !node.refresh()
                    || node.getWindowId()!=expected.windowId || !node.isVisibleToUser() || !node.isEnabled()
                    || !targets.get(selected).bounds.same(box(node))
                    || !TapTarget.safe(box(node),expected.screen,expected.target,x,y)) return Result.CANCELLED;
            if (!supports(node,action)) return Result.FALLBACK;
            return node.performAction(action) ? Result.CLICKED : Result.FALLBACK;
        } finally {
            while (!pending.isEmpty()) pending.removeFirst().node.recycle();
            for (AccessibilityNodeInfo node:nodes) node.recycle();
        }
    }

    private static boolean supports(AccessibilityNodeInfo node,int action) {
        return (node.getActions()&action)!=0 || (action==AccessibilityNodeInfo.ACTION_CLICK
            ? node.isClickable() : node.isLongClickable());
    }
    private static TabDetector.Box box(AccessibilityNodeInfo node) {
        Rect r=new Rect(); node.getBoundsInScreen(r);
        return new TabDetector.Box(r.left,r.top,r.right,r.bottom);
    }
}
