package com.kanbig.facesdk.demo;

import android.app.Activity;
import android.net.Uri;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.lzy.imagepicker.loader.ImageLoader;

import java.io.File;


public class GlideImageLoader implements ImageLoader {

    @Override
    public void displayImage(Activity activity, String path, ImageView imageView, int width, int height) {
        RequestOptions options = new RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.ALL);//缓存全尺寸
        Glide.with(activity)//配置上下文
                .setDefaultRequestOptions(options)
                .load(Uri.fromFile(new File(path)))      //设置图片路径(fix #8,文件名包含%符号 无法识别和显示)
                .into(imageView);
    }

    @Override
    public void displayImagePreview(Activity activity, String path, ImageView imageView, int width, int height) {
        RequestOptions options = new RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.ALL);//缓存全尺寸
        Glide.with(activity)//配置上下文
                .setDefaultRequestOptions(options)
                .load(Uri.fromFile(new File(path)))      //设置图片路径(fix #8,文件名包含%符号 无法识别和显示)
                .into(imageView);
    }

    @Override
    public void clearMemoryCache() {
        //这里是清除缓存的方法,根据需要自己实现
    }
}
