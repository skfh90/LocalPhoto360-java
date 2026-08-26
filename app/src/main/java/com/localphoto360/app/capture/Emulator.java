package com.localphoto360.app.capture;

import android.os.Build;

public final class Emulator {
    private Emulator() {
    }

    public static boolean isProbablyEmulator() {
        String fingerprint = Build.FINGERPRINT.toLowerCase();
        String model = Build.MODEL.toLowerCase();
        String product = Build.PRODUCT.toLowerCase();
        String hardware = Build.HARDWARE.toLowerCase();
        return fingerprint.contains("generic")
                || fingerprint.contains("emulator")
                || model.contains("emulator")
                || model.contains("android sdk")
                || product.contains("sdk")
                || hardware.contains("ranchu")
                || hardware.contains("goldfish");
    }
}
