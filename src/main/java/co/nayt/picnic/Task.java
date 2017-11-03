package co.nayt.picnic;

import android.graphics.Bitmap;
import android.widget.ImageView;

import java.lang.ref.WeakReference;

import co.nayt.picnic.TaskRunnable.TaskRunnableDownloadMethods;

/**
 * This class manages the TaskRunnable object which downloads and decodes images. It implements
 * the TaskRunnableDownloadMethods interface that the TaskRunnable defines to perform the action.
 */
class Task implements TaskRunnableDownloadMethods {
    /*
     * Creates a weak reference to the ImageView that this Task will populate
     * to prevent memory leaks and crashes.
     */
    private WeakReference<ImageView> mImageWeakRef;

    private String mImageURL;

    private int mTargetHeight;

    private int mTargetWidth;

    private Runnable mDownloadRunnable;

    private Bitmap mImageBitmap;

    private Thread mCurrentThread;

    private static Picnic sInstance;

    Thread mThreadThis;

    /**
     * Creates a Task containing the download runnable object
     */
    Task() {
        mDownloadRunnable = new TaskRunnable(this);
        sInstance = Picnic.getInstance();
    }

    /**
     * Initializes the Task
     *
     * @param instance A ThreadPool object
     * @param imageView An ImageView instance that shows the downloaded image
     * @param targetWidth The target width of the downloaded image
     * @param targetHeight The target height of the downloaded image
     */
    void initializeDownloaderTask(Picnic instance, ImageView imageView, String url, int targetWidth, int targetHeight) {
        sInstance = instance;
        mImageURL = url;
        mImageWeakRef = new WeakReference<>(imageView);
        mTargetWidth = targetWidth;
        mTargetHeight = targetHeight;
    }

    /**
     * Recycles a Task object before it's put back into the pool. One reason to do
     * this is to avoid memory leaks.
     */
    void recycle() {
        if (mImageWeakRef != null) {
            mImageWeakRef.clear();
            mImageWeakRef = null;
        }

        mImageBitmap = null;
    }

    /**
     * Delegates handling the current state of the task to the Picnic object.
     *
     * @param state The current state
     */
    private void handleState(int state) {
        sInstance.handleState(this, state);
    }

    /**
     * Returns the instance that downloaded the image.
     */
    Runnable getHTTPDownloadRunnable() {
        return mDownloadRunnable;
    }

    /**
     * Returns the ImageView that's being constructed.
     */
    public ImageView getView() {
        if (mImageWeakRef != null) {
            return mImageWeakRef.get();
        }
        return null;
    }

    /**
     * Returns the Thread that this Task is running on. It uses a lock on the ThreadPool singleton
     * to prevent thread interference.
     */
    Thread getCurrentThread() {
        synchronized(sInstance) {
            return mCurrentThread;
        }
    }

    /**
     * Sets the identifier for the current Thread.
     */
    private void setCurrentThread(Thread thread) {
        synchronized(sInstance) {
            mCurrentThread = thread;
        }
    }

    @Override
    public Bitmap getBitmap() {
        return mImageBitmap;
    }

    @Override
    public int getTargetWidth() {
        return mTargetWidth;
    }

    @Override
    public int getTargetHeight() {
        return mTargetHeight;
    }

    @Override
    public void clearCache() {
        sInstance.clearCache();
    }

    @Override
    public String getImageURL() {
        return mImageURL;
    }

    @Override
    public void setBitmap(Bitmap bitmap) {
        mImageBitmap = bitmap;
    }

    @Override
    public void setDownloadThread(Thread currentThread) {
        setCurrentThread(currentThread);
    }

    @Override
    public void handleTaskState(int state) {
        int outState;

        switch(state) {
            case TaskRunnable.HTTP_STATE_COMPLETED:
                outState = Picnic.DOWNLOAD_COMPLETE;
                break;
            case TaskRunnable.HTTP_STATE_FAILED:
                outState = Picnic.DOWNLOAD_FAILED;
                break;
            case TaskRunnable.HTTP_STATE_STARTED:
                outState = Picnic.DOWNLOAD_STARTED;
                break;
            case TaskRunnable.DECODE_STATE_COMPLETED:
                outState = Picnic.TASK_COMPLETE;
                break;
            case TaskRunnable.DECODE_STATE_FAILED:
                outState = Picnic.DOWNLOAD_FAILED;
                break;
            default:
                outState = Picnic.DECODE_STARTED;
                break;
        }

        handleState(outState);
    }
}
