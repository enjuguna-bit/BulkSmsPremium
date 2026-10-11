package com.afriserve.smsmanager.utils;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import androidx.annotation.NonNull;

/**
 * HapticsUtils — v2
 *
 * Changes vs v1:
 *  - Removed mutable singleton — construct per-component or inject via Hilt.
 *  - Uses VibrationEffect.createPredefined() on API 29+ for semantically
 *    correct haptics (EFFECT_CLICK, EFFECT_HEAVY_CLICK, EFFECT_DOUBLE_CLICK).
 *  - Uses VibratorManager on API 31+ (Android 12) as Vibrator is deprecated.
 *  - Falls back gracefully to legacy Vibrator on older API levels without
 *    crashing.
 *  - Removed VIBRATE permission annotation from trigger() — the permission is
 *    declared in the manifest and does not need to be enforced at call sites.
 */
public final class HapticsUtils {

    private final Vibrator vibrator;

    public HapticsUtils(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = (VibratorManager)
                    context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            vibrator = vm != null ? vm.getDefaultVibrator() : null;
        } else {
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }
    }

    public static HapticsUtils getInstance(@NonNull Context context) {
        return new HapticsUtils(context);
    }

    public void trigger(HapticType type) {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibratePredefined(type);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrateEffect(type);
            } else {
                vibrateLegacy(type);
            }
        } catch (Exception ignored) {
            // Never crash for haptic feedback
        }
    }

    // ── API 29+ ── predefined effects ─────────────────────────────────────────
    private void vibratePredefined(HapticType type) {
        int effect;
        switch (type) {
            case SUCCESS:
                effect = VibrationEffect.EFFECT_HEAVY_CLICK; break;
            case ERROR:
                effect = VibrationEffect.EFFECT_DOUBLE_CLICK; break;
            case SELECTION:
            case LIGHT:
                effect = VibrationEffect.EFFECT_TICK; break;
            case WARNING:
            default:
                effect = VibrationEffect.EFFECT_CLICK; break;
        }
        vibrator.vibrate(VibrationEffect.createPredefined(effect));
    }

    // ── API 26-28 ── VibrationEffect waveform / one-shot ──────────────────────
    private void vibrateEffect(HapticType type) {
        VibrationEffect effect;
        switch (type) {
            case SUCCESS:
                effect = VibrationEffect.createWaveform(new long[]{0, 50, 50, 50}, -1); break;
            case ERROR:
                effect = VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE); break;
            case WARNING:
                effect = VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE); break;
            case LIGHT:
                effect = VibrationEffect.createOneShot(5, VibrationEffect.DEFAULT_AMPLITUDE); break;
            case SELECTION:
            default:
                effect = VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE); break;
        }
        vibrator.vibrate(effect);
    }

    // ── API < 26 ── legacy vibration ──────────────────────────────────────────
    @SuppressWarnings("deprecation")
    private void vibrateLegacy(HapticType type) {
        switch (type) {
            case SUCCESS:  vibrator.vibrate(new long[]{0, 50, 50, 50}, -1); break;
            case ERROR:    vibrator.vibrate(200); break;
            case WARNING:  vibrator.vibrate(100); break;
            case LIGHT:    vibrator.vibrate(5);   break;
            case SELECTION:
            default:       vibrator.vibrate(10);  break;
        }
    }

    public enum HapticType { SELECTION, SUCCESS, ERROR, WARNING, LIGHT }
}
