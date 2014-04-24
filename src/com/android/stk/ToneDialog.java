/*
 * Copyright (c) 2009, 2013 The Linux Foundation. All rights reserved.
 * Not a Contribution.
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.stk;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.internal.telephony.cat.TextMessage;
import com.android.internal.telephony.cat.CatLog;

/**
 * Activity used to display tone dialog.
 *
 */
public class ToneDialog extends Activity {
    TextMessage toneMsg = null;
    private int mSlotId = 0;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        initFromIntent(getIntent());
        // Register receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(StkAppService.FINISH_TONE_ACTIVITY_ACTION);
        registerReceiver(mFinishActivityReceiver, filter);

        // remove window title
        View title = findViewById(com.android.internal.R.id.title);
        title.setVisibility(View.GONE);
        // set customized content view
        setContentView(R.layout.stk_tone_dialog);

        TextView tv = (TextView) findViewById(R.id.message);
        ImageView iv = (ImageView) findViewById(R.id.icon);

        // set text and icon
        tv.setText(toneMsg.text);
        if (toneMsg.icon == null) {
            iv.setImageResource(com.android.internal.R.drawable.ic_volume);
        } else {
            iv.setImageBitmap(toneMsg.icon);
        }

    }

    @Override
    protected void onDestroy() {
        // Unregister receiver
        unregisterReceiver(mFinishActivityReceiver);
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
        case KeyEvent.KEYCODE_BACK:
            sendStopTone();
            finish();
            break;
        }
        return false;
    }

    private BroadcastReceiver mFinishActivityReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Intent received from StkAppService to finish ToneDialog activity,
            // after finishing off playing the tone.
            if (intent.getAction().equals(StkAppService.FINISH_TONE_ACTIVITY_ACTION)) {
                CatLog.d(this, "Finishing Tone dialog activity");
                finish();
            }
        }
    };

    private void initFromIntent(Intent intent) {
        if (intent == null) {
            finish();
        }
        toneMsg = intent.getParcelableExtra("TEXT");
        mSlotId = intent.getIntExtra(StkAppService.SLOT_ID, 0);
    }

    // Send stop playing tone to StkAppService, when user presses back key.
    private void sendStopTone() {
        Bundle args = new Bundle();
        args.putInt(StkAppService.OPCODE, StkAppService.OP_STOP_TONE_USER);
        args.putInt(StkAppService.SLOT_ID, mSlotId);
        startService(new Intent(this, StkAppService.class).putExtras(args));
    }
}
