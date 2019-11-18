package com.starpanda.activity.myservicebest;

/**
 * @author JiangZongqing
 * @description: 定义一个回调接口
 *                用于下载过程中的各种状态进行监听和回调
 * @date :2019/11/17 17:12
 */
public interface DownLoadListener {
    void onProgress(int progress); //通知当前下载进度

    void onSuccess(); //通知当前下载成功事件

    void onFailed();

    void onPaused();

    void onCanceled();
}
