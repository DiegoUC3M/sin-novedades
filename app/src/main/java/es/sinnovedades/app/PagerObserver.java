package es.sinnovedades.app;

import android.os.Build;
import android.view.accessibility.AccessibilityNodeInfo;
import android.graphics.Rect;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Reads the main pager's geometry. Never listens to or dispatches physical input. */
final class PagerObserver {
    private AccessibilityNodeInfo pager;
    private int window=-1;
    private TabDetector.Box content;
    private long nextSearch;
    private boolean covered;
    String report="Páginas: todavía no comprobadas.";

    void searchSoon() { nextSearch=0; }

    void clear() {
        releaseNode(); window=-1; content=null; nextSearch=0; covered=false;
        report="Páginas: fuera de la navegación principal.";
    }

    private void releaseNode() {
        if (pager!=null) pager.recycle();
        pager=null;
    }

    boolean inspect(AccessibilityNodeInfo root,TabDetector.Box area,float d,long now) {
        if (window!=root.getWindowId() || !area.same(content)) {
            clear(); window=root.getWindowId(); content=area;
        }
        ServiceStatus.pagerChecks++;
        try {
        if (pager!=null && (!pager.refresh() || !mainPager(pager,area,d))) {
            releaseNode(); nextSearch=0;
        }
        if (pager==null && now>=nextSearch) {
            pager=find(root,area,d); nextSearch=now+1000;
        }
        PagerRules.State state=PagerRules.State.UNKNOWN;
        if (pager!=null) state=readPages(pager,d);
        else report="Páginas: UNKNOWN; contenedor principal no expuesto.";
        // A stationary finger produces no more scroll events. A timeout must
        // never reveal a half-exposed page. Only an aligned page clears this latch.
        if (state==PagerRules.State.PARTIAL) {
            if (!covered) ServiceStatus.pagerTransitions++;
            covered=true;
        } else if (state==PagerRules.State.ALIGNED) covered=false;
        } catch (RuntimeException unavailable) {
            // A transient accessibility failure is not evidence of a settled page.
            releaseNode(); nextSearch=0;
            report="Páginas: UNKNOWN; lectura interrumpida ("+unavailable.getClass().getSimpleName()+").";
        }
        report+="; cubierta de transición="+covered;
        return covered;
    }

    private AccessibilityNodeInfo find(AccessibilityNodeInfo root,TabDetector.Box area,float d) {
        ArrayDeque<AccessibilityNodeInfo> queue=new ArrayDeque<>();
        queue.add(AccessibilityNodeInfo.obtain(root));
        int visited=0;
        try {
            while (!queue.isEmpty() && visited++<64) {
                AccessibilityNodeInfo node=queue.removeFirst();
                try {
                    if (mainPager(node,area,d)) return AccessibilityNodeInfo.obtain(node);
                    TabDetector.Box b=box(node);
                    // The main pager needs a large ancestor. Do not walk message
                    // rows or small carousels while looking for its container.
                    if (node.isVisibleToUser() && b.width()>0 && b.height()>0
                        && (b.width()<area.width()*0.8 || b.height()<area.height()*0.45)) continue;
                    for (int i=0;i<node.getChildCount() && queue.size()<64;i++) {
                        AccessibilityNodeInfo child=child(node,i);
                        if (child!=null) queue.addLast(child);
                    }
                } finally { node.recycle(); }
            }
        } finally { while (!queue.isEmpty()) queue.removeFirst().recycle(); }
        return null;
    }

    private boolean mainPager(AccessibilityNodeInfo node,TabDetector.Box area,float d) {
        // ViewPager and ViewPager2 expose this class through accessibility even
        // when their application subclass has another name. Unknown layouts are
        // reported as unsupported instead of guessing from arbitrary text.
        return CoverService.isWhatsApp(node.getPackageName()) && node.isVisibleToUser()
            && className(node).contains("viewpager") && PagerRules.validViewport(box(node),area,d);
    }

