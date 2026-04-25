package tw.nekomimi.nekogram.filters;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ManageChatUserCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScrollSlidingTextTabStrip;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

import tw.nekomimi.nekogram.utils.AndroidUtil;

public class ShadowBanListActivity extends BaseFragment {

    private static final int TAB_USERS = 0;
    private static final int TAB_CHANNELS = 1;

    private static final int MENU_UNBLOCK_ALL = 1;
    private static final int MENU_ADD_FILTER = 2;

    private static final int VIEW_TYPE_USER_FILTER = 0;
    private static final int VIEW_TYPE_CHANNEL = 1;

    private static final int USERS_START_ROW = 0;

    private static final int CHANNELS_START_ROW = 0;

    private static final Interpolator INTERPOLATOR = t -> {
        --t;
        return t * t * t * t * t + 1.0f;
    };

    private final HashSet<Long> resolvingCustomFilteredUsers = new HashSet<>();
    private final HashSet<Long> resolvedCustomFilteredUsers = new HashSet<>();
    private final HashMap<Long, String> customFilteredUserDisplayCache = new HashMap<>();

    private final Paint backgroundPaint = new Paint();
    private ScrollSlidingTextTabStrip scrollSlidingTextTabStrip;
    private final ViewPage[] viewPages = new ViewPage[2];
    private AnimatorSet tabsAnimation;
    private boolean tabsAnimationInProgress;
    private boolean animatingForward;
    private boolean backAnimation;
    private boolean swipeBackEnabled = true;
    private int maximumVelocity;
    private int bottomInset;
    private boolean channelsUnavailableBulletinShown;
    private final int initialTab;

    private ActionBarMenuItem optionsItem;

    public ShadowBanListActivity() {
        this(TAB_USERS);
    }

    private ShadowBanListActivity(int initialTab) {
        this.initialTab = initialTab == TAB_CHANNELS ? TAB_CHANNELS : TAB_USERS;
    }

