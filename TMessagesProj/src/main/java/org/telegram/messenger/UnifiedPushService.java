package org.telegram.messenger;

import android.os.PowerManager;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Base64;

import org.json.JSONObject;
import org.telegram.tgnet.ConnectionsManager;
import org.unifiedpush.android.connector.FailedReason;
import org.unifiedpush.android.connector.PushService;
import org.unifiedpush.android.connector.UnifiedPush;
import org.unifiedpush.android.connector.data.PushEndpoint;
import org.unifiedpush.android.connector.data.PushMessage;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;

import tw.nekomimi.nekogram.utils.WebPushDecryptor;
import xyz.nextalone.nagram.NaConfig;

@SuppressWarnings("NullableProblems")
public class UnifiedPushService extends PushService {

    public static final String UP_GATEWAY_DEFAULT = "https://p2p.belloworld.it/"; // https://github.com/Mercurygram/Mercurygram?tab=readme-ov-file#unifiedpush-put-to-post-gateway

    private static final String DISTRIBUTOR_NTFY = "io.heckel.ntfy";
    private static final String UP_FAILED = "__UNIFIEDPUSH_FAILED__";

    private static final int WAKELOCK_TIMEOUT_MS = 30_000;

    private static long lastReceivedNotification = 0;
    private static long numOfReceivedNotifications = 0;

    private static volatile byte[] webPushPrivateKey;
    private static volatile byte[] webPushPublicKey;
    private static volatile byte[] webPushAuthSecret;
    public static long getLastReceivedNotification() {
        return lastReceivedNotification;
    }

    public static long getNumOfReceivedNotifications() {
        return numOfReceivedNotifications;
    }

