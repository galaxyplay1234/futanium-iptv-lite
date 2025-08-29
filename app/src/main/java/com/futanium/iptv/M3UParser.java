package com.futanium.iptv;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class M3UParser {

    public static class Item {
        public String name;
        public String url;
        public String logo;   // tvg-logo
        public String group;  // group-title

        public Item(String n, String u){ name = n; url = u; }
        public Item(String n, String u, String lg, String gp){
            name = n; url = u; logo = lg; group = gp;
        }
    }

    // regex para atributos do EXTINF: key="value"
    private static final Pattern ATTR = Pattern.compile("(\\w[\\w-]*)\\s*=\\s*\"([^\"]*)\"");

    public static ArrayList<Item> parse(InputStream is) throws Exception {
        ArrayList<Item> list = new ArrayList<Item>();
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"), 8192);
        String lastTitle = null, lastLogo = null, lastGroup = null, ln;
        while ((ln = br.readLine()) != null) {
            ln = ln.trim();
            if (ln.length() == 0) continue;

            if (ln.startsWith("#EXTINF")) {
                lastTitle = null; lastLogo = null; lastGroup = null;

                // pega atributos
                Matcher m = ATTR.matcher(ln);
                while (m.find()) {
                    String k = m.group(1);
                    String v = m.group(2);
                    if ("tvg-logo".equalsIgnoreCase(k)) lastLogo = v;
                    else if ("group-title".equalsIgnoreCase(k)) lastGroup = v;
                }
                // título após a vírgula
                int comma = ln.indexOf(",");
                if (comma >= 0 && comma + 1 < ln.length()) {
                    lastTitle = ln.substring(comma + 1).trim();
                }

            } else if (!ln.startsWith("#")) {
                String url = ln;
                String title = (lastTitle != null && lastTitle.length() > 0) ? lastTitle : url;
                list.add(new Item(title, url, lastLogo, lastGroup));
                lastTitle = null; lastLogo = null; lastGroup = null;
            }
        }
        br.close();
        return list;
    }
}