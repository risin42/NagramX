package tw.nekomimi.nekogram.filters;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Paint.ColorPickerBottomSheet;

import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import tw.nekomimi.nekogram.utils.LocaleUtil;


public class RegexFilterEditActivity extends BaseFragment {

    private final static int done_button = 1;

    private final int filterIdx;
    private final AyuFilter.FilterModel filterModel;
    private final long targetDialogId;
    private final int chatFilterIdx;
    private final String prefillText;
    private final boolean canSelectSharedTarget;
    private boolean caseInsensitive;
    private boolean addToSharedFilters;
    private int filterAction; // hide, spoiler all, spoiler match
    private int spoilerColor = 0xFFFFFFFF; // default spoiler color, white

    private EditTextBoldCursor editField;
    private View doneButton;
    private TextView helpTextView;
    private TextView errorTextView;

    private TextCheckCell caseInsensitiveButtonView;
    private TextCheckCell addToSharedFiltersButtonView;
    private TextCheckCell spoilerInsteadOfHideButtonView;
    private TextCheckCell spoilerMatchOnlyButtonView;
    private View spoilerColorRow;

    public RegexFilterEditActivity() {
        filterIdx = -1;
        filterModel = null;
        caseInsensitive = true;
        filterAction = AyuFilter.FilterModel.ACTION_HIDE;
        targetDialogId = 0L;
        chatFilterIdx = -1;
        prefillText = null;
        canSelectSharedTarget = false;
        addToSharedFilters = false;
    }

    public RegexFilterEditActivity(long dialogId) {
        filterIdx = -1;
        filterModel = null;
        caseInsensitive = true;
        filterAction = AyuFilter.FilterModel.ACTION_HIDE;
        targetDialogId = dialogId;
        chatFilterIdx = -1;
        prefillText = null;
        canSelectSharedTarget = false;
        addToSharedFilters = false;
    }

    public RegexFilterEditActivity(long dialogId, String prefillText) {
        filterIdx = -1;
        filterModel = null;
        caseInsensitive = true;
        filterAction = AyuFilter.FilterModel.ACTION_HIDE;
        targetDialogId = dialogId;
        chatFilterIdx = -1;
        this.prefillText = prefillText;
        canSelectSharedTarget = true; // text selection
        addToSharedFilters = false;
    }

    public RegexFilterEditActivity(long dialogId, int chatFilterIdx) {
        this.filterIdx = -1;
        this.targetDialogId = dialogId;
        this.chatFilterIdx = chatFilterIdx;
        this.filterModel = AyuFilter.getChatFiltersForDialog(dialogId).size() > chatFilterIdx && chatFilterIdx >= 0 ? AyuFilter.getChatFiltersForDialog(dialogId).get(chatFilterIdx) : null;
        this.caseInsensitive = this.filterModel == null || this.filterModel.caseInsensitive;
        this.filterAction = this.filterModel != null ? this.filterModel.filterAction : AyuFilter.FilterModel.ACTION_HIDE;
        this.spoilerColor = this.filterModel != null ? this.filterModel.spoilerColor : 0xFFFFFFFF;
        this.prefillText = null;
        this.canSelectSharedTarget = false;
        this.addToSharedFilters = false;
    }

