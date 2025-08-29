package com.futanium.iptv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class MainActivity extends Activity {

    // SUA PLAYLIST NA NUVEM:
    private static final String PLAYLIST_URL =
        "http://getxc.top/get.php?username=joao2025@@@&password=joao20252025&type=m3u_plus&output=hls";

    private ListView listView;
    private ArrayList<M3UParser.Item> items = new ArrayList<M3UParser.Item>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            // Layout básico em código (evita qualquer problema de inflate/res/layout)
            FrameLayout root = new FrameLayout(this);
            root.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            listView = new ListView(this);
            listView.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            listView.setDividerHeight(1);
            root.addView(listView);

            setContentView(root);

            Toast.makeText(this, "Carregando lista...", Toast.LENGTH_SHORT).show();
            new LoadTask().execute();

            listView.setOnItemClickListener((parent, view, position, id) -> {
                try {
                    String url = items.get(position).url;
                    Intent it = new Intent(MainActivity.this, PlayerActivity.class);
                    it.putExtra("url", url);
                    startActivity(it);
                } catch (Throwable t) {
                    Log.e("IPTV", "click err", t);
                }
            });

        } catch (Throwable t) {
            Log.e("IPTV", "onCreate crash", t);
            safeDialog("Falha ao iniciar: " + t.getMessage());
        }
    }

    private class LoadTask extends AsyncTask<Void, Void, Exception> {
        ArrayList<String> names = new ArrayList<String>();

        @Override protected Exception doInBackground(Void... voids) {
            try {
                InputStream is;
                if (PLAYLIST_URL != null && PLAYLIST_URL.length() > 0) {
                    URL url = new URL(PLAYLIST_URL);
                    HttpURLConnection c = (HttpURLConnection) url.openConnection();
                    c.setConnectTimeout(10000);
                    c.setReadTimeout(20000);
                    c.setRequestProperty("User-Agent", "FutaniumIPTV-Lite/1.0 (KitKat)");
                    is = c.getInputStream();
                    items = M3UParser.parse(is);
                    is.close();
                    c.disconnect();
                } else {
                    // fallback local (não usado, mas seguro)
                    is = getAssets().open("channels.m3u");
                    items = M3UParser.parse(is);
                    is.close();
                }

                names.clear();
                for (M3UParser.Item it : items) names.add(it.name);
                return null;

            } catch (Exception e) {
                Log.e("IPTV", "load err", e);
                return e;
            }
        }

        @Override protected void onPostExecute(Exception err) {
            if (err != null || items.isEmpty()) {
                safeDialog(err != null ? ("Erro ao carregar: " + err.getMessage()) : "Lista vazia");
                return;
            }
            try {
                listView.setAdapter(new ArrayAdapter<String>(
                        MainActivity.this,
                        android.R.layout.simple_list_item_1,
                        names
                ));
                listView.requestFocus();
            } catch (Throwable t) {
                Log.e("IPTV", "adapter err", t);
                safeDialog("Falha ao montar lista: " + t.getMessage());
            }
        }
    }

    private void safeDialog(String msg) {
        try {
            new AlertDialog.Builder(this)
                    .setTitle("Erro")
                    .setMessage(msg)
                    .setPositiveButton("OK", null)
                    .show();
        } catch (Throwable ignored) {
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        }
    }
}