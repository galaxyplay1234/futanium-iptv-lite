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

    private static final String PLAYLIST_URL =
        "http://getxc.top/get.php?username=joao2025@@@&password=joao20252025&type=m3u_plus&output=hls";

    private ListView listView;
    private HorizontalScrollView tabsScroll;
    private LinearLayout tabsBar;
    private EditText searchBox;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Handler debounce = new Handler(Looper.getMainLooper());

    private ArrayList<M3UParser.Item> itemsAll = new ArrayList<M3UParser.Item>();
    private ArrayList<M3UParser.Item> itemsFiltered = new ArrayList<M3UParser.Item>();
    private ChannelAdapter adapter;

    private String currentGroup = "Todos";
    private String currentQuery = "";

    private volatile int filterGen = 0;
    private Runnable pendingFilter;

    static { System.setProperty("http.keepAlive", "false"); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            // UI mínima primeiro (para evitar crash imediato)
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            tabsScroll = new HorizontalScrollView(this);
            tabsScroll.setHorizontalScrollBarEnabled(false);
            tabsScroll.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
            tabsBar = new LinearLayout(this);
            tabsBar.setOrientation(LinearLayout.HORIZONTAL);
            tabsBar.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
            tabsScroll.addView(tabsBar);

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

            listView = new ListView(this);
            listView.setFastScrollEnabled(true);
            listView.setFastScrollAlwaysVisible(false);
            listView.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            root.addView(tabsScroll);
            root.addView(searchBox);
            root.addView(listView);
            setContentView(root);

            // Adapter único e placeholder (evita recriar)
            itemsFiltered.clear();
            itemsFiltered.add(new M3UParser.Item("Iniciando…", null));
            boolean ENABLE_LOGOS = (android.os.Build.VERSION.SDK_INT >= 21); // desliga logos no 4.4.2
            adapter = new ChannelAdapter(this, itemsFiltered, ENABLE_LOGOS);
            listView.setAdapter(adapter);

            listView.setOnItemClickListener((p, v, pos, id) -> {
                try {
                    if (itemsFiltered == null || itemsFiltered.isEmpty()) return;
                    if (pos >= itemsFiltered.size()) pos = itemsFiltered.size() - 1;
                    String url = itemsFiltered.get(pos).url;
                    if (url == null || url.length() == 0) return;
                    Intent it = new Intent(MainActivity.this, PlayerActivity.class);
                    it.putExtra("url", url);
                    startActivity(it);
                } catch (Throwable tapErr) {
                    toast("Erro ao abrir player: " + shortMsg(tapErr));
                }
            });

            // Busca com debounce
            searchBox.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    currentQuery = s != null ? s.toString() : "";
                    scheduleFilter();
                }
            });
            searchBox.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    listView.requestFocus();
                    return true;
                }
                return false;
            });
            searchBox.setOnLongClickListener(v -> { searchBox.setText(""); return true; });

            // Deixa o “pesado” para depois do layout pronto
            ui.post(this::safeStart);

        } catch (Throwable e) {
            // Qualquer coisa que dê ruim no onCreate cai aqui, com mensagem clara
            toast("Falha ao iniciar: " + shortMsg(e));
            Log.e("IPTV", "Crash onCreate", e);
        }
    }

    private void safeStart() {
        new Thread(() -> {
            try {
                // Atualiza no máximo a cada 8 horas
                PlaylistDownloader.ensureFresh(this, PLAYLIST_URL, 28_800_000L);

                InputStream is = PlaylistDownloader.openCached(this);
                itemsAll = M3UParser.parse(is);
                is.close();

                if (itemsAll == null || itemsAll.isEmpty()) throw new Exception("Lista vazia");

                Collections.sort(itemsAll, (a,b) -> {
                    String an = a.name != null ? a.name : "";
                    String bn = b.name != null ? b.name : "";
                    return an.compareToIgnoreCase(bn);
                });

                final ArrayList<String> groups = buildGroups(itemsAll);
                itemsFiltered = filter(itemsAll, currentGroup, currentQuery);

                ui.post(() -> {
                    try {
                        buildTabs(groups);
                        adapter.setData(itemsFiltered);
                        adapter.notifyDataSetChanged();
                        listView.requestFocus();
                        toast("Lista carregada (" + itemsAll.size() + " canais)");
                    } catch (Throwable eUI) {
                        toast("Falha UI: " + shortMsg(eUI));
                        Log.e("IPTV", "UI update fail", eUI);
                    }
                });

            } catch (Throwable e) {
                toast("Falha ao carregar: " + shortMsg(e));
                Log.e("IPTV", "Load fail", e);
            }
        }).start();
    }

    private void scheduleFilter() {
        final int myGen = ++filterGen;
        if (pendingFilter != null) debounce.removeCallbacks(pendingFilter);
        pendingFilter = () -> new Thread(() -> {
            try {
                ArrayList<M3UParser.Item> out = filter(itemsAll, currentGroup, currentQuery);
                if (myGen != filterGen) return; // descarta filtro antigo
                ui.post(() -> {
                    try {
                        adapter.setData(out);
                        adapter.notifyDataSetChanged();
                    } catch (Throwable eUI) {
                        toast("Falha filtro: " + shortMsg(eUI));
                    }
                });
            } catch (Throwable ef) {
                toast("Erro filtro: " + shortMsg(ef));
            }
        }).start();
        debounce.postDelayed(pendingFilter, 300);
    }

    private ArrayList<String> buildGroups(ArrayList<M3UParser.Item> list) {
        LinkedHashSet<String> set = new LinkedHashSet<String>();
        set.add("Todos");
        for (M3UParser.Item it : list) {
            if (it.group != null && it.group.trim().length() > 0) set.add(it.group.trim());
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

            tab.setOnClickListener(v -> {
                try {
                    currentGroup = label;
                    scheduleFilter();
                    buildTabs(groups);
                } catch (Throwable eTab) {
                    toast("Falha aba: " + shortMsg(eTab));
                }
            });

            tabsBar.addView(tab);
        }
    }

    private int dp(int d){
        return (int)(d * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String s){
        ui.post(() -> Toast.makeText(MainActivity.this, s, Toast.LENGTH_LONG).show());
    }
    private String shortMsg(Throwable t){
        String m = (t.getMessage()!=null?t.getMessage():t.toString());
        if (m.length()>120) m = m.substring(0,120)+"…";
        return m;
    }
}