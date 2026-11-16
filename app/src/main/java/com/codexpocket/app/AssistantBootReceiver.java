package com.codexpocket.app;

import android.content.*;

public final class AssistantBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent){if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())||Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())){AssistantSync.schedule(c);AssistantSync.soon(c);AssistantBackgroundService.start(c);}}
}
