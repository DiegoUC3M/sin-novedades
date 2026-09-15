package fixture;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Synthetic navigation for an EMPTY EMULATOR. This is not a WhatsApp client. */
public final class FixtureActivity extends Activity {
    private SharedPreferences stats;
    private LinearLayout root;
    private TextView state;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private int events;
    private long muteUntil;
    private boolean navigation;
    private final TextView[] tabs=new TextView[4];
    private final String[] labels={"Chats","Novedades","Comunidades","Llamadas"};
    private int selectedTab;
    private final Runnable storm=new Runnable() {
        public void run() { state.setText("Evento de prueba "+(++events)); handler.postDelayed(this,15); }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        stats=getSharedPreferences("fixture",MODE_PRIVATE);
        showNavigation();
    }

    private void base(String title) {
        root=new TestLayout(); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xffffffff);
        getWindow().setDecorFitsSystemWindows(false);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(i.left,i.top,i.right,i.bottom); return insets;
        });
        setContentView(root);
        state=new TextView(this); state.setText(title); state.setTextSize(22);
        state.setTextColor(0xff111111); root.addView(state,new LinearLayout.LayoutParams(-1,72));
    }

    private void spacer() { root.addView(new View(this),new LinearLayout.LayoutParams(-1,0,1)); }

    private void remember(View view,String name) {
        final String[] before={""};
        view.getViewTreeObserver().addOnGlobalLayoutListener(()->{
            int[] xy=new int[2]; view.getLocationOnScreen(xy);
            String position=(xy[0]+view.getWidth()/2)+","+(xy[1]+view.getHeight()/2);
            if (!position.equals(before[0])) {
                before[0]=position; stats.edit().putString(name,position).apply();
            }
        });
    }

    private void showNavigation() {
        navigation=true;
        if (getIntent().getBooleanExtra("quiet_return",false)) muteUntil=SystemClock.uptimeMillis()+1200;
        base("Barra sintética para verificar la cubierta");
        stats.edit().putString("screen","navigation").apply();
        Button chat=new Button(this); chat.setText("Entrar en un chat de prueba");
        chat.setOnClickListener(v->showChat()); root.addView(chat); remember(chat,"chat_center");
        TextView content=new TextView(this); content.setText("Zona de gestos");
        content.setGravity(Gravity.CENTER);
        root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        remember(content,"content_center");
        content.setOnClickListener(v->count("content_taps"));
        content.setOnLongClickListener(v->{ count("long_presses"); return true; });
        LinearLayout bar=new LinearLayout(this) {
            @Override public CharSequence getAccessibilityClassName() { return "android.widget.TabWidget"; }
        };
        bar.setOrientation(LinearLayout.HORIZONTAL);
        // Old versions that omit INCLUDE_NOT_IMPORTANT_VIEWS lose this container.
        bar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        for (int index=0;index<labels.length;index++) {
            final int tab=index;
            TextView item=new TextView(this); item.setText(labels[index]); item.setTextSize(14);
            tabs[index]=item;
            item.setTextColor(0xff111111); item.setGravity(Gravity.CENTER);
            item.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            item.setOnClickListener(v->{
                String key="taps_"+tab;
                stats.edit().putInt(key,stats.getInt(key,0)+1).apply();
                selectTab(tab);
            });
            item.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override public void onInitializeAccessibilityNodeInfo(View host,AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host,info);
                    String mode=getIntent().getStringExtra("selection");
                    if (mode==null) return;
                    info.setSelected(false);
                    if (mode.equals("checked")) { info.setCheckable(true); info.setChecked(tab==selectedTab); }
                    if (mode.equals("description")) info.setStateDescription(tab==selectedTab ? "Seleccionada" : "No seleccionada");
                }
                @Override public boolean performAccessibilityAction(View host,int action,Bundle args) {
                    if (action==android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) {
                        count("direct_attempts_"+tab);
                        // A normal native click must still work when ACTION_CLICK
                        // is rejected. The cover service must never invoke it.
                        if (tab==3) return false;
                    }
                    return super.performAccessibilityAction(host,action,args);
                }
            });
            bar.addView(item,new LinearLayout.LayoutParams(0,80,1));
            remember(item,"tab_"+tab+"_center");
        }
        root.addView(bar,new LinearLayout.LayoutParams(-1,80));
        selectTab(getIntent().getIntExtra("tab",0));
        if (getIntent().getBooleanExtra("storm",false)) handler.post(storm);
    }

    private void selectTab(int tab) {
        selectedTab=Math.max(0,Math.min(3,tab));
        for (int i=0;i<tabs.length;i++) {
            tabs[i].setSelected(i==selectedTab);
            tabs[i].setBackgroundColor(i==selectedTab ? 0xffc6f0da : 0xffffffff);
        }
        state.setText("Contenido de prueba: "+labels[selectedTab]);
        stats.edit().putInt("selected_tab",selectedTab).apply();
        tabs[selectedTab].sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
    }

    private void showChat() {
        handler.removeCallbacks(storm);
        muteUntil=0; navigation=false;
        base("Conversación sintética");
        stats.edit().putString("screen","chat").apply();
        Button back=new Button(this); back.setText("Volver a la barra");
        back.setOnClickListener(v->showNavigation()); root.addView(back);
        remember(back,"back_center");
        spacer();
        EditText input=new EditText(this); input.setHint("Escribe aquí");
        root.addView(input,new LinearLayout.LayoutParams(-1,64));
        remember(input,"editor_center");
    }

    private void count(String key) { stats.edit().putInt(key,stats.getInt(key,0)+1).apply(); }

    private final class TestLayout extends LinearLayout {
        private float startX,startY;
        private boolean horizontal,vertical;
        TestLayout() { super(FixtureActivity.this); }
        @Override public boolean onInterceptTouchEvent(MotionEvent e) {
            if (!navigation) return false;
            if (e.getActionMasked()==MotionEvent.ACTION_DOWN) {
                startX=e.getX(); startY=e.getY(); horizontal=false; vertical=false;
            } else if (e.getActionMasked()==MotionEvent.ACTION_MOVE) {
                float dx=Math.abs(e.getX()-startX),dy=Math.abs(e.getY()-startY);
                int slop=ViewConfiguration.get(FixtureActivity.this).getScaledTouchSlop();
                if (dx>slop && dx>=dy && !vertical) { horizontal=true; return true; }
                if (dy>slop && dy>dx) { vertical=true; return true; }
            }
            return false;
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked()==MotionEvent.ACTION_UP) {
                if (horizontal) {
                    count("horizontal_swipes");
                    selectTab(selectedTab+(e.getX()<startX ? 1 : -1));
                }
                if (vertical) count("vertical_scrolls");
            }
            return true;
        }
        @Override public boolean requestSendAccessibilityEvent(View child,AccessibilityEvent e) {
            return SystemClock.uptimeMillis()>=muteUntil && super.requestSendAccessibilityEvent(child,e);
        }
        @Override public void sendAccessibilityEventUnchecked(AccessibilityEvent e) {
            if (SystemClock.uptimeMillis()>=muteUntil) super.sendAccessibilityEventUnchecked(e);
        }
    }

    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }
}
