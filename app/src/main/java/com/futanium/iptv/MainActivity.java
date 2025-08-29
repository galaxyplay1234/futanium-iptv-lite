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
        "https://raw.githubusercontent.com/galaxyplay1234/futanium-iptv-lite/refs/heads/main/playlist.m3u";

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

            if (conn instanceof HttpsURLConnection) {
    HttpsURLConnection https = (HttpsURLConnection) conn;
    https.setSSLSocketFactory(new TLS12SocketFactory());
}

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
        HttpURLConnection c = null;
        InputStream is = null;
        try {
            URL url = new URL(urlStr);
            HttpURLConnection.setFollowRedirects(true);
            c = (HttpURLConnection) url.openConnection();
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(10000);
            c.setReadTimeout(20000);
            c.setRequestProperty("User-Agent", "FutaniumIPTV-Lite/1.0 (KitKat)");
            // Alguns painéis exigem:
            // c.setRequestProperty("Referer", "http://getxc.top/");
            int code = c.getResponseCode();
            if (code >= 400) throw new Exception("HTTP " + code);
            is = c.getInputStream();

            // Usa parser linha-a-linha (super leve)
            return M3UParser.parse(is);
        } finally {
            try { if (is != null) is.close(); } catch (Exception ignored) {}
            try { if (c != null) c.disconnect(); } catch (Exception ignored) {}
        }
    }
} 