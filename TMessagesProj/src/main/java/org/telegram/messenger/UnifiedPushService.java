package org.telegram.messenger;

import android.os.PowerManager;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Base64;

import org.telegram.tgnet.ConnectionsManager;
import org.unifiedpush.android.connector.FailedReason;
import org.unifiedpush.android.connector.PushService;
import org.unifiedpush.android.connector.data.PushEndpoint;
import org.unifiedpush.android.connector.data.PushMessage;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import it.belloworld.mercurygram.WebPushDecryptor;
import xyz.nextalone.nagram.NaConfig;

/**
 * UnifiedPush service for NagramX.
 *
 * Ported from Nagram (NextAlone/Nagram), which ported it from Mercurygram.
 * Uses Telegram's WebPush (token_type=10) with aes128gcm encryption through
 * a PUT-to-POST gateway, plus a secondary Simple Push (token_type=4) channel
 * for events that carry no payload (secret chats).
 *
 * The flow:
 *   1. ntfy/distributor gives us an endpoint URL
 *   2. We register it with Telegram via a WebPush gateway as token_type=10
 *      (gateway serializes WebPush headers into the body because UP distributors
 *      strip HTTP headers)
 *   3. Telegram sends encrypted push -> gateway -> distributor -> this service
 *   4. onMessage() decrypts locally with WebPushDecryptor and feeds the
 *      MTProto payload into the same notification pipeline that FCM uses
 *   5. If decryption fails, falls back to wake-up + resumeNetworkMaybe
 */
public class UnifiedPushService extends PushService {

    public static final String UP_GATEWAY_DEFAULT = "https://p2p.belloworld.it/";
    private static final String UP_FAILED = "__UNIFIEDPUSH_FAILED__";

    // 30 seconds — enough for cold start + decrypt + MTProto, short enough to not
    // drain battery if something hangs
    private static final int WAKELOCK_TIMEOUT_MS = 30_000;

    private static long lastReceivedNotification = 0;
    private static long numOfReceivedNotifications = 0;
    private static long numDecryptSuccess = 0;
    private static long numDecryptFailed = 0;

    // WebPush ECDH keypair + auth secret (generated on first use, persisted in NaConfig)
    public static volatile byte[] webPushPrivateKey;    // PKCS#8
    public static volatile byte[] webPushPublicKey;     // Raw 65-byte uncompressed point (04||X||Y)
    public static volatile byte[] webPushAuthSecret;    // 16-byte random

    private static void releaseWakeLock(PowerManager.WakeLock wakeLock) {
        if (wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (RuntimeException ignored) {
            }
        }
    }

    public static long getLastReceivedNotification() {
        return lastReceivedNotification;
    }

    public static long getNumOfReceivedNotifications() {
        return numOfReceivedNotifications;
    }

    public static long getNumDecryptSuccess() {
        return numDecryptSuccess;
    }

    public static long getNumDecryptFailed() {
        return numDecryptFailed;
    }

    // ── WebPush key management ──────────────────────────────────────────────

    public static synchronized void loadWebPushKeys() {
        if (webPushPrivateKey != null && webPushPublicKey != null && webPushAuthSecret != null) {
            return;
        }
        String priv = NaConfig.INSTANCE.getPushServiceTypeUnifiedWebPushPrivateKey().String();
        if (!TextUtils.isEmpty(priv)) webPushPrivateKey = Base64.decode(priv, Base64.DEFAULT);
        String pub = NaConfig.INSTANCE.getPushServiceTypeUnifiedWebPushPublicKey().String();
        if (!TextUtils.isEmpty(pub)) webPushPublicKey = Base64.decode(pub, Base64.DEFAULT);
        String auth = NaConfig.INSTANCE.getPushServiceTypeUnifiedWebPushAuthSecret().String();
        if (!TextUtils.isEmpty(auth)) webPushAuthSecret = Base64.decode(auth, Base64.DEFAULT);
    }

    public static synchronized void saveWebPushKeys() {
        if (webPushPrivateKey == null || webPushPublicKey == null || webPushAuthSecret == null) {
            return;
        }
        NaConfig.INSTANCE.getPushServiceTypeUnifiedWebPushPrivateKey().setConfigString(
                Base64.encodeToString(webPushPrivateKey, Base64.DEFAULT));
        NaConfig.INSTANCE.getPushServiceTypeUnifiedWebPushPublicKey().setConfigString(
                Base64.encodeToString(webPushPublicKey, Base64.DEFAULT));
        NaConfig.INSTANCE.getPushServiceTypeUnifiedWebPushAuthSecret().setConfigString(
                Base64.encodeToString(webPushAuthSecret, Base64.DEFAULT));
    }

    public static synchronized void ensureWebPushKeys() {
        loadWebPushKeys();
        if (webPushPrivateKey != null && webPushPublicKey != null && webPushAuthSecret != null) {
            return;
        }
        try {
            java.security.KeyPairGenerator kpg = java.security.KeyPairGenerator.getInstance("EC");
            kpg.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
            java.security.KeyPair keyPair = kpg.generateKeyPair();
            java.security.interfaces.ECPublicKey ecPub = (java.security.interfaces.ECPublicKey) keyPair.getPublic();

            // Convert Java's ECPublicKey to raw 65-byte uncompressed format (04||X||Y)
            webPushPublicKey = WebPushDecryptor.extractRawPublicKey(ecPub);
            webPushPrivateKey = keyPair.getPrivate().getEncoded(); // PKCS#8

            byte[] secret = new byte[16];
            new java.security.SecureRandom().nextBytes(secret);
            webPushAuthSecret = secret;
        } catch (Exception e) {
            FileLog.e(e);
        }
        saveWebPushKeys();
    }

