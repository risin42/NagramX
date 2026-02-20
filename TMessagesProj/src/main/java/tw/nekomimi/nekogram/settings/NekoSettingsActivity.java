package tw.nekomimi.nekogram.settings;

import static android.view.View.OVER_SCROLL_NEVER;
import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.net.Uri;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.radolyn.ayugram.messages.AyuSavePreferences;
import com.radolyn.ayugram.utils.AyuGhostPreferences;

import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.BasePermissionsActivity;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.SettingsSearchCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.FilledTabsView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ViewPagerFixed;
import org.telegram.ui.DocumentSelectActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PeerColorActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import kotlin.text.StringsKt;
import tw.nekomimi.nekogram.DatacenterActivity;
import tw.nekomimi.nekogram.DialogConfig;
import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.helpers.AppRestartHelper;
import tw.nekomimi.nekogram.helpers.CloudSettingsHelper;
import tw.nekomimi.nekogram.helpers.LocalNameHelper;
import tw.nekomimi.nekogram.helpers.PasscodeHelper;
import tw.nekomimi.nekogram.helpers.SettingsHelper;
import tw.nekomimi.nekogram.helpers.SettingsSearchResult;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;
import tw.nekomimi.nekogram.utils.AlertUtil;
import tw.nekomimi.nekogram.utils.FileUtil;
import tw.nekomimi.nekogram.utils.GsonUtil;
import tw.nekomimi.nekogram.utils.ShareUtil;
import xyz.nextalone.nagram.NaConfig;
import xyz.nextalone.nagram.helper.BookmarksHelper;
import xyz.nextalone.nagram.helper.LocalPeerColorHelper;
import xyz.nextalone.nagram.helper.LocalPremiumStatusHelper;

public class NekoSettingsActivity extends BaseFragment {
    public static final int PAGE_TYPE = 0;
    public static final int PAGE_ABOUT = 1;

    private Page typePage;
    private Page abountPage;

    private ViewPagerFixed viewPager;

    private ImageView backButton;
    private ImageView syncButton;
    private ImageView searchButton;

    private FrameLayout actionBarContainer;
    private FilledTabsView tabsView;

    private boolean startAtAbout;

    public NekoSettingsActivity startOnAbout() {
        this.startAtAbout = true;
        return this;
    }

    @Override
    public View createView(Context context) {
        typePage = new Page(context, PAGE_TYPE);
        abountPage = new Page(context, PAGE_ABOUT);

        actionBar.setCastShadows(false);
        actionBar.setVisibility(View.GONE);
        actionBar.setAllowOverlayTitle(false);

        FrameLayout frameLayout = getFrameLayout(context);

        PeerColorActivity.ColoredActionBar colorBar = new PeerColorActivity.ColoredActionBar(context, resourceProvider) {
            @Override
            protected void onUpdateColor() {
                updateActionBarButtonsColor();
                if (tabsView != null) {
                    tabsView.setBackgroundColor(getTabsViewBackgroundColor());
                }
            }

            private int lastBtnColor = 0;

            public void updateActionBarButtonsColor() {
                final int btnColor = getActionBarButtonColor();
                if (lastBtnColor != btnColor) {
                    if (backButton != null) {
                        lastBtnColor = btnColor;
                        backButton.setColorFilter(new PorterDuffColorFilter(btnColor, PorterDuff.Mode.SRC_IN));
                    }
                    if (syncButton != null) {
                        lastBtnColor = btnColor;
                        syncButton.setColorFilter(new PorterDuffColorFilter(btnColor, PorterDuff.Mode.SRC_IN));
                    }
                    if (searchButton != null) {
                        lastBtnColor = btnColor;
                        searchButton.setColorFilter(new PorterDuffColorFilter(btnColor, PorterDuff.Mode.SRC_IN));
                    }
                }
            }
        };
        frameLayout.addView(colorBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL));

        viewPager = new ViewPagerFixed(context) {
            @Override
            public void onTabAnimationUpdate(boolean manual) {
                tabsView.setSelected(viewPager.getPositionAnimated());
            }
        };
        viewPager.setAdapter(new ViewPagerFixed.Adapter() {
            @Override
            public int getItemCount() {
                return 2;
            }

            @Override
            public View createView(int viewType) {
                if (viewType == PAGE_TYPE) return typePage;
                if (viewType == PAGE_ABOUT) return abountPage;
                return null;
            }

            @Override
            public int getItemViewType(int position) {
                return position;
            }

            @Override
            public void bindView(View view, int position, int viewType) {

            }
        });
        frameLayout.addView(viewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        actionBarContainer = new FrameLayout(context);
        frameLayout.addView(actionBarContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL));

