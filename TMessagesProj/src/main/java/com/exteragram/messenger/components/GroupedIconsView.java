package com.exteragram.messenger.components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;

@SuppressLint("ViewConstructor")
public class GroupedIconsView extends FrameLayout {

    private final static int OPTION_DELETE = 1;
    private final static int OPTION_COPY = 3;
    private final static int OPTION_REPLY = 8;
    private final static int OPTION_EDIT = 12;



    private Context context;
    private ChatActivity chatActivity;
    public LinearLayout linearLayout;

    public GroupedIconsView(Context context, ChatActivity chatActivity,
                            boolean canDelete, boolean canEdit,
                            boolean canCopy, boolean canReply) {
        super(context);

        linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        linearLayout.setGravity(Gravity.CENTER);


        this.context = context;
        this.chatActivity = chatActivity;
        addOption(R.drawable.menu_reply, OPTION_REPLY, canReply);
        addOption(R.drawable.msg_delete, OPTION_DELETE, canDelete);
        addOption(R.drawable.msg_copy, OPTION_COPY, canCopy);
        addOption(R.drawable.msg_edit, OPTION_EDIT, canEdit);
    }

    private void addOption(int icon, int option, boolean canClick) {
        var imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setImageDrawable(ContextCompat.getDrawable(context, icon).mutate());
        imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon), PorterDuff.Mode.MULTIPLY));

        var params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );

        params.setMargins(dp(14), dp(12), dp(14), dp(12));
        imageView.setLayoutParams(params);


        linearLayout.addView(imageView);

        if (canClick) {
            imageView.setOnClickListener(v1 -> chatActivity.processSelectedOption(option));
        } else {
            imageView.setAlpha(0.4f);
        }
    }

    public static boolean useGroupedIcons() {
        // todo: implement config or so
        // i hope nagramX devs can do it
        return true;
    }
}