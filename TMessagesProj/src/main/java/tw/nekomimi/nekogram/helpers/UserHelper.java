package tw.nekomimi.nekogram.helpers;

import android.util.SparseArray;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BaseController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

public class UserHelper extends BaseController {

    private static final UserHelper[] Instance = new UserHelper[UserConfig.MAX_ACCOUNT_COUNT];

    public UserHelper(int num) {
        super(num);
    }

    public static UserHelper getInstance(int num) {
        UserHelper localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (UserHelper.class) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new UserHelper(num);
                }
            }
        }
        return localInstance;
    }

    void resolveUser(String userName, long userId, Utilities.Callback<TLRPC.User> callback) {
        resolvePeer(userName, peer -> {
            if (peer instanceof TLRPC.TL_peerUser) {
                callback.run(peer.user_id == userId ? getMessagesController().getUser(userId) : null);
            } else {
                callback.run(null);
            }
        });
    }

    private void resolvePeer(String userName, Utilities.Callback<TLRPC.Peer> callback) {
        TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
        req.username = userName;
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response != null) {
                TLRPC.TL_contacts_resolvedPeer res = (TLRPC.TL_contacts_resolvedPeer) response;
                getMessagesController().putUsers(res.users, false);
                getMessagesController().putChats(res.chats, false);
                getMessagesStorage().putUsersAndChats(res.users, res.chats, true, true);
                callback.run(res.peer);
            } else {
                callback.run(null);
            }
        }));
    }

}
