package com.kanbig.facesdk.facedetect;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This class is an implementation of the Bridge View between OpenCV and Java Camera.
 * This class relays on the functionality available in base class and only implements
 * required functions:
 * connectCamera - opens Java camera and sets the PreviewCallback to be delivered.
 * disconnectCamera - closes the camera and stops preview.
 * When frame is delivered via callback from Camera - it processed via OpenCV to be
 * converted to RGBA32 and then passed to the external callback for modifications if required.
 */
public class JavaCameraView extends CameraBridgeViewBase implements PreviewCallback {

    private static final int MAGIC_TEXTURE_ID = 10;
    private static final String TAG = "JavaCameraView";

    private byte mBuffer[];
    private Mat[] mYUVMats;
    private int mMatIndex = 0;
    private Thread mThread;
    private volatile boolean mStopThread;

    protected Camera mCamera;
    protected JavaCameraFrame mCameraFrame;
    private SurfaceTexture mSurfaceTexture;

    private CameraFrameListener mListener;
    private static WindowManager windowManager;

    private int mCameraWidth;
    private int mCameraHeight;

    private ReentrantLock[] locks = {new ReentrantLock(), new ReentrantLock()};
    private OrientationEventListener mOrEventListener = new OrientationEventListener(getContext()) {
        @Override
        public void onOrientationChanged(int rotation) {
//                Log.i("OrientationChanged", "当前屏幕手持角度方向:" + rotation + "°");
            if (rotation < 0) {
                mRotation = rotation;
            } else if (rotation >= 45 && rotation < 135) {
                mRotation = Surface.ROTATION_90;
            } else if (rotation >= 135 && rotation < 225) {
                mRotation = Surface.ROTATION_180;
            } else if (rotation >= 225 && rotation < 315) {
                mRotation = Surface.ROTATION_270;
            } else {
                mRotation = Surface.ROTATION_0;
            }

        }
    };
    private volatile int mPreRotation = 0;

    public void rotate(int rotation) {
        mPreRotation = rotation;
    }


    public interface CameraFrameListener {
        /**
         * @param frameMat
         * @return
         */
        Mat onCameraFrame(Mat frameMat);
    }


    public static class JavaCameraSizeAccessor implements ListItemAccessor {

        @Override
        public int getWidth(Object obj) {
            Camera.Size size = (Camera.Size) obj;
            return size.width;
        }

        @Override
        public int getHeight(Object obj) {
            Camera.Size size = (Camera.Size) obj;
            return size.height;
        }
    }

    public JavaCameraView(Context context, int cameraId) {
        super(context, cameraId);
        init();
    }

