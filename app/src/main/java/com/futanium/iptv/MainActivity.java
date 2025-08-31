package com.futanium.iptv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

public class MainActivity extends Activity {

    // SUA PLAYLIST NA NUVEM (GitHub RAW - formato correto)
    private static final String PLAYLIST_URL =
            "https://raw.githubusercontent.com/galaxyplay1234/futanium-iptv-lite/main/playlist.m3u";

    private ListView listView;
    private ArrayAdapter<String> channelsAdapter;

    private final Handler ui = new Handler(Looper.getMainLooper());

    // Itens completos (nome+url) na ordem da M3U
    private ArrayList<M3UParser.Item> items = new ArrayList<M3UParser.Item>();
    // Categoria de cada item, na MESMA ordem de "items"
    private ArrayList<String> itemCats = new ArrayList<String>();

    // Lista filtrada a partir de "items"
    private ArrayList<M3UParser.Item> filtered = new ArrayList<M3UParser.Item>();
    private ArrayList<String> filteredNames = new ArrayList<String>();

    // Categorias e contagens (ordem de 1ª aparição)
    private LinkedHashMap<String, Integer> catCounts = new LinkedHashMap<String, Integer>();
    private String selectedCategory = "Todos";

    // Header de categorias
    private HorizontalScrollView catScroll;
    private LinearLayout catBar;

    static { System.setProperty("http.keepAlive", "false"); } // TVs antigas

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ===== Layout raiz =====
        FrameLayout root = new FrameLayout(this);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ListView principal
        listView = new ListView(this);
        root.addView(listView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ===== Header com barra horizontal de categorias =====
        catScroll = new HorizontalScrollView(this);
        catScroll.setHorizontalScrollBarEnabled(false);
        catScroll.setLayoutParams(new ListView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        catBar = new LinearLayout(this);
        catBar.setOrientation(LinearLayout.HORIZONTAL);
        int pad = dp(8);
        catBar.setPadding(pad, pad, pad, pad);
        catScroll.addView(catBar, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        listView.addHeaderView(catScroll, null, false);

        // Placeholder enquanto carrega
        ArrayList<String> initial = new ArrayList<String>();
        initial.add("Carregando lista...");
        channelsAdapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_list_item_1, initial
        );
        listView.setAdapter(channelsAdapter);

        // Clique no canal (ajustando o header)
        listView.setOnItemClickListener((p, v, pos, id) -> {
            int headerCount = listView.getHeaderViewsCount(); // 1
            int idx = pos - headerCount;
            if (idx < 0 || idx >= filtered.size()) return;
            String url = filtered.get(idx).url;
            Intent it = new Intent(MainActivity.this, PlayerActivity.class);
            it.putExtra("url", url);
            startActivity(it);
        });

        setContentView(root);

        // Busca em thread simples (evita AsyncTask bugar em TVs velhas)
        new Thread(this::loadAllSafe).start();
    }

    /* =========================
       Carregar M3U (texto) + montar categorias e canais
       ========================= */
    private void loadAllSafe() {
        try {
            // 1) NUVEM
            String text = fetchTextSmart(PLAYLIST_URL);
            if (TextUtils.isEmpty(text) || !text.trim().toUpperCase(Locale.US).contains("#EXTM3U")) {
                throw new Exception("Playlist inválida/vazia da nuvem.");
            }
            buildFromText(text);
            ui.post(() -> {
                renderCategoryHeader();
                applyFilter("Todos");
                Toast.makeText(MainActivity.this, "Lista carregada", Toast.LENGTH_SHORT).show();
            });

        } catch (Throwable e) {
            Log.e("IPTV", "Falha rede: " + e.getMessage(), e);

            // 2) ASSETS
            try {
                InputStream is = getAssets().open("channels.m3u");
                String text = readAll(is);
                is.close();
                if (TextUtils.isEmpty(text) || !text.trim().toUpperCase(Locale.US).contains("#EXTM3U")) {
                    throw new Exception("assets vazio/sem #EXTM3U");
                }
                buildFromText(text);
                ui.post(() -> {
                    renderCategoryHeader();
                    applyFilter("Todos");
                    Toast.makeText(MainActivity.this, "Sem internet — usando lista local.", Toast.LENGTH_LONG).show();
                });

            } catch (Throwable t2) {
                Log.e("IPTV", "Falha assets: " + t2.getMessage(), t2);

                // 3) DEMO embutido
                String demo =
                        "#EXTM3U\n" +
                        "#EXTINF:-1 group-title=\"Demo\",Demo Bunny\n" +
                        "http://184.72.239.149/vod/smil:BigBuckBunny.smil/playlist.m3u8\n" +
                        "#EXTINF:-1 group-title=\"Demo\",Apple BipBop\n" +
                        "http://devimages.apple.com/iphone/samples/bipbop/gear1/prog_index.m3u8\n";
                try {
                    buildFromText(demo);
                } catch (Exception ignore) {
                    items = new ArrayList<M3UParser.Item>();
                    itemCats = new ArrayList<String>();
                    catCounts = new LinkedHashMap<String, Integer>();
                    items.add(new M3UParser.Item("Demo Bunny", "http://184.72.239.149/vod/smil:BigBuckBunny.smil/playlist.m3u8"));
                    itemCats.add("Demo");
                    catCounts.put("Demo", 1);
                }
                ui.post(() -> {
                    renderCategoryHeader();
                    applyFilter("Todos");
                    Toast.makeText(MainActivity.this, "Falha total — canais de teste.", Toast.LENGTH_LONG).show();
                });
            }
        }
    }

    /** Lê o TEXTO completo da M3U, extrai categorias (group-title) e também parseia os canais com M3UParser. */
    private void buildFromText(String m3uText) throws Exception {
        // 1) Extrair categorias mantendo ordem
        catCounts = new LinkedHashMap<String, Integer>();
        itemCats = new ArrayList<String>();

        BufferedReader br = new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(m3uText.getBytes("UTF-8")), "UTF-8"));
        String line;
        while ((line = br.readLine()) != null) {
            if (!line.startsWith("#EXTINF:")) continue;
            String cat = extractGroupTitle(line);
            if (cat == null || cat.length() == 0) cat = "Sem Categoria";
            itemCats.add(cat); // um EXTINF por canal → alinhará com M3UParser depois

            Integer old = catCounts.get(cat);
            catCounts.put(cat, (old == null) ? 1 : (old + 1));
            // pula a próxima linha (URL) apenas avançando o loop naturalmente
        }
        br.close();

