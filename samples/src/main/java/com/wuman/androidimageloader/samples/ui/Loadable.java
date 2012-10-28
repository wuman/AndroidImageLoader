/*-
 * Copyright (C) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wuman.androidimageloader.samples.ui;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;

import com.wuman.androidfeedloader.FeedExtras;
import com.wuman.androidimageloader.samples.provider.SamplesContract;

/**
 * Manages the arguments {@link Bundle} passed to {@link LoaderManager} to
 * control how many items are loaded and how fresh the content is.
 * <p>
 * Use this class instead of calling {@link LoaderManager} methods like
 * {@link LoaderManager#initLoader(int, Bundle, LoaderCallbacks)} and
 * {@link LoaderManager#restartLoader(int, Bundle, LoaderCallbacks)} directly.
 * 
 * @see #refresh()
 * @see #loadMore()
 */
public final class Loadable implements LoaderManager.LoaderCallbacks<Cursor> {

    static final int PAGE_SIZE = 10;

    private static final String ARG_NUMBER = "loadable:number";

    private static final String ARG_MAX_AGE = "loadable:max-age";

    private final LoaderManager mLoaderManager;

    private final int mLoaderId;

    private final LoaderManager.LoaderCallbacks<Cursor> mCallbacks;

    private int mTargetCount = PAGE_SIZE;

    private boolean mHasError;

    private boolean mHasMore;

    private int mCount;

    public Loadable(LoaderManager loaderManager, int loaderId,
            LoaderCallbacks<Cursor> callbacks) {
        mLoaderManager = loaderManager;
        mLoaderId = loaderId;
        mCallbacks = callbacks;
    }

    private boolean isLoadingMore() {
        return mTargetCount > mCount;
    }

    public boolean isReadyToLoadMore() {
        return mHasMore && !mHasError && !isLoadingMore();
    }

    public void loadMore() {
        mTargetCount = mCount + PAGE_SIZE;
        Bundle args = new Bundle();
        args.putInt(ARG_NUMBER, mTargetCount);
        mLoaderManager.restartLoader(mLoaderId, args, this);
    }

    /**
     * @see LoaderManager#initLoader(int, Bundle, LoaderCallbacks)
     */
    public void init() {
        Bundle args = new Bundle();
        args.putInt(ARG_NUMBER, mTargetCount);
        mLoaderManager.initLoader(mLoaderId, Bundle.EMPTY, this);
    }

    public void init(int n) {
        mTargetCount = n;
        Bundle args = new Bundle();
        args.putInt(ARG_NUMBER, mTargetCount);
        mLoaderManager.initLoader(mLoaderId, args, this);
    }

    /**
     * @see LoaderManager#restartLoader(int, Bundle, LoaderCallbacks)
     */
    public void restart() {
        mLoaderManager.restartLoader(mLoaderId, Bundle.EMPTY, this);
    }

    /**
     * @see LoaderManager#destroyLoader(int)
     */
    public void destroy() {
        mLoaderManager.destroyLoader(mLoaderId);
    }

    public void retry() {
        Bundle args = new Bundle();
        args.putInt(ARG_NUMBER, mTargetCount);
        args.putLong(ARG_MAX_AGE, 0L);
        mLoaderManager.restartLoader(mLoaderId, args, this);
    }

    public void refresh() {
        Bundle args = new Bundle();
        args.putInt(ARG_NUMBER, mCount);
        args.putLong(ARG_MAX_AGE, 0L);
        mLoaderManager.restartLoader(mLoaderId, args, this);
    }

    /**
     * Appends query parameters to a {@link CursorLoader} {@link Uri}.
     * 
     * @param loader
     *            the {@link CursorLoader} to modify.
     * @param args
     *            the arguments.
     * @return the modified {@link CursorLoader}.
     */
    private CursorLoader appendQueryParameters(CursorLoader loader, Bundle args) {
        Uri.Builder builder = loader.getUri().buildUpon();
        if (args.containsKey(ARG_NUMBER)) {
            int n = args.getInt(ARG_NUMBER);
            builder.appendQueryParameter(SamplesContract.PARAM_NUMBER,
                    Integer.toString(n));
        }
        if (args.containsKey(ARG_MAX_AGE)) {
            long maxAge = args.getLong(ARG_MAX_AGE);
            builder.appendQueryParameter(SamplesContract.PARAM_MAX_AGE,
                    Long.toString(maxAge));
        }
        loader.setUri(builder.build());
        return loader;
    }

    /** {@inheritDoc} */
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return appendQueryParameters(
                (CursorLoader) mCallbacks.onCreateLoader(id, args), args);
    }

    /** {@inheritDoc} */
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        Bundle extras = data != null ? data.getExtras() : Bundle.EMPTY;
        mHasError = extras.containsKey(FeedExtras.EXTRA_ERROR);
        mHasMore = extras.getBoolean(FeedExtras.EXTRA_MORE);
        mCount = data != null ? data.getCount() : 0;
        mCallbacks.onLoadFinished(loader, data);
    }

    /** {@inheritDoc} */
    public void onLoaderReset(Loader<Cursor> loader) {
        mHasError = false;
        mHasMore = false;
        mCount = 0;
        mCallbacks.onLoaderReset(loader);
    }
}
