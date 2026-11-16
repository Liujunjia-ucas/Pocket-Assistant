package com.codexpocket.app;

import android.app.job.*;
import android.os.*;

public final class AssistantSyncJob extends JobService {
    private final java.util.Set<JobParameters> owned=new java.util.HashSet<>();
    @Override public boolean onStartJob(JobParameters params){boolean owner=AssistantSync.runAsync(getApplicationContext(),()->new Handler(Looper.getMainLooper()).post(()->{owned.remove(params);jobFinished(params,false);}),false);if(owner)owned.add(params);return true;}
    @Override public boolean onStopJob(JobParameters params){if(owned.remove(params))AssistantSync.cancel();return AssistantSync.enabled(this);}
}
