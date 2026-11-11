
package fr.netscope.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiInfo;
import android.telephony.TelephonyManager;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.CellSignalStrength;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.widget.*;

import java.net.NetworkInterface;
import java.net.InetAddress;
import java.util.Collections;
import java.util.Locale;

public class MainActivity extends Activity {

    private final int BG = 0xFF080D12;
    private final int PANEL = 0xFF101A22;

    private final int GREEN = 0xFF00FF9C;
    private final int BLUE = 0xFF00C8FF;
    private final int AMBER = 0xFFFFB547;

    private TextView connection;
    private TextView ipText;
    private TextView wifiText;
    private TextView mobileText;
    private TextView latencyText;
    private TextView logText;

    private LinearLayout content;

    private Handler handler = new Handler(Looper.getMainLooper());

    private long lastRx = -1;
    private long lastTx = -1;
    private long lastTime = 0;

    private GaugeView networkGauge;
    private GaugeView wifiGauge;
    private GaugeView mobileGauge;

    private TelephonyManager telephonyManager;
    private PhoneStateListener signalListener;

    private static final int PHONE_PERMISSION_CODE = 100;

    // INITIALISATION
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        buildUi();
    }

    // REPRISE DE L'APPLICATION
    @Override
    protected void onResume() {
        super.onResume();

        handler.removeCallbacks(ticker);
        refreshInfo();
        handler.postDelayed(ticker, 1000);

        startMobileMonitoring();
    }

    // MISE EN ARRIERE-PLAN
    @Override
    protected void onPause() {
        handler.removeCallbacks(ticker);
        stopMobileMonitoring();

        super.onPause();
    }

    // CONSTRUCTION DE L'INTERFACE
    private void buildUi() {

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(24));

        scroll.addView(content);
        setContentView(scroll);

        TextView title = text("AURORA X", 30, GREEN, true);
        content.addView(title);

        content.addView(text(
                "NETWORK OPERATIONS // ANDROID",
                11, BLUE, true
        ));

        content.addView(space(16));

        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.HORIZONTAL);

        String[] sections = {
                "DASHBOARD",
                "RESEAU",
                "WI-FI",
                "MOBILE",
                "DIAGNOSTIC"
        };

        for (String s : sections) {

            Button b = new Button(this);
            b.setText(s);
            b.setTextSize(10);
            b.setTextColor(GREEN);

            b.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(PANEL)
            );

            menu.addView(
                    b,
                    new LinearLayout.LayoutParams(-2, dp(44))
            );

            b.setOnClickListener(v ->
                    log("Section sélectionnée : " + s)
            );
        }

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.addView(menu);
        content.addView(hsv);

        content.addView(space(14));

        connection = text(
                "CONNEXION : vérification...",
                14, GREEN, true
        );

        content.addView(card(connection));

        LinearLayout gauges = new LinearLayout(this);
        gauges.setOrientation(LinearLayout.HORIZONTAL);

        networkGauge = new GaugeView(this, "DEBIT", "Mbps", GREEN);
        wifiGauge = new GaugeView(this, "WI-FI", "dBm", BLUE);
        mobileGauge = new GaugeView(this, "MOBILE", "dBm", AMBER);

        gauges.addView(
                networkGauge,
                new LinearLayout.LayoutParams(0, dp(160), 1)
        );

        gauges.addView(
                wifiGauge,
                new LinearLayout.LayoutParams(0, dp(160), 1)
        );

        gauges.addView(
                mobileGauge,
                new LinearLayout.LayoutParams(0, dp(160), 1)
        );

        content.addView(gauges);

        content.addView(space(8));
        content.addView(section("INFORMATIONS RESEAU"));

        ipText = text("IP locale : —", 13, 0xFFE6F1F5, false);

        wifiText = text(
                "Wi-Fi : non connecté",
                13, 0xFFE6F1F5, false
        );

        mobileText = text(
                "Mobile : en attente d'autorisation",
                13, 0xFFE6F1F5, false
        );

        latencyText = text(
                "Latence : —",
                13, 0xFFE6F1F5, false
        );

        content.addView(
                card(ipText, wifiText, mobileText, latencyText)
        );

        content.addView(section("OUTILS"));

        addAction("ACTUALISER LES MESURES", this::refreshInfo);
        addAction("TESTER LA LATENCE", this::testLatency);
        addAction("DECOUVERTE DU RESEAU", this::discoverNote);
        addAction("JOURNAL SYSTEME", () ->
                log("AURORA X fonctionne.")
        );

        content.addView(section("JOURNAL"));

        logText = text(
                "Initialisation terminée.",
                11, GREEN, false
        );

        logText.setPadding(dp(12), dp(12), dp(12), dp(12));
        logText.setBackgroundColor(0xFF05090D);

        content.addView(logText);

        content.addView(space(12));

        content.addView(text(
                "AURORA X 1.0 • Diagnostic réseau Android",
                10, 0xFF8195A3, false
        ));
    }

    // ACTUALISATION DES MESURES
    private void refreshInfo() {

        try {

            ConnectivityManager cm =
                    (ConnectivityManager)
                    getSystemService(CONNECTIVITY_SERVICE);

            Network n = cm.getActiveNetwork();

            NetworkCapabilities caps =
                    n == null ? null : cm.getNetworkCapabilities(n);

            boolean wifi = caps != null &&
                    caps.hasTransport(
                            NetworkCapabilities.TRANSPORT_WIFI
                    );

            boolean cellular = caps != null &&
                    caps.hasTransport(
                            NetworkCapabilities.TRANSPORT_CELLULAR
                    );

            connection.setText(
                    "CONNEXION : " +
                    (wifi ? "WI-FI" :
                    cellular ? "DONNEES MOBILES" :
                    "AUTRE / HORS LIGNE")
            );

            ipText.setText("IP locale : " + getLocalIp());

            WifiManager wm =
                    (WifiManager) getApplicationContext()
                    .getSystemService(WIFI_SERVICE);

            if (wifi && wm != null) {

                WifiInfo info = wm.getConnectionInfo();
                int rssi = info.getRssi();

                wifiText.setText(
                        "Wi-Fi : connecté • " + rssi + " dBm"
                );

                wifiGauge.setValue(
                        rssi, -100, -30, String.valueOf(rssi)
                );

            } else {

                wifiText.setText("Wi-Fi : non connecté");
                wifiGauge.setValue(0, 0, 100, "—");
            }

            long now = System.currentTimeMillis();

            long rx = android.net.TrafficStats.getTotalRxBytes();
            long tx = android.net.TrafficStats.getTotalTxBytes();

            if (lastTime > 0 && now > lastTime &&
                    lastRx >= 0 && lastTx >= 0) {

                double mbps =
                        ((rx - lastRx) + (tx - lastTx)) * 8.0 /
                        ((now - lastTime) * 1000000.0);

                networkGauge.setValue(
                        (float) mbps,
                        0,
                        100,
                        String.format(Locale.US, "%.2f", mbps)
                );

            } else {

                networkGauge.setValue(0, 0, 100, "0.00");
            }

            lastRx = rx;
            lastTx = tx;
            lastTime = now;

        } catch (Exception e) {
            log("Erreur de mesure : " + e.getMessage());
        }
    }

    // SURVEILLANCE DU SIGNAL MOBILE
    private void startMobileMonitoring() {

        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {

            mobileText.setText(
                    "Mobile : autorisation nécessaire"
            );

            requestPermissions(
                    new String[]{
                            Manifest.permission.READ_PHONE_STATE
                    },
                    PHONE_PERMISSION_CODE
            );

            return;
        }

        try {

            telephonyManager = (TelephonyManager)
                    getSystemService(TELEPHONY_SERVICE);

            if (telephonyManager == null) {
                mobileText.setText("Mobile : indisponible");
                return;
            }

            signalListener = new PhoneStateListener() {

                @Override
                public void onSignalStrengthsChanged(
                        SignalStrength signalStrength) {

                    super.onSignalStrengthsChanged(signalStrength);

                    int dbm = Integer.MAX_VALUE;

                    for (CellSignalStrength cell :
                            signalStrength.getCellSignalStrengths()) {

                        if (cell.getDbm() != Integer.MAX_VALUE) {
                            dbm = cell.getDbm();
                            break;
                        }
                    }

                    final int signal = dbm;

                    runOnUiThread(() -> {

                        if (signal != Integer.MAX_VALUE) {

                            mobileGauge.setValue(
                                    signal,
                                    -120,
                                    -60,
                                    String.valueOf(signal)
                            );

                            mobileText.setText(
                                    "Signal mobile : " + signal + " dBm"
                            );

                        } else {

                            mobileText.setText(
                                    "Signal mobile : indisponible"
                            );
                        }
                    });
                }
            };

            telephonyManager.listen(
                    signalListener,
                    PhoneStateListener.LISTEN_SIGNAL_STRENGTHS
            );

            updateMobileNetworkType();

            mobileText.setText(
                    "Mobile : récupération du signal..."
            );

        } catch (SecurityException e) {

            mobileText.setText(
                    "Mobile : autorisation refusée"
            );

        } catch (Exception e) {

            mobileText.setText(
                    "Mobile : erreur de lecture"
            );
        }
    }

    // TYPE DE RESEAU
    private void updateMobileNetworkType() {

        try {

            int type = telephonyManager.getDataNetworkType();

            String name;

            switch (type) {

                case TelephonyManager.NETWORK_TYPE_NR:
                    name = "5G";
                    break;

                case TelephonyManager.NETWORK_TYPE_LTE:
                    name = "4G LTE";
                    break;

                case TelephonyManager.NETWORK_TYPE_HSPA:
                case TelephonyManager.NETWORK_TYPE_HSPA_PLUS:
                case TelephonyManager.NETWORK_TYPE_HSDPA:
                case TelephonyManager.NETWORK_TYPE_HSUPA:
                case TelephonyManager.NETWORK_TYPE_UMTS:
                    name = "3G";
                    break;

                case TelephonyManager.NETWORK_TYPE_EDGE:
                case TelephonyManager.NETWORK_TYPE_GPRS:
                    name = "2G";
                    break;

                default:
                    name = "Réseau inconnu";
                    break;
            }

            mobileText.setText(
                    "Réseau mobile : " + name + " • en attente du signal"
            );

        } catch (Exception e) {

            mobileText.setText(
                    "Réseau mobile : type indisponible"
            );
        }
    }

    // ARRET DE LA SURVEILLANCE
    private void stopMobileMonitoring() {

        if (telephonyManager != null &&
                signalListener != null) {

            telephonyManager.listen(
                    signalListener,
                    PhoneStateListener.LISTEN_NONE
            );
        }

        signalListener = null;
    }

    // REPONSE A LA DEMANDE D'AUTORISATION
    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == PHONE_PERMISSION_CODE) {

            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                startMobileMonitoring();

            } else {

                mobileText.setText(
                        "Mobile : autorisation refusée"
                );
            }
        }
    }

    // ADRESSE IP LOCALE
    private String getLocalIp() {

        try {

            for (NetworkInterface ni :
                    Collections.list(
                            NetworkInterface.getNetworkInterfaces()
                    )) {

                for (java.net.InterfaceAddress ia :
                        ni.getInterfaceAddresses()) {

                    InetAddress a = ia.getAddress();

                    if (!a.isLoopbackAddress() &&
                            a instanceof java.net.Inet4Address) {

                        return a.getHostAddress();
                    }
                }
            }

        } catch (Exception ignored) {
        }

        return "indisponible";
    }

    // TEST DE LATENCE
    private void testLatency() {

        latencyText.setText("Latence : test en cours...");

        new Thread(() -> {

            long start = System.nanoTime();
            String result;

            try {

                boolean ok = InetAddress
                        .getByName("1.1.1.1")
                        .isReachable(2500);

                long ms =
                        (System.nanoTime() - start) / 1000000;

                result = ok
                        ? ms + " ms"
                        : "Pas de réponse (filtrage possible)";

            } catch (Exception e) {

                result = "Échec du test";
            }

            final String r = result;

            runOnUiThread(() -> {

                latencyText.setText("Latence : " + r);
                log("Test de latence : " + r);
            });

        }).start();
    }

    // DECOUVERTE RESEAU
    private void discoverNote() {

        new android.app.AlertDialog.Builder(this)

                .setTitle("Découverte du réseau")

                .setMessage(
                        "La découverte des appareils dépend du réseau " +
                        "et des protections Android et du routeur. " +
                        "Le scan automatique sera ajouté dans une prochaine version."
                )

                .setPositiveButton("Compris", null)

                .show();
    }

    // AJOUT DES BOUTONS
    private void addAction(String label, Runnable action) {

        Button b = new Button(this);

        b.setText(label);
        b.setTextColor(GREEN);
        b.setTextSize(12);

        b.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(PANEL)
        );

        b.setOnClickListener(v -> action.run());

        content.addView(
                b,
                new LinearLayout.LayoutParams(-1, dp(50))
        );
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

        for (View v : views) {
            box.addView(v);
        }

        return box;
    }

    private TextView text(
            String s,
            int size,
            int color,
            boolean bold
    ) {

        TextView t = new TextView(this);

        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);

        if (bold) {
            t.setTypeface(
                    android.graphics.Typeface.MONOSPACE,
                    android.graphics.Typeface.BOLD
            );
        } else {
            t.setTypeface(android.graphics.Typeface.MONOSPACE);
        }

        return t;
    }

    private View space(int h) {

        View v = new View(this);

        v.setLayoutParams(
                new LinearLayout.LayoutParams(1, dp(h))
        );

        return v;
    }

    private int dp(int x) {

        return (int) (
                x * getResources().getDisplayMetrics().density + 0.5f
        );
    }

    // JOURNAL
    private void log(String s) {

        if (logText != null) {
            logText.append("\n> " + s);
        }
    }

    // ACTUALISATION CHAQUE SECONDE
    private final Runnable ticker = new Runnable() {

        @Override
        public void run() {

            refreshInfo();

            handler.postDelayed(this, 1000);
        }
    };

    // FERMETURE
    @Override
    protected void onDestroy() {

        handler.removeCallbacks(ticker);
        stopMobileMonitoring();

        super.onDestroy();
    }

    // COMPTEURS CIRCULAIRES
    class GaugeView extends View {

        Paint p = new Paint(3);

        String label;
        String unit;
        String valueText = "—";

        int color;

        float value = 0;
        float min = 0;
        float max = 100;

        RectF oval = new RectF();

        GaugeView(
                Context c,
                String l,
                String u,
                int col
        ) {

            super(c);

            label = l;
            unit = u;
            color = col;
        }

        void setValue(
                float v,
                float mn,
                float mx,
                String txt
        ) {

            value = v;
            min = mn;
            max = mx;
            valueText = txt;

            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {

            super.onDraw(c);

            float w = getWidth();
            float h = getHeight();

            float cx = w / 2;
            float cy = h * 0.65f;

            float r = Math.min(w * 0.39f, h * 0.48f);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(9));
            p.setStrokeCap(Paint.Cap.ROUND);

            p.setColor(0xFF263844);

            oval.set(
                    cx - r,
                    cy - r,
                    cx + r,
                    cy + r
            );

            c.drawArc(oval, 135, 270, false, p);

            float fraction = max > min
                    ? Math.max(
                            0,
                            Math.min(1, (value - min) / (max - min))
                    )
                    : 0;

            p.setColor(color);

            c.drawArc(
                    oval,
                    135,
                    270 * fraction,
                    false,
                    p
            );

            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            p.setTextAlign(Paint.Align.CENTER);

            p.setTextSize(dp(11));

            c.drawText(label, cx, dp(18), p);

            p.setTextSize(dp(20));
            p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

            c.drawText(valueText, cx, cy + dp(4), p);

            p.setTextSize(dp(9));
            p.setTypeface(android.graphics.Typeface.DEFAULT);

            c.drawText(unit, cx, cy + dp(21), p);
        }
    }
}
 
