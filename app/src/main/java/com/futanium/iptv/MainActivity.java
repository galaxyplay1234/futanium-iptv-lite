package com.futanium.iptv;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.app.AlertDialog;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class MainActivity extends Activity {

    // Se quiser nuvem, troque por uma URL .m3u (http é mais compatível no KitKat)
    private static final String PLAYLIST_URL = ""; // ex: "http://seu-host/channels.m3u"

    private ListView listView;
    private ArrayList<M3UParser.Item> items = new ArrayList<M3UParser.Item>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        listView = findViewById(R.id.listChannels);

        new LoadTask().execute();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            String url = items.get(position).url;
            Intent it = new Intent(MainActivity.this, PlayerActivity.class);
            it.putExtra("url", url);
            startActivity(it);
        });
    }

    private class LoadTask extends AsyncTask<Void, Void, Exception> {
        ArrayList<String> names = new ArrayList<String>();

        @Override protected Exception doInBackground(Void... voids) {
            try {
                InputStream is;
                if (PLAYLIST_URL != null && PLAYLIST_URL.length() > 0) {
                    URL url = new URL(PLAYLIST_URL);
                    HttpURLConnection c = (HttpURLConnection) url.openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(12000);
                    c.setRequestProperty("User-Agent", "FutaniumIPTV-Lite/1.0 (KitKat)");
                    is = c.getInputStream();
                } else {
                    // fallback local
                    is = getAssets().open("channels.m3u");
                }
                items = M3UParser.parse(is);
                is.close();

                names.clear();
                for (M3UParser.Item it : items) names.add(it.name);
                return null;
            } catch (Exception e) {
                return e;
            }
        }

        @Override protected void onPostExecute(Exception err) {
            if (err != null || items.isEmpty()) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Erro")
                        .setMessage(err != null ? err.getMessage() : "Lista vazia")
                        .setPositiveButton("OK", null)
                        .show();
                return;
            }
            listView.setAdapter(new ArrayAdapter<String>(
                    MainActivity.this,
                    android.R.layout.simple_list_item_1,
                    names
            ));
            listView.requestFocus(); // bom para controle remoto
        }
    }
}