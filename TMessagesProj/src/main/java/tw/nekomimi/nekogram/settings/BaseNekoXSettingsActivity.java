package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.EmptyCell;
import org.telegram.ui.Cells.NotificationsCheckCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BlurredRecyclerView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
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
import tw.nekomimi.nekogram.config.cell.ConfigCellTextInput2;
import tw.nekomimi.nekogram.config.cell.WithBindConfig;
import tw.nekomimi.nekogram.config.cell.WithKey;
import tw.nekomimi.nekogram.config.cell.WithOnClick;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

public class BaseNekoXSettingsActivity extends BaseFragment {
    protected BlurredRecyclerView listView;
    protected LinearLayoutManager layoutManager;
    protected UndoView tooltip;
    protected HashMap<String, Integer> rowMap = new HashMap<>(20);
    protected HashMap<Integer, String> rowMapReverse = new HashMap<>(20);
    protected HashMap<Integer, ConfigItem> rowConfigMapReverse = new HashMap<>(20);

    protected BlurredRecyclerView createListView(Context context) {
        return new BlurredRecyclerView(context);
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        updateRows();
        return true;
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
                } else {
                    onActionBarItemClick(id);
                }
            }
        });

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
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

    protected void onActionBarItemClick(int id) {
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onResume() {
        super.onResume();
        if (getListAdapter() != null) {
            getListAdapter().notifyDataSetChanged();
        }
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

    @SuppressLint("NotifyDataSetChanged")
    protected void updateRows() {
        if (getListAdapter() != null) {
            getListAdapter().notifyDataSetChanged();
        }
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

    protected String getSettingsPrefix() {
        return null;
    }

    protected RecyclerListView.SelectionAdapter getListAdapter() {
        return null;
    }

    protected CellGroup getCellGroup() {
        return null;
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
        if (row instanceof WithBindConfig withBindConfig) {
            return withBindConfig.getBindConfig();
        }
        return null;
    }

    protected String getRowKey(AbstractConfigCell row) {
        if (row instanceof WithKey withKey) {
            return withKey.getKey();
        }
        return null;
    }

    protected ItemOptions makeLongClickOptions(View view) {
        ItemOptions options = ItemOptions.makeOptions(this, view);
        Drawable background = null;
        if (listView != null) {
            background = listView.getClipBackground(view);
        }
        return options.setScrimViewBackground(background);
    }

    protected void addDefaultLongClickOptions(ItemOptions options, String prefix, int position) {
        String key = getRowKey(position);
        String value = getRowValue(position);
        options.add(R.drawable.msg_link2, getString(R.string.CopyLink), () -> {
            AndroidUtilities.addToClipboard(String.format(Locale.getDefault(), "https://%s/nasettings/%s?r=%s", getMessagesController().linkPrefix, prefix, key));
            BulletinFactory.of(this).createCopyLinkBulletin().show();
        });
        options.addIf(value != null && !value.isEmpty(), R.drawable.msg_copy, getString(R.string.BackupSettings), () -> {
            AndroidUtilities.addToClipboard(String.format(Locale.getDefault(), "https://%s/nasettings/%s?r=%s&v=%s", getMessagesController().linkPrefix, prefix, key, value));
            BulletinFactory.of(this).createCopyLinkBulletin().show();
        });
    }

    protected void showDefaultLongClickOptions(View view, String prefix, int position) {
        ItemOptions options = makeLongClickOptions(view);
        addDefaultLongClickOptions(options, prefix, position);
        showLongClickOptions(view, options);
    }

    protected void showLongClickOptions(View view, ItemOptions options) {
        options.show();
    }

    protected void handleCellClick(View view, int position, float x, float y) {
        CellGroup cellGroup = getCellGroup();
        if (cellGroup == null || position < 0 || position >= cellGroup.rows.size()) {
            return;
        }
        AbstractConfigCell cell = cellGroup.rows.get(position);
        switch (cell) {
            case ConfigCellTextCheck c -> c.onClick((TextCheckCell) view);
            case ConfigCellTextCheck2 c -> c.onClick();
            case ConfigCellTextCheckIcon c -> c.onClick();
            case ConfigCellSelectBox c -> c.onClick(view);
            case ConfigCellTextInput c -> c.onClick();
            case ConfigCellTextInput2 c -> c.onClick();
            case ConfigCellTextDetail c -> c.onClick(view, position);
            case ConfigCellCheckBox ignored -> onCheckBoxCellClick(view, position);
            case ConfigCellCustom ignored -> onCustomCellClick(view, position, x, y);
            case WithOnClick withOnClick -> withOnClick.onClick();
            case null, default -> {}
        }
    }

    protected void onCustomCellClick(View view, int position, float x, float y) {
    }

    protected void onCheckBoxCellClick(View view, int position) {
    }

    protected boolean onItemLongClick(View view, int position, float x, float y) {
        return false;
    }

    protected void setupDefaultListeners() {
        final CellGroup cellGroup = getCellGroup();
        final RecyclerListView.SelectionAdapter listAdapter = getListAdapter();
        if (cellGroup != null && listAdapter != null) {
            cellGroup.setListAdapter(listView, listAdapter);
        }
        listView.setOnItemClickListener(this::handleCellClick);

        listView.setOnItemLongClickListener((view, position, x, y) -> {
            if (onItemLongClick(view, position, x, y)) {
                return true;
            }
            if (cellGroup != null) {
                if (position < 0 || position >= cellGroup.rows.size()) {
                    return false;
                }
                if (cellGroup.rows.get(position) instanceof ConfigCellCheckBox) {
                    return true;
                }
            }
            String prefix = getSettingsPrefix();
            if (prefix == null || listAdapter == null) {
                return false;
            }
            var holder = listView.findViewHolderForAdapterPosition(position);
            if (holder != null && listAdapter.isEnabled(holder)) {
                showDefaultLongClickOptions(view, prefix, position);
                return true;
            }
            return false;
        });
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

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{EmptyCell.class, TextSettingsCell.class, TextCheckCell.class, HeaderCell.class, TextDetailSettingsCell.class, NotificationsCheckCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_avatar_backgroundActionBarBlue));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_avatar_backgroundActionBarBlue));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_avatar_actionBarIconBlue));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_avatar_actionBarSelectorBlue));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUBACKGROUND, null, null, null, null, Theme.key_actionBarDefaultSubmenuBackground));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM, null, null, null, null, Theme.key_actionBarDefaultSubmenuItem));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextDetailSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextDetailSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));

        return themeDescriptions;
    }

    protected class BaseListAdapter extends RecyclerListView.SelectionAdapter {

        protected final Context mContext;

        public BaseListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            CellGroup cellGroup = getCellGroup();
            return cellGroup != null ? cellGroup.rows.size() : 0;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            CellGroup cellGroup = getCellGroup();
            if (cellGroup == null) {
                return false;
            }
            int position = holder.getAdapterPosition();
            if (position < 0 || position >= cellGroup.rows.size()) {
                return false;
            }
            AbstractConfigCell a = cellGroup.rows.get(position);
            return a != null && a.isEnabled();
        }

        @Override
        public int getItemViewType(int position) {
            CellGroup cellGroup = getCellGroup();
            if (cellGroup == null || position < 0 || position >= cellGroup.rows.size()) {
                return CellGroup.ITEM_TYPE_TEXT_DETAIL;
            }
            AbstractConfigCell a = cellGroup.rows.get(position);
            return a != null ? a.getType() : CellGroup.ITEM_TYPE_TEXT_DETAIL;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            CellGroup cellGroup = getCellGroup();
            if (cellGroup == null || position < 0 || position >= cellGroup.rows.size()) {
                return;
            }
            AbstractConfigCell a = cellGroup.rows.get(position);
            if (a != null) {
                if (a instanceof ConfigCellCustom) {
                    onBindCustomViewHolder(holder, position);
                } else {
                    a.onBindViewHolder(holder);
                    onBindDefaultViewHolder(holder, position);
                }
            }
        }

        protected void onBindCustomViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        }

        protected void onBindDefaultViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = onCreateCustomViewHolder(parent, viewType);
            if (view == null) {
                view = createDefaultViewByType(viewType);
            }
            // noinspection ConstantConditions
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        protected View onCreateCustomViewHolder(@NonNull ViewGroup parent, int viewType) {
            return null;
        }

        protected View createDefaultViewByType(int viewType) {
            View view = null;
            switch (viewType) {
                case CellGroup.ITEM_TYPE_DIVIDER:
                    view = new ShadowSectionCell(mContext);
                    break;
                case CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL:
                    view = new TextSettingsCell(mContext);
                    break;
                case CellGroup.ITEM_TYPE_TEXT_CHECK:
                    view = new TextCheckCell(mContext);
                    break;
                case CellGroup.ITEM_TYPE_HEADER:
                    view = new HeaderCell(mContext);
                    break;
                case CellGroup.ITEM_TYPE_TEXT_DETAIL:
                    view = new TextDetailSettingsCell(mContext);
                    break;
                case CellGroup.ITEM_TYPE_TEXT:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
                case CellGroup.ITEM_TYPE_TEXT_CHECK_ICON:
                    view = new TextCell(mContext);
                    break;
            }
            return view;
        }
    }

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
}