    public JavaCameraView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        if (windowManager == null) {
            synchronized (JavaCameraView.class) {
                if (windowManager == null) {
                    windowManager = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
                }
            }
        }
    }

    public void setCameraFrameListener(CameraFrameListener listener) {
        mListener = listener;
    }


    private int getRotateDegree(boolean reverse) {

        int degree = (mRotation) & 3;

        if (reverse) {
            return (4 - degree) & 3;
        }
        return degree;
    }

    private void rotateFrameMat(Mat fromMat, Mat toMat, boolean reverse) {
        int degree = getRotateDegree(reverse);
        rotateMat(fromMat, toMat, degree);
    }

    private void rotateMat(Mat fromMat, Mat toMat, int degree) {
        if (degree > 0) {
            Core.rotate(fromMat, toMat, degree - 1);
        }
//        switch (degree) {
//            case Surface.ROTATION_90:
//                Core.rotate(fromMat, toMat, Core.ROTATE_90_CLOCKWISE);
//                break;
//            case Surface.ROTATION_180:
//                Core.rotate(fromMat, toMat, Core.ROTATE_180);
//                break;
//            case Surface.ROTATION_270:
//                Core.rotate(fromMat, toMat, Core.ROTATE_90_COUNTERCLOCKWISE);
//                break;
//
//        }
    }


    private static int getViewRotation() {
        return windowManager.getDefaultDisplay().getRotation();
    }

    protected boolean initializeCamera(int width, int height) {
        Log.d(TAG, "Initialize java camera");
        boolean result = true;
        synchronized (this) {
            mCamera = null;

            if (mCameraIndex == CAMERA_ID_ANY) {
                Log.d(TAG, "Trying to open camera with old open()");
                try {
                    mCamera = Camera.open();
                } catch (Exception e) {
                    Log.e(TAG, "Camera is not available (in use or does not exist): " + e.getLocalizedMessage());
                }

                if (mCamera == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
                    boolean connected = false;
                    for (int camIdx = 0; camIdx < Camera.getNumberOfCameras(); ++camIdx) {
                        Log.d(TAG, "Trying to open camera with new open(" + Integer.valueOf(camIdx) + ")");
                        try {
                            mCamera = Camera.open(camIdx);
                            connected = true;
                        } catch (RuntimeException e) {
                            Log.e(TAG, "Camera #" + camIdx + "failed to open: " + e.getLocalizedMessage());
                        }
                        if (connected) break;
                    }
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
                    int localCameraIndex = mCameraIndex;
                    if (mCameraIndex == CAMERA_ID_BACK) {
                        Log.i(TAG, "Trying to open back camera");
                        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
                        for (int camIdx = 0; camIdx < Camera.getNumberOfCameras(); ++camIdx) {
                            Camera.getCameraInfo(camIdx, cameraInfo);
                            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                                localCameraIndex = camIdx;
                                break;
                            }
                        }
                    } else if (mCameraIndex == CAMERA_ID_FRONT) {
                        Log.i(TAG, "Trying to open front camera");
                        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
                        for (int camIdx = 0; camIdx < Camera.getNumberOfCameras(); ++camIdx) {
                            Camera.getCameraInfo(camIdx, cameraInfo);
                            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                                localCameraIndex = camIdx;
                                break;
                            }
                        }
                    }
                    if (localCameraIndex == CAMERA_ID_BACK) {
                        Log.e(TAG, "Back camera not found!");
                    } else if (localCameraIndex == CAMERA_ID_FRONT) {
                        Log.e(TAG, "Front camera not found!");
                    } else {
                        Log.d(TAG, "Trying to open camera with new open(" + Integer.valueOf(localCameraIndex) + ")");
                        try {
                            mCamera = Camera.open(localCameraIndex);
                        } catch (RuntimeException e) {
                            Log.e(TAG, "Camera #" + localCameraIndex + "failed to open: " + e.getLocalizedMessage());
                        }
                    }
                }
            }

            if (mCamera == null)
                return false;

            /* Now set camera parameters */
            try {
                Camera.Parameters params = mCamera.getParameters();
                Log.d(TAG, "getSupportedPreviewSizes()");
                List<Camera.Size> sizes = params.getSupportedPreviewSizes();

                if (sizes != null) {
                    /* Select the size that fits surface considering maximum size allowed */
                    Size frameSize = calculateCameraFrameSize(sizes, new JavaCameraSizeAccessor(), width, height);

                    params.setPreviewFormat(ImageFormat.NV21);
                    Log.d(TAG, "Set preview size to " + Integer.valueOf((int) frameSize.width) + "x" + Integer.valueOf((int) frameSize.height));

                    mCameraWidth = (int) frameSize.width;
                    mCameraHeight = (int) frameSize.height;

                    mFrameWidth = (int) frameSize.width;
                    mFrameHeight = (int) frameSize.height;

                    params.setPreviewSize(mCameraWidth, mCameraHeight);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH && !Build.MODEL.equals("GT-I9100"))
                        params.setRecordingHint(true);

                    List<String> FocusModes = params.getSupportedFocusModes();
                    if (FocusModes != null && FocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                        params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                    }

                    mCamera.setParameters(params);
                    params = mCamera.getParameters();


                    if ((getLayoutParams().width == LayoutParams.MATCH_PARENT) && (getLayoutParams().height == LayoutParams.MATCH_PARENT)) {
                        mScale = Math.min(((float) height) / mFrameHeight, ((float) width) / mFrameWidth);
                    } else {
                        mScale = 0;
                    }
                    if (mFpsMeter != null) {
                        mFpsMeter.setResolution(mCameraWidth, mCameraHeight);
                    }

                    int size = mFrameWidth * mFrameHeight;
                    size = size * ImageFormat.getBitsPerPixel(params.getPreviewFormat()) / 8;
                    mBuffer = new byte[size];

                    mCamera.addCallbackBuffer(mBuffer);
                    mCamera.setPreviewCallbackWithBuffer(this);

                    mYUVMats = new Mat[2];
                    mYUVMats[0] = new Mat(mCameraHeight + (mCameraHeight >>> 1), mCameraWidth, CvType.CV_8UC1);
                    mYUVMats[1] = new Mat(mCameraHeight + (mCameraHeight >>> 1), mCameraWidth, CvType.CV_8UC1);

                    AllocateCache();

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                        mSurfaceTexture = new SurfaceTexture(MAGIC_TEXTURE_ID);
                        mCamera.setPreviewTexture(mSurfaceTexture);
                    } else
                        mCamera.setPreviewDisplay(null);

                    /* Finally we are ready to start the preview */
                    Log.d(TAG, "startPreview");
