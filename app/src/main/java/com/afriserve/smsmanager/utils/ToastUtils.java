package com.afriserve.smsmanager.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

/**
 * ToastUtils — v2
 *
 * Changes vs v1:
 *  - Fixed garbled Unicode prefixes (UTF-8 mojibake artifacts replaced with
 *    plain ASCII symbols: ✓ → [OK], ✗ → [ERR], ℹ → [i], ⚠ → [!]).
 *  - All show*() methods are now safe to call from background threads;
 *    they post to the main handler automatically.
 *  - Added {@link #show(Context, String)} and {@link #showLong(Context, String)}
 *    static variants that don't require a pre-built instance.
 *  - Removed mutable singleton — callers that need an instance should
 *    create one per-component or inject it via Hilt if needed.
 */
public final class ToastUtils {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private final Context appContext;

    public ToastUtils(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static ToastUtils getInstance(@NonNull Context context) {
        return new ToastUtils(context);
    }

    // ── Instance methods ──────────────────────────────────────────────────────

    public void showSuccess(String message) {
        showOnMain("[OK] " + message, Toast.LENGTH_SHORT);
    }

    public void showError(String message) {
        showOnMain("[ERR] " + message, Toast.LENGTH_LONG);
    }

    public void showInfo(String message) {
        showOnMain("[i] " + message, Toast.LENGTH_SHORT);
    }

    public void showWarning(String message) {
        showOnMain("[!] " + message, Toast.LENGTH_LONG);
    }

    public void show(String message) {
        showOnMain(message, Toast.LENGTH_SHORT);
    }

    public void showLong(String message) {
        showOnMain(message, Toast.LENGTH_LONG);
    }

    public void show(@StringRes int resId) {
        showOnMain(appContext.getString(resId), Toast.LENGTH_SHORT);
    }

    // ── Static convenience variants ───────────────────────────────────────────

    public static void show(@NonNull Context context, String message) {
        postToast(context, message, Toast.LENGTH_SHORT);
    }

    public static void showLong(@NonNull Context context, String message) {
        postToast(context, message, Toast.LENGTH_LONG);
    }

    public static void showError(@NonNull Context context, String message) {
        postToast(context, "[ERR] " + message, Toast.LENGTH_LONG);
    }

    public static void showSuccess(@NonNull Context context, String message) {
        postToast(context, "[OK] " + message, Toast.LENGTH_SHORT);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void showOnMain(String text, int duration) {
        postToast(appContext, text, duration);
    }

    private static void postToast(@NonNull Context ctx, String text, int duration) {
        final Context appCtx = ctx.getApplicationContext();
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Toast.makeText(appCtx, text, duration).show();
        } else {
            MAIN.post(() -> Toast.makeText(appCtx, text, duration).show());
        }
    }
}
