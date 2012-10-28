package com.wuman.androidimageloader;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

import android.graphics.Bitmap;
import android.text.TextUtils;

import com.wuman.androidimageloader.ImageLoader.LoadSource;

public abstract class AbstractViewBinder<T> {

    protected final ImageLoader mImageLoader;
    protected final Map<T, String> mViewBindings;
    protected int mLoadingResource;
    protected int mErrorResource;

    public AbstractViewBinder(ImageLoader imageLoader) {
        super();
        mImageLoader = imageLoader;
        mViewBindings = new WeakHashMap<T, String>();
        mLoadingResource = mErrorResource = 0;
    }

    public final ImageLoader getImageLoader() {
        return mImageLoader;
    }

    public final void setLoadingResource(int loading) {
        mLoadingResource = loading;
    }

    public final void setErrorResource(int error) {
        mErrorResource = error;
    }

    public void unbind(T view) {
        mViewBindings.remove(view);
    }

    public void bind(T view, String url) {
        if (view == null) {
            throw new NullPointerException("view is null");
        }
        if (url == null) {
            throw new NullPointerException("URL is null");
        }

        // reset if wrong URL
        if (!TextUtils.equals(url, mViewBindings.get(view))) {
            unbind(view);
        }

        mViewBindings.put(view, url);
    }

    protected abstract void onImageLoaded(T view, Bitmap bitmap, String url,
            LoadSource loadSource);

    protected abstract void onImageError(T view, String url, Throwable error);

    protected class ViewCallback implements ImageLoader.Callback {
        private final WeakReference<T> mViewReference;

        public ViewCallback(T imageView) {
            super();
            mViewReference = new WeakReference<T>(imageView);
        }

        @Override
        public void onImageLoaded(Bitmap bitmap, String url,
                LoadSource loadSource) {
            T view = mViewReference.get();
            String binding = mViewBindings.get(view);
            if (view == null || bitmap == null
                    || !TextUtils.equals(binding, url)) {
                return;
            }
            AbstractViewBinder.this
                    .onImageLoaded(view, bitmap, url, loadSource);
        }

        @Override
        public void onImageError(String url, Throwable error) {
            T view = mViewReference.get();
            String binding = mViewBindings.get(view);
            if (view == null || error == null
                    || !TextUtils.equals(binding, url)) {
                return;
            }
            AbstractViewBinder.this.onImageError(view, url, error);
        }
    }

}
