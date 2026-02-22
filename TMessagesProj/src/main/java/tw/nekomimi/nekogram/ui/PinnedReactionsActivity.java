package tw.nekomimi.nekogram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.Components.Reactions.ReactionsUtils.addReactionToEditText;
import static org.telegram.ui.Components.Reactions.ReactionsUtils.createAnimatedEmojiSpan;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Build;
import android.text.Editable;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.FilledTabsView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Reactions.BackSpaceButtonView;
import org.telegram.ui.Components.Reactions.CustomReactionEditText;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;
import org.telegram.ui.Components.ReactionsContainerLayout;
import org.telegram.ui.Components.ViewPagerFixed;
import org.telegram.ui.SelectAnimatedEmojiDialog;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

import tw.nekomimi.nekogram.config.ConfigItem;
import tw.nekomimi.nekogram.helpers.PinnedElementsHelper;
import tw.nekomimi.nekogram.ui.cells.ReactionsContainerPreviewCell;
import xyz.nextalone.nagram.NaConfig;

public class PinnedReactionsActivity extends BaseFragment {

    public static final int PAGE_CHATS = 0;
    public static final int PAGE_CHANNELS = 1;
    public static final int PINNED_REACTIONS_LIMIT = 7;
    private final List<TLRPC.TL_availableReaction> allAvailableReactions = new ArrayList<>();
    private Page chatsPage;
    private Page channelsPage;
    private ViewPagerFixed viewPager;
    private ImageView backButton;
    private FrameLayout actionBarContainer;
    private FilledTabsView tabsView;

    public static String getRowDescription() {
        int count = PinnedElementsHelper.getFavoriteReactionsCount();
        return count > 0 ? String.valueOf(count) : getString(R.string.ReactionsOff);
    }

    public Page getCurrentPage() {
        if (viewPager == null) return chatsPage;
        return viewPager.getCurrentPosition() == 0 ? chatsPage : channelsPage;
    }

