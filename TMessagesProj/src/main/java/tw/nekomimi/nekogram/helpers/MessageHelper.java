package tw.nekomimi.nekogram.helpers;

import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.messenger.TranslateController.UNKNOWN_LANGUAGE;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.DatePicker;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BaseController;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LanguageDetector;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnimatedFileDrawable;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.Forum.ForumUtilities;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import xyz.nextalone.nagram.NaConfig;

public class MessageHelper extends BaseController {

    private static final MessageHelper[] Instance = new MessageHelper[UserConfig.MAX_ACCOUNT_COUNT];
    private static final CharsetDecoder utf8Decoder = StandardCharsets.UTF_8.newDecoder();

    public MessageHelper(int num) {
        super(num);
    }

    public static MessageHelper getInstance(int num) {
        MessageHelper localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (MessageHelper.class) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new MessageHelper(num);
                }
            }
        }
        return localInstance;
    }

    public static String getPathToMessage(MessageObject messageObject) {
        String path = messageObject.messageOwner.attachPath;
        if (!TextUtils.isEmpty(path)) {
            File f = new File(path);
            if (!f.exists()) {
                path = null;
            }
        }
        if (TextUtils.isEmpty(path)) {
            path = FileLoader.getInstance(UserConfig.selectedAccount).getPathToMessage(messageObject.messageOwner).toString();
            File f = new File(path);
            if (!f.exists() || f.getAbsolutePath().endsWith("/cache")) {
                path = null;
            }
        }
        if (TextUtils.isEmpty(path)) {
            File f = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(messageObject.getDocument(), true);
            if (f.exists() && !f.getAbsolutePath().endsWith("/cache")) {
                path = f.getAbsolutePath();
            } else {
                f = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(messageObject.getDocument());
                if (f.exists()) {
                    path = f.getAbsolutePath();
                }
            }
        }
        return path != null && !path.endsWith("/cache") ? path : null;
    }

    public void resetMessageContent(long dialog_id, MessageObject messageObject) {
        TLRPC.Message message = messageObject.messageOwner;

        MessageObject obj = new MessageObject(currentAccount, message, true, true);

        ArrayList<MessageObject> arrayList = new ArrayList<>();
        arrayList.add(obj);
        getNotificationCenter().postNotificationName(NotificationCenter.replaceMessagesObjects, dialog_id, arrayList, false, true);
    }

    public void resetMessageContent(long dialog_id, ArrayList<MessageObject> messageObjects) {
        ArrayList<MessageObject> arrayList = new ArrayList<>();
        for (MessageObject messageObject : messageObjects) {
            MessageObject obj = new MessageObject(currentAccount, messageObject.messageOwner, true, true);
            arrayList.add(obj);
        }
        getNotificationCenter().postNotificationName(NotificationCenter.replaceMessagesObjects, dialog_id, arrayList, false);
    }

    public void saveStickerToGallery(Context context, MessageObject messageObject) {
        if (messageObject.isAnimatedSticker()) return;
        // Animated Sticker is not supported.

        String path = messageObject.messageOwner.attachPath;
        if (!TextUtils.isEmpty(path)) {
            File temp = new File(path);
            if (!temp.exists()) {
                path = null;
            }
        }
        if (TextUtils.isEmpty(path)) {
            path = FileLoader.getInstance(currentAccount).getPathToMessage(messageObject.messageOwner).toString();
            File temp = new File(path);
            if (!temp.exists()) {
                path = null;
            }
        }
        if (TextUtils.isEmpty(path)) {
            path = FileLoader.getInstance(currentAccount).getPathToAttach(messageObject.getDocument(), true).toString();
        }
        if (!TextUtils.isEmpty(path)) {
            if (messageObject.isVideoSticker()) {
                MediaController.saveFile(path, context, 1, null, null);
            } else {
                try {
                    Bitmap image = BitmapFactory.decodeFile(path);
                    FileOutputStream stream = new FileOutputStream(path + ".png");
                    image.compress(Bitmap.CompressFormat.PNG, 100, stream);
                    stream.close();
                    MediaController.saveFile(path + ".png", context, 0, null, null);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
    }

    public void saveStickerToGallery(Context context, TLRPC.Document document) {
        String path = FileLoader.getInstance(currentAccount).getPathToAttach(document, true).toString();

        if (!TextUtils.isEmpty(path)) {
            if (MessageObject.isVideoSticker(document)) {
                MediaController.saveFile(path, context, 1, null, document.mime_type);
            } else {
                try {
                    Bitmap image = BitmapFactory.decodeFile(path);
                    FileOutputStream stream = new FileOutputStream(path + ".png");
                    image.compress(Bitmap.CompressFormat.PNG, 100, stream);
                    stream.close();
                    MediaController.saveFile(path + ".png", context, 0, null, null);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
    }

    public void addStickerToClipboard(TLRPC.Document document, Runnable callback) {
        String path = FileLoader.getInstance(currentAccount).getPathToAttach(document, true).toString();

        if (TextUtils.isEmpty(path)) {
            return;
        }
        if (MessageObject.isVideoSticker(document)) {
            return;
        }
        File file = new File(path);
        if (file.exists()) {
            addFileToClipboard(file, callback);
        }
    }

    public MessageObject getMessageForRepeat(MessageObject selectedObject, MessageObject.GroupedMessages selectedObjectGroup) {
        MessageObject messageObject = null;
        if (selectedObjectGroup != null && !selectedObjectGroup.isDocuments) {
            messageObject = getTargetMessageObjectFromGroup(selectedObjectGroup);
        } else if (!TextUtils.isEmpty(selectedObject.messageOwner.message) || selectedObject.isAnyKindOfSticker()) {
            messageObject = selectedObject;
        }
        return messageObject;
    }

    private MessageObject getTargetMessageObjectFromGroup(MessageObject.GroupedMessages selectedObjectGroup) {
        MessageObject messageObject = null;
        for (MessageObject object : selectedObjectGroup.messages) {
            if (!TextUtils.isEmpty(object.messageOwner.message)) {
                if (messageObject != null) {
                    messageObject = null;
                    break;
                } else {
                    messageObject = object;
                }
            }
        }
        return messageObject;
    }

    public void createDeleteHistoryAlert(BaseFragment fragment, TLRPC.Chat chat, TLRPC.TL_forumTopic forumTopic, long mergeDialogId, Theme.ResourcesProvider resourcesProvider) {
        createDeleteHistoryAlert(fragment, chat, forumTopic, mergeDialogId, -1, resourcesProvider);
    }

    private void createDeleteHistoryAlert(BaseFragment fragment, TLRPC.Chat chat, TLRPC.TL_forumTopic forumTopic, long mergeDialogId, int before, Theme.ResourcesProvider resourcesProvider) {
        if (fragment == null || fragment.getParentActivity() == null || chat == null) {
            return;
        }

        Context context = fragment.getParentActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);

        CheckBoxCell cell = before == -1 && forumTopic == null && ChatObject.isChannel(chat) && ChatObject.canUserDoAction(chat, ChatObject.ACTION_DELETE_MESSAGES) ? new CheckBoxCell(context, 1, resourcesProvider) : null;

        TextView messageTextView = new TextView(context);
        messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);

        FrameLayout frameLayout = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                if (cell != null) {
                    setMeasuredDimension(getMeasuredWidth(), getMeasuredHeight() + cell.getMeasuredHeight() + AndroidUtilities.dp(7));
                }
            }
        };
        builder.setView(frameLayout);

        AvatarDrawable avatarDrawable = new AvatarDrawable();
        avatarDrawable.setTextSize(AndroidUtilities.dp(12));
        avatarDrawable.setInfo(chat);

        BackupImageView imageView = new BackupImageView(context);
        imageView.setRoundRadius(AndroidUtilities.dp(20));
        if (forumTopic != null) {
            if (forumTopic.id == 1) {
                imageView.setImageDrawable(ForumUtilities.createGeneralTopicDrawable(context, 0.75f, Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider), false));
            } else {
                ForumUtilities.setTopicIcon(imageView, forumTopic, false, true, resourcesProvider);
            }
        } else {
            imageView.setForUserOrChat(chat, avatarDrawable);
        }
        frameLayout.addView(imageView, LayoutHelper.createFrame(40, 40, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 22, 5, 22, 0));

        TextView textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        textView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setText(getString(R.string.DeleteAllFromSelf));

        frameLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 21 : 76), 11, (LocaleController.isRTL ? 76 : 21), 0));
        frameLayout.addView(messageTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 24, 57, 24, 9));

        if (cell != null) {
            boolean sendAs = ChatObject.getSendAsPeerId(chat, getMessagesController().getChatFull(chat.id), true) != getUserConfig().getClientUserId();
            cell.setBackground(Theme.getSelectorDrawable(false));
            cell.setText(getString(R.string.DeleteAllFromSelfAdmin), "", !ChatObject.shouldSendAnonymously(chat) && !sendAs, false);
            cell.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(16) : AndroidUtilities.dp(8), 0, LocaleController.isRTL ? AndroidUtilities.dp(8) : AndroidUtilities.dp(16), 0);
            frameLayout.addView(cell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.LEFT, 0, 0, 0, 0));
            cell.setOnClickListener(v -> {
                CheckBoxCell cell1 = (CheckBoxCell) v;
                cell1.setChecked(!cell1.isChecked(), true);
            });
        }

        if (before > 0) {
            messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.DeleteAllFromSelfAlertBefore, LocaleController.formatDateForBan(before))));
        } else {
            messageTextView.setText(AndroidUtilities.replaceTags(getString(R.string.DeleteAllFromSelfAlert)));
        }

        builder.setNeutralButton(getString(R.string.DeleteAllFromSelfBefore), (dialog, which) -> showBeforeDatePickerAlert(fragment, before1 -> createDeleteHistoryAlert(fragment, chat, forumTopic, mergeDialogId, before1, resourcesProvider)));
        builder.setPositiveButton(getString(R.string.DeleteAll), (dialogInterface, i) -> {
            if (cell != null && cell.isChecked()) {
                showDeleteHistoryBulletin(fragment, 0, false, () -> getMessagesController().deleteUserChannelHistory(chat, getUserConfig().getCurrentUser(), null, 0), resourcesProvider);
            } else {
                deleteUserHistoryWithSearch(fragment, -chat.id, forumTopic != null ? forumTopic.id : 0, mergeDialogId, before == -1 ? getConnectionsManager().getCurrentTime() : before, (count, deleteAction) -> showDeleteHistoryBulletin(fragment, count, true, deleteAction, resourcesProvider));
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        AlertDialog alertDialog = builder.create();
        fragment.showDialog(alertDialog);
        TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_text_RedBold, resourcesProvider));
        }
    }

    private void showBeforeDatePickerAlert(BaseFragment fragment, Utilities.Callback<Integer> callback) {
        Context context = fragment.getParentActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getString(R.string.DeleteAllFromSelfBefore));
        builder.setItems(new CharSequence[]{
                LocaleController.formatPluralString("Days", 1),
                LocaleController.formatPluralString("Weeks", 1),
                LocaleController.formatPluralString("Months", 1),
                getString(R.string.UserRestrictionsCustom)
        }, (dialog1, which) -> {
            switch (which) {
                case 0:
                    callback.run(getConnectionsManager().getCurrentTime() - 60 * 60 * 24);
                    break;
                case 1:
                    callback.run(getConnectionsManager().getCurrentTime() - 60 * 60 * 24 * 7);
                    break;
                case 2:
                    callback.run(getConnectionsManager().getCurrentTime() - 60 * 60 * 24 * 30);
                    break;
                case 3: {
                    DatePickerDialog dateDialog = getDatePickerDialog(fragment, callback, context);

                    final DatePicker datePicker = dateDialog.getDatePicker();

                    datePicker.setMinDate(1375315200000L);
                    datePicker.setMaxDate(System.currentTimeMillis());

                    dateDialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(R.string.Set), dateDialog);
                    dateDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.Cancel), (dialog2, which2) -> {
                    });
                    dateDialog.setOnShowListener(dialog12 -> {
                        int count = datePicker.getChildCount();
                        for (int b = 0; b < count; b++) {
                            View child = datePicker.getChildAt(b);
                            ViewGroup.LayoutParams layoutParams = child.getLayoutParams();
                            layoutParams.width = LayoutHelper.MATCH_PARENT;
                            child.setLayoutParams(layoutParams);
                        }
                    });
                    fragment.showDialog(dateDialog);
                    break;
                }
            }
            builder.getDismissRunnable().run();
        });
        fragment.showDialog(builder.create());
    }

    @NonNull
    private static DatePickerDialog getDatePickerDialog(BaseFragment fragment, Utilities.Callback<Integer> callback, Context context) {
        Calendar calendar = Calendar.getInstance();
        return new DatePickerDialog(context, (view1, year1, month, dayOfMonth1) -> {
            TimePickerDialog timeDialog = new TimePickerDialog(context, (view11, hourOfDay, minute) -> {
                calendar.set(year1, month, dayOfMonth1, hourOfDay, minute);
                callback.run((int) (calendar.getTimeInMillis() / 1000));
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true);
            timeDialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(R.string.Set), timeDialog);
            timeDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.Cancel), (dialog3, which3) -> {
            });
            fragment.showDialog(timeDialog);
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
    }

    public static void showDeleteHistoryBulletin(BaseFragment fragment, int count, boolean search, Runnable delayedAction, Theme.ResourcesProvider resourcesProvider) {
        if (fragment.getParentActivity() == null) {
            if (delayedAction != null) {
                delayedAction.run();
            }
            return;
        }
        Bulletin.ButtonLayout buttonLayout;
        if (search) {
            final Bulletin.TwoLineLottieLayout layout = new Bulletin.TwoLineLottieLayout(fragment.getParentActivity(), resourcesProvider);
            layout.titleTextView.setText(getString(R.string.DeleteAllFromSelfDone));
            layout.subtitleTextView.setText(LocaleController.formatPluralString("MessagesDeletedHint", count));
            layout.setTimer();
            buttonLayout = layout;
        } else {
            final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(fragment.getParentActivity(), resourcesProvider);
            layout.textView.setText(getString(R.string.DeleteAllFromSelfDone));
            layout.setTimer();
            buttonLayout = layout;
        }
        buttonLayout.setButton(new Bulletin.UndoButton(fragment.getParentActivity(), true, resourcesProvider).setDelayedAction(delayedAction));
        Bulletin.make(fragment, buttonLayout, Bulletin.DURATION_PROLONG).show();
    }

    private void deleteUserHistoryWithSearch(BaseFragment fragment, final long dialogId, int replyMessageId, final long mergeDialogId, int before, SearchMessagesResultCallback callback) {
        Utilities.globalQueue.postRunnable(() -> {
            ArrayList<Integer> messageIds = new ArrayList<>();
            var latch = new CountDownLatch(1);
            var peer = getMessagesController().getInputPeer(dialogId);
            var fromId = MessagesController.getInputPeer(getUserConfig().getCurrentUser());
            doSearchMessages(fragment, latch, messageIds, peer, replyMessageId, fromId, before, Integer.MAX_VALUE, 0);
            try {
                latch.await();
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (!messageIds.isEmpty()) {
                ArrayList<ArrayList<Integer>> lists = new ArrayList<>();
                final int N = messageIds.size();
                for (int i = 0; i < N; i += 100) {
                    lists.add(new ArrayList<>(messageIds.subList(i, Math.min(N, i + 100))));
                }
                Runnable deleteAction = () -> {
                    for (ArrayList<Integer> list : lists) {
                        getMessagesController().deleteMessages(list, null, null, dialogId, 0, true, 0);
                    }
                };
                AndroidUtilities.runOnUIThread(callback != null ? () -> callback.run(messageIds.size(), deleteAction) : deleteAction);
            }
            if (mergeDialogId != 0) {
                deleteUserHistoryWithSearch(fragment, mergeDialogId, 0, 0, before, null);
            }
        });
    }

    private interface SearchMessagesResultCallback {
        void run(int count, Runnable deleteAction);
    }

    private void doSearchMessages(BaseFragment fragment, CountDownLatch latch, ArrayList<Integer> messageIds, TLRPC.InputPeer peer, int replyMessageId, TLRPC.InputPeer fromId, int before, int offsetId, long hash) {
        var req = new TLRPC.TL_messages_search();
        req.peer = peer;
        req.limit = 100;
        req.q = "";
        req.offset_id = offsetId;
        req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
        long dialogId = DialogObject.getPeerDialogId(peer);
        boolean isMonoForum = getMessagesStorage().isMonoForum(dialogId);
        if (!isMonoForum) {
            req.from_id = fromId;
            req.flags |= 1;
        }
        if (replyMessageId != 0) {
            if (isMonoForum) {
                req.saved_peer_id = getMessagesController().getInputPeer(replyMessageId);
                req.flags |= 4;
            } else {
                req.top_msg_id = replyMessageId;
                req.flags |= 2;
            }
        }
        req.hash = hash;
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (response instanceof TLRPC.messages_Messages res) {
                if (response instanceof TLRPC.TL_messages_messagesNotModified || res.messages.isEmpty()) {
                    latch.countDown();
                    return;
                }
                var newOffsetId = offsetId;
                for (TLRPC.Message message : res.messages) {
                    newOffsetId = Math.min(newOffsetId, message.id);
                    if (!message.out || message.post || message.date >= before) {
                        continue;
                    }
                    messageIds.add(message.id);
                }
                doSearchMessages(fragment, latch, messageIds, peer, replyMessageId, fromId, before, newOffsetId, calcMessagesHash(res.messages));
            } else {
                if (error != null) {
                    AndroidUtilities.runOnUIThread(() -> AlertsCreator.showSimpleAlert(fragment, getString(R.string.ErrorOccurred) + "\n" + error.text));
                }
                latch.countDown();
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    private long calcMessagesHash(ArrayList<TLRPC.Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        long acc = 0;
        for (TLRPC.Message message : messages) {
            acc = MediaDataController.calcHash(acc, message.id);
        }
        return acc;
    }

    public static String getTextOrBase64(byte[] data) {
        try {
            return utf8Decoder.decode(ByteBuffer.wrap(data)).toString();
        } catch (CharacterCodingException e) {
            return Base64.encodeToString(data, Base64.NO_PADDING | Base64.NO_WRAP);
        }
    }

    public void clearMessageFiles(MessageObject messageObject, Runnable done) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                var files = getFilesToMessage(messageObject);
                for (File file : files) {
                    if (file.exists() && !file.delete()) {
                        file.deleteOnExit();
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            messageObject.checkMediaExistance();
            AndroidUtilities.runOnUIThread(done);
        });
    }

    public ArrayList<File> getFilesToMessage(MessageObject messageObject) {
        ArrayList<File> files = new ArrayList<>();
        files.add(new File(messageObject.messageOwner.attachPath));
        files.add(getFileLoader().getPathToMessage(messageObject.messageOwner));
        var document = messageObject.getDocument();
        if (document != null) {
            files.add(getFileLoader().getPathToAttach(document, false));
            files.add(getFileLoader().getPathToAttach(document, true));
        }
        var media = messageObject.messageOwner.media;
        if (media != null && !media.alt_documents.isEmpty()) {
            media.alt_documents.forEach(doc -> {
                files.add(getFileLoader().getPathToAttach(doc, false));
                files.add(getFileLoader().getPathToAttach(doc, true));
            });
        }
        return files;
    }

    public static boolean shouldSkipTranslation(String message) {
        if (TextUtils.isEmpty(message)) {
            return true;
        }
        final int len = message.length();
        int wordStart = 0;
        for (int i = 0; i <= len; i++) {
            if (i == len || Character.isWhitespace(message.charAt(i))) {
                if (wordStart < i) {
                    if (!isSkippedWord(message, wordStart, i)) {
                        return false;
                    }
                }
                wordStart = i + 1;
            }
        }
        return true;
    }

    private static boolean isSkippedWord(String message, int start, int end) {
        int wordLen = end - start;
        if (wordLen == 0) return true;
        char firstChar = message.charAt(start);
        return switch (firstChar) {
            case '@', '#', '/' -> true;
            case 'h' -> {
                if (wordLen >= 8) {
                    yield message.startsWith("http://", start) || message.startsWith("https://", start);
                } else if (wordLen == 7) {
                    yield message.startsWith("http://", start);
                }
                yield false;
            }
            case 'f' -> {
                if (wordLen >= 6) {
                    yield message.startsWith("ftp://", start);
                }
                yield false;
            }
            default -> false;
        };
    }

    public void detectLanguageNow(MessageObject messageObject) {
        final long dialogId = messageObject.getDialogId();
        LanguageDetector.detectLanguage(messageObject.messageOwner.message, lng -> AndroidUtilities.runOnUIThread(() -> {
            String detectedLanguage = lng;
            if (detectedLanguage == null) {
                detectedLanguage = UNKNOWN_LANGUAGE;
            }
            messageObject.messageOwner.originalLanguage = detectedLanguage;
            getMessagesStorage().updateMessageCustomParams(dialogId, messageObject.messageOwner);
        }), err -> AndroidUtilities.runOnUIThread(() -> {
            messageObject.messageOwner.originalLanguage = UNKNOWN_LANGUAGE;
            getMessagesStorage().updateMessageCustomParams(dialogId, messageObject.messageOwner);
        }));
    }

    public static String getMessagePlainText(MessageObject messageObject, MessageObject.GroupedMessages messageGroup) {
        if (messageGroup != null) {
            MessageObject captionMessage = messageGroup.findCaptionMessageObject();
            if (captionMessage != null && !TextUtils.isEmpty(captionMessage.caption)) {
                return captionMessage.caption.toString();
            }
        }
        if (messageObject == null) {
            return null;
        }
        if (messageObject.isPoll()) {
            TLRPC.Poll poll = ((TLRPC.TL_messageMediaPoll) messageObject.messageOwner.media).poll;
            StringBuilder pollText = new StringBuilder(poll.question.text).append("\n");
            for (TLRPC.PollAnswer answer : poll.answers) {
                pollText.append("\n\uD83D\uDD18 ");
                pollText.append(answer.text.text);
            }
            return pollText.toString();
        } else if (!TextUtils.isEmpty(messageObject.getVoiceTranscription())) {
            return messageObject.messageOwner.voiceTranscription;
        }
        return messageObject.messageOwner.message;
    }

    public static CharSequence getMessagePlainTextFull(MessageObject messageObject, MessageObject.GroupedMessages messageGroup) {
        StringBuilder text = new StringBuilder();
        if (messageGroup != null) {
            for (var groupedMessage : messageGroup.messages) {
                text.append(getMessagePlainText(groupedMessage, null));
            }
        }
        if (messageObject != null && messageObject.messageOwner != null) {
            if (messageObject.isPoll()) {
                TLRPC.Poll poll = ((TLRPC.TL_messageMediaPoll) messageObject.messageOwner.media).poll;
                StringBuilder pollText = new StringBuilder(poll.question.text).append("\n");
                for (TLRPC.PollAnswer answer : poll.answers) {
                    pollText.append("\n\uD83D\uDD18 ");
                    pollText.append(answer.text.text);
                }
                text.append(pollText);
            } else if (!TextUtils.isEmpty(messageObject.getVoiceTranscription())) {
                text.append(messageObject.messageOwner.voiceTranscription);
            } else {
                text.append(messageObject.messageOwner.message);
            }
        }
        return text.toString();
    }

    public static boolean messageObjectIsFile(int type, MessageObject messageObject) {
        boolean canSave = (type == 4 || type == 5 || type == 6 || type == 10);
        boolean downloading = messageObject.loadedFileSize > 0;
        if (type == 4 && messageObject.getDocument() == null) {
            return false;
        }
        return canSave || downloading;
    }

    // Merged from xyz.nextalone.nagram.helper.MessageHelper.kt

    private static final SpannableStringBuilder[] spannedStrings = new SpannableStringBuilder[5];
    private static final Pattern ZALGO_PATTERN = Pattern.compile("\\p{M}{4}");
    private static final Pattern ZALGO_CLEANUP = Pattern.compile("\\p{M}+");

    public static void addMessageToClipboard(MessageObject selectedObject, Runnable callback) {
        String path = getPathToMessage(selectedObject);
        if (!TextUtils.isEmpty(path)) {
            File file = new File(path);
            if (file.exists()) {
                addFileToClipboard(file, callback);
            }
        }
    }

    public static void addMessageToClipboardAsSticker(MessageObject selectedObject, Runnable callback) {
        String path = getPathToMessage(selectedObject);
        try {
            if (!TextUtils.isEmpty(path)) {
                Bitmap image = BitmapFactory.decodeFile(path);
                if (image != null) {
                    File file2 = path.endsWith(".jpg") ? new File(path.replace(".jpg", ".webp")) : new File(path + ".webp");
                    FileOutputStream stream = new FileOutputStream(file2);
                    if (Build.VERSION.SDK_INT >= 30) {
                        image.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, stream);
                    } else {
                        image.compress(Bitmap.CompressFormat.WEBP, 100, stream);
                    }
                    stream.close();
                    addFileToClipboard(file2, callback);
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static void addFileToClipboard(File file, Runnable callback) {
        try {
            Context context = ApplicationLoader.applicationContext;
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            Uri uri = FileProvider.getUriForFile(context, ApplicationLoader.getApplicationId() + ".provider", file);
            ClipData clip = ClipData.newUri(context.getContentResolver(), "label", uri);
            clipboard.setPrimaryClip(clip);
            if (callback != null) callback.run();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static String showForwardDate(MessageObject obj, String orig) {
        long date = obj.messageOwner != null && obj.messageOwner.fwd_from != null ? obj.messageOwner.fwd_from.date : 0;
        String day = LocaleController.formatDate(date);
        String time = LocaleController.getInstance().getFormatterDay().format(new Date(date * 1000L));
        boolean enabled = NaConfig.INSTANCE.getDateOfForwardedMsg().Bool();
        if (!enabled || date == 0) {
            return orig;
        } else {
            if (day.equals(time)) {
                return orig + " · " + day;
            } else {
                return orig + " · " + day + ' ' + time;
            }
        }
    }

    public static String zalgoFilter(String text) {
        CharSequence res = zalgoFilter((CharSequence) text);
        return res == null ? "" : res.toString();
    }

    public static CharSequence zalgoFilter(CharSequence text) {
        if (TextUtils.isEmpty(text)) return "";
        if (!NaConfig.INSTANCE.getZalgoFilter().Bool()) return text;
        if (text.length() < 4 || text.length() > 2048) return text;
        if (!ZALGO_PATTERN.matcher(text).find()) return text;

        if (!(text instanceof Spannable)) {
            return ZALGO_CLEANUP.matcher(text).replaceAll("");
        }

        SpannableStringBuilder ssb = new SpannableStringBuilder(text);
        Matcher matcher = ZALGO_CLEANUP.matcher(ssb);
        List<int[]> ranges = new ArrayList<>();

        while (matcher.find()) {
            ranges.add(new int[]{matcher.start(), matcher.end()});
        }

        for (int i = ranges.size() - 1; i >= 0; i--) {
            int[] range = ranges.get(i);
            ssb.delete(range[0], range[1]);
        }

        return ssb;
    }

    public static boolean containsMarkdown(CharSequence text) {
        CharSequence newText = AndroidUtilities.getTrimmedString(text);
        var message = new CharSequence[]{AndroidUtilities.getTrimmedString(newText)};
        var entities = MediaDataController.getInstance(UserConfig.selectedAccount).getEntities(message, true);
        return entities != null && !entities.isEmpty();
    }

    public static boolean canSendAsDice(String text, ChatActivity parentFragment, long dialog_id) {
        boolean canSendGames = true;
        if (DialogObject.isChatDialog(dialog_id)) {
            TLRPC.Chat chat = parentFragment.getMessagesController().getChat(-dialog_id);
            canSendGames = ChatObject.canSendStickers(chat);
        }
        // noinspection UnnecessaryUnicodeEscape
        return canSendGames && parentFragment.getMessagesController().diceEmojies.contains(text.replace("\ufe0f", ""));
    }

    private static String formatTime(int timestamp) {
        return LocaleController.formatString(R.string.formatDateAtTime,
                LocaleController.getInstance().getFormatterYear().format(new Date(timestamp * 1000L)),
                LocaleController.getInstance().getFormatterDay().format(new Date(timestamp * 1000L)));
    }

    public static CharSequence getTimeHintText(MessageObject messageObject) {
        SpannableStringBuilder text = new SpannableStringBuilder();
        if (spannedStrings[3] == null) {
            spannedStrings[3] = new SpannableStringBuilder("\u200B");
            spannedStrings[3].setSpan(new ColoredImageSpan(Theme.chat_timeHintSentDrawable), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        text.append(spannedStrings[3]);
        text.append(' ');
        text.append(formatTime(messageObject.messageOwner.date));
        if (messageObject.messageOwner.edit_date != 0) {
            text.append("\n");
            if (spannedStrings[1] == null) {
                spannedStrings[1] = new SpannableStringBuilder("\u200B");
                spannedStrings[1].setSpan(new ColoredImageSpan(Theme.chat_editDrawable), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            text.append(spannedStrings[1]);
            text.append(' ');
            text.append(formatTime(messageObject.messageOwner.edit_date));
        }
        if (messageObject.messageOwner.fwd_from != null && messageObject.messageOwner.fwd_from.date != 0) {
            text.append("\n");
            if (spannedStrings[4] == null) {
                spannedStrings[4] = new SpannableStringBuilder("\u200B");
                ColoredImageSpan span = new ColoredImageSpan(Theme.chat_timeHintForwardDrawable);
                span.setSize(AndroidUtilities.dp(12f));
                spannedStrings[4].setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            text.append(spannedStrings[4]);
            text.append(' ');
            text.append(formatTime(messageObject.messageOwner.fwd_from.date));
        }
        return text;
    }

    public static void copyVideoFrameToClipboard(File videoFile, long positionMs, View bulletinContainer, Theme.ResourcesProvider resourcesProvider, Runnable fallbackAction) {
        Utilities.globalQueue.postRunnable(() -> {
            Bitmap bitmap = null;
            if (videoFile != null && videoFile.exists()) {
                bitmap = createVideoFrameBitmap(videoFile, positionMs);
            }
            if (bitmap == null) {
                if (fallbackAction != null) {
                    AndroidUtilities.runOnUIThread(fallbackAction);
                }
                return;
            }
            saveFrameBitmapToClipboard(bitmap, bulletinContainer, resourcesProvider);
        });
    }

    public static Bitmap createVideoFrameBitmap(File videoFile, long positionMs) {
        Bitmap bitmap = null;
        AnimatedFileDrawable fileDrawable = null;
        try {
            fileDrawable = new AnimatedFileDrawable(videoFile, true, 0, 0, null, null, null, 0, 0, true, null);
            bitmap = fileDrawable.getFrameAtTime(positionMs, true);
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            if (fileDrawable != null) {
                fileDrawable.recycle();
            }
        }
        if (bitmap == null) {
            bitmap = SendMessagesHelper.createVideoThumbnailAtTime(videoFile.getAbsolutePath(), positionMs * 1000);
        }
        return bitmap;
    }

    public static void saveFrameBitmapToClipboard(Bitmap bitmap, View bulletinContainer, Theme.ResourcesProvider resourcesProvider) {
        if (bitmap == null) {
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            File tempFile = null;
            boolean saved = false;
            try {
                File cacheDir = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE);
                tempFile = new File(cacheDir, "frame_" + System.currentTimeMillis() + ".png");
                try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                    saved = bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                try {
                    bitmap.recycle();
                } catch (Exception ignore) {
                }
            }
            if (!saved) {
                if (tempFile != null) {
                    try {
                        if (!tempFile.delete()) {
                            tempFile.deleteOnExit();
                        }
                    } catch (Exception ignore) {
                    }
                }
                return;
            }
            final File finalTempFile = tempFile;
            AndroidUtilities.runOnUIThread(() -> {
                Runnable callback = null;
                if (bulletinContainer instanceof FrameLayout container) {
                    callback = () -> BulletinFactory.of(container, resourcesProvider).createCopyBulletin(getString(R.string.PhotoCopied)).show();
                }
                addFileToClipboard(finalTempFile, callback);
            });
        });
    }

    public static ArrayList<TLRPC.MessageEntity> reparseMessageEntities(ArrayList<TLRPC.MessageEntity> translatedEntities) {
        if (translatedEntities == null) {
            return null;
        }
        if (!NaConfig.INSTANCE.getTranslatorKeepMarkdown().Bool()) {
            ArrayList<TLRPC.MessageEntity> entities = new ArrayList<>();
            for (TLRPC.MessageEntity entity : translatedEntities) {
                boolean isMarkdownEntity = entity instanceof TLRPC.TL_messageEntitySpoiler;
                isMarkdownEntity |= entity instanceof TLRPC.TL_messageEntityBold;
                isMarkdownEntity |= entity instanceof TLRPC.TL_messageEntityItalic;
                isMarkdownEntity |= entity instanceof TLRPC.TL_messageEntityCode;
                isMarkdownEntity |= entity instanceof TLRPC.TL_messageEntityStrike;
                isMarkdownEntity |= entity instanceof TLRPC.TL_messageEntityUnderline;
                if (!isMarkdownEntity) {
                    entities.add(entity);
                }
            }
            return entities;
        }
        return translatedEntities;
    }

    public static ArrayList<TLRPC.MessageEntity> getEntitiesForText(MessageObject messageObject, CharSequence text, boolean summarized) {
        if (messageObject == null || messageObject.messageOwner == null) {
            return null;
        }
        final TLRPC.Message messageOwner = messageObject.messageOwner;
        if (summarized) {
            if (messageOwner.translated && messageOwner.translatedSummaryText != null) {
                return messageOwner.translatedSummaryText.entities;
            } else if (messageOwner.summaryText != null) {
                return messageOwner.summaryText.entities;
            }
            return null;
        }
        if (messageObject.translated) {
            if (messageOwner.voiceTranscriptionOpen) {
                return messageOwner.translatedVoiceTranscription != null ? messageOwner.translatedVoiceTranscription.entities : null;
            } else {
                return messageOwner.translatedText != null ? reparseMessageEntities(messageOwner.translatedText.entities) : null;
            }
        }
        if (messageOwner.translated && messageOwner.translatedText != null) {
            return mergeAppendTranslatedEntities(messageOwner.entities, messageOwner.translatedText, text);
        }
        return messageOwner.entities;
    }

    public static ArrayList<TLRPC.MessageEntity> mergeAppendTranslatedEntities(ArrayList<TLRPC.MessageEntity> baseEntities, TLRPC.TL_textWithEntities translatedText, CharSequence fullText) {
        if (fullText == null || translatedText == null || TextUtils.isEmpty(translatedText.text) || translatedText.entities == null) {
            return baseEntities;
        }
        final int fullLen = fullText.length();
        final int translatedLen = translatedText.text.length();
        if (translatedLen == 0 || fullLen < translatedLen) {
            return baseEntities;
        }
        final int translatedOffset = fullLen - translatedLen;
        final ArrayList<TLRPC.MessageEntity> translatedEntities = reparseMessageEntities(translatedText.entities);
        if (translatedEntities == null) {
            return baseEntities;
        }
        if (translatedOffset == 0) {
            return translatedEntities;
        }
        if (!TextUtils.regionMatches(fullText, translatedOffset, translatedText.text, 0, translatedLen)) {
            return baseEntities;
        }
        final ArrayList<TLRPC.MessageEntity> merged = new ArrayList<>();
        if (baseEntities != null) {
            merged.addAll(baseEntities);
        }
        for (int i = 0, size = translatedEntities.size(); i < size; i++) {
            final TLRPC.MessageEntity entity = translatedEntities.get(i);
            if (entity == null) {
                continue;
            }
            final TLRPC.MessageEntity copied = copyMessageEntity(entity);
            if (copied == null) {
                continue;
            }
            copied.offset += translatedOffset;
            if (copied.length <= 0 || copied.offset < 0 || copied.offset >= fullLen) {
                continue;
            }
            if (copied.offset + copied.length > fullLen) {
                copied.length = fullLen - copied.offset;
            }
            merged.add(copied);
        }
        return merged;
    }

    public static TLRPC.MessageEntity copyMessageEntity(TLRPC.MessageEntity entity) {
        if (entity == null) {
            return null;
        }
        NativeByteBuffer data = null;
        try {
            data = new NativeByteBuffer(entity.getObjectSize());
            entity.serializeToStream(data);
            data.rewind();
            final int constructor = data.readInt32(true);
            final TLRPC.MessageEntity result = TLRPC.MessageEntity.TLdeserialize(data, constructor, true);
            if (result instanceof TLRPC.TL_messageEntityCustomEmoji && entity instanceof TLRPC.TL_messageEntityCustomEmoji) {
                ((TLRPC.TL_messageEntityCustomEmoji) result).document = ((TLRPC.TL_messageEntityCustomEmoji) entity).document;
            }
            return result;
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            if (data != null) {
                data.reuse();
            }
        }
        return null;
    }
}
