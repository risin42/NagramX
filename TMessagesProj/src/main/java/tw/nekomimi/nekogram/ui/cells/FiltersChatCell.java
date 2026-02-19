package tw.nekomimi.nekogram.ui.cells;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

public class FiltersChatCell extends FrameLayout {

    private final TextView textView;
    private final TextView subtitleView;
    private final BackupImageView imageView;
    private boolean needDivider;

    public FiltersChatCell(Context context) {
        super(context);

        imageView = new BackupImageView(context);
        imageView.setRoundRadius(dp(20));
        addView(imageView, LayoutHelper.createFrame(40, 40, Gravity.LEFT | Gravity.CENTER_VERTICAL, 16, 0, 0, 0));

        textView = new TextView(context);
        textView.setLines(1);
        textView.setTextSize(16);
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 68, 8, 16, 0));

        subtitleView = new TextView(context);
        subtitleView.setLines(1);
        subtitleView.setTextSize(13);
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        subtitleView.setMaxLines(1);
        subtitleView.setSingleLine(true);
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 68, 30, 16, 0));

        setWillNotDraw(false);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (needDivider) {
            canvas.drawLine(LocaleController.isRTL ? 0 : dp(68), getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }

    public void setDialog(long dialogId) {
        setDialog(dialogId, 0);
    }

    public void setDialog(long dialogId, int filterCount) {
        needDivider = true;
        String title = "";
        String subtitle = filterCount + " " + getString(R.string.RegexFiltersHeader);
        if (filterCount == 1) {
            subtitle = subtitle.replace("s", "");
        }
        if (dialogId > 0) {
            TLRPC.User user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(dialogId);
            if (user != null) {
                title = ContactsController.formatName(user.first_name, user.last_name);
                AvatarDrawable avatar = new AvatarDrawable();
                avatar.setInfo(user);
                imageView.setRoundRadius(dp(20));
                imageView.setForUserOrChat(user, avatar);
            } else {
                imageView.setImageDrawable(null);
            }
        } else {
            TLRPC.Chat chat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(-dialogId);
            if (chat != null) {
                title = chat.title;
                AvatarDrawable avatar = new AvatarDrawable();
                avatar.setInfo(chat);
                imageView.setRoundRadius(ChatObject.isForum(chat) ? dp(16) : dp(21));
                imageView.setForUserOrChat(chat, avatar);
            } else {
                imageView.setImageDrawable(null);
            }
        }

        textView.setText(title);
        subtitleView.setVisibility(VISIBLE);
        subtitleView.setText(subtitle);
        setWillNotDraw(!needDivider);
    }

    public void setUserFilter(long userId, String title, String subtitle, boolean divider) {
        needDivider = divider;
        imageView.setRoundRadius(dp(20));

        TLRPC.User user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(userId);
        AvatarDrawable avatar = new AvatarDrawable();
        if (user != null) {
            avatar.setInfo(user);
            imageView.setForUserOrChat(user, avatar);
            if (TextUtils.isEmpty(title)) {
                title = ContactsController.formatName(user.first_name, user.last_name);
            }
        } else {
            avatar.setAvatarType(AvatarDrawable.AVATAR_TYPE_ANONYMOUS);
            imageView.setImageDrawable(avatar);
        }

        if (TextUtils.isEmpty(title)) {
            title = String.valueOf(userId);
        }
        textView.setText(title);
        subtitleView.setVisibility(TextUtils.isEmpty(subtitle) ? GONE : VISIBLE);
        subtitleView.setText(subtitle);
        setWillNotDraw(!needDivider);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = dp(56) + (needDivider ? 1 : 0);
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        setMeasuredDimension(width, height);
    }
}
