package com.kanbig.facesdk.facedetect;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import com.kanbig.facesdk.demo.R;
import com.kanbig.vision4m.FaceQueryResult;
import com.kanbig.vision4m.V4Edge;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class Fd2Activity extends Activity implements JavaCameraView.CameraFrameListener {
    private static final String[] PERMISSION_LIST = {Manifest.permission.CAMERA};
    private static final String PACKAGE_URL_SCHEME = "package:";

    private static final Scalar FACE_RECT_COLOR = new Scalar(0, 255, 0, 255);
    private static final Scalar FAKE_COLOR = new Scalar(234, 217, 253);
    private static final Scalar FRAME_COLOR = new Scalar(255, 0, 0);

    private Button changeCameraButton;
    private volatile Mat mMat4Detection;
    private V4Edge mV4Edge;
    private final ReentrantLock mMat4DetectionLock = new ReentrantLock();
    private final ReentrantLock mMat4FaceMatLock = new ReentrantLock();
    private JavaCameraView mOpenCvCameraView;

    private static final ExecutorService service = new ThreadPoolExecutor(0, 1, 60, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), new ThreadFactory() {
        @Override
        public Thread newThread(final Runnable r) {
            return new Thread(new Runnable() {
                @Override
                public void run() {
                    Process.setThreadPriority(-5/*Process.THREAD_PRIORITY_VIDEO*/);
                    r.run();
                }
            });
        }
    }, new ThreadPoolExecutor.DiscardPolicy());
    private Point ltPoint = new Point();
    private Point rbPoint = new Point();
    private Point msgPoint = new Point();

    private StringBuilder msg = new StringBuilder();

    private volatile boolean hasFace;
    private volatile int faceLeft;
    private volatile int faceTop;
    private volatile int faceRight;
    private volatile int faceBottom;

    //    private volatile Mat mSqueezedMat = new Mat();
    private volatile boolean updateScore;
    private volatile float faceScore;
    private volatile int faceIdx;
    private volatile int[] cachedFaceRect = new int[4];
    private volatile float[] cached_feature = new float[128];

    private Mat mSqueezedMat;
    private Mat faceMat;
    private Size mFaceSize = new Size();
    private Rect mFaceRect = new Rect();
    private boolean realFace = false;
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
    private Runnable search = new Runnable() {

        @Override
        public void run() {
            boolean getfeature_suc = false;
            if (!faceMat.empty())
                if (mMat4FaceMatLock.tryLock()) {
                    try {
                        getfeature_suc = mV4Edge.getFeature2(faceMat, cached_feature);
                    } finally {
                        mMat4FaceMatLock.unlock();
                    }
                }
            if (getfeature_suc) {
                FaceQueryResult[] results = mV4Edge.queryFeature(cached_feature, 1);
                if (results != null && results.length > 0) {
                    faceScore = results[0].score;
                    faceIdx = results[0].idx;
                    updateScore = true;
                } else {
                    updateScore = false;
                }
            } else {
                updateScore = false;
            }

        }
    };
    private Runnable command = new Runnable() {

        @Override
        public void run() {
            if (mV4Edge != null && !mMat4Detection.empty()) {
                if (mMat4DetectionLock.tryLock()) {
                    try {

//                    Imgcodecs.imwrite(Environment.getExternalStorageDirectory() + "/facesdk.jpg", mMat4Detection);
                        int width = mMat4Detection.cols();
                        int height = mMat4Detection.rows();
                        mFaceSize.width = width >>> 1;
                        mFaceSize.height = height >>> 1;
                        Imgproc.resize(mMat4Detection, mSqueezedMat, mFaceSize);
                        hasFace = mV4Edge.getMaximumFace2(mSqueezedMat, cachedFaceRect);
                        if (hasFace) {
                            mFaceRect.x = cachedFaceRect[0] << 1;
                            mFaceRect.y = cachedFaceRect[1] << 1;
                            mFaceRect.width = cachedFaceRect[2] << 1;
                            mFaceRect.height = cachedFaceRect[3] << 1;

                            faceLeft = mFaceRect.x;
                            faceTop = mFaceRect.y;
                            faceRight = mFaceRect.x + mFaceRect.width;
                            faceBottom = mFaceRect.y + mFaceRect.height;
                            mMat4Detection.submat(mFaceRect).copyTo(faceMat);
                        }
                    } finally {
                        mMat4DetectionLock.unlock();
                    }
                }
                if (hasFace) {
                    int []rect =new int[]{mFaceRect.x,mFaceRect.y, mFaceRect.width,mFaceRect.height};
                    realFace = mV4Edge.testLiveness(mMat4Detection, rect);
                    boolean getfeature_suc = false;
                    if (mMat4FaceMatLock.tryLock())
                        try {
                            if (!faceMat.empty()) {
                                getfeature_suc = mV4Edge.getFeature2(faceMat, cached_feature);
                            }
                        } finally {
                            mMat4FaceMatLock.unlock();
                        }

                    if (getfeature_suc) {
                        FaceQueryResult[] results = mV4Edge.queryFeature(cached_feature, 1);
                        if (results != null && results.length > 0) {
                            faceScore = results[0].score;
                            faceIdx = results[0].idx;
                            updateScore = true;
                        } else {
                            updateScore = false;
                        }
                    } else {
                        updateScore = false;
                    }
                    return;
                }
            }
            hasFace = false;
            updateScore = false;
        }
    };
    private static int mScreenMode = 0;
    private static final int[] SCREEN_MOODS = {ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE};
    private static int mCurrentRotation = 0;


    /**
     * Called when the activity is first created.
     */
    @SuppressLint("WrongConstant")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRequestedOrientation(SCREEN_MOODS[mScreenMode]);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.face_detect_surface_2_view);
        changeCameraButton = (Button) findViewById(R.id.changeCameraButton);
        mOpenCvCameraView = (JavaCameraView) findViewById(R.id.fd_activity_surface_view);
        mOpenCvCameraView.setVisibility(CameraBridgeViewBase.VISIBLE);
        mOpenCvCameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_FRONT);
        mOpenCvCameraView.setCameraFrameListener(this);
        mV4Edge = V4Edge.getInstance(getApplicationContext());
        mMat4Detection = new Mat();
        mSqueezedMat = new Mat();
        faceMat = new Mat();
        changeCameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hasFace = false;
                mOpenCvCameraView.toggle();
            }
        });

        findViewById(R.id.changeScreenOrientation).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mScreenMode = (mScreenMode + 1) & 3;
                setRequestedOrientation(SCREEN_MOODS[mScreenMode]);
            }
        });
        findViewById(R.id.rotate).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCurrentRotation = (mCurrentRotation + 1) & 3;
                mOpenCvCameraView.rotate(mCurrentRotation);
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

        if (mMat4Detection != null) {
            try {
                mMat4DetectionLock.lock();
                mMat4Detection.release();
                mMat4Detection = null;
            } catch (Throwable t) {
            } finally {
                mMat4DetectionLock.unlock();
            }

        }
        if (mSqueezedMat != null) {
            mSqueezedMat.release();
            mSqueezedMat = null;
        }
        if (faceMat != null) {
            try {
                mMat4FaceMatLock.lock();
                faceMat.release();
                faceMat = null;
            } catch (Throwable t) {
            } finally {
                mMat4FaceMatLock.unlock();
            }
        }

    }


    public void onCameraViewStarted(int width, int height) {

    }

    public void onCameraViewStopped() {

    }

    @Override
    public Mat onCameraFrame(Mat frameMat) {
        if (mMat4DetectionLock.tryLock()) {
            try {
                if (mMat4Detection == null) {
                    return frameMat;
                }
                frameMat.copyTo(mMat4Detection);
            } finally {
                mMat4DetectionLock.unlock();
            }
            service.execute(command);
        }
        if (hasFace) {
            ltPoint.x = faceLeft;
            ltPoint.y = faceTop;
            rbPoint.x = faceRight;
            rbPoint.y = faceBottom;
            msgPoint.x = faceLeft;
            msgPoint.y = faceTop - 10;
            drawRoundRect(frameMat, new Rect(ltPoint, rbPoint), realFace ? FACE_RECT_COLOR : FAKE_COLOR, 3);
            if (!realFace) {
                Imgproc.putText(frameMat, "Fake", msgPoint, Core.FONT_HERSHEY_PLAIN, 6.18, FAKE_COLOR);
            } else if (updateScore) {
                msg.delete(0, msg.length());
                if (faceScore > 0.6) {
                    msg.append("found:");
                    msg.append(faceScore);
                    msg.append(" idx:");
                    msg.append(faceIdx);
                } else {
                    msg.append("failed:");
                    msg.append(faceScore);
                }
                Imgproc.putText(frameMat, msg.toString(), msgPoint, Core.FONT_HERSHEY_PLAIN, 6.18, FACE_RECT_COLOR);


            }
        }


        return frameMat;
    }

    private void drawBorder(Mat img, Point pt1, Point pt2, Scalar color, int thickness, int r, int d) {
        int x1 = (int) pt1.x;
        int y1 = (int) pt1.y;
        int x2 = (int) pt2.x;
        int y2 = (int) pt2.y;

        // #Top left
        Imgproc.line(img, new Point(x1 + r, y1), new Point(x1 + r + d, y1), color, thickness);
        Imgproc.line(img, new Point(x1, y1 + r), new Point(x1, y1 + r + d), color, thickness);
        Imgproc.ellipse(img, new Point(x1 + r, y1 + r), new Size(r, r), 180, 0, 90, color,
                thickness);

        // #Top right

        Imgproc.line(img, new Point(x2 - r, y1), new Point(x2 - r - d, y1), color, thickness);
        Imgproc.line(img, new Point(x2, y1 + r), new Point(x2, y1 + r + d), color, thickness);
        Imgproc.ellipse(img, new Point(x2 - r, y1 + r), new Size(r, r), 270, 0, 90, color, thickness);

        // #Bottom left

        Imgproc.line(img, new Point(x1 + r, y2), new Point(x1 + r + d, y2), color, thickness);
        Imgproc.line(img, new Point(x1, y2 - r), new Point(x1, y2 - r - d), color, thickness);
        Imgproc.ellipse(img, new Point(x1 + r, y2 - r), new Size(r, r), 90, 0, 90, color, thickness);

        // #Bottom right

        Imgproc.line(img, new Point(x2 - r, y2), new Point(x2 - r - d, y2), color, thickness);
        Imgproc.line(img, new Point(x2, y2 - r), new Point(x2, y2 - r - d), color, thickness);
        Imgproc.ellipse(img, new Point(x2 - r, y2 - r), new Size(r, r), 0, 0, 90, color, thickness);
    }

    private void drawRoundRect(Mat img, Rect rect, Scalar color, int thickness) {
        int d = (int) ((Math.min(rect.width, rect.height)) / (3.0f * 1.618f));
        int r = d / 3;
        drawBorder(img, new Point(rect.x, rect.y), new Point(rect.x + rect.width, rect.y + rect.height),
                color, thickness, r, d);
    }
}
