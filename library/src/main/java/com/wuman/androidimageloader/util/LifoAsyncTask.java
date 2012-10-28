/*                                                                                                                                                                    
 * Copyright (C) 8 The Android Open Source Project                                                                                                                 
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

package com.wuman.androidimageloader.util;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.wuman.androidimageloader.util.concurrent.ArrayDeque;
import com.wuman.androidimageloader.util.concurrent.LinkedBlockingStack;

import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.util.Log;

public abstract class LifoAsyncTask<Params, Progress, Result> {

    private static final String LOG_TAG = LifoAsyncTask.class.getSimpleName();

    private static final int CORE_POOL_SIZE = 5;
    private static final int MAXIMUM_POOL_SIZE = Integer.MAX_VALUE;
    private static final int KEEP_ALIVE = 1;

    private static final ThreadFactory sThreadFactory = new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger(1);

        public Thread newThread(Runnable r) {
            return new Thread(r, "LifoAsyncTask #" + mCount.getAndIncrement());
        }
    };

    // @formatter:off
    private static final BlockingQueue<Runnable> sPoolWorkQueue = 
            new LinkedBlockingStack<Runnable>();
    // @formatter:on

    public static final Executor LIFO_THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(
            CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE, TimeUnit.SECONDS,
            sPoolWorkQueue, sThreadFactory);

    public static final Executor SERIAL_LIFO_EXECUTOR = new SerialLifoExecutor();

    private static final int MESSAGE_POST_RESULT = 0x1;
    private static final int MESSAGE_POST_PROGRESS = 0x2;

    private static final InternalHandler sHandler = new InternalHandler();

    private static volatile Executor sDefaultExecutor = SERIAL_LIFO_EXECUTOR;
    private final WorkerRunnable<Params, Result> mWorker;
    private final FutureTask<Result> mFuture;

    private volatile Status mStatus = Status.PENDING;

    private final AtomicBoolean mCancelled = new AtomicBoolean();
    private final AtomicBoolean mTaskInvoked = new AtomicBoolean();

    private static class SerialLifoExecutor implements Executor {
        final ArrayDeque<Runnable> mTasks = new ArrayDeque<Runnable>();
        Runnable mActive;

        public synchronized void execute(final Runnable r) {
            mTasks.offerLast(new Runnable() {
                public void run() {
                    try {
                        r.run();
                    } finally {
                        scheduleNext();
                    }
                }
            });
            if (mActive == null) {
                scheduleNext();
            }
        }

        protected synchronized void scheduleNext() {
            if ((mActive = mTasks.pollLast()) != null) {
                LIFO_THREAD_POOL_EXECUTOR.execute(mActive);
            }
        }
    }

    public enum Status {
        PENDING, RUNNING, FINISHED,
    }

    public static final void init() {
        sHandler.getLooper();
    }

    public static final void setDefaultExecutor(Executor exec) {
        sDefaultExecutor = exec;
    }

    public LifoAsyncTask() {
        mWorker = new WorkerRunnable<Params, Result>() {
            public Result call() throws Exception {
                mTaskInvoked.set(true);

                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                // noinspection unchecked
                return postResult(doInBackground(mParams));
            }
        };

        mFuture = new FutureTask<Result>(mWorker) {
            @Override
            protected void done() {
                try {
                    postResultIfNotInvoked(get());
                } catch (InterruptedException e) {
                    Log.w(LOG_TAG, e);
                } catch (ExecutionException e) {
                    throw new RuntimeException(
                            "An error occured while executing doInBackground()",
                            e.getCause());
                } catch (CancellationException e) {
                    postResultIfNotInvoked(null);
                }
            }
        };
    }

    private void postResultIfNotInvoked(Result result) {
        final boolean wasTaskInvoked = mTaskInvoked.get();
        if (!wasTaskInvoked) {
            postResult(result);
        }
    }

    private Result postResult(Result result) {
        @SuppressWarnings("unchecked")
        Message message = sHandler.obtainMessage(MESSAGE_POST_RESULT,
                new LifoAsyncTaskResult<Result>(this, result));
        message.sendToTarget();
        return result;
    }

    public final Status getStatus() {
        return mStatus;
    }

    protected abstract Result doInBackground(Params... params);

    protected void onPreExecute() {
    }

    @SuppressWarnings({ "UnusedDeclaration" })
    protected void onPostExecute(Result result) {
    }

    @SuppressWarnings({ "UnusedDeclaration" })
    protected void onProgressUpdate(Progress... values) {
    }

    @SuppressWarnings({ "UnusedParameters" })
    protected void onCancelled(Result result) {
        onCancelled();
    }

    protected void onCancelled() {
    }

    public final boolean isCancelled() {
        return mCancelled.get();
    }

    public final boolean cancel(boolean mayInterruptIfRunning) {
        mCancelled.set(true);
        return mFuture.cancel(mayInterruptIfRunning);
    }

    public final Result get() throws InterruptedException, ExecutionException {
        return mFuture.get();
    }

    public final Result get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return mFuture.get(timeout, unit);
    }

    public final LifoAsyncTask<Params, Progress, Result> execute(
            Params... params) {
        return executeOnExecutor(sDefaultExecutor, params);
    }

    public final LifoAsyncTask<Params, Progress, Result> executeOnExecutor(
            Executor exec, Params... params) {
        if (mStatus != Status.PENDING) {
            switch (mStatus) {
            case RUNNING:
                throw new IllegalStateException("Cannot execute task:"
                        + " the task is already running.");
            case FINISHED:
                throw new IllegalStateException("Cannot execute task:"
                        + " the task has already been executed "
                        + "(a task can be executed only once)");
            }
        }

        mStatus = Status.RUNNING;

        onPreExecute();

        mWorker.mParams = params;
        exec.execute(mFuture);

        return this;
    }

    public static final void execute(Runnable runnable) {
        sDefaultExecutor.execute(runnable);
    }

    protected final void publishProgress(Progress... values) {
        if (!isCancelled()) {
            sHandler.obtainMessage(MESSAGE_POST_PROGRESS,
                    new LifoAsyncTaskResult<Progress>(this, values))
                    .sendToTarget();
        }
    }

    private void finish(Result result) {
        if (isCancelled()) {
            onCancelled(result);
        } else {
            onPostExecute(result);
        }
        mStatus = Status.FINISHED;
    }

    private static class InternalHandler extends Handler {
        @SuppressWarnings({ "unchecked", "RawUseOfParameterizedType" })
        @Override
        public void handleMessage(Message msg) {
            LifoAsyncTaskResult result = (LifoAsyncTaskResult) msg.obj;
            switch (msg.what) {
            case MESSAGE_POST_RESULT:
                // There is only one result
                result.mTask.finish(result.mData[0]);
                break;
            case MESSAGE_POST_PROGRESS:
                result.mTask.onProgressUpdate(result.mData);
                break;
            }
        }
    }

    private static abstract class WorkerRunnable<Params, Result> implements
            Callable<Result> {
        Params[] mParams;
    }

    @SuppressWarnings({ "RawUseOfParameterizedType" })
    private static class LifoAsyncTaskResult<Data> {
        final LifoAsyncTask mTask;
        final Data[] mData;

        LifoAsyncTaskResult(LifoAsyncTask task, Data... data) {
            mTask = task;
            mData = data;
        }
    }

}
