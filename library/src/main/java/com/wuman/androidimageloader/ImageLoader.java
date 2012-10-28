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

package com.wuman.androidimageloader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ContentHandler;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.util.HashMap;

import org.apache.commons.io.IOUtils;

import android.app.Activity;
import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ImageView;

import com.jakewharton.DiskLruCache;
import com.jakewharton.DiskLruCache.Editor;
import com.jakewharton.DiskLruCache.Snapshot;
import com.wuman.androidimageloader.net.BitmapContentHandler;
import com.wuman.androidimageloader.net.ContentURLStreamHandlerFactory;
import com.wuman.androidimageloader.util.LifoAsyncTask;
import com.wuman.twolevellrucache.LruCache;
import com.wuman.twolevellrucache.TwoLevelLruCache;

/**
 * A helper class to load images asynchronously.
 */
public final class ImageLoader {

    private static final String LOG_TAG = ImageLoader.class.getSimpleName();

    private static final int APP_VERSION = 1;

    /**
     * The default cache size (in bytes).
     */
    // 25% of available memory, up to a maximum of 16MB
    public static final long DEFAULT_CACHE_SIZE = Math.min(Runtime.getRuntime()
            .maxMemory() / 4, 16 * 1024 * 1024);

    /**
     * Use with {@link Context#getSystemService(String)} to retrieve an
     * {@link ImageLoader} for loading images.
     * <p>
     * Since {@link ImageLoader} is not a standard system service, you must
     * create a custom {@link Application} subclass implementing
     * {@link Application#getSystemService(String)} and add it to your
     * {@code AndroidManifest.xml}.
     * <p>
     * Using this constant is optional and it is only provided for convenience
     * and to promote consistency across deployments of this component.
     */
    public static final String IMAGE_LOADER_SERVICE = ImageLoader.class
            .getPackage().getName();

    /**
     * Gets the {@link ImageLoader} from a {@link Context}.
     * 
     * @throws IllegalStateException
     *             if the {@link Application} does not have an
     *             {@link ImageLoader}.
     * @see #IMAGE_LOADER_SERVICE
     */
    public static ImageLoader get(Context context) {
        ImageLoader loader = (ImageLoader) context
                .getSystemService(IMAGE_LOADER_SERVICE);
        if (loader == null) {
            context = context.getApplicationContext();
            loader = (ImageLoader) context
                    .getSystemService(IMAGE_LOADER_SERVICE);
        }
        if (loader == null) {
            throw new IllegalStateException("ImageLoader not available");
        }
        return loader;
    }

    /**
     * Callback interface for load and error events.
     */
    public interface Callback {
        /**
         * Notifies an observer that an image was loaded.
         * <p>
         * The bitmap will be assigned to the {@link ImageView} automatically.
         * <p>
         * Use this callback to dismiss any loading indicators.
         * 
         * @param bitmap
         *            the {@link Bitmap} that was loaded.
         * @param url
         *            the URL that was loaded.
         * @param loadSource
         *            indicates whether the {@link Bitmap} was loaded directly
         *            from cache.
         */
        void onImageLoaded(Bitmap bitmap, String url, LoadSource loadSource);

        /**
         * Notifies an observer that an image could not be loaded.
         * 
         * @param url
         *            the URL that could not be loaded.
         * @param error
         *            the exception that was thrown.
         */
        void onImageError(String url, Throwable error);
    }

    public static enum LoadResult {
        /**
         * Returned when an image was already loaded from memory cache.
         */
        OK,
        /**
         * Returned when an image needs to be loaded asynchronously, either from
         * disk cache or from network.
         * <p>
         * Callers may wish to assign a placeholder or show a progress spinner
         * while the image is being loaded whenever this value is returned.
         */
        LOADING,
        /**
         * Returned when an attempt to load the image has already been made and
         * it failed.
         * <p>
         * Callers may wish to show an error indicator when this value is
         * returned.
         * 
         * @see ImageLoader.Callback
         */
        ERROR
    }

