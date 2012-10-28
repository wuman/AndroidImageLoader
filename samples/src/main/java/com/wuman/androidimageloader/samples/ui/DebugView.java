package com.wuman.androidimageloader.samples.ui;

import com.wuman.androidimageloader.samples.R;
import com.wuman.androidimageloader.samples.R.string;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.TextView;

public class DebugView extends TextView {

    public static final int PERIOD = 500;

    public DebugView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public DebugView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DebugView(Context context) {
        this(context, null);
    }

    private void init() {
        setPadding(5, 5, 5, 5);
        setTextAppearance(getContext(),
                android.R.style.TextAppearance_Small_Inverse);
        setBackgroundColor(Color.WHITE);
    }

    public final void setCacheDebugStats(String stats) {
        setText(getContext().getString(R.string.debug_stats)
                + "\nmaxRuntime="
                + Runtime.getRuntime().maxMemory()
                + "\n"
                + TextUtils.join("\n", stats.substring(9, stats.length() - 1)
                        .split(",")));
    }

}