    public RegexFilterEditActivity(int filterIdx) {
        this.filterIdx = filterIdx; // use -1 to CREATE, not EDIT
        this.filterModel = AyuFilter.getRegexFilters().get(filterIdx);
        this.caseInsensitive = filterModel.caseInsensitive;
        this.filterAction = filterModel.filterAction;
        this.spoilerColor = filterModel.spoilerColor;
        this.targetDialogId = 0L;
        this.chatFilterIdx = -1;
        this.prefillText = null;
        this.canSelectSharedTarget = false;
        this.addToSharedFilters = false;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View createView(Context context) {
        boolean isEdit = (filterIdx != -1) || (chatFilterIdx != -1);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(!isEdit ? R.string.RegexFiltersAdd : R.string.RegexFiltersEdit));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == done_button) {
                    var text = editField.getText().toString();

                    if (TextUtils.isEmpty(text)) {
                        showError();
                        return;
                    }

                    try {
                        Pattern.compile(text);
                    } catch (PatternSyntaxException e) {
                        var errorText = e.getMessage();
                        if (!TextUtils.isEmpty(errorText)) {
                            errorText = errorText.replace(text, "");
                        }

                        errorTextView.setText(LocaleUtil.INSTANCE.htmlToString("<b>" + errorText + "</b>"));
                        showError();
                        return;
                    }

                    // If editing a chat-specific filter, update that entry and return.
                    int savedColor = filterAction != AyuFilter.FilterModel.ACTION_HIDE ? spoilerColor : 0xFFFFFFFF;

                    if (chatFilterIdx != -1 && targetDialogId != 0L) {
                        AyuFilter.editChatFilter(targetDialogId, chatFilterIdx, text, caseInsensitive, filterAction, savedColor);
                    } else if (filterIdx != -1) {
                        // editing shared filter
                        AyuFilter.editFilter(filterIdx, text, caseInsensitive, filterAction, savedColor);
                    } else {
                        // creating a new filter (shared or chat-scoped)
                        if (targetDialogId != 0L) {
                            if (canSelectSharedTarget && addToSharedFilters) {
                                AyuFilter.addFilter(text, caseInsensitive, filterAction, savedColor);
                            } else {
                                AyuFilter.addChatFilter(targetDialogId, text, caseInsensitive, filterAction, savedColor);
                            }
                        } else {
                            AyuFilter.addFilter(text, caseInsensitive, filterAction, savedColor);
                        }
                    }

                    finishFragment();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        doneButton = menu.addItemWithWidth(done_button, R.drawable.ic_ab_done, dp(56));

        fragmentView = new LinearLayout(context);
        LinearLayout linearLayout = (LinearLayout) fragmentView;
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        fragmentView.setOnTouchListener((v, event) -> true);

        editField = new EditTextBoldCursor(context);
        editField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        editField.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        editField.setBackground(null);
        editField.setLineColors(Theme.getColor(Theme.key_windowBackgroundWhiteInputField), Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated), Theme.getColor(Theme.key_text_RedRegular));
        editField.setPadding(0, 0, 0, dp(6));
        editField.setCursorColor(Theme.getColor(Theme.key_chat_TextSelectionCursor));
        editField.setCursorSize(dp(20));
        editField.setCursorWidth(1.5f);
        editField.setHandlesColor(Theme.getColor(Theme.key_chat_TextSelectionCursor));
        editField.setHighlightColor(Theme.getColor(Theme.key_chat_inTextSelectionHighlight));
        editField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                doneButton.setEnabled(!TextUtils.isEmpty(s));

