package com.kanbig.facesdk.demo;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.kanbig.vision4m.FaceInfo;
import com.kanbig.vision4m.FaceQueryResult;
import com.kanbig.vision4m.V4Edge;
import com.kanbig.vision4m.NativeImage;
import com.kanbig.facesdk.facedetect.Fd2Activity;
import com.lzy.imagepicker.ImagePicker;
import com.lzy.imagepicker.bean.ImageItem;
import com.lzy.imagepicker.ui.ImageGridActivity;

import org.opencv.android.Utils;
import org.opencv.core.CvException;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;


public class TestActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "FaceSDK::TestActivity";
    static final int IMAGE_PICKER1 = 100;
    static final int IMAGE_PICKER2 = 101;
    static final int IMAGE_PICKER3 = 103;
    static final int IMAGE_PICKER4 = 104;
    private Button getFeature;
    private Button findFace;
    private Button getMaxiumFace;
    private Button live;
    private Button loadModel;
    private Button hasHardhat;
    private ImageView imageView;

    V4Edge v4Edge;
    //读写权限
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE};
    //请求状态码
    private static int REQUEST_PERMISSION_CODE = 1;
    private int id = 10000;


    public static abstract class ImShowMethod {
        void show_gray(final String name, final Mat m) {
            imshow(name, m, Imgproc.COLOR_GRAY2RGBA, 4);
        }

        void show_bgr(final String name, final Mat m) {
            imshow(name, m, Imgproc.COLOR_RGB2BGRA, 0);
        }

        void imshow(final String name, final Mat m, int cvtColor_convert_code) {
            this.imshow(name, m, cvtColor_convert_code, 0);
        }

        void imshow(final String name, final Mat m, int cvtColor_convert_code, int cvtColor_dest_cdn) {

            Mat tmp = new Mat(m.rows(), m.cols(), CvType.CV_8U, new Scalar(4));
            // Imgproc.cvtColor(m, tmp, Imgproc.COLOR_RGB2BGRA);
            // Imgproc.cvtColor(m, tmp, Imgproc.COLOR_GRAY2RGBA, 4);
            Imgproc.resize(m, tmp, new Size(400, 600));
            Imgproc.cvtColor(tmp, tmp, cvtColor_convert_code, cvtColor_dest_cdn);
            imshow(name, tmp);
        }

        abstract void imshow(final String name, final Mat rgba_mat);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_test);
        initView();
        ImagePicker imagePicker = ImagePicker.getInstance();
        imagePicker.setImageLoader(new GlideImageLoader());   //设置图片加载器
        imagePicker.setShowCamera(true);  //显示拍照按钮
        imagePicker.setCrop(false);        //允许裁剪（单选才有效）
//        imagePicker.setSaveRectangle(true); //是否按矩形区域保存
        imagePicker.setMultiMode(false);
        imagePicker.setSelectLimit(1);    //选中数量限制
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, PERMISSIONS_STORAGE, REQUEST_PERMISSION_CODE);
            }
        }
        v4Edge = V4Edge.getInstance(getApplicationContext());
    }

    private void initView() {
        getFeature = (Button) findViewById(R.id.addDbItem);
        findFace = (Button) findViewById(R.id.findFace);
        getMaxiumFace = (Button) findViewById(R.id.getMaxiumFace);
        loadModel = (Button) findViewById(R.id.loadFeatureModel);
        hasHardhat = (Button) findViewById(R.id.hasHardhat);
        imageView = (ImageView) findViewById(R.id.image_1);
        live = (Button) findViewById(R.id.live);
        getFeature.setOnClickListener(this);
        findFace.setOnClickListener(this);
        getMaxiumFace.setOnClickListener(this);
        live.setOnClickListener(this);
        loadModel.setOnClickListener(this);
        hasHardhat.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.loadFeatureModel:
                // 载入服务端相同特征模型

                boolean suc = v4Edge.loadFaceModel("face_feature_128.param", 128);
                Toast toast = Toast.makeText(TestActivity.this, "loadFeatureModel:" + suc, Toast.LENGTH_SHORT);
                toast.show();
                break;
            case R.id.findFace:
                Intent intent3 = new Intent(this, ImageGridActivity.class);
                startActivityForResult(intent3, IMAGE_PICKER3);

                break;
            case R.id.hasHardhat:
                v4Edge.loadHardhatModel();

                Intent intent4 = new Intent(this, ImageGridActivity.class);
                startActivityForResult(intent4, IMAGE_PICKER4);

                break;


            case R.id.addDbItem:
                //file should be the absolut path of the file 要用绝对路径
                Intent intent2 = new Intent(this, ImageGridActivity.class);
                startActivityForResult(intent2, IMAGE_PICKER2);
                break;

            case R.id.live:
                if (v4Edge.getFaceDbSize() == 0) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Facesdk Demo");
                    builder.setMessage("FaceDB was empty, please click addFeature!");
                    builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            // You don't have to do anything here if you just
                            // want it dismissed when clicked
                        }
                    });
                    // Create the AlertDialog object and return it
                    builder.create().show();
                    return;
                }
