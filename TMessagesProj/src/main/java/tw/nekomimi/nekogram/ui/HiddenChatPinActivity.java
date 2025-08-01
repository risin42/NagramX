package tw.nekomimi.nekogram.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import android.widget.Toast;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

import tw.nekomimi.nekogram.NekoConfig;

public class HiddenChatPinActivity extends BaseFragment {

    private EditTextBoldCursor pinEditText;
    private LinearLayout pinLayout;
    private Handler timeoutHandler;
    private Runnable timeoutRunnable;
    
    public interface PinCallback {
        void onPinSuccess();
        void onPinFailure();
        void onTimeout();
    }
    
    private PinCallback callback;
    
    public HiddenChatPinActivity(PinCallback callback) {
        this.callback = callback;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString("EnterHiddenChatPin", R.string.EnterHiddenChatPin));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                    if (callback != null) {
                        callback.onTimeout();
                    }
                }
            }
        });

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        pinLayout = new LinearLayout(context);
        pinLayout.setOrientation(LinearLayout.VERTICAL);
        pinLayout.setGravity(Gravity.CENTER);
        frameLayout.addView(pinLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        pinEditText = new EditTextBoldCursor(context);
        pinEditText.setTextSize(18);
        pinEditText.setHint(LocaleController.getString("HiddenChatPinPrompt", R.string.HiddenChatPinPrompt));
        pinEditText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinEditText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        pinEditText.setBackgroundDrawable(Theme.createEditTextDrawable(context, false));
        pinEditText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        pinEditText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        pinEditText.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        pinEditText.setCursorSize(AndroidUtilities.dp(20));
        pinEditText.setCursorWidth(1.5f);
        pinEditText.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
        pinEditText.setGravity(Gravity.CENTER);
        
        pinEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() == 4) {
                    checkPin(s.toString());
                }
            }
        });
        
        pinEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                String pin = pinEditText.getText().toString();
                if (pin.length() == 4) {
                    checkPin(pin);
                }
                return true;
            }
            return false;
        });

        pinLayout.addView(pinEditText, LayoutHelper.createLinear(280, 50, Gravity.CENTER_HORIZONTAL));

        // Start timeout timer (15 seconds)
        startTimeoutTimer();

        return fragmentView;
    }

    private void checkPin(String enteredPin) {
        String savedPin = NekoConfig.hiddenChatPin.String();
        
        if (savedPin.isEmpty()) {
            // No PIN set
            Toast.makeText(ApplicationLoader.applicationContext, LocaleController.getString("HiddenChatPinSetFirst", R.string.HiddenChatPinSetFirst), Toast.LENGTH_SHORT).show();
            finishFragment();
            if (callback != null) {
                callback.onPinFailure();
            }
            return;
        }
        
        if (enteredPin.equals(savedPin)) {
            // Correct PIN
            cancelTimeoutTimer();
            finishFragment();
            if (callback != null) {
                callback.onPinSuccess();
            }
        } else {
            // Incorrect PIN
            pinEditText.setText("");
            Toast.makeText(ApplicationLoader.applicationContext, LocaleController.getString("HiddenChatPinIncorrect", R.string.HiddenChatPinIncorrect), Toast.LENGTH_SHORT).show();
            AndroidUtilities.shakeView(pinEditText);
        }
    }

    private void startTimeoutTimer() {
        timeoutHandler = new Handler(Looper.getMainLooper());
        timeoutRunnable = () -> {
            finishFragment();
            if (callback != null) {
                callback.onTimeout();
            }
        };
        timeoutHandler.postDelayed(timeoutRunnable, 30000); // 30 seconds timeout for PIN entry
    }

    private void cancelTimeoutTimer() {
        if (timeoutHandler != null && timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (pinEditText != null) {
            pinEditText.requestFocus();
            AndroidUtilities.showKeyboard(pinEditText);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        AndroidUtilities.hideKeyboard(pinEditText);
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        cancelTimeoutTimer();
    }
}