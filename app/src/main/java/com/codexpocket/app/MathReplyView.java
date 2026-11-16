package com.codexpocket.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/** Only mathematical replies use a local WebView; all app navigation stays native. */
final class MathReplyView extends WebView {
    private static final String HOST = "codex-pocket.invalid";
    private static final String PAGE = "https://" + HOST + "/renderer/index.html";
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final TextView fallback;
    private final Runnable heightChanged;
    private final boolean dark;
    private final Runnable render = this::flush;
    private String source = "";
    private boolean loaded, destroyed, scheduled, rendered;
    private int revision, pixelHeight;
    private float[][] horizontalRegions = new float[0][];
    boolean protectsHorizontalAt(float x, float y) {
        float density = getResources().getDisplayMetrics().density;
        float cssX = (x + getScrollX()) / density, cssY = (y + getScrollY()) / density;
        for (float[] r : horizontalRegions) if (cssX >= r[0] && cssY >= r[1] && cssX < r[0] + r[2] && cssY < r[1] + r[3]) return true;
        return false;
    }

    static boolean needsMath(String text) { return MathSyntax.containsMath(text); }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    MathReplyView(Context context, TextView fallback, boolean dark, Runnable heightChanged) {
        super(context);
        this.fallback = fallback;
        this.heightChanged = heightChanged;
        this.dark=dark;
        setBackgroundColor(Color.TRANSPARENT);
        setVerticalScrollBarEnabled(false);
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(View.OVER_SCROLL_NEVER);
        setContentDescription("数学公式与回复");
        WebSettings settings = getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        // Every request receives a local response below, including unknown URLs.
        // Keep HTTPS interception available for the bundled assets and fonts.
        settings.setBlockNetworkLoads(false);
        settings.setDomStorageEnabled(false);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setTextZoom(Math.round(100 * getResources().getConfiguration().fontScale));
        addJavascriptInterface(new HeightBridge(), "PocketHeight");
        setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return asset(request.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (PAGE.equals(uri.toString())) return false;
                if (request.hasGesture() && ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))) {
                    try { context.startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) {}
                }
                return true;
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (!PAGE.equals(url) || destroyed) return;
                loaded = true;
                flush();
            }
        });
        // Keep the readable fallback visible until the first correctly sized render.
        setVisibility(View.INVISIBLE);
        loadUrl(PAGE);
    }

    private WebResourceResponse asset(Uri uri) {
        String path = uri.getPath();
        if (!"https".equals(uri.getScheme()) || !HOST.equals(uri.getHost()) || path == null ||
            !path.matches("/renderer/(index\\.html|renderer\\.js|renderer\\.css|katex\\.min\\.js|katex\\.min\\.css|marked\\.umd\\.js|fonts/[A-Za-z0-9_-]+\\.woff2)")) {
            return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
        }
        String mime = path.endsWith(".html") ? "text/html" : path.endsWith(".css") ? "text/css" : path.endsWith(".woff2") ? "font/woff2" : "application/javascript";
        try { return new WebResourceResponse(mime, path.endsWith(".woff2") ? null : "UTF-8", getContext().getAssets().open(path.substring(1))); }
        catch (IOException e) { return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0])); }
    }

    void setSource(String text) {
        source = text;
        fallback.setText(text);
        if (!rendered) fallback.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        if (loaded && !scheduled && !destroyed) {
            scheduled = true;
            ui.postDelayed(render, 120);
        }
    }

    private void flush() {
        scheduled = false;
        if (!loaded || destroyed) return;
        revision++;
        evaluateJavascript("window.setReplyTheme("+dark+");window.renderMessage(" + JSONObject.quote(source) + "," + revision + ");", null);
    }

    boolean hasRendered() { return rendered; }

    private final class HeightBridge {
        @JavascriptInterface public void horizontalRegions(int version, String json) {
            try {
                JSONArray data = new JSONArray(json); float[][] regions = new float[Math.min(200, data.length())][];
                for (int i = 0; i < regions.length; i++) {
                    JSONArray r = data.getJSONArray(i); regions[i] = new float[4];
                    for (int j = 0; j < 4; j++) { double value = r.getDouble(j); if (!Double.isFinite(value) || Math.abs(value) > 200000) return; regions[i][j] = (float)value; }
                }
                ui.post(() -> { if (!destroyed && version == revision) horizontalRegions = regions; });
            } catch (Exception ignored) {}
        }
        @JavascriptInterface public void resize(int version, double cssHeight) {
            if (!Double.isFinite(cssHeight) || cssHeight <= 0 || cssHeight > 200000) return;
            ui.post(() -> {
                if (destroyed || version != revision) return;
                int height = Math.max(1, (int)Math.ceil(cssHeight * getResources().getDisplayMetrics().density));
                if (height != pixelHeight || !rendered) {
                    // Capture the outer ScrollView position before its content grows.
                    if (heightChanged != null) heightChanged.run();
                    pixelHeight = height;
                    ViewGroup.LayoutParams params = getLayoutParams();
                    if (params != null) { params.height = height; setLayoutParams(params); }
                }
                rendered = true;
                fallback.setVisibility(View.GONE);
                setVisibility(source.isEmpty() ? View.GONE : View.VISIBLE);
            });
        }
    }

    @Override public void destroy() {
        if (destroyed) return;
        destroyed = true;
        ui.removeCallbacks(render);
        removeJavascriptInterface("PocketHeight");
        stopLoading();
        super.destroy();
    }
}
