/*
 * Copyright (C) 2007 The Android Open Source Project
 * Copyright (c) 2009-10, Code Aurora Forum. All rights reserved.
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

import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.ComponentName;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Vibrator;
import android.telephony.TelephonyManager;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.telephony.gsm.stk.AppInterface;
import com.android.internal.telephony.gsm.stk.Menu;
import com.android.internal.telephony.gsm.stk.Item;
import com.android.internal.telephony.gsm.stk.ResultCode;
import com.android.internal.telephony.gsm.stk.StkCmdMessage;
import com.android.internal.telephony.gsm.stk.ToneSettings;
import com.android.internal.telephony.gsm.stk.StkCmdMessage.BrowserSettings;
import com.android.internal.telephony.gsm.stk.LaunchBrowserMode;
import com.android.internal.telephony.gsm.stk.StkLog;
import com.android.internal.telephony.gsm.stk.StkResponseMessage;
import com.android.internal.telephony.gsm.stk.TextMessage;
import com.android.internal.telephony.gsm.stk.LaunchBrowserMode;
import com.android.internal.telephony.gsm.stk.StkCmdMessage.SetupEventListSettings;
import static com.android.internal.telephony.gsm.stk.StkCmdMessage.SetupEventListConstants.*;

import java.util.LinkedList;
import java.util.List;
import java.util.Iterator;

/**
 * SIM toolkit application level service. Interacts with Telephopny messages,
 * application's launch and user input from STK UI elements.
 *
 */
public class StkAppService extends Service implements Runnable {

    // members
    private volatile Looper mServiceLooper;
    private volatile ServiceHandler mServiceHandler;
    private AppInterface mStkService;
    private Context mContext = null;
    private StkCmdMessage mMainCmd = null;
    private StkCmdMessage mCurrentCmd = null;
    private Menu mCurrentMenu = null;
    private String lastSelectedItem = null;
    private boolean mMenuIsVisibile = false;
    private boolean responseNeeded = true;
    private boolean mCmdInProgress = false;
    private NotificationManager mNotificationManager = null;
    private LinkedList<DelayedCmd> mCmdsQ = null;
    private boolean launchBrowser = false;
    private BrowserSettings mBrowserSettings = null;
    static StkAppService sInstance = null;
    private SetupEventListSettings mSetupEventListSettings = null;
    private boolean mClearSelectItem = false;
    private boolean mDisplayTextDlgIsVisibile = false;

    // Used for setting FLAG_ACTIVITY_NO_USER_ACTION when
    // creating an intent.
    private enum InitiatedByUserAction {
        yes,            // The action was started via a user initiated action
        unknown,        // Not known for sure if user initated the action
    }

    // constants
    static final String OPCODE = "op";
    static final String CMD_MSG = "cmd message";
    static final String RES_ID = "response id";
    static final String MENU_SELECTION = "menu selection";
    static final String INPUT = "input";
    static final String HELP = "help";
    static final String CONFIRMATION = "confirm";
    static final String SCREEN_STATUS = "screen status";

    // These below constants are used for SETUP_EVENT_LIST
    static final String SETUP_EVENT_TYPE = "event";
    static final String SETUP_EVENT_CAUSE = "cause";

    // operations ids for different service functionality.
    static final int OP_CMD = 1;
    static final int OP_RESPONSE = 2;
    static final int OP_LAUNCH_APP = 3;
    static final int OP_END_SESSION = 4;
    static final int OP_BOOT_COMPLETED = 5;
    private static final int OP_DELAYED_MSG = 6;
    static final int OP_IDLE_SCREEN = 7;
    static final int OP_BROWSER_TERMINATION = 8;

    // Response ids
    static final int RES_ID_MENU_SELECTION = 11;
    static final int RES_ID_INPUT = 12;
    static final int RES_ID_CONFIRM = 13;
    static final int RES_ID_DONE = 14;
    static final int RES_ID_SETUP_EVENT_LIST = 15;

    static final int RES_ID_TIMEOUT = 20;
    static final int RES_ID_BACKWARD = 21;
    static final int RES_ID_END_SESSION = 22;
    static final int RES_ID_EXIT = 23;

    private static final String PACKAGE_NAME = "com.android.stk";
    private static final String MENU_ACTIVITY_NAME =
                                        PACKAGE_NAME + ".StkMenuActivity";
    private static final String INPUT_ACTIVITY_NAME =
                                        PACKAGE_NAME + ".StkInputActivity";

    // Notification id used to display Idle Mode text in NotificationManager.
    private static final int STK_NOTIFICATION_ID = 333;
    private TextMessage idleModeText;
    private boolean mDisplayText = false;
    private boolean screenIdle = true;

