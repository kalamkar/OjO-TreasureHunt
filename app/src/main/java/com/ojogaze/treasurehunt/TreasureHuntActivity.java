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

import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;

import care.dovetail.ojo.EyeEvent;
import care.dovetail.ojo.Gesture;
import care.dovetail.ojo.EyeController;

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

    private static final float MIN_MODEL_DISTANCE = 3.0f;
    private static final float MAX_MODEL_DISTANCE = 7.0f;

//    private static final String OBJECT_SOUND_FILE = "cube_sound.wav";
    private static final String SUCCESS_SOUND_FILE = "success.wav";

    private final Model cube = new Model("Cube", 0, 36);
    private final Model floor = new Model("Floor");

    private Model camera = new Model("Camera");
    private Model headView = new Model("HeadView");

    private float objectDistance = (MAX_MODEL_DISTANCE + MIN_MODEL_DISTANCE) / 2.0f;

    private Vibrator vibrator;

    private GvrAudioEngine gvrAudioEngine;
    private volatile int sourceId = GvrAudioEngine.INVALID_ID;
    private volatile int successSourceId = GvrAudioEngine.INVALID_ID;


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
    public void onPause() {
        gvrAudioEngine.pause();
        eyeController.disconnect();
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        gvrAudioEngine.resume();
        eyeController.connect();
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
        cube.setColors(WorldLayoutData.CUBE_COLORS);
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
        updateModelPosition(new float[]{0.0f, 0.0f, -MAX_MODEL_DISTANCE / 2.0f});

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
        cube.rotate(TIME_DELTA, 0.5f, 0.5f, 1.0f);

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
        cube.setColors(isLookingAtObject()
                ? WorldLayoutData.CUBE_FOUND_COLORS : WorldLayoutData.CUBE_COLORS);
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

        if (isLookingAtObject() || (int) (Math.random() * 3) == 1) { // TODO(abhi): remove hack
            successSourceId = gvrAudioEngine.createStereoSound(SUCCESS_SOUND_FILE);
            gvrAudioEngine.playSound(successSourceId, false /* looping disabled */);
            moveObject();
        }

        // Always give user feedback.
        vibrator.vibrate(50);
    }

    /**
     * Find a new random position for the object.
     * <p>
     * <p>We'll rotate it around the Y-axis so it's out of sight, and then up or down by a little
     * bit.
     */
    protected void moveObject() {
        Model rotation = new Model("Rotation");

        // First rotate in XZ plane, between 90 and 270 deg away, and scale so that we vary
        // the object's distance from the user.
        float angleXZ = (float) Math.random() * 180 + 90;
        rotation.rotate(angleXZ, 0f, 1f, 0f);
        float oldObjectDistance = objectDistance;
        objectDistance = MIN_MODEL_DISTANCE +
                (float) Math.random() * (MAX_MODEL_DISTANCE - MIN_MODEL_DISTANCE);
        float objectScalingFactor = objectDistance / oldObjectDistance;
        rotation.scale(objectScalingFactor, objectScalingFactor, objectScalingFactor);
        Position position = rotation.multiply(cube, 12);

        float angleY = (float) Math.random() * 80 - 40; // Angle in Y plane, between -40 and 40.
        angleY = (float) Math.toRadians(angleY);
        float newY = (float) Math.tan(angleY) * objectDistance;

        updateModelPosition(new float[] { position.value[0], newY, position.value[2] });
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
        Log.v(TAG, gestureName);
        switch (gestureName) {
            case "blink":
                onCardboardTrigger();
                break;
        }
    }

    @Override
    public void setEyeEventSource(EyeEvent.Source eyeEventSource) {
        eyeEventSource.add(new Gesture("blink")
                .add(EyeEvent.Criterion.saccade(EyeEvent.Direction.UP, 2000))
                .add(EyeEvent.Criterion.saccade(EyeEvent.Direction.DOWN, 4000))
                .add(EyeEvent.Criterion.saccade(EyeEvent.Direction.UP, 2000))
                .addObserver(this));
    }
}