    private PagerRules.State readPages(AccessibilityNodeInfo container,float d) {
        TabDetector.Box viewport=box(container);
        AccessibilityNodeInfo pages=AccessibilityNodeInfo.obtain(container);
        int count=0;
        List<TabDetector.Box> bounds=new ArrayList<>();
        try {
            // ViewPager2 can expose the compatibility class name ViewPager. Its
            // horizontal collection metadata can live on the parent or recycler.
            // A vertical list is instead a real page in ordinary ViewPager.
            boolean explicitV2=className(container).contains("viewpager2");
            if (explicitV2 || pages.getChildCount()==1) {
                AccessibilityNodeInfo recycler=null;
                for (int i=0;i<pages.getChildCount() && i<12;i++) {
                    AccessibilityNodeInfo candidate=child(pages,i);
                    if (candidate==null) continue;
                    if (candidate.refresh() && candidate.isVisibleToUser()
                        && className(candidate).contains("recyclerview")) { recycler=candidate; break; }
                    candidate.recycle();
                }
                if (recycler==null && explicitV2) {
                    report="Páginas: UNKNOWN; ViewPager2 sin contenedor accesible.";
                    return PagerRules.State.UNKNOWN;
                }
                if (recycler!=null) {
                    boolean horizontal=explicitV2 || horizontalCollection(container) || horizontalCollection(recycler)
                        || horizontalActions(recycler) || pageActions(container);
                    boolean vertical=verticalCollection(recycler);
                    if (horizontal) { pages.recycle(); pages=recycler; }
                    else {
                        recycler.recycle();
                        if (!vertical) {
                            report="Páginas: UNKNOWN; orientación del contenedor ambigua.";
                            return PagerRules.State.UNKNOWN;
                        }
                    }
                }
            }
            count=pages.getChildCount();
            if (count>12) {
                report="Páginas: UNKNOWN; demasiadas raíces de página.";
                return PagerRules.State.UNKNOWN;
            }
            boolean incomplete=false;
            for (int i=0;i<count;i++) {
                AccessibilityNodeInfo page=child(pages,i);
                if (page==null) { incomplete=true; continue; }
                try {
                    // Refresh the page itself: the footer and pager container can
                    // stay identical while a child moves beneath a held finger.
                    if (!page.refresh()) { incomplete=true; continue; }
                    if (page.isVisibleToUser() && CoverService.isWhatsApp(page.getPackageName()))
                        bounds.add(box(page));
                } finally { page.recycle(); }
            }
            PagerRules.State state=PagerRules.state(viewport,bounds,d);
            // Missing roots cannot prove that a previously covered drag finished.
            if (incomplete && state==PagerRules.State.ALIGNED) state=PagerRules.State.UNKNOWN;
            report="Páginas: "+state+"; visor="+viewport+"; raíces="+count+"; visibles="+bounds.size();
            return state;
        } finally { pages.recycle(); }
    }

    private static AccessibilityNodeInfo child(AccessibilityNodeInfo node,int i) {
        return Build.VERSION.SDK_INT>=33 ? node.getChild(i,0) : node.getChild(i);
    }

    private static boolean horizontalCollection(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo.CollectionInfo c=node.getCollectionInfo();
        return c!=null && c.getRowCount()==1 && c.getColumnCount()>1;
    }

    private static boolean verticalCollection(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo.CollectionInfo c=node.getCollectionInfo();
        if (c!=null && c.getRowCount()>1 && c.getColumnCount()<=1) return true;
        for (AccessibilityNodeInfo.AccessibilityAction a:node.getActionList()) {
            if (a.equals(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP)
                || a.equals(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN)) return true;
        }
        return false;
    }

    private static boolean pageActions(AccessibilityNodeInfo node) {
        if (Build.VERSION.SDK_INT<29) return false;
        for (AccessibilityNodeInfo.AccessibilityAction a:node.getActionList()) {
            if (a.equals(AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_LEFT)
                || a.equals(AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_RIGHT)) return true;
        }
        return false;
    }

    private static boolean horizontalActions(AccessibilityNodeInfo node) {
        if (pageActions(node)) return true;
        for (AccessibilityNodeInfo.AccessibilityAction a:node.getActionList()) {
            if (a.equals(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT)
                || a.equals(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT)) return true;
        }
        return false;
    }

    private static String className(AccessibilityNodeInfo node) {
        return String.valueOf(node.getClassName()).toLowerCase(Locale.ROOT);
    }

    private static TabDetector.Box box(AccessibilityNodeInfo node) {
        Rect r=new Rect(); node.getBoundsInScreen(r);
        return new TabDetector.Box(r.left,r.top,r.right,r.bottom);
    }
}
