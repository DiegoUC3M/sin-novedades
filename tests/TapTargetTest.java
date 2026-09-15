package es.sinnovedades.app;

import java.util.Arrays;

public final class TapTargetTest {
    private static int checks;
    private static final TabDetector.Box screen=new TabDetector.Box(0,0,400,800);
    private static final TabDetector.Box blocked=new TabDetector.Box(100,700,200,780);
    private static TapTarget target(String path,int l,int t,int r,int b) {
        return new TapTarget(path,new TabDetector.Box(l,t,r,b));
    }
    private static void check(int actual,int expected,String name) {
        checks++; if (actual!=expected) throw new AssertionError(name+": "+actual+" != "+expected);
    }
    private static int select(float x,float y,TapTarget... targets) {
        return TapTarget.choose(Arrays.asList(targets),screen,blocked,x,y);
    }
    public static void main(String[] args) {
        TapTarget chats=target("r/0",0,700,100,780);
        TapTarget updates=target("r/1",100,700,200,780);
        TapTarget communities=target("r/2",200,700,300,780);
        TapTarget calls=target("r/3",300,700,400,780);
        check(select(50,740,chats,updates,communities,calls),0,"exact Chats target");
        check(select(250,740,chats,updates,communities,calls),2,"exact Communities target");
        check(select(350,740,chats,updates,communities,calls),3,"exact Calls target");
        check(select(150,740,chats,updates,communities,calls),-1,"Novedades never executes an action");
        check(select(100,740,chats,updates),-1,"left boundary of Novedades is blocked");
        check(select(200,740,updates,communities),1,"right boundary belongs to next tab");
        check(select(50,740,target("r",0,700,400,780)),-1,"whole navigation container cannot receive the click");
        check(select(50,740,target("r",0,0,400,800),chats),1,"root covering Novedades is ignored");
        TapTarget row=target("r/0",0,100,400,200);
        TapTarget child=target("r/0/1",10,120,90,180);
        check(select(50,150,row),0,"chat list row can be opened");
        check(select(50,150,row,child),1,"deepest clickable child receives its own action");
        check(select(50,150,child,row),0,"selection does not depend on traversal order");
        check(select(50,150,row,target("r/1",0,100,400,200)),-1,"overlapping siblings require native hit testing");
        check(select(50,150,child,target("r/0/10",10,120,90,180)),-1,"path prefix is not ancestry without a separator");
        check(select(50,250,row,child),-1,"no nearest-node guessing outside the touched control");
        check(select(50,150,target("r/0",-1,100,400,200)),-1,"out-of-window candidate rejected");
        check(select(-1,150,row),-1,"outside display rejected");
        check(select(50,800,target("r/0",0,780,400,850)),-1,"bottom display boundary excluded");
        check(select(50,150),-1,"no action metadata uses fallback");
        check(select(50,740,target("r/0",0,700,120,780)),-1,"button overlapping protected tab is rejected");
        check(select(50.75f,740.5f,chats),0,"fractional physical coordinates retain the correct control");
        System.out.println("TapTarget: "+checks+" checks passed");
    }
}
