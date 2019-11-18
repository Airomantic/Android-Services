package com.starpanda.activity.myservicebest;

import android.os.AsyncTask;
import android.os.Environment;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * @author JiangZongqing
 * @description: 编写下载功能
 * @date :2019/11/17 17:16
 */
public class DownloadTask extends AsyncTask<String,Integer,Integer> {
    //用于switch语句case
    public static final int TYPE_SUCCESS = 0;
    public static final int TYPE_FAILED = 1;
    public static final int TYPE_PAUSED = 2;
    public static final int TYPE_CANCELED = 3;

    private DownLoadListener Listener;
    private boolean isCanceled = false;
    private boolean isPaused = false;
    private int lastProgress;

    public DownloadTask(DownLoadListener listener) {
        this.Listener = listener;//来自 private DownLoadListener Listener;
    }
        /**
          * 重写
         * doInBackground()，用于在后台执行具体的下载逻辑
         *     onProgressUpdate()，在界面上更新当前的下载进度
         *     onPostExecute()，通知最终的下载结果
          **/
    @Override
    protected Integer doInBackground(String... params) {
        InputStream is = null;
        RandomAccessFile savedFile = null;
        File file = null;

        long downloadedLength = 0;//记录已下载的文件长度
        String downloadUrl = params[0]; //来自Integer doInBackground(String... params)，从参数中获取到了下载的URL地址
        //并根据URL地址解析出了下载的文件名
        String fileName = downloadUrl.substring(downloadUrl.lastIndexOf("/"));//末尾"/"
        //指定将文件下载到Environment.DIRECTORY_DOWNLOADS目录下，也就是SD卡的Download目录
        String directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath();
        file = new File(directory + fileName);//文件由系统目录的路径和文件名组成
        if (file.exists()) {
            //如果已经存在要下载的文件，则读取已下载的字节数，这样就可以在后面启用断点续传的功能
            downloadedLength = file.length();
        }
        try {
            long contentLength = getContentLength(downloadUrl);//调用getContentLength()方法，来获取文件内容总长度（需抛出异常检测）
            if (contentLength == 0) {
                return TYPE_FAILED;
            } else if (contentLength == downloadedLength) {
                //已下载字节和文件总字节相等，说明已经下载完成了
                return TYPE_SUCCESS;
            }
            OkHttpClient client = new OkHttpClient();
            //发送网络请求
            Request request = new Request.Builder()
                    //断点下载，指定从哪个字节开始下载
                    .addHeader("Range", "bytes=" + downloadedLength + "-")
                    //添加header，用于告诉服务器我们想要从那个字节开始下载（已下载部分就不用下载了）
                    .url(downloadUrl)
                    .build();
            //服务器响应的数据，直到文件全部下载完
            Response response = client.newCall(request).execute();
            if (response != null) {
                //并使用Java的文件流方式，
                is = response.body().byteStream();
                savedFile = new RandomAccessFile(file, "rw");
                savedFile.seek(downloadedLength);//跳过已下载的字节
                byte[] b = new byte[1024];
                int total = 0;
                int len;
                //不断从网络上读取数据，
                while ((len = is.read(b)) != -1) {
                    if (isCanceled) {
                        return TYPE_CANCELED;
                    } else if (isPaused) {
                        return TYPE_PAUSED;
                    } else {
                        total += len;
                        //不断写入本地，
                        savedFile.write(b,0,len);
                        //计算已下载的百分比
                        int progress = (int) ((total + downloadedLength) * 100 / contentLength);
                        publishProgress(progress);//自带方法，用于发布一个或多个进度单位(units of progress)
                    }
                }
                response.body().close();
                return TYPE_SUCCESS;
            }

        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            try {
                if (is != null) {
                    is.close();
                }
                if (savedFile != null) {
                    savedFile.close();
                }
                if (isCanceled && file != null) {
                    file.delete();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return TYPE_FAILED;
    }
    private long getContentLength(String downloadUrl) throws IOException {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder() //请求协议格式，最后.build()结尾
                .url(downloadUrl)
                .build();
        Response response = client.newCall(request).execute();//抛出IO异常检测
        if (response != null && response.isSuccessful()) {
            long contentLength = response.body().contentLength();//获取文件内容长度
            response.body().close();
            return contentLength;
        }
        return 0;
    }

    //从参数中获取到当前的下载进度，然后和上一次的下载进度进行对比
    @Override
    protected void onProgressUpdate(Integer... values) {
        int progress = values[0];
        //如果有变化，则调用DownloadListener的onProgress()方法来通知下载进度更新
        if (progress > lastProgress) {
            Listener.onProgress(progress);
            lastProgress = progress;
        }
    }

    //根据参数中传入的下载状态来进行回调
    @Override
    protected void onPostExecute(Integer status) {
        switch (status) {
            //下载成功就回调接口DownLoadListener()的onSuccess()方法
            case TYPE_SUCCESS:
                Listener.onSuccess();
                break;
            case TYPE_FAILED:
                Listener.onFailed();
                break;
            case TYPE_PAUSED:
                Listener.onPaused();
                break;
            case TYPE_CANCELED:
                Listener.onCanceled();
                break;
                default:
                    break;
        }
    }
//供DownloadService()调用
    public void pauseDownload() {
        isPaused = true;
    }

    public void cancelDownload() {
        isCanceled = true;
    }
}
