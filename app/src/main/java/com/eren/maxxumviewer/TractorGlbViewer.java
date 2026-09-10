package com.eren.maxxumviewer;

import android.content.Context;
import android.view.Choreographer;
import android.view.SurfaceView;
import android.view.View;

import com.google.android.filament.Camera;
import com.google.android.filament.Engine;
import com.google.android.filament.EntityManager;
import com.google.android.filament.LightManager;
import com.google.android.filament.utils.Float3;
import com.google.android.filament.utils.ModelViewer;
import com.google.android.filament.utils.Utils;
import com.google.android.filament.android.UiHelper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Full-screen FNF Supra GLB viewer.
 * The camera performs a continuous orbit around the normalized model.
 */
public final class TractorGlbViewer implements Choreographer.FrameCallback {
    private static final double CAMERA_DISTANCE = 4.25;
    private static final double CAMERA_HEIGHT = 0.92;
    private static final double TARGET_Y = 0.02;
    private static final double DEGREES_PER_SECOND = 14.0;

    private final SurfaceView surfaceView;
    private final Engine engine;
    private final UiHelper uiHelper;
    private final ModelViewer modelViewer;
    private final Choreographer choreographer = Choreographer.getInstance();
    private final int sunEntity;

    private long lastFrameNanos = 0L;
    private double orbitAngle = Math.toRadians(25.0); // start in a 3/4 front view
    private boolean started;

    public TractorGlbViewer(Context context) {
        surfaceView = new SurfaceView(context);
        surfaceView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        surfaceView.setFitsSystemWindows(false);

        // Filament native library initialization.
        Utils.INSTANCE.init();
        engine = Engine.create();
        uiHelper = new UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK);
        modelViewer = new ModelViewer(surfaceView, engine, uiHelper, null);
        modelViewer.setCameraFocalLength(32.0f);

        // Strong neutral sunlight so the GLB remains visible without an HDR environment map.
        sunEntity = EntityManager.get().create();
        new LightManager.Builder(LightManager.Type.SUN)
                .color(1.0f, 0.98f, 0.94f)
                .intensity(100000.0f)
                .direction(-0.55f, -1.0f, -0.45f)
                .castShadows(true)
                .build(engine, sunEntity);
        modelViewer.getScene().addEntity(sunEntity);

        loadModel();
        started = true;
    }

    public SurfaceView getSurfaceView() {
        return surfaceView;
    }

    private void loadModel() {
        try (InputStream in = surfaceView.getContext().getAssets().open("tractor/tractor.glb")) {
            byte[] bytes = readAll(in);
            ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.length);
            buffer.put(bytes).flip();
            modelViewer.loadModelGlb(buffer);

            if (modelViewer.getAsset() == null) {
                throw new IllegalStateException("tractor/tractor.glb yüklenemedi.");
            }

            // Normalize the Sketchfab model so the camera distance is predictable.
            modelViewer.transformToUnitCube(new Float3(0.0f, 0.0f, 0.0f));
        } catch (IOException e) {
            throw new IllegalStateException(
                    "GLB bulunamadı: app/src/main/assets/tractor/tractor.glb", e);
        }
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = input.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (started) {
            if (lastFrameNanos != 0L) {
                double dt = (frameTimeNanos - lastFrameNanos) / 1_000_000_000.0;
                if (dt > 0.0 && dt < 0.25) {
                    orbitAngle += Math.toRadians(DEGREES_PER_SECOND) * dt;
                    if (orbitAngle > Math.PI * 2.0) {
                        orbitAngle -= Math.PI * 2.0;
                    }
                }
            }
            lastFrameNanos = frameTimeNanos;

            double x = Math.sin(orbitAngle) * CAMERA_DISTANCE;
            double z = Math.cos(orbitAngle) * CAMERA_DISTANCE;

            Camera camera = modelViewer.getCamera();
            camera.lookAt(
                    x, CAMERA_HEIGHT, z,
                    0.0, TARGET_Y, 0.0,
                    0.0, 1.0, 0.0);

            modelViewer.render(frameTimeNanos);
        }
        choreographer.postFrameCallback(this);
    }

    public void onResume() {
        lastFrameNanos = 0L;
        choreographer.removeFrameCallback(this);
        choreographer.postFrameCallback(this);
    }

    public void onPause() {
        choreographer.removeFrameCallback(this);
        lastFrameNanos = 0L;
    }

    public void destroy() {
        choreographer.removeFrameCallback(this);
        try { modelViewer.destroyModel(); } catch (Throwable ignored) { }
        try { modelViewer.getScene().removeEntity(sunEntity); } catch (Throwable ignored) { }
        try { engine.destroyEntity(sunEntity); } catch (Throwable ignored) { }
        try { uiHelper.detach(); } catch (Throwable ignored) { }
        try { engine.destroy(); } catch (Throwable ignored) { }
    }
}
