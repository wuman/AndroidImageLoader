package com.wuman.androidimageloader.samples;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.wuman.androidimageloader.ImageLoader;
import com.wuman.androidimageloader.ImageViewBinder;
import com.wuman.androidimageloader.samples.ui.DebugView;

public class ImageViewBindingFragment extends ListFragment {

    private ImageViewBinder mImageViewBinder;
    private DebugView mDebugView;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mImageViewBinder = new ImageViewBinder(ImageLoader.get(getActivity()));
        mImageViewBinder.setLoadingResource(R.drawable.loading);
        mImageViewBinder.setErrorResource(R.drawable.unavailable);

        setListAdapter(new SamplesAdapter(getActivity(), mImageViewBinder));

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

    private static final class SamplesAdapter extends AbstractSamplesAdapter {

        private final ImageViewBinder mImageViewBinder;

        public SamplesAdapter(Context context, ImageViewBinder imageViewBinder) {
            super(context);
            mImageViewBinder = imageViewBinder;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            String url = (String) getItem(position);

            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.list_item, parent,
                        false);
                convertView
                        .setTag(new Holder((ImageView) convertView
                                .findViewById(android.R.id.icon1),
                                (TextView) convertView
                                        .findViewById(android.R.id.text1)));
            }

            Holder holder = (Holder) convertView.getTag();
            holder.textView.setText("Image #" + position);
            mImageViewBinder.bind(holder.imageView, url);

            return convertView;
        }

        private static class Holder {
            ImageView imageView;
            TextView textView;

            public Holder(ImageView imageView, TextView textView) {
                super();
                this.imageView = imageView;
                this.textView = textView;
            }
        }

    }

}
