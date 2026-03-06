package tw.nekomimi.nekogram.config;

import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

import tw.nekomimi.nekogram.config.cell.AbstractConfigCell;
import tw.nekomimi.nekogram.config.cell.ConfigCellDivider;

public class CellGroup {
    public static final int ITEM_TYPE_DIVIDER = 1;
    public static final int ITEM_TYPE_TEXT_SETTINGS_CELL = 2;
    public static final int ITEM_TYPE_TEXT_CHECK = 3;
    public static final int ITEM_TYPE_HEADER = 4;
    public static final int ITEM_TYPE_TEXT_DETAIL = 5;
    public static final int ITEM_TYPE_TEXT = 6;
    public static final int ITEM_TYPE_TEXT_CHECK_ICON = 7;
    public static final int ITEM_TYPE_CHECK2 = 8;
    public static final int ITEM_TYPE_CHECK_BOX = 9;

    public BaseFragment thisFragment;
    public RecyclerListView listView;
    public RecyclerListView.SelectionAdapter listAdapter;
    public ArrayList<AbstractConfigCell> rows;

    public CallBackSettingsChanged callBackSettingsChanged;

    public CellGroup(BaseFragment thisFragment) {
        this.thisFragment = thisFragment;
        this.rows = new ArrayList<>();
    }

    public RecyclerListView.SelectionAdapter getListAdapter() {
        return listAdapter;
    }

    public void setListAdapter(RecyclerListView lv, RecyclerListView.SelectionAdapter la) {
        this.listView = lv;
        this.listAdapter = la;
    }

    public AbstractConfigCell appendCell(AbstractConfigCell cell) {
        cell.bindCellGroup(this);
        this.rows.add(cell);
        return cell;
    }

    public interface CallBackSettingsChanged {
        void run(String key, Object newValue);
    }

    public void runCallback(String key, Object newValue) {
        if (callBackSettingsChanged == null) return;
        try {
            callBackSettingsChanged.run(key, newValue);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public boolean needSetDivider(AbstractConfigCell cell) {
        return !(rows.get(rows.indexOf(cell) + 1) instanceof ConfigCellDivider);
    }

}
