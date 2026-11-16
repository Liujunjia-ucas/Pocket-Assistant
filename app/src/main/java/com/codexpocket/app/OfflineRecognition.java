package com.codexpocket.app;
import android.app.Activity;
/** No model weights or native speech library is distributed in this edition. */
final class OfflineRecognition {
    static boolean available(Activity activity){return false;}
    void start(Activity activity,PhoneRecognition.Listener listener){listener.error("社区版未附带离线语音模型，请在语音输入设置中选择系统服务。",false);}
    void cancel(){}
    static void releaseModel(){}
}
