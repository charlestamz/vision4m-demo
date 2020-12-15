package com.kanbig.facesdk.demo;

import android.content.Intent;
import android.os.Bundle;
import android.os.Process;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.lzy.imagepicker.ImagePicker;
import com.lzy.imagepicker.bean.ImageItem;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    static final int IMAGE_PICKER1 = 100;
    static final int IMAGE_PICKER2 = 101;
    private Button faceBtn;
    private TextView textView;
    private Button lprBtn;


    private Executor service = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(final Runnable r) {
            return new Thread(new Runnable() {
                @Override
                public void run() {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                    r.run();
                }
            });
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        ImagePicker imagePicker = ImagePicker.getInstance();
        imagePicker.setImageLoader(new GlideImageLoader());   //设置图片加载器
        imagePicker.setShowCamera(true);  //显示拍照按钮
        imagePicker.setCrop(false);        //允许裁剪（单选才有效）
//        imagePicker.setSaveRectangle(true); //是否按矩形区域保存
        imagePicker.setMultiMode(false);
        imagePicker.setSelectLimit(1);    //选中数量限制

    }

    private void initView() {
        faceBtn = (Button) findViewById(R.id.addDbItem);
        textView = (TextView) findViewById(R.id.hint);
        lprBtn = (Button) findViewById(R.id.lprBtn);


        faceBtn.setOnClickListener(this);
        lprBtn.setOnClickListener(this);

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {


            case R.id.addDbItem:
                startActivity(new Intent(MainActivity.this,TestActivity.class));
                break;

            case R.id.lprBtn:
                startActivity(new Intent(MainActivity.this,LprActivity.class));
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
                    final File imgFile1 = new File(images.get(0).path);
                    service.execute(new Runnable() {
                        @Override
                        public void run() {
                            //提取人脸特征
//                            fea1 = V4Edge.getInstance(MainActivity.this).getFeatureFromFile(imgFile1.getAbsolutePath());
                        }
                    });

                } else if (requestCode == IMAGE_PICKER2) {
                    ArrayList<ImageItem> images = (ArrayList<ImageItem>) data.getSerializableExtra(ImagePicker.EXTRA_RESULT_ITEMS);
                    final File imgFile2 = new File(images.get(0).path);
                    service.execute(new Runnable() {
                        @Override
                        public void run() {
                            //提取人脸特征
//                            fea2 = V4Edge.getInstance(MainActivity.this).getFeatureFromFile(imgFile2.getAbsolutePath());
                        }
                    });

                }
            } else {
                Toast.makeText(this, "没有数据", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
