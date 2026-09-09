package com.eren.maxxumviewer;

import android.content.Context;
import android.view.Choreographer;
import android.view.SurfaceView;

import com.google.android.filament.Camera;
import com.google.android.filament.Engine;
import com.google.android.filament.utils.ModelViewer;
import com.google.android.filament.utils.Utils;
import com.google.android.filament.android.UiHelper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/** Full-screen GLB viewer with a slow automatic orbit camera. */
public final class TractorGlbViewer implements Choreographer.FrameCallback {
    private static final double CAMERA_DISTANCE = 9.4;
    private static final double CAMERA_HEIGHT = 2.35;
    private static final double TARGET_Y = 0.85;
    private static final double DEGREES_PER_SECOND = 12.0;

    private final Context context;
    private final SurfaceView surfaceView;
    private final Engine engine;
    private final UiHelper uiHelper;
    private final ModelViewer modelViewer;
    private final Choreographer choreographer = Choreographer.getInstance();
    private long startNanos;
    private boolean started;

    public TractorGlbViewer(Context context) {
        this.context = context;
        surfaceView = new SurfaceView(context);
        surfaceView.setSystemUiVisibility(
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                        | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        surfaceView.setFitsSystemWindows(false);

        // Load Filament native libraries before creating the Engine.
        Utils.INSTANCE.init();
        engine = Engine.create();
        uiHelper = new UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK);
        modelViewer = new ModelViewer(surfaceView, engine, uiHelper, null);

        loadModel();
        startNanos = System.nanoTime();
    }

    public SurfaceView getSurfaceView() { return surfaceView; }

    private void loadModel() {
        try (InputStream in = context.getAssets().open("tractor/tractor.glb")) {
            byte[] bytes = readAll(in);
            ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.length);
            buffer.put(bytes).flip();
            modelViewer.loadModelGlb(buffer);
            if (modelViewer.getAsset() != null) {
                modelViewer.getAsset().releaseSourceData();
            }
            started = true;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "GLB bulunamadı. app/src/main/assets/tractor/tractor.glb konumuna koyun.", e);
        }
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = input.read(buf)) != -1) out.write(buf, 0, n);
        return out.toByteArray();
    }

    @Override public void doFrame(long frameTimeNanos) {
        if (started) {
            double seconds = (frameTimeNanos - startNanos) / 1_000_000_000.0;
            double angle = Math.toRadians((seconds * DEGREES_PER_SECOND) % 360.0);
            double x = Math.sin(angle) * CAMERA_DISTANCE;
            double z = Math.cos(angle) * CAMERA_DISTANCE;

            Camera camera = modelViewer.getCamera();
            camera.lookAt(x, CAMERA_HEIGHT, z,
                    0.0, TARGET_Y, 0.0,
                    0.0, 1.0, 0.0);
            modelViewer.render(frameTimeNanos);
        }
        choreographer.postFrameCallback(this);
    }

    public void onResume() {
        choreographer.removeFrameCallback(this);
        choreographer.postFrameCallback(this);
    }

    public void onPause() {
        choreographer.removeFrameCallback(this);
    }

    public void destroy() {
        choreographer.removeFrameCallback(this);
        try { modelViewer.destroyModel(); } catch (Throwable ignored) { }
        try { uiHelper.detach(); } catch (Throwable ignored) { }
        try { engine.destroy(); } catch (Throwable ignored) { }
    }
}
