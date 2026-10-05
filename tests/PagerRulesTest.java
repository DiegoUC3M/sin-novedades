package es.sinnovedades.app;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class PagerRulesTest {
    private static int checks;
    static TabDetector.Box b(int l,int t,int r,int bottom) { return new TabDetector.Box(l,t,r,bottom); }
    static void expect(boolean pass,String label) { checks++; if (!pass) throw new AssertionError(label); }
    static PagerRules.State state(TabDetector.Box viewport,TabDetector.Box... pages) {
        return PagerRules.state(viewport,Arrays.asList(pages),1);
    }
    public static void main(String[] args) {
        TabDetector.Box content=b(0,24,400,840),viewport=b(0,80,400,840);
        expect(PagerRules.validViewport(viewport,content,1),"Main pager below the app toolbar");
        expect(!PagerRules.validViewport(b(0,80,300,840),content,1),"A narrow carousel is not the main pager");
        expect(!PagerRules.validViewport(b(0,500,400,840),content,1),"A short carousel is not the main pager");
        expect(!PagerRules.validViewport(b(-1,80,400,840),content,1),"Pager cannot extend outside validated content");
        expect(!PagerRules.validViewport(b(0,80,400,841),content,1),"Pager cannot enter the navigation bar");
        expect(!PagerRules.validViewport(null,content,1),"Missing viewport");
        expect(!PagerRules.validViewport(viewport,null,1),"Missing content");
        expect(!PagerRules.validViewport(viewport,content,0),"Invalid density");

        expect(state(viewport,viewport)==PagerRules.State.ALIGNED,"Resting page is aligned");
        expect(state(viewport,b(0,80,200,840))==PagerRules.State.PARTIAL,"Only current clipped page exposed halfway");
        expect(state(viewport,b(200,80,400,840))==PagerRules.State.PARTIAL,"Incoming clipped page is sufficient");
        expect(state(viewport,b(-200,80,200,840),b(200,80,600,840))==PagerRules.State.PARTIAL,"Raw page positions also reveal a transition");
        expect(state(viewport,b(0,80,200,840),b(200,80,400,840))==PagerRules.State.PARTIAL,"Both clipped halves reveal a transition");
        List<TabDetector.Box> held=Collections.singletonList(b(0,80,200,840));
        expect(PagerRules.state(viewport,held,1)==PagerRules.state(viewport,held,1)
            && PagerRules.state(viewport,held,1)==PagerRules.State.PARTIAL,"Holding the same geometry cannot expire the cover");
        expect(state(viewport,b(0,80,250,840))==PagerRules.State.PARTIAL,"First observation can be halfway without a selected Updates tab");
        expect(state(viewport,b(0,80,350,840))==PagerRules.State.PARTIAL,"Reversing the gesture is still partial");
        expect(state(viewport,viewport)==PagerRules.State.ALIGNED,"Returning fully to the original page is aligned");
        expect(state(viewport,b(50,80,400,840))==PagerRules.State.PARTIAL,"Mirrored RTL transition");
        expect(state(viewport,b(0,-200,400,840))==PagerRules.State.ALIGNED,"Vertical displacement alone is not a horizontal transition");
        expect(state(viewport,b(-400,80,0,840),viewport,b(400,80,800,840))==PagerRules.State.ALIGNED,"Off-screen retained neighbours do not trigger the cover");
        expect(state(viewport,viewport,b(200,80,400,840))==PagerRules.State.PARTIAL,"Partial visible root takes precedence over an aligned root");
        expect(state(viewport,b(200,80,400,840),viewport)==PagerRules.State.PARTIAL,"Precedence is independent of child order");
        expect(state(viewport,b(0,80,10,840))==PagerRules.State.UNKNOWN,"Tall narrow edge decoration is insufficient evidence");
        expect(state(viewport,b(190,80,210,840))==PagerRules.State.UNKNOWN,"Tall central decoration is insufficient evidence");
        expect(state(viewport,b(0,80,200,180))==PagerRules.State.UNKNOWN,"Short decor is not a page root");
        expect(state(viewport,b(0,400,200,840))==PagerRules.State.UNKNOWN,"Partial vertical overlap is insufficient");
        expect(state(viewport,b(0,80,398,840))==PagerRules.State.ALIGNED,"Two physical pixels tolerate rounding");
        expect(state(viewport,b(0,80,397,840))==PagerRules.State.PARTIAL,"Three visible physical pixels reveal displacement");
        expect(state(viewport,b(397,80,797,840))==PagerRules.State.PARTIAL,"Thin overlap of an un-clipped page remains evidence");
        expect(state(viewport,b(398,80,798,840))==PagerRules.State.UNKNOWN,"Two-pixel overlap alone is not evidence");
        expect(state(viewport,b(-100,80,500,840))==PagerRules.State.UNKNOWN,"Oversized content container is not a page root");
        expect(state(viewport)==PagerRules.State.UNKNOWN,"Missing visible pages remain unknown");
        expect(state(viewport,b(0,0,0,0))==PagerRules.State.UNKNOWN,"Zero-size unsupported page remains unknown");
        expect(state(viewport,b(0,80,-1,840))==PagerRules.State.UNKNOWN,"Reversed bounds remain unknown");
        expect(PagerRules.state(viewport,null,1)==PagerRules.State.UNKNOWN,"Null child snapshot remains unknown");
        expect(PagerRules.state(viewport,held,Float.NaN)==PagerRules.State.UNKNOWN,"NaN density remains unknown");
        expect(PagerRules.state(b(0,300,1500,3000),Collections.singletonList(b(0,300,1497,3000)),3.75f)
            ==PagerRules.State.PARTIAL,"High density does not increase the strip tolerance");
        expect(PagerRules.validViewport(b(0,300,1500,3000),b(0,90,1500,3000),3.75f),"High-density main pager geometry");
        expect(state(b(100,80,500,840),b(100,80,300,840))==PagerRules.State.PARTIAL,"Non-zero window origin is supported");
        System.out.println("PASS: "+checks+" pager geometry regression checks");
    }
}
