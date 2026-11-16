package com.codexpocket.app;

/** Initialize private encryption for activities and background services alike. */
public final class CommunityApplication extends android.app.Application {
    @Override public void onCreate(){
        super.onCreate();
        try{FileVault.initialize(getFilesDir());}
        catch(java.io.IOException e){throw new IllegalStateException("Private storage unavailable",e);}
        android.webkit.WebView.setWebContentsDebuggingEnabled(false);
    }
}
