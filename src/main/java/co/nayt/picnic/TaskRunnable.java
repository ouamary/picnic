package co.nayt.picnic;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * This task downloads bytes from a resource addressed by a URL. When the task
 * has finished, it calls handleState to report its results.
 */
class TaskRunnable implements Runnable {
    private static final String LOG_TAG = "Picnic - TaskRunnable";

    // Download state constants
    static final int HTTP_STATE_FAILED = -1;
    static final int HTTP_STATE_STARTED = 0;
    static final int HTTP_STATE_COMPLETED = 1;

    // Decode state constants
    static final int DECODE_STATE_FAILED = 2;
    static final int DECODE_STATE_STARTED = 3;
    static final int DECODE_STATE_COMPLETED = 4;

    // Defines a field that contains the calling object of type Task.
    private final TaskRunnableDownloadMethods mPictureTask;

    /**
     * An interface that defines methods that Task implements. An instance of
     * Task passes itself to a TaskRunnable instance through the
     * TaskRunnable constructor, after which the two instances can access each other's
     * variables.
     */
    interface TaskRunnableDownloadMethods {

        /**
         * Sets the Thread that this instance is running on
         * @param currentThread the current Thread
         */
        void setDownloadThread(Thread currentThread);

        /**
         * Returns the current contents of the decoded Bitmap
         * @return The Bitmap decoded from the content located at the URL
         */
        Bitmap getBitmap();

        /**
         * Sets the current contents of the Bitmap
         * @param bitmap The Bitmap that was just decoded
         */
        void setBitmap(Bitmap bitmap);

        /**
         * Defines the actions for each state of the Task instance.
         * @param state The current state of the task
         */
        void handleTaskState(int state);

        /**
         * Gets the URL for the image being downloaded
         * @return The image URL
         */
        String getImageURL();

        /**
         * Returns the desired width of the image, based on the ImageView being created.
         * @return The target width
         */
        int getTargetWidth();

        /**
         * Returns the desired height of the image, based on the ImageView being created.
         * @return The target height.
         */
        int getTargetHeight();

        /**
         * Clears the cache.
         */
        void clearCache();
    }

    /**
     * This constructor creates an instance of TaskRunnable and stores in it a reference
     * to the Task instance that instantiated it.
     *
     * @param task The Task, which implements TaskRunnableDownloadMethods
     */
    TaskRunnable(TaskRunnableDownloadMethods task) {
        mPictureTask = task;
    }

    @Override
    public void run() {
        mPictureTask.setDownloadThread(Thread.currentThread());

        // Moves the current Thread into the background
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);

        Bitmap bitmap = mPictureTask.getBitmap();

        try {
            // Before continuing, checks to see that the Thread hasn't been interrupted
            if (Thread.interrupted()) {

                throw new InterruptedException();
            }

            if (bitmap == null) {
                mPictureTask.handleTaskState(HTTP_STATE_STARTED);

                final OkHttpClient client = new OkHttpClient();

                Request request = new Request.Builder()
                        .url(mPictureTask.getImageURL())
                        .build();

                Response response;

                int targetWidth = mPictureTask.getTargetWidth();
                int targetHeight = mPictureTask.getTargetHeight();

                try {
                    response = client.newCall(request).execute();

                    if (response.isSuccessful()) {
                        mPictureTask.handleTaskState(HTTP_STATE_COMPLETED);

                        mPictureTask.handleTaskState(DECODE_STATE_STARTED);
                        bitmap = decodeSampledBitmap(response.body().bytes(), targetWidth, targetHeight);
                        Log.d(LOG_TAG, "Success - HTTP");
                    } else {
                        Log.d(LOG_TAG, "Failure - HTTP");
                    }
                } catch (NullPointerException n) {
                    bitmap = null;
                    mPictureTask.handleTaskState(DECODE_STATE_FAILED);
                    Log.d(LOG_TAG, "Failure - Decode Null");
                    n.printStackTrace();
                } catch (IOException i) {
                    bitmap = null;
                    mPictureTask.handleTaskState(DECODE_STATE_FAILED);
                    Log.d(LOG_TAG, "Failure - Decode IO");
                    i.printStackTrace();
                } catch (OutOfMemoryError o) {
                    mPictureTask.clearCache();
                    bitmap = null;
                    mPictureTask.handleTaskState(DECODE_STATE_FAILED);
                    Log.d(LOG_TAG, "Failure - Decode OOM");
                    o.printStackTrace();
                }
            }
        } catch (InterruptedException e1) {
            // Does nothing
            // In all cases, handle the results
        } finally {
            if (bitmap == null) {
                mPictureTask.handleTaskState(HTTP_STATE_FAILED);
            } else {
                mPictureTask.setBitmap(bitmap);
                mPictureTask.handleTaskState(DECODE_STATE_COMPLETED);
            }

            mPictureTask.setDownloadThread(null);
            Thread.interrupted();
        }
    }

    /**
     * Creates a Bitmap from the byte array and scales it down using supplied dimensions. It decodes
     * the bitmap twice: first, to get the original dimensions for calculating inSampleSize and then
     * to return the Bitmap.
     *
     * @param is A byte array containing the result of the network call
     * @param reqWidth The target width of the resulting image
     * @param reqHeight The target height of the resulting image
     * @return The decoded Bitmap
     */
    private Bitmap decodeSampledBitmap(byte[] is, int reqWidth, int reqHeight) throws IOException {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;

        BitmapFactory.decodeByteArray(is, 0, is.length, options);
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeByteArray(is, 0, is.length, options);
    }

    /**
     * Calculates the inSampleSize option used by the BitmapFactory to scale a Bitmap down.
     *
     * @param options The original BitmapFactory options
     * @param reqWidth The target width of the resulting image
     * @param reqHeight The target height of the resulting image
     * @return The resulting inSampleSize
     */
    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if(reqWidth > 0 && reqHeight > 0) {
            if (height > reqHeight || width > reqWidth) {

                final int halfHeight = height / 2;
                final int halfWidth = width / 2;

                while ((halfHeight / inSampleSize) >= reqHeight
                        && (halfWidth / inSampleSize) >= reqWidth) {
                    inSampleSize *= 2;
                }
            }
        }

        return inSampleSize;
    }
}
