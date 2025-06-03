package tw.nekomimi.nekogram.config.cell;

import static org.telegram.messenger.LocaleController.getString;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.ui.Cells.TextCell;

import tw.nekomimi.nekogram.config.CellGroup;
import tw.nekomimi.nekogram.config.ConfigItem;

public class ConfigCellTextCheckIcon extends AbstractConfigCell {
    private final ConfigItem bindConfig;
    private final String key;
    private final String title;
    private String value;
    private final int resId;
    private final boolean divider;
    private final Runnable onClickCustom;
    public TextCell cell;
    private boolean enabled = true;

    public ConfigCellTextCheckIcon(ConfigItem bind, int resId) {
        this(bind, null, resId);
    }

    public ConfigCellTextCheckIcon(ConfigItem bind, String customTitle, int resId) {
        this(bind, null, customTitle, resId);
    }

    public ConfigCellTextCheckIcon(ConfigItem bind, String customTitle, int resId, boolean divider) {
        this(bind, null, customTitle, null, resId, divider, null);
    }

    public ConfigCellTextCheckIcon(ConfigItem bind, String key, String customTitle, int resId) {
        this(bind, key, customTitle, null, resId, false, null);
    }

    public ConfigCellTextCheckIcon(ConfigItem bind, String key, String customTitle, int resId, boolean divider, Runnable customOnClick) {
        this(bind, key, customTitle, null, resId, divider, customOnClick);
    }

    public ConfigCellTextCheckIcon(ConfigItem bind, String key, String customTitle, String value, int resId, boolean divider, Runnable customOnClick) {
        this.bindConfig = bind;
        String key1 = key;
        if (key == null) {
            key1 = bindConfig.getKey();
        }
        this.key = key1;
        this.title = customTitle == null ? getString(this.key) : customTitle;
        this.value = value;
        this.resId = resId;
        this.divider = divider;
        this.onClickCustom = customOnClick;
    }

    public int getType() {
        return CellGroup.ITEM_TYPE_TEXT_CHECK_ICON;
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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (this.cell != null) {
            this.cell.setEnabled(this.enabled);
        }
    }

    public void setValue(String newValue) {
        value = newValue;
        if (cell != null) {
            if (value == null) {
                cell.setTextAndIcon(title, resId, cellGroup.needSetDivider(this));
            } else {
                cell.setTextAndValueAndIcon(title, value, resId, cellGroup.needSetDivider(this));
            }
        }
    }

    public void onBindViewHolder(RecyclerView.ViewHolder holder) {
        TextCell cell = (TextCell) holder.itemView;
        this.cell = cell;
        cell.setEnabled(enabled);
        if (bindConfig == null) {
            if (value == null) {
                cell.setTextAndIcon(title, resId, cellGroup.needSetDivider(this));
            } else {
                cell.setTextAndValueAndIcon(title, value, resId, cellGroup.needSetDivider(this));
            }
            return;
        }
        cell.setTextAndCheckAndIcon(title, bindConfig.Bool(), resId, cellGroup.needSetDivider(this));
    }

    public void onClick() {
        if (!enabled) return;
        if (onClickCustom != null) {
            try {
                onClickCustom.run();
            } catch (Exception ignored) {
            }
        }
    }
}

