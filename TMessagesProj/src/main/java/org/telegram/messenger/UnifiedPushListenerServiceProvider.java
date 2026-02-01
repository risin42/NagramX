package org.telegram.messenger;

import android.os.SystemClock;
import android.text.TextUtils;

import org.unifiedpush.android.connector.UnifiedPush;

import java.util.List;

public class UnifiedPushListenerServiceProvider implements PushListenerController.IPushListenerServiceProvider {
    public UnifiedPushListenerServiceProvider(){}

    @Override
    public boolean hasServices() {
        return !UnifiedPush.getDistributors(ApplicationLoader.applicationContext).isEmpty();
    }

    @Override
    public String getLogTitle() {
        return "UnifiedPush";
    }

    @Override
    public void onRequestPushToken() {
        String currentPushString = SharedConfig.pushString;
        if (!TextUtils.isEmpty(currentPushString)) {
            FileLog.d("UnifiedPush endpoint = " + currentPushString);
        } else {
            FileLog.d("No UnifiedPush string found");
        }
        Utilities.globalQueue.postRunnable(() -> {
            try {
                SharedConfig.pushStringGetTimeStart = SystemClock.elapsedRealtime();
                SharedConfig.saveConfig();
                if (UnifiedPush.getAckDistributor(ApplicationLoader.applicationContext) == null) {
                    List<String> distributors = UnifiedPush.getDistributors(ApplicationLoader.applicationContext);
                    if (distributors.size() > 0) {
                        String distributor =  distributors.get(0);
                        UnifiedPush.saveDistributor(ApplicationLoader.applicationContext, distributor);
                    }
                }
                UnifiedPush.register(
                        ApplicationLoader.applicationContext,
                        "default",
                        "Telegram Simple Push",
                        null
                );
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    @Override
    public int getPushType() {
        return PushListenerController.PUSH_TYPE_SIMPLE;
    }
}
