package com.hardbacknutter.tinyzxingwrapper.scanner;

@SuppressWarnings({"unused", "WeakerAccess"})
public enum ScanMode {
    Single(0),
    Continuous(1);

    private final int mode;

    ScanMode(final int mode) {
        this.mode = mode;
    }

    public int getAsInt() {
        return mode;
    }

    public static ScanMode getMode(final int mode) {
        switch (mode) {
            case 1:
                return Continuous;
            case 0:
            default:
                return Single;
        }
    }
}
