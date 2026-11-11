package fr.netscope.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiInfo;
import android.text.format.Formatter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

public class MainActivity extends Activity {
    private final int BG = 0xFF080D12, PANEL = 0xFF101A22;
    private final int GREEN = 0xFF00FF9C, BLUE = 0xFF00C8FF, AMBER = 0xFFFFB547;
    private TextView connection, ipText, wifiText, mobileText, latencyText, logText;
    private LinearLayout content;
    private Handler handler = new Handler(Looper.getMainLooper());
    private long lastRx = -1, lastTx = -1;
    private long lastTime = 0;
    private GaugeView networkGauge, wifiGauge, mobileGauge;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        buildUi();
        refreshInfo();
        handler.post(ticker);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(24));
        scroll.addView(content);
        setContentView(scroll);

        TextView title = text("NETSCOPE", 30, GREEN, true);
        content.addView(title);
        content.addView(text("NETWORK OPERATIONS // ANDROID", 11, BLUE, true));
        content.addView(space(16));

        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.HORIZONTAL);
        String[] sections = {"DASHBOARD", "RESEAU", "WI-FI", "MOBILE", "DIAGNOSTIC"};
        for (String s : sections) {
            Button b = new Button(this);
            b.setText(s);
            b.setTextSize(10);
            b.setTextColor(GREEN);
            b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PANEL));
            menu.addView(b, new LinearLayout.LayoutParams(-2, dp(44)));
            b.setOnClickListener(v -> log("Section sélectionnée : " + s));
        }
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.addView(menu);
        content.addView(hsv);
        content.addView(space(14));

        connection = text("CONNEXION : vérification…", 14, GREEN, true);
        content.addView(card(connection));

        LinearLayout gauges = new LinearLayout(this);
        gauges.setOrientation(LinearLayout.HORIZONTAL);
        networkGauge = new GaugeView(this, "DÉBIT", "Mbps", GREEN);
        wifiGauge = new GaugeView(this, "WI-FI", "dBm", BLUE);
        mobileGauge = new GaugeView(this, "MOBILE", "dBm", AMBER);
        gauges.addView(networkGauge, new LinearLayout.LayoutParams(0, dp(160), 1));
        gauges.addView(wifiGauge, new LinearLayout.LayoutParams(0, dp(160), 1));
        gauges.addView(mobileGauge, new LinearLayout.LayoutParams(0, dp(160), 1));
        content.addView(gauges);

        content.addView(space(8));
        content.addView(section("INFORMATIONS RÉSEAU"));
        ipText = text("IP locale : —", 13, 0xFFE6F1F5, false);
        wifiText = text("Wi-Fi : non connecté", 13, 0xFFE6F1F5, false);
        mobileText = text("Réseau mobile : informations limitées", 13, 0xFFE6F1F5, false);
        latencyText = text("Latence : —", 13, 0xFFE6F1F5, false);
        content.addView(card(ipText, wifiText, mobileText, latencyText));

        content.addView(section("OUTILS"));
        addAction("ACTUALISER LES MESURES", this::refreshInfo);
        addAction("TESTER LA LATENCE", this::testLatency);
        addAction("DÉCOUVERTE DES SERVICES LOCAUX", this::discoverNote);
        addAction("JOURNAL SYSTÈME", () -> log("NetScope fonctionne sur Android."));

        content.addView(section("JOURNAL"));
        logText = text("Initialisation terminée.", 11, GREEN, false);
        logText.setPadding(dp(12), dp(12), dp(12), dp(12));
        logText.setBackgroundColor(0xFF05090D);
        content.addView(logText);
        content.addView(space(12));
        content.addView(text("NetScope 1.0 • Les mesures dépendent des autorisations Android.", 10, 0xFF8195A3, false));
    }

    private void refreshInfo() {
        try {
            ConnectivityManager cm = (ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
            Network n = cm.getActiveNetwork();
            NetworkCapabilities caps = n == null ? null : cm.getNetworkCapabilities(n);
            boolean wifi = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
            boolean cellular = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
            connection.setText("CONNEXION : " + (wifi ? "WI-FI" : cellular ? "DONNÉES MOBILES" : "AUTRE / HORS LIGNE"));
            String ip = getLocalIp();
            ipText.setText("IP locale : " + ip);
            WifiManager wm = (WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wifi && wm != null) {
                WifiInfo info = wm.getConnectionInfo();
                int rssi = info.getRssi();
                wifiText.setText("Wi-Fi : connecté • signal " + rssi + " dBm");
                wifiGauge.setValue(rssi, -100, -30, rssi + "");
            } else {
                wifiText.setText("Wi-Fi : non connecté");
                wifiGauge.setValue(0, 0, 100, "—");
            }
            mobileText.setText(cellular ? "Réseau mobile : connecté (niveau radio non disponible ici)" :
                    "Réseau mobile : non utilisé pour la connexion actuelle");
            mobileGauge.setValue(0, 0, 100, "N/D");
            long now = System.currentTimeMillis();
            long rx = android.net.TrafficStats.getTotalRxBytes();
            long tx = android.net.TrafficStats.getTotalTxBytes();
            if (lastTime > 0 && now > lastTime) {
                double mbps = ((rx - lastRx) + (tx - lastTx)) * 8.0 / ((now - lastTime) * 1000.0);
                networkGauge.setValue((float)mbps, 0, 100, String.format(java.util.Locale.US, "%.1f", mbps));
            } else networkGauge.setValue(0, 0, 100, "0.0");
            lastRx = rx; lastTx = tx; lastTime = now;
            log("Mesures actualisées.");
        } catch (Exception e) {
            log("Mesure partielle : " + e.getMessage());
        }
    }

    private String getLocalIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (java.net.InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress a = ia.getAddress();
                    if (!a.isLoopbackAddress() && a instanceof java.net.Inet4Address)
                        return a.getHostAddress();
                }
            }
        } catch (Exception ignored) {}
        return "indisponible";
    }

    private void testLatency() {
        latencyText.setText("Latence : test en cours…");
        new Thread(() -> {
            long start = System.nanoTime();
            String result;
            try {
                boolean ok = InetAddress.getByName("1.1.1.1").isReachable(2500);
                long ms = (System.nanoTime() - start) / 1_000_000;
                result = ok ? ms + " ms" : "pas de réponse ICMP (filtrage possible)";
            } catch (Exception e) { result = "échec : " + e.getMessage(); }
            final String r = result;
            runOnUiThread(() -> { latencyText.setText("Latence : " + r); log("Test de latence : " + r); });
        }).start();
    }

    private void discoverNote() {
        new android.app.AlertDialog.Builder(this)
            .setTitle("Découverte du réseau")
            .setMessage("La découverte des appareils dépend du Wi-Fi utilisé et des protections Android/du routeur. Cette première version n'effectue pas de scan automatique des autres appareils. Une prochaine version pourra ajouter une découverte locale autorisée.")
            .setPositiveButton("Compris", null).show();
    }

    private void addAction(String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(GREEN);
        b.setTextSize(12);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PANEL));
        b.setOnClickListener(v -> action.run());
        content.addView(b, new LinearLayout.LayoutParams(-1, dp(50)));
    }

    private TextView section(String s) {
        TextView t = text("> " + s, 13, BLUE, true);
        t.setPadding(0, dp(16), 0, dp(8));
        return t;
    }

    private LinearLayout card(View... views) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(10), dp(12), dp(10));
        box.setBackgroundColor(PANEL);
        for (View v : views) box.addView(v);
        return box;
    }

    private TextView text(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(android.graphics.Typeface.MONOSPACE, 1);
        else t.setTypeface(android.graphics.Typeface.MONOSPACE);
        return t;
    }

    private View space(int h) { View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h))); return v; }
    private int dp(int x) { return (int)(x * getResources().getDisplayMetrics().density + 0.5f); }
    private void log(String s) {
        if (logText != null) logText.append("\n> " + s);
    }

    private final Runnable ticker = new Runnable() {
        @Override public void run() { refreshInfo(); handler.postDelayed(this, 3000); }
    };

    @Override protected void onDestroy() {
        handler.removeCallbacks(ticker);
        super.onDestroy();
    }

    class GaugeView extends View {
        Paint p = new Paint(3);
        String label, unit, valueText = "—";
        int color;
        float value = 0, min = 0, max = 100;
        RectF oval = new RectF();
        GaugeView(Context c, String l, String u, int col) {
            super(c); label=l; unit=u; color=col;
        }
        void setValue(float v, float mn, float mx, String txt) {
            value=v; min=mn; max=mx; valueText=txt; invalidate();
        }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w=getWidth(), h=getHeight(), cx=w/2, cy=h*0.65f;
            float r=Math.min(w*0.39f, h*0.48f);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(9)); p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(0xFF263844);
            oval.set(cx-r, cy-r, cx+r, cy+r);
            c.drawArc(oval, 135, 270, false, p);
            float fraction = max > min ? Math.max(0, Math.min(1, (value-min)/(max-min))) : 0;
            p.setColor(color);
            c.drawArc(oval, 135, 270*fraction, false, p);
            p.setStyle(Paint.Style.FILL); p.setColor(color); p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(dp(11)); c.drawText(label, cx, dp(18), p);
            p.setTextSize(dp(20)); p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            c.drawText(valueText, cx, cy+dp(4), p);
            p.setTextSize(dp(9)); p.setTypeface(android.graphics.Typeface.DEFAULT);
            c.drawText(unit, cx, cy+dp(21), p);
        }
    }
}
