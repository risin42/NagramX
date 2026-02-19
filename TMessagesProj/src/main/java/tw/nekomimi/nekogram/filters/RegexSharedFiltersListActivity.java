package tw.nekomimi.nekogram.filters;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;

import java.util.ArrayList;

import tw.nekomimi.nekogram.filters.popup.RegexFilterPopup;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

public class RegexSharedFiltersListActivity extends BaseNekoSettingsActivity {

    private int headerRow;
    private int startRow;
    private int endRow;
    private int addBtnRow;

    public RegexSharedFiltersListActivity() {
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        headerRow = rowCount++;
        addBtnRow = rowCount++;
        startRow = rowCount;
        ArrayList<AyuFilter.FilterModel> filters = AyuFilter.getRegexFilters();
        rowCount += filters.size();
        endRow = rowCount;
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.RegexFiltersSharedHeader);
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onResume() {
        super.onResume();
        updateRows();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position >= startRow && position < endRow) {
            int filterIndex = position - startRow;
            ArrayList<AyuFilter.FilterModel> filterModels = AyuFilter.getRegexFilters();
            if (filterIndex < 0 || filterIndex >= filterModels.size()) {
                return;
            }
            boolean canOpenPopup = LocaleController.isRTL && x > dp(76) || !LocaleController.isRTL && x < (view.getMeasuredWidth() - dp(76));
            if (canOpenPopup) {
                RegexFilterPopup.show(this, view, x, y, filterIndex);
            } else {
                TextCheckCell textCheckCell = (TextCheckCell) view;
                AyuFilter.FilterModel filterModel = filterModels.get(filterIndex);
                boolean enabled = !textCheckCell.isChecked();
                textCheckCell.setChecked(enabled);
                filterModel.enabled = enabled;
                AyuFilter.saveFilter(filterModels);
            }
        } else if (position == addBtnRow) {
            presentFragment(new RegexFilterEditActivity());
        }
    }

    @Override
    protected boolean onItemLongClick(View view, int position, float x, float y) {
        if (position >= startRow && position < endRow) {
            int filterIndex = position - startRow;
            ArrayList<AyuFilter.FilterModel> filterModels = AyuFilter.getRegexFilters();
            if (filterIndex >= 0 && filterIndex < filterModels.size()) {
                RegexFilterPopup.show(this, view, x, y, filterIndex);
                return true;
            }
        }
        return super.onItemLongClick(view, position, x, y);
    }

    private class ListAdapter extends BaseListAdapter {
        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean payload) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER:
                    if (position == headerRow) {
                        ((HeaderCell) holder.itemView).setText(getString(R.string.RegexFiltersHeader));
                    }
                    break;
                case TYPE_TEXT:
                    if (position == addBtnRow) {
                        TextCell textCell = (TextCell) holder.itemView;
                        textCell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
                        textCell.setTextAndIcon(getString(R.string.RegexFiltersAdd), R.drawable.msg_add, startRow < endRow);
                    }
                    break;
                case TYPE_CHECK:
                    if (position >= startRow && position < endRow) {
                        int idx = position - startRow;
                        ArrayList<AyuFilter.FilterModel> filters = AyuFilter.getRegexFilters();
                        if (idx >= 0 && idx < filters.size()) {
                            AyuFilter.FilterModel model = filters.get(idx);
                            ((TextCheckCell) holder.itemView).setTextAndCheck(model.regex, model.enabled, true);
                        }
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == headerRow) {
                return TYPE_HEADER;
            }
            if (position == addBtnRow) {
                return TYPE_TEXT;
            }
            return TYPE_CHECK;
        }
    }
}
