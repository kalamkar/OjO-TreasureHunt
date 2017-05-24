package com.ojogaze.treasurehunt.oogles20;

import android.opengl.GLES20;
import android.opengl.Matrix;

import com.ojogaze.treasurehunt.Utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Created by abhi on 5/19/17.
 */

public class Model {
    private static final String TAG = "Model20";

    private static final int COORDS_PER_VERTEX = 3;

    // Convenience vector for extracting the position from a matrix via multiplication.
    private static final float[] POS_MATRIX_MULTIPLY_VEC = {0, 0, 0, 1.0f};

    public final String name;
    private int programId = 0;

    public final float[] value;

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

    private final int drawArrayStart;
    private final int drawArrayCount; // 36 for cube

    public Model(String name) {
        this(name, new float[16], 0, 24);  // 36 for cube
    }

    public Model(String name, int drawArrayStart, int drawArrayCount) {
        this(name, new float[16], drawArrayStart, drawArrayCount);
    }

    public Model(String name, float value[]) {
        this(name, value, 0, 24);  // 36 for cube
    }

    public Model(String name, float value[], int drawArrayStart, int drawArrayCount) {
        this.name = name;
        this.value = value;

        this.drawArrayStart = drawArrayStart;
        this.drawArrayCount = drawArrayCount;
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

    public void attachShaders(Shader[] shaders) {
        programId = GLES20.glCreateProgram();
        for (Shader shader : shaders) {
            GLES20.glAttachShader(programId, shader.id);
        }
        GLES20.glLinkProgram(programId);
        GLES20.glUseProgram(programId);

        Utils.checkGLError(name + " program");

        modelParam = GLES20.glGetUniformLocation(programId, "u_Model");
        modelViewParam = GLES20.glGetUniformLocation(programId, "u_MVMatrix");
        modelViewProjectionParam = GLES20.glGetUniformLocation(programId, "u_MVP");
        lightPosParam = GLES20.glGetUniformLocation(programId, "u_LightPos");

        positionParam = GLES20.glGetAttribLocation(programId, "a_Position");
        normalParam = GLES20.glGetAttribLocation(programId, "a_Normal");
        colorParam = GLES20.glGetAttribLocation(programId, "a_Color");

        Utils.checkGLError(name + " program params");
    }

    /**
     * Draw the model.
     * <p>
     * <p>This feeds in data for the model into the shader. Note that this doesn't feed in data
     * about position of the light, so if we rewrite our code to draw the model first,
     * the lighting might look strange.
     */
    public void draw(Model modelView, Model modelViewProjection, Position lightPosInEyeSpace) {
        GLES20.glUseProgram(programId);

        // Set ModelView, MVP, position, normals, and color.
        GLES20.glUniform3fv(lightPosParam, 1, lightPosInEyeSpace.value, 0);
        GLES20.glUniformMatrix4fv(modelParam, 1, false, value, 0);
        GLES20.glUniformMatrix4fv(modelViewParam, 1, false, modelView.value, 0);
        GLES20.glUniformMatrix4fv(modelViewProjectionParam, 1, false, modelViewProjection.value, 0);
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

        Utils.checkGLError("drawing " + name);
    }

    public void rotate(float angle, float x, float y, float z) {
        rotate(0, angle, x, y, z);
    }

    public void rotate(int offset, float angle, float x, float y, float z) {
        Matrix.rotateM(value, offset, angle, x, y ,z);
    }

    public void scale(float x, float y, float z) {
        scale(0, x, y, z);
    }

    public void scale(int offset, float x, float y, float z) {
        Matrix.scaleM(value, offset, x, y ,z);
    }

    public void translate(float x, float y, float z) {
        translate(0, x, y, z);
    }

    public void translate(int offset, float x, float y, float z) {
        Matrix.setIdentityM(value, offset);
        Matrix.translateM(value, offset, x, y, z);
    }

    public Model multiply(Model model) {
        float result[] = new float[16];
        Matrix.multiplyMM(result, 0, value, 0, model.value, 0);
        return new Model(this.name + model.name, result);
    }

    public Position multiply(Model model, int offset) {
        float result[] = new float[4];
        Matrix.multiplyMV(result, 0, value, 0, model.value, offset);
        return new Position(name + model.name, result);
    }

    public Position multiply(Position position) {
        float result[] = new float[4];
        Matrix.multiplyMV(result, 0, value, 0, position.value, 0);
        return new Position(this.name + position.name, result);
    }

    public Position getPosition() {
        Position position = new Position(name + "Position");
        Matrix.multiplyMV(position.value, 0, value, 0, POS_MATRIX_MULTIPLY_VEC, 0);
        return position;
    }
}
