package es.sinnovedades.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Regression fixtures target accidental obstruction, not implementation details. */
public final class TabDetectorTest {
    private static int checks;
    static TabDetector.Box b(int l,int t,int r,int bt) { return new TabDetector.Box(l,t,r,bt); }
    static final TabDetector.Box SCREEN=b(0,24,400,900), BAR=b(0,800,400,880);
    static TabDetector.Tab tab(String label,int l,int r) {
        return new TabDetector.Tab(TabDetector.label(label),b(l,805,r,875),BAR,true);
    }
    static List<TabDetector.Tab> normal() {
        return new ArrayList<>(Arrays.asList(tab("Chats",0,100),tab("Novedades",100,200),
            tab("Comunidades",200,300),tab("Llamadas",300,400)));
    }
    static void expect(boolean test,String name) { checks++; if (!test) throw new AssertionError(name); }
    static TabDetector.Box detect(List<TabDetector.Tab> tabs) { return TabDetector.detect(tabs,SCREEN,1,false); }
    public static void main(String[] args) {
        expect(detect(normal()).same(b(100,805,200,875)),"Spanish four-tab navigation: exact updates hit target");
        List<TabDetector.Tab> english=Arrays.asList(tab("Chats",0,100),tab("Updates, tab 2 of 4",100,200),tab("Meta AI",200,300),tab("Calls",300,400));
        expect(detect(english)!=null,"English accessibility metadata");
        expect(TabDetector.label("Novedades, pestaña 2 de 4").equals("updates"),"Spanish tab metadata");
        expect(TabDetector.label("Pestaña Novedades").equals("updates"),"Tab role before the label");
        expect(TabDetector.label("Updates\nTab 2 of 4").equals("updates"),"Multiline accessibility description");
        expect(TabDetector.label("Novedades de mi trabajo").isEmpty(),"Message starting with Novedades");
        expect(TabDetector.label("Novedades123").isEmpty(),"Unseparated title suffix");
        expect(TabDetector.label("Novedades importantes").isEmpty(),"Chat title, not tab");
        expect(TabDetector.label("chatsbackup").isEmpty(),"Substring must not match");
        expect(TabDetector.detect(normal(),SCREEN,1,true)==null,"Never cover the message composer");
        expect(detect(Arrays.asList(tab("Novedades",100,200)))==null,"Single chat/message named Novedades");
        expect(detect(Arrays.asList(tab("Chats",0,100),tab("Novedades",100,200)))==null,"Incomplete navigation must fail open");
        List<TabDetector.Tab> duplicate=normal(); duplicate.add(tab("Novedades",100,200));
        expect(detect(duplicate).same(b(100,805,200,875)),"Duplicate parent/child labels");
        List<TabDetector.Tab> fake=new ArrayList<>();
        for (TabDetector.Tab t:normal()) fake.add(new TabDetector.Tab(t.kind,t.hit,null,false));
        expect(detect(fake)==null,"Chat content without a navigation container");
        List<TabDetector.Tab> flattened=new ArrayList<>();
        for (TabDetector.Tab t:normal()) flattened.add(new TabDetector.Tab(t.kind,t.hit,null,true));
        expect(detect(flattened).same(b(100,805,200,875)),"Semantic tabs survive a flattened accessibility container");
        flattened.set(0,new TabDetector.Tab("chats",b(0,805,100,875),null,false));
        expect(detect(flattened)==null,"A flattened row requires independent navigation semantics");
        List<TabDetector.Tab> shortLabels=Arrays.asList(
            new TabDetector.Tab("chats",b(0,840,100,865),null,true),
            new TabDetector.Tab("updates",b(100,840,200,865),null,true),
            new TabDetector.Tab("calls",b(300,840,400,865),null,true));
        expect(detect(shortLabels)==null,"Bare text labels are not complete button bounds");
        List<TabDetector.Tab> narrowRow=Arrays.asList(
            new TabDetector.Tab("chats",b(80,805,140,875),null,true),
            new TabDetector.Tab("updates",b(140,805,200,875),null,true),
            new TabDetector.Tab("calls",b(200,805,260,875),null,true));
        expect(detect(narrowRow)==null,"A small floating group is not the full navigation row");
        List<TabDetector.Tab> nested=normal();
        nested.set(1,new TabDetector.Tab("updates",b(100,805,200,875),b(0,790,400,880),true));
        expect(detect(nested).same(b(100,805,200,875)),"Different accessible wrappers around semantic tabs");
        List<TabDetector.Tab> overlapping=normal();
        overlapping.set(1,new TabDetector.Tab("updates",b(70,805,220,875),BAR,true));
        expect(detect(overlapping)==null,"An overlapping updates target must not cover neighbouring tabs");
        List<TabDetector.Tab> upper=new ArrayList<>();
        for (TabDetector.Tab t:normal()) upper.add(new TabDetector.Tab(t.kind,b(t.hit.left,405,t.hit.right,475),b(0,400,400,480),true));
        expect(detect(upper)==null,"Mid-screen controls cannot become a cover");
        List<TabDetector.Tab> crossRow=Arrays.asList(tab("Chats",0,100),tab("Novedades",100,200),
            new TabDetector.Tab("calls",b(300,800,400,810),BAR,true));
        expect(detect(crossRow)==null,"Misaligned labels");
        List<TabDetector.Tab> resized=new ArrayList<>();
        for (TabDetector.Tab t:normal()) resized.add(new TabDetector.Tab(t.kind,b(t.hit.left*3,t.hit.top*3,t.hit.right*3,t.hit.bottom*3),b(0,2400,1200,2640),true));
        expect(TabDetector.detect(resized,b(0,72,1200,2700),3,false).same(b(300,2415,600,2625)),"High density coordinates");
        List<TabDetector.Tab> landscape=Arrays.asList(
            new TabDetector.Tab("chats",b(0,322,200,382),b(0,320,800,384),true),
            new TabDetector.Tab("updates",b(200,322,400,382),b(0,320,800,384),true),
            new TabDetector.Tab("calls",b(600,322,800,382),b(0,320,800,384),true));
        TabDetector.Box land=TabDetector.detect(landscape,b(0,24,800,400),1,false);
        expect(land!=null && land.left==200 && land.right==400,"Unlabelled adjacent tab is never covered");
        expect(TabDetector.detect(normal(),b(0,0,100,180),1,false)==null,"Tiny floating window is ignored");
        System.out.println("PASS: "+checks+" detection regression checks");
    }
}
