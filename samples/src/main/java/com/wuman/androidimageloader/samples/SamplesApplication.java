package com.wuman.androidimageloader.samples;

import java.io.File;
import java.io.IOException;
import java.net.ContentHandler;
import java.net.URLStreamHandlerFactory;

import android.app.Application;
import android.content.Context;

import com.integralblue.httpresponsecache.HttpResponseCache;
import com.wuman.androidimageloader.ImageLoader;
import com.wuman.androidimageloader.net.BitmapContentHandler;
import com.wuman.androidimageloader.net.SinkContentHandler;

public class SamplesApplication extends Application {

    private ImageLoader mImageLoader;

    private static ImageLoader createImageLoader(Context context)
            throws IOException {
        ContentHandler prefetchHandler;

        try {
            HttpResponseCache.install(new File(context.getCacheDir(),
                    "HttpResponseCache"), ImageLoader.DEFAULT_CACHE_SIZE * 2);

            // For pre-fetching, use a "sink" content handler so that the
            // the binary image data is captured by the cache without actually
            // parsing and loading the image data into memory. After
            // pre-fetching, the image data can be loaded quickly on-demand from
            // the local cache.
            prefetchHandler = new SinkContentHandler();
        } catch (Exception e) {
            prefetchHandler = null;
        }

        // Just use the default URLStreamHandlerFactory because
        // it supports all of the required URI schemes (http).
        URLStreamHandlerFactory streamFactory = null;

        // Load images using a BitmapContentHandler
        BitmapContentHandler bitmapHandler = new BitmapContentHandler();
        bitmapHandler.setTimeout(5000);

        return new ImageLoader(streamFactory, bitmapHandler, prefetchHandler,
                ImageLoader.DEFAULT_CACHE_SIZE, new File(context.getCacheDir(),
                        "images"));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            mImageLoader = createImageLoader(this);
        } catch (IOException e) {
        }
    }

    @Override
    public void onTerminate() {
        mImageLoader = null;
        super.onTerminate();
    }

    @Override
    public Object getSystemService(String name) {
        if (ImageLoader.IMAGE_LOADER_SERVICE.equals(name)) {
            return mImageLoader;
        } else {
            return super.getSystemService(name);
        }
    }

}
