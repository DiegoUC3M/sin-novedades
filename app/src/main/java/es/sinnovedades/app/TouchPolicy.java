package es.sinnovedades.app;

/** One physical interaction. Decisions do not depend on the painted overlay. */
public final class TouchPolicy {
    public enum Decision { WAIT, BLOCK, DELEGATE, TAP }
    private final float slop;
    private float startX,startY;
    private Decision decision=Decision.BLOCK;
    private boolean moved;

    public TouchPolicy(float slop) { this.slop=slop; }

    public Decision down(float x,float y,boolean navigation,TabDetector.Box target) {
        startX=x; startY=y; moved=false;
        decision=contains(target,x,y) ? Decision.BLOCK : navigation ? Decision.WAIT : Decision.DELEGATE;
        return decision;
    }

    public Decision move(float x,float y) {
        if (decision!=Decision.WAIT) return decision;
        float dx=Math.abs(x-startX),dy=Math.abs(y-startY);
        if (Math.max(dx,dy)<=slop) return decision;
        moved=true;
        // A diagonal swipe must not turn into a horizontal page change.
        if (dx>=dy) decision=Decision.BLOCK;
        else if (dy>=dx*1.5f) decision=Decision.DELEGATE;
        return decision;
    }

    public Decision up(float x,float y,TabDetector.Box currentTarget) {
        move(x,y);
        if (contains(currentTarget,startX,startY) || contains(currentTarget,x,y)) decision=Decision.BLOCK;
        else if (decision==Decision.WAIT) decision=moved ? Decision.BLOCK : Decision.TAP;
        return decision;
    }

    public Decision multiplePointers() {
        // Do not turn a consumed horizontal swipe or a blocked tab into a pass-through.
        if (decision==Decision.WAIT) decision=Decision.BLOCK;
        return decision;
    }

    public Decision cancel() { return decision=Decision.BLOCK; }
    public Decision decision() { return decision; }
    public static boolean contains(TabDetector.Box b,float x,float y) {
        return b!=null && x>=b.left && x<b.right && y>=b.top && y<b.bottom;
    }
}
