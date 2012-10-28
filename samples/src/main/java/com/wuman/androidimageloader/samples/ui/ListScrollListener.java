/*-
 * Copyright (C) 2010 Google Inc.
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

import android.widget.AbsListView;
import android.widget.ListView;

/**
 * Automatically loads more items when the user scrolls to the bottom of a
 * {@link ListView}.
 */
public final class ListScrollListener implements AbsListView.OnScrollListener {

    private final Loadable mLoadable;

    public ListScrollListener(Loadable loadable) {
        mLoadable = loadable;
    }

    /**
     * {@inheritDoc}
     */
    public void onScroll(AbsListView view, int firstVisibleItem,
            int visibleItemCount, int totalItemCount) {
        if (firstVisibleItem >= totalItemCount - visibleItemCount) {
            if (mLoadable.isReadyToLoadMore()) {
                mLoadable.loadMore();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void onScrollStateChanged(AbsListView view, int scrollState) {
    }
}
