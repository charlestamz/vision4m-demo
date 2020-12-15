package com.kanbig.facesdk.facedetect;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import com.kanbig.vision4m.FaceQueryResult;
import com.kanbig.vision4m.V4Edge;
import com.kanbig.facesdk.demo.R;
import com.kanbig.vision4m.facedetect.PortraitCameraBridgeViewBase;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class FdActivity extends Activity implements PortraitCameraBridgeViewBase.CvCameraViewListener2 {
    private static final String[] PERMISSION_LIST = {Manifest.permission.CAMERA};
    private static final String PACKAGE_URL_SCHEME = "package:";

    private static final String TAG = "FaceSDK::FdActivity";
    private static final Scalar FACE_RECT_COLOR = new Scalar(0, 255, 0, 255);
    private static final Scalar FRAME_COLOR1 = new Scalar(255, 0, 0);
    private static final Scalar FRAME_COLOR2 = new Scalar(0, 255, 0);
    private Button changeCameraButton;
    private volatile Mat mRgba;
    private V4Edge mV4Edge;

    private PortraitCameraBridgeViewBase mOpenCvCameraView;

    private static final ExecutorService service = new ThreadPoolExecutor(0, 1, 60, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), new ThreadPoolExecutor.DiscardPolicy());

    private Point ltPoint = new Point();
    private Point rbPoint = new Point();
    private Point msgPoint = new Point();

    private StringBuilder msg = new StringBuilder();

    private volatile boolean hasFace;
    private volatile int faceLeft;
    private volatile int faceTop;
    private volatile int faceRight;
    private volatile int faceBottom;

    private Mat bgrMat = new Mat();
    private volatile byte[] tmpBuf;
    private volatile boolean updateScore;
    private volatile float faceScore;
    private volatile int faceIdx;


