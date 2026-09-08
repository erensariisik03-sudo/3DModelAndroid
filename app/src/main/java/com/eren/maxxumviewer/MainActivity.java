package com.eren.maxxumviewer;

import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

public class MainActivity extends Activity {
    private GLSurfaceView view;
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setNavigationBarColor(0xFF000000);
        view = new GLSurfaceView(this);
        view.setEGLContextClientVersion(2);
        view.setRenderer(new TractorRenderer());
        view.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        setContentView(view);
    }
    @Override protected void onPause(){ super.onPause(); view.onPause(); }
    @Override protected void onResume(){ super.onResume(); view.onResume(); }
}