    @Override
    public void onNewEndpoint(PushEndpoint endpoint, String instance) {
        if (isUnifiedPushDisabled()) {
            UnifiedPush.unregister(this, instance);
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            ApplicationLoader.postInitApplication();
            Utilities.globalQueue.postRunnable(() -> {
                SharedConfig.pushStringGetTimeEnd = SystemClock.elapsedRealtime();
                ensureWebPushKeys();

                String gateway = NaConfig.getPreferences().getString(NaConfig.INSTANCE.getPushServiceTypeUnifiedGateway().getKey(), "");
                if (gateway.isEmpty()) {
                    gateway = UP_GATEWAY_DEFAULT;
                }
                if (!gateway.endsWith("/")) gateway += "/";

                try {
                    String gatewayUrl = gateway + "aesgcm?e=" + URLEncoder.encode(endpoint.getUrl(), StandardCharsets.UTF_8);
                    String p256dh = Base64.encodeToString(webPushPublicKey, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
                    String auth = Base64.encodeToString(webPushAuthSecret, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);

                    JSONObject tokenObj = new JSONObject();
                    tokenObj.put("endpoint", gatewayUrl);
                    JSONObject keys = new JSONObject();
                    keys.put("p256dh", p256dh);
                    keys.put("auth", auth);
                    tokenObj.put("keys", keys);

                    String simplePushUrl = DISTRIBUTOR_NTFY.equals(UnifiedPush.getSavedDistributor(this))
                            ? endpoint.getUrl()
                            : gateway + URLEncoder.encode(endpoint.getUrl(), StandardCharsets.UTF_8);
                    PushListenerController.sendWebPushRegistrationToServer(tokenObj.toString(), simplePushUrl);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            });
        });
    }

    @Override
    public void onMessage(PushMessage message, String instance) {
        if (isUnifiedPushDisabled()) return;

        lastReceivedNotification = SystemClock.elapsedRealtime();
        numOfReceivedNotifications++;

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "nagramx:wp");
        wakeLock.acquire(WAKELOCK_TIMEOUT_MS);

        loadWebPushKeys();

        if (webPushPrivateKey != null && webPushPublicKey != null && webPushAuthSecret != null) {
            try {
                byte[] plaintext = WebPushDecryptor.decrypt(message.getContent(), webPushPrivateKey, webPushPublicKey, webPushAuthSecret);
                String encoded = new JSONObject(new String(plaintext, StandardCharsets.UTF_8)).getString("p");
                FileLog.d("WP START PROCESSING (decrypted)");
                Utilities.globalQueue.postRunnable(() -> {
                    try {
                        PushListenerController.processRemoteMessage(PushListenerController.PUSH_TYPE_WEB, encoded, System.currentTimeMillis());
                    } finally {
                        releaseWakeLock(wakeLock);
                    }
                });
                return;
            } catch (Exception e) {
                FileLog.e("WP DECRYPT ERROR, falling back to wake-up: " + e.getMessage());
            }
        }

        AndroidUtilities.runOnUIThread(() -> {
            ApplicationLoader.postInitApplication();
            Utilities.stageQueue.postRunnable(() -> {
                try {
                    FileLog.d("UP START PROCESSING (wake-up fallback)");
                    for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                        if (UserConfig.getInstance(a).isClientActivated()) {
                            ConnectionsManager.onInternalPushReceived(a);
                            ConnectionsManager.getInstance(a).resumeNetworkMaybe();
                        }
                    }
                } finally {
                    releaseWakeLock(wakeLock);
                }
            });
        });
    }

    @Override
    public void onRegistrationFailed(FailedReason reason, String instance) {
        if (isUnifiedPushDisabled()) return;
        FileLog.e("Failed to get endpoint: " + reason);
        SharedConfig.pushStringStatus = UP_FAILED;
        Utilities.globalQueue.postRunnable(() -> {
            SharedConfig.pushStringGetTimeEnd = SystemClock.elapsedRealtime();
            PushListenerController.sendRegistrationToServer(PushListenerController.PUSH_TYPE_WEB, null);
        });
    }

    @Override
    public void onUnregistered(String instance) {
        if (isUnifiedPushDisabled()) return;
        AndroidUtilities.runOnUIThread(() -> {
            ApplicationLoader.postInitApplication();
            SharedConfig.pushStringStatus = UP_FAILED;
            Utilities.globalQueue.postRunnable(() -> {
                SharedConfig.pushStringGetTimeEnd = SystemClock.elapsedRealtime();
                PushListenerController.unregisterWebPush();
                PushListenerController.sendRegistrationToServer(PushListenerController.PUSH_TYPE_WEB, null);
                PushListenerController.unregisterSimplePush();
            });
        });
    }

    private static void releaseWakeLock(PowerManager.WakeLock wakeLock) {
        if (wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (RuntimeException ignored) {
            }
        }
    }

    private static boolean isUnifiedPushDisabled() {
        return NaConfig.getPreferences().getInt(NaConfig.INSTANCE.getPushServiceType().getKey(), 1) != 2;
    }

    private static synchronized void loadWebPushKeys() {
        if (webPushPrivateKey != null && webPushPublicKey != null && webPushAuthSecret != null) {
            return;
        }
        String priv = NaConfig.getPreferences().getString(NaConfig.INSTANCE.getPushServiceTypeUnifiedWebPushPrivateKey().getKey(), "");
        String pub = NaConfig.getPreferences().getString(NaConfig.INSTANCE.getPushServiceTypeUnifiedWebPushPublicKey().getKey(), "");
        String auth = NaConfig.getPreferences().getString(NaConfig.INSTANCE.getPushServiceTypeUnifiedWebPushAuthSecret().getKey(), "");
        if (TextUtils.isEmpty(priv) && TextUtils.isEmpty(pub) && TextUtils.isEmpty(auth)) {
            return;
        }
        try {
            if (TextUtils.isEmpty(priv) || TextUtils.isEmpty(pub) || TextUtils.isEmpty(auth)) {
                throw new IllegalArgumentException("Incomplete WebPush keys");
            }
            byte[] privateKey = Base64.decode(priv, Base64.DEFAULT);
            byte[] publicKey = Base64.decode(pub, Base64.DEFAULT);
            byte[] authSecret = Base64.decode(auth, Base64.DEFAULT);
            KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(privateKey));
            if (publicKey.length != 65 || publicKey[0] != 0x04 || authSecret.length != 16) {
                throw new IllegalArgumentException("Invalid WebPush keys");
            }
            webPushPrivateKey = privateKey;
            webPushPublicKey = publicKey;
            webPushAuthSecret = authSecret;
        } catch (Exception e) {
            webPushPrivateKey = null;
            webPushPublicKey = null;
            webPushAuthSecret = null;
            NaConfig.INSTANCE.getPushServiceTypeUnifiedWebPushPrivateKey().setConfigString("");
            NaConfig.INSTANCE.getPushServiceTypeUnifiedWebPushPublicKey().setConfigString("");
            NaConfig.INSTANCE.getPushServiceTypeUnifiedWebPushAuthSecret().setConfigString("");
            FileLog.e(e);
        }
    }

    private static synchronized void ensureWebPushKeys() {
        loadWebPushKeys();
        if (webPushPrivateKey != null && webPushPublicKey != null && webPushAuthSecret != null) {
            return;
        }
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair keyPair = kpg.generateKeyPair();
            ECPublicKey ecPub = (ECPublicKey) keyPair.getPublic();

            webPushPublicKey = WebPushDecryptor.extractRawPublicKey(ecPub);
            webPushPrivateKey = keyPair.getPrivate().getEncoded();

            byte[] secret = new byte[16];
            Utilities.random.nextBytes(secret);
            webPushAuthSecret = secret;

            NaConfig.INSTANCE.getPushServiceTypeUnifiedWebPushPrivateKey().setConfigString(Base64.encodeToString(webPushPrivateKey, Base64.DEFAULT));
            NaConfig.INSTANCE.getPushServiceTypeUnifiedWebPushPublicKey().setConfigString(Base64.encodeToString(webPushPublicKey, Base64.DEFAULT));
            NaConfig.INSTANCE.getPushServiceTypeUnifiedWebPushAuthSecret().setConfigString(Base64.encodeToString(webPushAuthSecret, Base64.DEFAULT));
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}
