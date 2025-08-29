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
import android.widget.ProgressBar;

public class PlayerActivity extends Activity implements
        SurfaceHolder.Callback,
        MediaPlayer.OnPreparedListener,
        MediaPlayer.OnErrorListener {

    private SurfaceView surface;
    private SurfaceHolder holder;
    private MediaPlayer player;
    private ProgressBar loader;
    private String url;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        surface = findViewById(R.id.surface);
        loader  = findViewById(R.id.loader);
        holder  = surface.getHolder();
        holder.addCallback(this);

        url = getIntent().getStringExtra("url");
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
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
            loader.setVisibility(View.GONE);
        }
    }

    private void release() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored){}
            try { player.reset(); } catch (Exception ignored){}
            try { player.release(); } catch (Exception ignored){}
            player = null;
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
        loader.setVisibility(View.GONE);
        return true; // já consumimos o erro
    }

    @Override protected void onPause() { super.onPause(); if (player!=null) player.pause(); }
    @Override protected void onResume(){ super.onResume(); if (player!=null) player.start(); }
    @Override protected void onDestroy(){ release(); super.onDestroy(); }
}