package com.wuman.androidimageloader.samples;

import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;

import com.wuman.androidimageloader.ImageLoader;
import com.wuman.androidimageloader.samples.FlickrUtil.FlickrInterestingnessAdapter;
import com.wuman.androidimageloader.samples.provider.SamplesContract.InterestingPhotos;
import com.wuman.androidimageloader.samples.ui.DebugView;
import com.wuman.androidimageloader.samples.ui.ListDecorator;
import com.wuman.androidimageloader.samples.ui.ListScrollListener;
import com.wuman.androidimageloader.samples.ui.Loadable;
import com.wuman.androidimageloader.samples.ui.StatusViewManager;

public class FlickrInterestingnessFragment extends ListFragment implements
        LoaderManager.LoaderCallbacks<Cursor>, View.OnClickListener {

    private DebugView mDebugView;
    private FlickrInterestingnessAdapter mAdapter;
    private Loadable mPhotos;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setHasOptionsMenu(true);

        mAdapter = new FlickrInterestingnessAdapter(getActivity());
        mPhotos = new Loadable(getLoaderManager(), 0, new StatusViewManager(
                this, 0, this));
        setListAdapter(new ListDecorator(mAdapter, this));
        getListView().setOnScrollListener(new ListScrollListener(mPhotos));

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

        mPhotos.init();
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

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new CursorLoader(getActivity(), InterestingPhotos.CONTENT_URI,
                FlickrInterestingnessAdapter.PROJECTION, null, null, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        mAdapter.swapCursor(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mAdapter.swapCursor(null);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.refresh_options_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.menu_refresh: {
            ImageLoader imageLoader = ImageLoader.get(getActivity());
            imageLoader.clearErrors();
            Log.d("wuman", "onOptionsItemSelected: " + imageLoader);
            mPhotos.refresh();
            return true;
        }
        default: {
            return super.onOptionsItemSelected(item);
        }
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
        case R.id.retry: {
            mPhotos.retry();
            break;
        }
        }
    }

}
