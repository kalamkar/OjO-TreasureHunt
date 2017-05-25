/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ojogaze.treasurehunt;

import android.content.Context;
import android.media.MediaPlayer;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Log;

import com.google.vr.sdk.audio.GvrAudioEngine;
import com.google.vr.sdk.base.AndroidCompat;
import com.google.vr.sdk.base.Eye;
import com.google.vr.sdk.base.GvrActivity;
import com.google.vr.sdk.base.GvrView;
import com.google.vr.sdk.base.HeadTransform;
import com.google.vr.sdk.base.Viewport;
import com.ojogaze.treasurehunt.oogles20.Model;
import com.ojogaze.treasurehunt.oogles20.Position;
import com.ojogaze.treasurehunt.oogles20.Shader;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import javax.microedition.khronos.egl.EGLConfig;

import care.dovetail.ojo.EyeController;
import care.dovetail.ojo.EyeEvent;
import care.dovetail.ojo.Gesture;

/**
 * A Google VR sample application.
 * <p>
 * <p>The TreasureHunt scene consists of a planar ground grid and a floating "treasure" cube.
 * When the user looks at the cube, the cube will turn gold. While gold, the user can activate
 * the Cardboard trigger, either directly using the touch trigger on their Cardboard viewer,
 * or using the Daydream controller-based trigger emulation. Activating the trigger will in turn
 * randomly reposition the cube.
 */
