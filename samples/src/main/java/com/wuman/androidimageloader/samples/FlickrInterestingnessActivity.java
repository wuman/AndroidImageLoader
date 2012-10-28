package com.wuman.androidimageloader.samples;

import android.support.v4.app.Fragment;

public class FlickrInterestingnessActivity extends BaseSinglePaneActivity {

    @Override
    protected Fragment onCreatePane() {
        return new FlickrInterestingnessFragment();
    }

}