    public static enum LoadSource {
        /**
         * Returned when an image was loaded from memory cache.
         */
        CACHE_MEMORY,
        /**
         * Returned when an image was loaded from disk cache.
         */
        CACHE_DISK,
        /**
         * Returned when an image was loaded from an external source. The
         * default is via HttpUrlConnection.
         */
        EXTERNAL
    }

    private static String getProtocol(String url) {
        Uri uri = Uri.parse(url);
        return uri.getScheme();
    }

    private final ContentHandler mBitmapContentHandler;

    private final ContentHandler mPrefetchContentHandler;

    private final URLStreamHandlerFactory mURLStreamHandlerFactory;

    private final HashMap<String, URLStreamHandler> mStreamHandlers;

    /**
     * A cache containing recently used bitmaps in memory.
     * <p>
     * Use soft references so that the application does not run out of memory in
     * the case where one or more of the bitmaps are large.
     */
    private final LruCache<String, Bitmap> mBitmapsInMem;

    /**
     * A cache containing recently used bitmaps on file.
     */
    private DiskLruCache mBitmapsInDisk;

    /**
     * A {@link BitmapConverter} for converting a {@link Bitmap} to and from
     * bytes.
     */
    private final BitmapConverter mBitmapConverter;

    /**
     * Recent errors encountered when loading bitmaps.
     */
    private final LruCache<String, ImageError> mErrors;

    /**
     * Creates an {@link ImageLoader}.
     * 
     * @param streamFactory
     *            a {@link URLStreamHandlerFactory} for creating connections to
     *            special URLs such as {@code content://} URIs. This parameter
     *            can be {@code null} if the {@link ImageLoader} only needs to
     *            load images over HTTP or if a custom
     *            {@link URLStreamHandlerFactory} has already been passed to
     *            {@link URL#setURLStreamHandlerFactory(URLStreamHandlerFactory)}
     * @param bitmapHandler
     *            a {@link ContentHandler} for loading images.
     *            {@link ContentHandler#getContent(URLConnection)} must either
     *            return a {@link Bitmap} or throw an {@link IOException}. This
     *            parameter can be {@code null} to use the default
     *            {@link BitmapContentHandler}.
     * @param prefetchHandler
     *            a {@link ContentHandler} for caching a remote URL as a file,
     *            without parsing it or loading it into memory.
     *            {@link ContentHandler#getContent(URLConnection)} should always
     *            return {@code null}. If the URL passed to the
     *            {@link ContentHandler} is already local (for example,
     *            {@code file://}), this {@link ContentHandler} should do
     *            nothing. The {@link ContentHandler} can be {@code null} if
     *            pre-fetching is not required.
     * @param cacheSize
     *            the maximum size of the image cache (in bytes).
     * @param directory
     *            optional directory for disk cache
     * @throws IOException
     *             if the disk cache cannot be opened.
     */
    public ImageLoader(URLStreamHandlerFactory streamFactory,
            ContentHandler bitmapHandler, ContentHandler prefetchHandler,
            long cacheSize, File directory) throws IOException {
        if (cacheSize < 1) {
            throw new IllegalArgumentException("Cache size must be positive");
        }
        mURLStreamHandlerFactory = streamFactory;
        mStreamHandlers = streamFactory != null ? new HashMap<String, URLStreamHandler>()
                : null;
        mBitmapContentHandler = bitmapHandler != null ? bitmapHandler
                : new BitmapContentHandler();
        mPrefetchContentHandler = prefetchHandler;

        // Use a LruCache to prevent the set of keys from growing too large.
        // The Maps must be synchronized because they are accessed
        // by the UI thread and by background threads.
        mBitmapsInMem = new LruCache<String, Bitmap>((int) cacheSize) {
            // Assume a 32-bit image
            private static final int BYTES_PER_PIXEL = 4;

            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getWidth() * value.getHeight() * BYTES_PER_PIXEL;
            }

        };
        mErrors = new LruCache<String, ImageError>(256);