//                startActivity(new Intent(TestActivity.this, FdActivity.class));
                startActivity(new Intent(TestActivity.this, Fd2Activity.class));
                break;
        }
    }

    public void showImage(String imageUri) {
        Dialog builder = new Dialog(this);
        builder.requestWindowFeature(Window.FEATURE_NO_TITLE);
        builder.getWindow().setBackgroundDrawable(
                new ColorDrawable(android.graphics.Color.TRANSPARENT));
        builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                //nothing;
            }
        });

        ImageView imageView = new ImageView(this);
        imageView.setImageURI(Uri.fromFile(new File(imageUri)));
        builder.addContentView(imageView, new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        builder.show();
    }

    private Bitmap decodeUri(String selectedImage) throws FileNotFoundException, IOException {
        // Decode image size
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;

        File file = new File(selectedImage);
        FileInputStream fileInputStream = new FileInputStream(file);
        return BitmapFactory.decodeFile(selectedImage, o);
    }

    private static Bitmap convertMatToBitMap(Mat input) {
        Bitmap bmp = null;
        Mat rgb = new Mat();
        Imgproc.cvtColor(input, rgb, Imgproc.COLOR_BGR2RGB);

        try {
            bmp = Bitmap.createBitmap(rgb.cols(), rgb.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(rgb, bmp);
        } catch (CvException e) {
            Log.d("Exception", e.getMessage());
        }
        return bmp;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == ImagePicker.RESULT_CODE_ITEMS) {
            if (data != null) {
                if (requestCode == IMAGE_PICKER2) {
                    ArrayList<ImageItem> images = (ArrayList<ImageItem>) data.getSerializableExtra(ImagePicker.EXTRA_RESULT_ITEMS);
                    File imgFile2 = new File(images.get(0).path);
                    //提取人脸特征
                    float []feature = v4Edge.getFeatureFromFile(imgFile2.getAbsolutePath());
                    if (feature != null && feature.length > 0) {
                        int idx = v4Edge.addDbItem(feature, String.valueOf(id), String.format("第%d人", id - 1000));
                        Toast toast = Toast.makeText(TestActivity.this, "Feature1 was added to db. idx=" + idx, Toast.LENGTH_SHORT);
                        toast.show();
                    }
                } else if (requestCode == IMAGE_PICKER3) {
                    ArrayList<ImageItem> images = (ArrayList<ImageItem>) data.getSerializableExtra(ImagePicker.EXTRA_RESULT_ITEMS);
                    File imgFile2 = new File(images.get(0).path);
                    NativeImage nativeImage = v4Edge.readImage(images.get(0).path);

                    v4Edge.saveImage(nativeImage.buf, nativeImage.width, nativeImage.height, Environment.getExternalStorageDirectory() + "/facesdk", "input.jpg");
                    long timeDetectFace = System.currentTimeMillis();


                    FaceInfo faceInfo[] = v4Edge.getFaces(nativeImage.buf, nativeImage.width, nativeImage.height, false);
                    Mat canvas = org.opencv.imgcodecs.Imgcodecs.imread(images.get(0).path);
                    timeDetectFace = System.currentTimeMillis() - timeDetectFace;

                    //Get Results
                    if (faceInfo.length > 0) {
                        int faceNum = faceInfo.length;
//                        infoResult.setText("detect time："+timeDetectFace+"ms,   face number：" + faceNum);
                        Log.i(TAG, "detect time：" + timeDetectFace);
                        Log.i(TAG, "face num：" + faceNum);

                        for (int i = 0; i < faceNum; i++) {
                            if (faceInfo[i].face_quality_score > 0.8) {
                                int left, top, right, bottom;

                                Paint paint = new Paint();
                                left = faceInfo[i].x;
                                top = faceInfo[i].y;
                                right = faceInfo[i].x + faceInfo[i].width;
                                bottom = faceInfo[i].y + faceInfo[i].height;
                                paint.setColor(Color.RED);
                                paint.setStyle(Paint.Style.STROKE);
                                paint.setStrokeWidth(5);
                                //Draw rect
                                org.opencv.imgproc.Imgproc.rectangle(canvas, new Point(left, top), new Point(right, bottom), new Scalar(0, 0, 255), 3);
                            }

                        }
                        imageView.setImageBitmap(convertMatToBitMap(canvas));
                    } else {
//                        infoResult.setText("no face found");
                    }
                } else if (requestCode == IMAGE_PICKER4) {
                    ArrayList<ImageItem> images = (ArrayList<ImageItem>) data.getSerializableExtra(ImagePicker.EXTRA_RESULT_ITEMS);
                    File imgFile2 = new File(images.get(0).path);
                    Mat mat = org.opencv.imgcodecs.Imgcodecs.imread(images.get(0).path);
                    Imgproc.resize(mat, mat, new Size(mat.width() / 3, mat.height() / 3));
                    NativeImage nativeImage = v4Edge.readImage(images.get(0).path);

                    boolean hat = v4Edge.hasHardhat(mat.nativeObj);
//                    faceSDK.saveImage(nativeImage.buf, nativeImage.width, nativeImage.height, Environment.getExternalStorageDirectory() + "/facesdk","input.jpg");
                    int[] rect = v4Edge.getMaximumFace(nativeImage.buf, nativeImage.width, nativeImage.height);
                    if (rect != null) {
                        String str = "";
                        for (int i : rect) str += i + ",";
                        Toast.makeText(this, "人脸位置：[ " + str + " ],安全帽:" + hat, Toast.LENGTH_SHORT).show();
                        //提取人脸特征
//                    fea2 = faceSDK.getFeatureFromFile(imgFile2.getAbsolutePath());
                    } else
                        Toast.makeText(this, "无法找到人脸。", Toast.LENGTH_SHORT).show();

                    Imgcodecs.imwrite("/mnt/sdcard/facesdk/hardhat.png", mat);
                    showImage("/mnt/sdcard/facesdk/hardhat.png");
                }
            } else {
                Toast.makeText(this, "没有数据", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
