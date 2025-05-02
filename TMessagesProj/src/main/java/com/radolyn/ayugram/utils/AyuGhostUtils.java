package com.radolyn.ayugram.utils;

import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.tgnet.tl.TL_stories;

import tw.nekomimi.nekogram.NekoConfig;

public class AyuGhostUtils {

    private static final int OFFLINE_DELAY_MS = 1000;

    public static Long getDialogId(TLRPC.InputPeer peer) {
        long dialogId;
        if (peer.chat_id != 0) {
            dialogId = -peer.chat_id;
        } else if (peer.channel_id != 0) {
            dialogId = -peer.channel_id;
        } else {
            dialogId = peer.user_id;
        }

        return dialogId;
    }

    public static Long getDialogId(TLRPC.InputChannel peer) {
        return -peer.channel_id;
    }

    public static ConnectionsManager getConnectionsManager() {
        return ConnectionsManager.getInstance(UserConfig.selectedAccount);
    }

    public static MessagesController getMessagesController() {
        return MessagesController.getInstance(UserConfig.selectedAccount);
    }

    public static MessagesStorage getMessagesStorage() {
        return MessagesStorage.getInstance(UserConfig.selectedAccount);
    }

    public static void markReadOnServer(int messageId, TLRPC.InputPeer peer, boolean internal) {
        TLObject req;
        if (peer instanceof TLRPC.TL_inputPeerChannel) {
            TLRPC.TL_channels_readHistory request = new TLRPC.TL_channels_readHistory();
            request.channel = MessagesController.getInputChannel(peer);
            request.max_id = messageId;
            req = request;
        } else {
            TLRPC.TL_messages_readHistory request = new TLRPC.TL_messages_readHistory();
            request.peer = peer;
            request.max_id = messageId;
            req = request;
        }

        AyuState.setAllowReadPacket(true, 1);
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error == null) {
                if (response instanceof TLRPC.TL_messages_affectedMessages res) {
                    getMessagesController().processNewDifferenceParams(-1, res.pts, -1, res.pts_count);
                }
                if (BuildVars.LOGS_ENABLED && internal) FileLog.d("GhostMode: Read-after-send request completed.");
                // Go offline after sending
                if (NekoConfig.sendOfflinePacketAfterOnline.Bool() && !internal) {
                    Utilities.globalQueue.postRunnable(() -> performStatusRequest(true), OFFLINE_DELAY_MS);
                }
            }
        });
    }

    public static void performStatusRequest(Boolean offline) {
        TL_account.updateStatus offlineRequest = new TL_account.updateStatus();
        offlineRequest.offline = offline;

        getConnectionsManager().sendRequest(offlineRequest, (response, error) -> {
            if (BuildVars.LOGS_ENABLED) FileLog.d("GhostMode: Status request completed.");
        });
    }

    public static InterceptResult interceptRequest(TLObject object, RequestDelegate onCompleteOrig) {
        // Block typing if disabled
        if (!NekoConfig.sendUploadProgress.Bool() && (object instanceof TLRPC.TL_messages_setTyping || object instanceof TLRPC.TL_messages_setEncryptedTyping)) {
            if (BuildVars.LOGS_ENABLED) FileLog.d("GhostMode: Blocking typing status request.");
            return InterceptResult.Blocked(onCompleteOrig);
        }

        // Block read receipts if disabled
        if (!NekoConfig.sendReadMessagePackets.Bool() && (isReadMessageRequest(object))) {
            if (!AyuState.getAllowReadPacket()) {
                if (BuildVars.LOGS_ENABLED) FileLog.d("GhostMode: Blocking read status request and sending fake response.");
                sendFakeReadResponse(onCompleteOrig);
                return InterceptResult.Blocked(onCompleteOrig);
            }
        }
        if (!NekoConfig.sendReadStoriesPackets.Bool() && isReadStoriesRequest(object)) {
            if (BuildVars.LOGS_ENABLED) FileLog.d("GhostMode: Blocking story read request.");
            return InterceptResult.Blocked(onCompleteOrig);
        }

        // Force offline if online status sending disabled
        if (!NekoConfig.sendOnlinePackets.Bool() && object instanceof TL_account.updateStatus updateStatus) {
            if (BuildVars.LOGS_ENABLED) FileLog.d("GhostMode: Forcing offline status in updateStatus request.");
            updateStatus.offline = true;
        }

        // Handle Mark read after sending
        handleReadAfterSend(object);

        // Go offline after sending
        RequestDelegate effectiveOnComplete = handleOfflineAfterSend(object, onCompleteOrig);

        return InterceptResult.Proceed(effectiveOnComplete);
    }

    private static void handleReadAfterSend(TLObject object) {
        if (NekoConfig.markReadAfterSend.Bool() && !NekoConfig.sendReadMessagePackets.Bool()) {
            TLRPC.InputPeer peer = extractPeerFromSendObject(object);

            if (peer != null) {
                var dialogId = AyuGhostUtils.getDialogId(peer);
                getMessagesStorage().getStorageQueue().postRunnable(() ->
                    getMessagesStorage().getDialogMaxMessageId(dialogId, maxId ->
                        markReadOnServer(maxId, peer, true)
                    )
                );
            }
        }
    }

    private static RequestDelegate handleOfflineAfterSend(TLObject object, RequestDelegate onCompleteOrig) {
        if (NekoConfig.sendOfflinePacketAfterOnline.Bool() && isMessageSendRequest(object)) {
            if (BuildVars.LOGS_ENABLED) FileLog.d("GhostMode: Wrapping callback for offline-after-send.");

            return (response, error) -> {
                if (onCompleteOrig != null) {
                    Utilities.stageQueue.postRunnable(() -> onCompleteOrig.run(response, error));
                }

                if (BuildVars.LOGS_ENABLED) FileLog.d("GhostMode: Scheduling delayed offline status update.");
                Utilities.globalQueue.postRunnable(() -> performStatusRequest(true), OFFLINE_DELAY_MS);
            };
        }
        return onCompleteOrig;
    }

    private static void sendFakeReadResponse(RequestDelegate onCompleteOrig) {
        var fakeRes = new TLRPC.TL_messages_affectedMessages();
        fakeRes.pts = -1;
        fakeRes.pts_count = 0;
        Utilities.stageQueue.postRunnable(() -> {
            try {
                if (onCompleteOrig != null) {
                    onCompleteOrig.run(fakeRes, null);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private static TLRPC.InputPeer extractPeerFromSendObject(TLObject object) {
        if (object instanceof TLRPC.TL_messages_sendMessage) {
            return ((TLRPC.TL_messages_sendMessage) object).peer;
        } else if (object instanceof TLRPC.TL_messages_sendMedia) {
            return ((TLRPC.TL_messages_sendMedia) object).peer;
        } else if (object instanceof TLRPC.TL_messages_sendMultiMedia) {
            return ((TLRPC.TL_messages_sendMultiMedia) object).peer;
        }
        return null;
    }

    private static boolean isReadMessageRequest(TLObject object) {
        return object instanceof TLRPC.TL_messages_readHistory ||
                object instanceof TLRPC.TL_messages_readEncryptedHistory ||
                object instanceof TLRPC.TL_messages_readDiscussion ||
                object instanceof TLRPC.TL_messages_readMessageContents ||
                object instanceof TLRPC.TL_channels_readHistory;
    }

    private static boolean isReadStoriesRequest(TLObject object) {
        return object instanceof TL_stories.TL_stories_readStories ||
                object instanceof TL_stories.TL_stories_incrementStoryViews;
    }

    private static boolean isMessageSendRequest(TLObject object) {
        return object instanceof TLRPC.TL_messages_sendMessage ||
                object instanceof TLRPC.TL_messages_sendMedia ||
                object instanceof TLRPC.TL_messages_sendMultiMedia;
    }

    public static class InterceptResult {
        public final boolean blockRequest;
        public final RequestDelegate effectiveOnComplete;

        InterceptResult(boolean block, RequestDelegate onComplete) {
            this.blockRequest = block;
            this.effectiveOnComplete = onComplete;
        }

        public static InterceptResult Blocked(RequestDelegate originalOnComplete) {
            return new InterceptResult(true, originalOnComplete);
        }

        public static InterceptResult Proceed(RequestDelegate effectiveOnComplete) {
            return new InterceptResult(false, effectiveOnComplete);
        }
    }
}
