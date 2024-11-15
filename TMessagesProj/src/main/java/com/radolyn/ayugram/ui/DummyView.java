/*
 * This is the source code of AyuGram for Android.
 *
 * We do not and cannot prevent the use of our code,
 * but be respectful and credit the original author.
 *
 * Copyright @Radolyn, 2023
 */

package com.radolyn.ayugram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;
import org.telegram.messenger.MessageObject;

@SuppressLint("ViewConstructor")
public class DummyView extends View {
    private MessageObject messageObject;

    public DummyView(Context context) {
        super(context);

        // hacky way to make view visible
        // for `chatLayoutManager.findFirstVisibleItemPosition()`
        // ...and yes, it eats 1px, so what?
        setMinimumHeight(1);
    }

    public MessageObject getMessageObject() {
        return messageObject;
    }

    public void setMessageObject(MessageObject messageObject) {
        this.messageObject = messageObject;
    }
}
