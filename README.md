AndroidImageLoader
==================

AndroidImageLoader is a fork of the 
[Image Loader](http://code.google.com/p/libs-for-android/wiki/ImageLoader) 
component in [libs-for-android](http://code.google.com/p/libs-for-android/).

The AndroidImageLoader is an Android library that helps to load images 
asynchronously. Like its upstream libs-for-android project, it executes
image requests in a thread pool and provides caching support. The following
features of libs-for-android are kept:

* Images are downloaded and saved to cache via a pool of background threads.
* Supports preloading of off-screen images into a memory cache.
* Supports prefetching of images into a disk cache.
* `HttpUrlConnection` is used for loading images, which respects cache control.
* Custom `URLStreamHandlerFactory` is supported for creating connections to
  special URLs such as `content://` URIs.
* Supports `Bitmap` transformations by accepting a `ContentHandler` for loading
  images.

The AndroidImageLoader improves libs-for-android in the following ways:

* In addition to memory cache, the library also supports second level disk caching.
  Caching support in AndroidImageLoader is now made to depend on the
  [TwoLevelLruCache](http://wuman.github.com/TwoLevelLruCache/) library, which in
  turn depends on the more widely used `LruCache` and `DiskLruCache`.
* Image requests are now placed into a LIFO task queue, which makes more sense in
  most scrolling scenarios.
* The API for view binding is now in a separate `ViewBinder` class. Applications
  can use the `ImageViewBinder` class to bind to `ImageView`s or extend the 
  `AbstractViewBinder` class for custom views. Also, `ImageView`s within an
  `AdapterView` no longer require a different kind of binding.
* `OutOfMemoryError`s are automatically detected and caught. When memory is
  running low, the cache size is automatically decreased to give back more
  memory to the system.
* Prefetching can now be supported out of the box via the `SinkContentHandler`
  and the [HttpResponseCache library](https://github.com/candrews/HttpResponseCache).


USAGE
-----

The ImageLoader should be installed as a pseudo-system service. This is so that
it can be used as a singleton and accessed across Activities. You do this by
declaring a customized `Application` in the `AndroidManifest.xml`:

    public class SamplesApplication extends Application {
        private ImageLoader mImageLoader;

        @Override public void onCreate() {
            super.onCreate();
            try {
                mImageLoader = createImageLoader(this);
            } catch (IOException e) {
            }
        }

        @Override public void onTerminate() {
            mImageLoader = null;
            super.onTerminate();
        }

        @Override public Object getSystemService(String name) {
            if (ImageLoader.IMAGE_LOADER_SERVICE.equals(name)) {
                return mImageLoader;
            } else {
                return super.getSystemService(name);
            }
        }
        
        ...
    }

An example of creating the `ImageLoader` instance can be:

    private static ImageLoader createImageLoader(Context context)
            throws IOException {
        ContentHandler prefetchHandler = null;
        try {
            HttpResponseCache.install(new File(context.getCacheDir(), "HttpCache"),
                    ImageLoader.DEFAULT_CACHE_SIZE * 2);
            prefetchHandler = new SinkContentHandler();
        } catch (Exception e) {
        } 

        // Use a custom URLStreamHandlerFactory if special URL scheme is needed
        URLStreamHandlerFactory streamFactory = null;

        // Load images using the default BitmapContentHandler
        ContentHandler bitmapHandler = new BitmapContentHandler();
        bitmapHandler.setTimeout(5000);
        
        return new ImageLoader(streamFactory, bitmapHandler, prefetchHandler,
            ImageLoader.DEFAULT_CACHE_SIZE, new File(context.getCacheDir(), "images"));        
    }

Binding an image to `ImageView` is done via `ImageViewBinder`:

    ImageViewBinder binder = new ImageViewBinder(mImageLoader);
    binder.bind(imageView, url);

Optionally you can set alternative images to display:

    binder.setLoadingResource(R.drawable.loading);
    binder.setErrorResource(R.drawable.unavailable);

By default images loaded from disk cache or network are faded in. You can
disable this special effect by:

    binder.setFadeIn(false);

Note that the `ViewBinder` automatically checks whether the target `View` is
still requesting the same URL or already recycled to request another image.

Custom view binding is supported by extending the `AbstractViewBinder<V>` class
and overriding at least the following two methods:

* `void onImageLoaded(V targetView, Bitmap bitmap, String url, LoadSource loadSource)`
* `void onImageError(V targetView, String url, Throwable error)`


SAMPLES
-------

Sample applications are provided to better understand how to use the library.

* _ImageViewBinding_: loading of images using the `ImageViewBinder`.
* _CustomViewBinding_: loading of images into custom views by extending the 
  `AbstractViewBinder` class.
* _FlickrInterestingness_: practical example of loading Flickr images from its 
  Web API using the AndroidImageLoader in combination with the 
  [AndroidFeedLoader](http://wuman.github.com/AndroidFeedLoader/).


INCLUDING IN YOUR PROJECT
-------------------------

There are two ways to include AndroidImageLoader in your projects:

1. You can download the released jar file in the [Downloads section](https://github.com/wuman/AndroidImageLoader/downloads).
2. If you use Maven to build your project you can simply add a dependency to this library.

        <dependency>
            <groupId>com.wu-man</groupId>
            <artifactId>androidimageloader-library</artifactId>
            <version>0.1</version>
        </dependency>


CONTRIBUTE
----------

If you would like to contribute code to AndroidImageLoader you can do so through 
GitHub by forking the repository and sending a pull request.


DEVELOPED BY
------------

* David Wu - <david@wu-man.com> - [http://blog.wu-man.com](http://blog.wu-man.com)


LICENSE
-------

    Copyright 2012 David Wu
    Copyright (C) 2010 Google Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software 
    distributed under the License is distributed on an "AS IS" BASIS, 
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and 
    limitations under the License.