//    static {
//        OpenCVLoader.initDebug();
//    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {

                    mOpenCvCameraView.enableView();
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    private Runnable command = new Runnable() {
        @Override
        public void run() {
            Imgproc.cvtColor(mRgba, bgrMat, Imgproc.COLOR_RGBA2BGR);
            //faceFront;
//            bgrMat = bgrMat.t();
//            Core.flip(bgrMat, bgrMat, 0);  //x轴
//            Core.flip(bgrMat, bgrMat, 1);
//
//            Imgcodecs.imwrite(Environment.getExternalStorageDirectory() + "/facesdk.jpg", bgrMat);
            //vertical flip
//        Core.flip(mRgba, mRgba, 0);
//        Core.flip(mGray, mGray, 0);
//        Core.rotate(mRgba,mRgba,Core.ROTATE_90_CLOCKWISE);
//        Core.rotate(mGray,mRgba,Core.ROTATE_90_CLOCKWISE);


            if (mV4Edge != null) {
                int width = bgrMat.width();
                int height = bgrMat.height();
                int length = (int) (width * height * bgrMat.elemSize());
                if (tmpBuf == null || tmpBuf.length != length) {
                    tmpBuf = new byte[length];
                }
                bgrMat.get(0, 0, tmpBuf);
                int[] maximumFace = mV4Edge.getMaximumFace(tmpBuf, width, height);
                if (maximumFace != null && maximumFace.length > 3) {
                    float[] fea = mV4Edge.getFeature(tmpBuf, width, height);
                    FaceQueryResult[] results = mV4Edge.queryFeature(fea, 1);
                    if (results != null && results.length > 0) {
                        faceScore = results[0].score;
                        faceIdx = results[0].idx;
                        updateScore = true;
                    } else {
                        updateScore = false;
                    }
                    faceLeft = maximumFace[0];
                    faceTop = maximumFace[1];
                    faceRight = faceLeft + maximumFace[2];
                    faceBottom = faceTop + maximumFace[3];
                    hasFace = true;
                    return;
                }
            }
            updateScore = false;
            hasFace = false;

        }
    };


    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.face_detect_surface_view);
        changeCameraButton = (Button) findViewById(R.id.changeCameraButton);
        mOpenCvCameraView = (PortraitCameraBridgeViewBase) findViewById(R.id.fd_activity_surface_view);
        mOpenCvCameraView.setVisibility(CameraBridgeViewBase.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
        mV4Edge = V4Edge.getInstance(getApplicationContext());
        changeCameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String tag = "onClickChangeCameraButton";

                stopCamera(tag);

                int cameraIndex = mOpenCvCameraView.getCameraIndex();
                switch (cameraIndex) {
                    case PortraitCameraBridgeViewBase.CAMERA_ID_FRONT:
                        mOpenCvCameraView.setCameraIndex(PortraitCameraBridgeViewBase.CAMERA_ID_BACK);
                        break;
                    case PortraitCameraBridgeViewBase.CAMERA_ID_BACK:
                        mOpenCvCameraView.setCameraIndex(PortraitCameraBridgeViewBase.CAMERA_ID_FRONT);
                        break;
                    default:
                        mOpenCvCameraView.setCameraIndex(PortraitCameraBridgeViewBase.CAMERA_ID_BACK);
                        break;
                }

                startCamera(tag);
            }
        });
    }

    protected boolean checkPermission() {
        List<String> permissions = new ArrayList<>();
        for (String permission : PERMISSION_LIST) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                boolean hasRefused = ActivityCompat.shouldShowRequestPermissionRationale(this, permission);
                if (hasRefused) {
                    showMissingPermissionDialog();
                    return false;
                } else {
                    permissions.add(permission);
                }
            }
        }
        if (permissions.size() > 0) {
            ActivityCompat.requestPermissions(this, permissions.toArray(new String[permissions.size()]), 2010);
            return false;
        } else {
            return true;
        }
    }

    // 显示缺失权限提示
    private void showMissingPermissionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.help);
        builder.setMessage(R.string.string_help_text);

        // 拒绝, 退出应用
        builder.setNegativeButton(R.string.quit, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        });

        builder.setPositiveButton(R.string.settings, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                startAppSettings();
                finish();
            }
        });

        builder.setCancelable(false);

        builder.show();
    }

    // 启动应用的设置
    private void startAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse(PACKAGE_URL_SCHEME + getPackageName()));
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 2010) {
            for (int result : grantResults) {
                if (result == PackageManager.PERMISSION_DENIED) {
                    checkPermission();
                    return;
                }
            }
            init();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (checkPermission()) {
            init();
        }
    }

    private void init() {
        if (!OpenCVLoader.initDebug()) {
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        mOpenCvCameraView.disableView();
    }

    private BaseLoaderCallback loaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {

            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                    mOpenCvCameraView.enableView();
                    break;
                default:
                    super.onManagerConnected(status);
            }
        }
    };

    @Override
    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat();
    }

    @Override
    public void onCameraViewStopped() {
        mRgba.release();
    }

    @Override
    public Mat onCameraFrame(PortraitCameraBridgeViewBase.CvCameraViewFrame inputFrame) {

        mRgba = inputFrame.rgba();

        service.execute(command);

        if (hasFace) {
            ltPoint.x = faceLeft;
            ltPoint.y = faceTop;
            rbPoint.x = faceRight;
            rbPoint.y = faceBottom;
            Imgproc.rectangle(mRgba, ltPoint, rbPoint, FACE_RECT_COLOR, 3);
        }

        if (updateScore) {
            msg.delete(0, msg.length());

            msg.append("found, score ");
            msg.append(faceScore);
            msg.append(" idx ");
            msg.append(faceIdx);
            msgPoint.x = faceLeft;
            msgPoint.y = faceTop - 10;
            Imgproc.putText(mRgba, msg.toString(), msgPoint, Core.FONT_HERSHEY_PLAIN, 2.0, faceScore>0.8?FRAME_COLOR1:FRAME_COLOR2);

        }

        return mRgba;
    }


    private void startCamera(String TAG) {
        if (!OpenCVLoader.initDebug()) {
            Log.i(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initiation");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, loaderCallback);
        } else {
            Log.i(TAG, "OpenCV library found inside package. Using it");
            loaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    private void stopCamera(String TAG) {
        Log.i(TAG, "Disabling a camera view");

        if (mOpenCvCameraView != null) {
            mOpenCvCameraView.disableView();
        }
    }
}
