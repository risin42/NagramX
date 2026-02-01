package org.telegram.messenger;

import android.os.SystemClock;

import androidx.annotation.NonNull;

import org.telegram.tgnet.ConnectionsManager;
import org.unifiedpush.android.connector.FailedReason;
import org.unifiedpush.android.connector.PushService;
import org.unifiedpush.android.connector.UnifiedPush;
import org.unifiedpush.android.connector.data.PushEndpoint;
import org.unifiedpush.android.connector.data.PushMessage;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;

import xyz.nextalone.nagram.NaConfig;

public class UnifiedPushService extends PushService {

    public static final String UP_GATEWAY_DEFAULT = "https://p2p.belloworld.it/"; // https://github.com/Mercurygram/Mercurygram?tab=readme-ov-file#unifiedpush-put-to-post-gateway

    private static final String DISTRIBUTOR_NTFY = "io.heckel.ntfy";
    private static final String UP_FAILED = "__UNIFIEDPUSH_FAILED__";

    private static long lastReceivedNotification = 0;
    private static long numOfReceivedNotifications = 0;

    public static long getLastReceivedNotification() {
        return lastReceivedNotification;
    }

    public static long getNumOfReceivedNotifications() {
        return numOfReceivedNotifications;
    }

    @Override
    public void onNewEndpoint(@NonNull PushEndpoint endpoint, @NonNull String instance) {
        Utilities.globalQueue.postRunnable(() -> {
            SharedConfig.pushStringGetTimeEnd = SystemClock.elapsedRealtime();

            String savedDistributor = UnifiedPush.getSavedDistributor(this);

            if (DISTRIBUTOR_NTFY.equals(savedDistributor)) {
                PushListenerController.sendRegistrationToServer(PushListenerController.PUSH_TYPE_SIMPLE, endpoint.getUrl());
                return;
            }

            String gateway = NaConfig.INSTANCE.getPushServiceTypeUnifiedGateway().String();
            if (gateway.isEmpty()) {
                gateway = UP_GATEWAY_DEFAULT;
            } else if (!gateway.endsWith("/")) {
                gateway += "/";
            }

            PushListenerController.sendRegistrationToServer(PushListenerController.PUSH_TYPE_SIMPLE, gateway + URLEncoder.encode(endpoint.getUrl(), StandardCharsets.UTF_8));
        });
    }

    @Override
    public void onMessage(@NonNull PushMessage message, @NonNull String instance) {
        final long receiveTime = SystemClock.elapsedRealtime();
        final CountDownLatch countDownLatch = new CountDownLatch(1);

        lastReceivedNotification = SystemClock.elapsedRealtime();
        numOfReceivedNotifications++;

        AndroidUtilities.runOnUIThread(() -> {

            FileLog.d("UP PRE INIT APP");

            ApplicationLoader.postInitApplication();

            FileLog.d("UP POST INIT APP");

            Utilities.stageQueue.postRunnable(() -> {
                FileLog.d("UP START PROCESSING");

                for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                    if (UserConfig.getInstance(a).isClientActivated()) {
                        ConnectionsManager.onInternalPushReceived(a);
                        ConnectionsManager.getInstance(a).resumeNetworkMaybe();
                    }
                }
                countDownLatch.countDown();
            });
        });
        Utilities.globalQueue.postRunnable(() -> {
            try {
                countDownLatch.await();
            } catch (Throwable ignore) {
            }

            FileLog.d("finished UP service, time = " + (SystemClock.elapsedRealtime() - receiveTime));
        });
    }

    @Override
    public void onRegistrationFailed(@NonNull FailedReason reason, @NonNull String instance) {
        FileLog.e("Failed to get endpoint: " + reason);

        SharedConfig.pushStringStatus = UP_FAILED;

        Utilities.globalQueue.postRunnable(() -> {
            SharedConfig.pushStringGetTimeEnd = SystemClock.elapsedRealtime();
            PushListenerController.sendRegistrationToServer(PushListenerController.PUSH_TYPE_SIMPLE, null);
        });
    }

    @Override
    public void onUnregistered(@NonNull String instance) {
        SharedConfig.pushStringStatus = UP_FAILED;

        Utilities.globalQueue.postRunnable(() -> {
            SharedConfig.pushStringGetTimeEnd = SystemClock.elapsedRealtime();
            PushListenerController.sendRegistrationToServer(PushListenerController.PUSH_TYPE_SIMPLE, null);
        });
    }
}
