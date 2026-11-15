package it.irbas.tv;

import android.app.Activity;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tutte le app, a righe intere: lo scorrimento va sempre di una riga alla volta, niente caselle tagliate. */
public class AppsActivity extends Activity {
    private static final int COLUMNS = 5;
    private static final int VISIBLE_ROWS = 4;
    private final Map<String, String> labels = new LinkedHashMap<>();
    private ScrollView scroll;
    private int rowHeight;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = new FrameLayout(this);
        root.addView(new BackdropView(this), new FrameLayout.LayoutParams(-1, -1));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(56), dp(30), dp(56), 0);
        page.setClipChildren(false);
        page.setClipToPadding(false);

        TextView title = new TextView(this);
        title.setText("Tutte le app");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setTextColor(Glass.TEXT);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        page.addView(title);
        TextView hint = new TextView(this);
        hint.setText("Indietro per tornare a Irbas");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        hint.setTextColor(Glass.MUTED);
        hint.setPadding(0, dp(4), 0, dp(14));
        page.addView(hint);

        int tileW = dp(150);
        int tileH = dp(84);
        int gapH = dp(14);
        int gapV = dp(18);
        rowHeight = tileH + gapV;

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(COLUMNS);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        grid.setPadding(dp(10), dp(10), dp(10), dp(10));
        List<ResolveInfo> apps = Apps.installed(this, labels);
        for (int i = 0; i < apps.size(); i++) {
            final int row = i / COLUMNS;
            ResolveInfo app = apps.get(i);
            View tile = Apps.tile(this, app, labels.get(app.activityInfo.packageName), 16, (v, focused) -> {
                if (focused) showRow(row);
            });
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = tileW;
            params.height = tileH;
            params.setMargins(0, 0, gapH, gapV);
            grid.addView(tile, params);
        }

        scroll = new ScrollView(this) {
            // niente scorrimento automatico a metà riga: lo decide showRow, a righe intere
            @Override
            protected int computeScrollDeltaToGetChildRectOnScreen(android.graphics.Rect rect) {
                return 0;
            }
        };
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.addView(grid);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, rowHeight * VISIBLE_ROWS + dp(20)));
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        if (grid.getChildCount() > 0) grid.getChildAt(0).requestFocus();
    }

    private void showRow(int row) {
        int first = Math.max(0, row - (VISIBLE_ROWS - 1));
        int current = Math.round(scroll.getScrollY() / (float) rowHeight);
        if (row < current) scroll.smoothScrollTo(0, row * rowHeight);
        else if (row > current + VISIBLE_ROWS - 1) scroll.smoothScrollTo(0, first * rowHeight);
    }

    private int dp(float value) {
        return Glass.dp(this, value);
    }
}
