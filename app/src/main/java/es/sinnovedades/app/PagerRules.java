package es.sinnovedades.app;

import java.util.List;

/** Pure geometry for visible page roots of the confirmed main navigation pager. */
public final class PagerRules {
    private PagerRules() {}

    public enum State { UNKNOWN, ALIGNED, PARTIAL }

    // Physical pixels: a high-density display must not expose a wider strip.
    private static final int EDGE_TOLERANCE=2;

    public static boolean validViewport(TabDetector.Box viewport,TabDetector.Box content,float d) {
        return validDensity(d) && positive(viewport) && positive(content) && content.contains(viewport)
            && viewport.width()>=content.width()*0.8 && viewport.height()>=content.height()*0.45;
    }

    /**
     * The caller must pass fresh, visible, direct page roots, excluding pager decor.
     * Bounds may be clipped by Android or may retain their off-screen coordinates.
     * UNKNOWN is not evidence that a previously observed transition has finished.
     * In particular, no elapsed-time rule can distinguish a held drag from rest.
     */
    public static State state(TabDetector.Box viewport,List<TabDetector.Box> pages,float d) {
        if (!validDensity(d) || !positive(viewport) || pages==null) return State.UNKNOWN;
        boolean aligned=false;
        for (TabDetector.Box page:pages) {
            if (!positive(page)) continue;
            int height=Math.min(page.bottom,viewport.bottom)-Math.max(page.top,viewport.top);
            if (height<viewport.height()*0.7) continue;
            int left=Math.max(page.left,viewport.left),right=Math.min(page.right,viewport.right);
            int width=right-left;
            if (width<=EDGE_TOLERANCE) continue;

            // A page root wider than the viewport is not a supported page layout.
            // Do not mistake a large scrollable content container for a settled page.
            if (page.width()>viewport.width()+EDGE_TOLERANCE) continue;
            boolean leftEdge=left<=viewport.left+EDGE_TOLERANCE;
            boolean rightEdge=right>=viewport.right-EDGE_TOLERANCE;
            if (leftEdge && rightEdge) { aligned=true; continue; }

            // An intrinsically narrow decoration and a very thin, clipped page
            // are indistinguishable without a prior full-width observation.
            // A raw full-width page remains recognisable even at a tiny overlap.
            boolean originalPageWidth=page.width()>=viewport.width()-EDGE_TOLERANCE;
            if ((leftEdge || rightEdge) && (originalPageWidth || width>=viewport.width()*0.2))
                return State.PARTIAL;
        }
        return aligned ? State.ALIGNED : State.UNKNOWN;
    }

    private static boolean positive(TabDetector.Box b) {
        return b!=null && b.width()>0 && b.height()>0;
    }

    private static boolean validDensity(float d) {
        return d>0 && !Float.isInfinite(d) && !Float.isNaN(d);
    }
}