        tabsView = new FilledTabsView(context);
        tabsView.setTabs(getString(R.string.Categories), getString(R.string.About));
        tabsView.onTabSelected(tab -> {
            if (viewPager != null) {
                viewPager.scrollToPosition(tab);
            }
        });
        actionBarContainer.addView(tabsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 40, Gravity.CENTER));

        if (startAtAbout) {
            viewPager.setPosition(1);
            if (tabsView != null) {
                tabsView.setSelected(1);
            }
        }

        backButton = new ImageView(context);
        backButton.setScaleType(ImageView.ScaleType.CENTER);
        backButton.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_actionBarWhiteSelector), Theme.RIPPLE_MASK_CIRCLE_20DP));
        backButton.setImageResource(R.drawable.ic_ab_back);
        backButton.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        backButton.setOnClickListener(v -> {
            if (onBackPressed(true)) {
                finishFragment();
            }
        });
        actionBarContainer.addView(backButton, LayoutHelper.createFrame(54, 54, Gravity.LEFT | Gravity.CENTER_VERTICAL));

        syncButton = new ImageView(context);
        syncButton.setScaleType(ImageView.ScaleType.CENTER);
        syncButton.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), Theme.RIPPLE_MASK_CIRCLE_20DP));
        syncButton.setImageResource(R.drawable.cloud_sync);
        syncButton.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        syncButton.setOnClickListener(v -> CloudSettingsHelper.getInstance().showDialog(NekoSettingsActivity.this));
        actionBarContainer.addView(syncButton, LayoutHelper.createFrame(54, 54, Gravity.RIGHT | Gravity.CENTER_VERTICAL));

        searchButton = new ImageView(context);
        searchButton.setScaleType(ImageView.ScaleType.CENTER);
        searchButton.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), Theme.RIPPLE_MASK_CIRCLE_20DP));
        searchButton.setImageResource(R.drawable.ic_ab_search);
        searchButton.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        searchButton.setOnClickListener(v -> showSettingsSearchDialog());
        actionBarContainer.addView(searchButton, LayoutHelper.createFrame(54, 54, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 42, 0));

        FrameLayout contentView;
        fragmentView = contentView = frameLayout;

        return contentView;
    }

    /**
     * @noinspection SizeReplaceableByIsEmpty
     */
    private void showSettingsSearchDialog() {
        try {
            Activity parent = getParentActivity();
            if (parent == null) return;

            ArrayList<SettingsSearchResult> results = SettingsHelper.onCreateSearchArray(fragment -> AndroidUtilities.runOnUIThread(() -> {
                try {
                    presentFragment(fragment);
                } catch (Exception ignore) {
                }
            }));

            final ArrayList<SettingsSearchResult> filtered = new ArrayList<>(results);
            final String[] currentQuery = new String[]{""};
            final int searchHeight = dp(36);
            final int clearSize = dp(36);
            final int pad = dp(12);

            LinearLayout containerLayout = new LinearLayout(parent);
            containerLayout.setOrientation(LinearLayout.VERTICAL);
            containerLayout.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

            FrameLayout searchFrame = new FrameLayout(parent);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, searchHeight + dp(12));
            layoutParams.leftMargin = dp(10);
            layoutParams.rightMargin = dp(10);
            layoutParams.topMargin = dp(6);
            layoutParams.bottomMargin = dp(2);
            searchFrame.setLayoutParams(layoutParams);
            searchFrame.setClipToPadding(true);
            searchFrame.setClipChildren(true);

            ImageView searchIcon = new ImageView(parent);
            searchIcon.setScaleType(ImageView.ScaleType.CENTER);
            searchIcon.setImageResource(R.drawable.ic_ab_search);
            searchIcon.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
            searchFrame.addView(searchIcon, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.CENTER_VERTICAL));

            EditTextBoldCursor searchField = new EditTextBoldCursor(parent);
            searchField.setHint(getString(R.string.Search));
            searchField.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
            searchField.setHintTextColor(getThemedColor(Theme.key_windowBackgroundWhiteHintText));
            searchField.setSingleLine(true);
            searchField.setBackground(null);
            searchField.setInputType(InputType.TYPE_CLASS_TEXT);
            searchField.setLineColors(getThemedColor(Theme.key_windowBackgroundWhiteInputField), getThemedColor(Theme.key_windowBackgroundWhiteInputFieldActivated), getThemedColor(Theme.key_text_RedRegular));
            searchField.setPadding(dp(61), pad / 2, dp(48), pad / 2);
            searchField.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER_VERTICAL));
            searchFrame.addView(searchField);

            ImageView clearButton = new ImageView(parent);
            clearButton.setScaleType(ImageView.ScaleType.CENTER);
            clearButton.setImageResource(R.drawable.ic_close_white);
            clearButton.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_actionBarWhiteSelector), Theme.RIPPLE_MASK_CIRCLE_20DP));
            clearButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
            clearButton.setLayoutParams(new FrameLayout.LayoutParams(clearSize, clearSize, Gravity.END | Gravity.CENTER_VERTICAL));
            searchFrame.addView(clearButton);
            containerLayout.addView(searchFrame);

            AlertDialog.Builder builder = new AlertDialog.Builder(parent, resourceProvider);
            builder.setView(containerLayout);
            builder.setNegativeButton(getString(R.string.Close), null);
            final AlertDialog dialog = builder.create();
            dialog.setOnShowListener(d -> {
                try {
                    searchField.requestFocus();
                    AndroidUtilities.showKeyboard(searchField);
                } catch (Exception ignore) {
                }
            });

            RecyclerListView listView = new RecyclerListView(parent);
            listView.setOverScrollMode(OVER_SCROLL_NEVER);
            listView.setLayoutManager(new LinearLayoutManager(parent, LinearLayoutManager.VERTICAL, false));

            var adapter = new RecyclerListView.SelectionAdapter() {
                @Override
                public boolean isEnabled(RecyclerView.ViewHolder holder) {
                    return true;
                }

                @NonNull
                @Override
                public RecyclerListView.Holder onCreateViewHolder(@NonNull ViewGroup parent1, int viewType) {
                    View view = new SettingsSearchCell(parent);
                    view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                    return new RecyclerListView.Holder(view);
                }

                @Override
                public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                    SettingsSearchCell cell = (SettingsSearchCell) holder.itemView;
                    SettingsSearchResult r = filtered.get(position);
                    String[] path = r.path2 != null ? new String[]{r.path1, r.path2} : new String[]{r.path1};
                    CharSequence titleToSet = r.searchTitle == null ? "" : r.searchTitle;
                    String q = currentQuery[0];
                    if (q != null && !q.isEmpty() && titleToSet.length() > 0) {
                        SpannableStringBuilder ss = new SpannableStringBuilder(titleToSet);
                        String lower = titleToSet.toString().toLowerCase();
                        String[] parts = q.split("\\s+");
                        int highlightColor = getThemedColor(Theme.key_windowBackgroundWhiteBlueText4);
                        for (String p : parts) {
                            if (p.isEmpty()) continue;
                            int idx = 0;
                            while (true) {
                                int found = lower.indexOf(p, idx);
                                if (found == -1) break;
                                try {
                                    ss.setSpan(new ForegroundColorSpan(highlightColor), found, found + p.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                } catch (Exception ignore) {
                                }
                                idx = found + p.length();
                            }
                        }
                        titleToSet = ss;
                    }
                    cell.setTextAndValueAndIcon(titleToSet, path, r.iconResId, position < filtered.size() - 1);
                }

                @Override
                public int getItemCount() {
                    return filtered.size();
                }
            };

            listView.setAdapter(adapter);
            listView.setOnItemClickListener((v, position) -> {
                if (position < 0 || position >= filtered.size()) return;
                SettingsSearchResult r = filtered.get(position);
                try {
                    if (r.openRunnable != null) r.openRunnable.run();
                } catch (Exception ignore) {
                }
                dialog.dismiss();
            });

            containerLayout.addView(listView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

            searchField.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @SuppressLint("NotifyDataSetChanged")
                @Override
                public void afterTextChanged(Editable s) {
                    String q = s.toString().toLowerCase().trim();
                    currentQuery[0] = q;
                    filtered.clear();
                    if (q.isEmpty()) {
                        filtered.addAll(results);
                    } else {
                        String[] parts = q.split("\\s+");
                        for (SettingsSearchResult item : results) {
                            String title = item.searchTitle == null ? "" : item.searchTitle.toLowerCase();
                            boolean ok = true;
                            for (String p : parts) {
                                if (!title.contains(p)) {
                                    ok = false;
                                    break;
                                }
                            }
                            if (ok) filtered.add(item);
                        }
                    }
                    adapter.notifyDataSetChanged();
                    searchIcon.setVisibility(q.length() > 20 ? View.GONE : View.VISIBLE);
                    clearButton.setVisibility(q.isEmpty() ? View.GONE : View.VISIBLE);
                }
            });

            clearButton.setOnClickListener(v -> {
                searchField.setText("");
                searchField.requestFocus();
                AndroidUtilities.showKeyboard(searchField);
            });
            clearButton.setVisibility(View.GONE);

            showDialog(dialog);
        } catch (Exception ignore) {
        }
    }

    private @NonNull FrameLayout getFrameLayout(Context context) {
        FrameLayout frameLayout = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                if (actionBarContainer != null) {
                    actionBarContainer.getLayoutParams().height = ActionBar.getCurrentActionBarHeight();
                    ((MarginLayoutParams) actionBarContainer.getLayoutParams()).topMargin = AndroidUtilities.statusBarHeight;
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        frameLayout.setFitsSystemWindows(true);
        return frameLayout;
    }

    private class Page extends FrameLayout {

        private static final int VIEW_TYPE_HEADER = 1;
        private static final int VIEW_TYPE_BOTTOM = 2;
        private static final int VIEW_TYPE_TEXT = 3;
        private static final int VIEW_TYPE_TEXT_LINK = 4;

        private final RecyclerListView listView;
        private final int type;

        private int rowCount;
        private int generalRow = -1;
        private int translatorRow = -1;
        private int chatRow = -1;
        private int passcodeRow = -1;
        private int experimentRow = -1;
        private int categories2Row = -1;

        private int nSettingsHeaderRow = -1;
        private int importSettingsRow = -1;
        private int exportSettingsRow = -1;
        private int resetSettingsRow = -1;
        private int otherRow = -1;
        private int appRestartRow = -1;

        private int xChannelRow = -1;
        private int channelRow = -1;
        private int channelTipsRow = -1;
        private int sourceCodeRow = -1;
        private int translationRow = -1;
        private int datacenterStatusRow = -1;
        private int actionBarHeight;

        @SuppressLint("ApplySharedPref")
        public Page(Context context, int type) {
            super(context);
            this.type = type;

            listView = new RecyclerListView(context);
            listView.setVerticalScrollBarEnabled(false);
            listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
            addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
            listView.setAdapter(new RecyclerListView.SelectionAdapter() {
                @Override
                public int getItemCount() {
                    return rowCount;
                }

                @NonNull
                @Override
                public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                    View view = null;
                    switch (viewType) {
                        case VIEW_TYPE_HEADER:
                            view = new HeaderCell(getContext());
                            view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                            break;
                        case VIEW_TYPE_BOTTOM:
                            view = new ShadowSectionCell(getContext());
                            break;
                        case VIEW_TYPE_TEXT:
                            view = new TextCell(getContext());
                            view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                            break;
                        case VIEW_TYPE_TEXT_LINK:
                            view = new TextSettingsCell(getContext());
                            view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                            break;
                    }
                    // noinspection ConstantConditions
                    view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                    return new RecyclerListView.Holder(view);
                }

                @Override
                public boolean isEnabled(RecyclerView.ViewHolder holder) {
                    int type = holder.getItemViewType();
                    return type == VIEW_TYPE_TEXT || type == VIEW_TYPE_TEXT_LINK;
                }

                @Override
                public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                    switch (holder.getItemViewType()) {
                        case VIEW_TYPE_HEADER: {
                            HeaderCell headerCell = (HeaderCell) holder.itemView;
                            if (position == nSettingsHeaderRow) {
                                headerCell.setText(getString(R.string.NekoSettings));
                            } else if (position == otherRow) {
                                headerCell.setText(getString(R.string.Other));
                            }
                            break;
                        }
                        case VIEW_TYPE_BOTTOM: {
                            if (position == categories2Row) {
                                holder.itemView.setBackground(Theme.getThemedDrawable(getContext(), R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                            }
                            break;
                        }
                        case VIEW_TYPE_TEXT: {
                            TextCell textCell = (TextCell) holder.itemView;
                            if (position == chatRow) {
                                textCell.setTextAndIcon(getString(R.string.Chat), R.drawable.msg_discussion, true);
                            } else if (position == generalRow) {
                                textCell.setTextAndIcon(getString(R.string.General), R.drawable.msg_theme, true);
                            } else if (position == translatorRow) {
                                textCell.setTextAndIcon(getString(R.string.TranslatorSettings), R.drawable.ic_translate, true);
                            } else if (position == passcodeRow) {
                                textCell.setTextAndIcon(getString(R.string.PasscodeNeko), R.drawable.msg_permissions, true);
                            } else if (position == experimentRow) {
                                textCell.setTextAndIcon(getString(R.string.Experimental), R.drawable.msg_fave, true);
                            } else if (position == importSettingsRow) {
                                textCell.setTextAndIcon(getString(R.string.ImportSettings), R.drawable.msg_photo_settings_solar, true);
                            } else if (position == exportSettingsRow) {
                                textCell.setTextAndIcon(getString(R.string.BackupSettings), R.drawable.msg_instant_link_solar, true);
                            } else if (position == resetSettingsRow) {
                                textCell.setTextAndIcon(getString(R.string.ResetSettings), R.drawable.msg_reset_solar, true);
                            } else if (position == appRestartRow) {
                                textCell.setTextAndIcon(getString(R.string.RestartApp), R.drawable.msg_retry_solar, true);
                            }
                            break;
                        }
                        case VIEW_TYPE_TEXT_LINK: {
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
                                textCell.setText(getString(R.string.DatacenterStatus), true);
                            }
                            break;
                        }
                    }
                }

                @Override
                public int getItemViewType(int position) {
                    if (position == categories2Row) {
                        return VIEW_TYPE_BOTTOM;
                    } else if (position == nSettingsHeaderRow || position == otherRow) {
                        return VIEW_TYPE_HEADER;
                    } else if (position == chatRow || position == generalRow || position == passcodeRow || position == experimentRow || position == translatorRow ||
                            position == importSettingsRow || position == exportSettingsRow || position == resetSettingsRow || position == appRestartRow) {
                        return VIEW_TYPE_TEXT;
                    }
                    return VIEW_TYPE_TEXT_LINK;
                }
            });
            listView.setOnItemClickListener((view, position, x, y) -> {
                if (position == chatRow) {
                    presentFragment(new NekoChatSettingsActivity());
                } else if (position == generalRow) {
                    presentFragment(new NekoGeneralSettingsActivity());
                } else if (position == passcodeRow) {
                    presentFragment(new NekoPasscodeSettingsActivity());
                } else if (position == experimentRow) {
                    presentFragment(new NekoExperimentalSettingsActivity());
                } else if (position == translatorRow) {
                    presentFragment(new NekoTranslatorSettingsActivity());
                } else if (position == xChannelRow) {
                    MessagesController.getInstance(currentAccount).openByUserName("NagramX", NekoSettingsActivity.this, 1);
                } else if (position == channelRow) {
                    MessagesController.getInstance(currentAccount).openByUserName("nagram_channel", NekoSettingsActivity.this, 1);
                } else if (position == channelTipsRow) {
                    MessagesController.getInstance(currentAccount).openByUserName("NagramTips", NekoSettingsActivity.this, 1);
                } else if (position == translationRow) {
                    Browser.openUrl(getParentActivity(), "https://crowdin.com/project/NagramX");
                } else if (position == sourceCodeRow) {
                    Browser.openUrl(getParentActivity(), "https://github.com/risin42/NagramX");
                } else if (position == datacenterStatusRow) {
                    presentFragment(new DatacenterActivity(0));
                } else if (position == importSettingsRow) {
                    if (Build.VERSION.SDK_INT >= 33) {
                        openFilePicker();
                    } else {
                        DocumentSelectActivity activity = getDocumentSelectActivity(getParentActivity());
                        if (activity != null) {
                            presentFragment(activity);
                        }
                    }
                } else if (position == resetSettingsRow) {
                    AlertUtil.showConfirm(getParentActivity(),
                            getString(R.string.ResetSettingsAlert),
                            R.drawable.msg_reset,
                            getString(R.string.Reset),
                            true,
                            () -> {
                                ApplicationLoader.applicationContext.getSharedPreferences("nekocloud", Activity.MODE_PRIVATE).edit().clear().commit();
                                ApplicationLoader.applicationContext.getSharedPreferences("nekox_config", Activity.MODE_PRIVATE).edit().clear().commit();
                                NekoConfig.getPreferences().edit().clear().commit();
                                AppRestartHelper.triggerRebirth(context, new Intent(context, LaunchActivity.class));
                            });
                } else if (position == exportSettingsRow) {
                    backupSettings();
                } else if (position == appRestartRow) {
                    AppRestartHelper.triggerRebirth(context, new Intent(context, LaunchActivity.class));
                }
            });

            updateRows();

            setWillNotDraw(false);
        }

        private void updateRows() {
            rowCount = 0;
            if (type == PAGE_TYPE) {
                generalRow = rowCount++;
                translatorRow = rowCount++;
                chatRow = rowCount++;
                if (!PasscodeHelper.isSettingsHidden()) {
                    passcodeRow = rowCount++;
                } else {
                    passcodeRow = -1;
                }
                experimentRow = rowCount++;
                categories2Row = rowCount++;
                nSettingsHeaderRow = rowCount++;
                importSettingsRow = rowCount++;
                exportSettingsRow = rowCount++;
                resetSettingsRow = rowCount++;
                otherRow = rowCount++;
                appRestartRow = rowCount++;
            } else {
                xChannelRow = rowCount++;
                channelRow = rowCount++;
                channelTipsRow = rowCount++;
                sourceCodeRow = rowCount++;
                translationRow = rowCount++;
                datacenterStatusRow = rowCount++;
            }
        }

        @Override
        protected void dispatchDraw(@NonNull Canvas canvas) {
            super.dispatchDraw(canvas);
            if (getParentLayout() != null) {
                getParentLayout().drawHeaderShadow(canvas, actionBarHeight);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            actionBarHeight = ActionBar.getCurrentActionBarHeight() + AndroidUtilities.statusBarHeight;
            ((MarginLayoutParams) listView.getLayoutParams()).topMargin = actionBarHeight;
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    private void backupSettings() {
        Context context = getParentActivity();
        if (context == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getString(R.string.BackupSettings));

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        CheckBoxCell checkBoxCell = new CheckBoxCell(context, CheckBoxCell.TYPE_CHECK_BOX_DEFAULT, resourceProvider);
        checkBoxCell.setBackground(Theme.getSelectorDrawable(false));
        checkBoxCell.setText(getString(R.string.ExportSettingsIncludeApiKeys), "", true, false);
        checkBoxCell.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(16) : AndroidUtilities.dp(8), 0, LocaleController.isRTL ? AndroidUtilities.dp(8) : AndroidUtilities.dp(16), 0);
        checkBoxCell.setChecked(true, false);
        checkBoxCell.setOnClickListener(v -> {
            CheckBoxCell cell = (CheckBoxCell) v;
            cell.setChecked(!cell.isChecked(), true);
        });
        linearLayout.addView(checkBoxCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

        builder.setView(linearLayout);
        builder.setPositiveButton(getString(R.string.ExportTheme), (dialog, which) -> {
            boolean includeApiKeys = checkBoxCell.isChecked();
            try {
                File cacheFile = new File(AndroidUtilities.getCacheDir(), new Date().toLocaleString() + ".nekox-settings.json");
                FileUtil.writeUtf8String(backupSettingsJson(false, 4, includeApiKeys), cacheFile);
                ShareUtil.shareFile(getParentActivity(), cacheFile);
            } catch (JSONException e) {
                AlertUtil.showSimpleAlert(getParentActivity(), e);
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    public static String backupSettingsJson(boolean isCloud, int indentSpaces) throws JSONException {
        return backupSettingsJson(isCloud, indentSpaces, true);
    }

    public static String backupSettingsJson(boolean isCloud, int indentSpaces, boolean includeApiKeys) throws JSONException {

        JSONObject configJson = new JSONObject();

        ArrayList<String> userconfig = new ArrayList<>();
        userconfig.add("saveIncomingPhotos");
        userconfig.add("passcodeHash");
        userconfig.add("passcodeType");
        userconfig.add("passcodeHash");
        userconfig.add("autoLockIn");
        userconfig.add("useFingerprint");
        spToJSON("userconfing", configJson, userconfig::contains, isCloud);

        ArrayList<String> mainconfig = new ArrayList<>();
        mainconfig.add("saveToGallery");
        mainconfig.add("autoplayGifs");
        mainconfig.add("autoplayVideo");
        mainconfig.add("mapPreviewType");
        mainconfig.add("raiseToSpeak");
        mainconfig.add("customTabs");
        mainconfig.add("directShare");
        mainconfig.add("shuffleMusic");
        mainconfig.add("playOrderReversed");
        mainconfig.add("inappCamera");
        mainconfig.add("repeatMode");
        mainconfig.add("fontSize");
        mainconfig.add("bubbleRadius");
        mainconfig.add("ivFontSize");
        mainconfig.add("allowBigEmoji");
        mainconfig.add("streamMedia");
        mainconfig.add("saveStreamMedia");
        mainconfig.add("smoothKeyboard");
        mainconfig.add("pauseMusicOnRecord");
        mainconfig.add("streamAllVideo");
        mainconfig.add("streamMkv");
        mainconfig.add("suggestStickers");
        mainconfig.add("sortContactsByName");
        mainconfig.add("sortFilesByName");
        mainconfig.add("noSoundHintShowed");
        mainconfig.add("directShareHash");
        mainconfig.add("useThreeLinesLayout");
        mainconfig.add("archiveHidden");
        mainconfig.add("distanceSystemType");
        mainconfig.add("loopStickers");
        mainconfig.add("keepMedia");
        mainconfig.add("noStatusBar");
        mainconfig.add("lastKeepMediaCheckTime");
        mainconfig.add("searchMessagesAsListHintShows");
        mainconfig.add("searchMessagesAsListUsed");
        mainconfig.add("stickersReorderingHintUsed");
        mainconfig.add("textSelectionHintShows");
        mainconfig.add("scheduledOrNoSoundHintShows");
        mainconfig.add("lockRecordAudioVideoHint");
        mainconfig.add("disableVoiceAudioEffects");
        mainconfig.add("chatSwipeAction");

        if (!isCloud) mainconfig.add("theme");
        mainconfig.add("selectedAutoNightType");
        mainconfig.add("autoNightScheduleByLocation");
        mainconfig.add("autoNightBrighnessThreshold");
        mainconfig.add("autoNightDayStartTime");
        mainconfig.add("autoNightDayEndTime");
        mainconfig.add("autoNightSunriseTime");
        mainconfig.add("autoNightCityName");
        mainconfig.add("autoNightSunsetTime");
        mainconfig.add("autoNightLocationLatitude3");
        mainconfig.add("autoNightLocationLongitude3");
        mainconfig.add("autoNightLastSunCheckDay");

        mainconfig.add("lang_code");

        mainconfig.add("web_restricted_domains2");

        spToJSON("mainconfig", configJson, mainconfig::contains);
        if (!isCloud) spToJSON("themeconfig", configJson, null);
        spToJSON("nkmrcfg", configJson, null, includeApiKeys);

        return configJson.toString(indentSpaces);
    }

    private static void spToJSON(String sp, JSONObject object, Function<String, Boolean> filter) throws JSONException {
        spToJSON(sp, object, filter, true);
    }

    private static void spToJSON(String sp, JSONObject object, Function<String, Boolean> filter, boolean includeApiKeys) throws JSONException {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences(sp, Activity.MODE_PRIVATE);
        JSONObject jsonConfig = new JSONObject();
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            String key = entry.getKey();
            if (!includeApiKeys && (key.endsWith("Key") || key.contains("Token") || key.contains("AccountID"))) {
                continue;
            }
            if (filter != null && !filter.apply(key)) {
                continue;
            }
            if (entry.getValue() instanceof Long) {
                key = key + "_long";
            } else if (entry.getValue() instanceof Float) {
                key = key + "_float";
            }
            jsonConfig.put(key, entry.getValue());
        }
        object.put(sp, jsonConfig);
    }

    private DocumentSelectActivity getDocumentSelectActivity(Activity parent) {
        try {
            if (parent.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                parent.requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, BasePermissionsActivity.REQUEST_CODE_EXTERNAL_STORAGE);
                return null;
            }
        } catch (Throwable ignore) {
        }
        DocumentSelectActivity fragment = new DocumentSelectActivity(false);
        fragment.setMaxSelectedFiles(1);
        fragment.setAllowPhoto(false);
        fragment.setDelegate(new DocumentSelectActivity.DocumentSelectActivityDelegate() {
            @Override
            public void didSelectFiles(DocumentSelectActivity activity, ArrayList<String> files, String caption, boolean notify, int scheduleDate) {
                activity.finishFragment();
                importSettings(parent, new File(files.get(0)));
            }

            @Override
            public void didSelectPhotos(ArrayList<SendMessagesHelper.SendingMediaInfo> photos, boolean notify, int scheduleDate) {
            }

            @Override
            public void startDocumentSelectActivity() {
            }
        });
        return fragment;
    }

    public static void importSettings(Context context, File settingsFile) {

        AlertUtil.showConfirm(context,
                getString(R.string.ImportSettingsAlert),
                R.drawable.msg_photo_settings_solar,
                getString(R.string.Import),
                true,
                () -> importSettingsConfirmed(context, settingsFile));

    }

    public static void importSettingsConfirmed(Context context, File settingsFile) {

        try {
            JsonObject configJson = GsonUtil.toJsonObject(FileUtil.readUtf8String(settingsFile));
            importSettings(configJson);

            AlertDialog restart = new AlertDialog(context, 0);
            restart.setTitle(getString(R.string.NagramX));
            restart.setMessage(getString(R.string.RestartAppToTakeEffect));
            restart.setPositiveButton(getString(R.string.OK), (__, ___) -> AppRestartHelper.triggerRebirth(context, new Intent(context, LaunchActivity.class)));
            restart.show();
        } catch (Exception e) {
            AlertUtil.showSimpleAlert(context, e);
        }

    }

    @SuppressLint("ApplySharedPref")
    public static void importSettings(JsonObject configJson) throws JSONException {
        Set<String> allowedKeys = new HashSet<>();
        try {
            allowedKeys.addAll(NekoConfig.getAllKeys());
            allowedKeys.addAll(NaConfig.INSTANCE.getAllKeys());
        } catch (Throwable ignore) {
        }
        String[] preservePrefixes = {
                AyuGhostPreferences.ghostReadExclusionPrefix,
                AyuGhostPreferences.ghostTypingExclusionPrefix,
                AyuSavePreferences.saveExclusionPrefix,
                LocalNameHelper.chatNameOverridePrefix,
                LocalNameHelper.userNameOverridePrefix,
                DialogConfig.customForumTabPrefix,
                LocalPeerColorHelper.KEY_PREFIX,
                LocalPremiumStatusHelper.KEY_PREFIX,
                BookmarksHelper.KEY_PREFIX
        };

        for (Map.Entry<String, JsonElement> element : configJson.entrySet()) {
            String spName = element.getKey();
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences(spName, Activity.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            for (Map.Entry<String, JsonElement> config : ((JsonObject) element.getValue()).entrySet()) {
                String key = config.getKey();
                JsonPrimitive value = (JsonPrimitive) config.getValue();
                if ("nkmrcfg".equals(spName)) {
                    boolean shouldSkip = true;
                    for (String prefix : preservePrefixes) {
                        if (key.startsWith(prefix)) {
                            shouldSkip = false;
                            break;
                        }
                    }
                    if (shouldSkip) {
                        String actualKey = key;
                        if (key.endsWith("_long")) {
                            actualKey = StringsKt.substringBeforeLast(key, "_long", key);
                        } else if (key.endsWith("_float")) {
                            actualKey = StringsKt.substringBeforeLast(key, "_float", key);
                        }
                        shouldSkip = !allowedKeys.contains(actualKey);
                    }
                    if (shouldSkip) {
                        continue;
                    }
                }
                if (value.isBoolean()) {
                    editor.putBoolean(key, value.getAsBoolean());
                } else if (value.isNumber()) {
                    boolean isLong = false;
                    boolean isFloat = false;
                    if (key.endsWith("_long")) {
                        key = StringsKt.substringBeforeLast(key, "_long", key);
                        isLong = true;
                    } else if (key.endsWith("_float")) {
                        key = StringsKt.substringBeforeLast(key, "_float", key);
                        isFloat = true;
                    }
                    if (isLong) {
                        editor.putLong(key, value.getAsLong());
                    } else if (isFloat) {
                        editor.putFloat(key, value.getAsFloat());
                    } else {
                        editor.putInt(key, value.getAsInt());
                    }
                } else {
                    editor.putString(key, value.getAsString());
                }
            }
            editor.commit();
        }
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/json");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(intent, 21);
        } catch (android.content.ActivityNotFoundException ex) {
            AlertUtil.showSimpleAlert(getParentActivity(), ex);
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode == 21 && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                File cacheDir = AndroidUtilities.getCacheDir();
                String tempFile = UUID.randomUUID().toString().replace("-", "") + ".nekox-settings.json";
                File file = new File(cacheDir.getPath(), tempFile);
                try {
                    final InputStream inputStream = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri);
                    if (inputStream != null) {
                        OutputStream outputStream = new FileOutputStream(file);
                        final byte[] buffer = new byte[4 * 1024];
                        int read;
                        while ((read = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, read);
                        }
                        inputStream.close();
                        outputStream.flush();
                        outputStream.close();
                        importSettings(getParentActivity(), file);
                    }
                } catch (Exception ignore) {
                }
            }
            super.onActivityResultFragment(requestCode, resultCode, data);
        }
    }

    @Override
    public boolean isActionBarCrossfadeEnabled() {
        return false;
    }
}
