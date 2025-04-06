package tw.nekomimi.nekogram.helpers;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ReplacementSpan;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.ColoredImageSpan;

import java.util.Objects;

import xyz.nextalone.nagram.NaConfig;

public class TimeStringHelper {
    public static SpannableStringBuilder deletedSpan;
    public static Drawable deletedDrawable;
    public static SpannableStringBuilder editedSpan;
    public static Drawable editedDrawable;
    public static SpannableStringBuilder channelLabelSpan;
    public static Drawable channelLabelDrawable;
    public ChatActivity.ThemeDelegate themeDelegate;

    public static CharSequence createDeletedString(MessageObject messageObject, boolean isEdited) {
        String editedStr = NaConfig.INSTANCE.getCustomEditedMessage().String();
        String editedStrFin = editedStr.isEmpty() ? getString(R.string.EditedMessage) : editedStr;
        String deletedStr = NaConfig.INSTANCE.getCustomDeletedMark().String();
        String deletedStrFin = deletedStr.isEmpty() ? getString(R.string.DeletedMessage) : deletedStr;

        createSpan();
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();

        spannableStringBuilder
                .append(messageObject.messageOwner.post_author != null ? " " : "")
                .append(NaConfig.INSTANCE.getUseDeletedIcon().Bool() ? deletedSpan : deletedStrFin)
                .append("  ")
                .append(isEdited ? (NaConfig.INSTANCE.getUseEditedIcon().Bool() ? editedSpan : editedStrFin) : "")
                .append(isEdited ? "  " : "")
                .append(LocaleController.getInstance().getFormatterDay().format((long) (messageObject.messageOwner.date) * 1000));
        return spannableStringBuilder;
    }

    public static CharSequence createEditedString(MessageObject messageObject) {
        String editedStr = NaConfig.INSTANCE.getCustomEditedMessage().String();
        String editedStrFin = editedStr.isEmpty() ? getString(R.string.EditedMessage) : editedStr;

        createSpan();
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();

        spannableStringBuilder
                .append(messageObject.messageOwner.post_author != null ? " " : "")
                .append(NaConfig.INSTANCE.getUseEditedIcon().Bool() ? editedSpan : editedStrFin)
                .append("  ")
                .append(LocaleController.getInstance().getFormatterDay().format((long) (messageObject.messageOwner.date) * 1000));
        return spannableStringBuilder;
    }

    private static void createSpan() {
        if (editedDrawable == null) {
            editedDrawable = Objects.requireNonNull(ContextCompat.getDrawable(ApplicationLoader.applicationContext, R.drawable.msg_edit_solar)).mutate();
        }
        if (editedSpan == null) {
            editedSpan = new SpannableStringBuilder("\u200B");
            editedSpan.setSpan(new ColoredImageSpan(editedDrawable, true), 0, 1, 0);
        }

        if (deletedDrawable == null) {
            deletedDrawable = Objects.requireNonNull(ContextCompat.getDrawable(ApplicationLoader.applicationContext, R.drawable.msg_delete_solar)).mutate();
        }
        if (deletedSpan == null) {
            deletedSpan = new SpannableStringBuilder("\u200B");
            deletedSpan.setSpan(new ColoredImageSpan(deletedDrawable, true), 0, 1, 0);
        }
    }

    public static SpannableStringBuilder getChannelLabelSpan() {
        if (channelLabelDrawable == null) {
            channelLabelDrawable = Objects.requireNonNull(ContextCompat.getDrawable(ApplicationLoader.applicationContext, R.drawable.channel_label_solar)).mutate();
        }
        if (channelLabelSpan == null) {
            channelLabelSpan = new SpannableStringBuilder("\u200B");
            channelLabelSpan.setSpan(new ColoredImageSpan(channelLabelDrawable, true), 0, 1, 0);
        }
        return channelLabelSpan;
    }

    public static CharSequence getColoredAdminString(View parent, TextPaint namePaint, SpannableStringBuilder sb) {
        SpannableString spannableString = new SpannableString("\u200B");
        spannableString.setSpan(new adminStringSpan(parent, namePaint, sb), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannableString;
    }

    public static class adminStringSpan extends ReplacementSpan {

        private final AnimatedTextView.AnimatedTextDrawable adminString;
        private final TextPaint namePaint;

        public adminStringSpan(View parent, TextPaint namePaint, SpannableStringBuilder sb) {
            this.namePaint = namePaint;
            adminString = new AnimatedTextView.AnimatedTextDrawable(false, false, true);
            adminString.setCallback(parent);
            float smallerDp = (2 * SharedConfig.fontSize + 10) / 3f;
            adminString.setTextSize(dp(smallerDp - 1));
            adminString.setText("");
            adminString.setGravity(Gravity.CENTER);
            setText(sb, false);
        }

        public void setText(SpannableStringBuilder sb, boolean animated) {
            adminString.setText(sb.toString(), animated);
        }

        public void setColor(int color) {
            adminString.setTextColor(color);
        }

        @Override
        public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
            return getWidth();
        }

        public int getWidth() {
            return (int) adminString.getWidth();
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float _x, int top, int _y, int bottom, @NonNull Paint paint) {
            if (this.namePaint.getColor() != adminString.getTextColor()) {
                adminString.setTextColor(this.namePaint.getColor());
            }
            canvas.save();
            canvas.translate(_x, -dp(2.0f));
            AndroidUtilities.rectTmp2.set(0, 0, (int) adminString.getCurrentWidth(), (int) adminString.getHeight());
            adminString.setBounds(AndroidUtilities.rectTmp2);
            adminString.draw(canvas);
            canvas.restore();
        }
    }

}