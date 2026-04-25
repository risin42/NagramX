package tw.nekomimi.nekogram.config.cell;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.FileLog;
import org.telegram.ui.Cells.TextSettingsCell;

import kotlin.Unit;
import tw.nekomimi.nekogram.config.CellGroup;
import tw.nekomimi.nekogram.config.ConfigItem;
import tw.nekomimi.nekogram.ui.PopupBuilder;

// TextSettingsCell, select from a list
// Can be used without select list（custom）
public class ConfigCellSelectBox extends AbstractConfigCell implements WithBindConfig, WithKey {
    private final ConfigItem bindConfig;
    private final String[] selectList; // split by \n
    private final int[] selectValues;
    private final String title;
    private final Runnable onClickCustom;
    private final String key;

    // default: customTitle=null customOnClick=null
    public ConfigCellSelectBox(String key, ConfigItem bind, Object selectList_s, Runnable customOnClick) {
        this(key, bind, selectList_s, null, customOnClick);
    }

    public ConfigCellSelectBox(String key, ConfigItem bind, Object selectList_s, int[] selectValues, Runnable customOnClick) {
        this.bindConfig = bind;
        String key1 = key;
        if (key == null) {
            key1 = bindConfig.getKey();
        }
        this.key = key1;
        switch (selectList_s) {
            case String s -> this.selectList = s.split("\n");
            case String[] strings -> this.selectList = strings;
            case null, default -> this.selectList = null;
        }
        this.selectValues = selectValues;
        title = getString(this.key);
        this.onClickCustom = customOnClick;
    }

    public int getType() {
        return CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL;
    }

    public ConfigItem getBindConfig() {
        return this.bindConfig;
    }

    public String getKey() {
        return this.key;
    }

    public boolean isEnabled() {
        return true;
    }

    public void onBindViewHolder(RecyclerView.ViewHolder holder) {
        TextSettingsCell cell = (TextSettingsCell) holder.itemView;
        String valueText = "";
        int selectedIndex = getSelectedIndex(bindConfig.Int());
        if (selectList != null && selectedIndex >= 0 && selectedIndex < selectList.length) {
            valueText = selectList[selectedIndex];
        }
        cell.setTextAndValue(title, valueText, false, cellGroup.needSetDivider(this), true);
    }

    public void onClick(View view) {
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

        PopupBuilder builder = new PopupBuilder(view);

        builder.setItems(this.selectList, (i, __) -> {
            int selectedValue = getSelectedValue(i);
            bindConfig.setConfigInt(selectedValue);

            if (cellGroup.listAdapter != null)
                cellGroup.listAdapter.notifyItemChanged(cellGroup.rows.indexOf(this));
            if (cellGroup.thisFragment != null)
                cellGroup.thisFragment.getParentLayout().rebuildAllFragmentViews(false, false);

            cellGroup.runCallback(bindConfig.getKey(), selectedValue);

            return Unit.INSTANCE;
        });
        builder.show();


    }

    private int getSelectedIndex(int value) {
        if (selectValues == null) {
            return value;
        }
        for (int i = 0; i < selectValues.length; i++) {
            if (selectValues[i] == value) {
                return i;
            }
        }
        return -1;
    }

    private int getSelectedValue(int index) {
        if (selectValues == null || index < 0 || index >= selectValues.length) {
            return index;
        }
        return selectValues[index];
    }
}
