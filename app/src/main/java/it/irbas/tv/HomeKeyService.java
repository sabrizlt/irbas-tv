package it.irbas.tv;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Il servizio di Irbas TV (da attivare in Impostazioni › Accessibilità):
 * - il tasto Home del telecomando torna a Irbas TV, e se compare la schermata originale della TV la sostituisce;
 * - il telecomando sul telefono può muoversi, premere OK, tornare indietro e scrivere anche senza il debug di rete.
 */
public class HomeKeyService extends AccessibilityService {
    static final String TIM = "timvision.launcher";
    /** Le schermate principali originali che Irbas TV sostituisce. */
    static final Set<String> STOCK = new HashSet<>(Arrays.asList(
            TIM, "com.google.android.apps.tv.launcherx", "com.google.android.tvlauncher", "com.google.android.leanbacklauncher"));

    private static HomeKeyService instance;
    private long lastJump;

    static HomeKeyService get() {
        return instance;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;
        if (event.getAction() == KeyEvent.ACTION_UP) {
            IrbasLink.prefs(this).edit().putBoolean("timAllowed", false).apply();
            goHome();
        }
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = event.getPackageName();
        if (pkg == null || !STOCK.contains(pkg.toString())) return;
        if (TIM.contentEquals(pkg) && IrbasLink.prefs(this).getBoolean("timAllowed", false)) return;
        goHome();
    }

    void goHome() {
        long now = System.currentTimeMillis();
        if (now - lastJump < 800) return;   // niente rimbalzi
        lastJump = now;
        launch(new Intent(this, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }

    /** Apre un'attività anche quando Irbas TV non è in primo piano. */
    boolean launch(Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // MARK: il telecomando del telefono, senza debug di rete

    /** Sposta la selezione nella direzione data (View.FOCUS_UP, …). */
    boolean move(int direction) {
        AccessibilityNodeInfo focus = focused();
        if (focus == null) return false;
        AccessibilityNodeInfo next = focus.focusSearch(direction);
        return next != null && next.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
    }

    boolean ok() {
        AccessibilityNodeInfo focus = focused();
        return focus != null && focus.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    boolean back() {
        return performGlobalAction(GLOBAL_ACTION_BACK);
    }

    boolean type(String text) {
        AccessibilityNodeInfo focus = focused();
        if (focus == null || !focus.isEditable()) return false;
        Bundle arguments = new Bundle();
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        return focus.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
    }

    private AccessibilityNodeInfo focused() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        AccessibilityNodeInfo focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        return focus != null ? focus : root;
    }

    static int direction(String key) {
        switch (key) {
            case "up": return View.FOCUS_UP;
            case "down": return View.FOCUS_DOWN;
            case "left": return View.FOCUS_LEFT;
            case "right": return View.FOCUS_RIGHT;
            default: return 0;
        }
    }
}
