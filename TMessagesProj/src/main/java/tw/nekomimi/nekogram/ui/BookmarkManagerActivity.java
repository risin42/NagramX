package tw.nekomimi.nekogram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.FilterTabsView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.ViewPagerFixed;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.DownscaleScrollableNoiseSuppressor;
import org.telegram.ui.Components.blur3.ViewGroupPartRenderer;
import org.telegram.ui.Components.blur3.capture.IBlur3Capture;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode;
import org.telegram.ui.Components.chat.ViewPositionWatcher;
import org.telegram.ui.SearchTabsAndFiltersLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;

import tw.nekomimi.nekogram.ui.cells.BookmarksChatCell;
import tw.nekomimi.nekogram.utils.AlertUtil;
import xyz.nextalone.nagram.helper.BookmarksHelper;

public class BookmarkManagerActivity extends BaseFragment {

    private static final int SEARCH_BUTTON = 1;
    private static final int OPTIONS_BUTTON = 2;
    private static final int CLEAR_ALL_BOOKMARKS = 3;

    private static final int TAB_ALL = 0;
    private static final int TAB_CHANNELS = 1;
    private static final int TAB_GROUPS = 2;
    private static final int TAB_USERS = 3;
    private static final int TAB_BOTS = 4;
    private static final int ACTION_BAR_BLUR_ALPHA = 178;
    private static final int TABS_CONTAINER_HEIGHT_DP = 50;
    private final ArrayList<BookmarkChatItem> allItems = new ArrayList<>();
    private final CubicBezierInterpolator interpolator = CubicBezierInterpolator.EASE_OUT_QUINT;
    private TextView emptyView;
    private ActionBarMenuItem searchItem;
    private SearchTabsAndFiltersLayout tabsContainer;
    private ViewPagerFixed.TabsView tabsView;
    private BlurredBackgroundDrawable tabsContainerBackground;
    private int selectedTabId = TAB_ALL;
    private String searchQuery = "";
    private int loadRequestId;
    private ContentLayout contentLayout;
    private ViewPage[] viewPages;
    private boolean swipeBackEnabled = true;
    private AnimatorSet tabsAnimation;
    private boolean tabsAnimationInProgress;
    private boolean backAnimation;
    private boolean animatingForward;
    private float additionalOffset;
    private int maximumVelocity;
    private int startedTrackingPointerId;
    private boolean startedTracking;
    private boolean maybeStartTracking;
    private int startedTrackingX;
    private int startedTrackingY;
    private VelocityTracker velocityTracker;
    private final BlurredBackgroundSourceColor tabsBackgroundSourceColor;
    private final BlurredBackgroundSourceRenderNode tabsBackgroundSourceFrosted;
    private final BlurredBackgroundSourceRenderNode tabsBackgroundSourceGlass;
    private final BlurredBackgroundDrawableViewFactory tabsBackgroundDrawableFactory;
    private ViewPositionWatcher tabsViewPositionWatcher;

    private final DownscaleScrollableNoiseSuppressor scrollableViewNoiseSuppressor;
    private IBlur3Capture blur3Capture;
    private boolean blur3Invalidated;
    private final ArrayList<RectF> blur3Positions = new ArrayList<>();
    private final RectF blur3PositionActionBar = new RectF();
    private final RectF blur3PositionTabs = new RectF();

    private int getTopContentOffset() {
        if (actionBar == null) {
            return 0;
        }
        return ActionBar.getCurrentActionBarHeight() + (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0);
    }

