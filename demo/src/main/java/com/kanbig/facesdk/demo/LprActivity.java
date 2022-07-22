package com.kanbig.facesdk.demo;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.kanbig.vision4m.PlateInfo;
import com.kanbig.vision4m.V4Edge;
import com.lzy.imagepicker.ImagePicker;
import com.lzy.imagepicker.bean.ImageItem;
import com.lzy.imagepicker.ui.ImageGridActivity;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class LprActivity extends Activity implements View.OnClickListener {

    FrameLayout previewFl;
    LprCameraPreview cameraPreview;
    TextView plateTv;
    TextView regTv;
    ImageView image;
    static final int IMAGE_PICKER1 = 100;
    TableLayout tableLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_lpr);

        previewFl = findViewById(R.id.preview_fl);
        plateTv = findViewById(R.id.plate_tv);
        tableLayout = findViewById(R.id.lpr_table);
        image = findViewById(R.id.image);
        /**
         * 载入车牌识别需要的模型和参数
         * @param modelPath
         * @param detectorThresh 建议0.1 检测参数
         * @param iouThresh      建议0.4 IOU去重值
         * @param lprThresh      建议0.8 车牌识别阈值，越高越准确
         * @return true if the model is ok
         */
        V4Edge.getInstance(getApplicationContext()).loadLprModel("", 0.10f, 0.4f, 0.96f);
        /**
         * 初始化车辆计数参数括计数线，方向
         * 计数线
         * |
         * ————————————> 方向向量
         * |
         * |
         * |
         * 注意：当不设置计数线时，车辆计数将不会计算是否经过计数线，只计算是否和direct参数运动方向一致
         * @param line_start_x      计数线start点 x
         * @param line_start_y      计数线start点 y
         * @param line_end_x        计数线end点 x
         * @param line_end_y        计数线end点 y
         * @param dir_0             方向向量 [0]
         * @param dir_1             方向向量 [1]
         * @param max_recog_time    车牌识别次数
         * @param good_track_thresh 追踪阈值 建议 0.4
         * @param new_track_thresh  新track建立值 建议 0.1
         * @param match_thresh      匹配track值 建议 0.4
         */
        V4Edge.getInstance(getApplicationContext()).initVehicleCounter(200, 0, 0, 0, 1, 0, 3, 0.3f, 0.4f, 0.1f, 0.4f);
    }

    private void initCamera() {


    }

    public void showImage(Bitmap bitmap) {
        Dialog builder = new Dialog(this);
        builder.requestWindowFeature(Window.FEATURE_NO_TITLE);
        builder.getWindow().setBackgroundDrawable(
                new ColorDrawable(Color.TRANSPARENT));
        builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                //nothing;
            }
        });

        ImageView imageView = new ImageView(this);
        imageView.setImageBitmap(bitmap);
        builder.addContentView(imageView, new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        builder.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cameraPreview == null) {
            initCamera();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        cameraPreview = null;
    }

    private void stopPreview() {
        previewFl.removeAllViews();
    }

    @Override
    protected void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(PlateInfo plate) {
        plateTv.append("\n"+plate.plateNo);
//        stopPreview();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {

            case R.id.btn_manual:
                Intent intent1 = new Intent(this, ImageGridActivity.class);
                startActivityForResult(intent1, IMAGE_PICKER1);
                break;

            case R.id.btn_opencam:
                cameraPreview = new LprCameraPreview(this);
                previewFl.addView(cameraPreview);
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == ImagePicker.RESULT_CODE_ITEMS) {
            if (data != null) {
                if (requestCode == IMAGE_PICKER1) {
                    ArrayList<ImageItem> images = (ArrayList<ImageItem>) data.getSerializableExtra(ImagePicker.EXTRA_RESULT_ITEMS);
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inMutable = true;
                    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    Bitmap bitmap = BitmapFactory.decodeFile(images.get(0).path, options);
                    PlateInfo[] plateInfos = V4Edge.getInstance(getApplicationContext()).getPlates(bitmap);
                    String s = "";
                    Canvas cnvs = new Canvas(bitmap);
                    Paint paint = new Paint();
                    paint.setColor(Color.RED);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(2);
                    if (plateInfos != null)
                        for (PlateInfo p : plateInfos) {
                            s += (p.toString() + "\n");
                            cnvs.drawRect(p.x, p.y, p.x + p.width, p.y + p.height, paint);
                        }
                    plateTv.setText(s);
                    showImage(bitmap);
                }
            } else {
                Toast.makeText(this, "没有数据", Toast.LENGTH_SHORT).show();
            }
        }
    }
}