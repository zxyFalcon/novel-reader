package com.falcon.reader.domain;

/**
 * Persisted window bounds without a dependency on Swing components.
 */
public final class WindowState {
    private final int width;
    private final int height;
    private final int locationX;
    private final int locationY;

    public WindowState(int width, int height, int locationX, int locationY) {
        this.width = width;
        this.height = height;
        this.locationX = locationX;
        this.locationY = locationY;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getLocationX() {
        return locationX;
    }

    public int getLocationY() {
        return locationY;
    }
}