    private void updateBlur3Capture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || scrollableViewNoiseSuppressor == null || viewPages == null) {
            blur3Capture = null;
            return;
        }
        blur3Capture = viewPages[0].blur3Capture;
    }

    public BookmarkManagerActivity() {
        super();

        tabsBackgroundSourceColor = new BlurredBackgroundSourceColor();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            scrollableViewNoiseSuppressor = new DownscaleScrollableNoiseSuppressor();
            tabsBackgroundSourceFrosted = new BlurredBackgroundSourceRenderNode(tabsBackgroundSourceColor);
            tabsBackgroundSourceFrosted.setScrollableNoiseSuppressor(scrollableViewNoiseSuppressor, DownscaleScrollableNoiseSuppressor.DRAW_FROSTED_GLASS);
            tabsBackgroundSourceFrosted.setUnderSource(tabsBackgroundSourceColor);

            if (LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS)) {
                tabsBackgroundSourceGlass = new BlurredBackgroundSourceRenderNode(tabsBackgroundSourceColor);
                tabsBackgroundSourceGlass.setScrollableNoiseSuppressor(scrollableViewNoiseSuppressor, DownscaleScrollableNoiseSuppressor.DRAW_GLASS);
                tabsBackgroundSourceGlass.setUnderSource(tabsBackgroundSourceColor);
                tabsBackgroundDrawableFactory = new BlurredBackgroundDrawableViewFactory(tabsBackgroundSourceGlass);
            } else {
                tabsBackgroundSourceGlass = null;
                tabsBackgroundDrawableFactory = new BlurredBackgroundDrawableViewFactory(tabsBackgroundSourceFrosted);
            }
            tabsBackgroundDrawableFactory.setLiquidGlassEffectAllowed(LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS));
        } else {
            scrollableViewNoiseSuppressor = null;
            tabsBackgroundSourceFrosted = null;
            tabsBackgroundSourceGlass = null;
            tabsBackgroundDrawableFactory = new BlurredBackgroundDrawableViewFactory(tabsBackgroundSourceColor);
        }
        blur3Positions.add(blur3PositionActionBar);
        blur3Positions.add(blur3PositionTabs);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        actionBar.setTitle(getString(R.string.BookmarksManager));
        actionBar.setAllowOverlayTitle(false);
        actionBar.setClipContent(true);
        actionBar.setCastShadows(false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (onBackPressed(true)) {
                        finishFragment();
                    }
                } else if (id == CLEAR_ALL_BOOKMARKS) {
                    onClearAllBookmarksClicked();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        searchItem = menu.addItem(SEARCH_BUTTON, R.drawable.outline_header_search).setIsSearchField(true);
        searchItem.setSearchFieldHint(getString(R.string.Search));
        searchItem.setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchExpand() {
                searchItem.getSearchField().setText(searchQuery);
                searchItem.getSearchField().setSelection(searchItem.getSearchField().length());
            }

            @Override
            public void onSearchCollapse() {
                searchQuery = "";
                updateCurrentPage();
            }

            @Override
            public void onTextChanged(android.widget.EditText editText) {
                String newQuery = editText.getText().toString();
                if (!TextUtils.equals(searchQuery, newQuery)) {
                    searchQuery = newQuery;
                    updateCurrentPage();
                }
            }

            @Override
            public void onSearchPressed(android.widget.EditText editText) {
                searchQuery = editText.getText().toString();
                updateCurrentPage();
            }
        });

        ActionBarMenuItem optionsItem = menu.addItem(OPTIONS_BUTTON, R.drawable.ic_ab_other);
        ActionBarMenuSubItem clearAllItem = optionsItem.addSubItem(CLEAR_ALL_BOOKMARKS, R.drawable.msg_delete, getString(R.string.ClearAllBookmarks));
        clearAllItem.setIconColor(Theme.getColor(Theme.key_text_RedRegular));
        clearAllItem.setTextColor(Theme.getColor(Theme.key_text_RedBold));
        clearAllItem.setSelectorColor(Theme.multAlpha(Theme.getColor(Theme.key_text_RedRegular), .12f));
        optionsItem.setOnClickListener(v -> optionsItem.toggleSubMenu());

        tabsContainer = new SearchTabsAndFiltersLayout(context);
        tabsContainer.setPadding(0, dp(7), 0, dp(7));

        tabsView = new ViewPagerFixed.TabsView(context, false, ViewPagerFixed.SELECTOR_TYPE_BUBBLE_STYLE, resourceProvider);
        tabsView.setIndicatorAnimation(320, CubicBezierInterpolator.EASE_OUT_QUINT);
        tabsView.tabMarginDp = (int) (FilterTabsView.TAB_PADDING_WIDTH / 2f);
        int tabsListPadding = Math.max(0, dp(23.5f - FilterTabsView.TAB_PADDING_WIDTH / 2f));
        tabsView.listView.setPadding(tabsListPadding, 0, tabsListPadding, 0);
        tabsContainer.addView(tabsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        tabsView.setDelegate(new ViewPagerFixed.TabsView.TabsViewDelegate() {
            @Override
            public void onPageSelected(int page, boolean forward) {
                if (viewPages == null || viewPages[0].tabId == page) {
                    return;
                }
                selectedTabId = page;
                viewPages[1].tabId = page;
                setPageTab(viewPages[1], page, true);
                viewPages[1].setVisibility(View.VISIBLE);
                animatingForward = forward;
                if (forward) {
                    viewPages[1].setTranslationX(viewPages[0].getMeasuredWidth());
                } else {
                    viewPages[1].setTranslationX(-viewPages[0].getMeasuredWidth());
                }
                updateSwipeBackEnabled();
                updateEmptyView();
                invalidateGestureExclusion();
                invalidateGlass();
            }

            @Override
            public void onPageScrolled(float progress) {
                if (viewPages == null) {
                    return;
                }
                if (progress == 1.0f && viewPages[1].getVisibility() != View.VISIBLE) {
                    return;
                }
                if (animatingForward) {
                    viewPages[0].setTranslationX(-progress * viewPages[0].getMeasuredWidth());
                    viewPages[1].setTranslationX(viewPages[0].getMeasuredWidth() - progress * viewPages[0].getMeasuredWidth());
                } else {
                    viewPages[0].setTranslationX(progress * viewPages[0].getMeasuredWidth());
                    viewPages[1].setTranslationX(progress * viewPages[0].getMeasuredWidth() - viewPages[0].getMeasuredWidth());
                }
                if (progress == 1.0f) {
                    ViewPage tempPage = viewPages[0];
                    viewPages[0] = viewPages[1];
                    viewPages[1] = tempPage;
                    viewPages[1].setVisibility(View.GONE);
                    updateSwipeBackEnabled();
                    updateEmptyView();
                    invalidateGestureExclusion();
                    updateBlur3Capture();
                }
                invalidateGlass();
            }
        });
        updateTabs();

        ViewConfiguration configuration = ViewConfiguration.get(context);
        maximumVelocity = configuration.getScaledMaximumFlingVelocity();
        contentLayout = new ContentLayout(context);
        contentLayout.setTag(0xFF112233, new Object());
        contentLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        tabsViewPositionWatcher = new ViewPositionWatcher(contentLayout);
        tabsBackgroundDrawableFactory.setSourceRootView(tabsViewPositionWatcher, contentLayout);
        actionBar.setDrawBlurBackground(contentLayout);
        fragmentView = contentLayout;

        viewPages = new ViewPage[2];
        for (int a = 0; a < viewPages.length; a++) {
            viewPages[a] = new ViewPage(context) {
                @Override
                public void setTranslationX(float translationX) {
                    super.setTranslationX(translationX);
                    if (tabsAnimationInProgress && viewPages[0] == this) {
                        float scrollProgress = Math.abs(viewPages[0].getTranslationX()) / (float) viewPages[0].getMeasuredWidth();
                        tabsView.selectTabWithId(viewPages[1].tabId, scrollProgress);
                    }
                    invalidateGlass();
                }
            };
            if (a == 1) {
                viewPages[a].setVisibility(View.GONE);
            }
            contentLayout.addView(viewPages[a], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }

        emptyView = new TextView(context);
        emptyView.setText(getString(R.string.NoBookmarks));
        emptyView.setTextColor(Theme.getColor(Theme.key_emptyListPlaceholder));
        emptyView.setTextSize(15);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(dp(24), dp(24), dp(24), dp(24));
        final int topContentOffset = getTopContentOffset();
        final int tabsHeight = dp(TABS_CONTAINER_HEIGHT_DP);
        contentLayout.addView(emptyView, LayoutHelper.createFrameMarginPx(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, topContentOffset + tabsHeight, 0, 0));
        contentLayout.addView(tabsContainer, LayoutHelper.createFrameMarginPx(LayoutHelper.MATCH_PARENT, TABS_CONTAINER_HEIGHT_DP, Gravity.TOP, dp(4), topContentOffset, dp(4), 0));
        updateTabsStyle();
        setPageTab(viewPages[0], selectedTabId, false);
        updateBlur3Capture();
        invalidateGlass();
        updateSwipeBackEnabled();
        updateEmptyView();
        invalidateGestureExclusion();

        return fragmentView;
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        return swipeBackEnabled;
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (!super.onBackPressed(invoked)) {
            return false;
        }
        if (tabsAnimationInProgress || startedTracking || maybeStartTracking || tabsView != null && tabsView.isAnimatingIndicator()) {
            return false;
        }
        if (selectedTabId != TAB_ALL) {
            scrollToTab();
            return false;
        }
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateTabsStyle();
        reloadData();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (tabsViewPositionWatcher != null) {
            tabsViewPositionWatcher.shutdown();
            tabsViewPositionWatcher = null;
        }
    }

    private void onClearAllBookmarksClicked() {
        Context ctx = getParentActivity();
        if (ctx == null) {
            return;
        }
        AlertUtil.showConfirm(ctx, getString(R.string.ClearAllBookmarks), null, R.drawable.msg_delete, getString(R.string.Clear), true, this::clearAllBookmarksConfirmed);
    }

    private void clearAllBookmarksConfirmed() {
        AlertDialog progressDialog = new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_SPINNER);
        progressDialog.setCanCancel(false);
        progressDialog.show();
        int accountId = getCurrentAccount();
        Utilities.globalQueue.postRunnable(() -> {
            BookmarksHelper.clearAllBookmarks(accountId);
            AndroidUtilities.runOnUIThread(() -> {
                progressDialog.dismiss();
                BulletinFactory.of(this).createSimpleBulletin(R.raw.done, getString(R.string.ClearAllBookmarksNotification)).show();
                reloadData();
            });
        });
    }

    private void reloadData() {
        final int accountId = getCurrentAccount();
        final int requestId = ++loadRequestId;
        Utilities.globalQueue.postRunnable(() -> {
            Map<Long, Integer> counts = BookmarksHelper.getBookmarkedDialogsCounts(accountId);
            MessagesController messagesController = MessagesController.getInstance(accountId);
            if (counts.isEmpty()) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (requestId != loadRequestId) {
                        return;
                    }
                    allItems.clear();
                    updateTabs();
                    updateCurrentPage();
                });
                return;
            }

            ArrayList<Long> baseUsersToLoad = new ArrayList<>();
            ArrayList<Long> chatsToLoad = new ArrayList<>();
            HashSet<Long> seenUserIds = new HashSet<>();
            HashSet<Long> seenChatIds = new HashSet<>();

            for (Map.Entry<Long, Integer> entry : counts.entrySet()) {
                long dialogId = entry.getKey() == null ? 0L : entry.getKey();
                int count = entry.getValue() == null ? 0 : entry.getValue();
                if (dialogId == 0 || count <= 0) {
                    continue;
                }
                collectPeersToLoad(messagesController, dialogId, baseUsersToLoad, chatsToLoad, seenUserIds, seenChatIds);
            }

            MessagesStorage messagesStorage = MessagesStorage.getInstance(accountId);
            messagesStorage.getStorageQueue().postRunnable(() -> {
                ArrayList<Long> usersToLoad = new ArrayList<>(baseUsersToLoad);
                ArrayList<TLRPC.User> loadedUsers = new ArrayList<>(usersToLoad.size());
                ArrayList<TLRPC.Chat> loadedChats = new ArrayList<>(chatsToLoad.size());
                try {
                    if (!usersToLoad.isEmpty()) {
                        messagesStorage.getUsersInternal(usersToLoad, loadedUsers);
                    }
                    if (!chatsToLoad.isEmpty()) {
                        messagesStorage.getChatsInternal(TextUtils.join(",", chatsToLoad), loadedChats);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }

                AndroidUtilities.runOnUIThread(() -> {
                    if (requestId != loadRequestId) {
                        return;
                    }
                    messagesController.putUsers(loadedUsers, true);
                    messagesController.putChats(loadedChats, true);
                    ArrayList<BookmarkChatItem> items = new ArrayList<>(counts.size());
                    for (Map.Entry<Long, Integer> entry : counts.entrySet()) {
                        long dialogId = entry.getKey() == null ? 0L : entry.getKey();
                        int count = entry.getValue() == null ? 0 : entry.getValue();
                        if (dialogId == 0 || count <= 0) {
                            continue;
                        }

                        TLObject peer = resolvePeer(messagesController, dialogId);
                        String title = resolveTitle(peer);
                        String username = resolveUsername(peer);
                        int category = resolveCategory(peer);
                        long sortDate = 0;
                        TLRPC.Dialog dialog = messagesController.dialogs_dict.get(dialogId);
                        if (dialog != null) {
                            sortDate = dialog.last_message_date;
                        }
                        CharSequence subtitle = buildSubtitle(username, category);
                        items.add(new BookmarkChatItem(dialogId, peer, title, username, subtitle, count, sortDate, category));
                    }

                    items.sort(Comparator.comparingLong((BookmarkChatItem i) -> i.sortDate).reversed().thenComparing(i -> i.title == null ? "" : i.title.toLowerCase(Locale.ROOT)));
                    allItems.clear();
                    allItems.addAll(items);
                    updateTabs();
                    updateCurrentPage();
                });
            });
        });
    }

    private void collectPeersToLoad(MessagesController messagesController, long dialogId, ArrayList<Long> usersToLoad, ArrayList<Long> chatsToLoad, HashSet<Long> seenUserIds, HashSet<Long> seenChatIds) {
        if (DialogObject.isUserDialog(dialogId)) {
            TLRPC.User user = messagesController.getUser(dialogId);
            if (shouldLoadUserFromStorage(user) && seenUserIds.add(dialogId)) {
                usersToLoad.add(dialogId);
            }
            return;
        }
        if (DialogObject.isChatDialog(dialogId)) {
            long chatId = -dialogId;
            TLRPC.Chat chat = messagesController.getChat(chatId);
            if (shouldLoadChatFromStorage(chat) && seenChatIds.add(chatId)) {
                chatsToLoad.add(chatId);
            }
        }
    }

    private TLObject resolvePeer(MessagesController messagesController, long dialogId) {
        if (DialogObject.isUserDialog(dialogId)) {
            return messagesController.getUser(dialogId);
        }
        if (DialogObject.isChatDialog(dialogId)) {
            long chatId = -dialogId;
            return messagesController.getChat(chatId);
        }
        return null;
    }

    private boolean shouldLoadUserFromStorage(TLRPC.User user) {
        return user == null || user.min;
    }

    private boolean shouldLoadChatFromStorage(TLRPC.Chat chat) {
        return chat == null || chat.min || TextUtils.isEmpty(chat.title);
    }

    @NonNull
    private String resolveTitle(TLObject peer) {
        if (peer instanceof TLRPC.Chat chat) {
            return chat.title != null ? chat.title : "";
        } else if (peer instanceof TLRPC.User user) {
            if (UserObject.isUserSelf(user)) {
                return getString(R.string.SavedMessages);
            }
            return UserObject.getUserName(user);
        } else {
            return getString(R.string.HiddenName);
        }
    }

    private String resolveUsername(TLObject peer) {
        if (peer instanceof TLRPC.Chat chat) {
            return TextUtils.isEmpty(chat.username) ? null : chat.username;
        } else if (peer instanceof TLRPC.User user) {
            return TextUtils.isEmpty(user.username) ? null : user.username;
        }
        return null;
    }

    private int resolveCategory(TLObject peer) {
        if (peer instanceof TLRPC.Chat chat) {
            return ChatObject.isChannelAndNotMegaGroup(chat) ? TAB_CHANNELS : TAB_GROUPS;
        } else if (peer instanceof TLRPC.User user) {
            return user.bot ? TAB_BOTS : TAB_USERS;
        }
        return TAB_ALL;
    }

    private CharSequence buildSubtitle(String username, int category) {
        String type;
        switch (category) {
            case TAB_CHANNELS -> type = getString(R.string.FilterChannels);
            case TAB_GROUPS -> type = getString(R.string.FilterGroups);
            case TAB_BOTS -> type = getString(R.string.FilterBots);
            case TAB_USERS -> type = getString(R.string.BookmarksFilterUsers);
            default -> type = "";
        }
        String uname = TextUtils.isEmpty(username) ? null : "@" + username;
        if (TextUtils.isEmpty(uname)) {
            return type;
        }
        if (TextUtils.isEmpty(type)) {
            return uname;
        }
        return uname + " · " + type;
    }

    private void updateTabs() {
        int all = allItems.size();
        int channels = 0;
        int groups = 0;
        int users = 0;
        int bots = 0;
        for (int i = 0; i < allItems.size(); i++) {
            int category = allItems.get(i).category;
            if (category == TAB_CHANNELS) {
                channels++;
            } else if (category == TAB_GROUPS) {
                groups++;
            } else if (category == TAB_USERS) {
                users++;
            } else if (category == TAB_BOTS) {
                bots++;
            }
        }

        if (tabsView == null) {
            return;
        }

        int current = selectedTabId;
        tabsView.removeTabs();
        tabsView.addTab(TAB_ALL, getString(R.string.FilterAllChatsShort) + " (" + all + ")");
        tabsView.addTab(TAB_CHANNELS, getString(R.string.FilterChannels) + " (" + channels + ")");
        tabsView.addTab(TAB_GROUPS, getString(R.string.FilterGroups) + " (" + groups + ")");
        tabsView.addTab(TAB_USERS, getString(R.string.BookmarksFilterUsers) + " (" + users + ")");
        tabsView.addTab(TAB_BOTS, getString(R.string.FilterBots) + " (" + bots + ")");
        tabsView.finishAddingTabs();
        tabsView.selectTabWithId(current, 1.0f);
    }

    private void updateTabsStyle() {
        if (actionBar != null) {
            actionBar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider));
        }
        if (contentLayout != null) {
            contentLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider));
        }
        tabsBackgroundSourceColor.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider));
        if (tabsView == null) {
            return;
        }
        tabsView.setColors(
                Theme.key_profile_tabSelectedLine,
                Theme.key_profile_tabSelectedText,
                Theme.key_profile_tabText,
                Theme.key_profile_tabSelector,
                Theme.key_actionBarDefault
        );
        tabsView.updateColors();
        tabsView.setBackground(null);
        if (tabsContainer != null) {
            if (tabsContainerBackground == null) {
                tabsContainerBackground = tabsBackgroundDrawableFactory.create(tabsContainer, BlurredBackgroundProviderImpl.topPanel(resourceProvider));
                tabsContainerBackground.setRadius(dp(18));
                tabsContainerBackground.setPadding(dp(6.666f));
                tabsContainer.setBlurredBackground(tabsContainerBackground);
            } else {
                tabsContainer.updateColors();
            }
        }
        invalidateGlass();
    }

    private void updateCurrentPage() {
        if (viewPages == null) {
            return;
        }
        setPageTab(viewPages[0], viewPages[0].tabId, false);
        if (viewPages[1].getVisibility() == View.VISIBLE) {
            setPageTab(viewPages[1], viewPages[1].tabId, false);
        }
        updateSwipeBackEnabled();
        updateEmptyView();
        invalidateGestureExclusion();
        invalidateGlass();
    }

    private void scrollToTab() {
        if (BookmarkManagerActivity.TAB_ALL == selectedTabId) {
            return;
        }
        if (tabsView == null || viewPages == null) {
            selectedTabId = BookmarkManagerActivity.TAB_ALL;
            if (viewPages != null) {
                setPageTab(viewPages[0], BookmarkManagerActivity.TAB_ALL, true);
            }
            updateSwipeBackEnabled();
            updateEmptyView();
            invalidateGestureExclusion();
            return;
        }

        tabsView.scrollToTab(BookmarkManagerActivity.TAB_ALL, BookmarkManagerActivity.TAB_ALL);
    }

    @SuppressLint("NotifyDataSetChanged")
    private void setPageTab(ViewPage page, int tabId, boolean scrollToTop) {
        if (page == null) {
            return;
        }
        page.tabId = tabId;

        String q = searchQuery == null ? "" : searchQuery.trim().toLowerCase(Locale.ROOT);
        page.items.clear();
        for (int i = 0; i < allItems.size(); i++) {
            BookmarkChatItem item = allItems.get(i);
            if (tabId != TAB_ALL && item.category != tabId) {
                continue;
            }
            if (!q.isEmpty() && notContainsIgnoreCase(item.title, q) && notContainsIgnoreCase(item.username, q)) {
                continue;
            }
            page.items.add(item);
        }
        page.adapter.notifyDataSetChanged();
        if (scrollToTop) {
            page.listView.scrollToPosition(0);
        }
    }

    private void updateEmptyView() {
        if (emptyView == null || viewPages == null) {
            return;
        }
        if (tabsAnimationInProgress || startedTracking || maybeStartTracking || tabsView != null && tabsView.isAnimatingIndicator()) {
            emptyView.setVisibility(View.GONE);
            return;
        }
        emptyView.setVisibility(viewPages[0].items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void updateSwipeBackEnabled() {
        swipeBackEnabled = !tabsAnimationInProgress && !startedTracking && !maybeStartTracking && (tabsView == null || !tabsView.isAnimatingIndicator()) && selectedTabId == TAB_ALL;
    }

    private void invalidateGestureExclusion() {
        if (contentLayout != null) {
            contentLayout.requestLayout();
        }
    }

    private boolean prepareForMoving(MotionEvent ev, boolean forward) {
        if (tabsView == null || viewPages == null || contentLayout == null) {
            return false;
        }
        int id = tabsView.getNextPageId(forward);
        if (id < 0) {
            return false;
        }
        ViewParent parent = contentLayout.getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(true);
        }
        maybeStartTracking = false;
        startedTracking = true;
        startedTrackingX = (int) (ev.getX() + additionalOffset);
        actionBar.setEnabled(false);
        tabsView.setEnabled(false);
        viewPages[1].tabId = id;
        setPageTab(viewPages[1], id, true);
        viewPages[1].setVisibility(View.VISIBLE);
        animatingForward = forward;
        if (forward) {
            viewPages[1].setTranslationX(viewPages[0].getMeasuredWidth());
        } else {
            viewPages[1].setTranslationX(-viewPages[0].getMeasuredWidth());
        }
        updateEmptyView();
        invalidateGestureExclusion();
        return true;
    }

    private boolean checkTabsAnimationInProgress() {
        if (!tabsAnimationInProgress) {
            return false;
        }
        boolean cancel = false;
        if (backAnimation) {
            if (Math.abs(viewPages[0].getTranslationX()) < 1) {
                viewPages[0].setTranslationX(0);
                viewPages[1].setTranslationX(viewPages[0].getMeasuredWidth() * (animatingForward ? 1 : -1));
                cancel = true;
            }
        } else if (Math.abs(viewPages[1].getTranslationX()) < 1) {
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

    private boolean notContainsIgnoreCase(String value, String queryLower) {
        if (TextUtils.isEmpty(value) || TextUtils.isEmpty(queryLower)) {
            return true;
        }
        return !value.toLowerCase(Locale.ROOT).contains(queryLower);
    }

    private record BookmarkChatItem(long dialogId, TLObject peer, String title, String username,
                                    CharSequence subtitle, int bookmarkCount, long sortDate,
                                    int category) {
    }

    private static class ListAdapter extends RecyclerListView.SelectionAdapter {
        private final Context context;
        private final ArrayList<BookmarkChatItem> items;

        public ListAdapter(Context context, ArrayList<BookmarkChatItem> items) {
            this.context = context;
            this.items = items;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new BookmarksChatCell(context));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (!(holder.itemView instanceof BookmarksChatCell cell)) {
                return;
            }
            if (position < 0 || position >= items.size()) {
                return;
            }
            BookmarkChatItem item = items.get(position);
            cell.setData(item.peer, item.title, item.subtitle, item.bookmarkCount, false);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private class ViewPage extends FrameLayout {
        public final RecyclerListView listView;
        public final ListAdapter adapter;
        public final IBlur3Capture blur3Capture;
        public final ArrayList<BookmarkChatItem> items = new ArrayList<>();
        public int tabId = TAB_ALL;

        public ViewPage(Context context) {
            super(context);

            listView = new RecyclerListView(context);
            listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
            listView.setVerticalScrollBarEnabled(true);
            listView.setSelectorType(2);
            listView.setScrollingTouchSlop(RecyclerView.TOUCH_SLOP_PAGING);
            listView.setClipToPadding(false);
            listView.setPadding(0, dp(TABS_CONTAINER_HEIGHT_DP) + getTopContentOffset(), 0, 0);
            listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && scrollableViewNoiseSuppressor != null) {
                        scrollableViewNoiseSuppressor.onScrolled(dx, dy);
                    }
                    invalidateGlass();
                }
            });
            listView.addEdgeEffectListener(BookmarkManagerActivity.this::invalidateGlass);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && scrollableViewNoiseSuppressor != null && contentLayout != null) {
                blur3Capture = new ViewGroupPartRenderer(listView, contentLayout, listView::drawChild);
            } else {
                blur3Capture = null;
            }

            adapter = new ListAdapter(context, items);
            listView.setAdapter(adapter);
            addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            listView.setOnItemClickListener((view, position) -> {
                if (position < 0 || position >= items.size()) {
                    return;
                }
                BookmarkChatItem item = items.get(position);
                presentFragment(new BookmarksActivity(item.dialogId));
            });
        }
    }

    private void invalidateGlass() {
        if (contentLayout != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && scrollableViewNoiseSuppressor != null) {
                blur3Invalidated = true;
                contentLayout.invalidate();
                actionBar.invalidate();
            } else {
                contentLayout.invalidateBlur();
            }
            if (tabsContainer != null) {
                tabsContainer.invalidate();
            }
        }
    }

    private boolean blur3_InvalidateBlur() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || scrollableViewNoiseSuppressor == null || blur3Capture == null || contentLayout == null || actionBar == null) {
            return false;
        }
        if (!SharedConfig.chatBlurEnabled() || !BlurredBackgroundProviderImpl.checkBlurEnabled(currentAccount, resourceProvider)) {
            return true;
        }

        final int width = contentLayout.getMeasuredWidth();
        final int height = contentLayout.getMeasuredHeight();
        final int actionBarHeight = actionBar.getMeasuredHeight();
        if (width <= 0 || height <= 0 || actionBarHeight <= 0) {
            return false;
        }

        final int additional = dp(48);
        final int topContentOffset = getTopContentOffset();
        final int tabsHeight = dp(TABS_CONTAINER_HEIGHT_DP);

        blur3PositionActionBar.set(0, -additional, width, actionBarHeight + additional);
        blur3PositionTabs.set(0, topContentOffset, width, topContentOffset + tabsHeight);
        if (!LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS)) {
            blur3PositionTabs.bottom += dp(48);
        }

        scrollableViewNoiseSuppressor.setupRenderNodes(blur3Positions, 2);
        final boolean hasChanges = scrollableViewNoiseSuppressor.invalidateResultRenderNodes(blur3Capture, width, height);
        if (hasChanges) {
            if (tabsBackgroundSourceFrosted != null) {
                tabsBackgroundSourceFrosted.invalidateDisplayListForDrawables();
            }
            if (tabsBackgroundSourceGlass != null) {
                tabsBackgroundSourceGlass.invalidateDisplayListForDrawables();
            }
        }
        return true;
    }

    private class ContentLayout extends SizeNotifierFrameLayout {
        public ContentLayout(Context context) {
            super(context);
        }

        @Override
        protected void dispatchDraw(@NonNull Canvas canvas) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && scrollableViewNoiseSuppressor != null && blur3Invalidated) {
                if (blur3_InvalidateBlur()) {
                    blur3Invalidated = false;
                }
            }
            super.dispatchDraw(canvas);
        }

        @Override
        public void drawBlurRect(Canvas canvas, float y, Rect rectTmp, Paint blurScrimPaint, boolean top) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !SharedConfig.chatBlurEnabled() || tabsBackgroundSourceFrosted == null || !BlurredBackgroundProviderImpl.checkBlurEnabled(currentAccount, resourceProvider)) {
                canvas.drawRect(rectTmp, blurScrimPaint);
                return;
            }

            final boolean isThemeLight = resourceProvider != null ? !resourceProvider.isDark() : !Theme.isCurrentThemeDark();
            final int blurAlpha = isThemeLight ? 216 : ACTION_BAR_BLUR_ALPHA;
            canvas.save();
            canvas.translate(0, -y);
            tabsBackgroundSourceFrosted.draw(canvas, rectTmp.left, rectTmp.top + y, rectTmp.right, rectTmp.bottom + y);
            canvas.restore();

            final int oldScrimAlpha = blurScrimPaint.getAlpha();
            blurScrimPaint.setAlpha(blurAlpha);
            canvas.drawRect(rectTmp, blurScrimPaint);
            blurScrimPaint.setAlpha(oldScrimAlpha);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            return checkTabsAnimationInProgress() || (tabsView != null && tabsView.isAnimatingIndicator()) || onTouchEvent(ev);
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            if (parentLayout != null && parentLayout.checkTransitionAnimation()) {
                return false;
            }
            if (ev != null) {
                if (velocityTracker == null) {
                    velocityTracker = VelocityTracker.obtain();
                }
                velocityTracker.addMovement(ev);
            }

            if (ev != null && ev.getAction() == MotionEvent.ACTION_DOWN && checkTabsAnimationInProgress()) {
                startedTracking = true;
                maybeStartTracking = false;
                startedTrackingPointerId = ev.getPointerId(0);
                startedTrackingX = (int) ev.getX();
                actionBar.setEnabled(false);
                tabsView.setEnabled(false);
                if (animatingForward) {
                    if (startedTrackingX < viewPages[0].getMeasuredWidth() + viewPages[0].getTranslationX()) {
                        additionalOffset = viewPages[0].getTranslationX();
                    } else {
                        ViewPage page = viewPages[0];
                        viewPages[0] = viewPages[1];
                        viewPages[1] = page;
                        animatingForward = false;
                        additionalOffset = viewPages[0].getTranslationX();
                        tabsView.selectTabWithId(viewPages[0].tabId, 1.0f);
                        tabsView.selectTabWithId(viewPages[1].tabId, additionalOffset / viewPages[0].getMeasuredWidth());
                    }
                } else {
                    if (startedTrackingX < viewPages[1].getMeasuredWidth() + viewPages[1].getTranslationX()) {
                        ViewPage page = viewPages[0];
                        viewPages[0] = viewPages[1];
                        viewPages[1] = page;
                        animatingForward = true;
                        additionalOffset = viewPages[0].getTranslationX();
                        tabsView.selectTabWithId(viewPages[0].tabId, 1.0f);
                        tabsView.selectTabWithId(viewPages[1].tabId, -additionalOffset / viewPages[0].getMeasuredWidth());
                    } else {
                        additionalOffset = viewPages[0].getTranslationX();
                    }
                }
                updateBlur3Capture();
                invalidateGlass();
                if (tabsAnimation != null) {
                    tabsAnimation.removeAllListeners();
                    tabsAnimation.cancel();
                    tabsAnimation = null;
                }
                tabsAnimationInProgress = false;
            } else if (ev != null && ev.getAction() == MotionEvent.ACTION_DOWN) {
                additionalOffset = 0;
            }

            if (ev != null && ev.getAction() == MotionEvent.ACTION_DOWN && !startedTracking && !maybeStartTracking) {
                startedTrackingPointerId = ev.getPointerId(0);
                maybeStartTracking = true;
                startedTrackingX = (int) ev.getX();
                startedTrackingY = (int) ev.getY();
                if (velocityTracker != null) {
                    velocityTracker.clear();
                }
            } else if (ev != null && ev.getAction() == MotionEvent.ACTION_MOVE && ev.getPointerId(0) == startedTrackingPointerId) {
                int dx = (int) (ev.getX() - startedTrackingX + additionalOffset);
                int dy = Math.abs((int) ev.getY() - startedTrackingY);

                if (startedTracking && (animatingForward && dx > 0 || !animatingForward && dx < 0)) {
                    if (!prepareForMoving(ev, dx < 0)) {
                        maybeStartTracking = true;
                        startedTracking = false;
                        viewPages[0].setTranslationX(0);
                        viewPages[1].setTranslationX(animatingForward ? viewPages[0].getMeasuredWidth() : -viewPages[0].getMeasuredWidth());
                        tabsView.selectTabWithId(viewPages[1].tabId, 0);
                    }
                }

                if (maybeStartTracking && !startedTracking) {
                    float touchSlop = AndroidUtilities.getPixelsInCM(0.3f, true);
                    int dxLocal = (int) (ev.getX() - startedTrackingX);
                    if (Math.abs(dxLocal) >= touchSlop && Math.abs(dxLocal) > dy) {
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
                    tabsView.selectTabWithId(viewPages[1].tabId, scrollProgress);
                }
            } else if (ev == null || ev.getPointerId(0) == startedTrackingPointerId && (ev.getAction() == MotionEvent.ACTION_CANCEL || ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_POINTER_UP)) {
                if (velocityTracker != null) {
                    velocityTracker.computeCurrentVelocity(1000, maximumVelocity);
                }
                float velX;
                float velY;
                if (ev != null && ev.getAction() != MotionEvent.ACTION_CANCEL && velocityTracker != null) {
                    velX = velocityTracker.getXVelocity();
                    velY = velocityTracker.getYVelocity();
                    if (!startedTracking) {
                        if (Math.abs(velX) >= 3000 && Math.abs(velX) > Math.abs(velY)) {
                            prepareForMoving(ev, velX < 0);
                        }
                    }
                } else {
                    velX = 0;
                    velY = 0;
                }

                if (startedTracking) {
                    float x = viewPages[0].getTranslationX();
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
                    tabsAnimation.setInterpolator(interpolator);

                    int width = getMeasuredWidth();
                    int halfWidth = width / 2;
                    float distanceRatio = Math.min(1.0f, dx / (float) width);
                    float distance = (float) halfWidth + (float) halfWidth * AndroidUtilities.distanceInfluenceForSnapDuration(distanceRatio);
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
                                tabsView.selectTabWithId(viewPages[0].tabId, 1.0f);
                            } else {
                                ViewPage tempPage = viewPages[0];
                                viewPages[0] = viewPages[1];
                                viewPages[1] = tempPage;
                                viewPages[1].setVisibility(View.GONE);
                                selectedTabId = viewPages[0].tabId;
                                tabsView.selectTabWithId(selectedTabId, 1.0f);
                            }
                            updateBlur3Capture();
                            invalidateGlass();
                            tabsAnimationInProgress = false;
                            maybeStartTracking = false;
                            startedTracking = false;
                            actionBar.setEnabled(true);
                            tabsView.setEnabled(true);
                            updateSwipeBackEnabled();
                            updateEmptyView();
                            invalidateGestureExclusion();
                        }
                    });
                    tabsAnimation.start();
                    tabsAnimationInProgress = true;
                    startedTracking = false;
                } else {
                    maybeStartTracking = false;
                    actionBar.setEnabled(true);
                    tabsView.setEnabled(true);
                }

                if (velocityTracker != null) {
                    velocityTracker.recycle();
                    velocityTracker = null;
                }
            }
            updateSwipeBackEnabled();
            return startedTracking;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            updateSystemGestureExclusionRects();
        }

        private void updateSystemGestureExclusionRects() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                return;
            }
            setSystemGestureExclusionRects(Collections.emptyList());
        }
    }

    @Override
    public int getNavigationBarColor() {
        return getThemedColor(Theme.key_windowBackgroundWhite);
    }
}
