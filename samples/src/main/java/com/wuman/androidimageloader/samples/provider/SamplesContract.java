package com.wuman.androidimageloader.samples.provider;

import android.content.ContentResolver;
import android.net.Uri;
import android.provider.BaseColumns;

public class SamplesContract {

    public static final Uri AUTHORITY_URI = Uri
            .parse(ContentResolver.SCHEME_CONTENT + "://"
                    + SamplesContract.class.getPackage().getName());
    public static final String AUTHORITY = AUTHORITY_URI.getAuthority();

    public static interface PhotoColumns extends BaseColumns {
        String SECRET = "photo_secret";
        String SERVER = "photo_server";
        String FARM = "photo_farm";
        String TITLE = "photo_title";
    }

    public static interface InterestingPhotos extends PhotoColumns {
        Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI,
                "flickr_interestingness");
        String CONTENT_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE + "/"
                + SamplesContract.class.getPackage().getName()
                + ".interestingness";
        String CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/"
                + SamplesContract.class.getPackage().getName()
                + ".interestingness";
    }

    /**
     * Specifies the maximum age of cached content.
     */
    public static final String PARAM_MAX_AGE = "max-age";

    /**
     * Specifies the number of items to retrieve.
     */
    public static final String PARAM_NUMBER = "n";

    private static long getLongQueryParameter(Uri uri, String key,
            long defaultValue) {
        String value = uri.getQueryParameter(key);
        return value != null ? Long.parseLong(value) : defaultValue;
    }

    private static int getIntQueryParameter(Uri uri, String key,
            int defaultValue) {
        String value = uri.getQueryParameter(key);
        return value != null ? Integer.parseInt(value) : defaultValue;
    }

    private static Uri setLongQueryParameter(Uri uri, String key, long value) {
        if (0 != uri.getQueryParameters(key).size()) {
            throw new IllegalArgumentException(key + " is already specified.");
        }
        return uri.buildUpon().appendQueryParameter(key, Long.toString(value))
                .build();
    }

    private static Uri setIntQueryParameter(Uri uri, String key, int value) {
        if (0 != uri.getQueryParameters(key).size()) {
            throw new IllegalArgumentException(key + " is already specified.");
        }
        return uri.buildUpon()
                .appendQueryParameter(key, Integer.toString(value)).build();
    }

    public static int getNumber(Uri uri, int defaultValue) {
        return getIntQueryParameter(uri, PARAM_NUMBER, defaultValue);
    }

    public static Uri setNumber(Uri uri, int value) {
        return setIntQueryParameter(uri, PARAM_NUMBER, value);
    }

    public static long getMaxAge(Uri uri, long defaultValue) {
        return getLongQueryParameter(uri, PARAM_MAX_AGE, defaultValue);
    }

    public static Uri setMaxAge(Uri uri, long value) {
        return setLongQueryParameter(uri, PARAM_MAX_AGE, value);
    }

    public static Uri refresh(Uri uri) {
        return setMaxAge(uri, 0);
    }

    private SamplesContract() {
    }
}
