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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Synthetic navigation for an EMPTY EMULATOR. This is not a WhatsApp client.
 *
 * Manual pager checks (never install on a phone with WhatsApp):
 * am start -n com.whatsapp/fixture.FixtureActivity --ez pager true --ei tab 0
 * Drag left, hold the finger still, then reverse without lifting: the page roots
 * move while selected_tab stays 0. On release it changes only past half width.
 * Add --ei held_offset 240 to hold 240 physical pixels of the NEXT page in view,
 * with no completed-scroll or selected event. A negative value reveals PREVIOUS.
 * Repeat from tab 2 and offset -240; tab 0 and offset -240 must stay at the edge.
 * Existing selection, storm, quiet_return and standalone-page modes still apply.
 */
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
    private SyntheticPager pager;
    private final Runnable storm=new Runnable() {
        public void run() { state.setText("Evento de prueba "+(++events)); handler.postDelayed(this,15); }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        stats=getSharedPreferences("fixture",MODE_PRIVATE);
        String page=getIntent().getStringExtra("page");
        if ("hidden".equals(page) || "chat_named_hidden".equals(page)) showHidden("chat_named_hidden".equals(page));
        else showNavigation();
    }

    private void base(String title) {
        pager=null;
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
        if (getIntent().getBooleanExtra("pager",false)) {
            pager=new SyntheticPager();
            root.addView(pager,new LinearLayout.LayoutParams(-1,0,1));
            remember(pager,"content_center");
        } else {
            TextView content=new TextView(this); content.setText("Zona de gestos");
            content.setGravity(Gravity.CENTER);
            root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
            remember(content,"content_center");
            content.setOnClickListener(v->count("content_taps"));
            content.setOnLongClickListener(v->{ count("long_presses"); return true; });
        }
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
        if (pager!=null) pager.movePages(0,false);
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

    private void showHidden(boolean chat) {
        handler.removeCallbacks(storm); navigation=false; muteUntil=0;
        base(""); root.removeView(state);
        LinearLayout toolbar=new LinearLayout(this) {
            @Override public CharSequence getAccessibilityClassName() { return "android.widget.Toolbar"; }
        };
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        Button back=new Button(this); back.setText("←"); back.setContentDescription("Navigate up");
        back.setOnClickListener(v->{count("hidden_back");showNavigation();});
        toolbar.addView(back,new LinearLayout.LayoutParams(56,56)); remember(back,"hidden_back_center");
        state.setText("Actualizaciones ocultas"); state.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.addView(state,new LinearLayout.LayoutParams(-1,56));
        root.addView(toolbar,new LinearLayout.LayoutParams(-1,56));
        TextView content=new TextView(this); content.setText("Lista de estados de prueba");
        content.setGravity(Gravity.TOP);content.setTextColor(0xff111111);
        content.setOnClickListener(v->count("hidden_content_taps"));
        root.addView(content,new LinearLayout.LayoutParams(-1,0,1));remember(content,"hidden_content_center");
        if (chat) {
            EditText editor=new EditText(this);editor.setHint("Mensaje de prueba");
            root.addView(editor,new LinearLayout.LayoutParams(-1,64));
        }
        stats.edit().putString("screen",chat?"chat_named_hidden":"hidden").apply();
    }

    private void count(String key) { stats.edit().putInt(key,stats.getInt(key,0)+1).apply(); }

    /** Native views only: exposes the same pager class marker without AndroidX. */
    private final class SyntheticPager extends FrameLayout {
        private final FrameLayout[] pages=new FrameLayout[4];
        private float offset;
        private boolean initialHoldApplied;
        SyntheticPager() {
            super(FixtureActivity.this);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            setClipChildren(true); setClipToPadding(true);
            for (int i=0;i<pages.length;i++) {
                FrameLayout page=new FrameLayout(FixtureActivity.this);
                page.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
                page.setBackgroundColor(i%2==0 ? 0xfff4f4f4 : 0xffdcecf5);
                TextView content=new TextView(FixtureActivity.this);
                content.setText("Página de prueba "+labels[i]);
                content.setGravity(Gravity.CENTER); content.setTextColor(0xff111111);
                content.setOnClickListener(v->count("content_taps"));
                content.setOnLongClickListener(v->{count("long_presses");return true;});
                page.addView(content,new FrameLayout.LayoutParams(-1,-1));
                pages[i]=page;
                addView(page,new FrameLayout.LayoutParams(-1,-1));
            }
        }
        @Override public CharSequence getAccessibilityClassName() {
            return "androidx.viewpager.widget.ViewPager";
        }
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info); info.setScrollable(true);
        }
        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) {
            super.onSizeChanged(w,h,oldw,oldh);
            float requested=offset;
            if (!initialHoldApplied && w>0) {
                initialHoldApplied=true;
                requested=-getIntent().getIntExtra("held_offset",0);
            }
            movePages(requested,false);
        }
        void movePages(float requested,boolean announce) {
            int width=getWidth();
            float next=Math.max(selectedTab<3 ? -width : 0,
                    Math.min(selectedTab>0 ? width : 0,requested));
            boolean changed=offset!=next; offset=next;
            int incoming=selectedTab+(offset<0 ? 1 : offset>0 ? -1 : 0);
            for (int i=0;i<pages.length;i++) {
                pages[i].setTranslationX((i-selectedTab)*width+offset);
                pages[i].setVisibility(i==selectedTab || i==incoming ? View.VISIBLE : View.INVISIBLE);
            }
            stats.edit().putInt("pager_offset",Math.round(offset))
                    .putInt("pager_incoming",incoming).putInt("pager_width",width).apply();
            if (announce && changed) {
                AccessibilityEvent event=AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_SCROLLED);
                event.setSource(this); event.setClassName(getAccessibilityClassName());
                event.setPackageName(getPackageName()); event.setScrollable(true);
                event.setScrollX(Math.round(selectedTab*width-offset)); event.setMaxScrollX(3*width);
                event.setItemCount(4); event.setFromIndex(selectedTab); event.setToIndex(incoming);
                if (getParent()!=null) getParent().requestSendAccessibilityEvent(this,event);
                else event.recycle();
            }
        }
        void release(boolean cancel) {
            int destination=selectedTab;
            if (!cancel && Math.abs(offset)>=getWidth()/2f && offset!=0)
                destination+=offset<0 ? 1 : -1;
            selectTab(destination);
            sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
        }
    }

    private final class TestLayout extends LinearLayout {
        private float startX,startY;
        private float pagerStartOffset;
        private boolean horizontal,vertical;
        TestLayout() { super(FixtureActivity.this); }
        @Override public boolean onInterceptTouchEvent(MotionEvent e) {
            if (!navigation) return false;
            if (e.getActionMasked()==MotionEvent.ACTION_DOWN) {
                startX=e.getX(); startY=e.getY(); horizontal=false; vertical=false;
                pagerStartOffset=pager==null ? 0 : pager.offset;
            } else if (e.getActionMasked()==MotionEvent.ACTION_MOVE) {
                float dx=Math.abs(e.getX()-startX),dy=Math.abs(e.getY()-startY);
                int slop=ViewConfiguration.get(FixtureActivity.this).getScaledTouchSlop();
                if (dx>slop && dx>=dy && !vertical) { horizontal=true; return true; }
                if (dy>slop && dy>dx) { vertical=true; return true; }
            }
            return false;
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if (horizontal && pager!=null && e.getActionMasked()==MotionEvent.ACTION_MOVE)
                pager.movePages(pagerStartOffset+e.getX()-startX,true);
            if (e.getActionMasked()==MotionEvent.ACTION_UP) {
                if (horizontal) {
                    count("horizontal_swipes");
                    if (pager!=null) {
                        pager.movePages(pagerStartOffset+e.getX()-startX,true);
                        pager.release(false);
                    } else selectTab(selectedTab+(e.getX()<startX ? 1 : -1));
                }
                if (vertical) count("vertical_scrolls");
            }
            if (e.getActionMasked()==MotionEvent.ACTION_CANCEL && horizontal && pager!=null)
                pager.release(true);
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