    // Inner class used for queuing telephony messages (proactive commands,
    // session end) while the service is busy processing a previous message.
    private class DelayedCmd {
        // members
        int id;
        StkCmdMessage msg;

        DelayedCmd(int id, StkCmdMessage msg) {
            this.id = id;
            this.msg = msg;
        }
    }

    @Override
    public void onCreate() {
        // Initialize members
        mStkService = com.android.internal.telephony.gsm.stk.StkService
                .getInstance();

        // NOTE mStkService is a singleton and continues to exist even if the GSMPhone is disposed
        //   after the radio technology change from GSM to CDMA so the PHONE_TYPE_CDMA check is
        //   needed. In case of switching back from CDMA to GSM the GSMPhone constructor updates
        //   the instance. (TODO: test).
        if ((mStkService == null)
                && (TelephonyManager.getDefault().getPhoneType()
                                != TelephonyManager.PHONE_TYPE_CDMA)) {
            StkLog.d(this, " Unable to get Service handle");
            return;
        }

        mCmdsQ = new LinkedList<DelayedCmd>();
        Thread serviceThread = new Thread(null, this, "Stk App Service");
        serviceThread.start();
        mContext = getBaseContext();
        mNotificationManager = (NotificationManager) mContext
                .getSystemService(Context.NOTIFICATION_SERVICE);
        sInstance = this;
    }

    @Override
    public void onStart(Intent intent, int startId) {
        waitForLooper();

        // onStart() method can be passed a null intent
        // TODO: replace onStart() with onStartCommand()
        if (intent == null) {
            return;
        }

        Bundle args = intent.getExtras();

        if (args == null) {
            return;
        }

        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = args.getInt(OPCODE);
        switch(msg.arg1) {
        case OP_CMD:
            msg.obj = args.getParcelable(CMD_MSG);
            break;
        case OP_RESPONSE:
        case OP_IDLE_SCREEN:
        case OP_BROWSER_TERMINATION:
            msg.obj = args;
            /* falls through */
        case OP_LAUNCH_APP:
        case OP_END_SESSION:
        case OP_BOOT_COMPLETED:
            break;
        default:
            return;
        }
        mServiceHandler.sendMessage(msg);
    }

