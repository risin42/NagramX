package tw.nekomimi.nekogram.config.cell;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell2;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import tw.nekomimi.nekogram.config.CellGroup;

public class ConfigCellTextCheck2 extends AbstractConfigCell {

    private final String key;
    private final String title;
    private final int resId;
    private final Runnable onCheckClick;
    private final ArrayList<ConfigCellCheckBox> checkBox;
    public TextCheckCell2 cell;
    private boolean enabled = true;
    private boolean collapsed = true;

    public ConfigCellTextCheck2(String key, String title, ArrayList<ConfigCellCheckBox> checkbox, Runnable onCheckClick) {
        this(key, title, 0, checkbox, onCheckClick);
    }

    public ConfigCellTextCheck2(String key, String title, int resId, ArrayList<ConfigCellCheckBox> checkbox, Runnable onCheckClick) {
        this.key = key;
        this.title = title;
        this.resId = resId;
        this.checkBox = checkbox != null ? checkbox : new ArrayList<>();
        this.onCheckClick = onCheckClick;
    }

    public String getKey() {
        return key;
    }

    public int getType() {
        return CellGroup.ITEM_TYPE_CHECK2;
    }

    public String getTitle() {
        return title;
    }

    public int getResId() {
        return resId;
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

    public boolean isCollapsed() {
        return collapsed;
    }

    public void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
    }

    public ArrayList<ConfigCellCheckBox> getCheckBox() {
        return checkBox;
    }

    public int getSelectedCount() {
        int count = 0;
        for (ConfigCellCheckBox item : checkBox) {
            if (item != null && item.getBindConfig().Bool()) {
                count++;
            }
        }
        return count;
    }

    public boolean isFullChecked() {
        for (ConfigCellCheckBox item : checkBox) {
            if (item != null && !item.getBindConfig().Bool()) {
                return false;
            }
        }
        return true;
    }

    public boolean toggleFullChecked() {
        boolean newValue = !isFullChecked();
        for (ConfigCellCheckBox item : checkBox) {
            item.getBindConfig().setConfigBool(newValue);
            if (item.cell != null) {
                item.cell.setChecked(newValue, true);
            }
        }
        if (cellGroup != null && cellGroup.getListAdapter() != null) {
            int position = cellGroup.rows.indexOf(this);
            if (position != -1) {
                cellGroup.getListAdapter().notifyItemChanged(position);
            }
        }
        return newValue;
    }

    public void onBindViewHolder(RecyclerView.ViewHolder holder) {
        TextCheckCell2 cell = (TextCheckCell2) holder.itemView;
        this.cell = cell;
        cell.setEnabled(enabled);
        cell.setTextAndCheck(title, isFullChecked(), cellGroup.needSetDivider(this), true);
        cell.setCollapseArrow(String.format(Locale.US, "%d/" + checkBox.size(), getSelectedCount()), collapsed, this::onCheckClick);
        cell.getCheckBox().setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
        cell.getCheckBox().setDrawIconType(0);
    }

    public void onCheckClick() {
        if (!enabled) return;
        if (onCheckClick != null) {
            try {
                onCheckClick.run();
            } catch (Exception ignored) {
            }
            return;
        }
        boolean newValue = toggleFullChecked();
        cellGroup.runCallback(getKey() + "_check", newValue);
    }

    public void onClick() {
        if (!enabled) return;

        setCollapsed(!collapsed);

        RecyclerListView.SelectionAdapter listAdapter = cellGroup.getListAdapter();
        int toggleRowIndex = cellGroup.rows.indexOf(this);
        if (!collapsed) {
            List<AbstractConfigCell> boundNewRows = new ArrayList<>(getCheckBox().size());
            for (AbstractConfigCell checkBoxItem : getCheckBox()) {
                checkBoxItem.bindCellGroup(cellGroup);
                boundNewRows.add(checkBoxItem);
            }
            cellGroup.rows.addAll(toggleRowIndex + 1 , boundNewRows);
            listAdapter.notifyItemRangeInserted(toggleRowIndex + 1, getCheckBox().size());
        } else {
            cellGroup.rows.removeAll(getCheckBox());
            listAdapter.notifyItemRangeRemoved(toggleRowIndex + 1, getCheckBox().size());
        }
        listAdapter.notifyItemRangeChanged(toggleRowIndex, getCheckBox().size());

        cellGroup.runCallback(getKey(), collapsed);
    }

}
