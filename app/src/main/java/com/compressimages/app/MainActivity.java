package com.compressimages.app;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {

    private WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setDatabaseEnabled(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());

        // This is the bridge that fixes the download problem: the page calls
        // AndroidBridge.saveFile(base64Data, filename, mimeType) directly,
        // and we write the real file here in native code — no blob:/data: URI
        // guessing games that generic WebView wrapper tools fail at.
        webView.addJavascriptInterface(new WebAppInterface(), "AndroidBridge");

        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    private class WebAppInterface {

        @JavascriptInterface
        public void saveFile(String base64Data, String filename, String mimeType) {
            runOnUiThread(() -> {
                try {
                    // Strip the "data:...;base64," prefix if present
                    String pureBase64 = base64Data;
                    int commaIndex = base64Data.indexOf(',');
                    if (base64Data.startsWith("data:") && commaIndex != -1) {
                        pureBase64 = base64Data.substring(commaIndex + 1);
                    }
                    byte[] bytes = Base64.decode(pureBase64, Base64.DEFAULT);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // Android 10+: use MediaStore, no storage permission needed
                        ContentValues values = new ContentValues();
                        values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
                        values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
                        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                        Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                        if (uri != null) {
                            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                                if (out != null) {
                                    out.write(bytes);
                                }
                            }
                            Toast.makeText(MainActivity.this, "تم الحفظ في: التنزيلات/" + filename, Toast.LENGTH_LONG).show();
                        }
                    } else {
                        // Older Android: write directly to the public Downloads folder
                        java.io.File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                        if (!downloadsDir.exists()) downloadsDir.mkdirs();
                        java.io.File outFile = new java.io.File(downloadsDir, filename);
                        try (FileOutputStream fos = new FileOutputStream(outFile)) {
                            fos.write(bytes);
                        }
                        Toast.makeText(MainActivity.this, "تم الحفظ في: التنزيلات/" + filename, Toast.LENGTH_LONG).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "تعذّر الحفظ: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }
    }
}
