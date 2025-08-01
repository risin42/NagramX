package tw.nekomimi.nekogram.helpers;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.ui.HiddenChatPinActivity;

public class HiddenChatManager {
    
    private static HiddenChatManager instance;
    private boolean isInHiddenMode = false;
    private Set<Long> hiddenChats = new HashSet<>();
    private Handler autoExitHandler;
    private Runnable autoExitRunnable;
    private long lastActivityTime = 0;
    
    public static HiddenChatManager getInstance() {
        if (instance == null) {
            instance = new HiddenChatManager();
        }
        return instance;
    }
    
    private HiddenChatManager() {
        autoExitHandler = new Handler(Looper.getMainLooper());
    }
    
    public boolean isHiddenChatEnabled() {
        return NekoConfig.hiddenChatEnabled.Bool() && !NekoConfig.hiddenChatPin.String().isEmpty();
    }
    
    public boolean isInHiddenMode() {
        return isInHiddenMode;
    }
    
    public void enterHiddenMode(Context context, Runnable onSuccess) {
        if (!isHiddenChatEnabled()) {
            Toast.makeText(context, LocaleController.getString("HiddenChatPinSetFirst", R.string.HiddenChatPinSetFirst), Toast.LENGTH_SHORT).show();
            return;
        }
        
        HiddenChatPinActivity pinActivity = new HiddenChatPinActivity(new HiddenChatPinActivity.PinCallback() {
            @Override
            public void onPinSuccess() {
                isInHiddenMode = true;
                startAutoExitTimer();
                updateLastActivity();
                
                // Show only hidden chats
                refreshDialogsList();
                
                if (onSuccess != null) {
                    onSuccess.run();
                }
                
                Toast.makeText(context, "Entered Hidden Chat Mode", Toast.LENGTH_SHORT).show();
            }
            
            @Override
            public void onPinFailure() {
                // PIN failed, do nothing
            }
            
            @Override
            public void onTimeout() {
                // PIN entry timed out
            }
        });
        
        if (LaunchActivity.instance != null) {
            LaunchActivity.instance.presentFragment(pinActivity);
        }
    }
    
    public void exitHiddenMode() {
        if (!isInHiddenMode) return;
        
        isInHiddenMode = false;
        stopAutoExitTimer();
        
        // Show all chats again
        refreshDialogsList();
        
        Toast.makeText(LaunchActivity.instance, "Exited Hidden Chat Mode", Toast.LENGTH_SHORT).show();
    }
    
    public void addChatToHidden(long dialogId) {
        hiddenChats.add(dialogId);
        if (isInHiddenMode) {
            refreshDialogsList();
        }
    }
    
    public void removeChatFromHidden(long dialogId) {
        hiddenChats.remove(dialogId);
        if (isInHiddenMode) {
            refreshDialogsList();
        }
    }
    
    public boolean isChatHidden(long dialogId) {
        return hiddenChats.contains(dialogId);
    }
    
    public Set<Long> getHiddenChats() {
        return new HashSet<>(hiddenChats);
    }
    
    public void updateLastActivity() {
        lastActivityTime = System.currentTimeMillis();
        
        if (isInHiddenMode) {
            // Restart auto exit timer
            stopAutoExitTimer();
            startAutoExitTimer();
        }
    }
    
    private void startAutoExitTimer() {
        if (!isInHiddenMode) return;
        
        stopAutoExitTimer();
        
        int autoExitSeconds = NekoConfig.hiddenChatAutoExitTime.Int();
        autoExitRunnable = () -> {
            if (isInHiddenMode && System.currentTimeMillis() - lastActivityTime >= autoExitSeconds * 1000) {
                Toast.makeText(LaunchActivity.instance, LocaleController.getString("HiddenChatExiting", R.string.HiddenChatExiting), Toast.LENGTH_SHORT).show();
                exitHiddenMode();
            }
        };
        
        autoExitHandler.postDelayed(autoExitRunnable, autoExitSeconds * 1000);
    }
    
    private void stopAutoExitTimer() {
        if (autoExitHandler != null && autoExitRunnable != null) {
            autoExitHandler.removeCallbacks(autoExitRunnable);
            autoExitRunnable = null;
        }
    }
    
    private void refreshDialogsList() {
        // Notify DialogsActivity to refresh the dialogs list
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
    }
    
    public boolean shouldShowDialog(TLRPC.Dialog dialog) {
        if (!isHiddenChatEnabled()) {
            return true; // If hidden chat is disabled, show all dialogs
        }
        
        if (isInHiddenMode) {
            // In hidden mode, show only hidden chats
            return isChatHidden(dialog.id);
        } else {
            // In normal mode, show all chats except hidden ones
            return !isChatHidden(dialog.id);
        }
    }
    
    public void toggleChatHidden(long dialogId) {
        if (isChatHidden(dialogId)) {
            removeChatFromHidden(dialogId);
        } else {
            addChatToHidden(dialogId);
        }
    }
    
    // Called when user interacts with the app
    public void onUserActivity() {
        if (isInHiddenMode) {
            updateLastActivity();
        }
    }
    
    // Save/load hidden chats from preferences
    public void saveHiddenChats() {
        StringBuilder sb = new StringBuilder();
        for (Long chatId : hiddenChats) {
            if (sb.length() > 0) sb.append(",");
            sb.append(chatId);
        }
        NekoConfig.preferences.edit().putString("hidden_chats", sb.toString()).apply();
    }
    
    public void loadHiddenChats() {
        String savedChats = NekoConfig.preferences.getString("hidden_chats", "");
        hiddenChats.clear();
        
        if (!savedChats.isEmpty()) {
            String[] chatIds = savedChats.split(",");
            for (String chatId : chatIds) {
                try {
                    hiddenChats.add(Long.parseLong(chatId.trim()));
                } catch (NumberFormatException e) {
                    // Ignore invalid chat IDs
                }
            }
        }
    }
}