package com.futanium.iptv;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

public class ImageLoader {

    private final LruCache<String, Bitmap> cache;

    public ImageLoader() {
        // ~4MB de cache (suficiente para thumbs)
        int maxKb = 4 * 1024;
        cache = new LruCache<String, Bitmap>(maxKb) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount() / 1024;
            }
        };
    }

    public void load(String url, ImageView into, int reqSizePx) {
        into.setImageDrawable(null);
        if (url == null || url.length() == 0) return;

        Bitmap b = cache.get(url);
        if (b != null) {
            into.setImageBitmap(b);
            return;
        }
        new Task(url, into, reqSizePx).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private class Task extends AsyncTask<Void, Void, Bitmap> {
        private final String url;
        private final ImageView into;
        private final int req;

        Task(String u, ImageView v, int r){ url = u; into = v; req = r; }

        @Override
        protected Bitmap doInBackground(Void... voids) {
            InputStream is = null;
            HttpURLConnection c = null;
            try {
                URL u = new URL(url);
                c = (HttpURLConnection) u.openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(12000);
                c.setRequestProperty("User-Agent", "FutaniumIPTV-Lite/Logo/1.0");
                if (c instanceof HttpsURLConnection) {
                    ((HttpsURLConnection) c).setSSLSocketFactory(new TLS12SocketFactory());
                }
                int code = c.getResponseCode();
                if (code >= 400) return null;
                is = c.getInputStream();

                // primeira passada para dimensões
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(is, null, o);
                try { is.close(); } catch(Exception ignore){}
                c.disconnect();

                // abre de novo para decodificar com sample
                c = (HttpURLConnection) u.openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(12000);
                c.setRequestProperty("User-Agent", "FutaniumIPTV-Lite/Logo/1.0");
                if (c instanceof HttpsURLConnection) {
                    ((HttpsURLConnection) c).setSSLSocketFactory(new TLS12SocketFactory());
                }
                is = c.getInputStream();

                int sample = 1;
                int w = o.outWidth, h = o.outHeight;
                while (w / sample > req || h / sample > req) sample <<= 1;

                BitmapFactory.Options o2 = new BitmapFactory.Options();
                o2.inSampleSize = sample;
                o2.inPreferredConfig = Bitmap.Config.RGB_565; // leve

                Bitmap bmp = BitmapFactory.decodeStream(is, null, o2);
                return bmp;
            } catch (Exception ignored) {
                return null;
            } finally {
                try { if (is != null) is.close(); } catch (Exception ignored) {}
                try { if (c != null) c.disconnect(); } catch (Exception ignored) {}
            }
        }

        @Override
        protected void onPostExecute(Bitmap bmp) {
            if (bmp != null) {
                cache.put(url, bmp);
                into.setImageBitmap(bmp);
            }
        }
    }
}
