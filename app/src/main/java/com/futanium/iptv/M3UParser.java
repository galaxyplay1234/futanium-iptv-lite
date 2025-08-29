package com.futanium.iptv;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class M3UParser {

    public static class Item {
        public String name;
        public String url;
        Item(String n, String u){ name = n; url = u; }
    }

    public static ArrayList<Item> parse(InputStream is) throws Exception {
        ArrayList<Item> list = new ArrayList<Item>();
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        String lastTitle = null, ln;
        while ((ln = br.readLine()) != null) {
            ln = ln.trim();
            if (ln.startsWith("#EXTINF")) {
                int comma = ln.indexOf(",");
                lastTitle = (comma >= 0 && comma + 1 < ln.length()) ? ln.substring(comma+1).trim() : null;
            } else if (ln.length() > 0 && !ln.startsWith("#")) {
                String url = ln;
                String title = (lastTitle != null && lastTitle.length() > 0) ? lastTitle : url;
                list.add(new Item(title, url));
                lastTitle = null;
            }
        }
        br.close();
        return list;
    }
}