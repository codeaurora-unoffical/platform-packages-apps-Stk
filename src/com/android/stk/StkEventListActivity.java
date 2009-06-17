/*
 * Copyright (C) 2007 The Android Open Source Project
 * Copyright (C) 2009, Code Aurora Forum. All rights reserved
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

import com.android.internal.telephony.gsm.stk.SetEventList;
import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

public class StkEventListActivity extends Activity {

   private static int mEventValue;
   private static int addedInfo;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        initFromIntent(getIntent());
       sendResponse(StkAppService.RES_ID_EVENT_LIST);
    }

    private void sendResponse(int resId) {
        Bundle args = new Bundle();
        args.putInt(StkAppService.OPCODE, StkAppService.OP_RESPONSE);
        args.putInt(StkAppService.RES_ID, resId);
        args.putInt(StkAppService.EVENT,mEventValue);
        args.putInt(StkAppService.EVENT_CAUSE,addedInfo);
        startService(new Intent(this, StkAppService.class).putExtras(args));
    }

    private void initFromIntent(Intent intent) {
        if (intent != null) {
           mEventValue = intent.getIntExtra("EVENT",0);
           SetEventList event = SetEventList.fromInt(mEventValue);
           switch(event){
              case BROWSER_TERMINATION_EVENT:
                 addedInfo = SetEventList.USER_TERMINATION.value();
                 break;
           }
        } else {
            finish();
        }
    }
}
