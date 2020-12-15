package com.kanbig.facesdk.demo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

import com.kanbig.vision4m.PlateInfo;
import com.kanbig.vision4m.V4Edge;

import org.greenrobot.eventbus.EventBus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback, Camera.PreviewCallback {

    private static final String TAG = "CameraPreview";
    private Camera mCamera;
    private SurfaceHolder mHolder;
    public long handle;
    private byte[] lock = new byte[0];
    private List<String> mResultList = new ArrayList<>();
    private String currentPlate = "";
    private Paint mPaint;
    private float oldDist = 1f;
    /**
     * 停止识别
     */
    private boolean isStopReg;

    public CameraPreview(Context context) {
        super(context);
        mHolder = getHolder();
        mHolder.addCallback(this);
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setStrokeWidth(2);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setColor(ContextCompat.getColor(context, R.color.colorAccent));
    }

    public Camera getCameraInstance() {
        if (mCamera == null) {
            try {
                CameraHandlerThread mThread = new CameraHandlerThread("camera thread");
                synchronized (mThread) {
                    mThread.openCamera();
                }
            } catch (Exception e) {
                Log.e(TAG, "camera is not available");
            }
        }
        return mCamera;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mCamera = getCameraInstance();
        mCamera.setPreviewCallback(this);
        try {
            mCamera.setPreviewDisplay(mHolder);
            mCamera.startPreview();
            setPreviewFocus(mCamera);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        int rotation = getDisplayOrientation();
        mCamera.setDisplayOrientation(rotation);
        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setRotation(rotation);
        mCamera.setParameters(parameters);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mHolder.removeCallback(this);
        mCamera.setPreviewCallback(null);
        mCamera.stopPreview();
        mCamera.release();
        mCamera = null;
    }

    public int getDisplayOrientation() {
        Display display = ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        int rotation = display.getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_BACK, info);
        int result = (info.orientation - degrees + 360) % 360;
        return result;
    }

    @Override
    public void onPreviewFrame(final byte[] data, final Camera camera) {
        synchronized (lock) {
            //处理data
            Camera.Size previewSize = camera.getParameters().getPreviewSize();
            BitmapFactory.Options newOpts = new BitmapFactory.Options();
            newOpts.inJustDecodeBounds = true;
            YuvImage yuvimage = new YuvImage(
                    data,
                    ImageFormat.NV21,
                    previewSize.width,
                    previewSize.height,
                    null);

            int degree = getDisplayOrientation();
            PlateInfo[] reulsts = V4Edge.getInstance(getContext()).getPlates(data, previewSize.width, previewSize.height, degree, true);
            if (reulsts != null)
                for (PlateInfo result : reulsts) {
                    Log.e(TAG, "onPreviewFrame: " + result.plateNo + "----time: " + System.currentTimeMillis());
                    if (!TextUtils.isEmpty(result.plateNo)) {
                        isStopReg = true;
                        sendPlate(result);
                    }
                }

        }
    }

    private void sendPlate(PlateInfo plate) {
        EventBus.getDefault().post(plate);
    }


    private void openCameraOriginal() {
        try {
            mCamera = Camera.open();
        } catch (Exception e) {
            Log.e(TAG, "camera is not available");
        }
    }

    private class CameraHandlerThread extends HandlerThread {
        Handler handler;

        public CameraHandlerThread(String name) {
            super(name);
            start();
            handler = new Handler(getLooper());
        }

        synchronized void notifyCameraOpened() {
            notify();
        }

        void openCamera() {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    openCameraOriginal();
                    notifyCameraOpened();
                }
            });
            try {
                wait();
            } catch (Exception e) {
                Log.e(TAG, "wait was interrupted");
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getPointerCount() == 2) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_POINTER_DOWN:
                    oldDist = getFingerSpacing(event);
                    break;
                case MotionEvent.ACTION_MOVE:
                    float newDist = getFingerSpacing(event);
                    if (newDist > oldDist) {
                        handleZoom(true, mCamera);
                    } else if (newDist < oldDist) {
                        handleZoom(false, mCamera);
                    }
                    oldDist = newDist;
                    break;
            }
        }
        return true;
    }

    private float getFingerSpacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    private void handleZoom(boolean isZoomIn, Camera camera) {
        Camera.Parameters parameters = camera.getParameters();
        if (parameters.isZoomSupported()) {
            int maxZoom = parameters.getMaxZoom();
            int zoom = parameters.getZoom();
            if (isZoomIn && zoom < maxZoom) {
                zoom++;
            } else if (zoom > 0) {
                zoom--;
            }
            parameters.setZoom(zoom);
            camera.setParameters(parameters);
        } else {
            Log.e(TAG, "handleZoom: " + "the device is not support zoom");
        }
    }

    private void setPreviewFocus(Camera camera) {
        Camera.Parameters parameters = camera.getParameters();
        List<String> focusList = parameters.getSupportedFocusModes();
        if (focusList.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        }
        camera.setParameters(parameters);
    }
}