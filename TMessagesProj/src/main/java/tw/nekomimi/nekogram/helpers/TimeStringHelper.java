package tw.nekomimi.nekogram.helpers;

import static org.telegram.messenger.LocaleController.getString;

import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.UpdateAppearance;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.TranslateController;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.ColoredImageSpan;

import java.util.Locale;
import java.util.Objects;

import tw.nekomimi.nekogram.ui.icons.IconsResources;
import xyz.nextalone.nagram.NaConfig;

public class TimeStringHelper {
    public static SpannableStringBuilder deletedSpan;
    public static Drawable deletedDrawable;
    public static SpannableStringBuilder editedSpan;
    public static Drawable editedDrawable;
    public static SpannableStringBuilder channelLabelSpan;
    public static Drawable channelLabelDrawable;
    public static SpannableStringBuilder translatedSpan;
    public static Drawable translatedDrawable;
    public static SpannableStringBuilder arrowSpan;
    public static Drawable arrowDrawable;
    public static SpannableStringBuilder forwardsSpan;
    public static Drawable forwardsDrawable;
    public ChatActivity.ThemeDelegate themeDelegate;

    public static CharSequence createDeletedString(MessageObject messageObject, boolean isEdited, boolean isTranslated) {
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
                .append(isTranslated ? createTranslatedString(messageObject, true) : "")
                .append(isTranslated ? "  " : "")
                .append(LocaleController.getInstance().getFormatterDay().format((long) (messageObject.messageOwner.date) * 1000));
        return spannableStringBuilder;
    }

    public static CharSequence createEditedString(MessageObject messageObject, boolean isTranslated) {
        String editedStr = NaConfig.INSTANCE.getCustomEditedMessage().String();
        String editedStrFin = editedStr.isEmpty() ? getString(R.string.EditedMessage) : editedStr;

        createSpan();
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();

        spannableStringBuilder
                .append(messageObject.messageOwner.post_author != null ? " " : "")
                .append(NaConfig.INSTANCE.getUseEditedIcon().Bool() ? editedSpan : editedStrFin)
                .append("  ")
                .append(isTranslated ? createTranslatedString(messageObject, true) : "")
                .append(isTranslated ? "  " : "")
                .append(LocaleController.getInstance().getFormatterDay().format((long) (messageObject.messageOwner.date) * 1000));
        return spannableStringBuilder;
    }

    public static CharSequence createTranslatedString(MessageObject messageObject, boolean internal) {
        createSpan();
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();

        if (canShowLanguage(messageObject)) {
            spannableStringBuilder
                    .append(Locale.forLanguageTag(messageObject.messageOwner.originalLanguage).getDisplayName())
                    .append(" ")
                    .append(arrowSpan)
                    .append(" ")
                    .append(Locale.forLanguageTag(messageObject.messageOwner.translatedToLanguage).getDisplayName())
                    .append(internal ? "" : "  ")
                    .append(internal ? "" : LocaleController.getInstance().getFormatterDay().format((long) (messageObject.messageOwner.date) * 1000));
        } else {
            spannableStringBuilder
                    .append(internal || messageObject.messageOwner.post_author == null ? "" : " ")
                    .append(translatedSpan)
                    .append(internal ? "" : "  ")
                    .append(internal ? "" : LocaleController.getInstance().getFormatterDay().format((long) (messageObject.messageOwner.date) * 1000));
        }
        return spannableStringBuilder;
    }

    private static boolean canShowLanguage(MessageObject messageObject) {
        String fromCode = messageObject.messageOwner.originalLanguage;
        String toCode = messageObject.messageOwner.translatedToLanguage;
        if (TextUtils.isEmpty(fromCode) || TextUtils.isEmpty(toCode)) {
            return false;
        }
        if (messageObject.messageOwner.originalLanguage.equals(TranslateController.UNKNOWN_LANGUAGE)) {
            return false;
        }
        if (messageObject.messageOwner.post_author != null) {
            return false;
        }
        return MessagesController.getInstance(UserConfig.selectedAccount).getTranslateController().isManualTranslated(messageObject);
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

        if (translatedDrawable == null) {
            if (NaConfig.INSTANCE.getIconReplacements().Int() == IconsResources.ICON_REPLACE_SOLAR) {
                translatedDrawable = Objects.requireNonNull(ContextCompat.getDrawable(ApplicationLoader.applicationContext, R.drawable.msg_translate_solar_12)).mutate();
            } else {
                translatedDrawable = Objects.requireNonNull(ContextCompat.getDrawable(ApplicationLoader.applicationContext, R.drawable.msg_translate_12)).mutate();
            }
        }
        if (translatedSpan == null) {
            translatedSpan = new SpannableStringBuilder("\u200B");
            translatedSpan.setSpan(new ColoredImageSpan(translatedDrawable, true), 0, 1, 0);
        }

        if (arrowDrawable == null) {
            arrowDrawable = Objects.requireNonNull(ContextCompat.getDrawable(ApplicationLoader.applicationContext, R.drawable.search_arrow));
        }
        if (arrowSpan == null) {
            arrowSpan = new SpannableStringBuilder("\u200B");
            arrowSpan.setSpan(new ColoredImageSpan(arrowDrawable, true), 0, 1, 0);
        }

        if (forwardsDrawable == null) {
            forwardsDrawable = Objects.requireNonNull(ContextCompat.getDrawable(ApplicationLoader.applicationContext, R.drawable.forwards_solar)).mutate();
        }
        if (forwardsSpan == null) {
            forwardsSpan = new SpannableStringBuilder("\u200B");
            forwardsSpan.setSpan(new ColoredImageSpan(forwardsDrawable, true), 0, 1, 0);
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

    public static CharSequence getColoredAdminString(TextPaint namePaint, SpannableStringBuilder sb) {
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(sb);
        spannableStringBuilder.setSpan(new AdminTitleColorSpan(namePaint), 0, spannableStringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannableStringBuilder;
    }

    private static class AdminTitleColorSpan extends CharacterStyle implements UpdateAppearance {

        private final TextPaint namePaint;

        private AdminTitleColorSpan(TextPaint namePaint) {
            this.namePaint = namePaint;
        }

        @Override
        public void updateDrawState(TextPaint tp) {
            if (namePaint == null) {
                return;
            }
            int alpha = tp.getAlpha();
            tp.setColor(namePaint.getColor());
            tp.setAlpha(alpha);
        }
    }

}
