package com.wuman.androidimageloader;

import android.graphics.Bitmap;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;

import com.nineoldandroids.animation.ObjectAnimator;
import com.nineoldandroids.view.ViewPropertyAnimator;
import com.wuman.androidimageloader.ImageLoader.LoadResult;
import com.wuman.androidimageloader.ImageLoader.LoadSource;

public class ImageViewBinder extends AbstractViewBinder<ImageView> implements
        AbsListView.OnScrollListener {

    // Experimental, debug only
    private final boolean LOAD_ON_FLING = false;

    private int mScrollState;
    private boolean mFadeIn;

    public ImageViewBinder(ImageLoader imageLoader) {
        super(imageLoader);
        mScrollState = OnScrollListener.SCROLL_STATE_IDLE;
        mFadeIn = true;
    }

    public ImageViewBinder setFadeIn(boolean fadeIn) {
        mFadeIn = fadeIn;
        return this;
    }

    @Override
    public void unbind(ImageView view) {
        super.unbind(view);
        ViewPropertyAnimator.animate(view).cancel();
        view.setImageDrawable(null);
    }

    @Override
    public void bind(ImageView view, String url) {
        super.bind(view, url);

        // @formatter:off
        if (LOAD_ON_FLING
                && mScrollState == OnScrollListener.SCROLL_STATE_FLING) {
            Bitmap bitmap = mImageLoader.loadOnlyFromMemCache(url);
            if (bitmap != null) {
                view.setImageBitmap(bitmap);
            } else {
                view.setImageResource(mLoadingResource);
            }
        } else 
        // @formatter:on
        {
            LoadResult bindResult = mImageLoader.load(url, new ViewCallback(
                    view));
            if (bindResult == LoadResult.LOADING) {
                view.setImageResource(mLoadingResource);
            }
        }
    }

    @Override
    protected void onImageLoaded(ImageView view, Bitmap bitmap, String url,
            LoadSource loadSource) {
        view.setImageBitmap(bitmap);
        if (loadSource != LoadSource.CACHE_MEMORY && mFadeIn) {
            ObjectAnimator.ofFloat(view, "alpha", 0.0f, 1.0f).setDuration(400)
                    .start();
        }
    }

    @Override
    protected void onImageError(ImageView view, String url, Throwable error) {
        view.setImageResource(mErrorResource);
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem,
            int visibleItemCount, int totalItemCount) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        mScrollState = scrollState;
        if (LOAD_ON_FLING && scrollState != OnScrollListener.SCROLL_STATE_FLING) {
            ListAdapter adapter = view.getAdapter();
            if (adapter != null && adapter instanceof BaseAdapter) {
                ((BaseAdapter) adapter).notifyDataSetChanged();
            }
        }
    }

}
