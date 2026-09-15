package es.sinnovedades.app;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pure geometry/label rules. Never acts on views or falls back to fixed coordinates. */
public final class TabDetector {
    private TabDetector() {}

    public static final class Box {
        public final int left, top, right, bottom;
        public Box(int l, int t, int r, int b) { left=l; top=t; right=r; bottom=b; }
        public int width() { return right-left; }
        public int height() { return bottom-top; }
        public int cx() { return (left+right)/2; }
        public int cy() { return (top+bottom)/2; }
        public boolean contains(Box b) {
            return left<=b.left && top<=b.top && right>=b.right && bottom>=b.bottom;
        }
        public boolean same(Box b) {
            return b!=null && left==b.left && top==b.top && right==b.right && bottom==b.bottom;
        }
        @Override public String toString() { return left+","+top+"–"+right+","+bottom; }
    }

    public static final class Tab {
        public final String kind;
        public final Box hit, bar;
        public final boolean tabHint, selected;
        public Tab(String kind, Box hit, Box bar, boolean hint) {
            this(kind,hit,bar,hint,false);
        }
        public Tab(String kind, Box hit, Box bar, boolean hint, boolean selected) {
            this.kind=kind; this.hit=hit; this.bar=bar; this.tabHint=hint; this.selected=selected;
        }
    }

    public static final class Navigation {
        public final Box target, content;
        // Empty means unavailable or contradictory; accessibility focus is not selection.
        public final String selected;
        Navigation(Box target, Box content, String selected) {
            this.target=target; this.content=content; this.selected=selected;
        }
        public boolean updatesOpen() { return "updates".equals(selected); }
    }

    public static boolean selectedDescription(CharSequence value) {
        if (value==null || value.length()>120) return false;
        String s=Normalizer.normalize(value,Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).trim().replaceAll("\\s+"," ");
        if (s.equals("selected") || s.equals("seleccionado") || s.equals("seleccionada")) return true;
        // Read selection metadata only on a recognised tab, never arbitrary content.
        if (label(value).isEmpty()) return false;
        if (s.matches(".*\\b(?:not selected|no seleccionad[oa])\\b.*")) return false;
        return s.matches(".*\\b(?:selected|seleccionad[oa])\\b.*");
    }

