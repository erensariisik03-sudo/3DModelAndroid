package com.eren.maxxumviewer;

import android.content.Context;
import android.view.Choreographer;
import android.view.SurfaceView;
import android.view.View;

import com.google.android.filament.Camera;
import com.google.android.filament.Engine;
import com.google.android.filament.EntityManager;
import com.google.android.filament.LightManager;
import com.google.android.filament.utils.ModelViewer;
import com.google.android.filament.utils.Utils;
import com.google.android.filament.android.UiHelper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/** Full-screen GLB viewer. The model is normalized to a unit cube, then the
 * camera orbits around the normalized model so camera distance is independent
 * of the GLB's original units/scale.
 */
public final class TractorGlbViewer implements Choreographer.FrameCallback {
    private static final double CAMERA_DISTANCE = 3.15;
    private static final double CAMERA_HEIGHT = 1.15;
    private static final double TARGET_Y = 0.05;
    private static final double DEGREES_PER_SECOND = 10.0;

    private final SurfaceView surfaceView;
    private final Engine engine;
    private final UiHelper uiHelper;
    private final ModelViewer modelViewer;
    private final Choreographer choreographer = Choreographer.getInstance();
    private final int sunEntity;

    private long startNanos;
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

        Utils.INSTANCE.init();
        engine = Engine.create();
        uiHelper = new UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK);
        modelViewer = new ModelViewer(surfaceView, engine, uiHelper, null);

        // The test model can be completely black without an IBL. Add a strong
        // sun light so standard GLB/PBR materials remain visible offline.
        sunEntity = EntityManager.get().create();
        new LightManager.Builder(LightManager.Type.SUN)
                .color(1.0f, 0.97f, 0.90f)
                .intensity(80000.0f)
                .direction(-0.45f, -1.0f, -0.35f)
                .castShadows(true)
                .build(engine, sunEntity);
        modelViewer.getScene().addEntity(sunEntity);

        loadModel();
        started = true;
        startNanos = System.nanoTime();
    }

    public SurfaceView getSurfaceView() { return surfaceView; }

    private void loadModel() {
        try (InputStream in = surfaceView.getContext().getAssets().open("tractor/tractor.glb")) {
            byte[] bytes = readAll(in);
            ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.length);
            buffer.put(bytes).flip();
            modelViewer.loadModelGlb(buffer);

            if (modelViewer.getAsset() == null) {
                throw new IllegalStateException("GLB yüklenemedi: tractor/tractor.glb geçerli bir glTF binary dosyası değil.");
            }

            // Normalize origin, scale and camera framing independent of the
            // units exported by Sketchfab/Blender/etc.
            modelViewer.transformToUnitCube();
            modelViewer.getAsset().releaseSourceData();
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
        startNanos = System.nanoTime();
        choreographer.postFrameCallback(this);
    }

    public void onPause() {
        choreographer.removeFrameCallback(this);
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
