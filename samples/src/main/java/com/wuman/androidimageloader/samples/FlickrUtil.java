package com.wuman.androidimageloader.samples;

import java.io.IOException;
import java.net.URLConnection;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.support.v4.widget.CursorAdapter;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.AbsListView;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;

import com.wuman.androidfeedloader.FeedLoader;
import com.wuman.androidfeedloader.JsonContentHandler;
import com.wuman.androidimageloader.ImageLoader;
import com.wuman.androidimageloader.ImageViewBinder;
import com.wuman.androidimageloader.samples.provider.SamplesContract.InterestingPhotos;

public class FlickrUtil {

    private FlickrUtil() {
        super();
    }

    private static final String LOG_TAG = FlickrUtil.class.getSimpleName();

    private static final String API_KEY = "aa858972c326ab3465c7bbf556d5addc";
    private static final Uri END_POINT = Uri
            .parse("http://api.flickr.com/services/rest");
    private static final String FILENAME_TEMPLATE = "http://farm{farm-id}.staticflickr.com/{server-id}/{id}_{secret}_m.jpg";

    public static final String PARAM_PAGE = "page";
    public static final int DEFAULT_PAGE_SIZE = 100;
    public static final int DEFAULT_FIRST_PAGE = 1;

    public static final void updateProjectionMap(String[] projection,
            Map<String, String> outProjectionMap) {
        for (String columnName : projection) {
            if (InterestingPhotos._ID.equals(columnName)) {
                outProjectionMap.put(columnName, "id");
            } else if (InterestingPhotos.SECRET.equals(columnName)) {
                outProjectionMap.put(columnName, "secret");
            } else if (InterestingPhotos.SERVER.equals(columnName)) {
                outProjectionMap.put(columnName, "server");
            } else if (InterestingPhotos.FARM.equals(columnName)) {
                outProjectionMap.put(columnName, "farm");
            } else if (InterestingPhotos.TITLE.equals(columnName)) {
                outProjectionMap.put(columnName, "title");
            } else {
                outProjectionMap.put(columnName, columnName);
            }
        }
    }

    public static final Uri getPhotosUri() {
        return END_POINT
                .buildUpon()
                .appendQueryParameter("method",
                        "flickr.interestingness.getList")
                .appendQueryParameter("format", "json")
                .appendQueryParameter("api_key", API_KEY).build();
    }

    public static final JSONArray parsePhotos(String json) throws JSONException {
        json = json.substring("jsonFlickrApi(".length(), json.length() - 1);
        JSONObject root = new JSONObject(json);
        return root.getJSONObject("photos").getJSONArray("photo");
    }

    public static final class FlickrInterestingnessAdapter extends
            CursorAdapter {

        public static final String[] PROJECTION = new String[] {
                InterestingPhotos._ID, InterestingPhotos.SECRET,
                InterestingPhotos.SERVER, InterestingPhotos.FARM,
                InterestingPhotos.TITLE };

        private static final int COLUMN_SECRET = 1;
        private static final int COLUMN_SERVER = 2;
        private static final int COLUMN_FARM = 3;
        private static final int COLUMN_TITLE = 4;

        private static int sItemHeight;

        private final ImageViewBinder mImageViewBinder;

        public FlickrInterestingnessAdapter(Context context) {
            super(context, null, 0);
            mImageViewBinder = new ImageViewBinder(ImageLoader.get(context));
            mImageViewBinder.setLoadingResource(R.drawable.loading);
            mImageViewBinder.setErrorResource(R.drawable.unavailable);
            sItemHeight = context.getResources().getDimensionPixelSize(
                    android.R.dimen.thumbnail_height);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            ImageView view = new ImageView(parent.getContext());
            view.setLayoutParams(new AbsListView.LayoutParams(
                    LayoutParams.FILL_PARENT, sItemHeight));
            view.setScaleType(ScaleType.CENTER_CROP);
            return view;
        }

        private static String getPhotoUrl(Cursor cursor) {
            return (FILENAME_TEMPLATE
                    .replaceFirst("\\{farm-id\\}",
                            cursor.getString(COLUMN_FARM))
                    .replaceFirst("\\{server-id\\}",
                            cursor.getString(COLUMN_SERVER))
                    .replaceFirst("\\{secret\\}",
                            cursor.getString(COLUMN_SECRET)).replaceFirst(
                    "\\{id\\}", cursor.getString(0)));
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            ImageView imageView = (ImageView) view;
            String url = getPhotoUrl(cursor);
            mImageViewBinder.bind(imageView, url);
        }

    }

    public static final class FlickrContentHandler extends JsonContentHandler {

        private final long mMaxAge;
        private final MatrixCursor mOutput;
        private final Map<String, String> mProjectionMap;

        public FlickrContentHandler(MatrixCursor output,
                Map<String, String> projectionMap, long maxAge) {
            super();
            mOutput = output;
            mProjectionMap = projectionMap;
            mMaxAge = maxAge;
        }

        @Override
        public Object getContent(URLConnection connection) throws IOException {
            connection
                    .setRequestProperty("Cache-Control", "max-age=" + mMaxAge);
            return super.getContent(connection);
        }

        @Override
        protected Object getContent(String json) throws JSONException {
            JSONArray photos = FlickrUtil.parsePhotos(json);

            for (int i = 0; i < photos.length(); i++) {
                JSONObject photo = photos.getJSONObject(i);
                MatrixCursor.RowBuilder builder = mOutput.newRow();
                for (String columnName : mOutput.getColumnNames()) {
                    builder.add(photo.optString(mProjectionMap.get(columnName)));
                }
            }

            return FeedLoader.documentInfo(photos.length());
        }

    }

}
