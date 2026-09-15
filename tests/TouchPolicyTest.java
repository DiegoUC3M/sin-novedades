package es.sinnovedades.app;

import static es.sinnovedades.app.TouchPolicy.Decision.*;

public final class TouchPolicyTest {
    private static int checks;
    private static final TabDetector.Box target=new TabDetector.Box(100,700,200,780);
    private static TouchPolicy policy() { return new TouchPolicy(8); }
    private static void check(Object actual,Object expected,String name) {
        checks++;
        if (actual!=expected) throw new AssertionError(name+": "+actual+" != "+expected);
    }
    public static void main(String[] args) {
        TouchPolicy p=policy();
        check(p.down(150,750,true,target),BLOCK,"target blocked before any overlay exists");
        check(p.move(300,600),BLOCK,"drag cannot escape blocked target");
        check(p.multiplePointers(),BLOCK,"extra pointer cannot escape target");
        check(p.up(300,600,target),BLOCK,"release outside is still blocked");
        check(p.down(50,750,true,target),WAIT,"other tab waits for gesture classification");
        check(p.up(50,750,target),TAP,"other tab is allowed");
        check(p.down(250,350,true,target),WAIT,"navigation content waits");
        check(p.move(253,353),WAIT,"finger jitter is not a swipe");
        check(p.up(252,351,target),TAP,"content tap is allowed");
        p.down(250,350,true,target);
        check(p.move(350,350),BLOCK,"swipe to right blocked");
        check(p.up(350,350,target),BLOCK,"right swipe does not become tap");
        p.down(250,350,true,target);
        check(p.move(150,350),BLOCK,"swipe to left blocked");
        check(p.multiplePointers(),BLOCK,"horizontal swipe remains blocked with extra finger");
        p.down(250,350,true,target);
        check(p.move(251,200),DELEGATE,"vertical scroll forwards to app");
        p.down(250,350,true,target);
        check(p.move(270,370),BLOCK,"diagonal horizontal risk blocked");
        p.down(250,350,true,target);
        check(p.move(260,364),WAIT,"ambiguous diagonal waits");
        check(p.up(260,364,target),BLOCK,"ambiguous movement is not replayed as tap");
        p.down(250,350,true,target);
        check(p.multiplePointers(),BLOCK,"multi-touch on navigation cannot change pages");
        check(p.down(250,350,false,null),DELEGATE,"chat gestures retain native handling");
        check(p.down(250,350,false,null),DELEGATE,"other apps retain native handling");
        p.down(150,750,true,null);
        check(p.up(150,750,target),BLOCK,"target appearing after touch down blocks release");
        p.down(50,750,true,target);
        check(p.up(150,750,target),BLOCK,"release on target blocked");
        p.down(250,350,true,target);
        check(p.cancel(),BLOCK,"cancel never clicks");
        check(p.up(250,350,target),BLOCK,"cancelled gesture remains cancelled");
        check(p.down(50,750,true,target),WAIT,"new gesture resets previous cancellation");
        p.down(250,350,true,target);
        for (int x=251;x<=258;x++) check(p.move(x,350),WAIT,"slow initial movement "+x);
        check(p.move(259,350),BLOCK,"slow swipe is measured from original down");
        p.down(250,350,true,target);
        check(p.up(400,350,target),BLOCK,"fast swipe without move callback cannot become a tap");
        p.down(50,750,true,target);
        check(p.up(50,750,target),TAP,"one release permits exactly one tap");
        check(p.up(50,750,target),BLOCK,"a late duplicate release cannot click again");
        System.out.println("TouchPolicy: "+checks+" checks passed");
    }
}
