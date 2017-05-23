package com.ojogaze.treasurehunt.oogles20;

import android.content.Context;
import android.opengl.GLES20;
import android.util.Log;

import com.ojogaze.treasurehunt.Utils;

/**
 * Created by abhi on 5/23/17.
 */

public class Shader {
    private static final String TAG = "Shader";

    public final int id;

    private Shader(int id) {
        this.id = id;
    }

    /**
     * Converts a raw text file, saved as a resource, into an OpenGL ES shader.
     *
     * @param resId The resource id of the raw text file about to be turned into a shader.
     * @param type  The type of shader we will be creating.
     * @param context Context to load resource from.
     * @return The shader object handler.
     */
    public static Shader load(int resId, int type, Context context) {
        return load(Utils.readRawTextFile(resId, context), type);
    }

    /**
     * Converts a raw text file, saved as a resource, into an OpenGL ES shader.
     *
     * @param code The raw text file about to be turned into a shader.
     * @param type  The type of shader we will be creating.
     * @return The shader object handler.
     */
    public static Shader load(String code, int type) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, code);
        GLES20.glCompileShader(shader);

        // Get the compilation status.
        final int[] compileStatus = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);

        // If the compilation failed, delete the shader.
        if (compileStatus[0] == 0) {
            Log.e(TAG, "Error compiling shader: " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            shader = 0;
        }

        if (shader == 0) {
            throw new RuntimeException("Error creating shader.");
        }

        return new Shader(shader);
    }
}
