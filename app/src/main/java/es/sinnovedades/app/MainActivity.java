package es.sinnovedades.app;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private SharedPreferences prefs;
    private TextView status,colorLabel,lastResult;
    private Button permission;
    private int foreground,muted,accent;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refreshStatus=new Runnable() {
        public void run() { refresh(); handler.postDelayed(this,1000); }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs=getSharedPreferences(CoverService.PREFS,MODE_PRIVATE);
        boolean dark=(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        foreground=Color.parseColor(dark?"#EFF6F3":"#132B25");
        muted=Color.parseColor(dark?"#B7C9C2":"#52685F");
        accent=Color.parseColor(dark?"#65D6AE":"#147D64");
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.parseColor(dark?"#101B17":"#F5F8F5"));
        LinearLayout layout=new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24),dp(28),dp(24),dp(24));
        scroll.addView(layout);
        if (Build.VERSION.SDK_INT>=30) {
            getWindow().setDecorFitsSystemWindows(false);
            scroll.setOnApplyWindowInsetsListener((v,insets)->{
                android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                v.setPadding(i.left,i.top,i.right,i.bottom); return insets;
            });
        }
        setContentView(scroll);
        TextView badge=text("WHATSAPP · SIN DISTRACCIONES",12,accent);
        badge.setLetterSpacing(0.1f); layout.addView(badge);
        TextView title=text("Sin Novedades",32,foreground);
        title.setTypeface(null,Typeface.BOLD); margin(layout,title,12);
        margin(layout,text("Oculta el botón y bloquea sus pulsaciones.",18,muted),8);

        status=text("",16,foreground);
        status.setPadding(dp(18),dp(16),dp(18),dp(16));
        GradientDrawable card=new GradientDrawable();
        card.setColor(Color.parseColor(dark?"#1D3329":"#E2F0E7")); card.setCornerRadius(dp(16));
        status.setBackground(card); margin(layout,status,26);

        Switch enabled=new Switch(this);
        enabled.setText("Cubrir y bloquear el botón"); enabled.setTextSize(16); enabled.setTextColor(foreground);
        enabled.setMinHeight(dp(60)); enabled.setChecked(prefs.getBoolean("enabled",true));
        enabled.setOnCheckedChangeListener((v,value)->{ prefs.edit().putBoolean("enabled",value).apply(); refresh(); });
        margin(layout,enabled,16);
        margin(layout,text("La cubierta aparece únicamente cuando se reconoce la barra inferior de WhatsApp. No cambia de pestaña automáticamente.",14,muted),2);

        permission=button("Activar accesibilidad",v->openPermission()); margin(layout,permission,24);
        margin(layout,button("Abrir WhatsApp",v->openWhatsApp()),8);

        TextView check=text("Última comprobación en WhatsApp",18,foreground);
        check.setTypeface(null,Typeface.BOLD); margin(layout,check,26);
        lastResult=text("",14,muted); margin(layout,lastResult,8);
        margin(layout,button("Copiar diagnóstico",v->copyDiagnostics()),8);
        margin(layout,text("Si no funciona: abre WhatsApp con la barra inferior visible, vuelve aquí y copia el diagnóstico. No incluye mensajes ni capturas.",14,muted),6);

        TextView appearance=text("Aspecto de la cubierta",18,foreground);
        appearance.setTypeface(null,Typeface.BOLD); margin(layout,appearance,30);
        colorLabel=text("",14,muted); margin(layout,colorLabel,8);
        margin(layout,button("Elegir color",v->chooseColor()),8);
        margin(layout,text("Elige el mismo fondo que tiene tu barra de WhatsApp para que el botón quede disimulado.",14,muted),6);

        TextView privacy=text("Privacidad",18,foreground);
        privacy.setTypeface(null,Typeface.BOLD); margin(layout,privacy,28);
        margin(layout,text("Accesibilidad concede acceso al contenido visible. Esta app busca la barra de WhatsApp en tu móvil y comprueba cuándo sales de la aplicación para retirar la cubierta. No guarda mensajes, no tiene permiso de Internet y no pulsa controles por ti.",14,muted),8);
        margin(layout,button("Ayuda de instalación",v->help()),16);
        margin(layout,text("Versión 0.2.0 · Android 8 o posterior\nPuede requerir ajustes si WhatsApp cambia su interfaz.",12,muted),22);
        refresh();
    }

    @Override protected void onResume() {
        super.onResume(); handler.removeCallbacks(refreshStatus); handler.post(refreshStatus);
    }
    @Override protected void onPause() { handler.removeCallbacks(refreshStatus); super.onPause(); }

    private boolean serviceEnabled() {
        AccessibilityManager manager=(AccessibilityManager)getSystemService(ACCESSIBILITY_SERVICE);
        ComponentName wanted=new ComponentName(this,CoverService.class);
        for (AccessibilityServiceInfo info:manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            ComponentName found=ComponentName.unflattenFromString(info.getId());
            if (wanted.equals(found)) return true;
        }
        return false;
    }

    private void refresh() {
        boolean granted=serviceEnabled(),on=prefs.getBoolean("enabled",true);
        setText(status,!on ? "Protección en pausa" : !granted ? "Falta activar el permiso de accesibilidad"
            : !ServiceStatus.connected ? "Permiso concedido. Esperando a que Android conecte el servicio."
            : "Servicio conectado.\nAbre WhatsApp para comprobar la cubierta.");
        setText(permission,granted ? "Gestionar accesibilidad" : "Activar accesibilidad");
        SharedPreferences saved=ServiceStatus.saved(this);
        long when=saved.getLong("time",0);
        setText(lastResult,when==0 ? "Todavía no se ha detectado una ventana de WhatsApp."
            : saved.getString("summary","")+"\n"+android.text.format.DateFormat.getDateFormat(this).format(new java.util.Date(when))
                +" · "+android.text.format.DateFormat.getTimeFormat(this).format(new java.util.Date(when)));
        String c=prefs.getString("color","auto");
        setText(colorLabel,c.equals("auto")?"Según el tema del móvil":c.equals("light")?"Fondo claro":c.equals("dark")?"Fondo oscuro":"Color personalizado: "+c);
    }

    private static void setText(TextView view,String value) {
        if (!value.contentEquals(view.getText())) view.setText(value);
    }

    private String version(String pkg) {
        try { return getPackageManager().getPackageInfo(pkg,0).versionName; }
        catch (android.content.pm.PackageManager.NameNotFoundException e) { return "no instalado"; }
    }

    private void copyDiagnostics() {
        long elapsed=ServiceStatus.lastInspection==0 ? -1 : (SystemClock.elapsedRealtime()-ServiceStatus.lastInspection)/1000;
        SharedPreferences saved=ServiceStatus.saved(this);
        String report="Sin Novedades "+version(getPackageName())
            +"\nAndroid "+Build.VERSION.RELEASE+" (API "+Build.VERSION.SDK_INT+")"
            +"\nModelo: "+Build.MANUFACTURER+" "+Build.MODEL
            +"\nWhatsApp: "+version("com.whatsapp")+"; Business: "+version("com.whatsapp.w4b")
            +"\nPermiso: "+serviceEnabled()+"; interruptor: "+prefs.getBoolean("enabled",true)
            +"\nServicio conectado: "+ServiceStatus.connected+"; última inspección hace "+elapsed+" s"
            +"\nEstado actual: "+ServiceStatus.current
            +"\nEventos de WhatsApp: "+ServiceStatus.whatsappEvents
            +"\nÚltima comprobación de WhatsApp (fecha): "+saved.getLong("time",0)
            +"\n"+saved.getString("report","Todavía no hay ninguna comprobación de WhatsApp.");
        ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("Diagnóstico de Sin Novedades",report));
        Toast.makeText(this,"Diagnóstico copiado. Puedes pegarlo en la conversación.",Toast.LENGTH_LONG).show();
    }

    private void openPermission() {
        Runnable open=()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        if (serviceEnabled() || prefs.getBoolean("disclosure",false)) { open.run(); return; }
        new AlertDialog.Builder(this).setTitle("Activar Sin Novedades")
            .setMessage("Android pedirá permiso para ver el contenido de la pantalla. Sin Novedades lo utiliza para localizar y cubrir el botón de WhatsApp. Todo se procesa en tu móvil; no se guardan mensajes ni se envían datos.\n\nEn la siguiente pantalla busca Sin Novedades en las aplicaciones o servicios instalados y actívalo.")
            .setNegativeButton("Ahora no",null).setPositiveButton("Continuar",(d,w)->{
                prefs.edit().putBoolean("disclosure",true).apply(); open.run();
            }).show();
    }

    private void openWhatsApp() {
        Intent intent=getPackageManager().getLaunchIntentForPackage("com.whatsapp");
        if (intent==null) intent=getPackageManager().getLaunchIntentForPackage("com.whatsapp.w4b");
        if (intent!=null) startActivity(intent);
        else Toast.makeText(this,"No se encuentra WhatsApp instalado.",Toast.LENGTH_LONG).show();
    }

    private void chooseColor() {
        String[] labels={"Según el tema del móvil","Claro","Oscuro","Color personalizado"};
        new AlertDialog.Builder(this).setTitle("Fondo de la cubierta").setItems(labels,(dialog,index)->{
            if (index==3) { customColor(); return; }
            prefs.edit().putString("color",new String[]{"auto","light","dark"}[index]).apply(); refresh();
        }).show();
    }

    private void customColor() {
        EditText input=new EditText(this);
        String saved=prefs.getString("color","auto");
        input.setText(saved.startsWith("#")?saved:"#0B1014"); input.setSingleLine(true);
        input.setPadding(dp(24),dp(16),dp(24),dp(16));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Color hexadecimal")
            .setMessage("Introduce seis cifras de color, por ejemplo #FFFFFF.")
            .setView(input).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String value=input.getText().toString().trim();
            if (!value.matches("#[0-9a-fA-F]{6}")) { input.setError("Usa el formato #RRGGBB"); return; }
            prefs.edit().putString("color",value.toUpperCase(java.util.Locale.ROOT)).apply(); refresh(); dialog.dismiss();
        })); dialog.show();
    }

    private void help() {
        new AlertDialog.Builder(this).setTitle("Cómo activarlo")
            .setMessage("1. Activa Sin Novedades en Ajustes > Accesibilidad > Aplicaciones o servicios instalados.\n\n2. Si Android muestra Ajustes restringidos, abre Ajustes > Aplicaciones > Sin Novedades > menú ⋮ > Permitir ajustes restringidos. Después vuelve a Accesibilidad.\n\n3. Abre WhatsApp en la pestaña Chats. Novedades debería quedar cubierta y sus toques bloqueados.\n\nSi ya tiene permiso y no aparece la cubierta, abre WhatsApp con la barra inferior visible durante unos segundos. Vuelve a Sin Novedades y pulsa Copiar diagnóstico para comunicar el resultado.\n\nReconoce etiquetas en español e inglés. No bloquea enlaces directos a estados o canales ni accesos mediante otros servicios de accesibilidad.\n\nPuedes pausar la protección con el interruptor o desactivar el servicio en Ajustes.")
            .setPositiveButton("Entendido",null).show();
    }

    private Button button(String label,View.OnClickListener listener) {
        Button b=new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(15);
        b.setMinHeight(dp(52)); b.setOnClickListener(listener); return b;
    }
    private TextView text(String s,int size,int color) {
        TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color);
        t.setLineSpacing(dp(3),1f); return t;
    }
    private void margin(LinearLayout p,View v,int top) {
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.topMargin=dp(top); p.addView(v,lp);
    }
    private int dp(float v) { return Math.round(v*getResources().getDisplayMetrics().density); }
}
