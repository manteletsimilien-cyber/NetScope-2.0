
package fr.netscope.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.DhcpInfo;
import android.net.TrafficStats;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.URL;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

public class MainActivity extends Activity {

    // THEME AURORA X
    private final int BG = 0xFF080710;
    private final int PANEL = 0xFF151020;
    private final int PANEL2 = 0xFF201632;
    private final int PURPLE = 0xFF9B5CFF;
    private final int PURPLE_LIGHT = 0xFFBE91FF;
    private final int WHITE = 0xFFF5F0FF;
    private final int GREY = 0xFF9A91AA;
    private final int GREEN = 0xFF65F5B2;
    private final int BLUE = 0xFF64C8FF;
    private final int ORANGE = 0xFFFFB66E;

    private LinearLayout root;
    private LinearLayout page;
    private TextView pageTitle;
    private TextView logText;

    private GaugeView networkGauge;
    private GaugeView wifiGauge;
    private GaugeView mobileGauge;

    private TextView connectionText;
    private TextView ipText;
    private TextView wifiText;
    private TextView mobileText;
    private TextView latencyText;
    private TextView trafficText;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private long lastRx = -1;
    private long lastTx = -1;
    private long lastTime = 0;

    private boolean autoRefresh = true;
    private boolean testRunning = false;

    private final ArrayList<String> history =
            new ArrayList<>();