    public static ShadowBanListActivity forChannels() {
        return new ShadowBanListActivity(TAB_CHANNELS);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, getResourceProvider()));
        actionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, getResourceProvider()), false);
        actionBar.setTitleColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, getResourceProvider()));
        actionBar.setAllowOverlayTitle(false);
        actionBar.setClipContent(true);
        actionBar.setCastShadows(false);
        actionBar.setExtraHeight(dp(44));
        actionBar.setTitle(getString(R.string.ShadowBan));
        actionBar.setAddToContainer(false);
        if (AndroidUtilities.isTablet()) {
            actionBar.setOccupyStatusBar(false);
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_ADD_FILTER) {
                    showAddCustomFilteredUserDialog();
                } else if (id == MENU_UNBLOCK_ALL) {
                    onUnblockAllMenuClick();
                }
            }
        });
        hasOwnBackground = true;

        ActionBarMenu menu = actionBar.createMenu();
        optionsItem = menu.addItem(0, R.drawable.ic_ab_other);
        optionsItem.setContentDescription(getString(R.string.AccDescrMoreOptions));
        optionsItem.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_avatar_actionBarSelectorBlue, getResourceProvider())));
        optionsItem.setPopupItemsSelectorColor(Theme.getColor(Theme.key_dialogButtonSelector, getResourceProvider()));

        scrollSlidingTextTabStrip = new ScrollSlidingTextTabStrip(context);
        scrollSlidingTextTabStrip.setUseSameWidth(true);
        actionBar.addView(scrollSlidingTextTabStrip, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44, Gravity.LEFT | Gravity.BOTTOM));
        scrollSlidingTextTabStrip.setDelegate(new ScrollSlidingTextTabStrip.ScrollSlidingTabStripDelegate() {
            @Override
            public void onPageSelected(int id, boolean forward) {
                if (viewPages[0].selectedType == id) {
                    return;
                }
                swipeBackEnabled = id == scrollSlidingTextTabStrip.getFirstTabId();
                viewPages[1].selectedType = id;
                viewPages[1].setVisibility(View.VISIBLE);
                switchToCurrentSelectedMode(true);
                animatingForward = forward;
            }

            @Override
            public void onPageScrolled(float progress) {
                if (progress == 1f && viewPages[1].getVisibility() != View.VISIBLE) {
                    return;
                }
                if (animatingForward) {
                    viewPages[0].setTranslationX(-progress * viewPages[0].getMeasuredWidth());
                    viewPages[1].setTranslationX(viewPages[0].getMeasuredWidth() - progress * viewPages[0].getMeasuredWidth());
                } else {
                    viewPages[0].setTranslationX(progress * viewPages[0].getMeasuredWidth());
                    viewPages[1].setTranslationX(progress * viewPages[0].getMeasuredWidth() - viewPages[0].getMeasuredWidth());
                }
                if (progress == 1f) {
                    ViewPage tempPage = viewPages[0];
                    viewPages[0] = viewPages[1];
                    viewPages[1] = tempPage;
                    viewPages[1].setVisibility(View.GONE);
                    updateSelectedTabUi();
                }
            }
        });

        ViewConfiguration configuration = ViewConfiguration.get(context);
        maximumVelocity = configuration.getScaledMaximumFlingVelocity();

        FrameLayout frameLayout;
        fragmentView = frameLayout = new FrameLayout(context) {

            private int startedTrackingPointerId;
            private boolean startedTracking;
            private boolean maybeStartTracking;
            private int startedTrackingX;
            private int startedTrackingY;
            private VelocityTracker velocityTracker;
            private boolean globalIgnoreLayout;

            private boolean prepareForMoving(MotionEvent ev, boolean forward) {
                int id = scrollSlidingTextTabStrip.getNextPageId(forward);
                if (id < 0) {
                    return false;
                }
                getParent().requestDisallowInterceptTouchEvent(true);
                maybeStartTracking = false;
                startedTracking = true;
                startedTrackingX = (int) ev.getX();
                actionBar.setEnabled(false);
                scrollSlidingTextTabStrip.setEnabled(false);
                viewPages[1].selectedType = id;
                viewPages[1].setVisibility(View.VISIBLE);
                animatingForward = forward;
                switchToCurrentSelectedMode(true);
                if (forward) {
                    viewPages[1].setTranslationX(viewPages[0].getMeasuredWidth());
                } else {
                    viewPages[1].setTranslationX(-viewPages[0].getMeasuredWidth());
                }
                return true;
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int widthSize = MeasureSpec.getSize(widthMeasureSpec);
                int heightSize = MeasureSpec.getSize(heightMeasureSpec);

                setMeasuredDimension(widthSize, heightSize);

                measureChildWithMargins(actionBar, widthMeasureSpec, 0, heightMeasureSpec, 0);
                int actionBarHeight = actionBar.getMeasuredHeight();

                globalIgnoreLayout = true;
                for (ViewPage page : viewPages) {
                    if (page == null || page.listView == null) {
                        continue;
                    }
                    page.listView.setPadding(0, actionBarHeight, 0, bottomInset);
                }
                globalIgnoreLayout = false;

                int childCount = getChildCount();
                for (int i = 0; i < childCount; i++) {
                    View child = getChildAt(i);
                    if (child == null || child.getVisibility() == GONE || child == actionBar) {
                        continue;
                    }
                    measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                }
            }

            @Override
            protected void dispatchDraw(@NonNull Canvas canvas) {
                super.dispatchDraw(canvas);
                if (parentLayout != null) {
                    parentLayout.drawHeaderShadow(canvas, actionBar.getMeasuredHeight() + (int) actionBar.getTranslationY());
                }
            }

            @Override
            public void requestLayout() {
                if (globalIgnoreLayout) {
                    return;
                }
                super.requestLayout();
            }

            private boolean checkTabsAnimationInProgress() {
                if (!tabsAnimationInProgress) {
                    return false;
                }
                boolean cancel = false;
                if (backAnimation) {
                    if (Math.abs(viewPages[0].getTranslationX()) < 1f) {
                        viewPages[0].setTranslationX(0);
                        viewPages[1].setTranslationX(viewPages[0].getMeasuredWidth() * (animatingForward ? 1 : -1));
                        cancel = true;
                    }
                } else if (Math.abs(viewPages[1].getTranslationX()) < 1f) {
                    viewPages[0].setTranslationX(viewPages[0].getMeasuredWidth() * (animatingForward ? -1 : 1));
                    viewPages[1].setTranslationX(0);
                    cancel = true;
                }
                if (cancel) {
                    if (tabsAnimation != null) {
                        tabsAnimation.cancel();
                        tabsAnimation = null;
                    }
                    tabsAnimationInProgress = false;
                }
                return tabsAnimationInProgress;
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                return checkTabsAnimationInProgress() || scrollSlidingTextTabStrip.isAnimatingIndicator() || onTouchEvent(ev);
            }

            @Override
            protected void onDraw(@NonNull Canvas canvas) {
                backgroundPaint.setColor(Theme.getColor(Theme.key_windowBackgroundGray, getResourceProvider()));
                canvas.drawRect(0, actionBar.getMeasuredHeight(), getMeasuredWidth(), getMeasuredHeight(), backgroundPaint);
            }

            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouchEvent(MotionEvent ev) {
                if (!parentLayout.checkTransitionAnimation() && !checkTabsAnimationInProgress()) {
                    if (ev != null) {
                        if (velocityTracker == null) {
                            velocityTracker = VelocityTracker.obtain();
                        }
                        velocityTracker.addMovement(ev);
                    }
                    if (ev != null && ev.getAction() == MotionEvent.ACTION_DOWN && !startedTracking && !maybeStartTracking) {
                        startedTrackingPointerId = ev.getPointerId(0);
                        maybeStartTracking = true;
                        startedTrackingX = (int) ev.getX();
                        startedTrackingY = (int) ev.getY();
                        velocityTracker.clear();
                    } else if (ev != null && ev.getAction() == MotionEvent.ACTION_MOVE && ev.getPointerId(0) == startedTrackingPointerId) {
                        int dx = (int) (ev.getX() - startedTrackingX);
                        int dy = Math.abs((int) ev.getY() - startedTrackingY);
                        if (startedTracking && (animatingForward && dx > 0 || !animatingForward && dx < 0)) {
                            if (!prepareForMoving(ev, dx < 0)) {
                                maybeStartTracking = true;
                                startedTracking = false;
                                viewPages[0].setTranslationX(0);
                                viewPages[1].setTranslationX(animatingForward ? viewPages[0].getMeasuredWidth() : -viewPages[0].getMeasuredWidth());
                                scrollSlidingTextTabStrip.selectTabWithId(viewPages[1].selectedType, 0);
                            }
                        }
                        if (maybeStartTracking && !startedTracking) {
                            float touchSlop = AndroidUtilities.getPixelsInCM(0.3f, true);
                            if (Math.abs(dx) >= touchSlop && Math.abs(dx) > dy) {
                                prepareForMoving(ev, dx < 0);
                            }
                        } else if (startedTracking) {
                            viewPages[0].setTranslationX(dx);
                            if (animatingForward) {
                                viewPages[1].setTranslationX(viewPages[0].getMeasuredWidth() + dx);
                            } else {
                                viewPages[1].setTranslationX(dx - viewPages[0].getMeasuredWidth());
                            }
                            float scrollProgress = Math.abs(dx) / (float) viewPages[0].getMeasuredWidth();
                            scrollSlidingTextTabStrip.selectTabWithId(viewPages[1].selectedType, scrollProgress);
                        }
                    } else if (ev == null || ev.getPointerId(0) == startedTrackingPointerId && (ev.getAction() == MotionEvent.ACTION_CANCEL || ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_POINTER_UP)) {
                        velocityTracker.computeCurrentVelocity(1000, maximumVelocity);
                        float velX;
                        float velY;
                        if (ev != null && ev.getAction() != MotionEvent.ACTION_CANCEL) {
                            velX = velocityTracker.getXVelocity();
                            velY = velocityTracker.getYVelocity();
                            if (!startedTracking && Math.abs(velX) >= 3000 && Math.abs(velX) > Math.abs(velY)) {
                                prepareForMoving(ev, velX < 0);
                            }
                        } else {
                            velX = 0;
                            velY = 0;
                        }
                        if (startedTracking) {
                            float x = viewPages[0].getX();
                            tabsAnimation = new AnimatorSet();
                            backAnimation = Math.abs(x) < viewPages[0].getMeasuredWidth() / 3.0f && (Math.abs(velX) < 3500 || Math.abs(velX) < Math.abs(velY));
                            float dx;
                            if (backAnimation) {
                                dx = Math.abs(x);
                                if (animatingForward) {
                                    tabsAnimation.playTogether(ObjectAnimator.ofFloat(viewPages[0], View.TRANSLATION_X, 0), ObjectAnimator.ofFloat(viewPages[1], View.TRANSLATION_X, viewPages[1].getMeasuredWidth()));
                                } else {
                                    tabsAnimation.playTogether(ObjectAnimator.ofFloat(viewPages[0], View.TRANSLATION_X, 0), ObjectAnimator.ofFloat(viewPages[1], View.TRANSLATION_X, -viewPages[1].getMeasuredWidth()));
                                }
                            } else {
                                dx = viewPages[0].getMeasuredWidth() - Math.abs(x);
                                if (animatingForward) {
                                    tabsAnimation.playTogether(ObjectAnimator.ofFloat(viewPages[0], View.TRANSLATION_X, -viewPages[0].getMeasuredWidth()), ObjectAnimator.ofFloat(viewPages[1], View.TRANSLATION_X, 0));
                                } else {
                                    tabsAnimation.playTogether(ObjectAnimator.ofFloat(viewPages[0], View.TRANSLATION_X, viewPages[0].getMeasuredWidth()), ObjectAnimator.ofFloat(viewPages[1], View.TRANSLATION_X, 0));
                                }
                            }
                            tabsAnimation.setInterpolator(INTERPOLATOR);

                            int width = getMeasuredWidth();
                            int halfWidth = width / 2;
                            float distanceRatio = Math.min(1.0f, dx / width);
                            float distance = halfWidth + halfWidth * AndroidUtilities.distanceInfluenceForSnapDuration(distanceRatio);
                            velX = Math.abs(velX);
                            int duration;
                            if (velX > 0) {
                                duration = 4 * Math.round(1000.0f * Math.abs(distance / velX));
                            } else {
                                float pageDelta = dx / getMeasuredWidth();
                                duration = (int) ((pageDelta + 1.0f) * 100.0f);
                            }
                            duration = Math.max(150, Math.min(duration, 600));

                            tabsAnimation.setDuration(duration);
                            tabsAnimation.addListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animator) {
                                    tabsAnimation = null;
                                    if (backAnimation) {
                                        viewPages[1].setVisibility(View.GONE);
                                    } else {
                                        ViewPage tempPage = viewPages[0];
                                        viewPages[0] = viewPages[1];
                                        viewPages[1] = tempPage;
                                        viewPages[1].setVisibility(View.GONE);
                                        swipeBackEnabled = viewPages[0].selectedType == scrollSlidingTextTabStrip.getFirstTabId();
                                        scrollSlidingTextTabStrip.selectTabWithId(viewPages[0].selectedType, 1.0f);
                                    }
                                    tabsAnimationInProgress = false;
                                    maybeStartTracking = false;
                                    startedTracking = false;
                                    actionBar.setEnabled(true);
                                    scrollSlidingTextTabStrip.setEnabled(true);
                                    updateSelectedTabUi();
                                }
                            });
                            tabsAnimation.start();
                            tabsAnimationInProgress = true;
                            startedTracking = false;
                        } else {
                            maybeStartTracking = false;
                            actionBar.setEnabled(true);
                            scrollSlidingTextTabStrip.setEnabled(true);
                        }
                        if (velocityTracker != null) {
                            velocityTracker.recycle();
                            velocityTracker = null;
                        }
                    }
                    return startedTracking;
                }
                return false;
            }
        };
        frameLayout.setWillNotDraw(false);
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, getResourceProvider()));

        for (int i = 0; i < viewPages.length; i++) {
            ViewPage page = new ViewPage(context) {
                @Override
                public void setTranslationX(float translationX) {
                    super.setTranslationX(translationX);
                    if (tabsAnimationInProgress && viewPages[0] == this) {
                        float scrollProgress = Math.abs(viewPages[0].getTranslationX()) / (float) viewPages[0].getMeasuredWidth();
                        scrollSlidingTextTabStrip.selectTabWithId(viewPages[1].selectedType, scrollProgress);
                    }
                }
            };
            page.emptyView = new EmptyTextProgressView(context);
            page.emptyView.setText(getString(R.string.BlockedChannelsEmpty));
            page.emptyView.showTextView();
            page.addView(page.emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            page.listView = new RecyclerListView(context);
            page.listView.setSections();
            page.listView.setClipToPadding(false);
            page.listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
            page.listView.setVerticalScrollBarEnabled(false);
            page.listView.setVerticalScrollbarPosition(LocaleController.isRTL ? RecyclerListView.SCROLLBAR_POSITION_LEFT : RecyclerListView.SCROLLBAR_POSITION_RIGHT);
            page.adapter = new PageAdapter(page, context);
            page.listView.setAdapter(page.adapter);
            page.listView.setOnItemClickListener((view, position, x, y) -> onPageItemClick(page, position));
            page.listView.setOnItemLongClickListener((view, position, x, y) -> onPageItemLongClick(page, view, position));
            page.addView(page.listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            if (i == 0) {
                page.selectedType = initialTab;
            } else {
                page.selectedType = initialTab == TAB_CHANNELS ? TAB_USERS : TAB_CHANNELS;
                page.setVisibility(View.GONE);
            }
            bindPage(page, page.selectedType);
            viewPages[i] = page;
            frameLayout.addView(page, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }

        frameLayout.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        updateTabs();
        viewPages[0].selectedType = initialTab;
        viewPages[1].selectedType = initialTab == TAB_CHANNELS ? TAB_USERS : TAB_CHANNELS;
        scrollSlidingTextTabStrip.selectTabWithId(initialTab, 1.0f);
        switchToCurrentSelectedMode(false);
        swipeBackEnabled = scrollSlidingTextTabStrip.getCurrentTabId() == scrollSlidingTextTabStrip.getFirstTabId();

        return fragmentView;
    }

    @SuppressLint("NotifyDataSetChanged")
    private void bindPage(ViewPage page, int type) {
        page.selectedType = type;
        if (type == TAB_CHANNELS) {
            page.listView.setEmptyView(page.emptyView);
            page.emptyView.setVisibility(View.VISIBLE);
            page.emptyView.showTextView();
        } else {
            page.listView.setEmptyView(null);
            page.emptyView.setVisibility(View.GONE);
        }
        page.adapter.notifyDataSetChanged();
    }

    private void onPageItemClick(ViewPage page, int position) {
        if (page.selectedType == TAB_USERS) {
            if (position >= USERS_START_ROW) {
                ArrayList<Long> userIds = AyuFilter.getCustomFilteredUsersList();
                int userIndex = position - USERS_START_ROW;
                if (userIndex >= userIds.size()) {
                    return;
                }
                presentFragment(ProfileActivity.of(userIds.get(userIndex)));
            }
        } else if (isBlockedChannelRow(position)) {
            ArrayList<Long> blockedChannels = AyuFilter.getBlockedChannelsList();
            int channelIndex = position - CHANNELS_START_ROW;
            if (channelIndex < 0 || channelIndex >= blockedChannels.size()) {
                return;
            }
            presentFragment(ProfileActivity.of(blockedChannels.get(channelIndex)));
        }
    }

    private boolean onPageItemLongClick(ViewPage page, View view, int position) {
        if (page.selectedType == TAB_USERS && position >= USERS_START_ROW) {
            ArrayList<Long> userIds = AyuFilter.getCustomFilteredUsersList();
            int userIndex = position - USERS_START_ROW;
            if (userIndex < userIds.size()) {
                showUserOptions(userIds.get(userIndex), view);
                return true;
            }
        } else if (page.selectedType == TAB_CHANNELS && isBlockedChannelRow(position)) {
            ArrayList<Long> blockedChannels = AyuFilter.getBlockedChannelsList();
            int channelIndex = position - CHANNELS_START_ROW;
            if (channelIndex >= 0 && channelIndex < blockedChannels.size()) {
                showChannelOptions(blockedChannels.get(channelIndex), view);
                return true;
            }
        }
        return false;
    }

    private void updateTabs() {
        scrollSlidingTextTabStrip.addTextTab(TAB_USERS, getString(R.string.BookmarksFilterUsers) + "・" + getString(R.string.FilterBots));
        scrollSlidingTextTabStrip.addTextTab(TAB_CHANNELS, getString(R.string.FilterChannels));
        scrollSlidingTextTabStrip.setVisibility(View.VISIBLE);
        int id = scrollSlidingTextTabStrip.getCurrentTabId();
        if (id >= 0) {
            viewPages[0].selectedType = id;
        }
        scrollSlidingTextTabStrip.finishAddingTabs();
    }

    private void switchToCurrentSelectedMode(boolean animated) {
        for (ViewPage page : viewPages) {
            if (page != null && page.listView != null) {
                page.listView.stopScroll();
            }
        }
        bindPage(viewPages[animated ? 1 : 0], viewPages[animated ? 1 : 0].selectedType);
        updateSelectedTabUi(animated ? viewPages[1].selectedType : viewPages[0].selectedType);
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        return swipeBackEnabled;
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onResume() {
        super.onResume();
        channelsUnavailableBulletinShown = false;
        invalidateCustomFilteredUsersDisplayState();
        refreshAllPages();
        maybeShowBlockedChannelsUnavailableBulletin();
    }

    private void showAddCustomFilteredUserDialog() {
        Context context = getContext();
        if (context == null) {
            return;
        }

        EditTextBoldCursor editText = new EditTextBoldCursor(context);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, getResourceProvider()));
        editText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText, getResourceProvider()));
        editText.setHandlesColor(Theme.getColor(Theme.key_chat_TextSelectionCursor, getResourceProvider()));
        editText.setBackground(null);
        editText.setLineColors(Theme.getColor(Theme.key_windowBackgroundWhiteInputField, getResourceProvider()), Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated, getResourceProvider()), Theme.getColor(Theme.key_text_RedRegular, getResourceProvider()));
        editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        editText.setMinLines(1);
        editText.setMaxLines(1);
        editText.setHint(getString(R.string.RegexFiltersUserFilterHint));
        editText.setPadding(0, 0, 0, dp(6));
        editText.requestFocus();

        FrameLayout container = new FrameLayout(context);
        container.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 24, 8, 24, 0));

        AlertDialog dialog = new AlertDialog.Builder(context, getResourceProvider()).setTitle(getString(R.string.RegexFiltersAdd)).setView(container).setNegativeButton(getString(R.string.Cancel), null).setPositiveButton(getString(R.string.Save), null).create();

        dialog.setOnShowListener(d -> dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            String input = editText.getText() == null ? "" : editText.getText().toString();
            ParsedSingleIdResult result = parseSingleCustomFilteredUser(input);
            if (!result.valid) {
                AndroidUtil.showInputError(editText);
                return;
            }
            long selfUserId = getUserConfig().getClientUserId();
            if (result.userId == selfUserId) {
                AndroidUtil.showInputError(editText);
                return;
            }

            HashSet<Long> idSet = new HashSet<>(AyuFilter.getCustomFilteredUsersList());
            if (idSet.contains(result.userId)) {
                AndroidUtil.showInputError(editText);
                return;
            }

            idSet.add(result.userId);
            ArrayList<Long> updated = new ArrayList<>(idSet);
            Collections.sort(updated);
            AyuFilter.setCustomFilteredUsers(updated);
            TLRPC.User localUser = getMessagesController().getUser(result.userId);
            if (localUser != null) {
                AyuFilter.updateCustomFilteredUserFromLocalUser(localUser);
            }
            refreshUsers();
            dialog.dismiss();
        }));
        showDialog(dialog);
    }

    public void deleteCustomFilteredUser(long userId) {
        ArrayList<Long> userIds = AyuFilter.getCustomFilteredUsersList();
        if (!userIds.remove(userId)) {
            return;
        }
        AyuFilter.setCustomFilteredUsers(userIds);
        refreshUsers();
    }

    private ParsedSingleIdResult parseSingleCustomFilteredUser(String rawInput) {
        ParsedSingleIdResult result = new ParsedSingleIdResult();
        String input = rawInput == null ? "" : rawInput.trim();
        if (TextUtils.isEmpty(input)) {
            return result;
        }
        if (input.contains(",") || input.contains(" ") || input.contains("\n") || input.contains("\t")) {
            return result;
        }
        try {
            long userId = Long.parseLong(input);
            if (userId < 100000) {
                return result;
            }
            result.valid = true;
            result.userId = userId;
            return result;
        } catch (Exception ignore) {
            return result;
        }
    }

    private void invalidateCustomFilteredUsersDisplayState() {
        resolvingCustomFilteredUsers.clear();
        resolvedCustomFilteredUsers.clear();
        customFilteredUserDisplayCache.clear();
    }

    private boolean isCustomFilteredUserUntracked(long userId) {
        return userId <= 0L || !AyuFilter.getCustomFilteredUsersList().contains(userId);
    }

    private void clearCustomFilteredUserDisplayState(long userId) {
        resolvingCustomFilteredUsers.remove(userId);
        resolvedCustomFilteredUsers.remove(userId);
        customFilteredUserDisplayCache.remove(userId);
    }

    private boolean hasUsableUserIdentity(TLRPC.User user) {
        if (user == null || user.id == 0L) {
            return false;
        }
        String displayName = UserObject.getUserName(user);
        if (!TextUtils.isEmpty(displayName) && !TextUtils.isEmpty(displayName.trim())) {
            return true;
        }
        return !TextUtils.isEmpty(UserObject.getPublicUsername(user));
    }

    private String formatResolvedUserTitle(TLRPC.User user) {
        if (user == null || user.id == 0L) {
            return null;
        }
        String displayName = UserObject.getUserName(user);
        if (!TextUtils.isEmpty(displayName)) {
            displayName = displayName.trim();
        }
        if (TextUtils.isEmpty(displayName)) {
            String username = UserObject.getPublicUsername(user);
            if (!TextUtils.isEmpty(username)) {
                displayName = "@" + username;
            }
        }
        if (TextUtils.isEmpty(displayName)) {
            return String.valueOf(user.id);
        }
        return displayName;
    }

    private String buildFallbackCustomFilteredUserTitle(AyuFilter.CustomFilteredUser userData, long userId) {
        if (userData != null) {
            if (!TextUtils.isEmpty(userData.displayName)) {
                String displayName = userData.displayName.trim();
                if (!TextUtils.isEmpty(displayName)) {
                    return displayName;
                }
            }
            String username = userData.username;
            if (!TextUtils.isEmpty(username)) {
                return "@" + username;
            }
        }
        return String.valueOf(userId);
    }

    private String getCustomFilteredUserRowSubtitle(long userId) {
        return String.valueOf(userId);
    }

    private boolean cacheResolvedCustomFilteredUser(long userId, TLRPC.User user, boolean notifyRow) {
        if (user == null || user.id != userId) {
            return false;
        }
        AyuFilter.updateCustomFilteredUserFromLocalUser(user);
        if (!hasUsableUserIdentity(user)) {
            return false;
        }
        String title = formatResolvedUserTitle(user);
        if (TextUtils.isEmpty(title)) {
            return false;
        }
        resolvingCustomFilteredUsers.remove(userId);
        resolvedCustomFilteredUsers.add(userId);
        customFilteredUserDisplayCache.put(userId, title);
        if (notifyRow) {
            notifyCustomFilteredUserRowChanged(userId);
        }
        return true;
    }

    private String getCustomFilteredUserRowTitle(long userId) {
        TLRPC.User localUser = getMessagesController().getUser(userId);
        if (cacheResolvedCustomFilteredUser(userId, localUser, false)) {
            return customFilteredUserDisplayCache.get(userId);
        }
        String cached = customFilteredUserDisplayCache.get(userId);
        if (!TextUtils.isEmpty(cached)) {
            return cached;
        }
        AyuFilter.CustomFilteredUser userData = AyuFilter.getCustomFilteredUser(userId);
        String fallback = buildFallbackCustomFilteredUserTitle(userData, userId);
        customFilteredUserDisplayCache.put(userId, fallback);
        return fallback;
    }

    private void ensureCustomFilteredUserResolved(long userId) {
        if (userId <= 0L || isCustomFilteredUserUntracked(userId)) {
            return;
        }
        TLRPC.User localUser = getMessagesController().getUser(userId);
        if (cacheResolvedCustomFilteredUser(userId, localUser, false)) {
            return;
        }
        if (resolvedCustomFilteredUsers.contains(userId) || resolvingCustomFilteredUsers.contains(userId)) {
            return;
        }
        resolvingCustomFilteredUsers.add(userId);
        resolveCustomFilteredUserFromLocalDb(userId);
    }

    private void resolveCustomFilteredUserFromLocalDb(long userId) {
        Utilities.globalQueue.postRunnable(() -> {
            TLRPC.User storageUser = getMessagesStorage().getUserSync(userId);
            AndroidUtilities.runOnUIThread(() -> {
                if (isCustomFilteredUserUntracked(userId)) {
                    clearCustomFilteredUserDisplayState(userId);
                    return;
                }
                if (resolvedCustomFilteredUsers.contains(userId) && !resolvingCustomFilteredUsers.contains(userId)) {
                    return;
                }
                if (cacheResolvedCustomFilteredUser(userId, storageUser, true)) {
                    getMessagesController().putUser(storageUser, true);
                    return;
                }
                if (storageUser != null) {
                    getMessagesController().putUser(storageUser, true);
                    AyuFilter.updateCustomFilteredUserFromLocalUser(storageUser);
                }
                resolveCustomFilteredUserByUsername(userId);
            });
        });
    }

    private void resolveCustomFilteredUserByUsername(long userId) {
        AyuFilter.CustomFilteredUser userData = AyuFilter.getCustomFilteredUser(userId);
        String username = userData != null ? userData.username : null;
        if (TextUtils.isEmpty(username)) {
            resolveCustomFilteredUserById(userId);
            return;
        }
        TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
        req.username = username;
        int reqId = getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (isCustomFilteredUserUntracked(userId)) {
                clearCustomFilteredUserDisplayState(userId);
                return;
            }
            if (resolvedCustomFilteredUsers.contains(userId) && !resolvingCustomFilteredUsers.contains(userId)) {
                return;
            }
            if (response instanceof TLRPC.TL_contacts_resolvedPeer resolvedPeer) {
                getMessagesController().putUsers(resolvedPeer.users, false);
                getMessagesController().putChats(resolvedPeer.chats, false);
                getMessagesStorage().putUsersAndChats(resolvedPeer.users, resolvedPeer.chats, true, true);
                boolean matched = resolvedPeer.peer instanceof TLRPC.TL_peerUser && resolvedPeer.peer.user_id == userId;
                if (matched) {
                    TLRPC.User resolvedUser = getMessagesController().getUser(userId);
                    if (resolvedUser == null && resolvedPeer.users != null) {
                        for (TLRPC.User user : resolvedPeer.users) {
                            if (user != null && user.id == userId) {
                                resolvedUser = user;
                                break;
                            }
                        }
                    }
                    if (cacheResolvedCustomFilteredUser(userId, resolvedUser, true)) {
                        return;
                    }
                }
            }
            resolveCustomFilteredUserById(userId);
        }));
        getConnectionsManager().bindRequestToGuid(reqId, classGuid);
    }

    @SuppressWarnings("rawtypes")
    private void resolveCustomFilteredUserById(long userId) {
        AyuFilter.CustomFilteredUser userData = AyuFilter.getCustomFilteredUser(userId);
        if (userData == null || userData.accessHash == 0L) {
            onCustomFilteredUserResolveFailed(userId);
            return;
        }
        TLRPC.TL_users_getUsers req = new TLRPC.TL_users_getUsers();
        TLRPC.TL_inputUser inputUser = new TLRPC.TL_inputUser();
        inputUser.user_id = userId;
        inputUser.access_hash = userData.accessHash;
        req.id.add(inputUser);
        int reqId = getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (isCustomFilteredUserUntracked(userId)) {
                clearCustomFilteredUserDisplayState(userId);
                return;
            }
            if (resolvedCustomFilteredUsers.contains(userId) && !resolvingCustomFilteredUsers.contains(userId)) {
                return;
            }
            if (error == null && response instanceof Vector vector) {
                ArrayList<TLRPC.User> users = new ArrayList<>();
                for (Object object : vector.objects) {
                    if (object instanceof TLRPC.User user) {
                        users.add(user);
                    }
                }
                if (!users.isEmpty()) {
                    getMessagesController().putUsers(users, false);
                    getMessagesStorage().putUsersAndChats(users, null, true, true);
                    TLRPC.User resolvedUser = null;
                    for (TLRPC.User user : users) {
                        if (user != null && user.id == userId) {
                            resolvedUser = user;
                            break;
                        }
                    }
                    if (resolvedUser == null) {
                        resolvedUser = getMessagesController().getUser(userId);
                    }
                    if (cacheResolvedCustomFilteredUser(userId, resolvedUser, true)) {
                        return;
                    }
                }
            }
            TLRPC.User localUser = getMessagesController().getUser(userId);
            if (cacheResolvedCustomFilteredUser(userId, localUser, true)) {
                return;
            }
            onCustomFilteredUserResolveFailed(userId);
        }));
        getConnectionsManager().bindRequestToGuid(reqId, classGuid);
    }

    private void onCustomFilteredUserResolveFailed(long userId) {
        if (isCustomFilteredUserUntracked(userId)) {
            clearCustomFilteredUserDisplayState(userId);
            return;
        }
        resolvingCustomFilteredUsers.remove(userId);
        resolvedCustomFilteredUsers.add(userId);
        AyuFilter.CustomFilteredUser userData = AyuFilter.getCustomFilteredUser(userId);
        customFilteredUserDisplayCache.put(userId, buildFallbackCustomFilteredUserTitle(userData, userId));
        notifyCustomFilteredUserRowChanged(userId);
    }

    private void notifyCustomFilteredUserRowChanged(long userId) {
        ArrayList<Long> userIds = AyuFilter.getCustomFilteredUsersList();
        int index = userIds.indexOf(userId);
        if (index < 0) {
            return;
        }
        int position = USERS_START_ROW + index;
        for (ViewPage page : viewPages) {
            if (page != null && page.selectedType == TAB_USERS && page.adapter != null) {
                page.adapter.notifyItemChanged(position);
            }
        }
    }

    private void maybeShowBlockedChannelsUnavailableBulletin() {
        if (channelsUnavailableBulletinShown || viewPages[0] == null || viewPages[0].selectedType != TAB_CHANNELS) {
            return;
        }
        int visibleCount = AyuFilter.getBlockedChannelsList().size();
        if (AyuFilter.getBlockedChannelsCount() == visibleCount) {
            return;
        }
        channelsUnavailableBulletinShown = true;
        AndroidUtilities.runOnUIThread(() -> BulletinFactory.of(ShadowBanListActivity.this).createSimpleBulletin(R.raw.chats_infotip, getString(R.string.BlockChannelsUnavailable)).show(), 350);
    }

    private void updateSelectedTabUi() {
        updateSelectedTabUi(viewPages[0].selectedType);
    }

    private void updateSelectedTabUi(int selectedType) {
        updateOptionsMenu(selectedType);
        if (viewPages[0] != null && viewPages[0].listView != null) {
            actionBar.setAdaptiveBackground(viewPages[0].listView);
        }
        maybeShowBlockedChannelsUnavailableBulletin();
    }

    private void updateOptionsMenu(int selectedType) {
        if (optionsItem == null) {
            return;
        }

        optionsItem.removeAllSubItems();
        if (selectedType == TAB_USERS) {
            optionsItem.addSubItem(MENU_ADD_FILTER, R.drawable.msg_add, getString(R.string.RegexFiltersAdd), getResourceProvider());
            if (hasCustomFilteredUsers()) {
                optionsItem.addColoredGap();
                addUnblockAllMenuItem();
            }
            optionsItem.setVisibility(true);
            return;
        }

        if (hasBlockedChannels()) {
            addUnblockAllMenuItem();
            optionsItem.setVisibility(true);
        } else {
            optionsItem.setVisibility(false);
        }
    }

    private void addUnblockAllMenuItem() {
        int redColor = Theme.getColor(Theme.key_text_RedRegular, getResourceProvider());
        ActionBarMenuSubItem subItem = optionsItem.addSubItem(MENU_UNBLOCK_ALL, R.drawable.menu_clear_cache, getString(R.string.UnblockAll), getResourceProvider());
        subItem.setColors(redColor, redColor);
        subItem.setSelectorColor(Theme.multAlpha(redColor, .12f));
    }

    private void onUnblockAllMenuClick() {
        int selectedType = viewPages[0] != null ? viewPages[0].selectedType : TAB_USERS;
        if (selectedType == TAB_USERS) {
            showUnblockAllUsersDialog();
        } else if (selectedType == TAB_CHANNELS) {
            showUnblockAllChannelsDialog();
        }
    }

    private boolean hasCustomFilteredUsers() {
        return !AyuFilter.getCustomFilteredUsersList().isEmpty();
    }

    private boolean hasBlockedChannels() {
        return AyuFilter.getBlockedChannelsCount() > 0;
    }

    private void showUnblockAllUsersDialog() {
        if (getParentActivity() == null || !hasCustomFilteredUsers()) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
        builder.setTitle(getString(R.string.UnblockAll));
        builder.setMessage(getString(R.string.UnblockAllWarn));
        builder.setPositiveButton(getString(R.string.OK), (dialog, which) -> {
            AyuFilter.setCustomFilteredUsers(new ArrayList<>());
            refreshUsers();
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showUnblockAllChannelsDialog() {
        if (getParentActivity() == null || !hasBlockedChannels()) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
        builder.setTitle(getString(R.string.UnblockAll));
        builder.setMessage(getString(R.string.UnblockAllChannelsWarn));
        builder.setPositiveButton(getString(R.string.UnblockAll), (dialog, which) -> {
            AyuFilter.clearBlockedChannels();
            refreshAllPages();
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showUserOptions(long userId, View view) {
        if (getParentActivity() == null) {
            return;
        }
        ItemOptions.makeOptions(this, view).setScrimViewBackground(new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundWhite, getResourceProvider()))).add(R.drawable.msg_delete, getString(R.string.UnshadowBan), true, () -> deleteCustomFilteredUser(userId)).setMinWidth(190).show();
    }

    private void showChannelOptions(long dialogId, View view) {
        if (getParentActivity() == null) {
            return;
        }
        ItemOptions.makeOptions(this, view).setScrimViewBackground(new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundWhite, getResourceProvider()))).add(R.drawable.msg_delete, getString(R.string.UnshadowBan), true, () -> {
            AyuFilter.unblockPeer(dialogId);
            refreshAllPages();
        }).setMinWidth(190).show();
    }

    private void refreshAllPages() {
        for (ViewPage page : viewPages) {
            if (page != null) {
                bindPage(page, page.selectedType);
            }
        }
        if (viewPages[0] != null) {
            updateSelectedTabUi(viewPages[0].selectedType);
        }
    }

    private void refreshUsers() {
        invalidateCustomFilteredUsersDisplayState();
        refreshAllPages();
    }

    private boolean isBlockedChannelRow(int position) {
        return position >= CHANNELS_START_ROW && position < AyuFilter.getBlockedChannelsList().size();
    }

    private int getUsersRowCount() {
        return AyuFilter.getCustomFilteredUsersList().size();
    }

    private int getBlockedChannelsRowCount() {
        return AyuFilter.getBlockedChannelsList().size();
    }

    private static class ParsedSingleIdResult {
        boolean valid;
        long userId;
    }

    private static class ViewPage extends FrameLayout {
        private RecyclerListView listView;
        private EmptyTextProgressView emptyView;
        private int selectedType;
        private PageAdapter adapter;

        public ViewPage(Context context) {
            super(context);
        }
    }

    private class PageAdapter extends RecyclerListView.SelectionAdapter {

        private final ViewPage page;
        private final Context context;

        PageAdapter(ViewPage page, Context context) {
            this.page = page;
            this.context = context;
        }

        @Override
        public int getItemCount() {
            return page.selectedType == TAB_USERS ? getUsersRowCount() : getBlockedChannelsRowCount();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int viewType = holder.getItemViewType();
            return viewType == VIEW_TYPE_USER_FILTER || viewType == VIEW_TYPE_CHANNEL;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case VIEW_TYPE_USER_FILTER:
                    ManageChatUserCell userFilterCell = new ManageChatUserCell(context, 7, 6, true);
                    userFilterCell.setDelegate((cell, click) -> {
                        if (click && cell.getTag() instanceof Long userId) {
                            showUserOptions(userId, cell);
                        }
                        return true;
                    });
                    view = userFilterCell;
                    break;
                case VIEW_TYPE_CHANNEL:
                    ManageChatUserCell channelCell = new ManageChatUserCell(context, 7, 6, true);
                    channelCell.setDelegate((cell, click) -> {
                        if (click && cell.getTag() instanceof Long dialogId) {
                            showChannelOptions(dialogId, cell);
                        }
                        return true;
                    });
                    view = channelCell;
                    break;
                default:
                    ManageChatUserCell defaultCell = new ManageChatUserCell(context, 7, 6, true);
                    defaultCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, getResourceProvider()));
                    view = defaultCell;
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (page.selectedType == TAB_USERS) {
                bindUsersView(holder, position);
            } else {
                bindChannelsView(holder, position);
            }
        }

        private void bindUsersView(RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() != VIEW_TYPE_USER_FILTER) {
                return;
            }

            ArrayList<Long> userIds = AyuFilter.getCustomFilteredUsersList();
            int userIndex = position - USERS_START_ROW;
            if (userIndex >= 0 && userIndex < userIds.size()) {
                long userId = userIds.get(userIndex);
                boolean needDivider = position + 1 < getUsersRowCount();
                ManageChatUserCell userCell = (ManageChatUserCell) holder.itemView;
                userCell.setTag(userId);
                TLRPC.User user = getMessagesController().getUser(userId);
                if (user == null) {
                    user = new TLRPC.TL_user();
                    user.id = userId;
                }
                userCell.setData(user, getCustomFilteredUserRowTitle(userId), getCustomFilteredUserRowSubtitle(userId), needDivider);
                ensureCustomFilteredUserResolved(userId);
            }
        }

        private void bindChannelsView(RecyclerView.ViewHolder holder, int position) {
            ArrayList<Long> blockedChannels = AyuFilter.getBlockedChannelsList();
            if (holder.getItemViewType() == VIEW_TYPE_CHANNEL) {
                int channelIndex = position - CHANNELS_START_ROW;
                if (channelIndex < 0 || channelIndex >= blockedChannels.size()) {
                    return;
                }
                long dialogId = blockedChannels.get(channelIndex);
                ManageChatUserCell userCell = (ManageChatUserCell) holder.itemView;
                userCell.setTag(dialogId);
                TLRPC.Chat chat = getMessagesController().getChat(-dialogId);
                if (chat == null) {
                    return;
                }
                String subtitle;
                if (chat.participants_count != 0) {
                    subtitle = formatPluralString("Members", chat.participants_count);
                } else if (chat.has_geo) {
                    subtitle = getString(R.string.MegaLocation);
                } else if (!ChatObject.isPublic(chat)) {
                    subtitle = ChatObject.isChannelAndNotMegaGroup(chat) ? getString(R.string.ChannelPrivate) : getString(R.string.MegaPrivate);
                } else {
                    subtitle = ChatObject.isChannelAndNotMegaGroup(chat) ? getString(R.string.ChannelPublic) : getString(R.string.MegaPublic);
                }
                userCell.setData(chat, null, subtitle, position != getBlockedChannelsRowCount() - 1);
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (page.selectedType == TAB_USERS) {
                return VIEW_TYPE_USER_FILTER;
            }
            return VIEW_TYPE_CHANNEL;
        }
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        bottomInset = bottom;
        for (ViewPage page : viewPages) {
            if (page != null && page.listView != null) {
                page.listView.setPadding(0, page.listView.getPaddingTop(), 0, bottom);
            }
        }
    }
}
