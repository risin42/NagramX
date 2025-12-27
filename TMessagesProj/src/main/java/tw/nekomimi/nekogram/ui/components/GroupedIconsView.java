package tw.nekomimi.nekogram.ui.components;

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

import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.ScaleStateListAnimator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import xyz.nextalone.nagram.NaConfig;

@SuppressLint("ViewConstructor")
public class GroupedIconsView extends FrameLayout {

    private final static int OPTION_DELETE = 1;
    private final static int OPTION_FORWARD = 2;
    private final static int OPTION_FORWARD_NOQUOTE = 2011;
    private final static int OPTION_COPY = 3;
    private final static int OPTION_COPY_PHOTO = 150;
    private final static int OPTION_COPY_PHOTO_AS_STICKER = 151;
    private final static int OPTION_COPY_LINK = 22;
    private final static int OPTION_COPY_LINK_PM = 2025;
    private final static int OPTION_REPLY = 8;
    private final static int OPTION_REPLY_PM = 2033;
    private final static int OPTION_EDIT = 12;

    private final Context context;
    private final ChatActivity chatActivity;
    public LinearLayout linearLayout;

    public GroupedIconsView(Context context, ChatActivity chatActivity, MessageObject messageObject,
                            boolean allowReply, boolean allowReplyPm,
                            boolean allowEdit, boolean allowDelete, boolean allowForward,
                            boolean allowCopy, boolean allowCopyPhoto, boolean allowCopyLink, boolean allowCopyLinkPm) {
        super(context);

        linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        linearLayout.setGravity(Gravity.CENTER);

        this.context = context;
        this.chatActivity = chatActivity;

        List<OptionConfig> options = new ArrayList<>();

        // button 1: reply
        options.add(new OptionConfig(R.drawable.menu_reply, OPTION_REPLY, OPTION_REPLY_PM, allowReply, allowReplyPm));

        // button 2: copy text > copy photo > copy sticker > copy link
        if (allowCopy) {
            if (!allowCopyPhoto && messageObject != null && messageObject.isPhoto() && messageObject.isWebpage()) {
                options.add(new OptionConfig(R.drawable.msg_copy, OPTION_COPY, OPTION_COPY_PHOTO));
            } else if (allowCopyLink) {
                options.add(new OptionConfig(R.drawable.msg_copy, OPTION_COPY, OPTION_COPY_LINK));
            } else if (allowCopyLinkPm) {
                options.add(new OptionConfig(R.drawable.msg_copy, OPTION_COPY, OPTION_COPY_LINK_PM));
            } else {
                options.add(new OptionConfig(R.drawable.msg_copy, OPTION_COPY));
            }
        } else if (allowCopyPhoto) {
            if (messageObject != null && !messageObject.isSticker()) {
                options.add(new OptionConfig(R.drawable.msg_copy_photo, OPTION_COPY_PHOTO, OPTION_COPY_PHOTO_AS_STICKER));
            } else {
                if (allowCopyLink) {
                    options.add(new OptionConfig(R.drawable.msg_copy_photo, OPTION_COPY_PHOTO, OPTION_COPY_LINK));
                } else if (allowCopyLinkPm) {
                    options.add(new OptionConfig(R.drawable.msg_copy_photo, OPTION_COPY_PHOTO, OPTION_COPY_LINK_PM));
                } else {
                    options.add(new OptionConfig(R.drawable.msg_copy_photo, OPTION_COPY_PHOTO));
                }
            }
        } else if (allowCopyLink && allowDelete) {
            options.add(new OptionConfig(R.drawable.msg_link, OPTION_COPY_LINK));
        } else if (allowCopyLinkPm) {
            options.add(new OptionConfig(R.drawable.msg_link, OPTION_COPY_LINK_PM));
        } else {
            options.add(new OptionConfig(R.drawable.msg_copy, OPTION_COPY, false));
        }

        // button 3: delete > copy photo > copy link
        if (allowDelete) {
            options.add(new OptionConfig(R.drawable.msg_delete, OPTION_DELETE));
        } else if (allowCopy && allowCopyPhoto) {
            options.add(new OptionConfig(R.drawable.msg_copy_photo, OPTION_COPY_PHOTO, OPTION_COPY_PHOTO_AS_STICKER));
        } else {
            options.add(new OptionConfig(R.drawable.msg_link, OPTION_COPY_LINK, allowCopyLink));
        }

        // button 4: edit > forward
        if (allowEdit) {
            options.add(new OptionConfig(R.drawable.msg_edit, OPTION_EDIT));
        } else {
            options.add(new OptionConfig(R.drawable.msg_forward_noquote, OPTION_FORWARD, OPTION_FORWARD_NOQUOTE, allowForward, allowForward));
        }

        for (OptionConfig config : options) {
            addOption(config);
        }
    }

    public static boolean useGroupedIcons() {
        return NaConfig.INSTANCE.getGroupedMessageMenu().Bool();
    }

    private void addOption(OptionConfig config) {
        var imageView = new ImageView(context);
        imageView.setPadding(dp(8), dp(8), dp(8), dp(8));
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setImageDrawable(Objects.requireNonNull(ContextCompat.getDrawable(context, config.iconResId)).mutate());
        imageView.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_CIRCLE_20DP));
        imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon), PorterDuff.Mode.MULTIPLY));
        ScaleStateListAnimator.apply(imageView, .1f, 1.5f);

        var params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );

        params.setMargins(dp(6), dp(6), dp(6), dp(6));
        imageView.setLayoutParams(params);

        linearLayout.addView(imageView);

        if (config.isEnabled) {
            imageView.setOnClickListener(v1 -> chatActivity.processSelectedOption(config.shortPressOptionId));
            if (config.longPressOptionId != null && config.isLongClickEnabled) {
                imageView.setOnLongClickListener(v1 -> {
                    chatActivity.processSelectedOption(config.longPressOptionId);
                    return true;
                });
            }
        } else {
            imageView.setAlpha(0.4f);
        }
    }

    public static class OptionConfig {
        final int iconResId;
        final int shortPressOptionId;
        final Integer longPressOptionId;
        final boolean isEnabled;
        final boolean isLongClickEnabled;

        public OptionConfig(int iconResId, int shortPressOptionId) {
            this(iconResId, shortPressOptionId, null, true, false);
        }

        public OptionConfig(int iconResId, int shortPressOptionId, boolean isEnabled) {
            this(iconResId, shortPressOptionId, null, isEnabled, false);
        }

        public OptionConfig(int iconResId, int shortPressOptionId, int longPressOptionId) {
            this(iconResId, shortPressOptionId, longPressOptionId, true, true);
        }

        public OptionConfig(int iconResId, int shortPressOptionId, Integer longPressOptionId, boolean isEnabled, boolean isLongClickEnabled) {
            this.iconResId = iconResId;
            this.shortPressOptionId = shortPressOptionId;
            this.longPressOptionId = longPressOptionId;
            this.isEnabled = isEnabled;
            this.isLongClickEnabled = isLongClickEnabled;
        }
    }
}