package co.nayt.picnic;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.util.LruCache;
import android.util.Log;
import android.widget.ImageView;

import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static android.R.string.no;

/**
 * This class creates pools of background threads for downloading
 * images from the web using supplied URL strings.
 * The class is implemented as a singleton; the only way to get a Picnic instance is to
 * call {@link #getInstance}.
 * <p>
 * The class sets an LruCache image cache of 4MB and creates a fixed-size thread pool of 8 threads.
 * <p>
 * Finally, this class defines a handler that communicates back to the UI
 * thread to change the bitmap to reflect the state.
 */
public class Picnic {
    static final int DOWNLOAD_FAILED = -1;
    static final int DOWNLOAD_STARTED = 1;
    static final int DOWNLOAD_COMPLETE = 2;
    static final int DECODE_STARTED = 3;
    static final int TASK_COMPLETE = 4;

    private static final int IMAGE_CACHE_SIZE = 1024 * 1024 * 4;
    private static final int KEEP_ALIVE_TIME = 1;
    private static final TimeUnit KEEP_ALIVE_TIME_UNIT;

    private static final int CORE_POOL_SIZE = 8;
    private static final int MAXIMUM_POOL_SIZE = 8;

    private final LruCache<String, Bitmap> mPictureCache;

    private final BlockingQueue<Runnable> mDownloadWorkQueue;

    private final Queue<Task> mTaskWorkQueue;

    private final ThreadPoolExecutor mDownloadThreadPool;

    private Handler mHandler;

    private static Picnic sSingleton = null;

    static {
        KEEP_ALIVE_TIME_UNIT = TimeUnit.SECONDS;
        sSingleton = new Picnic();
    }

    /**
     * Constructs the work queue and the thread pool used to download and decode images.
     */
    private Picnic() {
        mDownloadWorkQueue = new LinkedBlockingQueue<>();
        mTaskWorkQueue = new LinkedBlockingQueue<>();
        mDownloadThreadPool = new ThreadPoolExecutor(CORE_POOL_SIZE, MAXIMUM_POOL_SIZE,
                KEEP_ALIVE_TIME, KEEP_ALIVE_TIME_UNIT, mDownloadWorkQueue);

        mPictureCache = new LruCache<String, Bitmap>(IMAGE_CACHE_SIZE) {
            /*
             * This overrides the default sizeOf() implementation to return the
             * correct size of each cache entry.
             */
            @Override
            protected int sizeOf(String uri, Bitmap bitmap) {
                return bitmap.getByteCount();
            }
        };

        mHandler = new Handler(Looper.getMainLooper()) {

            @Override
            public void handleMessage(Message inputMessage) {

                Task task = (Task) inputMessage.obj;
                ImageView imageView = task.getView();

                if (imageView != null) {
                    switch (inputMessage.what) {
                        case DOWNLOAD_STARTED:
                            imageView.setImageResource(R.drawable.imagedownloading);
                            break;
                        case DOWNLOAD_COMPLETE:
                            imageView.setImageResource(R.drawable.decodequeued);
                            break;
                        case DECODE_STARTED:
                            imageView.setImageResource(R.drawable.decodedecoding);
                            break;
                        case TASK_COMPLETE:
                            imageView.setImageBitmap(task.getBitmap());
                            recycleTask(task);
                            break;
                        case DOWNLOAD_FAILED:
                            imageView.setImageResource(R.drawable.imagedownloadfailed);
                            recycleTask(task);
                            break;
                        default:
                            super.handleMessage(inputMessage);
                    }
                }
            }
        };
    }

    /**
     * Handles state messages for a particular task object
     * @param task A task object
     * @param state The state of the task
     */
    @SuppressLint("HandlerLeak")
    void handleState(Task task, int state) {
        switch (state) {
            case TASK_COMPLETE:
                sSingleton.mPictureCache.put(task.getImageURL(), task.getBitmap());

                Message completeMessage = mHandler.obtainMessage(state, task);
                completeMessage.sendToTarget();
                break;

            default:
                mHandler.obtainMessage(state, task).sendToTarget();
                break;
        }

    }

    /**
     * Returns the Picnic object
     * @return The global Picnic object
     */
    static Picnic getInstance() {
        return sSingleton;
    }

    /**
     * Cancels all Threads in the ThreadPool
     */
    public static void cancelAll() {
        Task[] taskArray = new Task[sSingleton.mDownloadWorkQueue.size()];
        sSingleton.mDownloadWorkQueue.toArray(taskArray);

        synchronized (sSingleton) {
            for (Task aTaskArray : taskArray) {

                Thread thread = aTaskArray.mThreadThis;

                if (null != thread) {
                    thread.interrupt();
                }
            }
        }
    }

    /**
     * Stops a download Thread and removes it from the thread pool
     *
     * @param downloaderTask The download task associated with the Thread
     * @param pictureURL The URL being downloaded
     */
    static public void removeDownload(Task downloaderTask, String pictureURL) {
        if (downloaderTask != null && downloaderTask.getImageURL().equals(pictureURL)) {

            synchronized (sSingleton) {
                Thread thread = downloaderTask.getCurrentThread();

                if (null != thread)
                    thread.interrupt();
            }

            sSingleton.mDownloadThreadPool.remove(downloaderTask.getHTTPDownloadRunnable());
        }
    }

    /**
     * Starts an image download and decode. Both target width and height
     * must be supplied.
     *
     * @param imageView The ImageView that will get the resulting Bitmap
     * @param targetWidth The target width of the resulting Bitmap
     * @param targetHeight The target height of the resulting Bitmap
     * @return The task instance that will handle the work
     */
    static public Task startDownload(ImageView imageView, String url, int targetWidth, int targetHeight) {
        Task downloadTask = sSingleton.mTaskWorkQueue.poll();

        if (null == downloadTask) {
            downloadTask = new Task();
        }

        downloadTask.initializeDownloaderTask(Picnic.sSingleton, imageView, url, targetWidth, targetHeight);

        downloadTask.setBitmap(sSingleton.mPictureCache.get(downloadTask.getImageURL()));

        if (null == downloadTask.getBitmap()) {
            sSingleton.mDownloadThreadPool.execute(downloadTask.getHTTPDownloadRunnable());
            imageView.setImageResource(R.drawable.imagequeued);
        } else {
            sSingleton.handleState(downloadTask, TASK_COMPLETE);
        }

        return downloadTask;
    }

    /**
     * Recycles tasks by calling their internal recycle() method and then putting them back into
     * the task queue.
     * @param downloadTask The task to recycle
     */
    private void recycleTask(Task downloadTask) {
        downloadTask.recycle();
        mTaskWorkQueue.offer(downloadTask);
    }

    /**
     * Clears the cache and initiates garbage collection.
     */
    void clearCache() {
        mPictureCache.evictAll();
        System.gc();
    }
}
