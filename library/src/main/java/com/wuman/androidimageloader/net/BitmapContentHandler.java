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

package com.wuman.androidimageloader.net;

import java.io.IOException;
import java.io.InputStream;
import java.net.ContentHandler;
import java.net.URLConnection;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/**
 * A {@link ContentHandler} that decodes a {@link Bitmap} from a
 * {@link URLConnection}.
 * <p>
 * The implementation includes a work-around for <a
 * href="http://code.google.com/p/android/issues/detail?id=6066">Issue 6066</a>.
 * <p>
 * An {@link IOException} is thrown if there is a decoding exception.
 */
public class BitmapContentHandler extends ContentHandler {

    private static final int DEFAULT_TIMEOUT = 0;

    private int mTimeout = DEFAULT_TIMEOUT;

    public final void setTimeout(int millis) {
        mTimeout = millis;
    }

    @Override
    public Bitmap getContent(URLConnection connection) throws IOException {
        connection.setConnectTimeout(mTimeout);
        connection.setReadTimeout(mTimeout);

        InputStream input = connection.getInputStream();
        try {
            input = new BlockingFilterInputStream(input);
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            if (bitmap == null) {
                throw new IOException("Image could not be decoded");
            }
            return bitmap;
        } finally {
            input.close();
        }
    }
}
