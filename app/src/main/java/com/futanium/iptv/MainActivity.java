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
import android.view.KeyEvent;
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

    // ids fixos para navegação DPAD
    private static final int ID_LIST = 1001;
    private static final int ID_FIRST_CHIP = 1002;

    private ListView listView;
    private HorizontalScrollView catScroll;
    private LinearLayout catBar;

    private final Handler ui = new Handler(Looper.getMainLooper());

    private ArrayList<M3UParser.Item> items = new ArrayList<M3UParser.Item>();
    private ArrayList<String> itemCats = new ArrayList<String>();
    private LinkedHashMap<String, Integer> catCounts = new LinkedHashMap<String, Integer>();

    private ArrayList<M3UParser.Item> filtered = new ArrayList<M3UParser.Item>();
    private ArrayList<String> filteredNames = new ArrayList<String>();
    private ArrayAdapter<String> adapter;

    private String selectedCategory = "Todos";
    private View firstChipRef = null; // pra focar com DPAD ↑

    static { System.setProperty("http.keepAlive", "false"); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Raiz: Linear vertical (categorias fixas em cima, lista embaixo)
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Barra de categorias fixa (HorizontalScroll)
        catScroll = new HorizontalScrollView(this);
        catScroll.setHorizontalScrollBarEnabled(false);
        catScroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        catBar = new LinearLayout(this);
        catBar.setOrientation(LinearLayout.HORIZONTAL);
        int pad = dp(8);
        catBar.setPadding(pad, pad, pad, pad);
        catScroll.addView(catBar, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(catScroll);

        // ListView ocupa o restante
        listView = new ListView(this);
        listView.setId(ID_LIST);
        LinearLayout.LayoutParams lpList = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(listView, lpList);

        setContentView(root);

        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, filteredNames);
        listView.setAdapter(adapter);

        // Clique no item -> Player
        listView.setOnItemClickListener((p, v, pos, id) -> {
            String url = filtered.get(pos).url;
            Intent it = new Intent(MainActivity.this, PlayerActivity.class);
            it.putExtra("url", url);
            startActivity(it);
        });

        // DPAD ↑ na lista leva para a barra de categorias (foca o primeiro chip)
        listView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (firstChipRef != null) {
                    firstChipRef.requestFocus();
                    return true;
                }
            }
            return false;
        });

        // Placeholder inicial
        filteredNames.clear();
        filteredNames.add("Carregando lista...");
        adapter.notifyDataSetChanged();

        new Thread(this::loadAll).start();
    }

    private void loadAll() {
        try {
            ArrayList<M3UParser.Item> out = fetchFromUrl(PLAYLIST_URL);
            if (out == null || out.isEmpty()) throw new Exception("Lista vazia da nuvem.");
            items = out;

            try {
                readCategoriesFromUrl(PLAYLIST_URL);
            } catch (Throwable catEx) {
                Log.w("IPTV", "Falha ao ler categorias: " + catEx.getMessage());
                itemCats.clear(); catCounts.clear();
            }

            ui.post(() -> {
                buildCategoryBar();
                applyFilter("Todos");
                Toast.makeText(MainActivity.this,
                        "Lista carregada: " + items.size() +
                                (catCounts.isEmpty() ? "" : " · " + catCounts.size() + " categorias"),
                        Toast.LENGTH_SHORT).show();
            });

        } catch (Throwable e) {
            Log.e("IPTV", "Falha rede: " + e.getMessage(), e);
            try {
                InputStream is = getAssets().open("channels.m3u");
                items = M3UParser.parse(is);
                is.close();

                try {
                    InputStream is2 = getAssets().open("channels.m3u");
                    readCategoriesFromStream(is2);
                    is2.close();
                } catch (Throwable ignore) { itemCats.clear(); catCounts.clear(); }

                ui.post(() -> {
                    buildCategoryBar();
                    applyFilter("Todos");
                    Toast.makeText(MainActivity.this,
                            "Sem internet/compatibilidade — usando lista local.",
                            Toast.LENGTH_LONG).show();
                });
            } catch (Throwable t2) {
                Log.e("IPTV", "Falha assets: " + t2.getMessage(), t2);

                items = new ArrayList<M3UParser.Item>();
                items.add(new M3UParser.Item("Demo Bunny (fallback)", "http://184.72.239.149/vod/smil:BigBuckBunny.smil/playlist.m3u8"));
                items.add(new M3UParser.Item("Apple BipBop (fallback)", "http://devimages.apple.com/iphone/samples/bipbop/gear1/prog_index.m3u8"));
                itemCats.clear();
                itemCats.add("Demo"); itemCats.add("Demo");
                catCounts.clear(); catCounts.put("Demo", 2);

                ui.post(() -> {
                    buildCategoryBar();
                    applyFilter("Todos");
                    Toast.makeText(MainActivity.this,
                            "Falha na nuvem — usando canais de teste.",
                            Toast.LENGTH_LONG).show();
                });
            }
        }
    }

    // === Seu downloader original, mantido ===
    private ArrayList<M3UParser.Item> fetchFromUrl(String urlStr) throws Exception {
        HttpURLConnection c = null;
        InputStream is = null;
        try {
            URL url = new URL(urlStr);
            HttpURLConnection.setFollowRedirects(true);
            c = (HttpURLConnection) url.openConnection();
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(10000);
            c.setReadTimeout(20000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 4.4; FutaniumIPTV) AppleWebKit/537.36 (KHTML, like Gecko) Mobile Safari/537.36");
            if (c instanceof HttpsURLConnection) {
                HttpsURLConnection https = (HttpsURLConnection) c;
                https.setSSLSocketFactory(new TLS12SocketFactory());
            }
            int code = c.getResponseCode();
            if (code >= 400) throw new Exception("HTTP " + code);
            is = c.getInputStream();
            return M3UParser.parse(is);
        } finally {
            try { if (is != null) is.close(); } catch (Exception ignored) {}
            try { if (c != null) c.disconnect(); } catch (Exception ignored) {}
        }
    }

    // === Ler categorias do mesmo arquivo ===
    private void readCategoriesFromUrl(String urlStr) throws Exception {
        HttpURLConnection c = null;
        InputStream is = null;
        try {
            URL url = new URL(urlStr);
            c = (HttpURLConnection) url.openConnection();
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 4.4; FutaniumIPTV)");
            c.setRequestProperty("Accept-Encoding", "identity");
            if (c instanceof HttpsURLConnection) {
                try { ((HttpsURLConnection) c).setSSLSocketFactory(new TLS12SocketFactory()); } catch (Throwable ignored) {}
            }
            int code = c.getResponseCode();
            if (code >= 400) throw new Exception("HTTP " + code);
            is = c.getInputStream();
            readCategoriesFromStream(is);
        } finally {
            try { if (is != null) is.close(); } catch (Exception ignored) {}
            try { if (c != null) c.disconnect(); } catch (Exception ignored) {}
        }
    }

    private void readCategoriesFromStream(InputStream is) throws Exception {
        itemCats.clear();
        catCounts.clear();
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        String line;
        while ((line = br.readLine()) != null) {
            if (!line.startsWith("#EXTINF:")) continue;
            String cat = extractGroupTitle(line);
            if (cat == null || cat.length() == 0) cat = "Sem Categoria";
            itemCats.add(cat);
            Integer old = catCounts.get(cat);
            catCounts.put(cat, (old == null) ? 1 : (old + 1));
        }
        br.close();
        if (!items.isEmpty() && itemCats.size() != items.size()) {
            int n = Math.min(itemCats.size(), items.size());
            while (itemCats.size() > n) itemCats.remove(itemCats.size() - 1);
            while (items.size() > n) items.remove(items.size() - 1);
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

    // === UI categorias (chips) ===
    private void buildCategoryBar() {
        catBar.removeAllViews();
        firstChipRef = null;

        int total = (!catCounts.isEmpty()) ? totalCountAll() : items.size();
        addCategoryChip("Todos", total, true); // primeiro chip

        for (Map.Entry<String, Integer> e : catCounts.entrySet()) {
            addCategoryChip(e.getKey(), e.getValue(), false);
        }
        highlightSelected("Todos");
    }

    private void addCategoryChip(final String cat, int count, boolean first) {
        final TextView chip = new TextView(this);
        chip.setText(cat + " (" + count + ")");
        chip.setSingleLine(true);
        chip.setPadding(dp(14), dp(8), dp(14), dp(8));
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setTextSize(14);
        chip.setFocusable(true);
        chip.setFocusableInTouchMode(true);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF2B3350);
        bg.setCornerRadius(dp(16));
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

        // DPAD ↓ do chip leva à lista
        chip.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                listView.requestFocus();
                listView.setSelection(0);
                return true;
            }
            return false;
        });

        // guarda o primeiro para o DPAD ↑ da lista
        if (first) {
            chip.setId(ID_FIRST_CHIP);
            firstChipRef = chip;
        }

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
                g.setColor(sel ? 0xFF3A4470 : 0xFF2B3350);
            }
        }
    }

    private void applyFilter(String cat) {
        filtered.clear();
        filteredNames.clear();

        if ("Todos".equals(cat) || itemCats.isEmpty()) {
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
        adapter.notifyDataSetChanged();
        listView.setSelection(0);
    }

    private int totalCountAll() {
        int sum = 0;
        for (Integer v : catCounts.values()) sum += (v != null ? v : 0);
        return sum;
    }

    private int dp(int v) {
        float d = getResources().getDisplayMetrics().density;
        return (int) (v * d + 0.5f);
    }
}