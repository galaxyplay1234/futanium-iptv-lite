package com.futanium.iptv;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

public class PlaylistDownloader {

    // Nome do arquivo de cache dentro do armazenamento interno do app
    private static final String CACHE_NAME = "playlist_cache.m3u";

    public static File getCacheFile(Context ctx) {
        return new File(ctx.getFilesDir(), CACHE_NAME);
    }

    public static boolean hasCache(Context ctx) {
        return getCacheFile(ctx).exists();
    }

    public static InputStream openCached(Context ctx) throws Exception {
        return new FileInputStream(getCacheFile(ctx));
    }

    /**
     * Baixa a M3U da URL e salva de forma atômica no cache interno.
     * Se virar HTTPS em algum redirect, força TLS 1.2 (KitKat).
     */
    public static void downloadToCache(Context ctx, String urlStr) throws Exception {
        HttpURLConnection conn = null;
        InputStream is = null;
        FileOutputStream os = null;

        // arquivo temporário para escrita atômica
        File tmp = new File(ctx.getFilesDir(), CACHE_NAME + ".downloading");
        File dst = getCacheFile(ctx);

        String current = urlStr;
        for (int hop = 0; hop < 5; hop++) {
            try {
                URL url = new URL(current);
                HttpURLConnection.setFollowRedirects(false);
                conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(25000);

                // Headers "de navegador" + Referer (muitos painéis exigem)
                conn.setRequestProperty("User-Agent",
                        "Mozilla/5.0 (Linux; Android 4.4; FutaniumIPTV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/34.0 Mobile Safari/537.36");
                conn.setRequestProperty("Accept", "*/*");
                conn.setRequestProperty("Accept-Encoding", "identity"); // evita gzip em TVs teimosas
                conn.setRequestProperty("Connection", "close");
                conn.setRequestProperty("Referer", "http://getxc.top/");

                // Se redirecionar para HTTPS, força TLS 1.2 (classe já no projeto)
                if (conn instanceof HttpsURLConnection) {
                    HttpsURLConnection https = (HttpsURLConnection) conn;
                    https.setSSLSocketFactory(new TLS12SocketFactory());
                }

                int code = conn.getResponseCode();

                // Trata redirects
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String loc = conn.getHeaderField("Location");
                    if (loc == null || loc.length() == 0) throw new Exception("Redirect sem Location");
                    current = loc;
                    try { conn.disconnect(); } catch (Exception ignored) {}
                    conn = null;
                    continue; // próximo hop
                }

                if (code >= 400) throw new Exception("HTTP " + code + " em " + current);

                is = conn.getInputStream();
                os = new FileOutputStream(tmp);

                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) > 0) {
                    os.write(buf, 0, n);
                }
                os.flush();
                os.close(); os = null;
                is.close(); is = null;

                // Move atômico: substitui o cache antigo
                if (dst.exists()) dst.delete();
                if (!tmp.renameTo(dst)) {
                    // fallback se rename falhar
                    FileOutputStream os2 = new FileOutputStream(dst);
                    FileInputStream is2 = new FileInputStream(tmp);
                    while ((n = is2.read(buf)) > 0) os2.write(buf, 0, n);
                    os2.flush(); os2.close(); is2.close();
                    tmp.delete();
                }
                try { conn.disconnect(); } catch (Exception ignored) {}
                conn = null;
                return;

            } catch (javax.net.ssl.SSLHandshakeException ssl) {
                // Se virar https e der erro de TLS, tenta http equivalente
                if (current.startsWith("https://")) {
                    current = "http://" + current.substring("https://".length());
                    safeClose(is); safeClose(os); safeDisc(conn);
                    is = null; os = null; conn = null;
                    continue;
                }
                throw ssl;
            } catch (Exception e) {
                // Tentativa única de trocar https->http
                if (current.startsWith("https://")) {
                    current = "http://" + current.substring("https://".length());
                    safeClose(is); safeClose(os); safeDisc(conn);
                    is = null; os = null; conn = null;
                    continue;
                }
                throw e;
            } finally {
                safeClose(is);
                safeClose(os);
                safeDisc(conn);
                if (tmp.exists()) tmp.delete();
            }
        }
        throw new Exception("Excesso de redirects");
    }

    private static void safeClose(java.io.Closeable c) {
        if (c != null) try { c.close(); } catch (Exception ignored) {}
    }
    private static void safeDisc(HttpURLConnection c) {
        if (c != null) try { c.disconnect(); } catch (Exception ignored) {}
    }
}