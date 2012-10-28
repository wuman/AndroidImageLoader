package com.wuman.androidimageloader.samples;

import android.content.Context;
import android.view.LayoutInflater;
import android.widget.BaseAdapter;

abstract class AbstractSamplesAdapter extends BaseAdapter {

    private static final int NUM_IMAGES = 100;

    final LayoutInflater mInflater;

    public AbstractSamplesAdapter(Context context) {
        mInflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return NUM_IMAGES;
    }

    @Override
    public Object getItem(int position) {
        /**
         * These great sample images are taken from Alexander Blom's
         * WebImageLoader project on <a href=
         * "https://github.com/lexs/webimageloader/tree/master/extras/numbers"
         * >GitHub</a>.
         */
        return "https://raw.github.com/wuman/AndroidImageLoader/master/extras/numbers/"
                + position + ".png";
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

}