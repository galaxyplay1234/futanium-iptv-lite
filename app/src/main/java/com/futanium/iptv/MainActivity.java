package com.futanium.iptv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class MainActivity extends Activity {

    // SUA PLAYLIST NA NUVEM (HLS):
    private static final String PLAYLIST_URL =
        "http://getxc.top/get.php?username=joao2025@@@&password=joao20252025&type=m3u_plus&output=hls";

    private ListView listView;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private ArrayList<M3UParser.Item> items = new ArrayList<M3UParser.Item>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Layout 100% em código (nada de inflate)
        FrameLayout root = new FrameLayout(this);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        listView = new ListView(this);
        root.addView(listView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        // Mostra "Carregando" sem travar
        ArrayList<String> initial = new ArrayList<String>();
        initial.add("Carregando lista...");
        listView.setAdapter(new ArrayAdapter<String>(
                this, android.R.layout.simple_list_item_1, initial
        ));
        listView.setOnItemClickListener((p, v, pos, id) -> {
            if (items == null || items.isEmpty()) return;
            if (pos >= items.size()) pos = 0;
            String url = items.get(pos).url;
            Intent it = new Intent(MainActivity.this, PlayerActivity.class);
            it.putExtra("url", url);
            startActivity(it);
        });

        // Busca em thread simples (evita AsyncTask bugar em TVs velhas)
        new Thread(this::loadPlaylistSafe).start();
    }

    private void loadPlaylistSafe() {
        try {
            ArrayList<M3UParser.Item> out;

            // 1) tenta rede
            out = fetchFromUrl(PLAYLIST_URL);

            if (out == null || out.isEmpty()) throw new Exception("Lista vazia da nuvem.");

            items = out;
            ArrayList<String> names = new ArrayList<String>();
            for (M3UParser.Item it : items) names.add(it.name);

            ui.post(() -> {
                listView.setAdapter(new ArrayAdapter<String>(
                        MainActivity.this, android.R.layout.simple_list_item_1, names
                ));
                listView.requestFocus();
                Toast.makeText(MainActivity.this, "Lista carregada", Toast.LENGTH_SHORT).show();
            });
        } catch (Throwable e) {
            Log.e("IPTV", "Falha rede: " + e.getMessage(), e);

            // 2) fallback: tentar assets/channels.m3u
            try {
                InputStream is = getAssets().open("channels.m3u");
                items = M3UParser.parse(is);
                is.close();
                if (items == null || items.isEmpty()) throw new Exception("assets vazio");
                ArrayList<String> names = new ArrayList<String>();
                for (M3UParser.Item it : items) names.add(it.name);
                ui.post(() -> {
                    listView.setAdapter(new ArrayAdapter<String>(
                            MainActivity.this, android.R.layout.simple_list_item_1, names
                    ));
                    listView.requestFocus();
                    Toast.makeText(MainActivity.this, "Sem internet/compatibilidade — usando lista local.", Toast.LENGTH_LONG).show();
                });
            } catch (Throwable t2) {
                Log.e("IPTV", "Falha assets: " + t2.getMessage(), t2);

                // 3) fallback final: lista EMBUTIDA (pra nunca cair)
                items = new ArrayList<M3UParser.Item>();
                items.add(new M3UParser.Item("Demo Bunny (fallback)", "http://184.72.239.149/vod/smil:BigBuckBunny.smil/playlist.m3u8"));
                items.add(new M3UParser.Item("Apple BipBop (fallback)", "http://devimages.apple.com/iphone/samples/bipbop/gear1/prog_index.m3u8"));

                ArrayList<String> names = new ArrayList<String>();
                for (M3UParser.Item it : items) names.add(it.name);

                ui.post(() -> {
                    listView.setAdapter(new ArrayAdapter<String>(
                            MainActivity.this, android.R.layout.simple_list_item_1, names
                    ));
                    listView.requestFocus();
                    Toast.makeText(MainActivity.this, "Falha na nuvem — usando canais de teste.", Toast.LENGTH_LONG).show();
                });
            }
        }
    }

    private ArrayList<M3UParser.Item> fetchFromUrl(String urlStr) throws Exception {
    HttpURLConnection conn = null;
    InputStream is = null;

    // alguns firmwares KK têm bug com keepAlive
    System.setProperty("http.keepAlive", "false");

    // laço de redirect manual (até 5 hops)
    String current = urlStr;
    for (int hop = 0; hop < 5; hop++) {
        try {
            URL url = new URL(current);
            HttpURLConnection.setFollowRedirects(false);
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(25000);

            // headers que muitos painéis exigem
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 4.4; FutaniumIPTV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/34.0 Mobile Safari/537.36");
            conn.setRequestProperty("Accept", "*/*");
            conn.setRequestProperty("Accept-Encoding", "gzip,deflate,identity");
            conn.setRequestProperty("Connection", "close");
            conn.setRequestProperty("Referer", "http://getxc.top/"); // <- importante p/ alguns painéis

            int code = conn.getResponseCode();

            // trata redirects (301/302/303/307/308)
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String loc = conn.getHeaderField("Location");
                if (loc == null || loc.length() == 0)
                    throw new Exception("Redirect sem Location");
                // fallback: se mandou pra https e a TV falhar depois, vamos tentar http mais abaixo
                current = loc;
                try { conn.disconnect(); } catch (Exception ignored) {}
                conn = null;
                continue; // próximo hop
            }

            if (code >= 400) throw new Exception("HTTP " + code);

            is = conn.getInputStream();

            // se vier gzip/deflate, desembrulha
            String enc = conn.getHeaderField("Content-Encoding");
            if (enc != null) {
                enc = enc.toLowerCase();
                if (enc.contains("gzip")) {
                    is = new java.util.zip.GZIPInputStream(is);
                } else if (enc.contains("deflate")) {
                    is = new java.util.zip.InflaterInputStream(is, new java.util.zip.Inflater(true));
                }
            }

            // parse direto do stream
            ArrayList<M3UParser.Item> out = M3UParser.parse(is);
            is.close(); is = null;
            conn.disconnect(); conn = null;
            return out;

        } catch (javax.net.ssl.SSLHandshakeException ssl) {
            // se o redirect levou a HTTPS moderno (TLS 1.2+) e falhou, tenta forçar HTTP
            if (current.startsWith("https://")) {
                String http = "http://" + current.substring("https://".length());
                current = http;
                // tenta de novo no próximo loop
                try { if (is != null) is.close(); } catch (Exception ignored) {}
                try { if (conn != null) conn.disconnect(); } catch (Exception ignored) {}
                is = null; conn = null;
                continue;
            }
            throw ssl;
        } catch (Exception e) {
            // se falhou e a URL atual é https, tenta versão http uma vez
            if (current.startsWith("https://")) {
                String http = "http://" + current.substring("https://".length());
                current = http;
                try { if (is != null) is.close(); } catch (Exception ignored) {}
                try { if (conn != null) conn.disconnect(); } catch (Exception ignored) {}
                is = null; conn = null;
                continue;
            }
            throw e;
        } finally {
            try { if (is != null) is.close(); } catch (Exception ignored) {}
            try { if (conn != null) conn.disconnect(); } catch (Exception ignored) {}
        }
    }

    throw new Exception("Excesso de redirects");
}