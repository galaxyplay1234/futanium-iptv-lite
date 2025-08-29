package com.futanium.iptv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;

public class MainActivity extends Activity {

    // URL do painel (HTTP)
    private static final String PLAYLIST_URL =
        "http://getxc.top/get.php?username=joao2025@@@&password=joao20252025&type=m3u_plus&output=hls";

    private ListView listView;
    private HorizontalScrollView tabsScroll;
    private LinearLayout tabsBar;

    private final Handler ui = new Handler(Looper.getMainLooper());

    private ArrayList<M3UParser.Item> itemsAll = new ArrayList<M3UParser.Item>();
    private ArrayList<M3UParser.Item> itemsFiltered = new ArrayList<M3UParser.Item>();
    private ChannelAdapter adapter;

    private String currentGroup = "Todos";

    static { System.setProperty("http.keepAlive", "false"); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ROOT
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // NAVBAR (HorizontalScrollView + LinearLayout)
        tabsScroll = new HorizontalScrollView(this);
        tabsScroll.setHorizontalScrollBarEnabled(false);
        tabsScroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        tabsBar = new LinearLayout(this);
        tabsBar.setOrientation(LinearLayout.HORIZONTAL);
        tabsBar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        tabsScroll.addView(tabsBar);

        // LIST
        listView = new ListView(this);
        listView.setFastScrollEnabled(true);
        listView.setFastScrollAlwaysVisible(false);
        listView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(tabsScroll);
        root.addView(listView);
        setContentView(root);

        // placeholder enquanto carrega
        ArrayList<M3UParser.Item> placeholder = new ArrayList<M3UParser.Item>();
        placeholder.add(new M3UParser.Item("Carregando lista...", null));
        adapter = new ChannelAdapter(this, placeholder);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((p, v, pos, id) -> {
            if (itemsFiltered == null || itemsFiltered.isEmpty()) return;
            if (pos >= itemsFiltered.size()) pos = itemsFiltered.size() - 1;
            String url = itemsFiltered.get(pos).url;
            if (url == null || url.length() == 0) return;
            Intent it = new Intent(MainActivity.this, PlayerActivity.class);
            it.putExtra("url", url);
            startActivity(it);
        });

        new Thread(this::loadAndBuild).start();
    }

    private void loadAndBuild() {
        try {
            // Atualiza cache (não falha app se der erro)
            try {
                // 8 horas = 28.800.000 ms
PlaylistDownloader.ensureFresh(this, PLAYLIST_URL, 28_800_000L);
            } catch (Throwable e) {
                Log.w("IPTV", "Não atualizou da nuvem: " + (e.getMessage()!=null?e.getMessage():e));
            }

            // Lê do cache
            if (PlaylistDownloader.hasCache(this)) {
                InputStream is = PlaylistDownloader.openCached(this);
                itemsAll = M3UParser.parse(is);
                is.close();
            } else {
                throw new Exception("Sem cache ainda");
            }

            if (itemsAll == null || itemsAll.isEmpty()) throw new Exception("Cache vazio");

            // Ordena por nome (mais fácil de navegar)
            Collections.sort(itemsAll, (a,b) -> {
                String an = a.name != null ? a.name : "";
                String bn = b.name != null ? b.name : "";
                return an.compareToIgnoreCase(bn);
            });

            // Monta categorias (group-title)
            final ArrayList<String> groups = buildGroups(itemsAll);

            // Aplica filtro inicial
            itemsFiltered = filterByGroup(itemsAll, currentGroup);

            ui.post(() -> {
                buildTabs(groups);
                adapter = new ChannelAdapter(MainActivity.this, itemsFiltered);
                listView.setAdapter(adapter);
                listView.requestFocus();
                Toast.makeText(MainActivity.this,
                        "Lista carregada (" + itemsAll.size() + " canais)", Toast.LENGTH_SHORT).show();
            });

        } catch (Throwable e) {
            Log.e("IPTV", "Falha: " + (e.getMessage()!=null?e.getMessage():e.toString()), e);
            ui.post(() -> Toast.makeText(MainActivity.this,
                    "Falha ao carregar: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }

    private ArrayList<String> buildGroups(ArrayList<M3UParser.Item> list) {
        LinkedHashSet<String> set = new LinkedHashSet<String>();
        set.add("Todos");
        for (M3UParser.Item it : list) {
            if (it.group != null && it.group.trim().length() > 0) {
                set.add(it.group.trim());
            }
        }
        return new ArrayList<String>(set);
    }

    private ArrayList<M3UParser.Item> filterByGroup(ArrayList<M3UParser.Item> list, String group) {
        if (group == null || "Todos".equals(group)) return new ArrayList<M3UParser.Item>(list);
        ArrayList<M3UParser.Item> out = new ArrayList<M3UParser.Item>();
        for (M3UParser.Item it : list) {
            String g = it.group != null ? it.group.trim() : "";
            if (g.equalsIgnoreCase(group)) out.add(it);
        }
        return out;
    }

    private void buildTabs(ArrayList<String> groups) {
        tabsBar.removeAllViews();
        int padH = dp(12), padV = dp(10);

        for (String g : groups) {
            final String label = g;
            TextView tab = new TextView(this);
            tab.setText(label);
            tab.setGravity(Gravity.CENTER_VERTICAL);
            tab.setPadding(padH, padV, padH, padV);
            tab.setBackgroundColor( label.equals(currentGroup) ? 0xFF333333 : 0xFF202020 );
            tab.setTextColor(0xFFFFFFFF);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
            lp.rightMargin = dp(8);
            tab.setLayoutParams(lp);

            tab.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    currentGroup = label;
                    itemsFiltered = filterByGroup(itemsAll, currentGroup);
                    adapter = new ChannelAdapter(MainActivity.this, itemsFiltered);
                    listView.setAdapter(adapter);
                    // atualiza seleção visual
                    buildTabs(groups);
                }
            });

            tabsBar.addView(tab);
        }
    }

    private int dp(int d){
        return (int)(d * getResources().getDisplayMetrics().density + 0.5f);
    }
}