    public static String label(CharSequence value) {
        if (value==null || value.length()>120) return "";
        String s=Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).trim().replaceAll("\\s+"," ");
        if (s.startsWith("pestana ")) s=s.substring(8);
        else if (s.startsWith("tab ")) s=s.substring(4);
        // Only a whole label, with optional accessibility tab/count metadata.
        String[] kinds={"updates", "chats", "calls", "communities", "tools", "ai", "contacts"};
        String[][] words={{"novedades", "updates", "estados", "status", "actualizaciones"},
            {"chats", "conversaciones"}, {"llamadas", "calls"},
            {"comunidades", "communities"}, {"herramientas", "tools"},
            {"meta ai", "meta ia"}, {"contactos", "contacts"}};
        for (int i=0;i<kinds.length;i++) for (String word:words[i]) {
            if (s.equals(word)) return kinds[i];
            if (!s.startsWith(word)) continue;
            if (Character.isLetterOrDigit(s.charAt(word.length()))) continue;
            String tail=s.substring(word.length()).trim();
            if (tail.matches("[,.:;]?\\s*(?:(?:pestana|tab)\\b|\\d+\\b|(?:no )?seleccionad[oa]\\b|(?:not )?selected\\b).*"))
                return kinds[i];
        }
        return "";
    }

    public static Box detect(List<Tab> input, Box screen, float density, boolean editorVisible) {
        Navigation nav=navigation(input,screen,density,editorVisible);
        return nav==null ? null : nav.target;
    }

    public static Navigation navigation(List<Tab> input, Box screen, float density, boolean editorVisible) {
        if (editorVisible || screen.width()<160*density || screen.height()<200*density) return null;
        List<Tab> tabs=new ArrayList<>();
        for (Tab t:input) {
            if (t.kind.isEmpty() || t.hit==null || !screen.contains(t.hit)) continue;
            if (t.hit.width()<12*density || t.hit.height()<12*density || t.hit.width()>screen.width()*0.48) continue;
            if (t.hit.height()>130*density || t.hit.top<screen.bottom-220*density) continue;
            if (t.bar==null) {
                if (!t.tabHint || t.hit.height()<36*density || screen.bottom-t.hit.bottom>80*density) continue;
            } else {
                if (!screen.contains(t.bar) || !t.bar.contains(t.hit)) continue;
                if (t.bar.width()<screen.width()*0.7 || t.bar.height()<28*density || t.bar.height()>150*density) continue;
                if (t.bar.top<screen.bottom-220*density || screen.bottom-t.bar.bottom>80*density) continue;
            }
            boolean duplicate=false;
            for (int i=0;i<tabs.size();i++) {
                Tab prev=tabs.get(i);
                if (prev.kind.equals(t.kind) && Math.abs(prev.hit.cx()-t.hit.cx())<8*density
                        && Math.abs(prev.hit.cy()-t.hit.cy())<16*density) {
                    Tab chosen=t.hit.width()>prev.hit.width() || (t.tabHint && !prev.tabHint) ? t : prev;
                    tabs.set(i,new Tab(chosen.kind,chosen.hit,chosen.bar,chosen.tabHint,prev.selected || t.selected));
                    duplicate=true; break;
                }
            }
            if (!duplicate) tabs.add(t);
        }
        Navigation match=null;
        for (Tab target:tabs) {
            if (!target.kind.equals("updates")) continue;
            boolean chats=false, corroboration=false;
            int count=0, hints=0;
            int left=target.hit.left,right=target.hit.right;
            boolean sameContainer=true;
            int footerTop=target.bar==null ? target.hit.top : target.bar.top;
            String selected="";
            boolean selectionConflict=false;
            for (Tab other:tabs) {
                if (Math.abs(other.hit.cy()-target.hit.cy())>24*density) continue;
                boolean commonBar=target.bar!=null && target.bar.same(other.bar);
                if (!commonBar && !(target.tabHint && other.tabHint)) continue;
                if (other!=target && Math.abs(other.hit.cx()-target.hit.cx())<24*density) continue;
                if (other!=target && other.hit.left<target.hit.right && other.hit.right>target.hit.left) continue;
                sameContainer&=commonBar;
                count++;
                footerTop=Math.min(footerTop,other.bar==null ? other.hit.top : other.bar.top);
                if (other.selected) {
                    if (!selected.isEmpty() && !selected.equals(other.kind)) selectionConflict=true;
                    selected=other.kind;
                }
                if (other.tabHint) hints++;
                left=Math.min(left,other.hit.left); right=Math.max(right,other.hit.right);
                if (other.kind.equals("chats")) chats=true;
                if (!other.kind.equals("chats") && !other.kind.equals("updates")) corroboration=true;
            }
            if (!chats || !corroboration || count<3) continue;
            // Tab semantics are preferred; a complete, bottom-anchored bar is the fallback.
            if (sameContainer) {
                if (hints==0 && screen.bottom-target.bar.bottom>40*density) continue;
            } else {
                // Some custom navigation controls expose tabs without a separate
                // bar node. Require independent tab semantics across a full row.
                if (hints<count || right-left<screen.width()*0.7
                    || target.hit.height()<36*density || screen.bottom-target.hit.bottom>80*density) continue;
            }
            // Use the actual clickable target. Unknown or unlabeled neighbours must
            // never have their space covered through inferred equal-width slots.
            int l=target.hit.left;
            int r=target.hit.right;
            if (r-l>screen.width()*0.48 || r-l<24*density) continue;
            Box result=new Box(l,target.hit.top,r,target.hit.bottom);
            // Ambiguity must never produce a screen-wide or arbitrary cover.
            if (match!=null && !match.target.same(result)) return null;
            Box content=new Box(screen.left,screen.top,screen.right,footerTop);
            if (content.height()<=0 || !screen.contains(content)) return null;
            match=new Navigation(result,content,selectionConflict ? "" : selected);
        }
        return match;
    }
}
