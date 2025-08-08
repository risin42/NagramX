package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getPluralString;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ImageSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.BlurredRecyclerView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.PremiumGradient;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SeekBarView;
import org.telegram.ui.Components.TranslateAlert2;
import org.telegram.ui.RestrictedLanguagesSelectActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import kotlin.Unit;
import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.NekoXConfig;
import tw.nekomimi.nekogram.config.CellGroup;
import tw.nekomimi.nekogram.config.ConfigItem;
import tw.nekomimi.nekogram.config.cell.AbstractConfigCell;
import tw.nekomimi.nekogram.config.cell.ConfigCellCustom;
import tw.nekomimi.nekogram.config.cell.ConfigCellDivider;
import tw.nekomimi.nekogram.config.cell.ConfigCellHeader;
import tw.nekomimi.nekogram.config.cell.ConfigCellSelectBox;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextCheck;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextDetail;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextInput;
import tw.nekomimi.nekogram.translate.Translator;
import tw.nekomimi.nekogram.translate.TranslatorKt;
import tw.nekomimi.nekogram.ui.BottomBuilder;
import tw.nekomimi.nekogram.ui.PopupBuilder;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;
import xyz.nextalone.nagram.NaConfig;

public class NekoTranslatorSettingsActivity extends BaseNekoXSettingsActivity {

