package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.Cells.TextSettingsCell;

import tw.nekomimi.nekogram.DatacenterActivity;

public class NekoAboutActivity extends BaseNekoSettingsActivity {

    private int xChannelRow;
    private int channelRow;
    private int channelTipsRow;
    private int sourceCodeRow;
    private int translationRow;
    private int datacenterStatusRow;

    @Override
    protected void updateRows() {
        super.updateRows();

        xChannelRow = addRow();
        channelRow = addRow();
        channelTipsRow = addRow();
        sourceCodeRow = addRow();
        translationRow = addRow();
        datacenterStatusRow = addRow();
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.About);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == xChannelRow) {
            MessagesController.getInstance(currentAccount).openByUserName("NagramX", NekoAboutActivity.this, 1);
        } else if (position == channelRow) {
            MessagesController.getInstance(currentAccount).openByUserName("nagram_channel", NekoAboutActivity.this, 1);
        } else if (position == channelTipsRow) {
            MessagesController.getInstance(currentAccount).openByUserName("NagramTips", NekoAboutActivity.this, 1);
        } else if (position == translationRow) {
            Browser.openUrl(getParentActivity(), "https://crowdin.com/project/NagramX");
        } else if (position == sourceCodeRow) {
            Browser.openUrl(getParentActivity(), "https://github.com/risin42/NagramX");
        } else if (position == datacenterStatusRow) {
            presentFragment(new DatacenterActivity(0));
        }
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean partial) {
            if (holder.getItemViewType() == TYPE_SETTINGS) {
                TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                if (position == xChannelRow) {
                    textCell.setTextAndValue(getString(R.string.XChannel), "@NagramX", true);
                } else if (position == channelRow) {
                    textCell.setTextAndValue(getString(R.string.OfficialChannel), "@nagram_channel", true);
                } else if (position == channelTipsRow) {
                    textCell.setTextAndValue(getString(R.string.TipsChannel), "@" + "NagramTips", true);
                } else if (position == sourceCodeRow) {
                    textCell.setTextAndValue(getString(R.string.SourceCode), "Github", true);
                } else if (position == translationRow) {
                    textCell.setTextAndValue(getString(R.string.TransSite), "Crowdin", true);
                } else if (position == datacenterStatusRow) {
                    textCell.setText(getString(R.string.DatacenterStatus), false);
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            return TYPE_SETTINGS;
        }
    }
}
