package es.sinnovedades.app;

import java.util.List;

/** Chooses only the control under the user's finger, never an accessibility-focus target. */
final class TapTarget {
    final String path;
    final TabDetector.Box bounds;
    TapTarget(String path,TabDetector.Box bounds) { this.path=path; this.bounds=bounds; }

    static boolean safe(TabDetector.Box bounds,TabDetector.Box screen,TabDetector.Box forbidden,float x,float y) {
        if (!TouchPolicy.contains(screen,x,y) || TouchPolicy.contains(forbidden,x,y)
                || !TouchPolicy.contains(bounds,x,y)) return false;
        if (bounds.left<screen.left || bounds.top<screen.top
                || bounds.right>screen.right || bounds.bottom>screen.bottom) return false;
        // Never activate a common container whose click area includes Novedades.
        return forbidden==null || bounds.right<=forbidden.left || bounds.left>=forbidden.right
            || bounds.bottom<=forbidden.top || bounds.top>=forbidden.bottom;
    }

    static int choose(List<TapTarget> candidates,TabDetector.Box screen,TabDetector.Box forbidden,float x,float y) {
        int best=-1;
        for (int i=0;i<candidates.size();i++) {
            TapTarget candidate=candidates.get(i);
            if (!safe(candidate.bounds,screen,forbidden,x,y)) continue;
            if (best<0) { best=i; continue; }
            String before=candidates.get(best).path;
            if (candidate.path.startsWith(before+"/")) best=i;
            else if (!before.startsWith(candidate.path+"/")) return -1;
        }
        // Overlapping controls on different branches are ambiguous. Use physical
        // hit testing as a fallback instead of guessing which action to invoke.
        return best;
    }
}
