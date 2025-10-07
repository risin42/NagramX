package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.ActionBar;
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
import org.telegram.ui.Components.BlurredRecyclerView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UndoView;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

import kotlin.Unit;
import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.config.CellGroup;
import tw.nekomimi.nekogram.config.cell.AbstractConfigCell;
import tw.nekomimi.nekogram.config.cell.ConfigCellAutoTextCheck;
import tw.nekomimi.nekogram.config.cell.ConfigCellCustom;
import tw.nekomimi.nekogram.config.cell.ConfigCellDivider;
import tw.nekomimi.nekogram.config.cell.ConfigCellHeader;
import tw.nekomimi.nekogram.config.cell.ConfigCellSelectBox;
import tw.nekomimi.nekogram.config.cell.ConfigCellText;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextCheck;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextCheckIcon;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextDetail;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextInput;
import tw.nekomimi.nekogram.config.cell.WithOnClick;
import tw.nekomimi.nekogram.ui.PopupBuilder;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;
import tw.nekomimi.nekogram.utils.FileUtil;
import tw.nekomimi.nekogram.utils.ZipUtil;
import xyz.nextalone.nagram.NaConfig;
import xyz.nextalone.nagram.helper.ExternalStickerCacheHelper;

@SuppressLint("RtlHardcoded")
@SuppressWarnings("unused")
public class NekoExperimentalSettingsActivity extends BaseNekoXSettingsActivity {

    private ListAdapter listAdapter;
    private AnimatorSet animatorSet;
    private boolean sensitiveCanChange = false;
    private boolean sensitiveEnabled = false;
    private UndoView tooltip;
    private static final int INTENT_PICK_CUSTOM_EMOJI_PACK = 114;
    private static final int INTENT_PICK_EXTERNAL_STICKER_DIRECTORY = 514;

    private final CellGroup cellGroup = new CellGroup(this);

