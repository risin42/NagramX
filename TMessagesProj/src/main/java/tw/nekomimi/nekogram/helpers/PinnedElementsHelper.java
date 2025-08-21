package tw.nekomimi.nekogram.helpers;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;

import java.util.ArrayList;

import tw.nekomimi.nekogram.ui.PinnedReactionsActivity;
import xyz.nextalone.nagram.NaConfig;

public class PinnedElementsHelper {

    public static ArrayList<ReactionsLayoutInBubble.VisibleReaction> getFavoriteReactions(boolean isChannel) {
        ArrayList<ReactionsLayoutInBubble.VisibleReaction> reactions = new ArrayList<>();

        if ((isChannel && !NaConfig.INSTANCE.getUsePinnedReactionsChannels().Bool() || (!isChannel && !NaConfig.INSTANCE.getUsePinnedReactionsChats().Bool()))) {
            return reactions;
        }

        try {
            String value = isChannel ? NaConfig.INSTANCE.getPinnedReactionsChannels().String() : NaConfig.INSTANCE.getPinnedReactionsChats().String();
            JSONArray list = new JSONArray(new JSONTokener(value));

            int successHandled = 0;
            for (int i = 0; i < list.length(); i++) {
                try {
                    ReactionsLayoutInBubble.VisibleReaction visibleReaction = new ReactionsLayoutInBubble.VisibleReaction();
                    JSONObject object = list.getJSONObject(i);
                    if (object.has("emoticon")) {
                        visibleReaction.emojicon = object.getString("emoticon");
                        visibleReaction.hash = visibleReaction.emojicon.hashCode();
                        successHandled++;
                    } else if (object.has("document_id")) {
                        visibleReaction.documentId = object.getLong("document_id");
                        visibleReaction.hash = visibleReaction.documentId;
                        successHandled++;
                    }
                    reactions.add(visibleReaction);
                } catch (JSONException ignored) {
                }

                if (successHandled >= PinnedReactionsActivity.PINNED_REACTIONS_LIMIT) {
                    break;
                }
            }
        } catch (JSONException ignored) {
        }

        return reactions;
    }

    public static int getFavoriteReactionsCount() {
        return getFavoriteReactions(true).size() + getFavoriteReactions(false).size();
    }

}
