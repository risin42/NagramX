package tw.nekomimi.nekogram.config.cell;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.TextUtils;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

import java.util.Objects;
import java.util.function.Function;

import tw.nekomimi.nekogram.config.CellGroup;
import tw.nekomimi.nekogram.config.ConfigItem;

public class ConfigCellTextInput2 extends AbstractConfigCell {
    private final ConfigItem bindConfig;
    private final String hint;
    private final String title;
    private boolean enabled = true;
    private final Runnable onClickCustom;
    private final Function<String, String> inputChecker;
    public TextDetailSettingsCell cell;

    public ConfigCellTextInput2(String customTitle, ConfigItem bind, String hint, Runnable customOnClick) {
        this(customTitle, bind, hint, customOnClick, null);
    }

    public ConfigCellTextInput2(String customTitle, ConfigItem bind, String hint, Runnable customOnClick, Function<String, String> inputChecker) {
        this.bindConfig = bind;
        this.hint = Objects.requireNonNullElse(hint, "");
        if (customTitle == null) {
            title = getString(bindConfig.getKey());
        } else {
            title = customTitle;
        }
        this.onClickCustom = customOnClick;
        this.inputChecker = inputChecker;
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
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (this.cell != null) {
            this.cell.setEnabled(this.enabled);
        }
    }

    public void onBindViewHolder(RecyclerView.ViewHolder holder) {
        TextDetailSettingsCell cell = (TextDetailSettingsCell) holder.itemView;
        this.cell = cell;
        String value = bindConfig.String();
        value = TextUtils.isEmpty(value) ? getString(R.string.Default) : value;
        cell.setTextAndValue(title, value, cellGroup.needSetDivider(this));
        cell.setEnabled(enabled);
    }

    public void onClick() {
        if (!enabled) return;
        if (onClickCustom != null) {
            try {
                onClickCustom.run();
            } catch (Exception e) {
                FileLog.e(e);
            }
            return;
        }

        Context context = cellGroup.thisFragment.getParentActivity();
        if (context == null) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getString(bindConfig.getKey()));
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        EditTextBoldCursor editText = new EditTextBoldCursor(context);
        editText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setHint(hint);
        editText.setText(bindConfig.String());
        linearLayout.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(10), 0));

        builder.setPositiveButton(getString(R.string.OK), (d, v) -> {
            String newV = editText.getText().toString().trim();
            if (this.inputChecker != null)
                newV = this.inputChecker.apply(newV);
            bindConfig.setConfigString(newV);

            // refresh
            cellGroup.listAdapter.notifyItemChanged(cellGroup.rows.indexOf(this));
            builder.getDismissRunnable().run();
            cellGroup.thisFragment.getParentLayout().rebuildFragments(0);

            cellGroup.runCallback(bindConfig.getKey(), newV);
        });
        builder.setView(linearLayout);
        cellGroup.thisFragment.showDialog(builder.create());
    }
}

