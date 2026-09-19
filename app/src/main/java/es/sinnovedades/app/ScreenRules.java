package es.sinnovedades.app;

import java.text.Normalizer;
import java.util.Locale;

/** Geometry and exact labels for the separate hidden-updates page. */
public final class ScreenRules {
    private ScreenRules() {}

    static String normalize(CharSequence value) {
        if (value==null || value.length()>120) return "";
        return Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
            .toLowerCase(Locale.ROOT).trim().replaceAll("\\s+"," ");
    }

    public static boolean hiddenTitle(CharSequence value) {
        String label=normalize(value);
        return label.equals("actualizaciones ocultas") || label.equals("novedades ocultas")
            || label.equals("estados ocultos") || label.equals("hidden updates")
            || label.equals("muted updates") || label.equals("muted status updates");
    }

    public static boolean backLabel(CharSequence value) {
        String label=normalize(value);
        return label.equals("atras") || label.equals("volver") || label.equals("navigate up")
            || label.equals("navegar hacia arriba") || label.equals("back");
    }

    public static boolean headerTitle(TabDetector.Box title,TabDetector.Box screen,float d) {
        return screen.contains(title) && title.top<screen.top+160*d
            && title.bottom<=screen.top+200*d && title.width()>=70*d
            && title.height()>=12*d && title.height()<=80*d;
    }

    public static boolean toolbar(TabDetector.Box bar,TabDetector.Box screen,float d) {
        return bar!=null && screen.contains(bar) && bar.width()>=screen.width()*0.7
            && bar.height()>=40*d && bar.height()<=128*d
            && bar.top<screen.top+100*d && bar.bottom<=screen.top+220*d;
    }

    public static TabDetector.Box hiddenContent(TabDetector.Box screen,TabDetector.Box title,
            TabDetector.Box bar,TabDetector.Box back,float d,boolean editor,int contentBottom) {
        if (editor || title==null || back==null || !headerTitle(title,screen,d) || !toolbar(bar,screen,d)) return null;
        if (!bar.contains(title) || !bar.contains(back) || back.width()<24*d || back.width()>96*d
            || back.height()<24*d || back.height()>96*d) return null;
        boolean edge=back.right<=screen.left+screen.width()*0.3 || back.left>=screen.right-screen.width()*0.3;
        boolean overlaps=back.left<title.right && back.right>title.left && back.top<title.bottom && back.bottom>title.top;
        if (!edge || overlaps || contentBottom>screen.bottom || contentBottom-bar.bottom<100*d) return null;
        // Leave the real toolbar/back button and the system navigation accessible.
        return new TabDetector.Box(screen.left,bar.bottom,screen.right,contentBottom);
    }

    public static boolean visitChildren(TabDetector.Box bounds,TabDetector.Box screen,float d,boolean visible) {
        // Invisible/zero-sized wrappers may still expose visible descendants.
        // Visible rows wholly between the toolbar and footer cannot be navigation.
        return !visible || bounds.width()<=0 || bounds.height()<=0
            || bounds.top<screen.top+220*d || bounds.bottom>screen.bottom-260*d;
    }
}
