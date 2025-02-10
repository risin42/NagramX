package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BlurredRecyclerView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SeekBarView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.hutool.core.util.StrUtil;
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
import tw.nekomimi.nekogram.transtale.Translator;
import tw.nekomimi.nekogram.transtale.TranslatorKt;
import tw.nekomimi.nekogram.ui.BottomBuilder;
import tw.nekomimi.nekogram.ui.PopupBuilder;
import xyz.nextalone.nagram.NaConfig;

public class NekoTranslatorSettingsActivity extends BaseNekoXSettingsActivity {

    private final CellGroup cellGroup = new CellGroup(this);

    private final AbstractConfigCell headerTranslation = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.Translate)));
    private final AbstractConfigCell translationProviderRow = cellGroup.appendCell(new ConfigCellCustom(NekoConfig.translationProvider.getKey(), CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL, true));
    private final AbstractConfigCell translatorModeRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NaConfig.INSTANCE.getTranslatorMode(),
            new String[]{
                    getString(R.string.TranslatorModeAppend),
                    getString(R.string.TranslatorModeInline),
                    getString(R.string.TranslatorModePopup),
            }, null));
    private final AbstractConfigCell useTelegramUIAutoTranslateRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getTelegramUIAutoTranslate()));
    private final AbstractConfigCell translateToLangRow = cellGroup.appendCell(new ConfigCellCustom("TranslateToLang", CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL, true));
    private final AbstractConfigCell translateInputToLangRow = cellGroup.appendCell(new ConfigCellCustom("TranslateInputToLang", CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL, true));
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
    private final AbstractConfigCell googleCloudTranslateKeyRow = cellGroup.appendCell(new ConfigCellTextDetail(NekoConfig.googleCloudTranslateKey, (view, position) -> {
        customDialog_BottomInputString(position, NekoConfig.googleCloudTranslateKey, getString(R.string.GoogleCloudTransKeyNotice), "Key");
    }, getString(R.string.None), true));

    private final AbstractConfigCell dividerTranslation = cellGroup.appendCell(new ConfigCellDivider());

    // AI Translator
    private final AbstractConfigCell headerAITranslatorSettings = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.AITranslatorSettings)));
    private final AbstractConfigCell llmProviderRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NaConfig.INSTANCE.getLlmProviderPreset(),
            new String[]{
                    getString(R.string.LlmProviderCustom),
                    getString(R.string.LlmProviderOpenAI),
                    getString(R.string.LlmProviderGemini),
                    getString(R.string.LlmProviderGroq),
                    getString(R.string.LlmProviderDeepSeek),
                    getString(R.string.LlmProviderXAI),
            }, null));

    private final Map<Integer, List<AbstractConfigCell>> llmProviderConfigMap = new HashMap<>();
    private List<AbstractConfigCell> llmProviderConfigRows = new ArrayList<>();

    {
        llmProviderConfigMap.put(0, List.of(  // Custom
                new ConfigCellTextDetail(NaConfig.INSTANCE.getLlmApiKey(), (view, position) -> {
                    customDialog_BottomInputString(position, NaConfig.INSTANCE.getLlmApiKey(), getString(R.string.LlmApiKeyNotice), "Key");
                }, getString(R.string.None), true),
                new ConfigCellTextDetail(NaConfig.INSTANCE.getLlmApiUrl(), (view, position) -> {
                    customDialog_BottomInputString(position, NaConfig.INSTANCE.getLlmApiUrl(), getString(R.string.LlmApiUrlNotice), "e.g. https://api.openai.com/v1");
                }, getString(R.string.LlmApiUrlDefault)),
                new ConfigCellTextDetail(NaConfig.INSTANCE.getLlmModelName(), (view, position) -> {
                    customDialog_BottomInputString(position, NaConfig.INSTANCE.getLlmModelName(), getString(R.string.LlmModelNameNotice), "e.g. gpt-4o-mini");
                }, getString(R.string.LlmModelNameDefault))));
        llmProviderConfigMap.put(1, List.of( // OpenAI
                new ConfigCellTextDetail(NaConfig.INSTANCE.getLlmProviderOpenAIKey(), (view, position) -> {
                    customDialog_BottomInputString(position, NaConfig.INSTANCE.getLlmProviderOpenAIKey(), getString(R.string.LlmApiKeyNotice), "Key");
                }, getString(R.string.None), true)));
        llmProviderConfigMap.put(2, List.of( // Gemini
                new ConfigCellTextDetail(NaConfig.INSTANCE.getLlmProviderGeminiKey(), (view, position) -> {
                    customDialog_BottomInputString(position, NaConfig.INSTANCE.getLlmProviderGeminiKey(), getString(R.string.LlmApiKeyNotice), "Key");
                }, getString(R.string.None), true)));
        llmProviderConfigMap.put(3, List.of( // Groq
                new ConfigCellTextDetail(NaConfig.INSTANCE.getLlmProviderGroqKey(), (view, position) -> {
                    customDialog_BottomInputString(position, NaConfig.INSTANCE.getLlmProviderGroqKey(), getString(R.string.LlmApiKeyNotice), "Key");
                }, getString(R.string.None), true)));
        llmProviderConfigMap.put(4, List.of( // DeepSeek
                new ConfigCellTextDetail(NaConfig.INSTANCE.getLlmProviderDeepSeekKey(), (view, position) -> {
                    customDialog_BottomInputString(position, NaConfig.INSTANCE.getLlmProviderDeepSeekKey(), getString(R.string.LlmApiKeyNotice), "Key");
                }, getString(R.string.None), true)));
        llmProviderConfigMap.put(5, List.of( // xAI
                new ConfigCellTextDetail(NaConfig.INSTANCE.getLlmProviderXAIKey(), (view, position) -> {
                    customDialog_BottomInputString(position, NaConfig.INSTANCE.getLlmProviderXAIKey(), getString(R.string.LlmApiKeyNotice), "Key");
                }, getString(R.string.None), true)));

        int llmProviderPreset = NaConfig.INSTANCE.getLlmProviderPreset().Int();
        if (llmProviderConfigMap.containsKey(llmProviderPreset)) {
            llmProviderConfigRows.addAll(llmProviderConfigMap.get(llmProviderPreset));
            llmProviderConfigRows.forEach(cellGroup::appendCell);
        }
    }

    private final AbstractConfigCell llmSystemPromptRow = cellGroup.appendCell(new ConfigCellTextDetail(NaConfig.INSTANCE.getLlmSystemPrompt(), (view, position) -> {
        customDialog_BottomInputString(position, NaConfig.INSTANCE.getLlmSystemPrompt(), getString(R.string.LlmSystemPromptNotice), "You are a helpful assistant.");
    }, getString(R.string.None)));
    private final AbstractConfigCell llmUserPromptRow = cellGroup.appendCell(new ConfigCellTextDetail(NaConfig.INSTANCE.getLlmUserPrompt(), (view, position) -> {
        customDialog_BottomInputString(position, NaConfig.INSTANCE.getLlmUserPrompt(), getString(R.string.LlmUserPromptNotice), "");
    }, getString(R.string.None)));
    private final AbstractConfigCell header_temperature = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.LlmTemperature)));
    private final AbstractConfigCell temperatureValueRow = cellGroup.appendCell(new ConfigCellCustom(getString(R.string.LlmTemperature), ConfigCellCustom.CUSTOM_ITEM_Temperature, true));
    private final AbstractConfigCell dividerAITranslatorSettings = cellGroup.appendCell(new ConfigCellDivider());

    private final AbstractConfigCell headerArticleTranslation = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.InstantViewTranslation)));
    private final AbstractConfigCell enableSeparateArticleTranslatorRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getEnableSeparateArticleTranslator()));
    private final AbstractConfigCell articleTranslationProviderRow = cellGroup.appendCell(new ConfigCellCustom("ArticleTranslationProvider", CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL, true));
    private final AbstractConfigCell dividerArticleTranslation = cellGroup.appendCell(new ConfigCellDivider());

    private ListAdapter listAdapter;
    private int oldLlmProvider;

    public NekoTranslatorSettingsActivity() {
        if (!NaConfig.INSTANCE.getEnableSeparateArticleTranslator().Bool()) {
            cellGroup.rows.remove(articleTranslationProviderRow);
        }

        oldLlmProvider = NaConfig.INSTANCE.getLlmProviderPreset().Int();

        addRowsToMap(cellGroup);
    }

    protected void onItemClick(View view, int position, float x, float y) {
        if (position == cellGroup.rows.indexOf(translationProviderRow)) {
            showProviderSelectionPopup(view, NekoConfig.translationProvider, () -> {
                if (NekoConfig.translationProvider.Int() == Translator.providerTelegram) {
                    boolean isAutoTranslateEnabled = NaConfig.INSTANCE.getTelegramUIAutoTranslate().Bool();
                    boolean isRealPremium = UserConfig.getInstance(currentAccount).isRealPremium();
                    if (isAutoTranslateEnabled && !isRealPremium) {
                        NaConfig.INSTANCE.getTelegramUIAutoTranslate().setConfigBool(false);
                        listAdapter.notifyItemChanged(cellGroup.rows.indexOf(useTelegramUIAutoTranslateRow));
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.info, getString(R.string.TelegramUIAutoTranslateTips)).show();
                    }
                }
                listAdapter.notifyItemChanged(position);
            });
        } else if (position == cellGroup.rows.indexOf(articleTranslationProviderRow)) {
            showProviderSelectionPopup(view, NaConfig.INSTANCE.getArticleTranslationProvider(), () -> {
                listAdapter.notifyItemChanged(position);
            });
        } else if (position == cellGroup.rows.indexOf(translateToLangRow) || position == cellGroup.rows.indexOf(translateInputToLangRow)) {
            Translator.showTargetLangSelect(view, position == cellGroup.rows.indexOf(translateInputToLangRow), (locale) -> {
                if (position == cellGroup.rows.indexOf(translateToLangRow)) {
                    NekoConfig.translateToLang.setConfigString(TranslatorKt.getLocale2code(locale));
                } else {
                    NekoConfig.translateInputLang.setConfigString(TranslatorKt.getLocale2code(locale));
                }
                listAdapter.notifyItemChanged(position);
                return Unit.INSTANCE;
            });
        }
    }

    @Override
    protected void updateRows() {
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private void customDialog_BottomInputString(int position, ConfigItem bind, String subtitle, String hint) {
        BottomBuilder builder = new BottomBuilder(getParentActivity());
        builder.addTitle(getString(bind.getKey()), subtitle);
        var keyField = builder.addEditText(hint);
        if (StrUtil.isNotBlank(bind.String())) {
            keyField.setText(bind.String());
        }
        builder.addCancelButton();
        builder.addOkButton((it) -> {
            String key = keyField.getText().toString();
            if (StrUtil.isBlank(key)) key = null;
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

    private static class ProviderInfo {
        public final int providerConstant;
        public final int nameResId;

        public ProviderInfo(int providerConstant, int nameResId) {
            this.providerConstant = providerConstant;
            this.nameResId = nameResId;
        }

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
        if (listView.getItemAnimator() != null) {
            ((DefaultItemAnimator) listView.getItemAnimator()).setSupportsChangeAnimations(false);
        }
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        listView.setAdapter(listAdapter);

        // Fragment: Set OnClick Callbacks
        listView.setOnItemClickListener((view, position, x, y) -> {
            AbstractConfigCell a = cellGroup.rows.get(position);
            if (a instanceof ConfigCellTextCheck) {
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
                    } catch (Exception e) {
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
                                BulletinFactory.of(this).createSimpleBulletin(R.raw.info, getString(R.string.TelegramUIAutoTranslateTips)).show();
                            }
                        }
                        listAdapter.notifyItemChanged(position);
                    });
                } else if (position == cellGroup.rows.indexOf(translateToLangRow)) {
                    Translator.showTargetLangSelect(view, false, (locale) -> {
                        NekoConfig.translateToLang.setConfigString(TranslatorKt.getLocale2code(locale));
                        listAdapter.notifyItemChanged(position);
                        return Unit.INSTANCE;
                    });
                } else if (position == cellGroup.rows.indexOf(translateInputToLangRow)) {
                    Translator.showTargetLangSelect(view, true, (locale) -> {
                        NekoConfig.translateInputLang.setConfigString(TranslatorKt.getLocale2code(locale));
                        listAdapter.notifyItemChanged(position);
                        return Unit.INSTANCE;
                    });
                } else if (position == cellGroup.rows.indexOf(articleTranslationProviderRow)) {
                    showProviderSelectionPopup(view, NaConfig.INSTANCE.getArticleTranslationProvider(), () -> {
                        listAdapter.notifyItemChanged(position);
                    });
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
            if (key.equals(NaConfig.INSTANCE.getTelegramUIAutoTranslate().getKey())) {
                boolean enabled = (Boolean) newValue;
                if (enabled && NekoConfig.translationProvider.Int() == Translator.providerTelegram) {
                    boolean isAutoTranslateEnabled = NaConfig.INSTANCE.getTelegramUIAutoTranslate().Bool();
                    boolean isRealPremium = UserConfig.getInstance(currentAccount).isRealPremium();
                    if (isAutoTranslateEnabled && !isRealPremium) {
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.info, getString(R.string.TelegramUIAutoTranslateTips)).show();
                    }
                }
            } else if (key.equals(NaConfig.INSTANCE.getPreferredTranslateTargetLang().getKey())) {
                listAdapter.notifyItemChanged(cellGroup.rows.indexOf(translateToLangRow));
                listAdapter.notifyItemChanged(cellGroup.rows.indexOf(translateInputToLangRow));
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

                cellGroup.rows.clear();
                cellGroup.appendCell(headerTranslation);
                cellGroup.appendCell(translationProviderRow);
                cellGroup.appendCell(translatorModeRow);
                cellGroup.appendCell(useTelegramUIAutoTranslateRow);
                cellGroup.appendCell(translateToLangRow);
                cellGroup.appendCell(translateInputToLangRow);
                cellGroup.appendCell(preferredTranslateTargetLangRow);
                cellGroup.appendCell(googleCloudTranslateKeyRow);
                cellGroup.appendCell(dividerTranslation);

                cellGroup.appendCell(headerAITranslatorSettings);
                cellGroup.appendCell(llmProviderRow);
                List<AbstractConfigCell> newLlmProviderConfigRows = llmProviderConfigMap.get(newLlmProvider);
                if (newLlmProviderConfigRows != null) {
                    newLlmProviderConfigRows.forEach(cellGroup::appendCell);
                }
                cellGroup.appendCell(llmSystemPromptRow);
                cellGroup.appendCell(llmUserPromptRow);
                cellGroup.appendCell(header_temperature);
                cellGroup.appendCell(temperatureValueRow);
                cellGroup.appendCell(dividerAITranslatorSettings);

                cellGroup.appendCell(headerArticleTranslation);
                cellGroup.appendCell(enableSeparateArticleTranslatorRow);
                if (NaConfig.INSTANCE.getEnableSeparateArticleTranslator().Bool()) {
                    cellGroup.appendCell(articleTranslationProviderRow);
                }
                cellGroup.appendCell(dividerArticleTranslation);

                oldLlmProvider = newLlmProvider;
                updateRows();
            }
        };
        return fragmentView;
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private Context mContext;

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
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            AbstractConfigCell a = cellGroup.rows.get(position);
            if (a != null) {
                if (a instanceof ConfigCellCustom) {
                    if (holder.itemView instanceof TextSettingsCell) {
                        TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                        if (position == cellGroup.rows.indexOf(translationProviderRow)) {
                            textCell.setTextAndValue(getString(R.string.TranslationProvider), getProviderName(NekoConfig.translationProvider.Int()), true);
                        } else if (position == cellGroup.rows.indexOf(translateToLangRow)) {
                            textCell.setTextAndValue(getString(R.string.TransToLang), NekoXConfig.formatLang(NekoConfig.translateToLang.String()), true);
                        } else if (position == cellGroup.rows.indexOf(translateInputToLangRow)) {
                            textCell.setTextAndValue(getString(R.string.TransInputToLang), NekoXConfig.formatLang(NekoConfig.translateInputLang.String()), true);
                        } else if (position == cellGroup.rows.indexOf(articleTranslationProviderRow)) {
                            textCell.setTextAndValue(getString(R.string.ArticleTranslationProvider), getProviderName(NaConfig.INSTANCE.getArticleTranslationProvider().Int()), true);
                        }
                    }
                } else {
                    a.onBindViewHolder(holder);
                }
            }
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
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

    private class TemperatureSeekBar extends FrameLayout {

        private final SeekBarView sizeBar;
        private final TextPaint textPaint;

        public TemperatureSeekBar(Context context) {
            super(context);

            setWillNotDraw(false);

            textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextSize(AndroidUtilities.dp(16));

            sizeBar = new SeekBarView(context);
            sizeBar.setReportChanges(true);
            sizeBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
                @Override
                public void onSeekBarDrag(boolean stop, float progress) {
                    float value = Math.round(progress * 20) / 10f;
                    NaConfig.INSTANCE.getLlmTemperature().setConfigFloat(value);
                    invalidate();
                }

                @Override
                public void onSeekBarPressed(boolean pressed) {
                }
            });
            float currentValue = NaConfig.INSTANCE.getLlmTemperature().Float();
            sizeBar.setProgress(currentValue / 2f);
            addView(sizeBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, Gravity.LEFT | Gravity.TOP, 9, 5, 43, 11));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
            String text = String.format("%.1f", NaConfig.INSTANCE.getLlmTemperature().Float());
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
    public int getBaseGuid() {
        return 13000;
    }

    @Override
    public int getDrawable() {
        return R.drawable.ic_translate;
    }

    @Override
    public String getTitle() {
        return LocaleController.getString(R.string.TranslatorSettings);
    }
}