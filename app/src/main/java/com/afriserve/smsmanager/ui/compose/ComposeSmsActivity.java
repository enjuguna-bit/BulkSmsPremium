package com.afriserve.smsmanager.ui.compose;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.afriserve.smsmanager.MainActivity;
import com.afriserve.smsmanager.ui.conversation.ConversationActivity;

/**
 * Lightweight SMS compose entrypoint for external intents (sms:, smsto:, mms:, mmsto:).
 * Redirects to ConversationActivity and pre-fills drafts when possible.
 */
public class ComposeSmsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleIntent(getIntent());
        finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
        finish();
    }

    private void handleIntent(Intent intent) {
        if (intent == null) {
            openMain();
            return;
        }

        String address = extractAddress(intent);
        String body = extractBody(intent);

        if (address != null && !address.trim().isEmpty()) {
            address = address.trim();
            if (body != null && !body.trim().isEmpty()) {
                saveDraft(address, body.trim());
            }
            Intent convo = new Intent(this, ConversationActivity.class);
            convo.putExtra("address", address);
            convo.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(convo);
        } else {
            openMain();
        }
    }

    private void openMain() {
        Intent main = new Intent(this, MainActivity.class);
        main.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(main);
    }

    private void saveDraft(String address, String body) {
        SharedPreferences prefs = getSharedPreferences("sms_drafts", MODE_PRIVATE);
        prefs.edit().putString("draft_" + address, body).apply();
    }

    private String extractAddress(Intent intent) {
        String address = intent.getStringExtra("address");
        if (address == null) {
            address = intent.getStringExtra("phone_number");
        }
        if (address == null) {
            Uri data = intent.getData();
            if (data != null) {
                String scheme = data.getScheme();
                if (scheme != null && (scheme.equals("sms") || scheme.equals("smsto")
                        || scheme.equals("mms") || scheme.equals("mmsto"))) {
                    String schemePart = data.getSchemeSpecificPart();
                    if (schemePart != null) {
                        int q = schemePart.indexOf('?');
                        if (q >= 0) {
                            schemePart = schemePart.substring(0, q);
                        }
                        if (schemePart.contains(";")) {
                            schemePart = schemePart.split(";")[0];
                        }
                        if (schemePart.contains(",")) {
                            schemePart = schemePart.split(",")[0];
                        }
                        address = schemePart;
                    }
                }
            }
        }
        return address;
    }

    private String extractBody(Intent intent) {
        String body = intent.getStringExtra("sms_body");
        if (body == null) {
            body = intent.getStringExtra(Intent.EXTRA_TEXT);
        }
        if (body == null) {
            Uri data = intent.getData();
            if (data != null) {
                String param = data.getQueryParameter("body");
                if (param != null) {
                    body = param;
                }
            }
        }
        return body;
    }
}
