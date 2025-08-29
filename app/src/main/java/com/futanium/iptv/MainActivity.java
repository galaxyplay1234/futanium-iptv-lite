package com.futanium.iptv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import java.io.InputStream;
import java.text.Normalizer;
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
    private EditText searchBox;

    private final Handler ui = new Handler(Looper.getMainLooper());

    private ArrayList<M3UParser.Item> itemsAll = new ArrayList<M3UParser.Item>();
    private ArrayList<M3UParser.Item> itemsFiltered = new ArrayList<M3UParser.Item>();
    private ChannelAdapter adapter;

    private String currentGroup = "Todos";
    private String currentQuery = "";

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

        // SEARCH BOX
        searchBox = new EditText(this);
        searchBox.setHint("Buscar canal…");
        searchBox.setSingleLine(true);
        searchBox.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchBox.setInputType(InputType.TYPE_CLASS_TEXT);
        searchBox.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        int padH = dp(12), padV = dp(8);
        searchBox.setPadding(padH, padV, padH, padV);
        searchBox.setBackgroundColor(0xFF101010);
        searchBox.setTextColor(0xFFFFFFFF);
        searchBox.setHintTextColor(0xFF888888);

        // LIST
        listView = new ListView(this);
        listView.setFastScrollEnabled(true);
        listView.setFastScrollAlwaysVisible(false);
        listView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(tabsScroll);
        root.addView(searchBox);
        root.addView(listView);
        setContentView(root);

        // placeholder enquanto carrega
        ArrayList<M3UParser.Item> placeholder = new ArrayList<M3UParser.Item>();
        placeholder.add(new M3UParser.Item("Carregando lista...", null));
        adapter = new ChannelAdapter(this, placeholder);
        listView.setAdapter(adapter);

        // click na lista → Player
        listView.setOnItemClickListener((p, v, pos, id) -> {
            if (itemsFiltered == null || itemsFiltered.isEmpty()) return;
            if (pos >= itemsFiltered.size()) pos = itemsFiltered.size() - 1;
            String url = itemsFiltered.get(pos).url;
            if (url == null || url.length() == 0) return;
            Intent it = new Intent(MainActivity.this, PlayerActivity.class);
            it.putExtra("url", url);
            startActivity(it);
        });

        // BUSCA: atualiza enquanto digita
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                currentQuery = s != null ? s.toString() : "";
                applyFilterAndRefresh();
            }
        });
        // Enter/Busca esconde IME e mantém foco na lista
        searchBox.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    listView.requestFocus();
                    return true;
                }
                return false;
            }
        });
        // Long-press limpa a busca (atalho)
        searchBox.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                searchBox.setText("");
                return true;
            }
        });

        new Thread(this::loadAndBuild).start();
    }

    private void loadAndBuild() {
        try {
            // Atualiza cache (não quebra se falhar)
            try {
                // 8 horas = 28_800_000 ms
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

            // Ordena por nome
            Collections.sort(itemsAll, (a,b) -> {
                String an = a.name != null ? a.name : "";
                String bn = b.name != null ? b.name : "";
                return an.compareToIgnoreCase(bn);
            });

            // Constrói categorias
            final ArrayList<String> groups = buildGroups(itemsAll);

            // Aplica filtro inicial
            itemsFiltered = filter(itemsAll, currentGroup, currentQuery);

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

    // ===== Filtro por categoria + texto =====
    private void applyFilterAndRefresh() {
        itemsFiltered = filter(itemsAll, currentGroup, currentQuery);
        adapter = new ChannelAdapter(MainActivity.this, itemsFiltered);
        listView.setAdapter(adapter);
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

    private ArrayList<M3UParser.Item> filter(ArrayList<M3UParser.Item> list, String group, String query) {
        String q = normalize(query);
        boolean anyGroup = (group == null || "Todos".equals(group));

        ArrayList<M3UParser.Item> out = new ArrayList<M3UParser.Item>();
        for (M3UParser.Item it : list) {
            if (!anyGroup) {
                String g = it.group != null ? it.group.trim() : "";
                if (!g.equalsIgnoreCase(group)) continue;
            }
            if (q.length() > 0) {
                String name = it.name != null ? it.name : "";
                if (!normalize(name).contains(q)) continue;
            }
            out.add(it);
        }
        return out;
    }

    // normaliza (remove acentos/caixa) pra busca mais “inteligente”
    private String normalize(String s) {
        if (s == null) return "";
        String t = s.trim().toLowerCase();
        try {
            t = Normalizer.normalize(t, Normalizer.Form.NFD)
                    .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        } catch (Throwable ignore) {}
        return t;
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
                    applyFilterAndRefresh();
                    buildTabs(groups); // atualiza seleção visual
                }
            });

            tabsBar.addView(tab);
        }
    }

    private int dp(int d){
        return (int)(d * getResources().getDisplayMetrics().density + 0.5f);
    }
}