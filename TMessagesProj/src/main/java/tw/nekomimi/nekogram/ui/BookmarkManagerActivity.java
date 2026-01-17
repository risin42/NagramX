package tw.nekomimi.nekogram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.util.SparseArray;
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
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
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
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScrollSlidingTextTabStrip;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
    private final ArrayList<BookmarkChatItem> allItems = new ArrayList<>();
    private final CubicBezierInterpolator interpolator = CubicBezierInterpolator.EASE_OUT_QUINT;
    private TextView emptyView;
    private ActionBarMenuItem searchItem;
    private ScrollSlidingTextTabStrip tabsView;
    private int selectedTabId = TAB_ALL;
    private String searchQuery = "";
    private int loadRequestId;
    private FrameLayout contentLayout;
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

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(getString(R.string.BookmarksManager));
        actionBar.setAllowOverlayTitle(false);
        actionBar.setExtraHeight(dp(44));
        actionBar.setClipContent(true);
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
        searchItem = menu.addItem(SEARCH_BUTTON, R.drawable.ic_ab_search).setIsSearchField(true);
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

        tabsView = new ScrollSlidingTextTabStrip(context);
        tabsView.setUseSameWidth(true);
        actionBar.addView(tabsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44, Gravity.LEFT | Gravity.BOTTOM));
        tabsView.setDelegate(new ScrollSlidingTextTabStrip.ScrollSlidingTabStripDelegate() {
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
                }
            }
        });
        updateTabs();

        ViewConfiguration configuration = ViewConfiguration.get(context);
        maximumVelocity = configuration.getScaledMaximumFlingVelocity();
        contentLayout = new ContentLayout(context);
        contentLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
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
        contentLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        setPageTab(viewPages[0], selectedTabId, false);
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
        reloadData();
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
            ArrayList<BookmarkChatItem> items = new ArrayList<>(counts.size());
            MessagesController messagesController = MessagesController.getInstance(accountId);

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

            AndroidUtilities.runOnUIThread(() -> {
                if (requestId != loadRequestId) {
                    return;
                }
                allItems.clear();
                allItems.addAll(items);
                updateTabs();
                updateCurrentPage();
            });
        });
    }

    private TLObject resolvePeer(MessagesController messagesController, long dialogId) {
        TLObject peer = messagesController.getUserOrChat(dialogId);
        if (peer != null) {
            return peer;
        }
        if (!DialogObject.isEncryptedDialog(dialogId)) {
            return null;
        }
        int encryptedId = DialogObject.getEncryptedChatId(dialogId);
        TLRPC.EncryptedChat encryptedChat = messagesController.getEncryptedChat(encryptedId);
        if (encryptedChat == null) {
            encryptedChat = messagesController.getEncryptedChatDB(encryptedId, false);
        }
        if (encryptedChat == null) {
            return null;
        }
        return messagesController.getUser(encryptedChat.user_id);
    }

    @NonNull
    private String resolveTitle(TLObject peer) {
        if (peer instanceof TLRPC.Chat chat) {
            return chat.title != null ? chat.title : "";
        } else if (peer instanceof TLRPC.User user) {
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
        SparseArray<View> tabsViewsCache = tabsView.removeTabs();

        tabsView.addTextTab(TAB_ALL, getString(R.string.FilterAllChatsShort) + " (" + all + ")", tabsViewsCache);
        tabsView.addTextTab(TAB_CHANNELS, getString(R.string.FilterChannels) + " (" + channels + ")", tabsViewsCache);
        tabsView.addTextTab(TAB_GROUPS, getString(R.string.FilterGroups) + " (" + groups + ")", tabsViewsCache);
        tabsView.addTextTab(TAB_USERS, getString(R.string.BookmarksFilterUsers) + " (" + users + ")", tabsViewsCache);
        tabsView.addTextTab(TAB_BOTS, getString(R.string.FilterBots) + " (" + bots + ")", tabsViewsCache);
        tabsView.finishAddingTabs();
        tabsView.setInitialTabId(current);
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

        ViewGroup tabsContainer = tabsView.getTabsContainer();
        ArrayList<Integer> ids = tabsView.getTabIds();
        int position = ids.indexOf(BookmarkManagerActivity.TAB_ALL);
        View tabView = tabsContainer != null && position >= 0 ? tabsContainer.getChildAt(position) : null;

        if (tabView != null) {
            tabsView.scrollTo(BookmarkManagerActivity.TAB_ALL, position, tabView);
        } else {
            selectedTabId = BookmarkManagerActivity.TAB_ALL;
            setPageTab(viewPages[0], BookmarkManagerActivity.TAB_ALL, true);
            tabsView.selectTabWithId(BookmarkManagerActivity.TAB_ALL, 1.0f, true);
            updateSwipeBackEnabled();
            updateEmptyView();
            invalidateGestureExclusion();
        }
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
            boolean divider = position != items.size() - 1;
            cell.setData(item.peer, item.title, item.subtitle, item.bookmarkCount, divider);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private class ViewPage extends FrameLayout {
        public final RecyclerListView listView;
        public final ListAdapter adapter;
        public final ArrayList<BookmarkChatItem> items = new ArrayList<>();
        public int tabId = TAB_ALL;

        public ViewPage(Context context) {
            super(context);

            listView = new RecyclerListView(context);
            listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
            listView.setVerticalScrollBarEnabled(true);
            listView.setSelectorType(2);
            listView.setScrollingTouchSlop(RecyclerView.TOUCH_SLOP_PAGING);

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

    private class ContentLayout extends FrameLayout {
        public ContentLayout(Context context) {
            super(context);
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
                        tabsView.selectTabWithId(viewPages[0].tabId, 1.0f, true);
                        tabsView.selectTabWithId(viewPages[1].tabId, additionalOffset / viewPages[0].getMeasuredWidth());
                    }
                } else {
                    if (startedTrackingX < viewPages[1].getMeasuredWidth() + viewPages[1].getTranslationX()) {
                        ViewPage page = viewPages[0];
                        viewPages[0] = viewPages[1];
                        viewPages[1] = page;
                        animatingForward = true;
                        additionalOffset = viewPages[0].getTranslationX();
                        tabsView.selectTabWithId(viewPages[0].tabId, 1.0f, true);
                        tabsView.selectTabWithId(viewPages[1].tabId, -additionalOffset / viewPages[0].getMeasuredWidth());
                    } else {
                        additionalOffset = viewPages[0].getTranslationX();
                    }
                }
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
                                tabsView.selectTabWithId(viewPages[0].tabId, 1.0f, true);
                            } else {
                                ViewPage tempPage = viewPages[0];
                                viewPages[0] = viewPages[1];
                                viewPages[1] = tempPage;
                                viewPages[1].setVisibility(View.GONE);
                                selectedTabId = viewPages[0].tabId;
                                tabsView.selectTabWithId(selectedTabId, 1.0f, true);
                            }
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
}
