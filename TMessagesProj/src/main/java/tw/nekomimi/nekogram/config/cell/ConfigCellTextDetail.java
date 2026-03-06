package tw.nekomimi.nekogram.config.cell;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;

import tw.nekomimi.nekogram.config.CellGroup;
import tw.nekomimi.nekogram.config.ConfigItem;
import tw.nekomimi.nekogram.utils.AndroidUtil;

public class ConfigCellTextDetail extends AbstractConfigCell implements WithBindConfig, WithKey {
    private final ConfigItem bindConfig;
    private final String title;
    private final String hint;
    private final boolean isKey;
    private final Function<String, String> detailValueFormatter;
    private final String inputHint;
    private final Function<String, String> inputChecker;
    private final BiPredicate<String, String> invalidInputChecker;
    public final RecyclerListView.OnItemClickListener onItemClickListener;

    public ConfigCellTextDetail(ConfigItem bind, RecyclerListView.OnItemClickListener onItemClickListener, String hint) {
        this(bind, onItemClickListener, hint, false, null);
    }

    public ConfigCellTextDetail(ConfigItem bind, RecyclerListView.OnItemClickListener onItemClickListener, String hint, boolean isKey) {
        this(bind, onItemClickListener, hint, isKey, null);
    }

    public ConfigCellTextDetail(ConfigItem bind, RecyclerListView.OnItemClickListener onItemClickListener, String hint, boolean isKey, String customTitle) {
        this(bind, onItemClickListener, hint, isKey, customTitle, null, null, null, null);
    }

    public ConfigCellTextDetail(ConfigItem bind, String customTitle, String inputHint, Function<String, String> inputChecker, BiPredicate<String, String> invalidInputChecker, Function<String, String> detailValueFormatter) {
        this(bind, null, null, false, customTitle, detailValueFormatter, inputHint, inputChecker, invalidInputChecker);
    }

    public ConfigCellTextDetail(ConfigItem bind, RecyclerListView.OnItemClickListener onItemClickListener, String hint, boolean isKey, String customTitle, Function<String, String> detailValueFormatter, String inputHint, Function<String, String> inputChecker, BiPredicate<String, String> invalidInputChecker) {
        this.bindConfig = bind;
        this.title = !TextUtils.isEmpty(customTitle) ? customTitle : getString(bindConfig.getKey());
        this.hint = hint == null ? "" : hint;
        this.onItemClickListener = onItemClickListener;
        this.isKey = isKey;
        this.detailValueFormatter = detailValueFormatter;
        this.inputHint = Objects.requireNonNullElse(inputHint, "");
        this.inputChecker = inputChecker;
        this.invalidInputChecker = invalidInputChecker;
    }

    public int getType() {
        return CellGroup.ITEM_TYPE_TEXT_DETAIL;
    }

    public ConfigItem getBindConfig() {
        return bindConfig;
    }

    public String getKey() {
        return bindConfig == null ? null : bindConfig.getKey();
    }

    public boolean isEnabled() {
        return true;
    }

    public void onBindViewHolder(RecyclerView.ViewHolder holder) {
        TextDetailSettingsCell cell = (TextDetailSettingsCell) holder.itemView;
        String value;
        if (detailValueFormatter != null) {
            value = Objects.requireNonNullElse(detailValueFormatter.apply(bindConfig.String()), "");
        } else {
            value = bindConfig.String().trim();

            if (!TextUtils.isEmpty(value)) {
                if (isKey) {
                    // Split the value by commas, mask each key, and join them back
                    value = Arrays.stream(value.split(","))
                            .map(String::trim)
                            .map(this::maskKey)
                            .collect(Collectors.joining(", "));
                }
            } else {
                value = hint;
            }
        }
        cell.setTextAndValue(title, value, cellGroup.needSetDivider(this));
    }

    public void onClick(View view, int position) {
        if (onItemClickListener != null) {
            try {
                onItemClickListener.onItemClick(view, position);
            } catch (Exception ignored) {
            }
            return;
        }
        if (inputChecker == null && invalidInputChecker == null) {
            return;
        }

        Context context = cellGroup.thisFragment.getParentActivity();
        if (context == null) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        EditTextBoldCursor editText = new EditTextBoldCursor(context);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        editText.setHandlesColor(Theme.getColor(Theme.key_chat_TextSelectionCursor));
        editText.setFocusable(true);
        editText.setBackground(null);
        editText.setLineColors(Theme.getColor(Theme.key_windowBackgroundWhiteInputField), Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated), Theme.getColor(Theme.key_text_RedRegular));
        editText.setPadding(0, 0, 0, dp(6));
        editText.setText(bindConfig.String());
        editText.setHint(inputHint);
        editText.requestFocus();
        linearLayout.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, dp(8), 0, dp(10), 0));

        builder.setPositiveButton(getString(R.string.OK), null);
        builder.setView(linearLayout);

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String rawInput = editText.getText().toString();
            String output = rawInput;
            if (inputChecker != null) {
                output = inputChecker.apply(output);
            }
            if (invalidInputChecker != null && invalidInputChecker.test(rawInput, output)) {
                AndroidUtil.showInputError(editText);
                return;
            }
            bindConfig.setConfigString(output);
            cellGroup.listAdapter.notifyItemChanged(cellGroup.rows.indexOf(this));
            dialog.dismiss();
            cellGroup.thisFragment.getParentLayout().rebuildAllFragmentViews(false, false);
            cellGroup.runCallback(bindConfig.getKey(), output);
        }));
        cellGroup.thisFragment.showDialog(dialog);
    }

    private String maskKey(String key) {
        if (key.length() > 8) {
            return key.substring(0, 4) + "********" + key.substring(key.length() - 4);
        } else {
            return "********";
        }
    }
}
