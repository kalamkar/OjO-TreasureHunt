package com.ojogaze.treasurehunt;

import android.opengl.GLES20;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Created by abhi on 5/19/17.
 */

public class Model20 {
    private static final String TAG = "Model20";

    private static final int COORDS_PER_VERTEX = 3;

    public final float[] lightPosInEyeSpace = new float[4];

    public final String name;
    private int programId;

    public final float[] modelValue = new float[16];

    private FloatBuffer vertices;
    private FloatBuffer colors;
    private FloatBuffer normals;

    private int positionParam;
    private int normalParam;
    private int colorParam;
    private int modelParam;
    private int modelViewParam;
    private int modelViewProjectionParam;
    private int lightPosParam;

    public int drawArrayStart = 0;
    public int drawArrayCount = 24; // 36 for cube

    public Model20(String name) {
        this.name = name;
    }

    public void setVertices(float[] vertices) {
        this.vertices = ByteBuffer.allocateDirect(vertices.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        this.vertices.put(vertices);
        this.vertices.position(0);
    }

    public void setColors(float[] colors) {
        this.colors = ByteBuffer.allocateDirect(colors.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        this.colors.put(colors);
        this.colors.position(0);
    }

    public void setNormals(float[] normals) {
        this.normals = ByteBuffer.allocateDirect(normals.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        this.normals.put(normals);
        this.normals.position(0);
    }

    public void loadProgramParams(int[] shaders) {
        programId = GLES20.glCreateProgram();
        for (int shader : shaders) {
            GLES20.glAttachShader(programId, shader);
        }
        GLES20.glLinkProgram(programId);
        GLES20.glUseProgram(programId);

        checkGLError(name + " program");

        modelParam = GLES20.glGetUniformLocation(programId, "u_Model");
        modelViewParam = GLES20.glGetUniformLocation(programId, "u_MVMatrix");
        modelViewProjectionParam = GLES20.glGetUniformLocation(programId, "u_MVP");
        lightPosParam = GLES20.glGetUniformLocation(programId, "u_LightPos");

        positionParam = GLES20.glGetAttribLocation(programId, "a_Position");
        normalParam = GLES20.glGetAttribLocation(programId, "a_Normal");
        colorParam = GLES20.glGetAttribLocation(programId, "a_Color");

        checkGLError(name + " program params");
    }

    /**
     * Draw the model.
     * <p>
     * <p>This feeds in data for the model into the shader. Note that this doesn't feed in data
     * about position of the light, so if we rewrite our code to draw the model first,
     * the lighting might look strange.
     */
    public void draw(float[] modelView, float[] modelViewProjection) {
        GLES20.glUseProgram(programId);

        // Set ModelView, MVP, position, normals, and color.
        GLES20.glUniform3fv(lightPosParam, 1, lightPosInEyeSpace, 0);
        GLES20.glUniformMatrix4fv(modelParam, 1, false, modelValue, 0);
        GLES20.glUniformMatrix4fv(modelViewParam, 1, false, modelView, 0);
        GLES20.glUniformMatrix4fv(modelViewProjectionParam, 1, false, modelViewProjection, 0);
        GLES20.glVertexAttribPointer(
                positionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, vertices);
        GLES20.glVertexAttribPointer(normalParam, 3, GLES20.GL_FLOAT, false, 0, normals);
        GLES20.glVertexAttribPointer(colorParam, 4, GLES20.GL_FLOAT, false, 0, colors);

        GLES20.glEnableVertexAttribArray(positionParam);
        GLES20.glEnableVertexAttribArray(normalParam);
        GLES20.glEnableVertexAttribArray(colorParam);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, drawArrayStart, drawArrayCount);

        GLES20.glDisableVertexAttribArray(positionParam);
        GLES20.glDisableVertexAttribArray(normalParam);
        GLES20.glDisableVertexAttribArray(colorParam);

        checkGLError("drawing " + name);
    }

    /**
     * Checks if we've had an error inside of OpenGL ES, and if so what that error is.
     *
     * @param label Label to report in case of error.
     */
    public static void checkGLError(String label) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, label + ": glError " + error);
            throw new RuntimeException(label + ": glError " + error);
        }
    }
}
