package tw.nekomimi.nekogram.settings;

import static android.view.View.OVER_SCROLL_NEVER;
import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getPluralString;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Editable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ClickableSpan;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
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
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import tw.nekomimi.nekogram.llm.LlmConfig;
import tw.nekomimi.nekogram.llm.net.OpenAICompatClient;
import tw.nekomimi.nekogram.llm.preset.LlmPresetRegistry;
import tw.nekomimi.nekogram.llm.ui.LlmEditTextFactory;
import tw.nekomimi.nekogram.llm.utils.LlmModelUtil;
import tw.nekomimi.nekogram.translate.Translator;
import tw.nekomimi.nekogram.translate.TranslatorKt;
import tw.nekomimi.nekogram.ui.PopupBuilder;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;
import xyz.nextalone.nagram.NaConfig;

@SuppressLint("NotifyDataSetChanged")
public class NekoTranslatorSettingsActivity extends BaseNekoXSettingsActivity {

    private final int initialTranslationProvider;
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
    }, null));
    private final AbstractConfigCell translateToLangRow = cellGroup.appendCell(new ConfigCellCustom("TranslateTo", CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL, true));
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
    private final AbstractConfigCell googleCloudTranslateKeyRow = cellGroup.appendCell(new ConfigCellTextDetail(NekoConfig.googleCloudTranslateKey, (view, position) -> showConfigDialog(position, NekoConfig.googleCloudTranslateKey, getString(R.string.GoogleCloudTransKeyNotice), getString(R.string.LlmApiKey)), getString(R.string.None), true));

    private final AbstractConfigCell dividerTranslation = cellGroup.appendCell(new ConfigCellDivider());

    // AI Translator
    private final AbstractConfigCell headerAITranslatorSettings = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.AITranslatorSettings)));
    private final AbstractConfigCell llmProviderRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NaConfig.INSTANCE.getLlmProviderPreset(), new String[]{
            getString(R.string.LlmProviderCustom),
            "OpenAI",
            "Google AI Studio",
            "Groq",
            "DeepSeek",
            "xAI",
            "Cerebras",
            "Ollama",
            "OpenRouter",
            "Vercel AI Gateway",
    }, null));
    private final AbstractConfigCell llmModelRow = cellGroup.appendCell(new ConfigCellCustom("LlmModelName", CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL, true));

    private final Map<Integer, List<AbstractConfigCell>> llmProviderConfigMap = new HashMap<>();

    {
        llmProviderConfigMap.put(LlmPresetRegistry.CUSTOM, List.of(
                new ConfigCellTextDetail(NaConfig.INSTANCE.getLlmApiKey(), (view, position) -> showConfigDialog(position, NaConfig.INSTANCE.getLlmApiKey(), getString(R.string.LlmApiKeyNotice), getString(R.string.LlmApiKey)), getString(R.string.None), true),
                new ConfigCellTextDetail(NaConfig.INSTANCE.getLlmApiUrl(), (view, position) -> showConfigDialog(position, NaConfig.INSTANCE.getLlmApiUrl(), getString(R.string.LlmApiUrlNotice), getString(R.string.LlmApiUrlHint)), getString(R.string.LlmApiUrlDefault))));
        llmProviderConfigMap.put(LlmPresetRegistry.OPENAI, List.of(
                new ConfigCellTextDetail(NaConfig.INSTANCE.getLlmProviderOpenAIKey(), (view, position) -> showConfigDialog(position, NaConfig.INSTANCE.getLlmProviderOpenAIKey(), getString(R.string.LlmApiKeyNotice), getString(R.string.LlmApiKey)), getString(R.string.None), true, getString(R.string.LlmApiKey))));
        llmProviderConfigMap.put(LlmPresetRegistry.GEMINI, List.of(
                new ConfigCellTextDetail(NaConfig.INSTANCE.getLlmProviderGeminiKey(), (view, position) -> showConfigDialog(position, NaConfig.INSTANCE.getLlmProviderGeminiKey(), getString(R.string.LlmApiKeyNotice), getString(R.string.LlmApiKey)), getString(R.string.None), true, getString(R.string.LlmApiKey))));
        llmProviderConfigMap.put(LlmPresetRegistry.GROQ, List.of(
                new ConfigCellTextDetail(NaConfig.INSTANCE.getLlmProviderGroqKey(), (view, position) -> showConfigDialog(position, NaConfig.INSTANCE.getLlmProviderGroqKey(), getString(R.string.LlmApiKeyNotice), getString(R.string.LlmApiKey)), getString(R.string.None), true, getString(R.string.LlmApiKey))));
        llmProviderConfigMap.put(LlmPresetRegistry.DEEPSEEK, List.of(
                new ConfigCellTextDetail(NaConfig.INSTANCE.getLlmProviderDeepSeekKey(), (view, position) -> showConfigDialog(position, NaConfig.INSTANCE.getLlmProviderDeepSeekKey(), getString(R.string.LlmApiKeyNotice), getString(R.string.LlmApiKey)), getString(R.string.None), true, getString(R.string.LlmApiKey))));
        llmProviderConfigMap.put(LlmPresetRegistry.XAI, List.of(
                new ConfigCellTextDetail(NaConfig.INSTANCE.getLlmProviderXAIKey(), (view, position) -> showConfigDialog(position, NaConfig.INSTANCE.getLlmProviderXAIKey(), getString(R.string.LlmApiKeyNotice), getString(R.string.LlmApiKey)), getString(R.string.None), true, getString(R.string.LlmApiKey))));
        llmProviderConfigMap.put(LlmPresetRegistry.CEREBRAS, List.of(
                new ConfigCellTextDetail(NaConfig.INSTANCE.getLlmProviderCerebrasKey(), (view, position) -> showConfigDialog(position, NaConfig.INSTANCE.getLlmProviderCerebrasKey(), getString(R.string.LlmApiKeyNotice), getString(R.string.LlmApiKey)), getString(R.string.None), true, getString(R.string.LlmApiKey))));
        llmProviderConfigMap.put(LlmPresetRegistry.OLLAMA_CLOUD, List.of(
                new ConfigCellTextDetail(NaConfig.INSTANCE.getLlmProviderOllamaCloudKey(), (view, position) -> showConfigDialog(position, NaConfig.INSTANCE.getLlmProviderOllamaCloudKey(), getString(R.string.LlmApiKeyNotice), getString(R.string.LlmApiKey)), getString(R.string.None), true, getString(R.string.LlmApiKey))));
        llmProviderConfigMap.put(LlmPresetRegistry.OPENROUTER, List.of(
                new ConfigCellTextDetail(NaConfig.INSTANCE.getLlmProviderOpenRouterKey(), (view, position) -> showConfigDialog(position, NaConfig.INSTANCE.getLlmProviderOpenRouterKey(), getString(R.string.LlmApiKeyNotice), getString(R.string.LlmApiKey)), getString(R.string.None), true, getString(R.string.LlmApiKey))));
        llmProviderConfigMap.put(LlmPresetRegistry.VERCEL_AI_GATEWAY, List.of(
                new ConfigCellTextDetail(NaConfig.INSTANCE.getLlmProviderVercelAIGatewayKey(), (view, position) -> showConfigDialog(position, NaConfig.INSTANCE.getLlmProviderVercelAIGatewayKey(), getString(R.string.LlmApiKeyNotice), getString(R.string.LlmApiKey)), getString(R.string.None), true, getString(R.string.LlmApiKey))));
    }

    private final AbstractConfigCell llmSystemPromptRow = cellGroup.appendCell(new ConfigCellTextDetail(NaConfig.INSTANCE.getLlmSystemPrompt(), (view, position) -> showConfigDialog(position, NaConfig.INSTANCE.getLlmSystemPrompt(), getString(R.string.LlmSystemPromptNotice) + "\n", getString(R.string.LlmSystemPromptHint)), getString(R.string.Default)));
    private final AbstractConfigCell llmUserPromptRow = cellGroup.appendCell(new ConfigCellTextDetail(NaConfig.INSTANCE.getLlmUserPrompt(), (view, position) -> showConfigDialog(position, NaConfig.INSTANCE.getLlmUserPrompt(), getString(R.string.LlmUserPromptNotice) + "\n", getString(R.string.LlmUserPromptHint)), getString(R.string.Default)));
    private final AbstractConfigCell llmUseContextRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getLlmUseContext(), getString(R.string.LlmUseContextNotice)));
    private final AbstractConfigCell llmUseContextInAutoTranslateRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getLlmUseContextInAutoTranslate(), getString(R.string.LlmUseContextInAutoTranslateNotice)));
    private final AbstractConfigCell llmContextSizeRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NaConfig.INSTANCE.getLlmContextSize(), new String[]{
            "1",
            "3",
            "5",
            "7",
            "10",
    }, null));
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
        initialTranslationProvider = NekoConfig.translationProvider.Int();
        isAutoTranslateEnabled = NaConfig.INSTANCE.getTelegramUIAutoTranslate().Bool();
        oldLlmProvider = NaConfig.INSTANCE.getLlmProviderPreset().Int();
        rebuildRowsForLlmProvider(oldLlmProvider);
        checkContextRows();
        checkTemperatureRows();
        addRowsToMap(cellGroup);
    }

    @Override
    protected void updateRows() {
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private CharSequence getEnhancedSubtitleWithLink(ConfigItem bind, CharSequence originalSubtitle) {
        String providerUrl = getProviderKeyUrl(bind);
        if (providerUrl != null) {
            return AndroidUtilities.replaceSingleTag(
                    originalSubtitle + "\n**" + getString(R.string.HowToObtain) + "**",
                    -1,
                    AndroidUtilities.REPLACING_TAG_TYPE_LINKBOLD,
                    () -> Browser.openUrl(getParentActivity(), providerUrl),
                    getResourceProvider()
            );
        }
        return originalSubtitle;
    }

    private String getProviderKeyUrl(ConfigItem bind) {
        if (bind == NaConfig.INSTANCE.getLlmProviderOpenAIKey()) {
            return "https://platform.openai.com/api-keys";
        } else if (bind == NaConfig.INSTANCE.getLlmProviderGeminiKey()) {
            return "https://aistudio.google.com/app/apikey";
        } else if (bind == NaConfig.INSTANCE.getLlmProviderGroqKey()) {
            return "https://console.groq.com/keys";
        } else if (bind == NaConfig.INSTANCE.getLlmProviderDeepSeekKey()) {
            return "https://platform.deepseek.com/api_keys";
        } else if (bind == NaConfig.INSTANCE.getLlmProviderXAIKey()) {
            return "https://console.x.ai";
        } else if (bind == NaConfig.INSTANCE.getLlmProviderCerebrasKey()) {
            return "https://cloud.cerebras.ai";
        } else if (bind == NaConfig.INSTANCE.getLlmProviderOllamaCloudKey()) {
            return "https://ollama.com/settings/keys";
        } else if (bind == NaConfig.INSTANCE.getLlmProviderOpenRouterKey()) {
            return "https://openrouter.ai/keys";
        } else if (bind == NaConfig.INSTANCE.getLlmProviderVercelAIGatewayKey()) {
            return "https://vercel.com/ai-gateway";
        } else if (bind == NekoConfig.googleCloudTranslateKey) {
            return "https://console.cloud.google.com/apis/credentials";
        }
        return null;
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
                if (position == cellGroup.rows.indexOf(useTelegramUIAutoTranslateRow)) {
                    int provider = NekoConfig.translationProvider.Int();
                    boolean isAutoTranslateEnabled = NaConfig.INSTANCE.getTelegramUIAutoTranslate().Bool();
                    boolean isPremium = UserConfig.getInstance(currentAccount).isPremium();
                    if (provider == Translator.providerTelegram && !isAutoTranslateEnabled && !isPremium) {
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
                            boolean isPremium = UserConfig.getInstance(currentAccount).isPremium();
                            if (isAutoTranslateEnabled && !isPremium) {
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
                } else if (position == cellGroup.rows.indexOf(llmModelRow)) {
                    showLlmModelDialog();
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
                checkTemperatureRows();
                int providerRowIndex = cellGroup.rows.indexOf(llmProviderRow);
                int startIndex = providerRowIndex + 2;
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
                listAdapter.notifyItemChanged(cellGroup.rows.indexOf(llmModelRow));
                addRowsToMap(cellGroup);
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
            } else if (key.equals(NaConfig.INSTANCE.getLlmUseContext().getKey())) {
                checkContextRows();
                checkTemperatureRows();
            }
        };

        return superView;
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
                        } else if (position == cellGroup.rows.indexOf(llmModelRow)) {
                            int preset = NaConfig.INSTANCE.getLlmProviderPreset().Int();
                            textCell.setTextAndValue(getString(R.string.LlmModelName), LlmConfig.getEffectiveModelName(preset), true);
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

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onFragmentDestroy() {
        maybeRestoreTranslationProvider();
        super.onFragmentDestroy();
    }

    private void maybeRestoreTranslationProvider() {
        if (NekoConfig.translationProvider.Int() != Translator.providerLLMTranslator) {
            return;
        }
        if (!isCurrentLlmProviderApiKeyEmpty()) {
            return;
        }
        int providerToRestore = initialTranslationProvider;
        if (providerToRestore == Translator.providerLLMTranslator) {
            providerToRestore = Translator.providerGoogle;
        }
        NekoConfig.translationProvider.setConfigInt(providerToRestore);
    }

    private boolean isCurrentLlmProviderApiKeyEmpty() {
        ConfigItem apiKeyItem = getCurrentLlmProviderApiKeyItem();
        return apiKeyItem == null || TextUtils.isEmpty(apiKeyItem.String().trim());
    }

    private ConfigItem getCurrentLlmProviderApiKeyItem() {
        return switch (NaConfig.INSTANCE.getLlmProviderPreset().Int()) {
            case LlmPresetRegistry.CUSTOM -> NaConfig.INSTANCE.getLlmApiKey();
            case LlmPresetRegistry.OPENAI -> NaConfig.INSTANCE.getLlmProviderOpenAIKey();
            case LlmPresetRegistry.GEMINI -> NaConfig.INSTANCE.getLlmProviderGeminiKey();
            case LlmPresetRegistry.GROQ -> NaConfig.INSTANCE.getLlmProviderGroqKey();
            case LlmPresetRegistry.DEEPSEEK -> NaConfig.INSTANCE.getLlmProviderDeepSeekKey();
            case LlmPresetRegistry.XAI -> NaConfig.INSTANCE.getLlmProviderXAIKey();
            case LlmPresetRegistry.CEREBRAS -> NaConfig.INSTANCE.getLlmProviderCerebrasKey();
            case LlmPresetRegistry.OLLAMA_CLOUD -> NaConfig.INSTANCE.getLlmProviderOllamaCloudKey();
            case LlmPresetRegistry.OPENROUTER -> NaConfig.INSTANCE.getLlmProviderOpenRouterKey();
            case LlmPresetRegistry.VERCEL_AI_GATEWAY -> NaConfig.INSTANCE.getLlmProviderVercelAIGatewayKey();
            default -> null;
        };
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
        cellGroup.appendCell(llmModelRow);
        List<AbstractConfigCell> currentLlmProviderConfigRows = llmProviderConfigMap.get(currentLlmProvider);
        if (currentLlmProviderConfigRows != null) {
            currentLlmProviderConfigRows.forEach(cellGroup::appendCell);
        }
        cellGroup.appendCell(llmSystemPromptRow);
        cellGroup.appendCell(llmUserPromptRow);
        cellGroup.appendCell(llmUseContextRow);
        cellGroup.appendCell(llmUseContextInAutoTranslateRow);
        cellGroup.appendCell(llmContextSizeRow);
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
        } catch (Exception ignore) {
        }
        if (TextUtils.isEmpty(doNotTranslateCellValue)) {
            doNotTranslateCellValue = String.format(getPluralString("Languages", langCodes.size()), langCodes.size());
        }
        return doNotTranslateCellValue;
    }

    private void checkTemperatureRows() {
        int preset = NaConfig.INSTANCE.getLlmProviderPreset().Int();
        String modelName = LlmConfig.getEffectiveModelName(preset);
        boolean showTemperature = LlmModelUtil.supportsTemperature(modelName);
        if (listAdapter == null) {
            if (!showTemperature) {
                cellGroup.rows.remove(headerTemperature);
                cellGroup.rows.remove(temperatureValueRow);
            }
            return;
        }
        if (showTemperature) {
            final int index = cellGroup.rows.indexOf(NaConfig.INSTANCE.getLlmUseContext().Bool() ? llmContextSizeRow : llmUseContextRow);
            if (!cellGroup.rows.contains(headerTemperature)) {
                cellGroup.rows.add(index + 1, headerTemperature);
                cellGroup.rows.add(index + 2, temperatureValueRow);
                listAdapter.notifyItemRangeInserted(index + 1, 2);
            }
        } else {
            int temperatureRowIndex = cellGroup.rows.indexOf(headerTemperature);
            if (temperatureRowIndex != -1) {
                cellGroup.rows.remove(headerTemperature);
                cellGroup.rows.remove(temperatureValueRow);
                listAdapter.notifyItemRangeRemoved(temperatureRowIndex, 2);
            }
        }
    }

    private void checkContextRows() {
        boolean useContext = NaConfig.INSTANCE.getLlmUseContext().Bool();
        if (listAdapter == null) {
            if (!useContext) {
                cellGroup.rows.remove(llmUseContextInAutoTranslateRow);
                cellGroup.rows.remove(llmContextSizeRow);
            }
            return;
        }
        if (useContext) {
            final int index = cellGroup.rows.indexOf(llmUseContextRow);
            if (!cellGroup.rows.contains(llmUseContextInAutoTranslateRow)) {
                cellGroup.rows.add(index + 1, llmUseContextInAutoTranslateRow);
                cellGroup.rows.add(index + 2, llmContextSizeRow);
                listAdapter.notifyItemRangeInserted(index + 1, 2);
            }
        } else {
            int idxA = cellGroup.rows.indexOf(llmUseContextInAutoTranslateRow);
            int idxB = cellGroup.rows.indexOf(llmContextSizeRow);
            if (idxA != -1 || idxB != -1) {
                List<Integer> toRemove = new ArrayList<>();
                if (idxA != -1) toRemove.add(idxA);
                if (idxB != -1) toRemove.add(idxB);
                toRemove.sort((a, b) -> Integer.compare(b, a));
                for (int idx : toRemove) {
                    cellGroup.rows.remove(idx);
                    listAdapter.notifyItemRemoved(idx);
                }
            }
        }
    }

    /** @noinspection deprecation*/
    private void showConfigDialog(int position, ConfigItem bind, String subtitle, String hint) {
        Context context = getParentActivity();
        if (context == null) return;
        var resourcesProvider = getResourceProvider();

        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(shouldUseGenericApiKeyTitle(bind) ? getString(R.string.LlmApiKey) : getString(bind.getKey()));
        builder.setCustomViewOffset(0);

        LinearLayout ll = new LinearLayout(context);
        ll.setOrientation(LinearLayout.VERTICAL);

        boolean isPrompt = bind == NaConfig.INSTANCE.getLlmSystemPrompt() || bind == NaConfig.INSTANCE.getLlmUserPrompt();
        EditTextBoldCursor editText = isPrompt
                ? LlmEditTextFactory.createAndSetupMultilineEditText(context, resourcesProvider, bind.String(), hint, EditorInfo.IME_ACTION_DONE, true)
                : LlmEditTextFactory.createAndSetupEditText(context, resourcesProvider, bind.String(), hint, EditorInfo.IME_ACTION_DONE, true);
        ll.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 24, 0, 24, 0));

        CharSequence enhancedSubtitle = getEnhancedSubtitleWithLink(bind, subtitle);
        if (bind == NaConfig.INSTANCE.getLlmUserPrompt()) {
            enhancedSubtitle = makePlaceholdersClickable(enhancedSubtitle, editText);
        }
        builder.setMessage(enhancedSubtitle);

        builder.setView(ll);
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.setPositiveButton(getString(R.string.OK), null);

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            try {
                editText.requestFocus();
                editText.setSelection(editText.length());
                AndroidUtilities.showKeyboard(editText);
            } catch (Exception ignore) {
            }
        });
        showDialog(dialog);

        var button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (button != null) {
            button.setOnClickListener(v -> {
                String value = editText.getText() != null ? editText.getText().toString() : "";
                if (bind == NaConfig.INSTANCE.getLlmApiUrl()) {
                    LlmConfig.setSavedCustomBaseUrl(value);
                } else {
                    if (value.trim().isEmpty()) value = null;
                    bind.setConfigString(value);
                }
                if (listAdapter != null) {
                    listAdapter.notifyItemChanged(position);
                }
                AndroidUtilities.runOnUIThread(this::checkTemperatureRows, 500);
                dialog.dismiss();
            });
        }
    }

    private static boolean shouldUseGenericApiKeyTitle(ConfigItem bind) {
        return bind == NaConfig.INSTANCE.getLlmProviderOpenAIKey()
                || bind == NaConfig.INSTANCE.getLlmProviderGeminiKey()
                || bind == NaConfig.INSTANCE.getLlmProviderGroqKey()
                || bind == NaConfig.INSTANCE.getLlmProviderDeepSeekKey()
                || bind == NaConfig.INSTANCE.getLlmProviderXAIKey()
                || bind == NaConfig.INSTANCE.getLlmProviderCerebrasKey()
                || bind == NaConfig.INSTANCE.getLlmProviderOllamaCloudKey()
                || bind == NaConfig.INSTANCE.getLlmProviderOpenRouterKey()
                || bind == NaConfig.INSTANCE.getLlmProviderVercelAIGatewayKey();
    }

    private static CharSequence makePlaceholdersClickable(CharSequence subtitle, EditTextBoldCursor editText) {
        SpannableStringBuilder spannable = subtitle instanceof SpannableStringBuilder
                ? (SpannableStringBuilder) subtitle
                : new SpannableStringBuilder(subtitle);
        AndroidUtilities.replaceTags(spannable);

        Pattern pattern = Pattern.compile("@(?:text|toLang)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(spannable);
        while (matcher.find()) {
            String token = matcher.group();
            String placeholder = token.equalsIgnoreCase("@text") ? "@text" : "@toLang";
            spannable.setSpan(new ClickableSpan() {
                @Override
                public void updateDrawState(@NonNull TextPaint ds) {
                    super.updateDrawState(ds);
                    ds.setUnderlineText(false);
                }

                @Override
                public void onClick(@NonNull View widget) {
                    insertTextAtCursor(editText, placeholder);
                }
            }, matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return spannable;
    }

    private static void insertTextAtCursor(EditTextBoldCursor editText, String text) {
        if (editText == null || TextUtils.isEmpty(text)) {
            return;
        }
        try {
            editText.requestFocus();
            AndroidUtilities.showKeyboard(editText);
            Editable editable = editText.getText();
            if (editable == null) {
                return;
            }

            int start = editText.getSelectionStart();
            int end = editText.getSelectionEnd();
            if (start < 0 || end < 0) {
                start = end = editable.length();
            }
            int min = Math.min(start, end);
            int max = Math.max(start, end);
            editable.replace(min, max, text);
            int newCursor = min + text.length();
            editText.setSelection(newCursor, newCursor);
        } catch (Exception ignore) {
        }
    }

    private static final int MODEL_ITEM_TYPE_DEFAULT = 0;
    private static final int MODEL_ITEM_TYPE_MODEL = 1;
    private static final int MODEL_ITEM_TYPE_LOADING = 2;
    private static final int MODEL_ITEM_TYPE_ERROR = 3;
    private static final int MODEL_ITEM_TYPE_ERROR_DETAIL = 4;

    private record ModelDialogItem(int type, String text, String value) {
    }

    private static String formatModelNameForList(String modelName) {
        if (TextUtils.isEmpty(modelName)) {
            return "";
        }
        String s = modelName.trim();
        final int max = 28;
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "…";
    }

    private static void sortModelsForProvider(int preset, ArrayList<String> models) {
        if (preset == LlmPresetRegistry.OPENROUTER) {
            models.sort((a, b) -> {
                boolean aFree = LlmModelUtil.isOpenRouterFreeModel(a);
                boolean bFree = LlmModelUtil.isOpenRouterFreeModel(b);
                if (aFree != bFree) {
                    return aFree ? -1 : 1;
                }
                return a.compareToIgnoreCase(b);
            });
        } else {
            models.sort(String::compareToIgnoreCase);
        }
    }

    private static CharSequence highlightQueryInText(CharSequence text, String query, int highlightColor) {
        if (TextUtils.isEmpty(text) || TextUtils.isEmpty(query)) {
            return text;
        }
        String normalizedQuery = query.toLowerCase(Locale.ROOT).trim();
        if (normalizedQuery.isEmpty()) {
            return text;
        }
        String lowerText = text.toString().toLowerCase(Locale.ROOT);
        String[] parts = normalizedQuery.split("\\s+");
        SpannableStringBuilder highlighted = null;
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            int idx = 0;
            while (true) {
                int found = lowerText.indexOf(p, idx);
                if (found == -1) {
                    break;
                }
                if (highlighted == null) {
                    highlighted = new SpannableStringBuilder(text);
                }
                try {
                    highlighted.setSpan(new ForegroundColorSpan(highlightColor), found, found + p.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } catch (Exception ignore) {
                }
                idx = found + p.length();
            }
        }
        return highlighted != null ? highlighted : text;
    }

    private void showLlmModelDialog() {
        Context context = getParentActivity();
        if (context == null) return;
        var resourcesProvider = getResourceProvider();

        int preset = NaConfig.INSTANCE.getLlmProviderPreset().Int();
        String baseUrl = LlmConfig.getEffectiveBaseUrl(preset);
        String apiKey = LlmConfig.getFirstApiKey(preset);
        String defaultModel = LlmConfig.getDefaultModelName(preset);
        String initialText = LlmConfig.getEffectiveModelName(preset);
        final boolean pinDefaultModel;

        if (preset == 0) {
            String userUrl = NaConfig.INSTANCE.getLlmApiUrl().String();
            boolean hasCustomUrl = userUrl != null && !userUrl.trim().isEmpty();
            pinDefaultModel = !hasCustomUrl;
        } else {
            pinDefaultModel = true;
        }

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);

        final int dialogSideInsetDp = 24;
        final int listCellSideInsetDp = 20;
        final int listSidePaddingPx = dp(Math.max(0, dialogSideInsetDp - listCellSideInsetDp));

        EditTextBoldCursor editText = LlmEditTextFactory.createAndSetupEditText(
                context,
                resourcesProvider,
                initialText,
                getString(R.string.LlmModelName),
                EditorInfo.IME_ACTION_DONE,
                true
        );
        container.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, dialogSideInsetDp, 0, dialogSideInsetDp, 0));

        RecyclerListView listView = new RecyclerListView(context);
        listView.setOverScrollMode(OVER_SCROLL_NEVER);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setPadding(listSidePaddingPx, 0, listSidePaddingPx, dp(8));
        container.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f, 0, -12, 0, 0));

        ArrayList<String> allModels = new ArrayList<>();
        ArrayList<ModelDialogItem> items = new ArrayList<>();
        String[] rawError = new String[]{null};
        boolean[] isLoading = new boolean[]{false};
        boolean[] userEdited = new boolean[]{false};
        boolean[] suppressWatcher = new boolean[]{false};
        String[] currentQuery = new String[]{""};
        final AlertDialog[] dialogRef = new AlertDialog[]{null};

        Runnable rebuildItems = () -> {
            String q = editText.getText() != null ? editText.getText().toString().trim() : "";
            if (!userEdited[0]) {
                q = "";
            }
            q = q.toLowerCase(Locale.ROOT);
            currentQuery[0] = q;
            items.clear();
            if (pinDefaultModel) {
                items.add(new ModelDialogItem(MODEL_ITEM_TYPE_DEFAULT, defaultModel, getString(R.string.Default)));
            }

            if (isLoading[0]) {
                items.add(new ModelDialogItem(MODEL_ITEM_TYPE_LOADING, getString(R.string.Loading), null));
                if (listView.getAdapter() != null) {
                    listView.getAdapter().notifyDataSetChanged();
                }
                return;
            }

            if (rawError[0] != null) {
                if (!TextUtils.isEmpty(apiKey)) {
                    items.add(new ModelDialogItem(MODEL_ITEM_TYPE_ERROR, getString(R.string.LlmModelsLoadFailed), null));
                }
                items.add(new ModelDialogItem(MODEL_ITEM_TYPE_ERROR_DETAIL, rawError[0], null));
                if (listView.getAdapter() != null) {
                    listView.getAdapter().notifyDataSetChanged();
                }
                return;
            }
            String[] parts = q.isEmpty() ? new String[0] : q.split("\\s+");
            for (String model : allModels) {
                if (TextUtils.isEmpty(model)) continue;
                if (pinDefaultModel && model.equals(defaultModel)) continue;
                String lower = model.toLowerCase(Locale.ROOT);
                boolean ok = true;
                for (String p : parts) {
                    if (p.isEmpty()) continue;
                    if (!lower.contains(p)) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    items.add(new ModelDialogItem(MODEL_ITEM_TYPE_MODEL, model, null));
                }
            }
            if (listView.getAdapter() != null) {
                listView.getAdapter().notifyDataSetChanged();
            }
        };

        RecyclerListView.SelectionAdapter adapter = new RecyclerListView.SelectionAdapter() {
            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                int position = holder.getAdapterPosition();
                if (position < 0 || position >= items.size()) {
                    return false;
                }
                int type = items.get(position).type;
                return type == MODEL_ITEM_TYPE_MODEL
                        || type == MODEL_ITEM_TYPE_DEFAULT
                        || type == MODEL_ITEM_TYPE_ERROR;
            }

            @Override
            public int getItemCount() {
                return items.size();
            }

            @Override
            public int getItemViewType(int position) {
                if (position < 0 || position >= items.size()) return 0;
                int type = items.get(position).type;
                if (type == MODEL_ITEM_TYPE_ERROR_DETAIL) {
                    return 2;
                }
                return 0;
            }

            @NonNull
            @Override
            public RecyclerListView.Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view;
                if (viewType == 0) {
                    TextSettingsCell cell = new TextSettingsCell(context, listCellSideInsetDp, resourcesProvider);
                    cell.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
                    view = cell;
                } else {
                    TextInfoPrivacyCell cell = new TextInfoPrivacyCell(context, listCellSideInsetDp, resourcesProvider);
                    cell.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
                    cell.setTextColorByKey(Theme.key_dialogTextGray3);
                    view = cell;
                }
                view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                ModelDialogItem item = items.get(position);
                boolean divider = position < items.size() - 1;
                String query = currentQuery[0];
                int highlightColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider);
                switch (holder.itemView) {
                    case TextSettingsCell cell -> {
                        if (item.type == MODEL_ITEM_TYPE_DEFAULT) {
                            CharSequence text = formatModelNameForList(item.text);
                            text = highlightQueryInText(text, query, highlightColor);
                            cell.setTextAndValue(text, item.value, divider);
                            cell.setIcon(0);
                        } else if (item.type == MODEL_ITEM_TYPE_LOADING) {
                            cell.setText(item.text, divider);
                            cell.setIcon(0);
                        } else if (item.type == MODEL_ITEM_TYPE_MODEL) {
                            CharSequence text = highlightQueryInText(item.text, query, highlightColor);
                            cell.setText(text, divider);
                            cell.setIcon(0);
                        } else {
                            cell.setText(item.text, divider);
                            cell.setIcon(item.type == MODEL_ITEM_TYPE_ERROR ? R.drawable.msg_retry : 0);
                        }
                    }
                    case TextInfoPrivacyCell cell -> cell.setText(item.text);
                    default -> {
                    }
                }
            }
        };

        listView.setAdapter(adapter);

        Runnable loadModels = () -> {
            if (isLoading[0]) return;
            if (TextUtils.isEmpty(apiKey)) {
                allModels.clear();
                rawError[0] = getString(R.string.ApiKeyNotSet);
                rebuildItems.run();
                return;
            }
            isLoading[0] = true;
            rawError[0] = null;
            allModels.clear();
            rebuildItems.run();

            Utilities.globalQueue.postRunnable(() -> {
                OpenAICompatClient.LlmResponse<List<String>> res = OpenAICompatClient.fetchModels(baseUrl, apiKey);
                AndroidUtilities.runOnUIThread(() -> {
                    AlertDialog activeDialog = dialogRef[0];
                    if (activeDialog == null || !activeDialog.isShowing()) {
                        return;
                    }
                    isLoading[0] = false;
                    if (res.isSuccess() && res.data() != null) {
                        allModels.addAll(res.data());
                        sortModelsForProvider(preset, allModels);
                    } else {
                        rawError[0] = res.error() != null ? res.error() : getString(R.string.UnknownError);
                    }
                    rebuildItems.run();
                });
            });
        };

        listView.setOnItemClickListener((view, position, x, y) -> {
            if (position < 0 || position >= items.size()) return;
            ModelDialogItem item = items.get(position);
            if (item.type == MODEL_ITEM_TYPE_MODEL || item.type == MODEL_ITEM_TYPE_DEFAULT) {
                suppressWatcher[0] = true;
                editText.setText(item.text);
                editText.setSelection(editText.length());
                suppressWatcher[0] = false;
                userEdited[0] = false;
                rebuildItems.run();
            } else if (item.type == MODEL_ITEM_TYPE_ERROR) {
                loadModels.run();
            }
        });

        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (suppressWatcher[0]) {
                    return;
                }
                userEdited[0] = true;
                rebuildItems.run();
            }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(getString(R.string.LlmModelName));

        final int screenHeightDp = (int) (AndroidUtilities.displaySize.y / AndroidUtilities.density);
        final int viewHeightDp = Math.max(320, Math.min(600, (int) (screenHeightDp * 0.6f)));

        builder.setView(container, viewHeightDp);
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.setPositiveButton(getString(R.string.OK), null);
        builder.setNeutralButton(getString(R.string.LlmTest), null);
        AlertDialog dialog = builder.create();
        dialogRef[0] = dialog;

        final ScrollView[] contentScrollView = new ScrollView[]{null};
        final ViewTreeObserver.OnGlobalLayoutListener[] globalLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener[]{null};
        final AlertDialog[] testResultDialog = new AlertDialog[]{null};

        dialog.setOnShowListener(d -> {
            editText.requestFocus();
            editText.setSelection(editText.length());
            AndroidUtilities.showKeyboard(editText);

            container.post(() -> {
                ViewParent parent = container.getParent();
                if (!(parent instanceof ViewGroup scrollContainer)) {
                    return;
                }
                ViewParent scrollParent = scrollContainer.getParent();
                if (!(scrollParent instanceof ScrollView scrollView)) {
                    return;
                }
                contentScrollView[0] = scrollView;
                globalLayoutListener[0] = () -> {
                    if (scrollView.getHeight() <= 0) {
                        return;
                    }
                    int editHeight = editText.getMeasuredHeight();
                    if (editHeight <= 0) {
                        return;
                    }
                    int maxListHeight = scrollView.getHeight() - editHeight;
                    if (maxListHeight <= 0) {
                        return;
                    }
                    int listPaddingTop = listView.getPaddingTop();
                    int listPaddingBottom = listView.getPaddingBottom();
                    int maxCellsArea = maxListHeight - listPaddingTop - listPaddingBottom;
                    if (maxCellsArea <= 0) {
                        return;
                    }

                    int cellHeight = 0;
                    View sampleChild = listView.getChildAt(0);
                    if (sampleChild != null) {
                        cellHeight = sampleChild.getMeasuredHeight();
                    }
                    if (cellHeight <= 0) {
                        cellHeight = dp(50) + (items.size() > 1 ? 1 : 0);
                    }

                    int rows = Math.max(1, maxCellsArea / cellHeight);
                    int desiredListHeight = rows * cellHeight + listPaddingTop + listPaddingBottom;

                    ViewGroup.LayoutParams rawListLp = listView.getLayoutParams();
                    if (rawListLp instanceof LinearLayout.LayoutParams listLp) {
                        if (listLp.height != desiredListHeight || listLp.weight != 0f) {
                            listLp.height = desiredListHeight;
                            listLp.weight = 0f;
                            listView.setLayoutParams(listLp);
                        }
                    } else if (rawListLp != null && rawListLp.height != desiredListHeight) {
                        rawListLp.height = desiredListHeight;
                        listView.setLayoutParams(rawListLp);
                    }

                    int desiredContainerHeight = editHeight + desiredListHeight;
                    ViewGroup.LayoutParams rawContainerLp = container.getLayoutParams();
                    if (rawContainerLp != null && rawContainerLp.height != desiredContainerHeight) {
                        rawContainerLp.height = desiredContainerHeight;
                        container.setLayoutParams(rawContainerLp);
                    }

                    ViewGroup.LayoutParams containerLp = container.getLayoutParams();
                    if (containerLp != null && containerLp.height > scrollView.getHeight()) {
                        containerLp.height = scrollView.getHeight();
                        container.setLayoutParams(containerLp);
                    }
                    if (scrollView.getScrollY() != 0) {
                        scrollView.scrollTo(0, 0);
                    }
                };
                scrollView.getViewTreeObserver().addOnGlobalLayoutListener(globalLayoutListener[0]);
                globalLayoutListener[0].onGlobalLayout();
            });
            loadModels.run();
        });
        showDialog(dialog, d -> {
            dialogRef[0] = null;
            AlertDialog rDialog = testResultDialog[0];
            if (rDialog != null) {
                testResultDialog[0] = null;
                rDialog.dismiss();
            }

            ScrollView scrollView = contentScrollView[0];
            ViewTreeObserver.OnGlobalLayoutListener listener = globalLayoutListener[0];
            if (scrollView == null || listener == null) {
                return;
            }
            try {
                ViewTreeObserver observer = scrollView.getViewTreeObserver();
                if (observer.isAlive()) {
                    observer.removeOnGlobalLayoutListener(listener);
                }
            } catch (Exception ignore) {
            }
        });

        var okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (okButton != null) {
            okButton.setOnClickListener(v -> {
                String value = editText.getText() != null ? editText.getText().toString().trim() : "";
                if (value.isEmpty() || value.equals(defaultModel)) {
                    value = "";
                }
                LlmConfig.setSavedModelName(preset, value);
                int idx = cellGroup.rows.indexOf(llmModelRow);
                if (listAdapter != null && idx >= 0) {
                    listAdapter.notifyItemChanged(idx);
                }
                checkTemperatureRows();
                dialog.dismiss();
            });
        }

        View testButtonView = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (testButtonView instanceof TextView testButton) {
            testButton.setOnClickListener(v -> {
                String value = editText.getText() != null ? editText.getText().toString().trim() : "";
                String modelToTest = value.isEmpty() ? defaultModel : value;
                testButton.setEnabled(false);
                String originalText = testButton.getText() != null ? testButton.getText().toString() : "";
                testButton.setText(getString(R.string.Loading));

                Utilities.globalQueue.postRunnable(() -> {
                    OpenAICompatClient.LlmResponse<String> res = OpenAICompatClient.testChatCompletions(baseUrl, apiKey, modelToTest);
                    AndroidUtilities.runOnUIThread(() -> {
                        testButton.setEnabled(true);
                        testButton.setText(originalText);
                        if (!dialog.isShowing()) {
                            return;
                        }
                        AlertDialog.Builder rBuilder = new AlertDialog.Builder(context, resourcesProvider);
                        rBuilder.setTitle(getString(R.string.LlmTest));
                        SpannableStringBuilder message = new SpannableStringBuilder();
                        message.append(getString(R.string.LlmModelName)).append(": ").append(modelToTest).append('\n');
                        int httpCode = res.httpCode();
                        message.append(getString(R.string.LlmTestResultHttpStatusCode)).append(": ").append(httpCode > 0 ? String.valueOf(httpCode) : "N/A").append('\n');
                        message.append(getString(R.string.LlmTestResultDuration)).append(": ").append(String.valueOf(res.durationMs())).append(" ms").append("\n\n");
                        if (res.isSuccess()) {
                            message.append(getString(R.string.LlmTestResultResponse)).append(":\n").append(res.data() != null ? res.data() : getString(R.string.OK));
                        } else {
                            message.append(getString(R.string.ErrorOccurred)).append("\n").append(res.error() != null ? res.error() : getString(R.string.UnknownError));
                        }
                        rBuilder.setMessage(message);
                        rBuilder.setPositiveButton(getString(R.string.OK), null);
                        AlertDialog previousDialog = testResultDialog[0];
                        if (previousDialog != null) {
                            testResultDialog[0] = null;
                            previousDialog.dismiss();
                        }
                        AlertDialog resultDialog = rBuilder.create();
                        testResultDialog[0] = resultDialog;
                        resultDialog.setCanceledOnTouchOutside(true);
                        resultDialog.setOnDismissListener(d2 -> {
                            if (testResultDialog[0] == resultDialog) {
                                testResultDialog[0] = null;
                            }
                        });
                        resultDialog.show();
                    });
                });
            });
        }
    }
}
