/*
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
import android.content.Intent;
import android.os.Bundle;

/**
 * Launcher class. Serve as the app's MAIN activity, send an intent to the
 * StkAppService and finish.
 *
 */
public class UsatLauncherActivity extends Activity {
    private static int mSlotId = 0;
    static final int CARD_APP_TYPE_USIM=2;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = new Bundle();
        args.putInt(StkAppService.OPCODE, StkAppService.OP_LAUNCH_APP);
        args.putInt(StkAppService.CARD_TYPE,CARD_APP_TYPE_USIM);
        args.putInt(StkAppService.SLOT_ID,mSlotId);
        startService(new Intent(this, StkAppService.class).putExtras(args));
    }
    @Override
    public void onPause() {
        super.onPause();
       finish();
    }
}
