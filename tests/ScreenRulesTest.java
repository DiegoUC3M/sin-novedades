package es.sinnovedades.app;

public final class ScreenRulesTest {
    private static int checks;
    static TabDetector.Box b(int l,int t,int r,int bottom) { return new TabDetector.Box(l,t,r,bottom); }
    static void expect(boolean pass,String label) { checks++; if (!pass) throw new AssertionError(label); }
    public static void main(String[] args) {
        TabDetector.Box screen=b(0,0,400,900),bar=b(0,24,400,80),title=b(56,32,380,64),back=b(0,24,56,80);
        expect(ScreenRules.hiddenTitle("Actualizaciones ocultas"),"Exact title from the reported screen");
        expect(ScreenRules.hiddenTitle("  ACTUALIZACIONES\nOCULTAS  "),"Case and whitespace");
        expect(ScreenRules.hiddenTitle("Muted updates"),"English alternative");
        expect(!ScreenRules.hiddenTitle("Tengo actualizaciones ocultas"),"No message substring matching");
        expect(!ScreenRules.hiddenTitle("Actualizaciones ocultas de ayer"),"No partial title matching");
        expect(!ScreenRules.hiddenTitle(null),"Missing title");
        expect(ScreenRules.backLabel("Atrás"),"Spanish accessible back button");
        expect(ScreenRules.backLabel("Navigate up"),"Android toolbar navigation");
        expect(!ScreenRules.backLabel("Más opciones"),"Overflow menu is not a back action");
        TabDetector.Box content=ScreenRules.hiddenContent(screen,title,bar,back,1,false,852);
        expect(content!=null && content.same(b(0,80,400,852)),"Hide list while leaving header and system navigation");
        expect(content.top>=back.bottom,"Back button remains fully reachable");
        expect(ScreenRules.hiddenContent(screen,title,bar,back,1,true,852)==null,"A chat with the same title and editor stays visible");
        expect(ScreenRules.hiddenContent(screen,title,bar,null,1,false,852)==null,"No cover without a back button");
        expect(ScreenRules.hiddenContent(screen,title,null,back,1,false,852)==null,"A plain text row is not a toolbar");
        expect(ScreenRules.hiddenContent(screen,b(56,360,380,392),b(0,350,400,406),b(0,350,56,406),1,false,852)==null,"Mid-screen row with same title is ignored");
        expect(ScreenRules.hiddenContent(screen,title,bar,b(120,24,176,80),1,false,852)==null,"A central clickable icon does not prove navigation");
        expect(ScreenRules.hiddenContent(screen,title,bar,b(0,24,80,80),1,false,852)==null,"Overlapping title and back target rejected");
        expect(ScreenRules.hiddenContent(screen,title,bar,back,1,false,950)==null,"Do not cover beyond the app window");
        expect(ScreenRules.hiddenContent(screen,title,bar,back,1,false,90)==null,"Do not cover a tiny floating panel");
        expect(ScreenRules.hiddenContent(b(0,0,1200,2700),b(168,96,1140,192),b(0,72,1200,240),b(0,72,168,240),3,false,2556).same(b(0,240,1200,2556)),"High-density page bounds");
        expect(ScreenRules.hiddenContent(screen,b(20,32,344,64),bar,b(344,24,400,80),1,false,852)!=null,"Right-side navigation in RTL layout");
        expect(!ScreenRules.visitChildren(b(0,300,400,500),screen,1,true),"Skip descendants of visible middle rows");
        expect(ScreenRules.visitChildren(b(0,24,400,80),screen,1,true),"Keep toolbar descendants");
        expect(ScreenRules.visitChildren(b(0,780,400,852),screen,1,true),"Keep footer and editor descendants");
        expect(ScreenRules.visitChildren(b(0,300,400,500),screen,1,false),"Invisible wrappers may have visible descendants");
        expect(ScreenRules.visitChildren(b(0,0,0,0),screen,1,false),"Zero-sized wrappers remain traversable");
        expect(ScreenRules.visitChildren(screen,screen,1,true),"Traverse the root");
        expect(InspectionPolicy.due(1000,995,0,true)==1000,"Selection event bypasses cooldown immediately");
        expect(InspectionPolicy.due(1000,995,0,false)==1075,"Content storms retain bounded coalescing");
        expect(InspectionPolicy.due(1100,995,0,false)==1100,"Idle content event adds no artificial delay");
        expect(InspectionPolicy.due(1000,995,250,true)==1250,"Fallback timer remains scheduled");
        expect(InspectionPolicy.FOREGROUND_RETRY_MS==250,"Missing events retry after 250 ms instead of 1000");
        System.out.println("PASS: "+checks+" screen/scheduling regression checks");
    }
}
