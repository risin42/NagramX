/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

import xyz.nextalone.nagram.NaConfig;

public class GcmPushListenerService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(RemoteMessage message) {
        if (isGooglePushDisabled()) return;

        String from = message.getFrom();
        Map<String, String> data = message.getData();
        long time = message.getSentTime();

        FileLog.d("FCM received data: " + data + " from: " + from);

        PushListenerController.processRemoteMessage(PushListenerController.PUSH_TYPE_FIREBASE, data.get("p"), time);
    }

    @Override
    public void onNewToken(@NonNull String token) {
        if (isGooglePushDisabled()) return;
        AndroidUtilities.runOnUIThread(() -> {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("Refreshed FCM token: " + token);
            }
            ApplicationLoader.postInitApplication();
            PushListenerController.sendRegistrationToServer(PushListenerController.PUSH_TYPE_FIREBASE, token);
        });
    }

    private static boolean isGooglePushDisabled() {
        int pushServiceType = NaConfig.getPreferences().getInt(NaConfig.INSTANCE.getPushServiceType().getKey(), 1);
        return pushServiceType != 1 && pushServiceType != 3;
    }
}
