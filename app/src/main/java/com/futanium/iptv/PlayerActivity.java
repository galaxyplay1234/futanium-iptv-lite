package com.futanium.iptv;

import android.app.Activity;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.VideoView;

public class PlayerActivity extends Activity implements
        SurfaceHolder.Callback,
        MediaPlayer.OnPreparedListener,
        MediaPlayer.OnErrorListener {

    private SurfaceView surface;
    private SurfaceHolder holder;
    private MediaPlayer player;
    private ProgressBar loader;
    private String url;

    // fallback
    private VideoView videoView;
    private boolean usingFallback = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // layout em código (evita inflate)
        FrameLayout root = new FrameLayout(this);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        surface = new SurfaceView(this);
        surface.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        loader = new ProgressBar(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(120,120);
        lp.gravity = android.view.Gravity.CENTER;
        loader.setLayoutParams(lp);
        root.addView(surface);
        root.addView(loader);
        setContentView(root);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        holder = surface.getHolder();
        holder.addCallback(this);

        url = getIntent().getStringExtra("url");
    }

    private void play() {
        release();
        try {
            player = new MediaPlayer();
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            player.setDisplay(holder);
            player.setOnPreparedListener(this);
            player.setOnErrorListener(this);

            player.setDataSource(this, Uri.parse(url));
            loader.setVisibility(View.VISIBLE);
            player.prepareAsync();
        } catch (Exception e) {
            // se der erro na preparação do MediaPlayer, cai pro fallback direto
            startFallback();
        }
    }

    private void startFallback() {
        if (usingFallback) return;
        usingFallback = true;

        try {
            // Remove SurfaceView e usa VideoView
            FrameLayout root = (FrameLayout) surface.getParent();
            root.removeView(surface);

            videoView = new VideoView(this);
            videoView.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            root.addView(videoView, 0);

            loader.setVisibility(View.VISIBLE);
            videoView.setOnPreparedListener(mp -> loader.setVisibility(View.GONE));
            videoView.setOnErrorListener((mp, what, extra) -> {
                loader.setVisibility(View.GONE);
                Toast.makeText(this, "Falha ao reproduzir", Toast.LENGTH_LONG).show();
                return true;
            });

            videoView.setVideoURI(Uri.parse(url));
            videoView.start();
        } catch (Throwable t) {
            Toast.makeText(this, "Erro no fallback: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void release() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored){}
            try { player.reset(); } catch (Exception ignored){}
            try { player.release(); } catch (Exception ignored){}
            player = null;
        }
        if (videoView != null) {
            try { videoView.stopPlayback(); } catch (Exception ignored){}
        }
    }

    @Override public void surfaceCreated(SurfaceHolder holder) { play(); }
    @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int h2) {}
    @Override public void surfaceDestroyed(SurfaceHolder holder) { release(); }

    @Override public void onPrepared(MediaPlayer mp) {
        loader.setVisibility(View.GONE);
        mp.start();
    }

    @Override public boolean onError(MediaPlayer mp, int what, int extra) {
        // se o MediaPlayer da TV não aguentar o stream, usa fallback
        startFallback();
        return true;
    }

    @Override protected void onPause() { super.onPause(); if (player!=null) player.pause(); if (videoView!=null && videoView.isPlaying()) videoView.pause(); }
    @Override protected void onResume(){ super.onResume(); if (player!=null) player.start(); if (videoView!=null && !videoView.isPlaying()) videoView.start(); }
    @Override protected void onDestroy(){ release(); super.onDestroy(); }
}