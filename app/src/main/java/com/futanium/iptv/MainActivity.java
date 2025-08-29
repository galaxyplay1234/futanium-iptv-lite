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

import java.io.InputStream;
import java.util.ArrayList;

public class MainActivity extends Activity {

    // URL do painel (HTTP) — pode trocar depois se quiser GitHub RAW
    private static final String PLAYLIST_URL =
        "http://getxc.top/get.php?username=joao2025@@@&password=joao20252025&type=m3u_plus&output=hls";

    private ListView listView;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private ArrayList<M3UParser.Item> items = new ArrayList<M3UParser.Item>();

    // TVs antigas: desliga keep-alive globalmente
    static { System.setProperty("http.keepAlive", "false"); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Layout 100% em código (nada de inflate)
        FrameLayout root = new FrameLayout(this);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        listView = new ListView(this);
        // Fast scroll para navegar rápido nos 1000+ canais
        listView.setFastScrollEnabled(true);
        listView.setFastScrollAlwaysVisible(false);

        root.addView(listView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        // Mensagem inicial
        ArrayList<String> initial = new ArrayList<String>();
        initial.add("Carregando lista...");
        listView.setAdapter(new ArrayAdapter<String>(
                this, android.R.layout.simple_list_item_1, initial
        ));

        // Clique abre o player
        listView.setOnItemClickListener((p, v, pos, id) -> {
            if (items == null || items.isEmpty()) return;
            if (pos >= items.size()) pos = items.size() - 1;
            String url = items.get(pos).url;
            Intent it = new Intent(MainActivity.this, PlayerActivity.class);
            it.putExtra("url", url);
            startActivity(it);
        });

        // Carrega/atualiza em background
        new Thread(this::loadPlaylistSafe).start();
    }

    private void loadPlaylistSafe() {
        try {
            // 1) Tenta baixar e atualizar o cache local (não explode se falhar)
            try {
                PlaylistDownloader.downloadToCache(this, PLAYLIST_URL);
            } catch (Throwable e) {
                Log.w("IPTV", "Não atualizou da nuvem: " + (e.getMessage() != null ? e.getMessage() : e));
            }

            // 2) Lê a lista do cache (se existir)
            if (PlaylistDownloader.hasCache(this)) {
                InputStream is = PlaylistDownloader.openCached(this);
                items = M3UParser.parse(is);
                is.close();
            } else {
                throw new Exception("Sem cache ainda");
            }

            if (items == null || items.isEmpty()) throw new Exception("Cache vazio");

            // monta nomes (SEM QUALQUER LIMITE)
            ArrayList<String> names = new ArrayList<String>(items.size());
            for (M3UParser.Item it : items) names.add(it.name);

            ui.post(() -> {
                listView.setAdapter(new ArrayAdapter<String>(
                        MainActivity.this, android.R.layout.simple_list_item_1, names
                ));
                listView.requestFocus();
                Toast.makeText(MainActivity.this,
                        "Lista carregada (" + names.size() + " canais)", Toast.LENGTH_SHORT).show();
            });

        } catch (Throwable e) {
            Log.e("IPTV", "Falha cache/nuvem: " + (e.getMessage()!=null?e.getMessage():e.toString()), e);

            // 3) Fallback: assets/channels.m3u
            try {
                InputStream is = getAssets().open("channels.m3u");
                items = M3UParser.parse(is);
                is.close();
                if (items == null || items.isEmpty()) throw new Exception("assets vazio");

                ArrayList<String> names = new ArrayList<String>(items.size());
                for (M3UParser.Item it : items) names.add(it.name);

                ui.post(() -> {
                    listView.setAdapter(new ArrayAdapter<String>(
                            MainActivity.this, android.R.layout.simple_list_item_1, names
                    ));
                    listView.requestFocus();
                    Toast.makeText(MainActivity.this,
                            "Sem internet/compatibilidade — usando lista local.", Toast.LENGTH_LONG).show();
                });
            } catch (Throwable t2) {
                Log.e("IPTV", "Falha assets: " + (t2.getMessage()!=null?t2.getMessage():t2.toString()), t2);

                // 4) Fallback final: embutidos
                items = new ArrayList<M3UParser.Item>();
                items.add(new M3UParser.Item("Demo Bunny (fallback)", "http://184.72.239.149/vod/smil:BigBuckBunny.smil/playlist.m3u8"));
                items.add(new M3UParser.Item("Apple BipBop (fallback)", "http://devimages.apple.com/iphone/samples/bipbop/gear1/prog_index.m3u8"));

                ArrayList<String> names = new ArrayList<String>(items.size());
                for (M3UParser.Item it : items) names.add(it.name);

                ui.post(() -> {
                    listView.setAdapter(new ArrayAdapter<String>(
                            MainActivity.this, android.R.layout.simple_list_item_1, names
                    ));
                    listView.requestFocus();
                    Toast.makeText(MainActivity.this,
                            "Falha geral — usando canais de teste.", Toast.LENGTH_LONG).show();
                });
            }
        }
    }
}