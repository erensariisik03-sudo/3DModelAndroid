package com.eren.maxxumviewer;

import android.content.Context;
import android.graphics.Color;
import android.view.Choreographer;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;

import com.google.android.filament.Camera;
import com.google.android.filament.Engine;
import com.google.android.filament.EntityManager;
import com.google.android.filament.LightManager;
import com.google.android.filament.utils.ModelViewer;
import com.google.android.filament.android.UiHelper;
import com.google.android.filament.utils.Utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Full-screen GLB viewer.
 *
 * The camera continuously orbits around the model. Every quarter turn a brief black
 * transition is shown, similar to a showroom / truck-gallery presentation.
 */
public final class TractorGlbViewer implements Choreographer.FrameCallback {
    // Wider / higher camera requested by the user.
    private static final double CAMERA_DISTANCE = 12.5;
    private static final double CAMERA_HEIGHT = 4.0;
    private static final double TARGET_Y = 0.5;

    // Negative angular speed => reverse direction compared with the previous build.
    // This is the direction intended as "right" for the presentation orbit.
    private static final double DEGREES_PER_SECOND = -12.0;

    // Four quarter-turn presentation cuts.
    private static final double ANGLE_PER_CUT = Math.PI / 2.0;
    private static final long FADE_MS = 650L;

    private final FrameLayout container;
    private final SurfaceView surfaceView;
    private final View fadeView;
    private final Engine engine;
    private final UiHelper uiHelper;
    private final ModelViewer modelViewer;
    private final Choreographer choreographer = Choreographer.getInstance();
    private final int[] lightEntities = new int[4];

    private long lastFrameNanos = 0L;
    private double orbitAngle = Math.toRadians(25.0);
    private double lastCutBoundary;
    private boolean started;
    private float fadeAlpha = 0.0f;
    private long fadeStartNanos = -1L;

    public TractorGlbViewer(Context context) {
        container = new FrameLayout(context);
        container.setBackgroundColor(Color.BLACK);

        surfaceView = new SurfaceView(context);
        surfaceView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        surfaceView.setFitsSystemWindows(false);
        container.addView(surfaceView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        // Black transition layer. It is invisible most of the time, but sits above the renderer.
        fadeView = new View(context);
        fadeView.setBackgroundColor(Color.BLACK);
        fadeView.setAlpha(0.0f);
        container.addView(fadeView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        Utils.INSTANCE.init();
        engine = Engine.create();
        uiHelper = new UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK);
        modelViewer = new ModelViewer(surfaceView, engine, uiHelper, null);
        modelViewer.setCameraFocalLength(34.0f);

        // Shadowless multi-directional fill. This intentionally avoids dark shaded sides and
        // the expensive shadow-map pass, which is useful for a heavy 3D asset on Android.
        createFillLights();

        loadModel();
        started = true;
        lastCutBoundary = quantizeBoundary(orbitAngle);
    }

    public View getContainer() {
        return container;
    }

    private void createFillLights() {
        float[][] directions = {
                {-0.55f, -1.0f, -0.45f},
                { 0.55f, -0.70f, -0.35f},
                {-0.40f, -0.75f,  0.75f},
                { 0.45f, -0.55f,  0.80f}
        };
        float[] intensities = {70000.0f, 42000.0f, 32000.0f, 26000.0f};
        float[][] colors = {
                {1.0f, 0.98f, 0.94f},
                {0.92f, 0.96f, 1.0f},
                {1.0f, 0.92f, 0.84f},
                {0.88f, 0.94f, 1.0f}
        };

        for (int i = 0; i < lightEntities.length; i++) {
            int entity = EntityManager.get().create();
            lightEntities[i] = entity;
            new LightManager.Builder(LightManager.Type.SUN)
                    .color(colors[i][0], colors[i][1], colors[i][2])
                    .intensity(intensities[i])
                    .direction(directions[i][0], directions[i][1], directions[i][2])
                    .castShadows(false)
                    .build(engine, entity);
            modelViewer.getScene().addEntity(entity);
        }
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
            // Normalize once so the camera settings are predictable across Sketchfab models.
            modelViewer.transformToUnitCube(new com.google.android.filament.utils.Float3(
                    0.0f, 0.0f, 0.0f));
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
                    double oldAngle = orbitAngle;
                    orbitAngle += Math.toRadians(DEGREES_PER_SECOND) * dt;

                    // Periodic quarter-turn fade, independent of the device refresh rate.
                    double newBoundary = quantizeBoundary(orbitAngle);
                    if (newBoundary != lastCutBoundary) {
                        lastCutBoundary = newBoundary;
                        fadeStartNanos = frameTimeNanos;
                    }
                }
            }
            lastFrameNanos = frameTimeNanos;

            Camera camera = modelViewer.getCamera();
            double x = Math.sin(orbitAngle) * CAMERA_DISTANCE;
            double z = Math.cos(orbitAngle) * CAMERA_DISTANCE;
            camera.lookAt(
                    x, CAMERA_HEIGHT, z,
                    0.0, TARGET_Y, 0.0,
                    0.0, 1.0, 0.0);

            modelViewer.render(frameTimeNanos);
            updateFade(frameTimeNanos);
        }
        choreographer.postFrameCallback(this);
    }

    private static double quantizeBoundary(double angle) {
        double turns = angle / ANGLE_PER_CUT;
        return Math.floor(turns + 0.5) * ANGLE_PER_CUT;
    }

    private void updateFade(long frameTimeNanos) {
        if (fadeStartNanos < 0L) {
            fadeView.setAlpha(0.0f);
            return;
        }
        float t = (float) ((frameTimeNanos - fadeStartNanos) / (FADE_MS * 1_000_000.0));
        if (t <= 0.45f) {
            fadeAlpha = t / 0.45f;
        } else if (t < 1.0f) {
            fadeAlpha = 1.0f - ((t - 0.45f) / 0.55f);
        } else {
            fadeAlpha = 0.0f;
            fadeStartNanos = -1L;
        }
        fadeView.setAlpha(Math.max(0.0f, Math.min(1.0f, fadeAlpha)));
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
        try { uiHelper.detach(); } catch (Throwable ignored) { }
        for (int entity : lightEntities) {
            if (entity != 0) {
                try { modelViewer.getScene().removeEntity(entity); } catch (Throwable ignored) { }
                try { engine.destroyEntity(entity); } catch (Throwable ignored) { }
            }
        }
        try { engine.destroy(); } catch (Throwable ignored) { }
    }
}