//                    skipFrameNum = 10;
                    mCamera.startPreview();
                } else
                    result = false;
            } catch (Exception e) {
                result = false;
                e.printStackTrace();
            }
        }

        return result;
    }

    protected void releaseCamera() {
        synchronized (this) {
            if (mCamera != null) {
                mCamera.stopPreview();
                mCamera.setPreviewCallback(null);

                mCamera.release();
            }
            mCamera = null;
            if (mYUVMats != null) {
                for (int i = 0; i < 2; i++) {
                    mYUVMats[i].release();
                }
            }
        }
    }


    @Override
    protected boolean connectCamera(int width, int height) {

        mOrEventListener.enable();

        /* 1. We need to instantiate camera
         * 2. We need to start thread which will be getting frames
         */
        /* First step - initialize camera connection */
        Log.d(TAG, "Connecting to camera");
        if (!initializeCamera(width, height))
            return false;


        /* now we can start update thread */
        Log.d(TAG, "Starting processing thread");
        mStopThread = false;
        mThread = new Thread(new CameraWorker());
        mThread.start();

        return true;
    }

    @Override
    protected void disconnectCamera() {
        mOrEventListener.disable();
        /* 1. We need to stop thread which updating the frames
         * 2. Stop camera and release it
         */
        Log.d(TAG, "Disconnecting from camera");
        try {
            synchronized (this) {
                mStopThread = true;
                this.notifyAll();
            }
            if (mThread != null)
                mThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            mThread = null;
        }

        /* Now release camera */
        releaseCamera();

    }

    @Override
    public void onPreviewFrame(byte[] frame, Camera arg1) {
        ReentrantLock lock = locks[mMatIndex];
        try {
            lock.lock();
            if (mYUVMats != null) {
                mYUVMats[mMatIndex].put(0, 0, frame);
                mMatIndex = 1 - mMatIndex;
                synchronized (this) {
                    notify();
                }
            }
        } finally {
            lock.unlock();
        }


        if (mCamera != null)
            mCamera.addCallbackBuffer(mBuffer);
    }


    private class JavaCameraFrame implements CvCameraViewFrame {
        private int mIndex = 0;
        private Mat mBgrMat = new Mat();
        private int rotation;


        //        private void rotate(Mat mat) {
//            int viewRotation = getViewRotation();
//            if (mCameraIndex == CAMERA_ID_FRONT) {
//                rotateMat(mat, mat, (viewRotation + 3) & 3);
////                switch (viewRotation) {
////                    case Surface.ROTATION_0:
////                        rotateMat(mat, mat, Surface.ROTATION_270);
////                        break;
////                    case Surface.ROTATION_90:
////                        break;
////                    case Surface.ROTATION_180:
////                        rotateMat(mat, mat, Surface.ROTATION_90);
////                        break;
////                    case Surface.ROTATION_270:
////                        rotateMat(mat, mat, Surface.ROTATION_180);
////                        break;
////                }
//                Core.flip(mat, mat, 1);
//            } else {
//                rotateMat(mat, mat, (5 - viewRotation) & 3);
////                switch (viewRotation) {
////                    case Surface.ROTATION_0:
////                        rotateMat(mat, mat, Surface.ROTATION_90);
////                        break;
////                    case Surface.ROTATION_90:
////                        break;
////                    case Surface.ROTATION_180:
////                        rotateMat(mat, mat, Surface.ROTATION_270);
////                        break;
////                    case Surface.ROTATION_270:
////                        rotateMat(mat, mat, Surface.ROTATION_180);
////                        break;
////                }
//            }
//        }
        private void rotate(int curRotation) {
            int degree;
            if (mCameraIndex == CAMERA_ID_FRONT) {
                Core.flip(mBgrMat, mBgrMat, 0);
                degree = (3 + mPreRotation + curRotation) & 3;
            } else {
                degree = (1 + mPreRotation + curRotation) & 3;
            }
            Log.d(TAG, "rotate: " + degree);
            rotateMat(mBgrMat, mBgrMat, degree);
        }


        private void rotateOnce() {
            int degree;
            if (mCameraIndex == CAMERA_ID_FRONT) {
                Core.flip(mBgrMat, mBgrMat, 0);
                degree = (7 + mPreRotation) & 3;
            } else {
                degree = (5 + mPreRotation) & 3;
            }
            Log.d(TAG, "rotateOnce: " + degree);
            rotateMat(mBgrMat, mBgrMat, degree);
        }

        @Override
        public Mat mat() {
            while (true) {
                ReentrantLock lock = locks[mIndex];
                if (lock.tryLock()) {
                    try {

                        if (mYUVMats[mIndex].empty()) {
                            return null;
                        }
                        Imgproc.cvtColor(mYUVMats[mIndex], mBgrMat, Imgproc.COLOR_YUV2BGR_NV21, 3);

                        mIndex = 1 - mIndex;
                        break;
                    } finally {
                        lock.unlock();
                    }
                }
                mIndex = 1 - mIndex;
            }
            if (rotation != mRotation) {
                rotation = mRotation;
                skipFrameNum = 2;
            }
            if (rotation >= 0) {
                rotate(rotation);
            } else {
                rotateOnce();
            }
            if (skipFrameNum < 0 && mListener != null)
                mListener.onCameraFrame(mBgrMat);
            if (mBgrMat.size() != mCachedRGBMat.size())
                mCachedRGBMat.create(mBgrMat.size(), mBgrMat.type());
            Imgproc.cvtColor(mBgrMat, mCachedRGBMat, Imgproc.COLOR_BGR2RGB, 3);
            return mCachedRGBMat;
        }

        public void release() {
            mBgrMat.release();
        }

    }


    private class CameraWorker implements Runnable {

        @Override
        public void run() {
            while (!mStopThread) {
                synchronized (JavaCameraView.this) {
                    try {
                        if (!mStopThread) {
                            JavaCameraView.this.wait();
                        }
                        if (mStopThread) {
                            mStopThread = false;
                            break;
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                if (mCameraFrame == null) {
                    mCameraFrame = new JavaCameraFrame();
                }
                deliverAndDrawFrame(mCameraFrame);
            }
            if (mCameraFrame != null) {
                mCameraFrame.release();
                mCameraFrame = null;
            }
        }
    }
}
