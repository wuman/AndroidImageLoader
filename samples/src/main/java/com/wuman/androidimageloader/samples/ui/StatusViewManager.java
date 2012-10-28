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
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.util.SparseBooleanArray;
import android.view.Window;
import android.widget.Adapter;
import android.widget.AdapterView;

import com.wuman.androidfeedloader.FeedExtras;
import com.wuman.androidimageloader.samples.R;

/**
 * Shows a "Loading..." message while a list is loading and a indeterminate
 * progress spinner in the title bar when something is loading in the
 * background, or an error message if there is a problem loading the data.
 */
public final class StatusViewManager implements
        LoaderManager.LoaderCallbacks<Cursor> {

    private final LoaderManager.LoaderCallbacks<Cursor> mCallbacks;

    private final int mLoaderId;

    private final ListFragment mListFragment;

    private final AdapterView<?> mAdapterView;

    private final SparseBooleanArray mActive = new SparseBooleanArray();

    /**
     * Decorates {@link LoaderManager.LoaderCallbacks} in order to show loading
     * and error indicator views.
     * 
     * @param callbacks
     *            the callbacks to decorate.
     * @param id
     *            the loader ID of the primary data.
     * @param activity
     *            an activity containing the loading and error views. If the
     *            {@link Window} has
     *            {@link Window#FEATURE_INDETERMINATE_PROGRESS}, a spinner will
     *            when the primary loader is reloading data or when a secondary
     *            loader is running.
     */
    public StatusViewManager(LoaderManager.LoaderCallbacks<Cursor> callbacks,
            int id, ListFragment fragment) {
        mCallbacks = callbacks;
        mLoaderId = id;
        mListFragment = fragment;
        mAdapterView = (AdapterView<?>) fragment.getListView();

        // Loader may still be running from last Activity instance
        mListFragment.setListShown(false);
    }

    /** {@inheritDoc} */
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        mActive.put(id, true);
        if (mLoaderId == id) {
            mListFragment.setListShown(!isEmpty());
        }
        updateWindowIndeterminateProgress();
        return mCallbacks.onCreateLoader(id, args);
    }

    /** {@inheritDoc} */
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        mCallbacks.onLoadFinished(loader, data);
        mActive.delete(loader.getId());
        if (mLoaderId == loader.getId()) {
            if (mListFragment.isResumed()) {
                mListFragment.setListShown(true);
            } else {
                mListFragment.setListShownNoAnimation(true);
            }
            if (isEmpty() && hasError(data)) {
                mListFragment.setEmptyText(mListFragment
                        .getString(R.string.error_occured));
            } else {
                mListFragment.setEmptyText(mListFragment
                        .getString(R.string.empty));
            }
        }
        updateWindowIndeterminateProgress();
    }

    /** {@inheritDoc} */
    public void onLoaderReset(Loader<Cursor> loader) {
        mCallbacks.onLoaderReset(loader);
        mActive.delete(loader.getId());
        if (mLoaderId == loader.getId()) {
            if (mListFragment.isVisible()) {
                mListFragment.setListShown(true);
            }
        }
        updateWindowIndeterminateProgress();
    }

    private void updateWindowIndeterminateProgress() {
        if (mListFragment.isVisible()) {
            mListFragment.getActivity().setProgressBarIndeterminateVisibility(
                    mActive.size() != 0);
        }
    }

    private boolean isEmpty() {
        // Don't use AdapterView#getCount() because it is updated asynchronously
        Adapter adapter = mAdapterView.getAdapter();
        return adapter == null || adapter.isEmpty();
    }

    private static boolean hasError(Cursor data) {
        return data == null
                || data.getExtras().containsKey(FeedExtras.EXTRA_ERROR);
    }
}