    // Experimental
    private final AbstractConfigCell headerExperimental = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.Experimental)));
    private final AbstractConfigCell enhancedFileLoaderRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.enhancedFileLoader));
    private final AbstractConfigCell boostUploadRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.uploadBoost));
    private final AbstractConfigCell disableFilteringRow = cellGroup.appendCell(new ConfigCellCustom("DisableFiltering", CellGroup.ITEM_TYPE_TEXT_CHECK, true));
    private final AbstractConfigCell unlimitedFavedStickersRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.unlimitedFavedStickers, getString(R.string.UnlimitedFavoredStickersAbout)));
    private final AbstractConfigCell unlimitedPinnedDialogsRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.unlimitedPinnedDialogs, getString(R.string.UnlimitedPinnedDialogsAbout)));
    private final AbstractConfigCell useMediaStreamInVoipRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.useMediaStreamInVoip));
    private final AbstractConfigCell saveToChatSubfolderRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getSaveToChatSubfolder()));
    private final AbstractConfigCell springAnimationRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getSpringAnimation()));
    private final AbstractConfigCell springAnimationCrossfadeRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getSpringAnimationCrossfade()));
    private final AbstractConfigCell customAudioBitrateRow = cellGroup.appendCell(new ConfigCellCustom("CustomAudioBitrate", CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL, true));
    private final AbstractConfigCell playerDecoderRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NaConfig.INSTANCE.getPlayerDecoder(), new String[]{
            getString(R.string.VideoPlayerDecoderHardware),
            getString(R.string.VideoPlayerDecoderPreferHW),
            getString(R.string.VideoPlayerDecoderPreferSW),
    }, null));
    private final AbstractConfigCell dividerExperimental = cellGroup.appendCell(new ConfigCellDivider());

    // N-Config
    private final AbstractConfigCell headerNConfig = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.N_Config)));
    private final AbstractConfigCell forceCopyRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getForceCopy()));
    private final AbstractConfigCell disableFlagSecureRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getDisableFlagSecure()));
    private final AbstractConfigCell audioEnhanceRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getNoiseSuppressAndVoiceEnhance()));
    private final AbstractConfigCell showRPCErrorRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getShowRPCError()));
    private final AbstractConfigCell disableEmojiDrawLimitRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getDisableEmojiDrawLimit()));
    private final AbstractConfigCell sendMp4DocumentAsVideoRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getSendMp4DocumentAsVideo()));
    private final AbstractConfigCell enhancedVideoBitrateRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getEnhancedVideoBitrate()));
    private final AbstractConfigCell hideProxySponsorChannelRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.hideProxySponsorChannel));
    private final AbstractConfigCell ignoreBlockedRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.ignoreBlocked, getString(R.string.IgnoreBlockedAbout)));
    private final AbstractConfigCell regexFiltersEnabledRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getRegexFiltersEnabled(), getString(R.string.RegexFiltersNotice)));
    private final AbstractConfigCell disableChatActionRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.disableChatAction));
    private final AbstractConfigCell disableChoosingStickerRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.disableChoosingSticker));
    private final AbstractConfigCell disableScreenshotDetectionRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getDisableScreenshotDetection()));
    private final AbstractConfigCell showAdsRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getShowAds()));
    private final AbstractConfigCell devicePerformanceClassRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NaConfig.INSTANCE.getPerformanceClass(), new String[]{
            getString(R.string.QualityAuto) + " [" + SharedConfig.getPerformanceClassName(SharedConfig.measureDevicePerformanceClass()) + "]",
            getString(R.string.PerformanceClassHigh),
            getString(R.string.PerformanceClassAverage),
            getString(R.string.PerformanceClassLow),
    }, null));
    private final AbstractConfigCell customArtworkApiRow = cellGroup.appendCell(new ConfigCellTextInput(null, NaConfig.INSTANCE.getCustomArtworkApi(), "", null));
    private final AbstractConfigCell dividerNConfig = cellGroup.appendCell(new ConfigCellDivider());

    // Story
    private final AbstractConfigCell headerStory = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.Story)));
    private final AbstractConfigCell disableStoriesRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getDisableStories()));
    private final AbstractConfigCell hideFromHeaderRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getHideStoriesFromHeader()));
    private final AbstractConfigCell dividerStory = cellGroup.appendCell(new ConfigCellDivider());

    // Sticker Cache
    private final AbstractConfigCell headerExternalStickerCache = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.ExternalStickerCache)));
    private final AbstractConfigCell externalStickerCacheRow = cellGroup.appendCell(new ConfigCellAutoTextCheck(NaConfig.INSTANCE.getExternalStickerCache(), getString(R.string.ExternalStickerCacheHint), this::onExternalStickerCacheButtonClick));
    private final AbstractConfigCell externalStickerCacheAutoSyncRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getExternalStickerCacheAutoRefresh(), getString(R.string.ExternalStickerCacheAutoRefreshHint)));
    private final AbstractConfigCell externalStickerCacheDirNameTypeRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NaConfig.INSTANCE.getExternalStickerCacheDirNameType(), new String[]{ "Short name", "ID" }, null));
    private final AbstractConfigCell externalStickerCacheSyncAllRow = cellGroup.appendCell(new ConfigCellText("ExternalStickerCacheRefreshAll", ExternalStickerCacheHelper::syncAllCaches));
    private final AbstractConfigCell externalStickerCacheDeleteAllRow = cellGroup.appendCell(new ConfigCellText("ExternalStickerCacheDeleteAll", ExternalStickerCacheHelper::deleteAllCaches));
    private final AbstractConfigCell dividerExternalStickerCache = cellGroup.appendCell(new ConfigCellDivider());

    // Pangu
    private final AbstractConfigCell headerPangu = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.Pangu)));
    private final AbstractConfigCell enablePanguOnSendingRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getEnablePanguOnSending(), getString(R.string.PanguInfo)));
    private final AbstractConfigCell localeToDBCRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.localeToDBC));
    private final AbstractConfigCell dividerPangu = cellGroup.appendCell(new ConfigCellDivider());

    private final List<AbstractConfigCell> externalStickerRows;

    public NekoExperimentalSettingsActivity() {
        externalStickerRows = List.of(
            externalStickerCacheDirNameTypeRow,
            externalStickerCacheSyncAllRow,
            externalStickerCacheDeleteAllRow
        );
        if (NaConfig.INSTANCE.getExternalStickerCache().String().isBlank()) {
            cellGroup.rows.removeAll(externalStickerRows);
        }
        if (!NaConfig.INSTANCE.getSpringAnimation().Bool()) {
            cellGroup.rows.remove(springAnimationCrossfadeRow);
        }
        checkStoriesCellRows();
        if (!BuildVars.LOGS_ENABLED) {
            cellGroup.rows.remove(unlimitedFavedStickersRow);
            cellGroup.rows.remove(unlimitedPinnedDialogsRow);
            cellGroup.rows.remove(forceCopyRow);
            cellGroup.rows.remove(disableFlagSecureRow);
            cellGroup.rows.remove(hideProxySponsorChannelRow);
            cellGroup.rows.remove(ignoreBlockedRow);
            cellGroup.rows.remove(regexFiltersEnabledRow);
            cellGroup.rows.remove(disableChatActionRow);
            cellGroup.rows.remove(disableScreenshotDetectionRow);
            cellGroup.rows.remove(showAdsRow);
        }
        addRowsToMap(cellGroup);
    }

    private void setExternalStickerCacheCellsEnabled(boolean enabled) {
        ((ConfigCellTextCheck) externalStickerCacheAutoSyncRow).setEnabled(enabled);
        ((ConfigCellText) externalStickerCacheSyncAllRow).setEnabled(enabled);
        ((ConfigCellText) externalStickerCacheDeleteAllRow).setEnabled(enabled);
    }

    private void refreshExternalStickerStorageState() {
        ConfigCellAutoTextCheck cell = (ConfigCellAutoTextCheck) externalStickerCacheRow;
        setExternalStickerCacheCellsEnabled(!cell.getBindConfig().String().isEmpty());
        Context context = ApplicationLoader.applicationContext;
        ExternalStickerCacheHelper.checkUri(cell, context);
    }

    private void onExternalStickerCacheButtonClick(boolean isChecked) {
        if (isChecked) {
            // clear config
            setExternalStickerCacheCellsEnabled(false);
            ConfigCellAutoTextCheck cell = (ConfigCellAutoTextCheck) externalStickerCacheRow;
            cell.setSubtitle(null);
            NaConfig.INSTANCE.getExternalStickerCache().setConfigString("");
            if (cellGroup.rows.containsAll(externalStickerRows)) {
                cellGroup.rows.removeAll(externalStickerRows);
                int externalStickerCacheIndex = cellGroup.rows.indexOf(externalStickerCacheRow);
                listAdapter.notifyItemRangeRemoved(externalStickerCacheIndex + 2, externalStickerRows.size());
            }
            tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
        } else {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            startActivityForResult(intent, INTENT_PICK_EXTERNAL_STICKER_DIRECTORY);
            if (!cellGroup.rows.containsAll(externalStickerRows)) {
                int externalStickerCacheIndex = cellGroup.rows.indexOf(externalStickerCacheRow);
                cellGroup.rows.addAll(externalStickerCacheIndex + 2, externalStickerRows);
                listAdapter.notifyItemRangeInserted(externalStickerCacheIndex + 2, externalStickerRows.size());
            }
        }
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

        refreshExternalStickerStorageState(); // Cell (externalStickerCacheRow): Refresh state

        listAdapter = new ListAdapter(context);
        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        listView = new BlurredRecyclerView(context);
        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));

        DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setChangeDuration(350);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setSupportsChangeAnimations(false);
        listView.setItemAnimator(itemAnimator);

        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        listView.setAdapter(listAdapter);

        // Fragment: Set OnClick Callbacks
        listView.setOnItemClickListener((view, position, x, y) -> {
            AbstractConfigCell a = cellGroup.rows.get(position);
            if (a instanceof ConfigCellTextCheck) {
                if (position == cellGroup.rows.indexOf(regexFiltersEnabledRow) && (LocaleController.isRTL && x > AndroidUtilities.dp(76) || !LocaleController.isRTL && x < (view.getMeasuredWidth() - AndroidUtilities.dp(76)))) {
                    presentFragment(new RegexFiltersSettingActivity());
                    return;
                }
                ((ConfigCellTextCheck) a).onClick((TextCheckCell) view);
            } else if (a instanceof ConfigCellSelectBox) {
                ((ConfigCellSelectBox) a).onClick(view);
            } else if (a instanceof WithOnClick) {
                ((WithOnClick) a).onClick();
            } else if (a instanceof ConfigCellTextInput) {
                ((ConfigCellTextInput) a).onClick();
            } else if (a instanceof ConfigCellAutoTextCheck) {
                ((ConfigCellAutoTextCheck) a).onClick();
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
            if (key.equals(NekoConfig.useCustomEmoji.getKey())) {
                // Check
                if (!(boolean) newValue) {
                    tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
                    return;
                }
                NekoConfig.useCustomEmoji.setConfigBool(false);

                // Open picker
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/zip");
                Activity act = getParentActivity();
                act.startActivityFromChild(act, intent, INTENT_PICK_CUSTOM_EMOJI_PACK);
            } else if (key.equals(NekoConfig.localeToDBC.getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getDisableFlagSecure().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getDisableStories().getKey())) {
                checkStoriesCellRows();
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getSpringAnimation().getKey())) {
                 if (!(boolean) newValue) {
                    if (cellGroup.rows.contains(springAnimationCrossfadeRow)) {
                        final int index = cellGroup.rows.indexOf(springAnimationCrossfadeRow);
                        cellGroup.rows.remove(springAnimationCrossfadeRow);
                        listAdapter.notifyItemRemoved(index);
                    }
                } else {
                    if (!cellGroup.rows.contains(springAnimationCrossfadeRow)) {
                        final int index = cellGroup.rows.indexOf(springAnimationRow) + 1;
                        cellGroup.rows.add(index, springAnimationCrossfadeRow);
                        listAdapter.notifyItemInserted(index);
                    }
                }
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getSpringAnimationCrossfade().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NekoConfig.hideProxySponsorChannel.getKey())) {
                for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                    if (UserConfig.getInstance(a).isClientActivated()) {
                        MessagesController.getInstance(a).checkPromoInfo(true);
                    }
                }
            } else if (key.equals(NaConfig.INSTANCE.getPerformanceClass().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getPlayerDecoder().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getHideStoriesFromHeader().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            }
        };

        //Cells: Set ListAdapter
        cellGroup.setListAdapter(listView, listAdapter);

        tooltip = new UndoView(context);
        frameLayout.addView(tooltip, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 8, 0, 8, 8));

        return fragmentView;
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode == INTENT_PICK_CUSTOM_EMOJI_PACK && resultCode == Activity.RESULT_OK) {
            try {
                // copy emoji zip
                Uri uri = data.getData();
                String zipPath = MediaController.copyFileToCache(uri, "file");

                if (zipPath == null || zipPath.isEmpty()) {
                    throw new Exception("zip copy failed");
                }

                //dirs
                File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), "custom_emoji");
                if (dir.exists()) {
                    FileUtil.deleteDirectory(dir);
                }
                dir.mkdir();

                //process zip
                File zipFile = new File(zipPath);
                ZipUtil.unzip(new FileInputStream(zipFile), dir);
                zipFile.delete();
                if (!new File(ApplicationLoader.applicationContext.getFilesDir(), "custom_emoji/emoji/0_0.png").exists()) {
                    throw new Exception(getString(R.string.useCustomEmojiInvalid));
                }

                NekoConfig.useCustomEmoji.setConfigBool(true);
            } catch (Exception e) {
                FileLog.e(e);
                NekoConfig.useCustomEmoji.setConfigBool(false);
                Toast.makeText(ApplicationLoader.applicationContext, "Failed: " + e, Toast.LENGTH_LONG).show();
            }
            tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
        } else if (requestCode == INTENT_PICK_EXTERNAL_STICKER_DIRECTORY && resultCode == Activity.RESULT_OK) {
            Uri uri = data.getData();
            if (uri == null) {
                return;
            }
            // reserve permissions
            int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
            ApplicationLoader.applicationContext.getContentResolver().takePersistableUriPermission(uri, takeFlags);
            // save config
            NaConfig.INSTANCE.setExternalStickerCacheUri(uri);
            refreshExternalStickerStorageState();
            tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
        }
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

    //impl ListAdapter
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
            //noinspection ConstantConditions
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }
    }

    private void checkStoriesCellRows() {
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