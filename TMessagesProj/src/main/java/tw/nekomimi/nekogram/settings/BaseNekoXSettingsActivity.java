package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.BlurredRecyclerView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UndoView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import tw.nekomimi.nekogram.config.CellGroup;
import tw.nekomimi.nekogram.config.ConfigItem;
import tw.nekomimi.nekogram.config.cell.AbstractConfigCell;
import tw.nekomimi.nekogram.config.cell.ConfigCellCheckBox;
import tw.nekomimi.nekogram.config.cell.ConfigCellCustom;
import tw.nekomimi.nekogram.config.cell.ConfigCellSelectBox;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextCheck;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextCheck2;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextCheckIcon;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextDetail;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextInput;
import tw.nekomimi.nekogram.config.cell.WithKey;

public class BaseNekoXSettingsActivity extends BaseFragment {
    protected BlurredRecyclerView listView;
    protected LinearLayoutManager layoutManager;
    protected UndoView tooltip;
    protected HashMap<String, Integer> rowMap = new HashMap<>(20);
    protected HashMap<Integer, String> rowMapReverse = new HashMap<>(20);
    protected HashMap<Integer, ConfigItem> rowConfigMapReverse = new HashMap<>(20);

    public static AlertDialog showConfigMenuAlert(Context context, String titleKey, ArrayList<ConfigCellTextCheck> configItems) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getString(titleKey));

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        LinearLayout linearLayoutInviteContainer = new LinearLayout(context);
        linearLayoutInviteContainer.setOrientation(LinearLayout.VERTICAL);
        linearLayout.addView(linearLayoutInviteContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        int count = configItems.size();
        for (int a = 0; a < count; a++) {
            ConfigCellTextCheck configItem = configItems.get(a);
            TextCheckCell textCell = new TextCheckCell(context);
            textCell.setTextAndCheck(configItem.getTitle(), configItem.getBindConfig().Bool(), false);
            textCell.setTag(a);
            textCell.setBackground(Theme.getSelectorDrawable(false));
            linearLayoutInviteContainer.addView(textCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            int finalA = a;
            textCell.setOnClickListener(v2 -> {
                Integer tag = (Integer) v2.getTag();
                if (tag == finalA) {
                    textCell.setChecked(configItem.getBindConfig().toggleConfigBool());
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
                }
            });
        }
        builder.setPositiveButton(getString(R.string.OK), null);
        builder.setView(linearLayout);
        return builder.create();
    }

    public static AlertDialog showConfigMenuWithIconAlert(BaseFragment bf, int titleKeyRes, ArrayList<ConfigCellTextCheckIcon> configItems) {
        Context context = bf.getContext();
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getString(titleKeyRes));

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        LinearLayout linearLayoutInviteContainer = new LinearLayout(context);
        linearLayoutInviteContainer.setOrientation(LinearLayout.VERTICAL);
        linearLayout.addView(linearLayoutInviteContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        int count = configItems.size();
        for (int a = 0; a < count; a++) {
            ConfigCellTextCheckIcon configItem = configItems.get(a);
            TextCell textCell = new TextCell(context, 23, false, true, bf.getResourceProvider());
            textCell.setTextAndCheckAndIcon(configItem.getTitle(), configItem.getBindConfig().Bool(), configItem.getResId(), configItem.getDivider());
            textCell.setTag(a);
            textCell.setBackground(Theme.getSelectorDrawable(false));
            linearLayoutInviteContainer.addView(textCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            int finalA = a;
            textCell.setOnClickListener(v2 -> {
                Integer tag = (Integer) v2.getTag();
                if (tag == finalA) {
                    textCell.setChecked(configItem.getBindConfig().toggleConfigBool());
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
                }
            });
        }
        builder.setPositiveButton(getString(R.string.OK), null);
        builder.setView(linearLayout);
        return builder.create();
    }

    protected BlurredRecyclerView createListView(Context context) {
        return new BlurredRecyclerView(context);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(getTitle());

        if (AndroidUtilities.isTablet()) {
            actionBar.setOccupyStatusBar(false);
        }

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        listView = createListView(context);
        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));

        DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setChangeDuration(350);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setSupportsChangeAnimations(false);

        listView.setItemAnimator(itemAnimator);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

        tooltip = new UndoView(context);
        frameLayout.addView(tooltip, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 8, 0, 8, 8));

        listView.setSections(true);
        actionBar.setAdaptiveBackground(listView);
        return fragmentView;
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        listView.setPadding(0, 0, 0, bottom);
        listView.setClipToPadding(false);
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) tooltip.getLayoutParams();
        layoutParams.setMargins(dp(8), 0, dp(8), dp(8) + bottom);
        tooltip.setLayoutParams(layoutParams);
    }

    protected void updateRows() {
    }

    public int getBaseGuid() {
        return 10000;
    }

    public int getDrawable() {
        return 0;
    }

    public String getTitle() {
        return "";
    }

    protected void addRowsToMap(CellGroup cellGroup) {
        rowMap.clear();
        rowMapReverse.clear();
        rowConfigMapReverse.clear();
        String key;
        ConfigItem config;
        for (int i = 0; i < cellGroup.rows.size(); i++) {
            config = getBindConfig(cellGroup.rows.get(i));
            key = getRowKey(cellGroup.rows.get(i));
            if (key == null) key = String.valueOf(i);
            rowMap.put(key, i);
            rowMapReverse.put(i, key);
            rowConfigMapReverse.put(i, config);
        }
    }

    protected String getRowKey(int position) {
        if (rowMapReverse.containsKey(position)) {
            return rowMapReverse.get(position);
        }
        return String.valueOf(position);
    }

    protected String getRowValue(int position) {
        ConfigItem config = rowConfigMapReverse.get(position);
        if (config != null) return config.String();
        return null;
    }

    protected ConfigItem getBindConfig(AbstractConfigCell row) {
        if (row instanceof ConfigCellTextCheck) {
            return ((ConfigCellTextCheck) row).getBindConfig();
        } else if (row instanceof ConfigCellSelectBox) {
            return ((ConfigCellSelectBox) row).getBindConfig();
        } else if (row instanceof ConfigCellTextDetail) {
            return ((ConfigCellTextDetail) row).getBindConfig();
        } else if (row instanceof ConfigCellTextInput) {
            return ((ConfigCellTextInput) row).getBindConfig();
        } else if (row instanceof ConfigCellTextCheckIcon) {
            return ((ConfigCellTextCheckIcon) row).getBindConfig();
        }
        return null;
    }

    protected String getRowKey(AbstractConfigCell row) {
        if (row instanceof WithKey) {
            return ((WithKey) row).getKey();
        } else if (row instanceof ConfigCellTextCheck) {
            return ((ConfigCellTextCheck) row).getKey();
        } else if (row instanceof ConfigCellSelectBox) {
            return ((ConfigCellSelectBox) row).getKey();
        } else if (row instanceof ConfigCellTextDetail) {
            return ((ConfigCellTextDetail) row).getKey();
        } else if (row instanceof ConfigCellTextInput) {
            return ((ConfigCellTextInput) row).getKey();
        } else if (row instanceof ConfigCellCustom) {
            return ((ConfigCellCustom) row).getKey();
        } else if (row instanceof ConfigCellTextCheckIcon) {
            return ((ConfigCellTextCheckIcon) row).getKey();
        } else if (row instanceof ConfigCellTextCheck2) {
            return ((ConfigCellTextCheck2) row).getKey();
        } else if (row instanceof ConfigCellCheckBox) {
            return ((ConfigCellCheckBox) row).getKey();
        }
        return null;
    }

    protected void createLongClickDialog(Context context, BaseFragment fragment, String prefix, int position) {
        String key = getRowKey(position);
        String value = getRowValue(position);
        ArrayList<CharSequence> itemsArray = new ArrayList<>();
        itemsArray.add(getString(R.string.CopyLink));
        if (value != null) {
            itemsArray.add(getString(R.string.BackupSettings));
        }
        CharSequence[] items = itemsArray.toArray(new CharSequence[0]);
        showDialog(new AlertDialog.Builder(context).setItems(items, (dialogInterface, i) -> {
            switch (i) {
                case 0:
                    AndroidUtilities.addToClipboard(String.format(Locale.getDefault(), "https://%s/nasettings/%s?r=%s", getMessagesController().linkPrefix, prefix, key));
                    BulletinFactory.of(fragment).createCopyLinkBulletin().show();
                    break;
                case 1:
                    AndroidUtilities.addToClipboard(String.format(Locale.getDefault(), "https://%s/nasettings/%s?r=%s&v=%s", getMessagesController().linkPrefix, prefix, key, value));
                    BulletinFactory.of(fragment).createCopyLinkBulletin().show();
                    break;
            }
        }).create());
    }

    public void importToRow(String key, String value, Runnable unknown) {
        int position = -1;
        try {
            position = Integer.parseInt(key);
        } catch (NumberFormatException exception) {
            Integer temp = rowMap.get(key);
            if (temp != null) position = temp;
        }
        ConfigItem config = rowConfigMapReverse.get(position);
        Context context = getParentActivity();
        if (context != null && config != null) {
            Object new_value = config.checkConfigFromString(value);
            if (new_value == null) {
                scrollToRow(key, unknown);
                return;
            }
            var builder = new AlertDialog.Builder(context);
            builder.setTitle(getString(R.string.ImportSettings));
            builder.setMessage(getString(R.string.ImportSettingsAlert));
            builder.setNegativeButton(getString(R.string.Cancel), (dialogInter, i) -> scrollToRow(key, unknown));
            builder.setPositiveButton(getString(R.string.Import), (dialogInter, i) -> {
                config.changed(new_value);
                config.saveConfig();
                updateRows();
                scrollToRow(key, unknown);
            });
            builder.show();
        } else {
            scrollToRow(key, unknown);
        }
    }

    public void scrollToRow(String key, Runnable unknown) {
        int position = -1;
        try {
            position = Integer.parseInt(key);
        } catch (NumberFormatException exception) {
            Integer temp = rowMap.get(key);
            if (temp != null) position = temp;
        }
        if (position > -1 && listView != null && layoutManager != null) {
            int finalPosition = position;
            listView.highlightRow(() -> {
                layoutManager.scrollToPositionWithOffset(finalPosition, dp(60));
                return finalPosition;
            });
        } else if (unknown != null) {
            unknown.run();
        }
    }

    public HashMap<Integer, String> getRowMapReverse() {
        return rowMapReverse;
    }


}
