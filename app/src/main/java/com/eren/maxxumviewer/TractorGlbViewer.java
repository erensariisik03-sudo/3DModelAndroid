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
import com.google.android.filament.TransformManager;
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
    private static final double CAMERA_DISTANCE = 9.5;
    private static final double CAMERA_HEIGHT = 1.65;
    private static final double TARGET_Y = 0.0;
    private static final double DEGREES_PER_SECOND = 12.0;

    private final SurfaceView surfaceView;
    private final Engine engine;
    private final UiHelper uiHelper;
    private final ModelViewer modelViewer;
    private final Choreographer choreographer = Choreographer.getInstance();
    private final int sunEntity;

    private long lastFrameNanos = 0L;
    private double orbitAngle = Math.toRadians(25.0); // start in a 3/4 front view
    private boolean started;
    private int modelRootEntity = 0;
    private TransformManager transformManager;
    private final float[] baseRootTransform = new float[16];
    private boolean baseTransformReady;

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
            modelRootEntity = modelViewer.getAsset().getRoot();
            transformManager = engine.getTransformManager();
            int rootInstance = transformManager.getInstance(modelRootEntity);
            transformManager.getTransform(rootInstance, baseRootTransform);
            baseTransformReady = true;
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

            // Keep the camera fixed in a wider 3/4 presentation position.
            // The MODEL itself is rotated so the 360-degree motion cannot be overridden by
            // any camera helper.
            Camera camera = modelViewer.getCamera();
            double fixedAngle = Math.toRadians(25.0);
            double x = Math.sin(fixedAngle) * CAMERA_DISTANCE;
            double z = Math.cos(fixedAngle) * CAMERA_DISTANCE;
            camera.lookAt(
                    x, CAMERA_HEIGHT, z,
                    0.0, TARGET_Y, 0.0,
                    0.0, 1.0, 0.0);

            if (baseTransformReady && transformManager != null && modelRootEntity != 0) {
                int root = transformManager.getInstance(modelRootEntity);
                float a = (float) orbitAngle;
                float c = (float) Math.cos(a);
                float s = (float) Math.sin(a);

                // Filament matrices are column-major. Build Y rotation and multiply it by
                // the normalized base transform so the original scale/centering are preserved.
                float[] rot = new float[] {
                        c, 0.0f, -s, 0.0f,
                        0.0f, 1.0f, 0.0f, 0.0f,
                        s, 0.0f, c, 0.0f,
                        0.0f, 0.0f, 0.0f, 1.0f
                };
                float[] out = multiply4x4(baseRootTransform, rot);
                transformManager.setTransform(root, out);
            }

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

    private static float[] multiply4x4(float[] a, float[] b) {
        float[] out = new float[16];
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                float sum = 0.0f;
                for (int k = 0; k < 4; k++) {
                    sum += a[k * 4 + row] * b[col * 4 + k];
                }
                out[col * 4 + row] = sum;
            }
        }
        return out;
    }

    public void destroy() {
        choreographer.removeFrameCallback(this);
        try { modelViewer.destroyModel(); } catch (Throwable ignored) { }
        modelRootEntity = 0;
        transformManager = null;
        baseTransformReady = false;
        try { modelViewer.getScene().removeEntity(sunEntity); } catch (Throwable ignored) { }
        try { engine.destroyEntity(sunEntity); } catch (Throwable ignored) { }
        try { uiHelper.detach(); } catch (Throwable ignored) { }
        try { engine.destroy(); } catch (Throwable ignored) { }
    }
}
