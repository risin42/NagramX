package tw.nekomimi.nekogram.config.cell;

import static org.telegram.messenger.LocaleController.getString;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Components.RecyclerListView;

import java.util.Arrays;
import java.util.stream.Collectors;

import cn.hutool.core.util.StrUtil;
import tw.nekomimi.nekogram.config.CellGroup;
import tw.nekomimi.nekogram.config.ConfigItem;

public class ConfigCellTextDetail extends AbstractConfigCell {
    private final ConfigItem bindConfig;
    private final String title;
    private final String hint;
    private final boolean isKey;
    public final RecyclerListView.OnItemClickListener onItemClickListener;

    public ConfigCellTextDetail(ConfigItem bind, RecyclerListView.OnItemClickListener onItemClickListener, String hint) {
        this(bind, onItemClickListener, hint, false);
    }

    public ConfigCellTextDetail(ConfigItem bind, RecyclerListView.OnItemClickListener onItemClickListener, String hint, boolean isKey) {
        this.bindConfig = bind;
        this.title = getString(bindConfig.getKey());
        this.hint = hint == null ? "" : hint;
        this.onItemClickListener = onItemClickListener;
        this.isKey = isKey;
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
        String value = bindConfig.String();

        if (StrUtil.isNotBlank(value)) {
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
        cell.setTextAndValue(title, value, cellGroup.needSetDivider(this));
    }

    private String maskKey(String key) {
        if (key.length() > 8) {
            return key.substring(0, 4) + "********" + key.substring(key.length() - 4);
        } else {
            return "********";
        }
    }
}
