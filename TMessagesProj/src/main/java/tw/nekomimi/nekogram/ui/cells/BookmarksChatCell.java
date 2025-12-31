package tw.nekomimi.nekogram.ui.cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

public class BookmarksChatCell extends FrameLayout {

    private final TextView titleView;
    private final TextView subtitleView;
    private final TextView countView;
    private final BackupImageView imageView;
    private boolean needDivider;

    public BookmarksChatCell(Context context) {
        super(context);

        imageView = new BackupImageView(context);
        imageView.setRoundRadius(dp(20));
        addView(imageView, LayoutHelper.createFrame(40, 40, Gravity.LEFT | Gravity.CENTER_VERTICAL, 16, 0, 0, 0));

        countView = new TextView(context);
        countView.setLines(1);
        countView.setTextSize(15);
        countView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        countView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
        countView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        addView(countView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 16, 0));

        titleView = new TextView(context);
        titleView.setLines(1);
        titleView.setTextSize(16);
        titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleView.setMaxLines(1);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 68, 8, 64, 0));

        subtitleView = new TextView(context);
        subtitleView.setLines(1);
        subtitleView.setTextSize(13);
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        subtitleView.setMaxLines(1);
        subtitleView.setSingleLine(true);
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 68, 30, 64, 0));

        setWillNotDraw(false);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (needDivider) {
            canvas.drawLine(LocaleController.isRTL ? 0 : dp(68), getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }

    public void setData(TLObject peer, CharSequence title, CharSequence subtitle, int bookmarkCount, boolean divider) {
        needDivider = divider;

        if (peer instanceof TLRPC.User user) {
            AvatarDrawable avatarDrawable = new AvatarDrawable();
            avatarDrawable.setInfo(user);
            imageView.setRoundRadius(dp(20));
            imageView.setForUserOrChat(user, avatarDrawable);
        } else if (peer instanceof TLRPC.Chat chat) {
            AvatarDrawable avatarDrawable = new AvatarDrawable();
            avatarDrawable.setInfo(chat);
            imageView.setRoundRadius(ChatObject.isForum(chat) ? dp(16) : dp(20));
            imageView.setForUserOrChat(chat, avatarDrawable);
        } else {
            imageView.setImageDrawable(null);
            imageView.setRoundRadius(dp(20));
        }

        titleView.setText(!TextUtils.isEmpty(title) ? title : LocaleController.getString(R.string.HiddenName));
        subtitleView.setText(subtitle);
        countView.setText(String.valueOf(Math.max(0, bookmarkCount)));

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

