/*
 * Copyright (C) 2007 The Android Open Source Project
 * Copyright (c) 2011, 2013 The Linux Foundation. All rights reserved.
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

import com.android.internal.telephony.cat.CatLog;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import com.qrd.plugin.feature_query.FeatureQuery;

/**
 * Application installer for SIM Toolkit.
 *
 */
abstract class StkAppInstaller {
    static final int  CARD_APP_TYPE_UNKNOWN=0;
    static final int  CARD_APP_TYPE_SIM=1;
    static final int  CARD_APP_TYPE_USIM=2;
    static final int  CARD_APP_TYPE_RUIM=3;

    private StkAppInstaller() {}

    static void install(Context context, int slotId) {
        if(FeatureQuery.FEATURE_USE_CU_USAT_STK_STYLE)
        {
            setWGAppState(context, true, slotId);
        }
        else
            {
            setAppState(context, true, slotId);
            }
    }

    static void unInstall(Context context, int slotId) {
         if(FeatureQuery.FEATURE_USE_CU_USAT_STK_STYLE)
        {
            setWGAppState(context, false, slotId);
        }
        else
            {
            setAppState(context, false, slotId);
            }
    }

    private static void setAppState(Context context, boolean install, int slotId) {
        if (context == null) {
            return;
        }
        PackageManager pm = context.getPackageManager();
        if (pm == null) {
            return;
        }
        ComponentName cName;

        // check that STK app package is known to the PackageManager
        if (slotId == 0) {
            cName = new ComponentName("com.android.stk",
                    "com.android.stk.StkLauncherActivity");
        } else if (slotId == 1) {
            cName = new ComponentName("com.android.stk",
            "com.android.stk.StkLauncherActivity2");
        } else {
            CatLog.d("StkAppInstaller", "Invalid subscription: " + slotId);
            return;
        }

        int state = install ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;

        try {
            pm.setComponentEnabledSetting(cName, state,
                    PackageManager.DONT_KILL_APP);
        } catch (Exception e) {
            CatLog.d("StkAppInstaller", "Could not change STK app state");
        }
    }
    private static void setWGAppState(Context context, boolean install, int slotId) {
            if (context == null) {
                return;
            }
            PackageManager pm = context.getPackageManager();
            if (pm == null) {
                return;
            }
    
            ComponentName cName;
            
            CatLog.d("StkAppInstaller", "slotId= "+slotId+",  CardType="+StkAppService.getCardType(slotId));
            if(install==false)
            {
                removeWGStkapp(slotId, pm);
            }
            else
            {
                if (slotId == 0)
                {
                    if(StkAppService.getCardType(slotId)==CARD_APP_TYPE_SIM)
                    {
                        cName = new ComponentName("com.android.stk", "com.android.stk.StkLauncherActivity");
                    }
                    else
                    {
                        cName = new ComponentName("com.android.stk", "com.android.stk.UsatLauncherActivity");
                    }
                    int state = install ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
                    CatLog.d("StkAppInstaller", "slotId= "+slotId+",  CardType="+StkAppService.getCardType(slotId)+", state="+state);
                    try {
                        pm.setComponentEnabledSetting(cName, state,
                            PackageManager.DONT_KILL_APP);
                    } catch (Exception e) {
                        CatLog.d("StkAppInstaller", "Could not change STK app state");
                    }
                }
                else if (slotId == 1)
                {
                    if(StkAppService.getCardType(slotId)==CARD_APP_TYPE_USIM)
                    {
                        cName = new ComponentName("com.android.stk", "com.android.stk.UsatLauncherActivity2");
                    }
                    else 
                    {
                        cName = new ComponentName("com.android.stk","com.android.stk.StkLauncherActivity2");
                    }
                    int state = install ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
                    CatLog.d("StkAppInstaller", "slotId= "+slotId+",  CardType="+StkAppService.getCardType(slotId)+", state="+state);
                    try {
                        pm.setComponentEnabledSetting(cName, state,
                            PackageManager.DONT_KILL_APP);
                    } catch (Exception e) {
                        CatLog.d("StkAppInstaller", "Could not change STK app state");
                    }
                }
                else
                {
                    removeWGAllStkapp(pm);
                    CatLog.d("StkAppInstaller", "slot id is "+slotId+", wrong! ");
                }
            }
        }
      private static void removeWGStkapp(int slot_id, PackageManager pm)
    {
        ComponentName cName;
        ComponentName cName1;
        ComponentName cName2;
        ComponentName cName3;
        CatLog.d("StkAppInstaller", "enter removeStkapp");
        if(slot_id==0)
        {
            cName = new ComponentName("com.android.stk", "com.android.stk.UsatLauncherActivity");
            try {
                pm.setComponentEnabledSetting(cName,  PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
            } catch (Exception e) {
                CatLog.d("StkAppInstaller", "Could not change STK app state");
            }
            cName2 = new ComponentName("com.android.stk", "com.android.stk.StkLauncherActivity");
            try {
                pm.setComponentEnabledSetting(cName2,  PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
            } catch (Exception e) {
                CatLog.d("StkAppInstaller", "Could not change STK app state");
            }
        }       
        if(slot_id==1)  
        {
            cName1 = new ComponentName("com.android.stk", "com.android.stk.UsatLauncherActivity2");
            try {
                pm.setComponentEnabledSetting(cName1,  PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
            } catch (Exception e) {
                CatLog.d("StkAppInstaller", "Could not change STK app state");
            }
            cName3 = new ComponentName("com.android.stk", "com.android.stk.StkLauncherActivity2");
            try {
                pm.setComponentEnabledSetting(cName3,  PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
            } catch (Exception e) {
                CatLog.d("StkAppInstaller", "Could not change STK app state");
            }
        }
    }

    private static void removeWGAllStkapp(PackageManager pm)
    {
        ComponentName cName;
        ComponentName cName1;
        ComponentName cName2;
        ComponentName cName3;
        CatLog.d("StkAppInstaller", "enter removeAllStkapp");

        cName = new ComponentName("com.android.stk", "com.android.stk.UsatLauncherActivity");
        try {
            pm.setComponentEnabledSetting(cName,  PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
        } catch (Exception e) {
            CatLog.d("StkAppInstaller", "Could not change STK app state");
        }

        cName1 = new ComponentName("com.android.stk", "com.android.stk.UsatLauncherActivity2");
        try {
            pm.setComponentEnabledSetting(cName1,  PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
        } catch (Exception e) {
            CatLog.d("StkAppInstaller", "Could not change STK app state");
        }

        cName2 = new ComponentName("com.android.stk", "com.android.stk.StkLauncherActivity");
        try {
            pm.setComponentEnabledSetting(cName2,  PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
        } catch (Exception e) {
            CatLog.d("StkAppInstaller", "Could not change STK app state");
        }

        cName3 = new ComponentName("com.android.stk", "com.android.stk.StkLauncherActivity2");
        try {
            pm.setComponentEnabledSetting(cName3,  PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
        } catch (Exception e) {
            CatLog.d("StkAppInstaller", "Could not change STK app state");
        }
    }   

}
