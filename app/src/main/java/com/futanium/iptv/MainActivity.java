package com.futanium.iptv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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

    // M3U hospedada no GitHub RAW
    private static final String PLAYLIST_URL =
            "https://raw.githubusercontent.com/galaxyplay1234/futanium-iptv-lite/main/playlist.m3u";

    private ListView listView;
    private ArrayAdapter<String> channelsAdapter;

    private final Handler ui = new Handler(Looper.getMainLooper());

    private ArrayList<M3UParser.Item> items = new ArrayList<M3UParser.Item>();
    private ArrayList<String> itemCats = new ArrayList<String>();

    private ArrayList<M3UParser.Item> filtered = new ArrayList<M3UParser.Item>();
    private ArrayList<String> filteredNames = new ArrayList<String>();

    private LinkedHashMap<String, Integer> catCounts = new LinkedHashMap<String, Integer>();
    private String selectedCategory = "Todos";

    private HorizontalScrollView catScroll;
    private LinearLayout catBar;

    static { System.setProperty("http.keepAlive", "false"); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        listView = new ListView(this);

        // header fixo com categorias
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

        // adapter único (não trocamos mais)
        channelsAdapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_list_item_1, filteredNames
        );
        listView.setAdapter(channelsAdapter);

        root.addView(listView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        listView.setOnItemClickListener((p, v, pos, id) -> {
            int idx = pos - listView.getHeaderViewsCount();
            if (idx < 0 || idx >= filtered.size()) return;
            String url = filtered.get(idx).url;
            Intent it = new Intent(MainActivity.this, PlayerActivity.class);
            it.putExtra("url", url);
            startActivity(it);
        });

        // placeholder
        filteredNames.clear();
        filteredNames.add("Carregando lista...");
        channelsAdapter.notifyDataSetChanged();

        new Thread(this::loadAllSafe).start();
    }

    /* =======================
       Carregar + montar
       ======================= */
    private void loadAllSafe() {
        try {
            File temp = downloadToTempFile(PLAYLIST_URL);

            // 1) contar categorias (leve, sem carregar tudo em RAM)
            catCounts.clear();
            itemCats.clear();
            int entriesCount = 0;

            BufferedReader br = new BufferedReader(
                    new InputStreamReader(new FileInputStream(temp), "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.startsWith("#EXTINF:")) continue;
                String cat = extractGroupTitle(line);
                if (cat == null || cat.length() == 0) cat = "Sem Categoria";
                itemCats.add(cat);
                Integer old = catCounts.get(cat);
                catCounts.put(cat, (old == null) ? 1 : (old + 1));
                entriesCount++;
            }
            br.close();

            // 2) parse de canais
            InputStream is = new FileInputStream(temp);
            items = M3UParser.parse(is);
            is.close();
            temp.delete();

            if (items == null) items = new ArrayList<M3UParser.Item>();
            if (itemCats.size() != items.size()) {
                int n = Math.min(itemCats.size(), items.size());
                while (itemCats.size() > n) itemCats.remove(itemCats.size() - 1);
                while (items.size() > n) items.remove(items.size() - 1);
            }

            if (items.isEmpty()) throw new Exception("Playlist vazia ou ilegível");

            ui.post(() -> {
                renderCategoryHeader();
                applyFilter("Todos");
                Toast.makeText(MainActivity.this,
                        "OK: " + items.size() + " canais · " + catCounts.size() + " categorias",
                        Toast.LENGTH_SHORT).show();
            });

        } catch (Throwable e) {
            Log.e("IPTV", "Falha rede: " + e.getMessage(), e);
            fallbackLoad();
        }
    }

    private void fallbackLoad() {
        try {
            // assets/channels.m3u
            InputStream isRaw = getAssets().open("channels.m3u");
            BufferedReader br = new BufferedReader(new InputStreamReader(isRaw, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            catCounts.clear();
            itemCats.clear();
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
                if (line.startsWith("#EXTINF:")) {
                    String cat = extractGroupTitle(line);
                    if (cat == null || cat.length() == 0) cat = "Sem Categoria";
                    itemCats.add(cat);
                    Integer old = catCounts.get(cat);
                    catCounts.put(cat, (old == null) ? 1 : (old + 1));
                }
            }
            br.close();

            InputStream is = new ByteArrayInputStream(sb.toString().getBytes("UTF-8"));
            items = M3UParser.parse(is);
            is.close();

            if (itemCats.size() != items.size()) {
                int n = Math.min(itemCats.size(), items.size());
                while (itemCats.size() > n) itemCats.remove(itemCats.size() - 1);
                while (items.size() > n) items.remove(items.size() - 1);
            }

            ui.post(() -> {
                renderCategoryHeader();
                applyFilter("Todos");
                Toast.makeText(MainActivity.this,
                        "Sem internet — usando lista local.",
                        Toast.LENGTH_LONG).show();
            });

        } catch (Throwable t2) {
            Log.e("IPTV", "Falha assets: " + t2.getMessage(), t2);

            // demo mínima
            String demo =
                    "#EXTM3U\n" +
                    "#EXTINF:-1 group-title=\"Demo\",Demo Bunny\n" +
                    "http://184.72.239.149/vod/smil:BigBuckBunny.smil/playlist.m3u8\n" +
                    "#EXTINF:-1 group-title=\"Demo\",Apple BipBop\n" +
                    "http://devimages.apple.com/iphone/samples/bipbop/gear1/prog_index.m3u8\n";
            try {
                InputStream is3 = new ByteArrayInputStream(demo.getBytes("UTF-8"));
                items = M3UParser.parse(is3);
                is3.close();
                itemCats.clear();
                itemCats.add("Demo");
                itemCats.add("Demo");
                catCounts.clear();
                catCounts.put("Demo", 2);
            } catch (Exception ignore) { }

            ui.post(() -> {
                renderCategoryHeader();
                applyFilter("Todos");
                Toast.makeText(MainActivity.this,
                        "Falha total — canais de teste.",
                        Toast.LENGTH_LONG).show();
            });
        }
    }

    private String extractGroupTitle(String extinfLine) {
        int idx = extinfLine.toLowerCase(Locale.US).indexOf("group-title=\"");
        if (idx < 0) return null;
        int start = idx + "group-title=\"".length();
        int end = extinfLine.indexOf('"', start);
        if (end > start) return extinfLine.substring(start, end).trim();
        return null;
    }

    /* =======================
       UI: categorias (chips)
       ======================= */
    private void renderCategoryHeader() {
        catBar.removeAllViews();
        addCategoryChip("Todos", totalCountAll());
        for (Map.Entry<String, Integer> e : catCounts.entrySet()) {
            addCategoryChip(e.getKey(), e.getValue());
        }
        highlightSelected("Todos");
    }

    private int totalCountAll() {
        int sum = 0;
        for (Integer v : catCounts.values()) sum += (v != null ? v : 0);
        return sum;
    }

    private void addCategoryChip(final String cat, int count) {
        final TextView chip = new TextView(this);
        chip.setText(cat + " (" + count + ")");
        chip.setSingleLine(true);
        chip.setPadding(dp(14), dp(8), dp(14), dp(8));
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setTextSize(14);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF262C38);
        bg.setCornerRadius(dp(14));
        chip.setBackgroundDrawable(bg);
        chip.setTextColor(0xFFEFEFEF);

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
        int n = catBar.getChildCount();
        for (int i = 0; i < n; i++) {
            View v = catBar.getChildAt(i);
            if (v instanceof TextView) {
                TextView tv = (TextView) v;
                boolean sel = tv.getText().toString().startsWith(cat + " ");
                tv.setTypeface(null, sel ? Typeface.BOLD : Typeface.NORMAL);
                GradientDrawable g = (GradientDrawable) tv.getBackground();
                g.setColor(sel ? 0xFF2E3646 : 0xFF262C38);
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
        channelsAdapter.notifyDataSetChanged();
    }

    /* =======================
       Download → arquivo (sem gzip)
       ======================= */
    private File downloadToTempFile(String urlStr) throws Exception {
        HttpURLConnection conn = null;
        InputStream is = null;
        File out = File.createTempFile("m3u_", ".tmp", getCacheDir());
        FileOutputStream fos = new FileOutputStream(out);
        long bytes = 0;

        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(45000);
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 4.4; FutaniumIPTV) AppleWebKit/537.36 (KHTML, like Gecko) Mobile Safari/537.36");
            conn.setRequestProperty("Accept", "*/*");
            // >>> chave para TV/Android antigo: sem compressão
            conn.setRequestProperty("Accept-Encoding", "identity");
            conn.setRequestProperty("Connection", "close");

            if (conn instanceof HttpsURLConnection) {
                try { ((HttpsURLConnection) conn).setSSLSocketFactory(new TLS12SocketFactory()); } catch (Throwable ignored) {}
            }

            int code = conn.getResponseCode();
            if (code >= 400) throw new Exception("HTTP " + code);

            is = new BufferedInputStream(conn.getInputStream(), 8192);

            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                fos.write(buf, 0, n);
                bytes += n;
            }
            fos.flush();

            if (bytes == 0) throw new Exception("Stream vazio");
            return out;

        } finally {
            try { if (is != null) is.close(); } catch (Exception ignored) {}
            try { fos.close(); } catch (Exception ignored) {}
            try { if (conn != null) conn.disconnect(); } catch (Exception ignored) {}
        }
    }

    private int dp(int v) {
        float d = getResources().getDisplayMetrics().density;
        return (int) (v * d + 0.5f);
    }
}