    private String currentPage = "DASHBOARD";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        buildMainLayout();
        showPage("DASHBOARD");
    }

    @Override
    protected void onResume() {
        super.onResume();

        handler.removeCallbacks(ticker);

        if (autoRefresh) {
            refreshDashboard();
            handler.postDelayed(ticker, 1000);
        }
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(ticker);
        super.onDestroy();
    }

    // INTERFACE PRINCIPALE

    private void buildMainLayout() {

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(18), dp(18), dp(18), dp(12));

        TextView title = text(
                "AURORA X",
                28,
                PURPLE_LIGHT,
                true
        );

        header.addView(title);

        header.addView(text(
                "NETWORK INTELLIGENCE SYSTEM",
                10,
                GREY,
                true
        ));

        root.addView(header);

        HorizontalScrollView menuScroll =
                new HorizontalScrollView(this);

        menuScroll.setHorizontalScrollBarEnabled(false);

        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.HORIZONTAL);
        menu.setPadding(dp(10), 0, dp(10), dp(8));

        String[] sections = {
                "ACCUEIL",
                "RESEAU",
                "WI-FI",
                "SCANNER",
                "DEBIT",
                "DIAGNOSTIC",
                "HISTORIQUE",
                "PARAMETRES"
        };

        String[] pages = {
                "DASHBOARD",
                "RESEAU",
                "WIFI",
                "SCANNER",
                "DEBIT",
                "DIAGNOSTIC",
                "HISTORIQUE",
                "PARAMETRES"
        };

        for (int i = 0; i < sections.length; i++) {

            final String destination = pages[i];

            Button b = new Button(this);
            b.setText(sections[i]);
            b.setTextSize(10);
            b.setTextColor(PURPLE_LIGHT);
            b.setAllCaps(false);

            b.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(PANEL2)
            );

            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(
                            -2,
                            dp(42)
                    );

            params.setMargins(dp(3), 0, dp(3), 0);

            menu.addView(b, params);

            b.setOnClickListener(v ->
                    showPage(destination)
            );
        }

        menuScroll.addView(menu);
        root.addView(menuScroll);

        View line = new View(this);
        line.setBackgroundColor(0xFF38264F);

        root.addView(
                line,
                new LinearLayout.LayoutParams(-1, dp(1))
        );

        pageTitle = text(
                "",
                16,
                PURPLE_LIGHT,
                true
        );

        pageTitle.setPadding(
                dp(18), dp(12), dp(18), dp(8)
        );

        root.addView(pageTitle);

        ScrollView scroll = new ScrollView(this);

        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(
                dp(16), dp(8), dp(16), dp(25)
        );

        scroll.addView(page);

        root.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        -1, 0, 1
                )
        );
    }

    // NAVIGATION

    private void showPage(String destination) {

        currentPage = destination;

        page.removeAllViews();

        switch (destination) {

            case "DASHBOARD":
                pageTitle.setText("TABLEAU DE BORD");
                buildDashboard();
                break;

            case "RESEAU":
                pageTitle.setText("INFORMATIONS RESEAU");
                buildNetworkPage();
                break;

            case "WIFI":
                pageTitle.setText("ANALYSE WI-FI");
                buildWifiPage();
                break;

            case "SCANNER":
                pageTitle.setText("DECOUVERTE RESEAU");
                buildScannerPage();
                break;

            case "DEBIT":
                pageTitle.setText("TEST DE DEBIT");
                buildSpeedPage();
                break;

            case "DIAGNOSTIC":
                pageTitle.setText("DIAGNOSTIC SYSTEME");
                buildDiagnosticPage();
                break;

            case "HISTORIQUE":
                pageTitle.setText("HISTORIQUE");
                buildHistoryPage();
                break;

            case "PARAMETRES":
                pageTitle.setText("PARAMETRES");
                buildSettingsPage();
                break;
        }
    }

    // TABLEAU DE BORD

    private void buildDashboard() {

        connectionText = text(
                "CONNEXION : VERIFICATION...",
                13, GREEN, true
        );

        addCard(connectionText);

        LinearLayout gauges = new LinearLayout(this);
        gauges.setOrientation(LinearLayout.HORIZONTAL);

        networkGauge = new GaugeView(
                this, "DEBIT", "Mbps", PURPLE
        );

        wifiGauge = new GaugeView(
                this, "WI-FI", "dBm", BLUE
        );

        mobileGauge = new GaugeView(
                this, "MOBILE", "dBm", ORANGE
        );

        gauges.addView(
                networkGauge,
                new LinearLayout.LayoutParams(0, dp(155), 1)
        );

        gauges.addView(
                wifiGauge,
                new LinearLayout.LayoutParams(0, dp(155), 1)
        );

        gauges.addView(
                mobileGauge,
                new LinearLayout.LayoutParams(0, dp(155), 1)
        );

        page.addView(gauges);

        addSection("INFORMATIONS EN DIRECT");

        ipText = text("IP locale : —", 12, WHITE, false);
        wifiText = text("Wi-Fi : —", 12, WHITE, false);
        mobileText = text(
                "Mobile : non disponible",
                12, GREY, false
        );

        latencyText = text("Latence : —", 12, WHITE, false);
        trafficText = text("Trafic : —", 12, WHITE, false);

        addCard(
                ipText,
                wifiText,
                mobileText,
                latencyText,
                trafficText
        );

        addSection("ACTIONS RAPIDES");

        addButton("ACTUALISER", this::refreshDashboard);
        addButton("TEST DE LATENCE", this::testLatency);
        addButton("ANALYSER LE WI-FI",
                () -> showPage("WIFI"));
        addButton("DIAGNOSTIC RESEAU",
                () -> showPage("DIAGNOSTIC"));

        addSection("JOURNAL SYSTEME");

        logText = text(
                "AURORA X initialisé.",
                11, GREEN, false
        );

        addCard(logText);

        refreshDashboard();
    }

    // ACTUALISATION

    private void refreshDashboard() {

        try {

            ConnectivityManager cm =
                    (ConnectivityManager)
                    getSystemService(CONNECTIVITY_SERVICE);

            Network network = cm.getActiveNetwork();

            NetworkCapabilities caps =
                    network == null
                            ? null
                            : cm.getNetworkCapabilities(network);

            boolean wifi = caps != null &&
                    caps.hasTransport(
                            NetworkCapabilities.TRANSPORT_WIFI
                    );

            boolean cellular = caps != null &&
                    caps.hasTransport(
                            NetworkCapabilities.TRANSPORT_CELLULAR
                    );

            if (connectionText != null) {

                connectionText.setText(
                        "CONNEXION : " +
                        (wifi ? "WI-FI" :
                        cellular ? "DONNEES MOBILES" :
                        caps != null ? "AUTRE" : "HORS LIGNE")
                );
            }

            String ip = getLocalIp();

            if (ipText != null) {
                ipText.setText("IP locale : " + ip);
            }

            WifiManager wm =
                    (WifiManager) getApplicationContext()
                    .getSystemService(WIFI_SERVICE);

            if (wifi && wm != null) {

                WifiInfo info = wm.getConnectionInfo();

                int rssi = info.getRssi();

                if (wifiGauge != null) {
                    wifiGauge.setValue(
                            rssi, -100, -30,
                            String.valueOf(rssi)
                    );
                }

                if (wifiText != null) {
                    wifiText.setText(
                            "Wi-Fi : connecté • " + rssi + " dBm"
                    );
                }

            } else {

                if (wifiGauge != null) {
                    wifiGauge.setValue(0, 0, 100, "—");
                }

                if (wifiText != null) {
                    wifiText.setText("Wi-Fi : non connecté");
                }
            }

            long now = System.currentTimeMillis();

            long rx = TrafficStats.getTotalRxBytes();
            long tx = TrafficStats.getTotalTxBytes();

            if (rx >= 0 && tx >= 0 &&
                    lastTime > 0 &&
                    now > lastTime &&
                    lastRx >= 0 && lastTx >= 0) {

                double mbps =
                        ((rx - lastRx) + (tx - lastTx)) *
                        8.0 / ((now - lastTime) * 1000000.0);

                if (networkGauge != null) {
                    networkGauge.setValue(
                            (float) mbps,
                            0, 100,
                            String.format(
                                    Locale.US, "%.2f", mbps
                            )
                    );
                }

                if (trafficText != null) {
                    trafficText.setText(
                            String.format(
                                    Locale.US,
                                    "Trafic instantané : %.2f Mbps",
                                    mbps
                            )
                    );
                }
            }

            lastRx = rx;
            lastTx = tx;
            lastTime = now;

            if (mobileGauge != null) {
                mobileGauge.setValue(0, 0, 100, "N/D");
            }

        } catch (Exception e) {
            addLog("Erreur : " + e.getMessage());
        }
    }

    // PAGE RESEAU

    private void buildNetworkPage() {

        addSection("ADRESSES");

        addInfo("Adresse IP locale", getLocalIp());
        addInfo("Passerelle", getGateway());
        addInfo("Nom du réseau", getNetworkType());

        addSection("COMPTEURS");

        addInfo(
                "Données reçues",
                formatBytes(TrafficStats.getTotalRxBytes())
        );

        addInfo(
                "Données envoyées",
                formatBytes(TrafficStats.getTotalTxBytes())
        );

        addSection("TEST");

        addButton("VERIFIER LA CONNEXION",
                this::testLatency);

        addButton("ACTUALISER",
                () -> showPage("RESEAU"));
    }

    // PAGE WI-FI

    private void buildWifiPage() {

        addSection("ANALYSE DU SIGNAL");

        WifiManager wm =
                (WifiManager) getApplicationContext()
                .getSystemService(WIFI_SERVICE);

        if (wm == null) {
            addInfo("Wi-Fi", "Indisponible");
            return;
        }

        WifiInfo info = wm.getConnectionInfo();

        addInfo(
                "Etat",
                info.getNetworkId() == -1
                        ? "Non connecté"
                        : "Connecté"
        );

        addInfo("Puissance", info.getRssi() + " dBm");

        addInfo(
                "Fréquence",
                info.getFrequency() + " MHz"
        );

        addInfo(
                "Vitesse de liaison",
                info.getLinkSpeed() + " Mbps"
        );

        addInfo(
                "Adresse IP",
                getLocalIp()
        );

        addSection("INTERPRETATION");

        int rssi = info.getRssi();

        String quality;

        if (rssi >= -55) {
            quality = "Très bon signal";
        } else if (rssi >= -67) {
            quality = "Bon signal";
        } else if (rssi >= -75) {
            quality = "Signal moyen";
        } else {
            quality = "Signal faible";
        }

        addInfo("Qualité estimée", quality);

        addButton("ACTUALISER",
                () -> showPage("WIFI"));
    }

    // PAGE SCANNER

    private void buildScannerPage() {

        addSection("RESEAU LOCAL");

        addInfo("Adresse locale", getLocalIp());
        addInfo("Passerelle", getGateway());
        addInfo("Type de connexion", getNetworkType());

        addSection("DECOUVERTE");

        addText(
                "Android limite l'accès aux appareils du réseau. " +
                "Cette page affiche les informations locales " +
                "accessibles sans autorisation supplémentaire."
        );

        addButton("ACTUALISER LES INFORMATIONS",
                () -> showPage("SCANNER"));

        addButton("TESTER LA CONNEXION",
                this::testLatency);
    }

    // PAGE TEST DE DEBIT

    private void buildSpeedPage() {

        addSection("TEST INTERNET");

        addText(
                "Le test télécharge un fichier de mesure. " +
                "Il utilise des données mobiles ou Wi-Fi selon " +
                "la connexion active."
        );

        TextView result = text(
                "Débit descendant : —",
                16, PURPLE_LIGHT, true
        );

        addCard(result);

        addButton("LANCER LE TEST",
                () -> runSpeedTest(result));

        addButton("TESTER LA LATENCE",
                this::testLatency);
    }

    private void runSpeedTest(TextView result) {

        if (testRunning) {
            return;
        }

        testRunning = true;

        result.setText("Test en cours...");

        new Thread(() -> {

            long start = System.nanoTime();
            String message;

            HttpURLConnection connection = null;

            try {

                URL url = new URL(
                        "https://speed.cloudflare.com/__down?bytes=5000000"
                );

                connection =
                        (HttpURLConnection) url.openConnection();

                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                connection.setRequestMethod("GET");

                connection.connect();

                long total = 0;

                byte[] buffer = new byte[8192];

                try (java.io.InputStream input =
                             connection.getInputStream()) {

                    int count;

                    while ((count = input.read(buffer)) != -1) {
                        total += count;
                    }
                }

                long elapsed =
                        System.nanoTime() - start;

                double seconds = elapsed / 1000000000.0;

                double mbps =
                        total * 8.0 / seconds / 1000000.0;

                message = String.format(
                        Locale.US,
                        "Débit descendant : %.2f Mbps",
                        mbps
                );

                history.add(message);

            } catch (Exception e) {

                message = "Test impossible : vérifie ta connexion.";
            } finally {

                if (connection != null) {
                    connection.disconnect();
                }
            }

            final String finalMessage = message;

            runOnUiThread(() -> {

                result.setText(finalMessage);
                addLog(finalMessage);
                testRunning = false;
            });

        }).start();
    }

    // PAGE DIAGNOSTIC

    private void buildDiagnosticPage() {

        addSection("CONTROLES");

        addInfo("Connexion", getNetworkType());
        addInfo("Adresse IP", getLocalIp());
        addInfo("Passerelle", getGateway());

        addSection("TESTS DISPONIBLES");

        addButton("TESTER LA LATENCE",
                this::testLatency);

        addButton("VERIFIER LE DNS",
                this::testDns);

        addButton("ACTUALISER",
                () -> showPage("DIAGNOSTIC"));
    }

    private void testDns() {

        addText("Recherche DNS en cours...");

        new Thread(() -> {

            String result;

            try {

                InetAddress[] addresses =
                        InetAddress.getAllByName("example.com");

                result = "DNS opérationnel : " +
                        addresses[0].getHostAddress();

            } catch (Exception e) {

                result = "Echec de la résolution DNS";
            }

            final String r = result;

            runOnUiThread(() -> {
                addText(r);
                addLog(r);
            });

        }).start();
    }

    // TEST LATENCE TCP

    private void testLatency() {

        addLog("Test de latence lancé.");

        if (latencyText != null) {
            latencyText.setText("Latence : test en cours...");
        }

        new Thread(() -> {

            long start = System.nanoTime();
            String result;

            try (Socket socket = new Socket()) {

                socket.connect(
                        new java.net.InetSocketAddress(
                                "1.1.1.1", 443
                        ),
                        3000
                );

                long ms =
                        (System.nanoTime() - start) / 1000000;

                result = ms + " ms (connexion TCP)";

                history.add("Latence : " + result);

            } catch (Exception e) {

                result = "Connexion impossible";
            }

            final String r = result;

            runOnUiThread(() -> {

                if (latencyText != null) {
                    latencyText.setText("Latence : " + r);
                }

                addLog("Latence : " + r);
            });

        }).start();
    }

    // HISTORIQUE

    private void buildHistoryPage() {

        addSection("MESURES ENREGISTREES");

        if (history.isEmpty()) {

            addText("Aucune mesure enregistrée pour le moment.");

        } else {

            for (int i = history.size() - 1; i >= 0; i--) {
                addText("> " + history.get(i));
            }
        }

        addButton("EFFACER L'HISTORIQUE", () -> {

            history.clear();
            showPage("HISTORIQUE");
        });
    }

    // PARAMETRES

    private void buildSettingsPage() {

        addSection("ACTUALISATION");

        addInfo(
                "Fréquence",
                "Toutes les secondes lorsque l'application est ouverte"
        );

        Switch refreshSwitch = new Switch(this);

        refreshSwitch.setText(
                "Actualisation automatique"
        );

        refreshSwitch.setTextColor(WHITE);
        refreshSwitch.setChecked(autoRefresh);

        page.addView(refreshSwitch);

        refreshSwitch.setOnCheckedChangeListener(
                (button, checked) -> {

                    autoRefresh = checked;

                    handler.removeCallbacks(ticker);

                    if (checked && !isFinishing()) {
                        refreshDashboard();
                        handler.postDelayed(ticker, 1000);
                    }

                    addLog(
                            checked
                                    ? "Actualisation activée"
                                    : "Actualisation désactivée"
                    );
                }
        );

        addSection("APPLICATION");

        addInfo("Nom", "AURORA X");
        addInfo("Version", "2.0");
        addInfo("Interface", "Dark / Deep Purple");
        addInfo("Système", "Android");

        addSection("INFORMATIONS");

        addText(
                "Certaines données mobiles peuvent être " +
                "indisponibles selon les restrictions Android."
        );
    }

    // OUTILS D'INTERFACE

    private void addSection(String title) {

        TextView t = text(
                "◆ " + title,
                13,
                PURPLE_LIGHT,
                true
        );

        t.setPadding(0, dp(15), 0, dp(9));

        page.addView(t);
    }

    private void addInfo(String label, String value) {

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackgroundColor(PANEL);

        TextView a = text(
                label,
                10,
                GREY,
                false
        );

        TextView b = text(
                value,
                13,
                WHITE,
                true
        );

        row.addView(a);
        row.addView(b);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, -2);

        params.setMargins(0, dp(3), 0, dp(3));

        page.addView(row, params);
    }

    private void addText(String value) {

        TextView t = text(
                value,
                12,
                WHITE,
                false
        );

        t.setPadding(
                dp(12), dp(10), dp(12), dp(10)
        );

        t.setBackgroundColor(PANEL);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, -2);

        params.setMargins(0, dp(3), 0, dp(3));

        page.addView(t, params);
    }

    private void addCard(View... views) {

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(12), dp(12), dp(12));
        box.setBackgroundColor(PANEL);

        for (View v : views) {
            box.addView(v);
        }

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, -2);

        params.setMargins(0, dp(4), 0, dp(5));

        page.addView(box, params);
    }

    private void addButton(String label, Runnable action) {

        Button b = new Button(this);

        b.setText(label);
        b.setTextSize(11);
        b.setTextColor(PURPLE_LIGHT);
        b.setAllCaps(false);

        b.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(PANEL2)
        );

        b.setOnClickListener(v -> action.run());

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, dp(49));

        params.setMargins(0, dp(4), 0, dp(4));

        page.addView(b, params);
    }

    private TextView text(
            String value,
            int size,
            int color,
            boolean bold
    ) {

        TextView t = new TextView(this);

        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);

        t.setTypeface(
                Typeface.MONOSPACE,
                bold ? Typeface.BOLD : Typeface.NORMAL
        );

        return t;
    }

    private int dp(int value) {

        return (int) (
                value *
                getResources().getDisplayMetrics().density +
                0.5f
        );
    }

    // INFORMATIONS RESEAU

    private String getNetworkType() {

        try {

            ConnectivityManager cm =
                    (ConnectivityManager)
                    getSystemService(CONNECTIVITY_SERVICE);

            Network n = cm.getActiveNetwork();

            NetworkCapabilities caps =
                    n == null ? null :
                    cm.getNetworkCapabilities(n);

            if (caps == null) {
                return "Hors ligne";
            }

            if (caps.hasTransport(
                    NetworkCapabilities.TRANSPORT_WIFI)) {
                return "Wi-Fi";
            }

            if (caps.hasTransport(
                    NetworkCapabilities.TRANSPORT_CELLULAR)) {
                return "Données mobiles";
            }

            if (caps.hasTransport(
                    NetworkCapabilities.TRANSPORT_ETHERNET)) {
                return "Ethernet";
            }

        } catch (Exception ignored) {
        }

        return "Inconnu";
    }

    private String getLocalIp() {

        try {

            for (NetworkInterface ni :
                    Collections.list(
                            NetworkInterface.getNetworkInterfaces()
                    )) {

                for (java.net.InterfaceAddress ia :
                        ni.getInterfaceAddresses()) {

                    InetAddress address = ia.getAddress();

                    if (!address.isLoopbackAddress() &&
                            address instanceof java.net.Inet4Address) {

                        return address.getHostAddress();
                    }
                }
            }

        } catch (Exception ignored) {
        }

        return "Indisponible";
    }

    private String getGateway() {

        try {

            WifiManager wm =
                    (WifiManager) getApplicationContext()
                    .getSystemService(WIFI_SERVICE);

            DhcpInfo info = wm.getDhcpInfo();

            int ip = info.gateway;

            return (ip & 0xFF) + "." +
                    ((ip >> 8) & 0xFF) + "." +
                    ((ip >> 16) & 0xFF) + "." +
                    ((ip >> 24) & 0xFF);

        } catch (Exception e) {

            return "Indisponible";
        }
    }

    private String formatBytes(long bytes) {

        if (bytes < 0) {
            return "Indisponible";
        }

        double mb = bytes / 1048576.0;

        if (mb >= 1024) {
            return String.format(
                    Locale.US, "%.2f Go", mb / 1024
            );
        }

        return String.format(
                Locale.US, "%.2f Mo", mb
        );
    }

    // JOURNAL

    private void addLog(String message) {

        if (logText != null) {
            logText.append("\n> " + message);
        }
    }

    // ACTUALISATION AUTOMATIQUE

    private final Runnable ticker = new Runnable() {

        @Override
        public void run() {

            if (!autoRefresh) {
                return;
            }

            if ("DASHBOARD".equals(currentPage)) {
                refreshDashboard();
            }

            handler.postDelayed(this, 1000);
        }
    };

    // JAUGES CIRCULAIRES

    class GaugeView extends View {

        private final Paint paint = new Paint(3);
        private final RectF oval = new RectF();

        private final String label;
        private final String unit;
        private final int color;

        private float value = 0;
        private float min = 0;
        private float max = 100;

        private String valueText = "—";

        GaugeView(
                Context context,
                String label,
                String unit,
                int color
        ) {

            super(context);

            this.label = label;
            this.unit = unit;
            this.color = color;
        }

        void setValue(
                float value,
                float min,
                float max,
                String text
        ) {

            this.value = value;
            this.min = min;
            this.max = max;
            this.valueText = text;

            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {

            super.onDraw(canvas);

            float width = getWidth();
            float height = getHeight();

            float cx = width / 2;
            float cy = height * 0.65f;

            float radius = Math.min(
                    width * 0.38f,
                    height * 0.47f
            );

            oval.set(
                    cx - radius,
                    cy - radius,
                    cx + radius,
                    cy + radius
            );

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(8));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(0xFF30243F);

            canvas.drawArc(
                    oval,
                    135,
                    270,
                    false,
                    paint
            );

            float fraction = max > min
                    ? Math.max(
                            0,
                            Math.min(
                                    1,
                                    (value - min) / (max - min)
                            )
                    )
                    : 0;

            paint.setColor(color);

            canvas.drawArc(
                    oval,
                    135,
                    270 * fraction,
                    false,
                    paint
            );

            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(color);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(10));

            canvas.drawText(
                    label,
                    cx,
                    dp(16),
                    paint
            );

            paint.setTextSize(dp(17));

            canvas.drawText(
                    valueText,
                    cx,
                    cy + dp(5),
                    paint
            );

            paint.setTextSize(dp(9));
            paint.setTypeface(Typeface.DEFAULT);
            paint.setColor(GREY);

            canvas.drawText(
                    unit,
                    cx,
                    cy + dp(22),
                    paint
            );
        }
    }
}