    @Override
    public View createView(Context context) {

        chatsPage = new Page(context, PAGE_CHATS, this);
        channelsPage = new Page(context, PAGE_CHANNELS, this);

        actionBar.setCastShadows(false);
        actionBar.setVisibility(View.GONE);
        actionBar.setAllowOverlayTitle(false);

        FrameLayout frameLayout = getFrameLayout(context);

        Utilities.Callback2<Boolean, Integer> onScrollEndCallback = (Boolean isShowing, Integer page) -> {
            ReactionsContainerLayout preview = null;
            ArrayList<ReactionsLayoutInBubble.VisibleReaction> visibleReactions = new ArrayList<>();
            if (page == PAGE_CHATS && chatsPage.reactionsPreview.getVisibility() == View.VISIBLE) {
                preview = chatsPage.reactionsPreview.tagSelector;
                visibleReactions = chatsPage.grabAsVisibleReactions();
            } else if (page == PAGE_CHANNELS && channelsPage.reactionsPreview.getVisibility() == View.VISIBLE) {
                preview = channelsPage.reactionsPreview.tagSelector;
                visibleReactions = channelsPage.grabAsVisibleReactions();
            }

            if (preview != null) {
                if (!isShowing) {
                    preview.startCloseAnimation();
                    return;
                }

                preview.animate().setListener(null).start();
                preview.startEnterAnimation(true);
                preview.setVisibleReactionsList(visibleReactions, true);
            }
        };

        viewPager = new ViewPagerFixed(context) {
            @Override
            public void onTabAnimationUpdate(boolean manual) {
                tabsView.setSelected(viewPager.getPositionAnimated());
            }

            @Override
            protected void onScrollEnd() {
                super.onScrollEnd();
                onScrollEndCallback.run(true, viewPager.currentPosition);
            }
        };
        viewPager.setAdapter(new ViewPagerFixed.Adapter() {
            @Override
            public int getItemCount() {
                return 2;
            }

            @Override
            public View createView(int viewType) {
                if (viewType == PAGE_CHATS) return chatsPage;
                if (viewType == PAGE_CHANNELS) return channelsPage;
                return null;
            }

            @Override
            public int getItemViewType(int position) {
                return position;
            }

            @Override
            public void bindView(View view, int position, int viewType) {

            }

            @Override
            public boolean canScrollTo(int position) {
                AndroidUtilities.runOnUIThread(() -> {
                    channelsPage.closeKeyboard();
                    chatsPage.closeKeyboard();
                    onScrollEndCallback.run(false, position == PAGE_CHANNELS ? PAGE_CHATS : PAGE_CHANNELS);
                }, 100);
                return super.canScrollTo(position);
            }
        });
        frameLayout.addView(viewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        actionBarContainer = new FrameLayout(context);
        actionBarContainer.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefault));
        frameLayout.addView(actionBarContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL));

        tabsView = new FilledTabsView(context);
        tabsView.setTabs(getString(R.string.FilterChats), getString(R.string.FilterChannels));
        tabsView.onTabSelected(tab -> {
            if (viewPager != null) {
                viewPager.scrollToPosition(tab);
            }
            AndroidUtilities.runOnUIThread(() -> {
                channelsPage.closeKeyboard();
                chatsPage.closeKeyboard();
                onScrollEndCallback.run(false, tab == PAGE_CHANNELS ? PAGE_CHATS : PAGE_CHANNELS);
            }, 100);
        });
        tabsView.setBackgroundColor(AndroidUtilities.computePerceivedBrightness(Theme.getColor(Theme.key_actionBarDefault, getResourceProvider())) > .721f ? Theme.getColor(Theme.key_actionBarDefaultIcon, getResourceProvider()) : Theme.adaptHSV(Theme.getColor(Theme.key_actionBarDefault, getResourceProvider()), +.08f, -.08f));
        actionBarContainer.addView(tabsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 40, Gravity.CENTER));

        backButton = new ImageView(context);
        backButton.setScaleType(ImageView.ScaleType.CENTER);
        backButton.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_actionBarWhiteSelector), Theme.RIPPLE_MASK_CIRCLE_20DP));
        backButton.setImageResource(R.drawable.ic_ab_back);
        backButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_actionBarDefaultIcon), PorterDuff.Mode.SRC_IN));
        backButton.setOnClickListener(v -> {
            if (onBackPressed(true)) {
                finishFragment();
            }
        });
        actionBarContainer.addView(backButton, LayoutHelper.createFrame(54, 54, Gravity.LEFT | Gravity.CENTER_VERTICAL));

        FrameLayout contentView;
        fragmentView = contentView = frameLayout;

        return contentView;
    }

    @NonNull
    private FrameLayout getFrameLayout(Context context) {
        FrameLayout frameLayout = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                if (actionBarContainer != null) {
                    actionBarContainer.getLayoutParams().height = ActionBar.getCurrentActionBarHeight() + AndroidUtilities.statusBarHeight;
                    ((MarginLayoutParams) backButton.getLayoutParams()).topMargin = AndroidUtilities.statusBarHeight / 2;
                    ((MarginLayoutParams) tabsView.getLayoutParams()).topMargin = AndroidUtilities.statusBarHeight / 2;
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        frameLayout.setFitsSystemWindows(true);
        return frameLayout;
    }

    public boolean hasUnsavedChanged() {
        return chatsPage.hasChanges() || channelsPage.hasChanges();
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (chatsPage.emojiKeyboardVisible || channelsPage.emojiKeyboardVisible) {
            chatsPage.closeKeyboard();
            channelsPage.closeKeyboard();
            return false;
        }
        if (hasUnsavedChanged()) {
            showUnsavedAlert();
            return false;
        }
        return super.onBackPressed(invoked);
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        if (hasUnsavedChanged()) {
            return false;
        }
        if (viewPager.getCurrentPosition() > PAGE_CHATS) {
            return false;
        }
        return super.isSwipeBackEnabled(event);
    }

    private void showUnsavedAlert() {
        if (getVisibleDialog() != null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), getResourceProvider());
        builder.setTitle(getString(R.string.UnsavedChanges));
        String text = getString(R.string.ReactionApplyChangesDialog);
        builder.setMessage(text);
        builder.setPositiveButton(getString(R.string.ApplyTheme), (dialogInterface, i) -> buttonClick());
        builder.setNegativeButton(getString(R.string.Discard), (dialogInterface, i) -> finishFragment());

        AlertDialog dialog = builder.show();
        TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(getThemedColor(Theme.key_text_RedBold));
        }
    }

    private void buttonClick() {
        chatsPage.save();
        channelsPage.save();
        finishFragment();
    }

    @Override
    public boolean onFragmentCreate() {
        allAvailableReactions.addAll(getMediaDataController().getEnabledReactionsList());
        Bulletin.addDelegate(this, new Bulletin.Delegate() {
            @Override
            public int getBottomOffset(int tag) {
                if ((viewPager.getCurrentPosition() == PAGE_CHATS && chatsPage.emojiKeyboardVisible) || (viewPager.getCurrentPosition() == PAGE_CHANNELS && channelsPage.emojiKeyboardVisible)) {
                    return 0;
                }
                return dp(62);
            }

            @Override
            public boolean clipWithGradient(int tag) {
                return true;
            }
        });
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        allAvailableReactions.clear();
        if (chatsPage != null) {
            chatsPage.cleanup();
        }
        if (channelsPage != null) {
            channelsPage.cleanup();
        }
    }

    @Override
    public void onFragmentClosed() {
        super.onFragmentClosed();
        Bulletin.removeDelegate(this);
    }

    public void updateLightStatusBar() {
        if (getParentActivity() == null) return;
        AndroidUtilities.setLightStatusBar(getParentActivity().getWindow(), isLightStatusBar());
    }

    @Override
    public boolean isActionBarCrossfadeEnabled() {
        return false;
    }

    /** @noinspection UnnecessaryUnicodeEscape */
    public class Page extends FrameLayout {

        private final FrameLayout bottomDialogLayout;
        private final TextCheckCell enableReactionsCell;
        private final LinearLayout switchLayout;
        private final CustomReactionEditText editText;
        private final ButtonWithCounterView actionButton;
        final AdjustPanLayoutHelper adjustPanLayoutHelper = new AdjustPanLayoutHelper(this) {

            @Override
            protected void onTransitionStart(boolean keyboardVisible, int contentHeight) {
                actionButton.setVisibility(View.VISIBLE);
                actionButton.animate().alpha(!keyboardVisible ? 1.0f : 0.0f).withEndAction(() -> {
                    if (keyboardVisible) {
                        actionButton.setVisibility(View.INVISIBLE);
                    }
                }).start();
            }

            @Override
            protected boolean applyTranslation() {
                return false;
            }

            @Override
            protected boolean heightAnimationEnabled() {
                INavigationLayout actionBarLayout = getParentLayout();
                return !inPreviewMode && !AndroidUtilities.isTablet() && !inBubbleMode && !AndroidUtilities.isInMultiwindow && actionBarLayout != null;
            }
        };
        private final ScrollView scrollView;
        private final LinkedHashMap<Long, AnimatedEmojiSpan> selectedEmojisMap = new LinkedHashMap<>();
        private final ArrayList<Long> selectedCustomEmojisList = new ArrayList<>();
        private SelectAnimatedEmojiDialog selectAnimatedEmojiDialog;
        private final int type;
        private final BaseFragment fragment;
        private final ReactionsContainerPreviewCell reactionsPreview;
        private boolean emojiKeyboardVisible = false;
        private boolean checked = false;
        private int actionBarHeight;

        public Page(@NonNull Context context, int type, BaseFragment fragment) {
            super(context);

            this.type = type;
            this.fragment = fragment;
            checked = getCurrentState().Bool();

            scrollView = new ScrollView(context);
            scrollView.setFillViewport(true);

            LinearLayout contentLayout = new LinearLayout(context);
            contentLayout.setOrientation(LinearLayout.VERTICAL);

            scrollView.addView(contentLayout);

            enableReactionsCell = new TextCheckCell(context);
            enableReactionsCell.setHeight(56);
            enableReactionsCell.setBackgroundColor(Theme.getColor(enableReactionsCell.isChecked() ? Theme.key_windowBackgroundChecked : Theme.key_windowBackgroundUnchecked));
            enableReactionsCell.setTypeface(AndroidUtilities.bold());
            enableReactionsCell.setTextAndCheck(getString(type == PAGE_CHATS ? R.string.PinnedReactions_Status_Chats : R.string.PinnedReactions_Status_Channels), false, false);
            enableReactionsCell.setColors(Theme.key_windowBackgroundCheckText, Theme.key_switchTrackBlue, Theme.key_switchTrackBlueChecked, Theme.key_switchTrackBlueThumb, Theme.key_switchTrackBlueThumbChecked);
            enableReactionsCell.setOnClickListener(v -> setCheckedEnableReactionCell(!enableReactionsCell.isChecked(), true, false));
            contentLayout.addView(enableReactionsCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            TextInfoPrivacyCell infoCell = new TextInfoPrivacyCell(context);
            infoCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
            infoCell.setTopPadding(12);
            infoCell.setBottomPadding(16);
            infoCell.setText(getString(type == PAGE_CHATS ? R.string.PinnedReactions_Description : R.string.PinnedReactions_Description_Channels));
            contentLayout.addView(infoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            reactionsPreview = new ReactionsContainerPreviewCell(context, getParentLayout(), null);
            contentLayout.addView(reactionsPreview, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, dp(45)));

            ShadowSectionCell shadowSectionCell = new ShadowSectionCell(context);
            shadowSectionCell.setBackground(Theme.getThemedDrawable(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
            contentLayout.addView(shadowSectionCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            HeaderCell headerCell = new HeaderCell(context);
            headerCell.setText(getString(R.string.PinnedReactions));
            headerCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            headerCell.setTextSize(15);
            headerCell.setTopMargin(14);

            switchLayout = new LinearLayout(context);
            switchLayout.setOrientation(LinearLayout.VERTICAL);

            contentLayout.addView(switchLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            switchLayout.addView(headerCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            editText = new CustomReactionEditText(context, getResourceProvider(), 150, true) {
                @Override
                protected void onLineCountChanged(int oldLineCount, int newLineCount) {
                    if (newLineCount > oldLineCount) {
                        scrollView.smoothScrollBy(0, dp(30));
                    }
                }

                @Override
                public boolean onTextContextMenuItem(int id) {
                    if (id == R.id.menu_delete || id == android.R.id.cut) {
                        return deleteSelectedEmojis();
                    } else if (id == android.R.id.paste || id == android.R.id.copy) {
                        return false;
                    }
                    return super.onTextContextMenuItem(id);
                }
            };
            editText.setOnFocused(this::showKeyboard);

            switchLayout.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            actionButton = new ButtonWithCounterView(context, getResourceProvider());
            actionButton.setText(new SpannableStringBuilder(getString(R.string.PinnedReactions_Apply)), false);
            actionButton.setOnClickListener(v -> buttonClick());
            addView(scrollView);
            addView(actionButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM, 13, 13, 13, 13));
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

            bottomDialogLayout = new FrameLayout(context) {
                @Override
                protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                    super.onLayout(changed, left, top, right, bottom);
                    if (emojiKeyboardVisible && changed) {
                        actionButton.setTranslationY(-bottomDialogLayout.getMeasuredHeight());
                        updateScrollViewMarginBottom(bottomDialogLayout.getMeasuredHeight());
                    }
                }
            };
            bottomDialogLayout.setVisibility(View.INVISIBLE);
            addView(bottomDialogLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

            SpannableStringBuilder editable = new SpannableStringBuilder();
            try {
                String currentList = getList().String();
                JSONArray object = new JSONArray(new JSONTokener(currentList));

                int successHandled = 0;
                for (int i = 0; i < object.length(); i++) {
                    try {
                        JSONObject jsonObject = object.getJSONObject(i);
                        if (jsonObject.has("emoticon")) {
                            String emoji = jsonObject.getString("emoticon").replace("\uFE0F", "");
                            TLRPC.TL_availableReaction availableReaction = getMediaDataController().getReactionsMap().get(emoji);
                            if (availableReaction == null) {
                                continue;
                            }
                            addReactionToEditText(availableReaction, selectedEmojisMap, selectedCustomEmojisList, editable, selectAnimatedEmojiDialog, editText.getFontMetricsInt());
                            successHandled++;
                        } else if (jsonObject.has("document_id")) {
                            TLRPC.TL_reactionCustomEmoji emoji = new TLRPC.TL_reactionCustomEmoji();
                            emoji.document_id = jsonObject.getLong("document_id");
                            addReactionToEditText(emoji, selectedEmojisMap, selectedCustomEmojisList, editable, selectAnimatedEmojiDialog, editText.getFontMetricsInt());
                            successHandled++;
                        }
                    } catch (JSONException ignored) {
                    }

                    if (successHandled >= PINNED_REACTIONS_LIMIT) {
                        break;
                    }
                }
            } catch (JSONException ignored) {
            }

            editText.append(editable);
            editText.setSelection(editText.getText().length());
            setCheckedEnableReactionCell(checked, false, true);
            editText.addReactionsSpan();

            reactionsPreview.tagSelector.setVisibleReactionsList(grabAsVisibleReactions(), true);

            setWillNotDraw(false);
            initEmojiView();

            if (type == PAGE_CHATS) {
                reactionsPreview.tagSelector.setAlpha(0f);
                AndroidUtilities.runOnUIThread(() -> reactionsPreview.tagSelector.startEnterAnimation(true), 400);
            } else {
                reactionsPreview.tagSelector.setAlpha(0f);
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            adjustPanLayoutHelper.onAttach();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            adjustPanLayoutHelper.onDetach();
        }

        public void save() {
            JSONArray reactionsList = grabReactions();
            getCurrentState().setConfigBool(checked && reactionsList.length() > 0);
            getList().setConfigString(reactionsList.toString());
        }

        private ConfigItem getCurrentState() {
            return type == PAGE_CHATS ? NaConfig.INSTANCE.getUsePinnedReactionsChats() : NaConfig.INSTANCE.getUsePinnedReactionsChannels();
        }

        private ConfigItem getList() {
            return type == PAGE_CHATS ? NaConfig.INSTANCE.getPinnedReactionsChats() : NaConfig.INSTANCE.getPinnedReactionsChannels();
        }

        private void initEmojiView() {
            if (selectAnimatedEmojiDialog != null) {
                return;
            }
            int accentColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, getResourceProvider());
            selectAnimatedEmojiDialog = new SelectAnimatedEmojiDialog(fragment, getContext(), false, null, SelectAnimatedEmojiDialog.TYPE_CHAT_REACTIONS, false, getResourceProvider(), 16, accentColor) {
                private boolean firstLayout = true;

                {
                    setDrawBackground(false);
                }

                @Override
                protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                    super.onLayout(changed, left, top, right, bottom);
                    if (firstLayout) {
                        firstLayout = false;
                        selectAnimatedEmojiDialog.onShow(null);
                    }
                }

                protected void onEmojiSelected(View view, Long documentId, TLRPC.Document document, TL_stars.TL_starGiftUnique gift, Integer until) {
                    if (gift != null) {
                        BulletinFactory.of(fragment).createSimpleBulletin(gift.sticker, getString(R.string.PinnedReactions_Type)).show();
                        return;
                    }

                    if (!selectedCustomEmojisList.contains(documentId)) {
                        if (type == PAGE_CHANNELS) {
                            boolean isReactionEmoji = false;
                            for (TLRPC.TL_availableReaction availableReaction : allAvailableReactions) {
                                if (documentId == availableReaction.activate_animation.id) {
                                    isReactionEmoji = true;
                                    break;
                                }
                            }

                            if (!isReactionEmoji) {
                                BulletinFactory.of(fragment).createSimpleBulletin(document, getString(R.string.PinnedReactions_Type)).show();
                                return;
                            }
                        }

                        if (!checkMaxNumberReached()) {
                            return;
                        }

                        try {
                            int selectionEnd = editText.getEditTextSelectionEnd();
                            SpannableString spannable = new SpannableString("b");
                            AnimatedEmojiSpan span = createAnimatedEmojiSpan(document, documentId, editText.getFontMetricsInt());
                            span.cacheType = AnimatedEmojiDrawable.getCacheTypeForEnterView();
                            span.setAdded();
                            Spannable text = editText.getText();
                            AnimatedEmojiSpan[] allSpans = text.getSpans(0, text.length(), AnimatedEmojiSpan.class);
                            Arrays.sort(allSpans, Comparator.comparingInt(text::getSpanStart));
                            int insertionIndex = 0;
                            for (AnimatedEmojiSpan s : allSpans) {
                                if (text.getSpanStart(s) < selectionEnd) {
                                    insertionIndex++;
                                }
                            }
                            selectedCustomEmojisList.add(insertionIndex, documentId);
                            selectedEmojisMap.put(documentId, span);
                            spannable.setSpan(span, 0, spannable.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            editText.getText().insert(selectionEnd, spannable);
                            editText.setSelection(selectionEnd + spannable.length());
                            selectAnimatedEmojiDialog.setMultiSelected(documentId, true);
                        } catch (Exception ignored) {
                        }
                    } else {
                        selectedCustomEmojisList.remove(documentId);
                        AnimatedEmojiSpan removedSpan = selectedEmojisMap.remove(documentId);
                        if (removedSpan != null) {
                            removedSpan.setRemoved(() -> {
                                SpannableStringBuilder spanned = new SpannableStringBuilder(editText.getText());
                                AnimatedEmojiSpan[] spans = spanned.getSpans(0, spanned.length(), AnimatedEmojiSpan.class);
                                for (AnimatedEmojiSpan span : spans) {
                                    if (span == removedSpan) {
                                        int selectionEnd = editText.getEditTextSelectionEnd();
                                        int spanEnd = spanned.getSpanEnd(span);
                                        int spanStart = spanned.getSpanStart(span);
                                        editText.getText().delete(spanStart, spanEnd);
                                        int spanDiff = spanEnd - spanStart;
                                        editText.setSelection(spanEnd <= selectionEnd ? selectionEnd - spanDiff : selectionEnd);
                                        break;
                                    }
                                }
                            });
                        }
                        animateChangesInNextRows(removedSpan);
                        selectAnimatedEmojiDialog.setMultiSelected(documentId, true);
                    }
                    reactionsPreview.tagSelector.setVisibleReactionsList(grabAsVisibleReactions(), true);
                }
            };
            selectAnimatedEmojiDialog.setAnimationsEnabled(false);
            selectAnimatedEmojiDialog.setClipChildren(false);
            selectAnimatedEmojiDialog.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            bottomDialogLayout.addView(selectAnimatedEmojiDialog, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

            BackSpaceButtonView backSpaceButtonView = getBackSpaceButtonView();
            bottomDialogLayout.addView(backSpaceButtonView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, 8, 8));
            for (Long selectedEmojisId : selectedCustomEmojisList) {
                selectAnimatedEmojiDialog.setMultiSelected(selectedEmojisId, false);
            }
        }

        @NonNull
        private BackSpaceButtonView getBackSpaceButtonView() {
            BackSpaceButtonView backSpaceButtonView = new BackSpaceButtonView(getContext(), getResourceProvider());
            backSpaceButtonView.setOnBackspace(isFast -> {
                if (deleteSelectedEmojis()) {
                    return;
                }
                int selectionEnd = editText.getEditTextSelectionEnd();
                SpannableStringBuilder spanned = new SpannableStringBuilder(editText.getText());
                AnimatedEmojiSpan[] spans = spanned.getSpans(0, spanned.length(), AnimatedEmojiSpan.class);
                for (AnimatedEmojiSpan span : spans) {
                    int removedSpanEnd = spanned.getSpanEnd(span);
                    if (removedSpanEnd == selectionEnd) {
                        selectedEmojisMap.remove(span.documentId);
                        selectedCustomEmojisList.remove(span.documentId);
                        selectAnimatedEmojiDialog.unselect(span.documentId);
                        if (isFast) {
                            editText.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
                        } else {
                            span.setRemoved(() -> {
                                Editable editable = editText.getText();
                                int spanStart = editable.getSpanStart(span);
                                int spanEnd = editable.getSpanEnd(span);
                                int spanDiff = spanEnd - spanStart;
                                if (spanStart == -1 || spanEnd == -1) {
                                    return;
                                }
                                editText.getText().delete(spanStart, spanEnd);
                                editText.setSelection(Math.min(selectionEnd - spanDiff, editText.getText().length()));
                            });
                            animateChangesInNextRows(span);
                        }
                        break;
                    }
                }
                reactionsPreview.tagSelector.setVisibleReactionsList(grabAsVisibleReactions(), true);
            });
            return backSpaceButtonView;
        }

        private boolean deleteSelectedEmojis() {
            int selectionEnd = editText.getEditTextSelectionEnd();
            int selectionStart = editText.getEditTextSelectionStart();
            SpannableStringBuilder spanned = new SpannableStringBuilder(editText.getText());
            if (editText.hasSelection()) {
                AnimatedEmojiSpan[] spans = spanned.getSpans(selectionStart, selectionEnd, AnimatedEmojiSpan.class);
                for (AnimatedEmojiSpan span : spans) {
                    selectedEmojisMap.remove(span.documentId);
                    selectedCustomEmojisList.remove(span.documentId);
                    selectAnimatedEmojiDialog.unselect(span.documentId);
                }
                editText.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
                reactionsPreview.tagSelector.setVisibleReactionsList(grabAsVisibleReactions(), true);
                return true;
            }
            return false;
        }

        private void animateChangesInNextRows(AnimatedEmojiSpan actionSpan) {
            Editable editable = editText.getText();
            Layout layout = editText.getLayout();
            int deleteLine = layout.getLineForOffset(editable.getSpanStart(actionSpan));
            int nextLine = deleteLine + 1;
            if (nextLine < layout.getLineCount()) {
                int newLineStart = layout.getLineStart(nextLine);
                AnimatedEmojiSpan[] spans = editable.getSpans(newLineStart, editable.length(), AnimatedEmojiSpan.class);
                for (AnimatedEmojiSpan span : spans) {
                    span.setAnimateChanges();
                }
            }
        }

        private void setCheckedEnableReactionCell(boolean checked, boolean animated, boolean forced) {
            if (this.checked == checked && !forced) {
                return;
            }

            this.checked = checked;

            enableReactionsCell.setChecked(checked);
            int clr = Theme.getColor(checked ? Theme.key_windowBackgroundChecked : Theme.key_windowBackgroundUnchecked);
            if (animated) {
                if (checked) {
                    enableReactionsCell.setBackgroundColorAnimated(true, clr);
                } else {
                    enableReactionsCell.setBackgroundColorAnimatedReverse(clr);
                }
            } else {
                enableReactionsCell.setBackgroundColor(clr);
            }

            editText.setFocusableInTouchMode(checked);

            if (checked) {
                reactionsPreview.tagSelector.startEnterAnimation(true);

                switchLayout.setVisibility(View.VISIBLE);
                actionButton.setVisibility(View.VISIBLE);
                reactionsPreview.setVisibility(View.VISIBLE);
                if (animated) {
                    actionButton.animate().setListener(null).cancel();
                    switchLayout.animate().setListener(null).cancel();
                    reactionsPreview.animate().setListener(null).cancel();
                    switchLayout.animate().alpha(1f).setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT).setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            editText.setFocusableInTouchMode(true);
                        }
                    }).start();
                    actionButton.animate().alpha(1f).setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                    reactionsPreview.animate().alpha(1f).setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                }
            } else {
                if (animated) {
                    closeKeyboard();
                    actionButton.animate().setListener(null).cancel();
                    switchLayout.animate().setListener(null).cancel();
                    reactionsPreview.animate().setListener(null).cancel();
                    actionButton.animate().alpha(0f).setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT).setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            actionButton.setVisibility(View.INVISIBLE);
                        }
                    }).start();
                    switchLayout.animate().alpha(0f).setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT).setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            editText.setFocusableInTouchMode(false);
                            switchLayout.setVisibility(View.INVISIBLE);
                        }
                    }).start();
                    reactionsPreview.animate().alpha(0f).setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT).setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            reactionsPreview.setVisibility(View.INVISIBLE);
                        }
                    }).start();
                } else {
                    switchLayout.setVisibility(View.INVISIBLE);
                    actionButton.setVisibility(View.INVISIBLE);
                    reactionsPreview.setVisibility(View.INVISIBLE);
                }
            }
        }

        private boolean checkMaxNumberReached() {
            if (grabReactions().length() >= PINNED_REACTIONS_LIMIT) {
                BulletinFactory.of(fragment).createSimpleBulletin(R.raw.chats_infotip, formatString(R.string.PinnedReactions_Limit, PINNED_REACTIONS_LIMIT)).show();
                return false;
            }

            return true;
        }

        public boolean hasChanges() {
            if (checked != getCurrentState().Bool()) {
                getCurrentState().setConfigBool(checked && !selectedCustomEmojisList.isEmpty());
            }
            return checkChangesInList();
        }

        private boolean checkChangesInList() {
            ArrayList<String> savedEmoticons = new ArrayList<>();
            ArrayList<Long> savedDocumentIds = new ArrayList<>();
            try {
                String currentList = getList().String();
                JSONArray object = new JSONArray(new JSONTokener(currentList));

                for (int i = 0; i < object.length(); i++) {
                    try {
                        JSONObject jsonObject = object.getJSONObject(i);
                        if (jsonObject.has("emoticon")) {
                            savedEmoticons.add(jsonObject.getString("emoticon").replace("\uFE0F", ""));
                        } else if (jsonObject.has("document_id")) {
                            savedDocumentIds.add(jsonObject.getLong("document_id"));
                        }
                    } catch (JSONException ignored) {
                    }

                    if (i >= PINNED_REACTIONS_LIMIT) {
                        break;
                    }
                }
            } catch (JSONException ignored) {
            }

            JSONArray currentReactions = grabReactions();
            ArrayList<String> currentEmoticons = new ArrayList<>();
            ArrayList<Long> currentDocumentIds = new ArrayList<>();
            for (int i = 0; i < currentReactions.length(); i++) {
                try {
                    JSONObject object = currentReactions.getJSONObject(i);
                    if (object.has("document_id")) {
                        currentDocumentIds.add(object.getLong("document_id"));
                    } else if (object.has("emoticon")) {
                        currentEmoticons.add(object.getString("emoticon").replace("\uFE0F", ""));
                    }
                } catch (JSONException ignored) {
                }
            }

            if (currentEmoticons.size() != savedEmoticons.size() || currentDocumentIds.size() != savedDocumentIds.size()) {
                return true;
            }

            for (String emoticon : currentEmoticons) {
                if (!savedEmoticons.contains(emoticon)) {
                    return true;
                }
            }

            for (String emoticon : savedEmoticons) {
                if (!currentEmoticons.contains(emoticon)) {
                    return true;
                }
            }

            for (Long documentId : savedDocumentIds) {
                if (!currentDocumentIds.contains(documentId)) {
                    return true;
                }
            }

            for (Long documentId : currentDocumentIds) {
                if (!savedDocumentIds.contains(documentId)) {
                    return true;
                }
            }

            return false;
        }

        private JSONArray grabReactions() {
            JSONArray jsonArray = new JSONArray();
            for (Long documentId : selectedCustomEmojisList) {
                if (documentId == -1) continue;

                JSONObject object = new JSONObject();

                boolean isReactionEmoji = false;
                for (TLRPC.TL_availableReaction availableReaction : allAvailableReactions) {
                    if (documentId == availableReaction.activate_animation.id) {
                        try {
                            object.put("emoticon", availableReaction.reaction.replace("\uFE0F", ""));
                        } catch (JSONException ignored) {
                        }
                        isReactionEmoji = true;
                        break;
                    }
                }

                if (!isReactionEmoji) {
                    try {
                        object.put("document_id", documentId);
                    } catch (JSONException ignored) {
                    }
                }

                jsonArray.put(object);
            }

            return jsonArray;
        }

        private ArrayList<ReactionsLayoutInBubble.VisibleReaction> grabAsVisibleReactions() {
            ArrayList<ReactionsLayoutInBubble.VisibleReaction> finalList = new ArrayList<>();
            JSONArray array = grabReactions();
            for (int i = 0; i < array.length(); i++) {
                try {
                    ReactionsLayoutInBubble.VisibleReaction reaction = new ReactionsLayoutInBubble.VisibleReaction();
                    JSONObject object = array.getJSONObject(i);
                    if (object.has("document_id")) {
                        reaction.documentId = object.getLong("document_id");
                        reaction.hash = reaction.documentId;
                    } else if (object.has("emoticon")) {
                        reaction.emojicon = object.getString("emoticon").replace("\uFE0F", "");
                        reaction.hash = reaction.emojicon.hashCode();
                    }
                    finalList.add(reaction);
                } catch (JSONException ignored) {
                }
            }

            if (finalList.isEmpty()) {
                ArrayList<TLRPC.Reaction> topReactions = MediaDataController.getInstance(currentAccount).getTopReactions();
                for (int i = 0; i < topReactions.size(); i++) {
                    ReactionsLayoutInBubble.VisibleReaction visibleReaction = ReactionsLayoutInBubble.VisibleReaction.fromTL(topReactions.get(i));
                    if (visibleReaction.documentId == 0) {
                        finalList.add(visibleReaction);
                    }
                }
            }

            return finalList;
        }

        private void showKeyboard() {
            if (!emojiKeyboardVisible) {
                emojiKeyboardVisible = true;
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 512);
                updateScrollViewMarginBottom(bottomDialogLayout.getMeasuredHeight());
                bottomDialogLayout.setVisibility(View.VISIBLE);
                bottomDialogLayout.setTranslationY(bottomDialogLayout.getMeasuredHeight());
                bottomDialogLayout.animate().setListener(null).cancel();
                bottomDialogLayout.animate().translationY(0).withLayer().setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT).setUpdateListener(animation -> actionButton.setTranslationY(-(float) animation.getAnimatedValue() * bottomDialogLayout.getMeasuredHeight())).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 512);
                    }
                }).start();
            }
        }

        private void closeKeyboard() {
            if (emojiKeyboardVisible) {
                emojiKeyboardVisible = false;
                if (isClearFocusNotWorking()) {
                    switchLayout.setFocusableInTouchMode(true);
                    switchLayout.requestFocus();
                } else {
                    editText.clearFocus();
                }
                updateScrollViewMarginBottom(0);
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 512);
                bottomDialogLayout.animate().setListener(null).cancel();
                bottomDialogLayout.animate().translationY(bottomDialogLayout.getMeasuredHeight()).setDuration(350).withLayer().setInterpolator(CubicBezierInterpolator.DEFAULT).setUpdateListener(animation -> actionButton.setTranslationY(-(1f - (float) animation.getAnimatedValue()) * bottomDialogLayout.getMeasuredHeight())).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 512);
                        bottomDialogLayout.setVisibility(View.INVISIBLE);
                        if (isClearFocusNotWorking()) {
                            switchLayout.setFocusableInTouchMode(false);
                        }
                    }
                }).start();
            }
        }

        private boolean isClearFocusNotWorking() {
            return Build.MODEL.toLowerCase().startsWith("zte") && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P;
        }

        private void updateScrollViewMarginBottom(int margin) {
            ViewGroup.MarginLayoutParams marginLayoutParams = ((ViewGroup.MarginLayoutParams) scrollView.getLayoutParams());
            marginLayoutParams.bottomMargin = margin;
            scrollView.setLayoutParams(marginLayoutParams);
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
            ((MarginLayoutParams) scrollView.getLayoutParams()).topMargin = actionBarHeight;
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

        public void cleanup() {
            if (selectedEmojisMap != null) {
                selectedEmojisMap.clear();
            }
            if (selectedCustomEmojisList != null) {
                selectedCustomEmojisList.clear();
            }
            closeKeyboard();
        }
    }
}
