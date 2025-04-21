package com.radolyn.ayugram.utils;

import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
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
    private static final int currentAccount = UserConfig.selectedAccount;

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

    public static void performStatusRequest(Boolean offline) {
        TL_account.updateStatus offlineRequest = new TL_account.updateStatus();
        offlineRequest.offline = offline;

        ConnectionsManager.getInstance(currentAccount).sendRequest(offlineRequest, (response, error) -> {
            if (BuildVars.LOGS_ENABLED) FileLog.d("GhostMode: Status request completed.");
        });
    }

    public InterceptResult interceptRequest(TLObject object, RequestDelegate onCompleteOrig) {
        // Block typing if disabled
        if (!NekoConfig.sendUploadProgress.Bool() && (object instanceof TLRPC.TL_messages_setTyping || object instanceof TLRPC.TL_messages_setEncryptedTyping)) {
            if (BuildVars.LOGS_ENABLED) FileLog.d("GhostMode: Blocking typing status request.");
            return InterceptResult.Blocked(onCompleteOrig);
        }

        // Force offline if online status sending disabled
        if (!NekoConfig.sendOnlinePackets.Bool() && object instanceof TL_account.updateStatus updateStatus) {
            if (BuildVars.LOGS_ENABLED) FileLog.d("GhostMode: Forcing offline status in updateStatus request.");
            updateStatus.offline = true;
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

        // Handle Mark read after sending
        RequestDelegate effectiveOnComplete = handleReadAfterSend(object, onCompleteOrig);

        // Go offline after sending
        effectiveOnComplete = handleOfflineAfterSend(object, effectiveOnComplete);

        return InterceptResult.Proceed(effectiveOnComplete);
    }

    private RequestDelegate handleReadAfterSend(TLObject object, RequestDelegate onCompleteOrig) {
        if (NekoConfig.markReadAfterSend.Bool() && !NekoConfig.sendReadMessagePackets.Bool()) {
            TLRPC.InputPeer peer = extractPeerFromSendObject(object);

            if (peer != null) {
                if (BuildVars.LOGS_ENABLED) FileLog.d("GhostMode: Wrapping callback for read-after-send.");
                var dialogId = AyuGhostUtils.getDialogId(peer);

                return (response, error) -> {
                    if (onCompleteOrig != null) {
                        Utilities.stageQueue.postRunnable(() -> onCompleteOrig.run(response, error));
                    }
                    Utilities.stageQueue.postRunnable(() -> {
                        MessagesStorage.getInstance(currentAccount).getDialogMaxMessageId(dialogId, maxId -> {
                            TLRPC.TL_messages_readHistory request = new TLRPC.TL_messages_readHistory();
                            request.peer = peer;
                            request.max_id = maxId;

                            AyuState.setAllowReadPacket(true, 1);
                            ConnectionsManager.getInstance(currentAccount).sendRequest(request, (a1, a2) -> {
                                if (BuildVars.LOGS_ENABLED) FileLog.d("GhostMode: Read-after-send request completed.");
                            });
                        });
                    });
                };
            }
        }
        return onCompleteOrig;
    }

    private RequestDelegate handleOfflineAfterSend(TLObject object, RequestDelegate onCompleteOrig) {
        if (NekoConfig.sendOfflinePacketAfterOnline.Bool() && isMessageSendRequest(object)) {
            if (BuildVars.LOGS_ENABLED) FileLog.d("GhostMode: Wrapping callback for offline-after-send.");

            return (response, error) -> {
                if (onCompleteOrig != null) {
                    Utilities.stageQueue.postRunnable(() -> onCompleteOrig.run(response, error));
                }

                if (BuildVars.LOGS_ENABLED) FileLog.d("GhostMode: Scheduling delayed offline status update.");
                Utilities.globalQueue.postRunnable(() -> {
                    performStatusRequest(true);
                }, OFFLINE_DELAY_MS);
            };
        }
        return onCompleteOrig;
    }

    private void sendFakeReadResponse(RequestDelegate onCompleteOrig) {
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

    private TLRPC.InputPeer extractPeerFromSendObject(TLObject object) {
        if (object instanceof TLRPC.TL_messages_sendMessage) {
            return ((TLRPC.TL_messages_sendMessage) object).peer;
        } else if (object instanceof TLRPC.TL_messages_sendMedia) {
            return ((TLRPC.TL_messages_sendMedia) object).peer;
        } else if (object instanceof TLRPC.TL_messages_sendMultiMedia) {
            return ((TLRPC.TL_messages_sendMultiMedia) object).peer;
        }
        return null;
    }

    private boolean isReadMessageRequest(TLObject object) {
        return object instanceof TLRPC.TL_messages_readHistory ||
                object instanceof TLRPC.TL_messages_readEncryptedHistory ||
                object instanceof TLRPC.TL_messages_readDiscussion ||
                object instanceof TLRPC.TL_messages_readMessageContents ||
                object instanceof TLRPC.TL_channels_readHistory ||
                object instanceof TLRPC.TL_channels_readMessageContents;
    }

    private boolean isReadStoriesRequest(TLObject object) {
        return object instanceof TL_stories.TL_stories_readStories ||
                object instanceof TL_stories.TL_stories_incrementStoryViews;
    }

    private boolean isMessageSendRequest(TLObject object) {
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
