package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.AlertDialog;
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
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UndoView;

import java.util.ArrayList;

import kotlin.Unit;
import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.config.CellGroup;
import tw.nekomimi.nekogram.config.cell.AbstractConfigCell;
import tw.nekomimi.nekogram.config.cell.ConfigCellCustom;
import tw.nekomimi.nekogram.config.cell.ConfigCellDivider;
import tw.nekomimi.nekogram.config.cell.ConfigCellHeader;
import tw.nekomimi.nekogram.config.cell.ConfigCellSelectBox;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextCheck;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextCheckIcon;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextDetail;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextInput;
import tw.nekomimi.nekogram.config.cell.WithOnClick;
import tw.nekomimi.nekogram.ui.PopupBuilder;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;
import xyz.nextalone.nagram.NaConfig;

@SuppressLint("RtlHardcoded")
@SuppressWarnings({"unused", "FieldCanBeLocal"})
public class NekoExperimentalSettingsActivity extends BaseNekoXSettingsActivity {

    private ListAdapter listAdapter;
    private AnimatorSet animatorSet;
    private boolean sensitiveCanChange = false;
    private boolean sensitiveEnabled = false;

    private final CellGroup cellGroup = new CellGroup(this);

    // General
    private final AbstractConfigCell headerGeneral = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.General)));
    private final AbstractConfigCell backAnimationStyleRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NaConfig.INSTANCE.getBackAnimationStyle(),
            Build.VERSION.SDK_INT >= 34 ? new String[]{
                    getString(R.string.BackAnimationClassic),
                    getString(R.string.BackAnimationSpring),
                    getString(R.string.BackAnimationPredictive),
            } : new String[]{
                    getString(R.string.BackAnimationClassic),
                    getString(R.string.BackAnimationSpring),
            }, null));
    private final AbstractConfigCell springAnimationCrossfadeRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getSpringAnimationCrossfade()));
    private final AbstractConfigCell forceEdgeToEdgeRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getForceEdgeToEdge()));
    private final AbstractConfigCell smoothRoundedMenuRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getSmoothRoundedMenu()));
    private final AbstractConfigCell saveToChatSubfolderRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getSaveToChatSubfolder()));
    private final AbstractConfigCell unlimitedPinnedDialogsRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.unlimitedPinnedDialogs, getString(R.string.UnlimitedPinnedDialogsAbout)));
    private final AbstractConfigCell unlimitedFavedStickersRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.unlimitedFavedStickers, getString(R.string.UnlimitedFavoredStickersAbout)));
    private final AbstractConfigCell dividerGeneral = cellGroup.appendCell(new ConfigCellDivider());

    // Connections
    private final AbstractConfigCell headerConnection = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.Connection)));
    private final AbstractConfigCell boostUploadRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.uploadBoost));
    private final AbstractConfigCell enhancedFileLoaderRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.enhancedFileLoader));
    private final AbstractConfigCell dividerConnection = cellGroup.appendCell(new ConfigCellDivider());

    // Media
    private final AbstractConfigCell headerMedia = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.MediaSettings)));
    private final AbstractConfigCell audioEnhanceRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getNoiseSuppressAndVoiceEnhance()));
    private final AbstractConfigCell useMediaStreamInVoipRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.useMediaStreamInVoip));
    private final AbstractConfigCell sendMp4DocumentAsVideoRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getSendMp4DocumentAsVideo()));
    private final AbstractConfigCell enhancedVideoBitrateRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getEnhancedVideoBitrate()));
    private final AbstractConfigCell customAudioBitrateRow = cellGroup.appendCell(new ConfigCellCustom("customGroupVoipAudioBitrate", CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL, true));
    private final AbstractConfigCell playerDecoderRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NaConfig.INSTANCE.getPlayerDecoder(), new String[]{
            getString(R.string.VideoPlayerDecoderHardware),
            getString(R.string.VideoPlayerDecoderPreferHW),
            getString(R.string.VideoPlayerDecoderPreferSW),
    }, null));
    private final AbstractConfigCell dividerMedia = cellGroup.appendCell(new ConfigCellDivider());

    // N-Config
    private final AbstractConfigCell headerNConfig = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.N_Config)));
    private final AbstractConfigCell showAdsRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getShowAds()));
    private final AbstractConfigCell forceCopyRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getForceCopy()));
    private final AbstractConfigCell disableFlagSecureRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getDisableFlagSecure()));
    private final AbstractConfigCell showRPCErrorRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getShowRPCError()));
    private final AbstractConfigCell disableChatActionRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.disableChatAction));
    private final AbstractConfigCell disableChoosingStickerRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.disableChoosingSticker));
    private final AbstractConfigCell disableFilteringRow = cellGroup.appendCell(new ConfigCellCustom("SensitiveDisableFiltering", CellGroup.ITEM_TYPE_TEXT_CHECK, true));
    private final AbstractConfigCell devicePerformanceClassRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NaConfig.INSTANCE.getPerformanceClass(), new String[]{
            getString(R.string.QualityAuto) + " [" + SharedConfig.getPerformanceClassName(SharedConfig.measureDevicePerformanceClass()) + "]",
            getString(R.string.PerformanceClassHigh),
            getString(R.string.PerformanceClassAverage),
            getString(R.string.PerformanceClassLow),
    }, null));
    private final AbstractConfigCell dividerNConfig = cellGroup.appendCell(new ConfigCellDivider());

    // Story
    private final AbstractConfigCell headerStory = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.Story)));
    private final AbstractConfigCell disableStoriesRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getDisableStories()));
    private final AbstractConfigCell hideFromHeaderRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getHideStoriesFromHeader()));
    private final AbstractConfigCell dividerStory = cellGroup.appendCell(new ConfigCellDivider());

    // Pangu
    private final AbstractConfigCell headerPangu = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.Pangu)));
    private final AbstractConfigCell enablePanguOnSendingRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getEnablePanguOnSending(), getString(R.string.PanguInfo)));
    private final AbstractConfigCell dividerPangu = cellGroup.appendCell(new ConfigCellDivider());

    public NekoExperimentalSettingsActivity() {
        if (NaConfig.INSTANCE.getBackAnimationStyle().Int() != ActionBarLayout.BACK_ANIMATION_SPRING) {
            cellGroup.rows.remove(springAnimationCrossfadeRow);
        }
        if (!BuildVars.LOGS_ENABLED) {
            cellGroup.rows.remove(unlimitedFavedStickersRow);
            cellGroup.rows.remove(unlimitedPinnedDialogsRow);
            cellGroup.rows.remove(forceCopyRow);
            cellGroup.rows.remove(disableFlagSecureRow);
            cellGroup.rows.remove(showAdsRow);
        }
        checkStoriesRows();
        addRowsToMap(cellGroup);
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        updateRows();

        return true;
    }

    @SuppressLint("NewApi")
    @Override
    public View createView(Context context) {
        View superView = super.createView(context);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        listAdapter = new ListAdapter(context);

        listView.setAdapter(listAdapter);

        // Fragment: Set OnClick Callbacks
        listView.setOnItemClickListener((view, position, x, y) -> {
            AbstractConfigCell a = cellGroup.rows.get(position);
            if (a instanceof ConfigCellTextCheck) {
                ((ConfigCellTextCheck) a).onClick((TextCheckCell) view);
            } else if (a instanceof ConfigCellSelectBox) {
                ((ConfigCellSelectBox) a).onClick(view);
            } else if (a instanceof WithOnClick) {
                ((WithOnClick) a).onClick();
            } else if (a instanceof ConfigCellTextInput) {
                ((ConfigCellTextInput) a).onClick();
            } else if (a instanceof ConfigCellTextDetail) {
                RecyclerListView.OnItemClickListener o = ((ConfigCellTextDetail) a).onItemClickListener;
                if (o != null) {
                    try {
                        o.onItemClick(view, position);
                    } catch (Exception ignored) {
                    }
                }
            } else if (a instanceof ConfigCellCustom) { // Custom onclick
                if (position == cellGroup.rows.indexOf(disableFilteringRow)) {
                    sensitiveEnabled = !sensitiveEnabled;
                    TL_account.setContentSettings req = new TL_account.setContentSettings();
                    req.sensitive_enabled = sensitiveEnabled;
                    AlertDialog progressDialog = new AlertDialog(getParentActivity(), 3);
                    progressDialog.show();
                    getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        progressDialog.dismiss();
                        if (error == null) {
                            if (response instanceof TLRPC.TL_boolTrue && view instanceof TextCheckCell) {
                                ((TextCheckCell) view).setChecked(sensitiveEnabled);
                            }
                        } else {
                            AndroidUtilities.runOnUIThread(() -> AlertsCreator.processError(currentAccount, error, this, req));
                        }
                    }));
                } else if (position == cellGroup.rows.indexOf(customAudioBitrateRow)) {
                    PopupBuilder builder = new PopupBuilder(view);
                    builder.setItems(new String[]{
                            "32 (" + getString(R.string.Default) + ")",
                            "64",
                            "128",
                            "192",
                            "256",
                            "320"
                    }, (i, __) -> {
                        switch (i) {
                            case 0:
                                NekoConfig.customAudioBitrate.setConfigInt(32);
                                break;
                            case 1:
                                NekoConfig.customAudioBitrate.setConfigInt(64);
                                break;
                            case 2:
                                NekoConfig.customAudioBitrate.setConfigInt(128);
                                break;
                            case 3:
                                NekoConfig.customAudioBitrate.setConfigInt(192);
                                break;
                            case 4:
                                NekoConfig.customAudioBitrate.setConfigInt(256);
                                break;
                            case 5:
                                NekoConfig.customAudioBitrate.setConfigInt(320);
                                break;
                        }
                        listAdapter.notifyItemChanged(position);
                        return Unit.INSTANCE;
                    });
                    builder.show();
                }
            } else if (a instanceof ConfigCellTextCheckIcon) {
                ((ConfigCellTextCheckIcon) a).onClick();
            }
        });
        listView.setOnItemLongClickListener((view, position, x, y) -> {
            var holder = listView.findViewHolderForAdapterPosition(position);
            if (holder != null && listAdapter.isEnabled(holder)) {
                createLongClickDialog(context, NekoExperimentalSettingsActivity.this, "experimental", position);
                return true;
            }
            return false;
        });

        // Cells: Set OnSettingChanged Callbacks
        cellGroup.callBackSettingsChanged = (key, newValue) -> {
            if (key.equals(NaConfig.INSTANCE.getDisableFlagSecure().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getDisableStories().getKey())) {
                checkStoriesRows();
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getBackAnimationStyle().getKey())) {
                final int style = (int) newValue;
                if (style != ActionBarLayout.BACK_ANIMATION_SPRING) {
                    if (cellGroup.rows.contains(springAnimationCrossfadeRow)) {
                        final int index = cellGroup.rows.indexOf(springAnimationCrossfadeRow);
                        cellGroup.rows.remove(springAnimationCrossfadeRow);
                        listAdapter.notifyItemRemoved(index);
                    }
                } else {
                    if (!cellGroup.rows.contains(springAnimationCrossfadeRow)) {
                        final int index = cellGroup.rows.indexOf(backAnimationStyleRow) + 1;
                        cellGroup.rows.add(index, springAnimationCrossfadeRow);
                        listAdapter.notifyItemInserted(index);
                    }
                }
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getSpringAnimationCrossfade().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getPerformanceClass().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getPlayerDecoder().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getHideStoriesFromHeader().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getForceEdgeToEdge().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getSmoothRoundedMenu().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            }
        };

        // Cells: Set ListAdapter
        cellGroup.setListAdapter(listView, listAdapter);

        return superView;
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            checkSensitive();
            listAdapter.notifyDataSetChanged();
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    protected void updateRows() {
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public int getBaseGuid() {
        return 11000;
    }

    @Override
    public int getDrawable() {
        return R.drawable.msg_fave;
    }

    @Override
    public String getTitle() {
        return getString(R.string.Experimental);
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

    private void checkSensitive() {
        TL_account.getContentSettings req = new TL_account.getContentSettings();
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                TL_account.contentSettings settings = (TL_account.contentSettings) response;
                sensitiveEnabled = settings.sensitive_enabled;
                sensitiveCanChange = settings.sensitive_can_change;
                int count = listView.getChildCount();
                ArrayList<Animator> animators = new ArrayList<>();
                for (int a = 0; a < count; a++) {
                    View child = listView.getChildAt(a);
                    RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.getChildViewHolder(child);
                    int position = holder.getAdapterPosition();
                    if (position == cellGroup.rows.indexOf(disableFilteringRow)) {
                        TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                        checkCell.setChecked(sensitiveEnabled);
                        checkCell.setEnabled(sensitiveCanChange, animators);
                        if (sensitiveCanChange) {
                            if (!animators.isEmpty()) {
                                if (animatorSet != null) {
                                    animatorSet.cancel();
                                }
                                animatorSet = new AnimatorSet();
                                animatorSet.playTogether(animators);
                                animatorSet.addListener(new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationEnd(Animator animator) {
                                        if (animator.equals(animatorSet)) {
                                            animatorSet = null;
                                        }
                                    }
                                });
                                animatorSet.setDuration(150);
                                animatorSet.start();
                            }
                        }
                    }
                }
            } else {
                AndroidUtilities.runOnUIThread(() -> AlertsCreator.processError(currentAccount, error, this, req));
            }
        }));
    }

    // impl ListAdapter
    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return cellGroup.rows.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            AbstractConfigCell a = cellGroup.rows.get(position);
            if (a != null) {
                return a.isEnabled();
            }
            return true;
        }

        @Override
        public int getItemViewType(int position) {
            AbstractConfigCell a = cellGroup.rows.get(position);
            if (a != null) {
                return a.getType();
            }
            return CellGroup.ITEM_TYPE_TEXT_DETAIL;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            AbstractConfigCell a = cellGroup.rows.get(position);
            if (a != null) {
                if (a instanceof ConfigCellCustom) {
                    // Custom binds
                    if (holder.itemView instanceof TextCheckCell textCheckCell) {
                        textCheckCell.setEnabled(true, null);
                        if (position == cellGroup.rows.indexOf(disableFilteringRow)) {
                            textCheckCell.setTextAndValueAndCheck(getString(R.string.SensitiveDisableFiltering), getString(R.string.SensitiveAbout), sensitiveEnabled, true, true);
                            textCheckCell.setEnabled(sensitiveCanChange, null);
                        }
                    } else if (holder.itemView instanceof TextSettingsCell textSettingsCell) {
                        textSettingsCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                        if (position == cellGroup.rows.indexOf(customAudioBitrateRow)) {
                            String value = NekoConfig.customAudioBitrate.Int() + "kbps";
                            if (NekoConfig.customAudioBitrate.Int() == 32)
                                value += " (" + getString(R.string.Default) + ")";
                            textSettingsCell.setTextAndValue(getString(R.string.customGroupVoipAudioBitrate), value, true);
                        }
                    }
                } else {
                    // Default binds
                    a.onBindViewHolder(holder);
                }
            }
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = null;
            switch (viewType) {
                case CellGroup.ITEM_TYPE_DIVIDER:
                    view = new ShadowSectionCell(mContext);
                    break;
                case CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case CellGroup.ITEM_TYPE_TEXT_CHECK:
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case CellGroup.ITEM_TYPE_HEADER:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case CellGroup.ITEM_TYPE_TEXT_DETAIL:
                    view = new TextDetailSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case CellGroup.ITEM_TYPE_TEXT:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
                case CellGroup.ITEM_TYPE_TEXT_CHECK_ICON:
                    view = new TextCell(mContext);
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            // noinspection ConstantConditions
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }
    }

    private void checkStoriesRows() {
        boolean disabled = NaConfig.INSTANCE.getDisableStories().Bool();
        if (listAdapter == null) {
            if (disabled) {
                cellGroup.rows.remove(hideFromHeaderRow);
            }
            return;
        }
        if (!disabled) {
            final int index = cellGroup.rows.indexOf(disableStoriesRow);
            if (!cellGroup.rows.contains(hideFromHeaderRow)) {
                cellGroup.rows.add(index + 1, hideFromHeaderRow);
                listAdapter.notifyItemInserted(index + 1);
            }
        } else {
            final int index = cellGroup.rows.indexOf(hideFromHeaderRow);
            if (index != -1) {
                cellGroup.rows.remove(hideFromHeaderRow);
                listAdapter.notifyItemRemoved(index);
            }
        }
    }

}