    @Override
    public void onDestroy() {
        waitForLooper();
        mServiceLooper.quit();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void run() {
        Looper.prepare();

        mServiceLooper = Looper.myLooper();
        mServiceHandler = new ServiceHandler();

        Looper.loop();
    }

    /*
     * Package api used by StkMenuActivity to indicate if its on the foreground.
     */
    void indicateMenuVisibility(boolean visibility) {
        mMenuIsVisibile = visibility;
    }

    /*
     * Package api used by StkDialogActivity to indicate if its on the foreground.
     */
    void indicateDisplayTextDlgVisibility(boolean visibility) {
        mDisplayTextDlgIsVisibile = visibility;
    }

    /*
     * Package api used by StkMenuActivity to get its Menu parameter.
     */
    Menu getMenu() {
        return mCurrentMenu;
    }

    /*
     * Package api used by StkMenuActivity to check if SelectItem needs to be
     * cleaned.
     */
    boolean isClearSelectItem() {
        return mClearSelectItem;
    }

    /*
     * Package api used by UI Activities and Dialogs to communicate directly
     * with the service to deliver state information and parameters.
     */
    static StkAppService getInstance() {
        return sInstance;
    }

    private void waitForLooper() {
        while (mServiceHandler == null) {
            synchronized (this) {
                try {
                    wait(100);
                } catch (InterruptedException e) {
                }
            }
        }
    }

    private final class ServiceHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            int opcode = msg.arg1;

            switch (opcode) {
            case OP_LAUNCH_APP:
                if (mMainCmd == null) {
                    // nothing todo when no SET UP MENU command didn't arrive.
                    return;
                }
                launchMenuActivity(null);
                break;
            case OP_CMD:
                StkCmdMessage cmdMsg = (StkCmdMessage) msg.obj;
                // There are two types of commands:
                // 1. Interactive - user's response is required.
                // 2. Informative - display a message, no interaction with the user.
                //
                // Informative commands can be handled immediately without any delay.
                // Interactive commands can't override each other. So if a command
                // is already in progress, we need to queue the next command until
                // the user has responded or a timeout expired.
                if (!isCmdInteractive(cmdMsg)) {
                    handleCmd(cmdMsg);
                } else {
                    if (!mCmdInProgress) {
                        mCmdInProgress = true;
                        handleCmd((StkCmdMessage) msg.obj);
                    } else {
                        mCmdsQ.addLast(new DelayedCmd(OP_CMD,
                                (StkCmdMessage) msg.obj));
                    }
                }
                break;
            case OP_RESPONSE:
                if (responseNeeded) {
                    handleCmdResponse((Bundle) msg.obj);
                }
                // call delayed commands if needed.
                if (mCmdsQ.size() != 0) {
                    callDelayedMsg();
                } else {
                    mCmdInProgress = false;
                }
                // reset response needed state var to its original value.
                responseNeeded = true;
                break;
            case OP_END_SESSION:
                if (!mCmdInProgress) {
                    mCmdInProgress = true;
                    handleSessionEnd();
                } else {
                    mCmdsQ.addLast(new DelayedCmd(OP_END_SESSION, null));
                }
                break;
            case OP_BOOT_COMPLETED:
                StkLog.d(this, "OP_BOOT_COMPLETED");
                if (mMainCmd == null) {
                    StkAppInstaller.unInstall(mContext);
                }
                break;
            case OP_DELAYED_MSG:
                handleDelayedCmd();
                break;
            case OP_BROWSER_TERMINATION:
                StkLog.d(this, "Browser Closed");
                handleSetupEventList(BROWSER_TERMINATION_EVENT,(Bundle) msg.obj);
                break;
            case OP_IDLE_SCREEN:
                Bundle args = ((Bundle) msg.obj);
                screenIdle = args.getBoolean (SCREEN_STATUS);
                if (idleModeText != null) {
                    launchIdleModeText();
                }
                if (mDisplayText) {
                    if (!screenIdle) {
                        sendScreenBusyResponse();
                    } else {
                        launchTextDialog();
                    }
                    mDisplayText = false;
                    // If an idle text proactive command is set then the
                    // request for getting screen status still holds true.
                    if (idleModeText == null) {
                        Intent StkIntent = new Intent(AppInterface.CHECK_SCREEN_IDLE_ACTION);
                        StkIntent.putExtra("SCREEN_STATUS_REQUEST",false);
                        sendBroadcast(StkIntent);
                    }
                }
                break;
            case MSG_ID_STOP_TONE:
                StkLog.d(this, "Received MSG_ID_STOP_TONE");
                handleStopTone();
                break;
            }
        }
    }

    private void handleStopTone() {
        sendResponse(StkAppService.RES_ID_DONE);
        player.stop();
        player.release();
        mVibrator.cancel();
        StkLog.d(this, "Finished handling PlayTone with Null alpha");
    }

    private void sendResponse(int resId) {
        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = OP_RESPONSE;
        Bundle args = new Bundle();
        args.putInt(StkAppService.RES_ID, resId);
        msg.obj = args;
        mServiceHandler.sendMessage(msg);
    }

    private boolean isCmdInteractive(StkCmdMessage cmd) {
        switch (cmd.getCmdType()) {
        case SEND_DTMF:
        case SEND_SMS:
        case SEND_SS:
        case SEND_USSD:
        case SET_UP_IDLE_MODE_TEXT:
        case SET_UP_MENU:
        case SET_UP_EVENT_LIST:
            return false;
        }

        return true;
    }

    private void handleDelayedCmd() {
        if (mCmdsQ.size() != 0) {
            DelayedCmd cmd = mCmdsQ.poll();
            switch (cmd.id) {
            case OP_CMD:
                handleCmd(cmd.msg);
                break;
            case OP_END_SESSION:
                handleSessionEnd();
                break;
            }
        }
    }

    private void callDelayedMsg() {
        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = OP_DELAYED_MSG;
        mServiceHandler.sendMessage(msg);
    }

    private void handleSessionEnd() {
        mCurrentCmd = mMainCmd;
        lastSelectedItem = null;
        // In case of SET UP MENU command which removed the app, don't
        // update the current menu member.
        if (mCurrentMenu != null && mMainCmd != null) {
            mCurrentMenu = mMainCmd.getMenu();
        }
        // In some scenarios race condition occurs. Where finish() is called
        // for SELECT_ITEM in STKMenuActivity but not destroyed.
        StkLog.d(this, "mMenuIsVisibile: " + mMenuIsVisibile + " mClearSelectItem: "
                + mClearSelectItem);
        if (mMenuIsVisibile && !mClearSelectItem) {
            launchMenuActivity(null);
        }
        mClearSelectItem = false;
        if (mCmdsQ.size() != 0) {
            callDelayedMsg();
        } else {
            mCmdInProgress = false;
        }
        // In case a launch browser command was just confirmed, launch that url.
        if (launchBrowser) {
            launchBrowser = false;
            launchBrowser(mBrowserSettings);
        }
    }

    private void sendScreenBusyResponse() {
        if (mCurrentCmd == null) {
            return;
        }
        StkResponseMessage resMsg = new StkResponseMessage(mCurrentCmd);
        StkLog.d(this, "SCREEN_BUSY");
        resMsg.setResultCode(ResultCode.TERMINAL_CRNTLY_UNABLE_TO_PROCESS);
        mStkService.onCmdResponse(resMsg);
        // reset response needed state var to its original value.
        responseNeeded = true;
        if (mCmdsQ.size() != 0) {
            callDelayedMsg();
        } else {
            mCmdInProgress = false;
        }
    }

    private void handleCmd(StkCmdMessage cmdMsg) {
        if (cmdMsg == null) {
            return;
        }
        // save local reference for state tracking.
        mCurrentCmd = cmdMsg;
        boolean waitForUsersResponse = true;

        StkLog.d(this, cmdMsg.getCmdType().name());
        switch (cmdMsg.getCmdType()) {
        case DISPLAY_TEXT:
            TextMessage msg = cmdMsg.geTextMessage();

            //In case when TR already sent no response is expected by Stk app
            if (!msg.responseNeeded) {
                waitForUsersResponse = false;
            }
            if (lastSelectedItem != null) {
                msg.title = lastSelectedItem;
            } else if (mMainCmd != null){
                msg.title = mMainCmd.getMenu().title;
            } else {
                // TODO: get the carrier name from the SIM
                msg.title = "";
            }
            //If the device is not in idlescreen and a low priority display
            //text message command arrives then send screen busy terminal
            //response with out displaying the message. Otherwise display the
            //message. The existing displayed message shall be updated with the
            //new display text proactive command (Refer to ETSI TS 102 384
            //section 27.22.4.1.4.4.2).
            if (!(msg.isHighPriority || mMenuIsVisibile || mDisplayTextDlgIsVisibile)) {
                Intent StkIntent = new Intent(AppInterface.CHECK_SCREEN_IDLE_ACTION);
                StkIntent.putExtra("SCREEN_STATUS_REQUEST",true);
                sendBroadcast(StkIntent);
                mDisplayText = true;
            } else {
                launchTextDialog();
            }
            break;
        case SELECT_ITEM:
            mCurrentMenu = cmdMsg.getMenu();
            // Check if STkMenuActivity is already active, else clean
            // the SELECT_ITEM after user selects an item.
            if (!mMenuIsVisibile) {
                StkLog.d(this, "Clear SelectItem after user selection");
                mClearSelectItem = true;
            }
            launchMenuActivity(cmdMsg.getMenu());
            break;
        case SET_UP_MENU:
            mMainCmd = mCurrentCmd;
            mCurrentMenu = cmdMsg.getMenu();
            if (removeMenu()) {
                StkLog.d(this, "Uninstall App");
                mCurrentMenu = null;
                StkAppInstaller.unInstall(mContext);
            } else {
                StkLog.d(this, "Install App");
                StkAppInstaller.install(mContext);
            }
            if (mMenuIsVisibile) {
                launchMenuActivity(null);
            }
            break;
        case GET_INPUT:
        case GET_INKEY:
            launchInputActivity();
            break;
        case SET_UP_IDLE_MODE_TEXT:
            waitForUsersResponse = false;
            idleModeText = mCurrentCmd.geTextMessage();
            // Send intent to ActivityManagerService to get the screen status
            Intent idleStkIntent  = new Intent(AppInterface.CHECK_SCREEN_IDLE_ACTION);
            if (idleModeText != null) {
                idleStkIntent.putExtra("SCREEN_STATUS_REQUEST",true);
            } else {
                idleStkIntent.putExtra("SCREEN_STATUS_REQUEST",false);
                launchIdleModeText();
            }
            StkLog.d(this, "set up idle mode");
            mCurrentCmd = mMainCmd;
            sendBroadcast(idleStkIntent);
            break;
        case SEND_DTMF:
        case SEND_SMS:
        case SEND_SS:
        case SEND_USSD:
            waitForUsersResponse = false;
            launchEventMessage();
            mCurrentCmd = mMainCmd;
            break;
        case LAUNCH_BROWSER:
            launchConfirmationDialog(mCurrentCmd.geTextMessage());
            break;
        case SET_UP_CALL:
            launchConfirmationDialog(mCurrentCmd.getCallSettings().confirmMsg);
            break;
        case PLAY_TONE:
            launchToneDialog();
            break;
        case SET_UP_EVENT_LIST:
            mSetupEventListSettings = mCurrentCmd.getSetEventList();
            mCurrentCmd = mMainCmd;
            break;
        }

        if (!waitForUsersResponse) {
            if (mCmdsQ.size() != 0) {
                callDelayedMsg();
            } else {
                mCmdInProgress = false;
            }
        }
    }

    private void handleCmdResponse(Bundle args) {
        if (mCurrentCmd == null) {
            return;
        }
        StkResponseMessage resMsg = new StkResponseMessage(mCurrentCmd);

        // set result code
        boolean helpRequired = args.getBoolean(HELP, false);

        switch(args.getInt(RES_ID)) {
        case RES_ID_MENU_SELECTION:
            StkLog.d(this, "RES_ID_MENU_SELECTION");
            int menuSelection = args.getInt(MENU_SELECTION);
            switch(mCurrentCmd.getCmdType()) {
            case SET_UP_MENU:
            case SELECT_ITEM:
                lastSelectedItem = getItemName(menuSelection);
                if (helpRequired) {
                    resMsg.setResultCode(ResultCode.HELP_INFO_REQUIRED);
                } else {
                    resMsg.setResultCode( mCurrentCmd.getLoadOptionalIconFailed()? ResultCode.PRFRMD_ICON_NOT_DISPLAYED
                                        : ResultCode.OK);
                }
                resMsg.setMenuSelection(menuSelection);
                break;
            }
            break;
        case RES_ID_SETUP_EVENT_LIST:
            StkLog.d(this, "RES_ID_SETUP_EVENT_LIST");
            int eventValue = args.getInt(SETUP_EVENT_TYPE);
            byte[] addedInfo = args.getByteArray(SETUP_EVENT_CAUSE);
            resMsg.setResultCode(ResultCode.OK);
            resMsg.setEventDownload(eventValue, addedInfo);
            break;
        case RES_ID_INPUT:
            StkLog.d(this, "RES_ID_INPUT");
            String input = args.getString(INPUT);
            if (mCurrentCmd.geInput().yesNo) {
                boolean yesNoSelection = input
                        .equals(StkInputActivity.YES_STR_RESPONSE);
                resMsg.setYesNo(yesNoSelection);
            } else {
                if (helpRequired) {
                    resMsg.setResultCode(ResultCode.HELP_INFO_REQUIRED);
                } else {
                    resMsg.setResultCode( mCurrentCmd.getLoadOptionalIconFailed()? ResultCode.PRFRMD_ICON_NOT_DISPLAYED
                                        : ResultCode.OK);
                    resMsg.setInput(input);
                }
            }
            break;
        case RES_ID_CONFIRM:
            StkLog.d(this, "RES_ID_CONFIRM");
            boolean confirmed = args.getBoolean(CONFIRMATION);
            switch (mCurrentCmd.getCmdType()) {
            case DISPLAY_TEXT:
		if(confirmed){
                   resMsg.setResultCode( mCurrentCmd.getLoadOptionalIconFailed()? ResultCode.PRFRMD_ICON_NOT_DISPLAYED
                                       : ResultCode.OK);
                }
                else{
                   resMsg.setResultCode(ResultCode.UICC_SESSION_TERM_BY_USER);
                }
                break;
            case LAUNCH_BROWSER:
                mBrowserSettings = mCurrentCmd.getBrowserSettings();
                /* If Launch Browser mode is LAUNCH_IF_NOT_ALREADY_LAUNCHED and if the browser is already launched
                 * then send the error code and additional info indicating 'Browser Unavilable'(0x02)
                 */
                if( (mBrowserSettings.mode ==  LaunchBrowserMode.LAUNCH_IF_NOT_ALREADY_LAUNCHED) &&
                                               confirmed && isBrowserLaunched(mContext)) {
                    resMsg.setResultCode(ResultCode.LAUNCH_BROWSER_ERROR);
                    resMsg.setAdditionalInfo(true,0x02);
                    StkLog.d(this, "LAUNCH_BROWSER_ERROR - Browser_Unavailable");
                } else {
                    resMsg.setResultCode(confirmed ? ResultCode.OK
                                                   : ResultCode.UICC_SESSION_TERM_BY_USER);
                }

                if (confirmed) {
                    launchBrowser = true;
                }
                break;
            case SET_UP_CALL:
                resMsg.setResultCode(ResultCode.OK);
                resMsg.setConfirmation(confirmed);
                if (confirmed) {
                    launchCallMsg();
                }
                break;
            }
            break;
        case RES_ID_DONE:
            resMsg.setResultCode(ResultCode.OK);
            break;
        case RES_ID_BACKWARD:
            StkLog.d(this, "RES_ID_BACKWARD");
            resMsg.setResultCode(ResultCode.BACKWARD_MOVE_BY_USER);
            break;
        case RES_ID_END_SESSION:
            StkLog.d(this, "RES_ID_END_SESSION");
            resMsg.setResultCode(ResultCode.UICC_SESSION_TERM_BY_USER);
            break;
        case RES_ID_TIMEOUT:
            StkLog.d(this, "RES_ID_TIMEOUT");
            //GCF testcase 27.22.4.1.1 Expected Sequence 1.5 (DISPLAY TEXT, Clear
            //message after delay,successful) expects result code OK.
            //If the command qualifier specifies no user response is required
            //then sending the response as OK instead of NO_RESPONSE_FROM_USER.
            if ((mCurrentCmd.getCmdType().value() ==
                 AppInterface.CommandType.DISPLAY_TEXT.value()) &&
                 (mCurrentCmd.geTextMessage().userClear == false)) {
                resMsg.setResultCode(ResultCode.OK);
            } else {
                resMsg.setResultCode(ResultCode.NO_RESPONSE_FROM_USER);
            }
            break;
        default:
            StkLog.d(this, "Unknown result id");
            return;
        }
        mStkService.onCmdResponse(resMsg);
        handleSessionEnd();
    }

    /**
     * Returns 0 or FLAG_ACTIVITY_NO_USER_ACTION, 0 means the user initiated the action.
     *
     * @param userAction If the userAction is yes then we always return 0 otherwise
     * mMenuIsVisible is used to determine what to return. If mMenuIsVisible is true
     * then we are the foreground app and we'll return 0 as from our perspective a
     * user action did cause. If it's false than we aren't the foreground app and
     * FLAG_ACTIVITY_NO_USER_ACTION is returned.
     *
     * @return 0 or FLAG_ACTIVITY_NO_USER_ACTION
     */
    private int getFlagActivityNoUserAction(InitiatedByUserAction userAction) {
        return ((userAction == InitiatedByUserAction.yes) | mMenuIsVisibile) ?
                                                    0 : Intent.FLAG_ACTIVITY_NO_USER_ACTION;
    }

    private void launchMenuActivity(Menu menu) {
        Intent newIntent = new Intent(Intent.ACTION_VIEW);
        newIntent.setClassName(PACKAGE_NAME, MENU_ACTIVITY_NAME);
        int intentFlags = Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP;
        if (menu == null) {
            // We assume this was initiated by the user pressing the tool kit icon
            intentFlags |= getFlagActivityNoUserAction(InitiatedByUserAction.yes);

            newIntent.putExtra("STATE", StkMenuActivity.STATE_MAIN);
        } else {
            // We don't know and we'll let getFlagActivityNoUserAction decide.
            intentFlags |= getFlagActivityNoUserAction(InitiatedByUserAction.unknown);

            newIntent.putExtra("STATE", StkMenuActivity.STATE_SECONDARY);
        }
        newIntent.setFlags(intentFlags);
        mContext.startActivity(newIntent);
    }

    private void launchInputActivity() {
        Intent newIntent = new Intent(Intent.ACTION_VIEW);
        newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | getFlagActivityNoUserAction(InitiatedByUserAction.unknown));
        newIntent.setClassName(PACKAGE_NAME, INPUT_ACTIVITY_NAME);
        newIntent.putExtra("INPUT", mCurrentCmd.geInput());
        mContext.startActivity(newIntent);
    }

    private void launchTextDialog() {
        Intent newIntent = new Intent(this, StkDialogActivity.class);
        newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_NO_HISTORY
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                | getFlagActivityNoUserAction(InitiatedByUserAction.unknown));
        newIntent.putExtra("TEXT", mCurrentCmd.geTextMessage());
        startActivity(newIntent);
    }

    private void sendSetUpEventResponse(int event, byte[] addedInfo) {
        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = OP_RESPONSE;
        Bundle args = new Bundle();
        args.putInt(StkAppService.SETUP_EVENT_TYPE,event);
        args.putByteArray(StkAppService.SETUP_EVENT_CAUSE,addedInfo);
        args.putInt(StkAppService.RES_ID, RES_ID_SETUP_EVENT_LIST);
        msg.obj = args;
        mServiceHandler.sendMessage(msg);
    }

    private void handleSetupEventList(int event, Bundle args) {

        boolean eventPresent = false;
        byte[] addedInfo;
        StkLog.d(this, "Event :" + event);

        if (mSetupEventListSettings != null) {
            /*
             * Checks if the event is present in the EventList updated by last
             * SetupEventList Proactive Command
             */
            for (int i = 0; i < mSetupEventListSettings.eventList.length; i++) {
                if (event == mSetupEventListSettings.eventList[i]) {
                    eventPresent = true;
                    break;
                }
            }

            /* If Event is present send the response to ICC */
            if (eventPresent == true) {
                StkLog.d(this, " Event " + event + "exists in the EventList");
                addedInfo = new byte[MAX_ADDED_EVENT_DOWNLOAD_LEN];
                switch (event) {
                    case BROWSER_TERMINATION_EVENT:
                        int browserTerminationCause = args
                                .getInt(AppInterface.BROWSER_TERMINATION_CAUSE);
                        StkLog.d(this, "BrowserTerminationCause: " + browserTerminationCause);
                        addedInfo[0] = (byte) browserTerminationCause;
                        sendSetUpEventResponse(event, addedInfo);
                        break;
                    default:
                        break;
                }
            } else {
                StkLog.d(this, " Event does not exist in the EventList");
            }
        } else {
            StkLog.d(this, "SetupEventList is not received. Ignoring the event: " + event);
        }
    }

    private void launchEventMessage() {
        TextMessage msg = mCurrentCmd.geTextMessage();
        // Suppress the Alpha NULL identifier.So checking for
        // msg.text
        if (msg == null || msg.text == null) {
            return;
        }
        Toast toast = new Toast(mContext.getApplicationContext());
        LayoutInflater inflate = (LayoutInflater) mContext
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View v = inflate.inflate(R.layout.stk_event_msg, null);
        TextView tv = (TextView) v
                .findViewById(com.android.internal.R.id.message);
        ImageView iv = (ImageView) v
                .findViewById(com.android.internal.R.id.icon);
        if (msg.icon != null) {
            iv.setImageBitmap(msg.icon);
        } else {
            iv.setVisibility(View.GONE);
        }

        /** This is the case to handle icon identifier 'self-explanatory'.
         ** In case of 'self explanatory' stkapp should display the specified
         ** icon in proactive command (but not the alpha string).
         ** But, Android at present doesn't support ICON display. So, considering the
         ** self explanatory case as non-self explanatory and displaying the
         ** alpha string always. Refer to TS ETSI 102 223 6.5.4  */
        if (!msg.iconSelfExplanatory || mCurrentCmd.getLoadOptionalIconFailed()) {
            tv.setText(msg.text);
        }

        toast.setView(v);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setGravity(Gravity.BOTTOM, 0, 0);
        toast.show();
    }

    private void launchConfirmationDialog(TextMessage msg) {
        msg.title = lastSelectedItem;
        Intent newIntent = new Intent(this, StkDialogActivity.class);
        newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NO_HISTORY
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                | getFlagActivityNoUserAction(InitiatedByUserAction.unknown));
        newIntent.putExtra("TEXT", msg);
        startActivity(newIntent);
    }

    private void launchBrowser(BrowserSettings settings) {
        if (settings == null) {
            return;
        }
        // Set browser launch mode
        Intent intent = new Intent();
        intent.setClassName("com.android.browser",
                "com.android.browser.BrowserActivity");

        // to launch home page, make sure that data Uri is null.
        Uri data = null;
        if (settings.url != null) {
            data = Uri.parse(settings.url);
        }
        intent.setData(data);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        switch (settings.mode) {
        case USE_EXISTING_BROWSER:
            intent.setAction(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            break;
        case LAUNCH_NEW_BROWSER:
            intent.setAction(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            break;
        case LAUNCH_IF_NOT_ALREADY_LAUNCHED:
            if(data != null)
                intent.setAction(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            break;
        }
        // start browser activity
        startActivity(intent);
        // a small delay, let the browser start, before processing the next command.
        // this is good for scenarios where a related DISPLAY TEXT command is
        // followed immediately.
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {}
    }

    private boolean isBrowserLaunched(Context context) {
        int MAX_TASKS = 99;
        ActivityManager mAcivityManager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
        if(mAcivityManager == null)
            return false;
        List<RunningTaskInfo> mRunningTasksList = mAcivityManager.getRunningTasks(MAX_TASKS);
        Iterator<RunningTaskInfo> mIterator = mRunningTasksList.iterator();
        while (mIterator.hasNext()) {
              RunningTaskInfo mRunningTask = mIterator.next();
              if (mRunningTask != null) {
                  ComponentName runningTaskComponent = mRunningTask.baseActivity;
                  if (runningTaskComponent.getClassName().equals("com.android.browser.BrowserActivity")) {
                      return true;
                  }
             }
        }
        return false;
    }

    private void launchCallMsg() {
        TextMessage msg = mCurrentCmd.getCallSettings().callMsg;
        if (msg.text == null || msg.text.length() == 0) {
            return;
        }
        msg.title = lastSelectedItem;

        Toast toast = Toast.makeText(mContext.getApplicationContext(), msg.text,
                Toast.LENGTH_LONG);
        toast.setGravity(Gravity.BOTTOM, 0, 0);
        toast.show();
    }

    private void launchIdleModeText() {
        TextMessage msg = idleModeText;
        if (msg.text == null) {
            mNotificationManager.cancel(STK_NOTIFICATION_ID);
        } else {
            if (screenIdle == false) {
                mNotificationManager.cancel(STK_NOTIFICATION_ID);
                return;
            }

            Notification notification = new Notification();
            RemoteViews contentView = new RemoteViews(
                    PACKAGE_NAME,
                    com.android.internal.R.layout.status_bar_latest_event_content);

            notification.flags |= Notification.FLAG_NO_CLEAR;
            notification.icon = com.android.internal.R.drawable.stat_notify_sim_toolkit;

           /** This is the case to handle icon identifier 'self-explanatory'.
            ** In case of 'self explanatory' stkapp should display the specified
            ** icon in proactive command (but not the alpha string).
            ** But, Android at present doesn't support ICON display. So, considering the
            ** self explanatory case as non-self explanatory and displaying the
            ** alpha string always. Refer to TS ETSI 102 223 6.5.4  */
            if (!msg.iconSelfExplanatory || mCurrentCmd.getLoadOptionalIconFailed()) {
                notification.tickerText = msg.text;
                contentView.setTextViewText(com.android.internal.R.id.text,
                        msg.text);
            }
            if (msg.icon != null) {
                contentView.setImageViewBitmap(com.android.internal.R.id.icon,
                        msg.icon);
            } else {
                contentView
                    .setImageViewResource(
                            com.android.internal.R.id.icon,
                            com.android.internal.R.drawable.stat_notify_sim_toolkit);
            }
            notification.contentView = contentView;
            notification.contentIntent = PendingIntent.getService(mContext, 0,
                    new Intent(mContext, StkAppService.class), 0);

            mNotificationManager.notify(STK_NOTIFICATION_ID, notification);
        }
    }

    private void launchToneDialog() {
        TextMessage toneMsg = mCurrentCmd.geTextMessage();
        ToneSettings settings = mCurrentCmd.getToneSettings();
        // Start activity only when there is alpha data otherwise play tone
        if (toneMsg.text != null) {
            StkLog.d(this, "toneMsg.text: " + toneMsg.text + " Starting Activity");
            Intent newIntent = new Intent(this, ToneDialog.class);
            newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY
                    | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    | getFlagActivityNoUserAction(InitiatedByUserAction.unknown));
            newIntent.putExtra("TEXT", toneMsg);
            newIntent.putExtra("TONE", settings);
            StkLog.d(this, "Starting Activity based on the ToneDialog Intent");
            startActivity(newIntent);
        } else {
            StkLog.d(this, "toneMsg.text: " + toneMsg.text + " NO Activity, play tone");
            onPlayToneNullAlphaTag(toneMsg, settings);
        }
    }

    TonePlayer player = null;

    Vibrator mVibrator = new Vibrator();

    // Message id to signal tone duration timeout.
    private static final int MSG_ID_STOP_TONE = 0xda;

    private void onPlayToneNullAlphaTag(TextMessage toneMsg, ToneSettings settings) {
        // Start playing tone and vibration
        player = new TonePlayer();
        StkLog.d(this, "Play tone settings.tone:" + settings.tone);
        player.play(settings.tone);
        int timeout = StkApp.calculateDurationInMilis(settings.duration);
        StkLog.d(this, "ToneDialog: Tone timeout :" + timeout);
        if (timeout == 0) {
            timeout = StkApp.TONE_DFEAULT_TIMEOUT;
        }
        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = MSG_ID_STOP_TONE;
        // trigger tone stop after timeout duration
        mServiceHandler.sendMessageDelayed(msg, timeout);
        if (settings.vibrate) {
            mVibrator.vibrate(timeout);
        }
    }

    private String getItemName(int itemId) {
        Menu menu = mCurrentCmd.getMenu();
        if (menu == null) {
            return null;
        }
        for (Item item : menu.items) {
            if (item.id == itemId) {
                return item.text;
            }
        }
        return null;
    }

    private boolean removeMenu() {
        try {
            if (mCurrentMenu.items.size() == 1 &&
                mCurrentMenu.items.get(0) == null) {
                return true;
            }
        } catch (NullPointerException e) {
            StkLog.d(this, "Unable to get Menu's items size");
            return true;
        }
        return false;
    }
}
