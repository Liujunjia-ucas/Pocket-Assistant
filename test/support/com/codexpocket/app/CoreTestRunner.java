package com.codexpocket.app;
/** JVM-only storage initialization, never included in the Android application. */
public final class CoreTestRunner {
    public static void main(String[] args)throws Exception{
        FileVault.initialize(new java.io.File(System.getProperty("java.io.tmpdir")));
        try{Class.forName("com.codexpocket.app."+args[0]).getMethod("main",String[].class).invoke(null,(Object)new String[0]);}
        catch(java.lang.reflect.InvocationTargetException e){Throwable cause=e.getCause();if(cause instanceof Exception)throw (Exception)cause;if(cause instanceof Error)throw (Error)cause;throw e;}
    }
}
