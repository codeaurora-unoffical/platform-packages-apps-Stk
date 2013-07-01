/*
 * Copyright (C) 2007 The Android Open Source Project
 * Copyright (c) 2009, 2012-2013 The Linux Foundation. All rights reserved.
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

import com.android.internal.telephony.cat.AppInterface;
import com.android.internal.telephony.uicc.IccRefreshResponse;
import com.android.internal.telephony.IccCardConstants;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import static com.android.internal.telephony.cat.CatCmdMessage.SetupEventListConstants.*;
import static com.android.internal.telephony.cat.CatCmdMessage.BrowserTerminationCauses.*;
import com.android.internal.telephony.cat.CatLog;

/**
 * Receiver class to get STK intents, broadcasted by telephony layer.
 *
 */
public class StkCmdReceiver extends BroadcastReceiver {
    private static int SLOT0=0;
    private static int SLOT1=1;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (action.equals(AppInterface.CAT_CMD_ACTION)) {
            handleCommandMessage(context, intent);
        } else if (action.equals(AppInterface.CAT_SESSION_END_ACTION)) {
            handleSessionEnd(context, intent);
        } else if (action.equals(AppInterface.CAT_IDLE_SCREEN_ACTION)) {
            handleScreenStatus(context, intent.getBooleanExtra("SCREEN_IDLE",true));
        } else if (action.equals(Intent.ACTION_LOCALE_CHANGED)) {
            handleLocaleChange(context);
        } else if (action.equals(AppInterface.CAT_ICC_STATUS_CHANGE)) {
            handleCardStatusChange(context, intent);
        } else if (action.equals(AppInterface.CAT_ALPHA_NOTIFY_ACTION)) {
            handleAlphaNotify(context, intent);
        } else if (action.equals(AppInterface.CAT_ICC_STATE_CHANGED0)) {
            handleIccStateChanged(context, intent, SLOT0);
        } else if (action.equals(AppInterface.CAT_ICC_STATE_CHANGED1)) {
            handleIccStateChanged(context, intent,SLOT1);
        }
    }

    private void handleCommandMessage(Context context, Intent intent) {
        Bundle args = new Bundle();
        args.putInt(StkAppService.OPCODE, StkAppService.OP_CMD);
        args.putParcelable(StkAppService.CMD_MSG, intent
                .getParcelableExtra("STK CMD"));
        args.putInt(StkAppService.SLOT_ID, intent
                .getIntExtra("SLOT_ID",0));
        args.putInt(StkAppService.CARD_TYPE, intent
                .getIntExtra("CARD_TYPE",0));
        CatLog.d(this, "enter handleCommandMessage");
        context.startService(new Intent(context, StkAppService.class)
                .putExtras(args));
    }

    private void handleSessionEnd(Context context, Intent intent) {
        Bundle args = new Bundle();
        args.putInt(StkAppService.OPCODE, StkAppService.OP_END_SESSION);
        args.putInt(StkAppService.SLOT_ID, intent
                .getIntExtra("SLOT_ID",0));
        args.putInt(StkAppService.CARD_TYPE, intent
                .getIntExtra("CARD_TYPE",0));
        CatLog.d(this, "enter handleSessionEnd");
        context.startService(new Intent(context, StkAppService.class)
                .putExtras(args));
    }

    private void handleScreenStatus(Context context, boolean mScreenIdle) {
        Bundle args = new Bundle();
        args.putInt(StkAppService.OPCODE, StkAppService.OP_IDLE_SCREEN);
        args.putBoolean(StkAppService.SCREEN_STATUS,  mScreenIdle);
        context.startService(new Intent(context, StkAppService.class)
                .putExtras(args));
    }

    private void handleLocaleChange(Context context) {
        Bundle args = new Bundle();
        args.putInt(StkAppService.OPCODE, StkAppService.OP_LOCALE_CHANGED);
        context.startService(new Intent(context, StkAppService.class)
                .putExtras(args));
    }

    private void handleCardStatusChange(Context context, Intent intent) {
        // If the Card is absent then check if the StkAppService is even
        // running before starting it to stop it right away
        if ((intent.getBooleanExtra(AppInterface.CARD_STATUS, false) == false)
                && StkAppService.getInstance() == null) {
            return;
        }
        Bundle args = new Bundle();
        args.putInt(StkAppService.OPCODE, StkAppService.OP_CARD_STATUS_CHANGED);
        args.putBoolean(AppInterface.CARD_STATUS,
                intent.getBooleanExtra(AppInterface.CARD_STATUS,true));
        args.putInt(AppInterface.REFRESH_RESULT, intent
                .getIntExtra(AppInterface.REFRESH_RESULT,
                IccRefreshResponse.REFRESH_RESULT_FILE_UPDATE));
        args.putInt(StkAppService.SLOT_ID, intent
                .getIntExtra("SLOT_ID", 0));
        context.startService(new Intent(context, StkAppService.class)
                .putExtras(args));
    }

    private void handleAlphaNotify(Context context, Intent intent) {
        Bundle args = new Bundle();
        String alphaString = intent.getStringExtra(AppInterface.ALPHA_STRING);
        args.putInt(StkAppService.OPCODE, StkAppService.OP_ALPHA_NOTIFY);
        args.putString(AppInterface.ALPHA_STRING, alphaString);
        context.startService(new Intent(context, StkAppService.class)
                .putExtras(args));
    }
    

    private void handleIccStateChanged(Context context, Intent intent,int slotid) {
        CatLog.d(this, "enter handleIccStateChanged");
        if (StkAppService.getInstance() == null) {
            CatLog.d(this, "enter handleIccStateChanged, StkAppService.getInstance() is null");
            return;
        }
        Bundle args = new Bundle();
        args.putInt(StkAppService.OPCODE, StkAppService.OP_CARD_STATE_CHANGED);

        String stateExtra = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
        
        if (IccCardConstants.INTENT_VALUE_ICC_READY.equals(stateExtra)){
            args.putBoolean(AppInterface.CARD_STATE,true);
        }            
        else if(IccCardConstants.INTENT_VALUE_ICC_DEACTIVATED.equals(stateExtra)){
            args.putBoolean(AppInterface.CARD_STATE,false);
        }
        else {
           return;
        }
        args.putInt(StkAppService.SLOT_ID, slotid);
        context.startService(new Intent(context, StkAppService.class)
                .putExtras(args));
    }
}
