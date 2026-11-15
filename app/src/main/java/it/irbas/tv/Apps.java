package it.irbas.tv;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Le app installate sul box: elenco, caselle con il loro banner, apertura. */
final class Apps {
    private Apps() {}

    static List<ResolveInfo> installed(Context context, Map<String, String> labels) {
        PackageManager pm = context.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(intent, 0);
        List<ResolveInfo> list = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        labels.clear();
        for (ResolveInfo app : found) {
            String name = app.activityInfo.packageName;
            if (name.equals(context.getPackageName()) || !seen.add(name)) continue;
            CharSequence label = app.loadLabel(pm);
            labels.put(name, label == null ? name : label.toString());
            list.add(app);
        }
        Collections.sort(list, (a, b) -> labels.get(a.activityInfo.packageName).compareToIgnoreCase(labels.get(b.activityInfo.packageName)));
        return list;
    }

    /** Una casella con il banner vero dell'app (o la sua icona, se non ha un banner). */
    static View tile(Context context, ResolveInfo app, String label, float radius, View.OnFocusChangeListener also) {
        PackageManager pm = context.getPackageManager();
        String name = app.activityInfo.packageName;
        FrameLayout tile = new FrameLayout(context);
        tile.setFocusable(true);
        tile.setClickable(true);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(0x14FFFFFF);
        shape.setCornerRadius(Glass.dp(context, radius));
        tile.setBackground(shape);
        tile.setClipToOutline(true);
        tile.setContentDescription(label);

        Drawable banner = app.activityInfo.loadBanner(pm);
        if (banner == null) {
            try {
                banner = pm.getApplicationBanner(name);
            } catch (PackageManager.NameNotFoundException ignored) {
                banner = null;
            }
        }
        if (banner != null) {
            ImageView image = new ImageView(context);
            image.setImageDrawable(banner);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            tile.addView(image, new FrameLayout.LayoutParams(-1, -1));
        } else {
            LinearLayout column = new LinearLayout(context);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.CENTER);
            ImageView icon = new ImageView(context);
            icon.setImageDrawable(app.loadIcon(pm));
            column.addView(icon, new LinearLayout.LayoutParams(Glass.dp(context, 40), Glass.dp(context, 40)));
            TextView caption = new TextView(context);
            caption.setText(label);
            caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            caption.setTextColor(Glass.TEXT);
            caption.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            caption.setGravity(Gravity.CENTER);
            caption.setMaxLines(1);
            caption.setPadding(Glass.dp(context, 6), Glass.dp(context, 6), Glass.dp(context, 6), 0);
            column.addView(caption);
            tile.addView(column, new FrameLayout.LayoutParams(-1, -1));
        }

        // un velo di vetro sopra il banner, che sparisce quando la casella è selezionata
        View veil = new View(context);
        veil.setBackground(new GlassDrawable(context, radius, 0f, false));
        tile.addView(veil, new FrameLayout.LayoutParams(-1, -1));
        View ring = new View(context);
        ring.setBackground(Glass.ring(context, radius));
        ring.setVisibility(View.INVISIBLE);
        tile.addView(ring, new FrameLayout.LayoutParams(-1, -1));

        tile.setAlpha(0.88f);
        tile.setOnFocusChangeListener((v, focused) -> {
            ring.setVisibility(focused ? View.VISIBLE : View.INVISIBLE);
            veil.setVisibility(focused ? View.INVISIBLE : View.VISIBLE);
            v.animate().scaleX(focused ? 1.1f : 1f).scaleY(focused ? 1.1f : 1f).alpha(focused ? 1f : 0.88f)
                    .translationZ(focused ? Glass.dp(context, 12) : 0).setDuration(160).start();
            if (also != null) also.onFocusChange(v, focused);
        });
        tile.setOnClickListener(v -> open(context, name));
        return tile;
    }

    /** L'app si può aprire? (No, per esempio, se è stata disattivata.) */
    static boolean canOpen(Context context, String packageName) {
        PackageManager pm = context.getPackageManager();
        return pm.getLeanbackLaunchIntentForPackage(packageName) != null || pm.getLaunchIntentForPackage(packageName) != null;
    }

    static void open(Context context, String packageName) {
        // TIMVISION aperta apposta: il tasto Home la lascia stare finché non lo premi
        if (HomeKeyService.TIM.equals(packageName)) {
            IrbasLink.prefs(context).edit().putBoolean("timAllowed", true).apply();
        }
        PackageManager pm = context.getPackageManager();
        Intent intent = pm.getLeanbackLaunchIntentForPackage(packageName);
        if (intent == null) intent = pm.getLaunchIntentForPackage(packageName);
        if (intent == null) {
            Toast.makeText(context, "Questa app non si apre da qui", Toast.LENGTH_SHORT).show();
            return;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "Questa app non si apre da qui", Toast.LENGTH_SHORT).show();
        }
    }
}