                if (errorTextView != null) {
                    errorTextView.setText("");
                }
            }
        });

        if (filterModel != null) {
            editField.setText(filterModel.regex);
            editField.setSelection(editField.length());
        } else if (prefillText != null) {
            editField.setText(prefillText);
            editField.setSelection(editField.length());
        }

        linearLayout.addView(editField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 24, 24, 24, 0));

        helpTextView = new TextView(context);
        helpTextView.setFocusable(true);
        helpTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        helpTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText8));
        helpTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        helpTextView.setText(LocaleUtil.INSTANCE.htmlToString(getString(R.string.RegexFiltersAddDescription)));
        helpTextView.setLinksClickable(true);
        helpTextView.setMovementMethod(LinkMovementMethod.getInstance());
        linearLayout.addView(helpTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 24, 10, 24, 0));

        errorTextView = new TextView(context);
        errorTextView.setFocusable(true);
        errorTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        errorTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText8));
        errorTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        errorTextView.setText("");
        linearLayout.addView(errorTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 24, 10, 24, 0));

        if (!isEdit && targetDialogId != 0L && canSelectSharedTarget) {
            addToSharedFiltersButtonView = new TextCheckCell(context);
            addToSharedFiltersButtonView.setFocusable(true);
            addToSharedFiltersButtonView.setTextAndCheck(getString(R.string.RegexFiltersTextSelectionAddtoShared), addToSharedFilters, true);
            addToSharedFiltersButtonView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
            addToSharedFiltersButtonView.setOnClickListener((v) -> {
                boolean checked = !addToSharedFiltersButtonView.isChecked();
                addToSharedFiltersButtonView.setChecked(checked);
                addToSharedFilters = checked;
            });
            linearLayout.addView(addToSharedFiltersButtonView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 24, 10, 24, 0));
        }

        caseInsensitiveButtonView = new TextCheckCell(context);
        caseInsensitiveButtonView.setFocusable(true);
        caseInsensitiveButtonView.setTextAndCheck(getString(R.string.RegexFiltersCaseInsensitive), caseInsensitive, true);
        caseInsensitiveButtonView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        caseInsensitiveButtonView.setOnClickListener((v) -> {
            boolean checked = !caseInsensitiveButtonView.isChecked();
            caseInsensitiveButtonView.setChecked(checked);
            caseInsensitive = checked;
        });
        linearLayout.addView(caseInsensitiveButtonView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 24, 10, 24, 0));

        // Toggle to mask message instead of hiding
        spoilerInsteadOfHideButtonView = new TextCheckCell(context);
        spoilerInsteadOfHideButtonView.setFocusable(true);
        spoilerInsteadOfHideButtonView.setTextAndCheck(getString(R.string.RegexFiltersSpoilerInsteadOfHide), filterAction != AyuFilter.FilterModel.ACTION_HIDE, true);
        spoilerInsteadOfHideButtonView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        spoilerInsteadOfHideButtonView.setOnClickListener((v) -> {
            boolean checked = !spoilerInsteadOfHideButtonView.isChecked();
            spoilerInsteadOfHideButtonView.setChecked(checked);
            if (!checked) {
                filterAction = AyuFilter.FilterModel.ACTION_HIDE;
                spoilerMatchOnlyButtonView.setVisibility(View.GONE);
                spoilerColorRow.setVisibility(View.GONE);
            } else {
                filterAction = spoilerMatchOnlyButtonView.isChecked()
                        ? AyuFilter.FilterModel.ACTION_SPOILER_MATCH
                        : AyuFilter.FilterModel.ACTION_SPOILER_ALL;
                spoilerMatchOnlyButtonView.setVisibility(View.VISIBLE);
                spoilerColorRow.setVisibility(View.VISIBLE);
            }
        });
        linearLayout.addView(spoilerInsteadOfHideButtonView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 24, 10, 24, 0));

        // when mask message is enabled, show a sub option to only mask matched word
        spoilerMatchOnlyButtonView = new TextCheckCell(context);
        spoilerMatchOnlyButtonView.setFocusable(true);
        spoilerMatchOnlyButtonView.setTextAndCheck(getString(R.string.RegexFiltersSpoilerMatchOnly), filterAction == AyuFilter.FilterModel.ACTION_SPOILER_MATCH, true);
        spoilerMatchOnlyButtonView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        spoilerMatchOnlyButtonView.setVisibility(filterAction == AyuFilter.FilterModel.ACTION_HIDE ? View.GONE : View.VISIBLE);
        spoilerMatchOnlyButtonView.setOnClickListener((v) -> {
            boolean checked = !spoilerMatchOnlyButtonView.isChecked();
            spoilerMatchOnlyButtonView.setChecked(checked);
            filterAction = checked ? AyuFilter.FilterModel.ACTION_SPOILER_MATCH : AyuFilter.FilterModel.ACTION_SPOILER_ALL;
        });
        linearLayout.addView(spoilerMatchOnlyButtonView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 24, 10, 24, 0));

        // color picker when mask message is enabled
        FrameLayout colorRowFrame = new FrameLayout(context);
        colorRowFrame.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        colorRowFrame.setVisibility(filterAction == AyuFilter.FilterModel.ACTION_HIDE ? View.GONE : View.VISIBLE);

        TextView colorLabel = new TextView(context);
        colorLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        colorLabel.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        colorLabel.setText(getString(R.string.RegexFiltersSpoilerColor));
        colorLabel.setGravity(Gravity.CENTER_VERTICAL);
        colorRowFrame.addView(colorLabel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.CENTER_VERTICAL, 0, 0, 56, 0));

        View colorSwatch = new View(context) {
            private final Paint swatchPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            {
                borderPaint.setStyle(Paint.Style.STROKE);
                borderPaint.setStrokeWidth(dp(1));
                borderPaint.setColor(0x33000000);
            }
            @Override
            protected void onDraw(Canvas canvas) {
                float r = Math.min(getWidth(), getHeight()) / 2f;
                swatchPaint.setColor(spoilerColor);
                canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, r - dp(1), swatchPaint);
                canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, r - dp(1), borderPaint);
            }
        };
        colorSwatch.setOnClickListener(swatchView -> {
            ColorPickerBottomSheet picker = new ColorPickerBottomSheet(context, getResourceProvider());
            picker.setPipetteDelegate(new ColorPickerBottomSheet.PipetteDelegate() {
                public void onStartColorPipette() {}
                public void onStopColorPipette() {}
                public android.view.ViewGroup getContainerView() { return null; }
                public android.view.View getSnapshotDrawingView() { return null; }
                public void onDrawImageOverCanvas(android.graphics.Bitmap b, Canvas c) {}
                public boolean isPipetteVisible() { return false; }
                public boolean isPipetteAvailable() { return false; }
                public void onColorSelected(int color) {}
            });
            picker.setColor(spoilerColor);
            picker.setColorListener(color -> {
                spoilerColor = color;
                colorSwatch.invalidate();
            });
            picker.show();
        });
        colorRowFrame.addView(colorSwatch, LayoutHelper.createFrame(32, 32, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 12, 0));

        spoilerColorRow = colorRowFrame;
        linearLayout.addView(colorRowFrame, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 24, 0, 24, 0));

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        boolean animations = preferences.getBoolean("view_animations", true);
        if (!animations) {
            editField.requestFocus();
            AndroidUtilities.showKeyboard(editField);
        }
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen) {
            AndroidUtilities.runOnUIThread(() -> {
                if (editField != null) {
                    editField.requestFocus();
                    AndroidUtilities.showKeyboard(editField);
                }
            }, 200);
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        final ThemeDescription.ThemeDescriptionDelegate delegate = () -> {
            if (editField != null) {
                editField.setLineColors(Theme.getColor(Theme.key_windowBackgroundWhiteInputField), Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated), Theme.getColor(Theme.key_text_RedRegular));
                editField.setCursorColor(Theme.getColor(Theme.key_chat_TextSelectionCursor));
                editField.setHandlesColor(Theme.getColor(Theme.key_chat_TextSelectionCursor));
                editField.setHighlightColor(Theme.getColor(Theme.key_chat_inTextSelectionHighlight));
            }
        };

        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(editField, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(editField, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_windowBackgroundWhiteInputField));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_windowBackgroundWhiteInputFieldActivated));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_text_RedRegular));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_chat_TextSelectionCursor));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_chat_inTextSelectionHighlight));

        themeDescriptions.add(new ThemeDescription(helpTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText8));

        return themeDescriptions;
    }

    private void showError() {
        BulletinFactory.of(RegexFilterEditActivity.this).createSimpleBulletin(R.raw.error, getString(R.string.RegexFiltersAddError)).show();
    }
}
