/*
 * This is the source code of OctoGram for Android
 * It is licensed under GNU GPL v2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright OctoGram, 2023-2025.
 */

package tw.nekomimi.nekogram.transtale;

import android.text.Editable;
import android.text.Spannable;
import android.text.Spanned;

import org.telegram.messenger.utils.CopyUtilities;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.xml.sax.Attributes;

public class CustomElementHandler implements HTMLTagAttributesHandler.TagHandler {

    @Override
    public boolean handleTag(boolean opening, String tag, Editable output, Attributes attributes) {
        if (tag.equalsIgnoreCase("tg-emoji")) {
            if (opening) {
                String emojiIdString = CopyUtilities.HTMLTagAttributesHandler.getValue(attributes, "emoji-id");
                if (emojiIdString != null) {
                    long documentId = Long.parseLong(emojiIdString);
                    output.setSpan(new AnimatedEmojiSpan(documentId, null), output.length(), output.length(), Spanned.SPAN_MARK_MARK);
                    return true;
                }
            } else {
                AnimatedEmojiSpan obj = getLast(output);
                if (obj != null) {
                    int where = output.getSpanStart(obj);
                    output.removeSpan(obj);
                    if (where != output.length()) {
                        output.setSpan(obj, where, output.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private <T> T getLast(Editable text) {
        T[] objs = text.getSpans(0, text.length(), (Class<T>) AnimatedEmojiSpan.class);
        if (objs.length != 0) {
            for (int i = objs.length; i > 0; i--) {
                if (text.getSpanFlags(objs[i - 1]) == Spannable.SPAN_MARK_MARK) {
                    return objs[i - 1];
                }
            }
        }
        return null;
    }
}