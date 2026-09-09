package com.eren.maxxumviewer;

import android.app.Activity;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

public class MainActivity extends Activity {
    private TractorGlbViewer viewer;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setNavigationBarColor(0xFF000000);

        viewer = new TractorGlbViewer(this);
        setContentView(viewer.getSurfaceView());
    }

    @Override protected void onResume() {
        super.onResume();
        if (viewer != null) viewer.onResume();
    }

    @Override protected void onPause() {
        if (viewer != null) viewer.onPause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (viewer != null) viewer.destroy();
        super.onDestroy();
    }
}
