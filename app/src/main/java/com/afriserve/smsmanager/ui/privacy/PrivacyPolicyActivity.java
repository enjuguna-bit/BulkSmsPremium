package com.afriserve.smsmanager.ui.privacy;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.afriserve.smsmanager.R;
import com.google.android.material.appbar.MaterialToolbar;

public class PrivacyPolicyActivity extends AppCompatActivity {
    private WebView webView;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_privacy_policy);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.settings_privacy_policy));
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        progressBar = findViewById(R.id.progressBar);
        webView = findViewById(R.id.webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(false);
        settings.setDomStorageEnabled(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (progressBar != null) {
                    progressBar.setVisibility(android.view.View.VISIBLE);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (progressBar != null) {
                    progressBar.setVisibility(android.view.View.GONE);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame()) {
                    if (progressBar != null) {
                        progressBar.setVisibility(android.view.View.GONE);
                    }
                    Toast.makeText(PrivacyPolicyActivity.this, "Failed to load privacy policy", Toast.LENGTH_LONG).show();
                }
            }

            @SuppressWarnings("deprecation")
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                if (progressBar != null) {
                    progressBar.setVisibility(android.view.View.GONE);
                }
                Toast.makeText(PrivacyPolicyActivity.this, "Failed to load privacy policy", Toast.LENGTH_LONG).show();
            }
        });

        String url = getString(R.string.settings_privacy_url);
        if (url == null || url.trim().isEmpty()) {
            Toast.makeText(this, "Privacy policy URL not set", Toast.LENGTH_LONG).show();
            return;
        }
        webView.loadUrl(url);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        super.onBackPressed();
    }
}