        mBitmapsInDisk = directory == null ? null : DiskLruCache.open(
                directory, APP_VERSION, 1, cacheSize * 2);
        mBitmapConverter = mBitmapsInDisk == null ? null
                : new BitmapConverter();
    }

    /**
     * Creates a basic {@link ImageLoader} with support for HTTP URLs and
     * in-memory caching.
     * <p>
     * Persistent caching and content:// URIs are not supported when this
     * constructor is used.
     * 
     * @throws IOException
     */
    public ImageLoader() throws IOException {
        this(null, null, null, DEFAULT_CACHE_SIZE, null);
    }

    /**
     * Creates a basic {@link ImageLoader} with support for HTTP URLs and
     * in-memory caching.
     * <p>
     * Persistent caching and content:// URIs are not supported when this
     * constructor is used.
     * 
     * @param taskLimit
     *            the maximum number of background tasks that may be active at a
     *            time.
     * @throws IOException
     */
    public ImageLoader(int taskLimit) throws IOException {
        this(null, null, null, DEFAULT_CACHE_SIZE, null);
    }

    /**
     * Creates a basic {@link ImageLoader} with support for HTTP URLs and
     * in-memory caching.
     * <p>
     * Persistent caching and content:// URIs are not supported when this
     * constructor is used.
     * 
     * @param cacheSize
     *            the maximum size of the image cache (in bytes).
     * @throws IOException
     */
    public ImageLoader(long cacheSize) throws IOException {
        this(null, null, null, cacheSize, null);
    }

    /**
     * Creates an {@link ImageLoader} with support for pre-fetching.
     * 
     * @param bitmapHandler
     *            a {@link ContentHandler} that reads, caches, and returns a
     *            {@link Bitmap}.
     * @param prefetchHandler
     *            a {@link ContentHandler} for caching a remote URL as a file,
     *            without parsing it or loading it into memory.
     *            {@link ContentHandler#getContent(URLConnection)} should always
     *            return {@code null}. If the URL passed to the
     *            {@link ContentHandler} is already local (for example,
     *            {@code file://}), this {@link ContentHandler} should return
     *            {@code null} immediately.
     * @throws IOException
     */
    public ImageLoader(ContentHandler bitmapHandler,
            ContentHandler prefetchHandler) throws IOException {
        this(null, bitmapHandler, prefetchHandler, DEFAULT_CACHE_SIZE, null);
    }

    /**
     * Creates an {@link ImageLoader} with support for http:// and content://
     * URIs.
     * <p>
     * Prefetching is not supported when this constructor is used.
     * 
     * @param resolver
     *            a {@link ContentResolver} for accessing content:// URIs.
     * @throws IOException
     */
    public ImageLoader(ContentResolver resolver) throws IOException {
        this(new ContentURLStreamHandlerFactory(resolver), null, null,
                DEFAULT_CACHE_SIZE, null);
    }

    /**
     * Creates an {@link ImageLoader} with a custom
     * {@link URLStreamHandlerFactory}.
     * <p>
     * Use this constructor when loading images with protocols other than
     * {@code http://} and when a custom {@link URLStreamHandlerFactory} has not
     * already been installed with
     * {@link URL#setURLStreamHandlerFactory(URLStreamHandlerFactory)}. If the
     * only additional protocol support required is for {@code content://} URIs,
     * consider using {@link #ImageLoader(ContentResolver)}.
     * <p>
     * Prefetching is not supported when this constructor is used.
     * 
     * @throws IOException
     */
    public ImageLoader(URLStreamHandlerFactory factory) throws IOException {
        this(factory, null, null, DEFAULT_CACHE_SIZE, null);
    }

    public final String getCacheDebugStats() {
        return mBitmapsInMem.toString();
    }

    private URLStreamHandler getURLStreamHandler(String protocol) {
        URLStreamHandlerFactory factory = mURLStreamHandlerFactory;
        if (factory == null) {
            return null;
        }
        HashMap<String, URLStreamHandler> handlers = mStreamHandlers;
        synchronized (handlers) {
            URLStreamHandler handler = handlers.get(protocol);
            if (handler == null) {
                handler = factory.createURLStreamHandler(protocol);
                if (handler != null) {
                    handlers.put(protocol, handler);
                }
            }
            return handler;
        }
    }

    private void enqueueRequest(ImageRequest request) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new RuntimeException("Must be called in the main thread.");
        }
        ImageTask task = new ImageTask();
        task.executeOnExecutor(ImageTask.LIFO_THREAD_POOL_EXECUTOR, request);
    }

    /**
     * Loads an image at the given URL.
     * <p>
     * If the image needs to be loaded asynchronously, it will be assigned at a
     * later time.
     * 
     * @param url
     *            the image URL.
     * @param callback
     *            invoked after the image has finished loading or after an
     *            error. The callback may be executed before this method returns
     *            when the result is cached. This parameter can be {@code null}
     *            if a callback is not required.
     * @return a {@link LoadResult}.
     * @throws NullPointerException
     *             if a required argument is {@code null}
     */
    public LoadResult load(String url, Callback callback) {
        if (url == null) {
            throw new NullPointerException("URL is null");
        }
        Bitmap bitmap = getBitmapFromMemory(url);
        ImageError error = getError(url);
        if (bitmap != null) {
            if (callback != null) {
                callback.onImageLoaded(bitmap, url, LoadSource.CACHE_MEMORY);
            }
            return LoadResult.OK;
        } else {
            if (error != null) {
                if (callback != null) {
                    callback.onImageError(url, error.getCause());
                }
                return LoadResult.ERROR;
            } else {
                ImageRequest request = new ImageRequest(url, callback);
                enqueueRequest(request);
                return LoadResult.LOADING;
            }
        }
    }

    /**
     * Loads an image at the given URL only if it already exists in the cache.
     * Unlike {@link #load(String, Callback)} this call will not trigger a
     * scheduled remote fetch of the image if it does not already exist in the
     * cache. This is typically best called when the user is fast scrolling
     * (fling) through a list of {@link ImageView}s.
     * 
     * @param url
     * @return a {@link Bitmap} already held in cache
     */
    public Bitmap loadOnlyFromMemCache(String url) {
        if (url == null) {
            throw new NullPointerException("URL is null");
        }
        return getBitmapFromMemory(url);
    }

    /**
     * Clears any cached errors.
     * <p>
     * Call this method when a network connection is restored, or the user
     * invokes a manual refresh of the screen.
     */
    public void clearErrors() {
        mErrors.evictAll();
    }

    /**
     * Clears both the memory cache and the disk cache.
     */
    public void clearCache() {
        clearMemCache();
        clearDiskCache();
    }

    /**
     * Clears the memory cache.
     */
    public void clearMemCache() {
        mBitmapsInMem.evictAll();
        mBitmapsInMem.clearStats();
    }

    /**
     * Decrease the memory cache max size.
     */
    private void decreaseMemCacheSize() {
        mBitmapsInMem.setMaxSize((int) (mBitmapsInMem.maxSize() * 0.7f));
    }

    /**
     * Clears the disk cache if it is present.
     */
    public void clearDiskCache() {
        if (mBitmapsInDisk != null) {
            try {
                mBitmapsInDisk.delete();
                mBitmapsInDisk = DiskLruCache.open(
                        mBitmapsInDisk.getDirectory(), APP_VERSION, 1,
                        mBitmapsInDisk.getMaxSize());
            } catch (IOException e) {
                Log.e(LOG_TAG, "Failed to clear disk cache.", e);
            } catch (Exception e) {
                Log.e(LOG_TAG, "Failed to clear disk cache.", e);
            }
        }
    }

    /**
     * Pre-loads an image into memory.
     * <p>
     * The image may be unloaded if memory is low. Use {@link #prefetch(String)}
     * and a file-based cache to pre-load more images.
     * 
     * @param url
     *            the image URL
     * @throws NullPointerException
     *             if the URL is {@code null}
     */
    public void preload(String url) {
        if (url == null) {
            throw new NullPointerException();
        }
        if (null != getBitmapFromMemory(url)) {
            // The image is already loaded
            return;
        }
        if (null != getError(url)) {
            // A recent attempt to load the image failed,
            // therefore this attempt is likely to fail as well.
            return;
        }
        boolean loadBitmap = true;
        ImageRequest task = new ImageRequest(url, loadBitmap);
        enqueueRequest(task);
    }

    /**
     * Pre-loads a range of images into memory from a {@link Cursor}.
     * <p>
     * Typically, an {@link Activity} would register a {@link DataSetObserver}
     * and an {@link android.widget.AdapterView.OnItemSelectedListener}, then
     * call this method to prime the in-memory cache with images adjacent to the
     * current selection whenever the selection or data changes.
     * <p>
     * Any invalid positions in the specified range will be silently ignored.
     * 
     * @param cursor
     *            a {@link Cursor} containing the image URLs.
     * @param columnIndex
     *            the column index of the image URL. The column value may be
     *            {@code NULL}.
     * @param start
     *            the first position to load. For example,
     *            {@code selectedPosition - 5}.
     * @param end
     *            the first position not to load. For example,
     *            {@code selectedPosition + 5}.
     * @see #preload(String)
     */
    public void preload(Cursor cursor, int columnIndex, int start, int end) {
        for (int position = start; position < end; position++) {
            if (cursor.moveToPosition(position)) {
                String url = cursor.getString(columnIndex);
                if (!TextUtils.isEmpty(url)) {
                    preload(url);
                }
            }
        }
    }

    /**
     * Pre-fetches the binary content for an image and stores it in a file-based
     * cache (if it is not already cached locally) without loading the image
     * data into memory.
     * <p>
     * Pre-fetching should not be used unless a {@link ContentHandler} with
     * support for persistent caching was passed to the constructor.
     * 
     * @param url
     *            the URL to pre-fetch.
     * @throws NullPointerException
     *             if the URL is {@code null}
     */
    public void prefetch(String url) {
        if (url == null) {
            throw new NullPointerException();
        }
        if (null != getBitmapFromMemory(url)) {
            // The image is already loaded, therefore
            // it does not need to be prefetched.
            return;
        }
        if (null != getError(url)) {
            // A recent attempt to load or prefetch the image failed,
            // therefore this attempt is likely to fail as well.
            return;
        }
        boolean loadBitmap = false;
        ImageRequest request = new ImageRequest(url, loadBitmap);
        enqueueRequest(request);
    }

    /**
     * Pre-fetches the binary content for images referenced by a {@link Cursor},
     * without loading the image data into memory.
     * <p>
     * Pre-fetching should not be used unless a {@link ContentHandler} with
     * support for persistent caching was passed to the constructor.
     * <p>
     * Typically, an {@link Activity} would register a {@link DataSetObserver}
     * and call this method from {@link DataSetObserver#onChanged()} to load
     * off-screen images into a file-based cache when they are not already
     * present in the cache.
     * 
     * @param cursor
     *            the {@link Cursor} containing the image URLs.
     * @param columnIndex
     *            the column index of the image URL. The column value may be
     *            {@code NULL}.
     * @see #prefetch(String)
     */
    public void prefetch(Cursor cursor, int columnIndex) {
        for (int position = 0; cursor.moveToPosition(position); position++) {
            String url = cursor.getString(columnIndex);
            if (!TextUtils.isEmpty(url)) {
                prefetch(url);
            }
        }
    }

    private static String urlToKey(String url) {
        return Integer.toHexString(url.hashCode());
    }

    private void putBitmapInMemory(String url, Bitmap bitmap) {
        mBitmapsInMem.put(urlToKey(url), bitmap);
    }

    private void putError(String url, ImageError error) {
        mErrors.put(url, error);
    }

    private Bitmap getBitmapFromMemory(String url) {
        return mBitmapsInMem.get(urlToKey(url));
    }

    private void putBitmapOnDisk(String url, Bitmap bitmap) {
        if (mBitmapsInDisk != null && mBitmapConverter != null) {
            Editor editor = null;
            OutputStream out = null;
            try {
                editor = mBitmapsInDisk.edit(urlToKey(url));
                if (editor != null) {
                    out = editor.newOutputStream(0);
                    mBitmapConverter.toStream(bitmap, out);
                    editor.commit();
                }
            } catch (IOException e) {
                Log.w(LOG_TAG, "Failed to put Bitmap on disk.", e);
            } catch (Exception e) {
                Log.w(LOG_TAG, "Failed to put Bitmap on disk.", e);
            } finally {
                IOUtils.closeQuietly(out);
                quietlyAbortUnlessCommitted(editor);
            }
        }
    }

    private static void quietlyAbortUnlessCommitted(DiskLruCache.Editor editor) {
        // Give up because the cache cannot be written.
        try {
            if (editor != null) {
                editor.abortUnlessCommitted();
            }
        } catch (Exception ignored) {
        }
    }

    private Bitmap getBitmapFromDisk(String url) {
        if (mBitmapsInDisk != null && mBitmapConverter != null) {
            Snapshot snapshot = null;
            InputStream in = null;
            try {
                snapshot = mBitmapsInDisk.get(urlToKey(url));
                if (snapshot != null) {
                    in = snapshot.getInputStream(0);
                    byte[] bytes = IOUtils.toByteArray(in);
                    return mBitmapConverter.from(bytes);
                }
            } catch (IOException e) {
                Log.w(LOG_TAG, "Failed to get Bitmap from disk.", e);
            } catch (Exception e) {
                Log.w(LOG_TAG, "Failed to get Bitmap from disk.", e);
            } finally {
                IOUtils.closeQuietly(in);
                IOUtils.closeQuietly(snapshot);
            }
        }
        return null;
    }

    private ImageError getError(String url) {
        ImageError error = mErrors.get(url);
        return error != null && !error.isExpired() ? error : null;
    }

    /**
     * Returns {@code true} if there was an error the last time the given URL
     * was accessed and the error is not expired, {@code false} otherwise.
     */
    private boolean hasError(String url) {
        return getError(url) != null;
    }

    private class ImageRequest {

        private final Callback mCallback;

        private final String mUrl;

        private final boolean mLoadBitmap;

        private Bitmap mBitmap;

        private ImageError mError;

        private LoadSource mLoadSource;

        private ImageRequest(String url, Callback callback, boolean loadBitmap) {
            mUrl = url;
            mCallback = callback;
            mLoadBitmap = loadBitmap;
            mLoadSource = LoadSource.EXTERNAL;
        }

        /**
         * Creates an {@link ImageTask} to load a {@link Bitmap} for an
         * {@link ImageView}.
         */
        public ImageRequest(String url, Callback callback) {
            this(url, callback, true);
        }

        /**
         * Creates an {@link ImageTask} to prime the cache.
         */
        public ImageRequest(String url, boolean loadBitmap) {
            this(url, null, loadBitmap);
        }

        private Bitmap loadImage(URL url) throws IOException {
            URLConnection connection = url.openConnection();
            return (Bitmap) mBitmapContentHandler.getContent(connection);
        }

        /**
         * Executes the {@link ImageTask}.
         * 
         * @return {@code true} if the result for this {@link ImageTask} should
         *         be posted, {@code false} otherwise.
         */
        public boolean execute() {
            try {
                // Check if the last attempt to load the URL had an error
                mError = getError(mUrl);
                if (mError != null) {
                    return true;
                }

                // Check if the Bitmap is already cached in memory
                try {
                    mBitmap = getBitmapFromMemory(mUrl);
                } catch (OutOfMemoryError e) {
                    decreaseMemCacheSize();
                    System.gc();
                    mBitmap = getBitmapFromMemory(mUrl);
                }
                if (mBitmap != null) {
                    // Keep a hard reference until the view has been notified.
                    mLoadSource = LoadSource.CACHE_MEMORY;
                    return true;
                }

                // Check if the Bitmap is already cached on disk
                try {
                    mBitmap = getBitmapFromDisk(mUrl);
                } catch (OutOfMemoryError e) {
                    decreaseMemCacheSize();
                    System.gc();
                    mBitmap = getBitmapFromDisk(mUrl);
                }
                if (mBitmap != null) {
                    mLoadSource = LoadSource.CACHE_DISK;
                    return true;
                }

                String protocol = getProtocol(mUrl);
                URLStreamHandler streamHandler = getURLStreamHandler(protocol);
                URL url = new URL(null, mUrl, streamHandler);

                if (mLoadBitmap) {
                    try {
                        mBitmap = loadImage(url);
                    } catch (OutOfMemoryError e) {
                        decreaseMemCacheSize();
                        // The VM does not always free-up memory as it should,
                        // so manually invoke the garbage collector
                        // and try loading the image again.
                        System.gc();
                        mBitmap = loadImage(url);
                    }
                    if (mBitmap == null) {
                        throw new NullPointerException(
                                "ContentHandler returned null");
                    }
                    return true;
                } else {
                    if (mPrefetchContentHandler != null) {
                        // Cache the URL without loading a Bitmap into memory.
                        URLConnection connection = url.openConnection();
                        mPrefetchContentHandler.getContent(connection);
                    }
                    mBitmap = null;
                    return false;
                }
            } catch (IOException e) {
                mError = new ImageError(e);
                return true;
            } catch (RuntimeException e) {
                mError = new ImageError(e);
                return true;
            } catch (Error e) {
                mError = new ImageError(e);
                return true;
            }
        }

        public void publishResult() {
            if (mBitmap != null) {
                putBitmapInMemory(mUrl, mBitmap);
            } else if (mError != null && !hasError(mUrl)) {
                putError(mUrl, mError);
            }
            if (mCallback != null) {
                if (mBitmap != null) {
                    mCallback.onImageLoaded(mBitmap, mUrl, mLoadSource);
                } else if (mError != null) {
                    mCallback.onImageError(mUrl, mError.getCause());
                }
            }
        }

        public void writeBackResult() {
            if (mBitmap != null) {
                putBitmapOnDisk(mUrl, mBitmap);
            }
        }

    }

    private class ImageTask extends
            LifoAsyncTask<ImageRequest, ImageRequest, Void> {

        @Override
        protected Void doInBackground(ImageRequest... requests) {
            for (ImageRequest request : requests) {
                if (request.execute()) {
                    publishProgress(request);
                    request.writeBackResult();
                }
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(ImageRequest... values) {
            for (ImageRequest request : values) {
                request.publishResult();
            }
        }

    }

    private static class ImageError {
        private static final int TIMEOUT = 2 * 60 * 1000; // Two minutes

        private final Throwable mCause;

        private final long mTimestamp;

        public ImageError(Throwable cause) {
            if (cause == null) {
                throw new NullPointerException();
            }
            mCause = cause;
            mTimestamp = now();
        }

        public boolean isExpired() {
            return (now() - mTimestamp) > TIMEOUT;
        }

        public Throwable getCause() {
            return mCause;
        }

        private static long now() {
            return SystemClock.elapsedRealtime();
        }
    }

    private static final class BitmapConverter implements
            TwoLevelLruCache.Converter<Bitmap> {

        @Override
        public Bitmap from(byte[] bytes) throws IOException {
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        }

        @Override
        public void toStream(Bitmap o, OutputStream bytes) throws IOException {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            try {
                o.compress(CompressFormat.PNG, 100, os);
                bytes.write(os.toByteArray());
            } finally {
                IOUtils.closeQuietly(os);
            }
        }

    }

}