    private final CellGroup cellGroup = new CellGroup(this);
    private final AbstractConfigCell headerOptions = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.TranslatorOptions)));
    private final AbstractConfigCell showTranslateRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.showTranslate, null, getString(R.string.ShowTranslateButton)));
    private final AbstractConfigCell useTelegramUIAutoTranslateRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getTelegramUIAutoTranslate()));
    private final AbstractConfigCell keepMarkdownRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getTranslatorKeepMarkdown()));
    private final AbstractConfigCell dividerOptions = cellGroup.appendCell(new ConfigCellDivider());

    // Translation
    private final AbstractConfigCell headerTranslation = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.Translate)));
    private final AbstractConfigCell translationProviderRow = cellGroup.appendCell(new ConfigCellCustom(NekoConfig.translationProvider.getKey(), CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL, true));
    private final AbstractConfigCell translatorModeRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NaConfig.INSTANCE.getTranslatorMode(), new String[]{
            getString(R.string.TranslatorModeAppend),
            getString(R.string.TranslatorModeInline),
            getString(R.string.TranslatorModePopup),
    }, null));
    private final AbstractConfigCell translateToLangRow = cellGroup.appendCell(new ConfigCellCustom("TranslateToLang", CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL, true));
    private final AbstractConfigCell doNotTranslateRow = cellGroup.appendCell(new ConfigCellCustom("DoNotTranslate", CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL, true));
    private final AbstractConfigCell preferredTranslateTargetLangRow = cellGroup.appendCell(
            new ConfigCellTextInput(
                    getString(R.string.PreferredTranslateTargetLangName),
                    NaConfig.INSTANCE.getPreferredTranslateTargetLang(),
                    getString(R.string.PreferredTranslateTargetLangExample),
                    null,
                    (value) -> {
                        NaConfig.INSTANCE.getPreferredTranslateTargetLang().setConfigString(value);
                        NaConfig.INSTANCE.updatePreferredTranslateTargetLangList();
                        return value;
                    }
            )
    );
    private final AbstractConfigCell googleCloudTranslateKeyRow = cellGroup.appendCell(new ConfigCellTextDetail(NekoConfig.googleCloudTranslateKey, (view, position) -> customDialog_BottomInputString(position, NekoConfig.googleCloudTranslateKey, getString(R.string.GoogleCloudTransKeyNotice), getString(R.string.LlmApiKey)), getString(R.string.None), true));

    private final AbstractConfigCell dividerTranslation = cellGroup.appendCell(new ConfigCellDivider());

    // AI Translator
    private final AbstractConfigCell headerAITranslatorSettings = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.AITranslatorSettings)));
    private final AbstractConfigCell llmProviderRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NaConfig.INSTANCE.getLlmProviderPreset(), new String[]{
            getString(R.string.LlmProviderCustom),
            "OpenAI " + getString(R.string.LlmProviderOpenAIModel),
            "Google " + getString(R.string.LlmProviderGeminiModel),
            "Groq " + "llama-4-maverick",
            "DeepSeek " + "DeepSeek-V3",
            "xAI " + getString(R.string.LlmProviderXAIModel),
    }, null));

    private final Map<Integer, List<AbstractConfigCell>> llmProviderConfigMap = new HashMap<>();

    {
        llmProviderConfigMap.put(0, List.of(  // Custom
                new ConfigCellTextDetail(NaConfig.INSTANCE.getLlmApiKey(), (view, position) -> customDialog_BottomInputString(position, NaConfig.INSTANCE.getLlmApiKey(), getString(R.string.LlmApiKeyNotice), getString(R.string.LlmApiKey)), getString(R.string.None), true),
                new ConfigCellTextDetail(NaConfig.INSTANCE.getLlmApiUrl(), (view, position) -> customDialog_BottomInputString(position, NaConfig.INSTANCE.getLlmApiUrl(), getString(R.string.LlmApiUrlNotice), getString(R.string.LlmApiUrlHint)), getString(R.string.LlmApiUrlDefault)),
                new ConfigCellTextDetail(NaConfig.INSTANCE.getLlmModelName(), (view, position) -> customDialog_BottomInputString(position, NaConfig.INSTANCE.getLlmModelName(), getString(R.string.LlmModelNameNotice), getString(R.string.LlmModelNameDefault)), getString(R.string.LlmModelNameDefault))));
        llmProviderConfigMap.put(1, List.of( // OpenAI
                new ConfigCellTextDetail(NaConfig.INSTANCE.getLlmProviderOpenAIKey(), (view, position) -> customDialog_BottomInputString(position, NaConfig.INSTANCE.getLlmProviderOpenAIKey(), getString(R.string.LlmApiKeyNotice), getString(R.string.LlmApiKey)), getString(R.string.None), true)));
        llmProviderConfigMap.put(2, List.of( // Gemini
                new ConfigCellTextDetail(NaConfig.INSTANCE.getLlmProviderGeminiKey(), (view, position) -> customDialog_BottomInputString(position, NaConfig.INSTANCE.getLlmProviderGeminiKey(), getString(R.string.LlmApiKeyNotice), getString(R.string.LlmApiKey)), getString(R.string.None), true)));
        llmProviderConfigMap.put(3, List.of( // Groq
                new ConfigCellTextDetail(NaConfig.INSTANCE.getLlmProviderGroqKey(), (view, position) -> customDialog_BottomInputString(position, NaConfig.INSTANCE.getLlmProviderGroqKey(), getString(R.string.LlmApiKeyNotice), getString(R.string.LlmApiKey)), getString(R.string.None), true)));
        llmProviderConfigMap.put(4, List.of( // DeepSeek
                new ConfigCellTextDetail(NaConfig.INSTANCE.getLlmProviderDeepSeekKey(), (view, position) -> customDialog_BottomInputString(position, NaConfig.INSTANCE.getLlmProviderDeepSeekKey(), getString(R.string.LlmApiKeyNotice), getString(R.string.LlmApiKey)), getString(R.string.None), true)));
        llmProviderConfigMap.put(5, List.of( // xAI
                new ConfigCellTextDetail(NaConfig.INSTANCE.getLlmProviderXAIKey(), (view, position) -> customDialog_BottomInputString(position, NaConfig.INSTANCE.getLlmProviderXAIKey(), getString(R.string.LlmApiKeyNotice), getString(R.string.LlmApiKey)), getString(R.string.None), true)));
    }

    private final AbstractConfigCell llmSystemPromptRow = cellGroup.appendCell(new ConfigCellTextDetail(NaConfig.INSTANCE.getLlmSystemPrompt(), (view, position) -> customDialog_BottomInputString(position, NaConfig.INSTANCE.getLlmSystemPrompt(), getString(R.string.LlmSystemPromptNotice), getString(R.string.LlmSystemPromptHint)), getString(R.string.Default)));
    private final AbstractConfigCell llmUserPromptRow = cellGroup.appendCell(new ConfigCellTextDetail(NaConfig.INSTANCE.getLlmUserPrompt(), (view, position) -> customDialog_BottomInputString(position, NaConfig.INSTANCE.getLlmUserPrompt(), getString(R.string.LlmUserPromptNotice), ""), getString(R.string.Default)));
    private final AbstractConfigCell headerTemperature = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.LlmTemperature)));
    private final AbstractConfigCell temperatureValueRow = cellGroup.appendCell(new ConfigCellCustom(getString(R.string.LlmTemperature), ConfigCellCustom.CUSTOM_ITEM_Temperature, false));
    private final AbstractConfigCell dividerAITranslatorSettings = cellGroup.appendCell(new ConfigCellDivider());

    // article translation
    private final AbstractConfigCell headerArticleTranslation = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.InstantViewTranslation)));
    private final AbstractConfigCell enableSeparateArticleTranslatorRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getEnableSeparateArticleTranslator()));
    private final AbstractConfigCell articleTranslationProviderRow = cellGroup.appendCell(new ConfigCellCustom("ArticleTranslationProvider", CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL, true));
    private final AbstractConfigCell dividerArticleTranslation = cellGroup.appendCell(new ConfigCellDivider());

    private final AbstractConfigCell headerExperimental = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.Experimental)));
    private final AbstractConfigCell googleTranslateExpRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getGoogleTranslateExp()));
    private final AbstractConfigCell keepTranslatorPrefRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getKeepTranslatorPreferences(), getString(R.string.KeepTranslatorPreferencesNotice)));
    private final AbstractConfigCell dividerExperimental = cellGroup.appendCell(new ConfigCellDivider());

    private ListAdapter listAdapter;
    private int oldLlmProvider;
    private final boolean isAutoTranslateEnabled;

    public NekoTranslatorSettingsActivity() {
        isAutoTranslateEnabled = NaConfig.INSTANCE.getTelegramUIAutoTranslate().Bool();
        oldLlmProvider = NaConfig.INSTANCE.getLlmProviderPreset().Int();
        rebuildRowsForLlmProvider(oldLlmProvider);
        addRowsToMap(cellGroup);
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    protected void updateRows() {
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    /** @noinspection deprecation*/
    private void customDialog_BottomInputString(int position, ConfigItem bind, String subtitle, String hint) {
        BottomBuilder builder = new BottomBuilder(getParentActivity());
        builder.addTitle(getString(bind.getKey()), subtitle);
        var keyField = builder.addEditText(hint);
        if (!bind.String().trim().isEmpty()) {
            keyField.setText(bind.String());
        }
        builder.addCancelButton();
        builder.addOkButton((it) -> {
            String key = keyField.getText().toString();
            if (key.trim().isEmpty()) key = null;
            bind.setConfigString(key);
            listAdapter.notifyItemChanged(position);
            return Unit.INSTANCE;
        });
        builder.show();
        keyField.requestFocus();
        AndroidUtilities.showKeyboard(keyField);
    }

    private void showProviderSelectionPopup(View view, ConfigItem configItem, Runnable onSelected) {
        PopupBuilder builder = new PopupBuilder(view);
        List<ProviderInfo> filteredProviders = new ArrayList<>();
        for (ProviderInfo provider : ProviderInfo.PROVIDERS) {
            if (configItem == NaConfig.INSTANCE.getArticleTranslationProvider() && provider.providerConstant == Translator.providerLLMTranslator) {
                continue;
            }
            filteredProviders.add(provider);
        }
        String[] itemNames = new String[filteredProviders.size()];
        for (int i = 0; i < filteredProviders.size(); i++) {
            itemNames[i] = getString(filteredProviders.get(i).nameResId);
        }
        builder.setItems(itemNames, (i, __) -> {
            configItem.setConfigInt(filteredProviders.get(i).providerConstant);
            onSelected.run();
            return Unit.INSTANCE;
        });
        builder.show();
    }

    private String getProviderName(int providerConstant) {
        for (ProviderInfo info : ProviderInfo.PROVIDERS) {
            if (info.providerConstant == providerConstant) {
                return getString(info.nameResId);
            }
        }
        return "Unknown";
    }

    private record ProviderInfo(int providerConstant, int nameResId) {

        public static final ProviderInfo[] PROVIDERS = {
                new ProviderInfo(Translator.providerGoogle, R.string.ProviderGoogleTranslate),
                new ProviderInfo(Translator.providerYandex, R.string.ProviderYandexTranslate),
                new ProviderInfo(Translator.providerLingo, R.string.ProviderLingocloud),
                new ProviderInfo(Translator.providerMicrosoft, R.string.ProviderMicrosoftTranslator),
                new ProviderInfo(Translator.providerRealMicrosoft, R.string.ProviderRealMicrosoftTranslator),
                new ProviderInfo(Translator.providerDeepL, R.string.ProviderDeepLTranslate),
                new ProviderInfo(Translator.providerTelegram, R.string.ProviderTelegramAPI),
                new ProviderInfo(Translator.providerTranSmart, R.string.ProviderTranSmartTranslate),
                new ProviderInfo(Translator.providerLLMTranslator, R.string.ProviderLLMTranslator),
        };
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
                if (position == cellGroup.rows.indexOf(useTelegramUIAutoTranslateRow)) {
                    int provider = NekoConfig.translationProvider.Int();
                    boolean isAutoTranslateEnabled = NaConfig.INSTANCE.getTelegramUIAutoTranslate().Bool();
                    boolean isRealPremium = UserConfig.getInstance(currentAccount).isRealPremium();
                    if (provider == Translator.providerTelegram && !isAutoTranslateEnabled && !isRealPremium) {
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.info, getString(R.string.LoginEmailResetPremiumRequiredTitle)).show();
                        BotWebViewVibrationEffect.APP_ERROR.vibrate();
                        AndroidUtilities.shakeViewSpring(view, -4);
                        return;
                    }
                }
                ((ConfigCellTextCheck) a).onClick((TextCheckCell) view);
            } else if (a instanceof ConfigCellSelectBox) {
                ((ConfigCellSelectBox) a).onClick(view);
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
            } else if (a instanceof ConfigCellCustom) { // Custom OnClick
                if (position == cellGroup.rows.indexOf(translationProviderRow)) {
                    showProviderSelectionPopup(view, NekoConfig.translationProvider, () -> {
                        if (NekoConfig.translationProvider.Int() == Translator.providerTelegram) {
                            boolean isAutoTranslateEnabled = NaConfig.INSTANCE.getTelegramUIAutoTranslate().Bool();
                            boolean isRealPremium = UserConfig.getInstance(currentAccount).isRealPremium();
                            if (isAutoTranslateEnabled && !isRealPremium) {
                                NaConfig.INSTANCE.getTelegramUIAutoTranslate().setConfigBool(false);
                                listAdapter.notifyItemChanged(cellGroup.rows.indexOf(useTelegramUIAutoTranslateRow));
                                BulletinFactory.of(this).createSimpleBulletin(R.raw.info, getString(R.string.LoginEmailResetPremiumRequiredTitle)).show();
                                BotWebViewVibrationEffect.APP_ERROR.vibrate();
                                View useTelegramUIAutoTranslateView = ((ConfigCellTextCheck) useTelegramUIAutoTranslateRow).cell;
                                AndroidUtilities.shakeViewSpring(useTelegramUIAutoTranslateView, -4);
                            }
                        } else {
                            NaConfig.INSTANCE.getTelegramUIAutoTranslate().setConfigBool(isAutoTranslateEnabled);
                            listAdapter.notifyItemChanged(cellGroup.rows.indexOf(useTelegramUIAutoTranslateRow));
                        }
                        listAdapter.notifyItemChanged(position);
                    });
                } else if (position == cellGroup.rows.indexOf(translateToLangRow)) {
                    Translator.showTargetLangSelect(view, false, (locale) -> {
                        NekoConfig.translateToLang.setConfigString(TranslatorKt.getLocale2code(locale));
                        listAdapter.notifyItemChanged(position);
                        return Unit.INSTANCE;
                    });
                } else if (position == cellGroup.rows.indexOf(doNotTranslateRow)) {
                    presentFragment(new RestrictedLanguagesSelectActivity());
                } else if (position == cellGroup.rows.indexOf(articleTranslationProviderRow)) {
                    showProviderSelectionPopup(view, NaConfig.INSTANCE.getArticleTranslationProvider(), () -> listAdapter.notifyItemChanged(position));
                }
            }
        });

        listView.setOnItemLongClickListener((view, position, x, y) -> {
            var holder = listView.findViewHolderForAdapterPosition(position);
            if (holder != null && listAdapter.isEnabled(holder)) {
                createLongClickDialog(context, NekoTranslatorSettingsActivity.this, "translator", position);
                return true;
            }
            return false;
        });

        // Cells: Set ListAdapter
        cellGroup.setListAdapter(listView, listAdapter);

        // Cells: Set OnSettingChanged Callbacks
        cellGroup.callBackSettingsChanged = (key, newValue) -> {
            if (key.equals(NaConfig.INSTANCE.getPreferredTranslateTargetLang().getKey())) {
                listAdapter.notifyItemChanged(cellGroup.rows.indexOf(translateToLangRow));
            } else if (key.equals(NaConfig.INSTANCE.getEnableSeparateArticleTranslator().getKey())) {
                if ((boolean) newValue) {
                    if (!cellGroup.rows.contains(articleTranslationProviderRow)) {
                        final int index = cellGroup.rows.indexOf(enableSeparateArticleTranslatorRow) + 1;
                        cellGroup.rows.add(index, articleTranslationProviderRow);
                        listAdapter.notifyItemInserted(index);
                    }
                } else {
                    if (cellGroup.rows.contains(articleTranslationProviderRow)) {
                        final int index = cellGroup.rows.indexOf(articleTranslationProviderRow);
                        cellGroup.rows.remove(articleTranslationProviderRow);
                        listAdapter.notifyItemRemoved(index);
                    }
                }
                listAdapter.notifyItemChanged(cellGroup.rows.indexOf(enableSeparateArticleTranslatorRow));
            } else if (key.equals(NaConfig.INSTANCE.getLlmProviderPreset().getKey())) {
                int newLlmProvider = (int) newValue;
                if (newLlmProvider == oldLlmProvider) {
                    return;
                }
                int providerRowIndex = cellGroup.rows.indexOf(llmProviderRow);
                int startIndex = providerRowIndex + 1;
                List<AbstractConfigCell> oldSpecificRowBlueprints = llmProviderConfigMap.getOrDefault(oldLlmProvider, List.of());
                List<AbstractConfigCell> newSpecificRowBlueprints = llmProviderConfigMap.getOrDefault(newLlmProvider, List.of());
                int oldRowCount = oldSpecificRowBlueprints != null ? oldSpecificRowBlueprints.size() : 0;
                int newRowCount = newSpecificRowBlueprints != null ? newSpecificRowBlueprints.size() : 0;
                if (oldRowCount > 0) {
                    if (startIndex <= cellGroup.rows.size() && startIndex + oldRowCount <= cellGroup.rows.size()) {
                        cellGroup.rows.subList(startIndex, startIndex + oldRowCount).clear();
                        listAdapter.notifyItemRangeRemoved(startIndex, oldRowCount);
                    }
                }
                if (newRowCount > 0) {
                    if (startIndex <= cellGroup.rows.size()) {
                        List<AbstractConfigCell> boundNewRows = new ArrayList<>(newRowCount);
                        for (AbstractConfigCell blueprint : newSpecificRowBlueprints) {
                            blueprint.bindCellGroup(cellGroup);
                            boundNewRows.add(blueprint);
                        }
                        cellGroup.rows.addAll(startIndex, boundNewRows);
                        listAdapter.notifyItemRangeInserted(startIndex, newRowCount);
                    }
                }
                oldLlmProvider = newLlmProvider;
            } else if (key.equals(NaConfig.INSTANCE.getGoogleTranslateExp().getKey())) {
                if ((boolean) newValue) {
                    if (cellGroup.rows.contains(googleCloudTranslateKeyRow)) {
                        final int index = cellGroup.rows.indexOf(googleCloudTranslateKeyRow);
                        cellGroup.rows.remove(googleCloudTranslateKeyRow);
                        listAdapter.notifyItemRemoved(index);
                    }
                } else {
                    if (!cellGroup.rows.contains(googleCloudTranslateKeyRow)) {
                        final int index = cellGroup.rows.indexOf(preferredTranslateTargetLangRow) + 1;
                        cellGroup.rows.add(index, googleCloudTranslateKeyRow);
                        listAdapter.notifyItemInserted(index);
                    }
                }
            }
        };
        return fragmentView;
    }

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
                    if (holder.itemView instanceof TextSettingsCell textCell) {
                        if (position == cellGroup.rows.indexOf(translationProviderRow)) {
                            if (NekoConfig.translationProvider.Int() == Translator.providerTelegram) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    textCell.setTextAndValue(getString(R.string.TranslationProvider), addPremiumStar(getProviderName(NekoConfig.translationProvider.Int())), true);
                                } else {
                                    textCell.setTextAndValue(getString(R.string.TranslationProvider), getProviderName(NekoConfig.translationProvider.Int()), true);
                                }
                            } else {
                                textCell.setTextAndValue(getString(R.string.TranslationProvider), getProviderName(NekoConfig.translationProvider.Int()), true);
                            }
                        } else if (position == cellGroup.rows.indexOf(translateToLangRow)) {
                            String value = TextUtils.isEmpty(NekoConfig.translateToLang.String()) ? getString(R.string.TranslationTargetApp) : NekoXConfig.formatLang(NekoConfig.translateToLang.String());
                            textCell.setTextAndValue(getString(R.string.TransToLang), value, true);
                        } else if (position == cellGroup.rows.indexOf(doNotTranslateRow)) {
                            textCell.setTextAndValue(getString(R.string.DoNotTranslate), getRestrictedLanguages(), true, true);
                        } else if (position == cellGroup.rows.indexOf(articleTranslationProviderRow)) {
                            textCell.setTextAndValue(getString(R.string.ArticleTranslationProvider), getProviderName(NaConfig.INSTANCE.getArticleTranslationProvider().Int()), true);
                        }
                    }
                } else {
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
                case ConfigCellCustom.CUSTOM_ITEM_Temperature:
                    view = new TemperatureSeekBar(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            // noinspection ConstantConditions
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }
    }

    private static class TemperatureSeekBar extends FrameLayout {

        private final SeekBarView sizeBar;
        private final TextPaint textPaint;

        public TemperatureSeekBar(Context context) {
            super(context);

            setWillNotDraw(false);

            textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextSize(AndroidUtilities.dp(16));

            sizeBar = new SeekBarView(context);
            sizeBar.setReportChanges(true);
            sizeBar.setSeparatorsCount(21);
            sizeBar.setDelegate((stop, progress) -> {
                float value = Math.round(progress * 20) / 10f;
                NaConfig.INSTANCE.getLlmTemperature().setConfigFloat(value);
                invalidate();
            });
            float currentValue = NaConfig.INSTANCE.getLlmTemperature().Float();
            sizeBar.setProgress(currentValue / 2f);
            addView(sizeBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, Gravity.LEFT | Gravity.TOP, 9, 5, 43, 11));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
            @SuppressLint("DefaultLocale") String text = String.format("%.1f", NaConfig.INSTANCE.getLlmTemperature().Float());
            canvas.drawText(text, getMeasuredWidth() - AndroidUtilities.dp(39), AndroidUtilities.dp(28), textPaint);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            float currentValue = NaConfig.INSTANCE.getLlmTemperature().Float();
            sizeBar.setProgress(currentValue / 2f);
        }

        @Override
        public void invalidate() {
            super.invalidate();
            sizeBar.invalidate();
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public int getBaseGuid() {
        return 13000;
    }

    @Override
    public int getDrawable() {
        return R.drawable.ic_translate;
    }

    @Override
    public String getTitle() {
        return getString(R.string.TranslatorSettings);
    }

    @SuppressLint("NotifyDataSetChanged")
    private void rebuildRowsForLlmProvider(int currentLlmProvider) {
        cellGroup.rows.clear();

        cellGroup.appendCell(headerOptions);
        cellGroup.appendCell(showTranslateRow);
        cellGroup.appendCell(useTelegramUIAutoTranslateRow);
        cellGroup.appendCell(keepMarkdownRow);
        cellGroup.appendCell(dividerOptions);

        cellGroup.appendCell(headerTranslation);
        cellGroup.appendCell(translationProviderRow);
        cellGroup.appendCell(translatorModeRow);
        cellGroup.appendCell(translateToLangRow);
        cellGroup.appendCell(doNotTranslateRow);
        cellGroup.appendCell(preferredTranslateTargetLangRow);
        if (!NaConfig.INSTANCE.getGoogleTranslateExp().Bool()) {
            cellGroup.appendCell(googleCloudTranslateKeyRow);
        }
        cellGroup.appendCell(dividerTranslation);

        cellGroup.appendCell(headerAITranslatorSettings);
        cellGroup.appendCell(llmProviderRow);
        List<AbstractConfigCell> currentLlmProviderConfigRows = llmProviderConfigMap.get(currentLlmProvider);
        if (currentLlmProviderConfigRows != null) {
            currentLlmProviderConfigRows.forEach(cellGroup::appendCell);
        }
        cellGroup.appendCell(llmSystemPromptRow);
        cellGroup.appendCell(llmUserPromptRow);
        cellGroup.appendCell(headerTemperature);
        cellGroup.appendCell(temperatureValueRow);
        cellGroup.appendCell(dividerAITranslatorSettings);

        cellGroup.appendCell(headerArticleTranslation);
        cellGroup.appendCell(enableSeparateArticleTranslatorRow);
        if (NaConfig.INSTANCE.getEnableSeparateArticleTranslator().Bool()) {
            cellGroup.appendCell(articleTranslationProviderRow);
        }
        cellGroup.appendCell(dividerArticleTranslation);

        cellGroup.appendCell(headerExperimental);
        cellGroup.appendCell(googleTranslateExpRow);
        cellGroup.appendCell(keepTranslatorPrefRow);
        cellGroup.appendCell(dividerExperimental);
    }

    private SpannableString premiumStar;
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private CharSequence addPremiumStar(String text) {
        if (premiumStar == null) {
            premiumStar = new SpannableString("★");
            Drawable drawable = new AnimatedEmojiDrawable.WrapSizeDrawable(PremiumGradient.getInstance().premiumStarMenuDrawable, dp(18), dp(18));
            drawable.setBounds(0, 0, dp(18), dp(18));
            premiumStar.setSpan(new ImageSpan(drawable, DynamicDrawableSpan.ALIGN_CENTER), 0, premiumStar.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        }
        return new SpannableStringBuilder(text).append("  ").append(premiumStar);
    }

    private String getRestrictedLanguages() {
        HashSet<String> langCodes = RestrictedLanguagesSelectActivity.getRestrictedLanguages();
        if (langCodes.isEmpty()) return "";
        String doNotTranslateCellValue = null;
        try {
            if (langCodes.size() < 3) {
                List<String> names = new ArrayList<>();
                for (String lang : langCodes) {
                    String name = TranslateAlert2.languageName(lang, null);
                    if (name != null) {
                        names.add(TranslateAlert2.capitalFirst(name));
                    }
                }
                doNotTranslateCellValue = TextUtils.join(", ", names);
            }
        } catch (Exception ignore) {}
        if (TextUtils.isEmpty(doNotTranslateCellValue)) {
            doNotTranslateCellValue = String.format(getPluralString("Languages", langCodes.size()), langCodes.size());
        }
        return doNotTranslateCellValue;
    }
}