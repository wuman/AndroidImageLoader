package com.wuman.androidimageloader.samples;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.wuman.androidimageloader.AbstractViewBinder;
import com.wuman.androidimageloader.ImageLoader;
import com.wuman.androidimageloader.ImageLoader.LoadResult;
import com.wuman.androidimageloader.ImageLoader.LoadSource;
import com.wuman.androidimageloader.samples.ui.DebugView;

public class CustomViewBindingFragment extends ListFragment {

    private DebugView mDebugView;
    private CustomBinder mCustomBinder;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mCustomBinder = new CustomBinder(ImageLoader.get(getActivity()));
        mCustomBinder.setLoadingResource(R.drawable.loading);
        mCustomBinder.setErrorResource(R.drawable.unavailable);

        setListAdapter(new SamplesAdapter(getActivity(), mCustomBinder));

        // Show debug stats
        mDebugView.post(new Runnable() {
            public void run() {
                if (!isDetached() && isAdded()) {
                    mDebugView.setCacheDebugStats(ImageLoader
                            .get(getActivity()).getCacheDebugStats());
                    mDebugView.postDelayed(this, DebugView.PERIOD);
                }
            }
        });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View root = super.onCreateView(inflater, container, savedInstanceState);

        // Attach DebugView
        FrameLayout listContainer = (FrameLayout) root.findViewById(0x00ff0003);
        if (listContainer != null) {
            mDebugView = new DebugView(root.getContext());
            listContainer.addView(mDebugView, new FrameLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.RIGHT));
        }

        return root;
    }

    private static final class CustomBinder extends
            AbstractViewBinder<TextView> {

        public CustomBinder(ImageLoader imageLoader) {
            super(imageLoader);
        }

        @Override
        public void unbind(TextView view) {
            super.unbind(view);
            view.setCompoundDrawables(null, null, null, null);
        }

        private static void setLeftCompoundDrawable(TextView view, Drawable d) {
            Resources r = view.getResources();
            final int size = r
                    .getDimensionPixelSize(android.R.dimen.app_icon_size);
            d.setBounds(0, 0, size, size);
            view.setCompoundDrawables(d, null, null, null);
        }

        @Override
        public void bind(TextView view, String url) {
            super.bind(view, url);

            LoadResult bindResult = mImageLoader.load(url, new ViewCallback(
                    view));
            if (bindResult == LoadResult.LOADING) {
                Drawable d = view.getResources()
                        .getDrawable(R.drawable.loading);
                setLeftCompoundDrawable(view, d);
            }
        }

        @Override
        protected void onImageLoaded(TextView view, Bitmap bitmap, String url,
                LoadSource loadSource) {
            Drawable d = new BitmapDrawable(view.getResources(), bitmap);
            setLeftCompoundDrawable(view, d);
        }

        @Override
        protected void onImageError(TextView view, String url, Throwable error) {
            Drawable d = view.getResources()
                    .getDrawable(R.drawable.unavailable);
            setLeftCompoundDrawable(view, d);
        }

    }

    private static final class SamplesAdapter extends AbstractSamplesAdapter {

        private final CustomBinder mCustomBinder;

        public SamplesAdapter(Context context, CustomBinder customBinder) {
            super(context);
            mCustomBinder = customBinder;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            String url = (String) getItem(position);

            if (convertView == null) {
                convertView = mInflater.inflate(
                        android.R.layout.simple_list_item_1, parent, false);
                TextView view = (TextView) convertView;
                final int padding = view.getResources().getDimensionPixelSize(
                        R.dimen.padding_medium);
                view.setCompoundDrawablePadding(padding);
                view.setTextAppearance(view.getContext(),
                        android.R.style.TextAppearance_Large);
            }

            TextView view = (TextView) convertView;
            view.setText("Image #" + position);
            mCustomBinder.bind(view, url);

            return convertView;
        }

    }

}
