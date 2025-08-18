# TinyZXingWrapper
A tiny android wrapper for the ZXing barcode scanner library (https://github.com/zxing/zxing). 

- Uses CameraX.
- Written as a replacement for https://github.com/journeyapps/zxing-android-embedded.
  Not call-compatible, but it's very easy to migrate.

### Device support:
- Requires minimal Android 8.0 (API 26)
- Supported/tested up to Android 16 (API 36).
- Compiled against ZXing core 3.5.3

### Usage:
Add a repository:

    ivy {
        url "https://github.com/tfonteyn/"
        metadataSources {
            artifact()
        }
        patternLayout {
            artifact "/[module]/releases/download/v[revision]/"
                     +"[module]-[classifier]-[revision].[ext]"
        }
    }

Gradle dependency string:

    com.hardbacknutter.tinyzxingwrapper:TinyZXingWrapper:1.3.0:release@aar

### History (Library only)

- 1.3.0: performance improvements, zoom and auto-focus support
- 1.2.0: beta/test builds
- 1.1.0: bugfix for DecodeHintType.POSSIBLE_FORMATS
- 1.0.1: translations added, no lib code changes
- 1.0.0: initial release
