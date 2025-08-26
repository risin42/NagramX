package tw.nekomimi.nekogram.helpers;

import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.messenger.TranslateController.UNKNOWN_LANGUAGE;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.DatePicker;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteException;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BaseController;
import org.telegram.messenger.ChatObject;
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
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.Bulletin;
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
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

public class MessageHelper extends BaseController {

    private static final MessageHelper[] Instance = new MessageHelper[UserConfig.MAX_ACCOUNT_COUNT];
    private static final CharsetDecoder utf8Decoder = StandardCharsets.UTF_8.newDecoder();

    public MessageHelper(int num) {
        super(num);
    }

    public static String getPathToMessage(MessageObject messageObject) {
        String path = messageObject.messageOwner.attachPath;
        if (!TextUtils.isEmpty(path)) {
            File temp = new File(path);
            if (!temp.exists()) {
                path = null;
            }
        }
        if (TextUtils.isEmpty(path)) {
            path = FileLoader.getInstance(UserConfig.selectedAccount).getPathToMessage(messageObject.messageOwner).toString();
            File temp = new File(path);
            if (!temp.exists()) {
                path = null;
            }
        }
        if (TextUtils.isEmpty(path)) {
            path = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(messageObject.getDocument(), true).toString();
            File temp = new File(path);
            if (!temp.exists()) {
                return null;
            }
        }
        return path;
    }

    public void resetMessageContent(long dialog_id, MessageObject messageObject) {
        TLRPC.Message message = messageObject.messageOwner;

        MessageObject obj = new MessageObject(currentAccount, message, true, true);

        ArrayList<MessageObject> arrayList = new ArrayList<>();
        arrayList.add(obj);
        getNotificationCenter().postNotificationName(NotificationCenter.replaceMessagesObjects, dialog_id, arrayList, false);
    }

    public void resetMessageContent(long dialog_id, ArrayList<MessageObject> messageObjects) {
        ArrayList<MessageObject> arrayList = new ArrayList<>();
        for (MessageObject messageObject : messageObjects) {
            MessageObject obj = new MessageObject(currentAccount, messageObject.messageOwner, true, true);
            arrayList.add(obj);
        }
        getNotificationCenter().postNotificationName(NotificationCenter.replaceMessagesObjects, dialog_id, arrayList, false);
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

    public MessageObject getLastMessageFromUnblock(long dialogId) {
        SQLiteCursor cursor;
        MessageObject ret = null;
        try {
            cursor = getMessagesStorage().getDatabase().queryFinalized(String.format(Locale.US, "SELECT data,send_state,mid,date FROM messages_v2 WHERE uid = %d ORDER BY date DESC LIMIT %d,%d", dialogId, 0, 10));
            while (cursor.next()) {
                NativeByteBuffer data = cursor.byteBufferValue(0);
                if (data == null)
                    continue;
                TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                data.reuse();
                if (getMessagesController().blockePeers.indexOfKey(message.from_id.user_id) < 0) {
                    // valid message
                    ret = new MessageObject(currentAccount, message, true, true);
                    message.send_state = cursor.intValue(1);
                    message.id = cursor.intValue(2);
                    message.date = cursor.intValue(3);
                    message.dialog_id = dialogId;
                    // Fix username show
                    if (getMessagesController().getUser(ret.getSenderId()) == null) {
                        TLRPC.User user = getMessagesStorage().getUser(ret.getSenderId());
                        if (user != null)
                            getMessagesController().putUser(user, true);
                    }
                    break;
                }
            }
            cursor.dispose();
        } catch (SQLiteException sqLiteException) {
            FileLog.e("NekoX, ignoreBlocked, SQLiteException when read last message from unblocked user", sqLiteException);
            return null;
        }
        return ret;
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
            xyz.nextalone.nagram.helper.MessageHelper.INSTANCE.addFileToClipboard(file, callback);
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
                    Calendar calendar = Calendar.getInstance();
                    DatePickerDialog dateDialog = new DatePickerDialog(context, (view1, year1, month, dayOfMonth1) -> {
                        TimePickerDialog timeDialog = new TimePickerDialog(context, (view11, hourOfDay, minute) -> {
                            calendar.set(year1, month, dayOfMonth1, hourOfDay, minute);
                            callback.run((int) (calendar.getTimeInMillis() / 1000));
                        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true);
                        timeDialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(R.string.Set), timeDialog);
                        timeDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.Cancel), (dialog3, which3) -> {
                        });
                        fragment.showDialog(timeDialog);
                    }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));

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
                e.printStackTrace();
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
        req.from_id = fromId;
        req.flags |= 1;
        req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
        if (replyMessageId != 0) {
            req.top_msg_id = replyMessageId;
            req.flags |= 2;
        }
        req.hash = hash;
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (response instanceof TLRPC.messages_Messages) {
                var res = (TLRPC.messages_Messages) response;
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

}
