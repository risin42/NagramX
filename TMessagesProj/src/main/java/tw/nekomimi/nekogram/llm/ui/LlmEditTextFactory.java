package tw.nekomimi.nekogram.llm.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.util.TypedValue;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;

public final class LlmEditTextFactory {

    private LlmEditTextFactory() {
    }

    public static EditTextBoldCursor createAndSetupEditText(
            Context context,
            Theme.ResourcesProvider resourcesProvider,
            String initialText,
            String hintText,
            int imeOptions,
            boolean requestFocus
    ) {
        EditTextBoldCursor editText = new EditTextBoldCursor(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(dp(64), View.MeasureSpec.EXACTLY));
            }
        };
        applyCommonStyle(editText, resourcesProvider, initialText, hintText, requestFocus);
        editText.setSingleLine(true);
        editText.setImeOptions(imeOptions);
        editText.setPadding(0, 0, 0, 0);
        return editText;
    }

    public static EditTextBoldCursor createAndSetupMultilineEditText(
            Context context,
            Theme.ResourcesProvider resourcesProvider,
            String initialText,
            String hintText,
            int imeOptions,
            boolean requestFocus
    ) {
        EditTextBoldCursor editText = new EditTextBoldCursor(context);
        editText.lineYFix = true;
        applyCommonStyle(editText, resourcesProvider, initialText, hintText, requestFocus);
        editText.setSingleLine(false);
        editText.setMinLines(1);
        editText.setImeOptions(imeOptions);
        editText.setPadding(0, 0, 0, dp(6));
        return editText;
    }

    private static void applyCommonStyle(
            EditTextBoldCursor editText,
            Theme.ResourcesProvider resourcesProvider,
            String initialText,
            String hintText,
            boolean requestFocus
    ) {
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        editText.setHintColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText, resourcesProvider));
        editText.setHeaderHintColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader, resourcesProvider));
        editText.setFocusable(true);
        editText.setLineColors(
                Theme.getColor(Theme.key_windowBackgroundWhiteInputField, resourcesProvider),
                Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated, resourcesProvider),
                Theme.getColor(Theme.key_text_RedRegular, resourcesProvider)
        );
        editText.setBackground(null);
        editText.setText(initialText != null ? initialText : "");
        editText.setHintText(hintText);
        if (requestFocus) {
            AndroidUtilities.runOnUIThread(() -> {
                editText.requestFocus();
                editText.setSelection(editText.length());
                AndroidUtilities.showKeyboard(editText);
            }, 250);
        }
    }
}