    // ── UnifiedPush callbacks ───────────────────────────────────────────────

    @Override
    public void onNewEndpoint(PushEndpoint endpoint, String instance) {
        Utilities.globalQueue.postRunnable(() -> {
            SharedConfig.pushStringGetTimeEnd = SystemClock.elapsedRealtime();
            ensureWebPushKeys();

            String gateway = NaConfig.INSTANCE.getPushServiceTypeUnifiedGateway().String();
            if (gateway.isEmpty()) {
                gateway = UP_GATEWAY_DEFAULT;
            }
            if (!gateway.endsWith("/")) gateway += "/";

            try {
                // Register WebPush (token_type=10): encrypted notification payloads
                String gatewayUrl = gateway + "aesgcm?e="
                        + URLEncoder.encode(endpoint.getUrl(), StandardCharsets.UTF_8.name());
                String p256dh = Base64.encodeToString(webPushPublicKey,
                        Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
                String auth = Base64.encodeToString(webPushAuthSecret,
                        Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);

                org.json.JSONObject tokenObj = new org.json.JSONObject();
                tokenObj.put("endpoint", gatewayUrl);
                org.json.JSONObject keys = new org.json.JSONObject();
                keys.put("p256dh", p256dh);
                keys.put("auth", auth);
                tokenObj.put("keys", keys);
                // Register Simple Push (token_type=4): wake-up for secret chats and
                // other events that carry no payload. The gateway correlates PUT
                // requests with POST /aesgcm to suppress duplicates.
                String simplePushUrl = gateway
                        + URLEncoder.encode(endpoint.getUrl(), StandardCharsets.UTF_8.name());
                NaConfig.INSTANCE.getPushServiceTypeUnifiedSimple().setConfigString(simplePushUrl);

                PushListenerController.sendRegistrationToServer(
                        PushListenerController.PUSH_TYPE_WEB, tokenObj.toString());
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    @Override
    public void onMessage(PushMessage message, String instance) {
        lastReceivedNotification = SystemClock.elapsedRealtime();
        numOfReceivedNotifications++;

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "nagramx:wp");
        wakeLock.acquire(WAKELOCK_TIMEOUT_MS);

        // Load persisted keys — on a cold start (process killed by Android) all
        // static fields are null even though keys exist on disk. Without this,
        // decryption is skipped and every cold push falls back to wake-up only.
        loadWebPushKeys();

        // Try WebPush decryption first
        if (webPushPrivateKey != null && webPushPublicKey != null && webPushAuthSecret != null) {
            try {
                byte[] plaintext = WebPushDecryptor.decrypt(
                        message.getContent(), webPushPrivateKey, webPushPublicKey, webPushAuthSecret);
                String encoded = new org.json.JSONObject(new String(plaintext, StandardCharsets.UTF_8))
                        .getString("p");
                numDecryptSuccess++;
                if (BuildVars.LOGS_ENABLED) FileLog.d("WP START PROCESSING (decrypted)");

                Utilities.globalQueue.postRunnable(() -> {
                    try {
                        PushListenerController.processRemoteMessage(
                                PushListenerController.PUSH_TYPE_WEB, encoded, System.currentTimeMillis());
                    } finally {
                        releaseWakeLock(wakeLock);
                    }
                });
                return;
            } catch (Exception e) {
                numDecryptFailed++;
                if (BuildVars.LOGS_ENABLED)
                    FileLog.d("WP DECRYPT ERROR, falling back to wake-up: " + e.getMessage());
            }
        }

        // Fallback: wake up the app to fetch updates via MTProto
        AndroidUtilities.runOnUIThread(() -> {
            ApplicationLoader.postInitApplication();
            Utilities.stageQueue.postRunnable(() -> {
                try {
                    if (BuildVars.LOGS_ENABLED) FileLog.d("UP START PROCESSING (wake-up fallback)");
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
        FileLog.e("Failed to get endpoint: " + reason);
        SharedConfig.pushStringStatus = UP_FAILED;
        Utilities.globalQueue.postRunnable(() -> {
            SharedConfig.pushStringGetTimeEnd = SystemClock.elapsedRealtime();
            PushListenerController.sendRegistrationToServer(PushListenerController.PUSH_TYPE_WEB, null);
            PushListenerController.unregisterSimplePush();
        });
    }

    @Override
    public void onUnregistered(String instance) {
        SharedConfig.pushStringStatus = UP_FAILED;
        Utilities.globalQueue.postRunnable(() -> {
            SharedConfig.pushStringGetTimeEnd = SystemClock.elapsedRealtime();
            PushListenerController.sendRegistrationToServer(PushListenerController.PUSH_TYPE_WEB, null);
            PushListenerController.unregisterSimplePush();
        });
    }
}
