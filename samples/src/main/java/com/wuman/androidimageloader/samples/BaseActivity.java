/*
 * Copyright 2011 Google Inc.
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

package com.wuman.androidimageloader.samples;

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;

import com.wuman.androidimageloader.ImageLoader;

/**
 * A base activity that defers common functionality across app activities to an
 * {@link ActivityHelper}. This class shouldn't be used directly; instead,
 * activities should inherit from {@link BaseSinglePaneActivity} or
 * {@link BaseMultiPaneActivity}.
 */
public abstract class BaseActivity extends FragmentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        super.onCreate(savedInstanceState);
    }

    /**
     * Takes a given intent and either starts a new activity to handle it (the
     * default behavior), or creates/updates a fragment (in the case of a
     * multi-pane activity) that can handle the intent.
     * 
     * Must be called from the main (UI) thread.
     */
    public void openActivityOrFragment(Intent intent) {
        // Default implementation simply calls startActivity
        startActivity(intent);
    }

    /**
     * Converts an intent into a {@link Bundle} suitable for use as fragment
     * arguments.
     */
    public static Bundle intentToFragmentArguments(Intent intent) {
        Bundle arguments = new Bundle();
        if (intent == null) {
            return arguments;
        }

        final Uri data = intent.getData();
        if (data != null) {
            arguments.putParcelable("_uri", data);
        }

        final String action = intent.getAction();
        if (action != null) {
            arguments.putString("_action", action);
        }

        final String mimeType = intent.getType();
        if (mimeType != null) {
            arguments.putString("_mimetype", mimeType);
        }

        final Bundle extras = intent.getExtras();
        if (extras != null) {
            arguments.putAll(intent.getExtras());
        }

        return arguments;
    }

    /**
     * Converts a fragment arguments bundle into an intent.
     */
    public static Intent fragmentArgumentsToIntent(Bundle arguments) {
        Intent intent = new Intent();
        if (arguments == null) {
            return intent;
        }

        final String action = arguments.getString("_action");
        if (action != null) {
            intent.setAction(action);
        }

        final Uri data = arguments.getParcelable("_uri");
        final String mimeType = arguments.getString("_mimetype");
        intent.setDataAndType(data, mimeType);

        intent.putExtras(arguments);
        return intent;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.default_options_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home: {
            Intent intent = new Intent(this, SamplesActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            openActivityOrFragment(intent);
            return true;
        }
        case R.id.menu_clear_cache: {
            new AsyncTask<Void, Void, Void>() {

                @Override
                protected Void doInBackground(Void... params) {
                    ImageLoader imageLoader = ImageLoader
                            .get(getApplicationContext());
                    imageLoader.clearCache();
                    return null;
                }

            }.execute();
            return true;
        }

        default: {
            return super.onOptionsItemSelected(item);
        }
        }
    }

}
