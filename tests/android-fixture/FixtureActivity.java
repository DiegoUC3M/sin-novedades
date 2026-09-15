package fixture;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
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
    private final Runnable storm=new Runnable() {
        public void run() { state.setText("Evento de prueba "+(++events)); handler.postDelayed(this,15); }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        stats=getSharedPreferences("fixture",MODE_PRIVATE);
        showNavigation();
    }

    private void base(String title) {
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
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
        base("Barra sintética para verificar la cubierta");
        stats.edit().putString("screen","navigation").apply();
        Button chat=new Button(this); chat.setText("Entrar en un chat de prueba");
        chat.setOnClickListener(v->showChat()); root.addView(chat); remember(chat,"chat_center");
        spacer();
        LinearLayout bar=new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        // Old versions that omit INCLUDE_NOT_IMPORTANT_VIEWS lose this container.
        bar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        String[] labels={"Chats","Novedades","Comunidades","Llamadas"};
        for (int index=0;index<labels.length;index++) {
            final int tab=index;
            TextView item=new TextView(this); item.setText(labels[index]); item.setTextSize(14);
            item.setTextColor(0xff111111); item.setGravity(Gravity.CENTER);
            item.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            item.setOnClickListener(v->{
                String key="taps_"+tab;
                stats.edit().putInt(key,stats.getInt(key,0)+1).apply();
                state.setText("Pulsada: "+labels[tab]);
            });
            bar.addView(item,new LinearLayout.LayoutParams(0,80,1));
            remember(item,"tab_"+tab+"_center");
        }
        root.addView(bar,new LinearLayout.LayoutParams(-1,80));
        if (getIntent().getBooleanExtra("storm",false)) handler.post(storm);
    }

    private void showChat() {
        handler.removeCallbacks(storm);
        base("Conversación sintética");
        stats.edit().putString("screen","chat").apply();
        Button back=new Button(this); back.setText("Volver a la barra");
        back.setOnClickListener(v->showNavigation()); root.addView(back);
        spacer();
        EditText input=new EditText(this); input.setHint("Escribe aquí");
        root.addView(input,new LinearLayout.LayoutParams(-1,64));
    }

    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }
}