public class TreasureHuntActivity extends GvrActivity implements GvrView.StereoRenderer,
        Gesture.Observer {

    private final EyeController eyeController = new EyeController(this);

    private static final String TAG = "TreasureHuntActivity";

    private static final float Z_NEAR = 0.1f;
    private static final float Z_FAR = 100.0f;

    private static final float CAMERA_Z = 0.01f;
    private static final float TIME_DELTA = 0.3f;

    private static final float YAW_LIMIT = 0.12f;
    private static final float PITCH_LIMIT = 0.12f;

    private static final float FLOOR_DEPTH = 20f;

    // We keep the light always position just above the user.
    private static final Position LIGHT_POS_IN_WORLD_SPACE =
            new Position("LIGHT_POS_IN_WORLD_SPACE", new float[]{0.0f, 2.0f, 0.0f, 1.0f});

    private static final float MIN_Z = 8.0f;
    private static final float MAX_Z = 12.0f;

    private static final float MAX_X = 1.2f;

    private static final int GESTURE_VISIBILITY_MILLIS = 1000;
    private static final int FIXATION_VISIBILITY_MILLIS = 1000;

//    private static final String OBJECT_SOUND_FILE = "cube_sound.wav";
    private static final String SUCCESS_SOUND_FILE = "success.wav";

    private final Model cube = new Model("Cube", 0, 36);
    private final Model floor = new Model("Floor");

    private Model camera = new Model("Camera");
    private Model headView = new Model("HeadView");

    private Vibrator vibrator;

    private GvrAudioEngine gvrAudioEngine;
    private volatile int sourceId = GvrAudioEngine.INVALID_ID;
    private volatile int successSourceId = GvrAudioEngine.INVALID_ID;

    private EyeEvent.Source eyeEventSource;
    private final Set<Gesture> directions = new HashSet<>();
    private int blinkCount = 0;
    private final Set<Integer> blinkAmplitudes = new HashSet<>();
    private boolean animationRunning = false;
    private long lastFruitChangeTimeMillis = 0;
    private final Map<String, MediaPlayer> players = new HashMap<>();

    private int currentColorIndex = 0;

    /**
     * Sets the view to our GvrView and initializes the transformation matrices we will use
     * to render our scene.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initializeGvrView();

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        // Initialize 3D audio engine.
        gvrAudioEngine =
                new GvrAudioEngine(this, GvrAudioEngine.RenderingMode.BINAURAL_HIGH_QUALITY);
        setEyeEventSource((EyeEvent.Source) eyeController.processor);
    }

    public void initializeGvrView() {
        setContentView(R.layout.common_ui);

        GvrView gvrView = (GvrView) findViewById(R.id.gvr_view);
        gvrView.setEGLConfigChooser(8, 8, 8, 8, 16, 8);

        gvrView.setRenderer(this);
        gvrView.setTransitionViewEnabled(true);

        // Enable Cardboard-trigger feedback with Daydream headsets.
        // This is a simple way of supporting Daydream controller input for basic interactions
        // using the existing Cardboard trigger API.
        gvrView.enableCardboardTriggerEmulation();

        if (gvrView.setAsyncReprojectionEnabled(true)) {
            // Async reprojection decouples the app framerate from the display framerate,
            // allowing immersive interaction even at the throttled clockrates set by
            // sustained performance mode.
            AndroidCompat.setSustainedPerformanceMode(this, true);
        }

        setGvrView(gvrView);
    }

    @Override
    public void onResume() {
        super.onResume();
        gvrAudioEngine.resume();
        eyeController.connect();
    }

    @Override
    public void onPause() {
        gvrAudioEngine.pause();
        eyeController.disconnect();
        super.onPause();
    }

    @Override
    public void onStart() {
        super.onStart();
        players.put("left", MediaPlayer.create(this, R.raw.slice));
        players.put("right", MediaPlayer.create(this, R.raw.slice));
        players.put("fixation", MediaPlayer.create(this, R.raw.jump));
        players.put("blink", MediaPlayer.create(this, R.raw.ping));
        players.put("explode", MediaPlayer.create(this, R.raw.explode));
    }

    @Override
    public void onStop() {
        for (MediaPlayer player : players.values()) {
            player.release();
        }
        players.clear();
        super.onStop();
    }

    @Override
    public void onRendererShutdown() {
        Log.i(TAG, "onRendererShutdown");
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        Log.i(TAG, "onSurfaceChanged");
    }

    /**
     * Creates the buffers we use to store information about the 3D world.
     * <p>
     * <p>OpenGL doesn't use Java arrays, but rather needs data in a format it can understand.
     * Hence we use ByteBuffers.
     *
     * @param config The EGL configuration used when creating the surface.
     */
    @Override
    public void onSurfaceCreated(EGLConfig config) {
        Log.i(TAG, "onSurfaceCreated");
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 0.5f); // Dark background so text shows up well.

        cube.setVertices(WorldLayoutData.CUBE_COORDS);
        cube.setColors(WorldLayoutData.CUBE_COLORS[currentColorIndex]);
        cube.setNormals(WorldLayoutData.CUBE_NORMALS);

        // make a floor
        floor.setVertices(WorldLayoutData.FLOOR_COORDS);
        floor.setNormals(WorldLayoutData.FLOOR_NORMALS);
        floor.setColors(WorldLayoutData.FLOOR_COLORS);

        Shader vertexShader = Shader.load(R.raw.light_vertex, GLES20.GL_VERTEX_SHADER, this);
        Shader gridShader = Shader.load(R.raw.grid_fragment, GLES20.GL_FRAGMENT_SHADER, this);
        Shader passthroughShader =
                Shader.load(R.raw.passthrough_fragment, GLES20.GL_FRAGMENT_SHADER, this);
        cube.attachShaders(new Shader[] {vertexShader, passthroughShader});
        floor.attachShaders(new Shader[] {vertexShader, gridShader});

        floor.translate(0, -FLOOR_DEPTH, 0); // Floor appears below user.

        // Avoid any delays during start-up due to decoding of sound files.
        new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        // Start spatial audio playback of OBJECT_SOUND_FILE at the model position.
                        // The returned sourceId handle is stored and allows for repositioning the
                        // sound object whenever the cube position changes.
//                        gvrAudioEngine.preloadSoundFile(OBJECT_SOUND_FILE);
//                        sourceId = gvrAudioEngine.createSoundObject(OBJECT_SOUND_FILE);
//                        gvrAudioEngine.setSoundObjectPosition(
//                                sourceId, modelPosition[0], modelPosition[1], modelPosition[2]);
//                        gvrAudioEngine.playSound(sourceId, true /* looped playback */);
                        // Preload an unspatialized sound to be played on a successful trigger on
                        // the cube.
                        gvrAudioEngine.preloadSoundFile(SUCCESS_SOUND_FILE);
                    }
                })
                .start();

        // Model first appears directly in front of user.
        updateModelPosition(new float[]{0.0f, 0.0f, -MIN_Z});

        Utils.checkGLError("onSurfaceCreated");
    }

    /**
     * Updates the cube model position.
     */
    protected void updateModelPosition(float modelPosition[]) {
        cube.translate(modelPosition[0], modelPosition[1], modelPosition[2]);

        // Update the sound location to match it with the new cube position.
        if (sourceId != GvrAudioEngine.INVALID_ID) {
            gvrAudioEngine.setSoundObjectPosition(
                    sourceId, modelPosition[0], modelPosition[1], modelPosition[2]);
        }
        Utils.checkGLError("updateCubePosition");
    }

    /**
     * Prepares OpenGL ES before we draw a frame.
     *
     * @param headTransform The head transformation in the new frame.
     */
    @Override
    public void onNewFrame(HeadTransform headTransform) {
        if (eyeController.processor.isGoodSignal()) {
            cube.rotate(TIME_DELTA, 0.5f, 0.5f, 1.0f);
        }

        // Build the camera matrix and apply it to the ModelView.
        Matrix.setLookAtM(camera.value, 0, 0.0f, 0.0f, CAMERA_Z, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);

        headTransform.getHeadView(headView.value, 0);

        // Update the 3d audio engine with the most recent head rotation.
        Position headRotation = new Position("HeadRotation");
        headTransform.getQuaternion(headRotation.value, 0);
        gvrAudioEngine.setHeadRotation(headRotation.value[0], headRotation.value[1],
                headRotation.value[2], headRotation.value[3]);
        // Regular update call to GVR audio engine.
        gvrAudioEngine.update();

        Utils.checkGLError("onReadyToDraw");
    }

    /**
     * Draws a frame for an eye.
     *
     * @param eye The eye to render. Includes all required transformations.
     */
    @Override
    public void onDrawEye(Eye eye) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        Utils.checkGLError("colorParam");

        // Apply the eye transformation to the camera.
        Model view = new Model("EyeView", eye.getEyeView()).multiply(camera);

        // Set the position of the light
        Position lightPosInEyeSpace = view.multiply(LIGHT_POS_IN_WORLD_SPACE);

        // Build the ModelView and ModelViewProjection matrices
        // for calculating cube position and light.
        Model perspective = new Model("Perspective", eye.getPerspective(Z_NEAR, Z_FAR));
        Model modelView = view.multiply(cube);
        Model modelViewProjection = perspective.multiply(modelView);
        cube.draw(modelView, modelViewProjection, lightPosInEyeSpace);

        // Set modelView for the floor, so we draw floor in the correct location
        modelView = view.multiply(floor);
        modelViewProjection = perspective.multiply(modelView);
        floor.draw(modelView, modelViewProjection, lightPosInEyeSpace);
    }

    @Override
    public void onFinishFrame(Viewport viewport) {
    }

    /**
     * Called when the Cardboard trigger is pulled.
     */
    @Override
    public void onCardboardTrigger() {
        Log.i(TAG, "onCardboardTrigger");

        if (isLookingAtObject()) {
            successSourceId = gvrAudioEngine.createStereoSound(SUCCESS_SOUND_FILE);
            gvrAudioEngine.playSound(successSourceId, false /* looping disabled */);
        }

        // Always give user feedback.
        vibrator.vibrate(50);
    }

    /**
     * Check if user is looking at object by calculating where the object is in eye-space.
     *
     * @return true if the user is looking at the object.
     */
    private boolean isLookingAtObject() {
        // Convert object space to camera space. Use the headView from onNewFrame.
        Position gaze = headView.multiply(cube).getPosition();
        float pitch = (float) Math.atan2(gaze.value[1], -gaze.value[2]);
        float yaw = (float) Math.atan2(gaze.value[0], -gaze.value[2]);

        return Math.abs(pitch) < PITCH_LIMIT && Math.abs(yaw) < YAW_LIMIT;
    }

    @Override
    public void onGesture(final String gestureName, final List<EyeEvent> events) {
        if (isDestroyed() || isRestricted() || isFinishing() || animationRunning) {
            return;
        }
        switch (gestureName) {
            case "left":
                play(gestureName);
                updateModelPosition(new float[] { -MAX_X, 0f, -MIN_Z});
                scheduleResetPosition(GESTURE_VISIBILITY_MILLIS);
                cube.setColors(WorldLayoutData.CUBE_COLORS[currentColorIndex]);
                animationRunning = true;
                break;
            case "right":
                play(gestureName);
                updateModelPosition(new float[] { MAX_X, 0f, -MIN_Z});
                scheduleResetPosition(GESTURE_VISIBILITY_MILLIS);
                cube.setColors(WorldLayoutData.CUBE_COLORS[currentColorIndex]);
                animationRunning = true;
                break;
            case "blink":
                play(gestureName);
                cube.setColors(WorldLayoutData.CUBE_COLORS[currentColorIndex]);
                maybeUpdateDirections(events);
                break;
            case "multiblink":
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastFruitChangeTimeMillis < 2000) {
                    // Ignore quick multiblink events.
                    return;
                }
                currentColorIndex = ++currentColorIndex < WorldLayoutData.CUBE_COLORS.length
                        ? currentColorIndex : 0;
                cube.setColors(WorldLayoutData.CUBE_COLORS[currentColorIndex]);
                lastFruitChangeTimeMillis = currentTime;
                break;
            case "fixation":
                play(gestureName);
                cube.setColors(WorldLayoutData.CUBE_COLOR_GOLD);
                scheduleResetFixation(FIXATION_VISIBILITY_MILLIS);
                break;
            case "explode":
                play(gestureName);
                cube.setColors(WorldLayoutData.CUBE_COLOR_INVISIBLE);
                scheduleResetFixation(FIXATION_VISIBILITY_MILLIS);
                animationRunning = true;
                break;
        }
    }

    @Override
    public void setEyeEventSource(EyeEvent.Source eyeEventSource) {
        this.eyeEventSource = eyeEventSource;
        eyeEventSource.add(new Gesture("blink")
                .add(EyeEvent.Criterion.saccade(EyeEvent.Direction.UP, 2000))
                .add(EyeEvent.Criterion.saccade(EyeEvent.Direction.DOWN, 4000))
                .add(EyeEvent.Criterion.saccade(EyeEvent.Direction.UP, 2000))
                .addObserver(this));
        eyeEventSource.add(new Gesture("multiblink")
                .add(EyeEvent.Criterion.saccade(EyeEvent.Direction.UP, 4000))
                .add(EyeEvent.Criterion.saccade(EyeEvent.Direction.DOWN, 4000))
                .add(EyeEvent.Criterion.saccade(EyeEvent.Direction.UP, 2000))
                .add(EyeEvent.Criterion.saccade(EyeEvent.Direction.DOWN, 2000))
                .add(EyeEvent.Criterion.saccade(EyeEvent.Direction.UP, 4000))
                .add(EyeEvent.Criterion.saccade(EyeEvent.Direction.DOWN, 4000))
                .addObserver(this));
        eyeEventSource.add(new Gesture("fixation")
                .add(EyeEvent.Criterion.fixation(1000))
                .addObserver(this));
        eyeEventSource.add(new Gesture("explode")
                .add(EyeEvent.Criterion.fixation(4000, 4500))
                .addObserver(this));
        replaceDirections(1500);
    }

    private void replaceDirections(int amplitude) {
        amplitude = Math.min(Math.max(amplitude, 800), 2000);
        for (Gesture direction : directions) {
            eyeEventSource.remove(direction);
        }
        directions.clear();
        directions.add(new Gesture("left")
                .add(EyeEvent.Criterion.fixation(1000, 4000))
                .add(EyeEvent.Criterion.saccade(EyeEvent.Direction.LEFT, amplitude))
                .add(EyeEvent.Criterion.saccade(EyeEvent.Direction.RIGHT, amplitude))
                .addObserver(this));
        directions.add(new Gesture("right")
                .add(EyeEvent.Criterion.fixation(1000, 4000))
                .add(EyeEvent.Criterion.saccade(EyeEvent.Direction.RIGHT, amplitude))
                .add(EyeEvent.Criterion.saccade(EyeEvent.Direction.LEFT, amplitude))
                .addObserver(this));
        for (Gesture direction : directions) {
            eyeEventSource.add(direction);
        }
    }

    private void maybeUpdateDirections(List<EyeEvent> events) {
        blinkCount++;
        if (blinkCount % 10 == 0 || blinkAmplitudes.size() > 0) {
            if (blinkAmplitudes.size() < 9) {
                blinkAmplitudes.add(events.get(0).amplitude);
                blinkAmplitudes.add(events.get(1).amplitude / 2);
                blinkAmplitudes.add(events.get(2).amplitude);
            } else {
                int sum = 0;
                for (Integer amplitude : blinkAmplitudes) {
                    sum += amplitude;
                }
                replaceDirections((sum / blinkAmplitudes.size()) / 3);
                blinkAmplitudes.clear();
            }
        }
    }

    private void scheduleResetPosition(int delay) {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                updateModelPosition(new float[]{0.0f, 0.0f, -MIN_Z});
                animationRunning = false;
            }
        }, delay);
    }

    private void scheduleResetFixation(int delay) {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                cube.setColors(WorldLayoutData.CUBE_COLORS[currentColorIndex]);
                animationRunning = false;
            }
        }, delay);
    }

    private void play(String name) {
        MediaPlayer player = players.get(name);
        if (player != null) {
            if (player.isPlaying()) {
                player.stop();
            }
            player.start();
        }
    }
}