        // 2) Parse dos canais usando seu parser (mantendo compatibilidade)
        InputStream is = new ByteArrayInputStream(m3uText.getBytes("UTF-8"));
        items = M3UParser.parse(is);
        is.close();

        // Se tamanhos divergirem, alinhar pelo mínimo para evitar OOB em kits muito antigos
        if (itemCats.size() != items.size()) {
            int n = Math.min(itemCats.size(), items.size());
            while (itemCats.size() > n) itemCats.remove(itemCats.size() - 1);
            while (items.size() > n) items.remove(items.size() - 1);
        }
    }

    private String extractGroupTitle(String extinfLine) {
        int idx = indexOfIgnoreCase(extinfLine, "group-title=\"");
        if (idx < 0) return null;
        int start = idx + "group-title=\"".length();
        int end = extinfLine.indexOf('"', start);
        if (end > start) {
            return extinfLine.substring(start, end).trim();
        }
        return null;
    }

    private int indexOfIgnoreCase(String s, String needle) {
        final String a = s.toLowerCase(Locale.US);
        final String b = needle.toLowerCase(Locale.US);
        return a.indexOf(b);
    }

    /* =========================
       UI: categoria header + filtro
       ========================= */
    private void renderCategoryHeader() {
        catBar.removeAllViews();
        // Chip "Todos"
        addCategoryChip("Todos", totalCountAll());
        // Demais categorias na ordem encontrada
        for (Map.Entry<String, Integer> e : catCounts.entrySet()) {
            addCategoryChip(e.getKey(), e.getValue());
        }
        // Seleciona "Todos" inicialmente
        highlightSelected("Todos");
    }

    private int totalCountAll() {
        int sum = 0;
        for (Integer v : catCounts.values()) sum += (v != null ? v : 0);
        return sum;
    }

    private void addCategoryChip(final String cat, int count) {
        TextView chip = new TextView(this);
        chip.setText(cat + " (" + count + ")");
        chip.setSingleLine(true);
        chip.setEllipsize(android.text.TextUtils.TruncateAt.END);
        chip.setPadding(dp(12), dp(8), dp(12), dp(8));
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setTextSize(14);
        chip.setBackgroundResource(android.R.drawable.btn_default_small);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(6), 0, dp(6), 0);
        chip.setLayoutParams(lp);

        chip.setOnClickListener(v -> {
            applyFilter(cat);
            highlightSelected(cat);
        });

        catBar.addView(chip);
    }

    private void highlightSelected(String cat) {
        selectedCategory = cat;
        // efeito simples: deixa o selecionado em bold
        int n = catBar.getChildCount();
        for (int i = 0; i < n; i++) {
            View v = catBar.getChildAt(i);
            if (v instanceof TextView) {
                TextView tv = (TextView) v;
                boolean sel = tv.getText().toString().startsWith(cat + " ");
                tv.setTypeface(null, sel ? Typeface.BOLD : Typeface.NORMAL);
            }
        }
    }

    private void applyFilter(String cat) {
        filtered.clear();
        filteredNames.clear();

        if ("Todos".equals(cat)) {
            for (int i = 0; i < items.size(); i++) {
                filtered.add(items.get(i));
                filteredNames.add(items.get(i).name);
            }
        } else {
            for (int i = 0; i < items.size(); i++) {
                String c = (i < itemCats.size()) ? itemCats.get(i) : "Sem Categoria";
                if (cat.equals(c)) {
                    filtered.add(items.get(i));
                    filteredNames.add(items.get(i).name);
                }
            }
        }

        ui.post(() -> {
            channelsAdapter = new ArrayAdapter<String>(
                    MainActivity.this, android.R.layout.simple_list_item_1, filteredNames
            );
            listView.setAdapter(channelsAdapter);
            // recoloca o header (o setAdapter remove headers internamente)
            listView.addHeaderView(catScroll, null, false);
            channelsAdapter.notifyDataSetChanged();
            listView.requestFocus();
        });
    }

    /* =========================
       REDE: obter TEXTO com redirects + TLS 1.2 (KitKat)
       ========================= */
    private String fetchTextSmart(String urlStr) throws Exception {
        HttpURLConnection conn = null;
        InputStream is = null;

        String current = urlStr;
        for (int hop = 0; hop < 5; hop++) {
            try {
                URL url = new URL(current);
                HttpURLConnection.setFollowRedirects(false);
                conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(25000);

                conn.setRequestProperty("User-Agent",
                        "Mozilla/5.0 (Linux; Android 4.4; FutaniumIPTV) AppleWebKit/537.36 (KHTML, like Gecko) Mobile Safari/537.36");
                conn.setRequestProperty("Accept", "*/*");
                conn.setRequestProperty("Accept-Encoding", "identity");
                conn.setRequestProperty("Connection", "close");
                conn.setRequestProperty("Referer", "http://getxc.top/");

                // TLS 1.2 (se sua TLS12SocketFactory existir no projeto)
                if (conn instanceof HttpsURLConnection) {
                    try {
                        HttpsURLConnection https = (HttpsURLConnection) conn;
                        https.setSSLSocketFactory(new TLS12SocketFactory());
                    } catch (Throwable ignore) {}
                }

                int code = conn.getResponseCode();

                // redirects
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String loc = conn.getHeaderField("Location");
                    if (loc == null || loc.length() == 0) throw new Exception("Redirect sem Location");
                    current = loc;
                    safeDisc(conn); conn = null;
                    continue;
                }

                if (code >= 400) throw new Exception("HTTP " + code);

                is = conn.getInputStream();

                // desembrulhar se vier compactado
                String enc = conn.getHeaderField("Content-Encoding");
                if (enc != null) {
                    enc = enc.toLowerCase(Locale.US);
                    if (enc.contains("gzip")) {
                        is = new java.util.zip.GZIPInputStream(is);
                    } else if (enc.contains("deflate")) {
                        is = new java.util.zip.InflaterInputStream(is, new java.util.zip.Inflater(true));
                    }
                }

                String text = readAll(is);
                safeClose(is); is = null;
                safeDisc(conn); conn = null;
                return text;

            } catch (javax.net.ssl.SSLHandshakeException ssl) {
                if (current.startsWith("https://")) {
                    current = "http://" + current.substring("https://".length());
                    safeClose(is); safeDisc(conn); is = null; conn = null;
                    continue;
                }
                throw ssl;
            } catch (Exception e) {
                if (current.startsWith("https://")) {
                    current = "http://" + current.substring("https://".length());
                    safeClose(is); safeDisc(conn); is = null; conn = null;
                    continue;
                }
                throw e;
            } finally {
                safeClose(is);
                safeDisc(conn);
            }
        }
        throw new Exception("Excesso de redirects");
    }

    private String readAll(InputStream is) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) baos.write(buf, 0, n);
        return new String(baos.toByteArray(), "UTF-8");
    }

    private void safeClose(InputStream c) {
        if (c != null) try { c.close(); } catch (Exception ignored) {}
    }
    private void safeDisc(HttpURLConnection c) {
        if (c != null) try { c.disconnect(); } catch (Exception ignored) {}
    }

    private int dp(int v) {
        float d = getResources().getDisplayMetrics().density;
        return (int) (v * d + 0.5f);
    }
}