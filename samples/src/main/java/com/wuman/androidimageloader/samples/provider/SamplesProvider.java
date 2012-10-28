package com.wuman.androidimageloader.samples.provider;

import java.net.ContentHandler;
import java.util.HashMap;
import java.util.Map;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;

import com.wuman.androidfeedloader.FeedLoader;
import com.wuman.androidfeedloader.FeedProvider;
import com.wuman.androidimageloader.samples.FlickrUtil;
import com.wuman.androidimageloader.samples.FlickrUtil.FlickrContentHandler;
import com.wuman.androidimageloader.samples.provider.SamplesContract.InterestingPhotos;

public class SamplesProvider extends ContentProvider {

    private static final String LOG_TAG = SamplesProvider.class.getSimpleName();

    private static final long DEFAULT_MAX_AGE = DateUtils.DAY_IN_MILLIS;

    private static final UriMatcher sUriMatcher;

    private static final int FLICKR_INTERESTING_PHOTOS = 1;

    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(SamplesContract.AUTHORITY,
                InterestingPhotos.CONTENT_URI.getLastPathSegment(),
                FLICKR_INTERESTING_PHOTOS);
    }

    @Override
    public String getType(Uri uri) {
        switch (sUriMatcher.match(uri)) {
        case FLICKR_INTERESTING_PHOTOS:
            return InterestingPhotos.CONTENT_TYPE;
        default:
            return null;
        }
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        if (projection == null) {
            return null;
        }

        MatrixCursor output = new MatrixCursor(projection);
        Bundle extras = new Bundle();

        try {
            Map<String, String> projectionMap = new HashMap<String, String>();
            Long maxAge = Long.valueOf(SamplesContract.getMaxAge(uri,
                    DEFAULT_MAX_AGE));

            ContentHandler handler;
            Uri feedUri;
            String pageParameter;
            int firstPage;
            int pageSize;
            switch (sUriMatcher.match(uri)) {
            case FLICKR_INTERESTING_PHOTOS:
                FlickrUtil.updateProjectionMap(projection, projectionMap);
                handler = new FlickrContentHandler(output, projectionMap,
                        maxAge);
                feedUri = FlickrUtil.getPhotosUri();
                pageParameter = FlickrUtil.PARAM_PAGE;
                firstPage = FlickrUtil.DEFAULT_FIRST_PAGE;
                pageSize = FlickrUtil.DEFAULT_PAGE_SIZE;
                break;
            default:
                return null;
            }

            FeedLoader.loadPagedFeed(handler, feedUri, pageParameter,
                    firstPage, pageSize,
                    SamplesContract.getNumber(uri, pageSize), extras);
            return FeedProvider.feedCursor(output, extras);
        } catch (Throwable t) {
            Log.e(LOG_TAG, "query failed", t);
            return FeedProvider.errorCursor(output, extras, t, null);
        }
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }

}
