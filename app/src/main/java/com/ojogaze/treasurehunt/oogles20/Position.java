package com.ojogaze.treasurehunt.oogles20;

/**
 * Created by abhi on 5/23/17.
 */

public class Position {
    public final String name;
    public final float[] value;

    public Position(String name) {
        this(name, new float[4]);
    }

    public Position(String name, float value[]) {
        this.name = name;
        this.value = value;
    }
}
