package tw.nekomimi.nekogram.config.cell;

import static org.telegram.messenger.LocaleController.getString;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.ui.Cells.CheckBoxCell;

import tw.nekomimi.nekogram.config.CellGroup;
import tw.nekomimi.nekogram.config.ConfigItem;

public class ConfigCellCheckBox extends AbstractConfigCell {
    private final ConfigItem bindConfig;
    private final String key;
    private final String title;
    private final int resId;
    private final boolean divider;
    public CheckBoxCell cell;
    private String value;
    private boolean enabled = true;

    public ConfigCellCheckBox(ConfigItem bindConfig) {
        this(bindConfig, null, null, 0, true);
    }

    public ConfigCellCheckBox(ConfigItem bindConfig, String key, String customTitle, int resId, boolean divider) {
        this.bindConfig = bindConfig;
        String key1 = key;
        if (key == null) {
            key1 = bindConfig.getKey();
        }
        this.key = key1;
        this.title = customTitle == null ? getString(this.key) : customTitle;
        this.resId = resId;
        this.divider = divider;
    }

    public int getType() {
        return CellGroup.ITEM_TYPE_CHECK_BOX;
    }

    public ConfigItem getBindConfig() {
        return bindConfig;
    }

    public String getKey() {
        return key;
    }

    public String getTitle() {
        return title;
    }

    public int getResId() {
        return resId;
    }

    public boolean getDivider() {
        return divider;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (cell != null) {
            cell.setEnabled(this.enabled);
        }
    }

    public void onBindViewHolder(RecyclerView.ViewHolder holder) {
        CheckBoxCell cell = (CheckBoxCell) holder.itemView;
        this.cell = cell;
        cell.setEnabled(enabled);
        cell.setPad(1);
        this.cell.setText(title, value == null ? "" : value, bindConfig.Bool(), cellGroup.needSetDivider(this), true);
    }

    public void onClick(CheckBoxCell cell) {
        if (!enabled) return;

        boolean newV = bindConfig.toggleConfigBool();
        cell.setChecked(newV, true);

        cellGroup.runCallback(bindConfig.getKey(), newV);
    }
}
