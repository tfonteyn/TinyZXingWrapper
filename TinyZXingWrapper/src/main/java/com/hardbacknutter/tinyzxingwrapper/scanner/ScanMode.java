package com.hardbacknutter.tinyzxingwrapper.scanner;

/**
 * Possible scan modes.
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public enum ScanMode {
    /**
     * Do a single scan, stop the scanner, and return the result.
     */
    Single(0),
    /**
     * Keep scanning, returning the results after each scan.
     */
    Continuous(1);

    private final int mode;

    ScanMode(final int mode) {
        this.mode = mode;
    }

    /**
     * Helper method for converting a previously stored in from {@link #getAsInt()}
     * back to the enum value.
     *
     * @param mode to lookup
     *
     * @return enum value
     */
    public static ScanMode getMode(final int mode) {
        switch (mode) {
            case 1:
                return Continuous;
            case 0:
            default:
                return Single;
        }
    }

    /**
     * Helper method to retrieve the int value.
     * e.g. for storing in Preferences.
     *
     * @return int
     */
    public int getAsInt() {
        return mode;
    }
}
