# TinyZXingWrapper
A tiny android wrapper for the ZXing barcode scanner library (https://github.com/zxing/zxing). 

- Uses CameraX.

- Written as a replacement for https://github.com/journeyapps/zxing-android-embedded.
  Not call-compatible, but it should be (it is!) easy to migrate to.

- Minimum API 26 (Android 8.0)
- Compiled against ZXing core 3.5.1

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

    com.hardbacknutter.tinyzxingwrapper:TinyZXingWrapper:1.0.1:release@aar
