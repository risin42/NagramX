package tw.nekomimi.nekogram.ui;

import static org.telegram.messenger.LocaleController.getString;
import static tw.nekomimi.nekogram.DatacenterActivity.getDCLocation;
import static tw.nekomimi.nekogram.DatacenterActivity.getDCName;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.SpannableString;
import android.text.TextUtils;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.CodeHighlighting;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LanguageDetector;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.TranslateController;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.EmptyCell;
import org.telegram.ui.Cells.NotificationsCheckCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UndoView;
import org.telegram.ui.ProfileActivity;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import tw.nekomimi.nekogram.helpers.MessageHelper;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

public class MessageDetailsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    public static final Gson gson = new GsonBuilder()
            .registerTypeHierarchyAdapter(byte[].class, new ByteArrayToBase64TypeAdapter())
            .setExclusionStrategies(new CustomExclusionStrategy()).create();
    public static final Gson prettyGson = new GsonBuilder().setPrettyPrinting().create();
    private final MessageObject messageObject;
    private final MessageObject.GroupedMessages messageGroup;
    private volatile boolean fragmentDestroyed;
    private RecyclerListView listView;
    private ListAdapter listAdapter;
    private TLRPC.Chat fromChat;
    private TLRPC.User fromUser;
    private String fileName;
    private String filePath;
    private int width;
    private int height;
    private String video_codec;
    private float frameRate;
    private long bitRate;
    private boolean isBitRateEstimated;
    private long audioBitRate;
    private boolean isAudioBitRateEstimated;
    private boolean hasMultipleTracks;
    private int sampleRate;
    private int dc;
    private int rowCount;
    private int idRow;
    private int scheduledRow;
    private int messageRow;
    private int captionRow;
    private int groupRow;
    private int channelRow;
    private int fromRow;
    private int botRow;
    private int dateRow;
    private int editedRow;
    private int forwardRow;
    private int restrictionReasonRow;
    private int fileNameRow;
    private int filePathRow;
    private int fileSizeRow;
    private int fileMimeTypeRow;
    private int mediaRow;
    private int dcRow;
    private int languageRow;
    private int buttonsRow;
    private int emptyRow;
    private int jsonTextRow;
    private int exportRow;
    private int endRow;

    public MessageDetailsActivity(MessageObject messageObject, MessageObject.GroupedMessages messageGroup) {
        this.messageObject = messageObject;
        this.messageGroup = messageGroup;
        if (messageObject.messageOwner.peer_id != null && messageObject.messageOwner.peer_id.channel_id != 0) {
            fromChat = getMessagesController().getChat(messageObject.messageOwner.peer_id.channel_id);
        } else if (messageObject.messageOwner.peer_id != null && messageObject.messageOwner.peer_id.chat_id != 0) {
            fromChat = getMessagesController().getChat(messageObject.messageOwner.peer_id.chat_id);
        }
        if (messageObject.messageOwner.from_id.user_id != 0) {
            fromUser = getMessagesController().getUser(messageObject.messageOwner.from_id.user_id);
        }
        var media = MessageObject.getMedia(messageObject.messageOwner);
        if (media != null) {
            filePath = MessageHelper.getPathToMessage(messageObject);
            var photo = media.webpage != null ? media.webpage.photo : media.photo;
            if (photo != null) {
                dc = photo.dc_id;
                var photoSize = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, Integer.MAX_VALUE);
                if (photoSize != null) {
                    width = photoSize.w;
                    height = photoSize.h;
                }
            }
            var document = media.webpage != null ? media.webpage.document : media.document;
            if (document != null) {
                dc = document.dc_id;
                for (var attribute : document.attributes) {
                    if (attribute instanceof TLRPC.TL_documentAttributeFilename) {
                        fileName = attribute.file_name;
                    }
                    if (attribute instanceof TLRPC.TL_documentAttributeImageSize ||
                            attribute instanceof TLRPC.TL_documentAttributeVideo) {
                        width = attribute.w;
                        height = attribute.h;
                        video_codec = attribute.video_codec;
                    }
                }
                // extract metadata
                if (!TextUtils.isEmpty(filePath)) {
                    final String path = filePath;
                    Utilities.globalQueue.postRunnable(() -> {
                        if (fragmentDestroyed) {
                            return;
                        }
                        extractMediaMetadata(path);
                        AndroidUtilities.runOnUIThread(() -> {
                            if (fragmentDestroyed || isFinishing() || getParentActivity() == null) {
                                return;
                            }
                            updateRows();
                        }, 300);
                    });
                }
            }
        }
    }

    private void extractMediaMetadata(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            return;
        }

        frameRate = 0;
        bitRate = 0;
        audioBitRate = 0;
        sampleRate = 0;
        isBitRateEstimated = false;
        isAudioBitRateEstimated = false;
        hasMultipleTracks = false;

        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(filePath);
            boolean isVideo = false;
            boolean isAudio = false;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("video/")) {
                    isVideo = true;
                    if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        try {
                            frameRate = format.getFloat(MediaFormat.KEY_FRAME_RATE);
                        } catch (ClassCastException e) {
                            frameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE);
                        }
                    }
                    if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                        bitRate = format.getInteger(MediaFormat.KEY_BIT_RATE);
                    } else if (format.containsKey("max-bitrate")) {
                        bitRate = format.getInteger("max-bitrate");
                    }
                } else if (mime != null && mime.startsWith("audio/")) {
                    isAudio = true;
                    if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    }
                    if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                        audioBitRate = format.getInteger(MediaFormat.KEY_BIT_RATE);
                    } else if (format.containsKey("max-bitrate")) {
                        audioBitRate = format.getInteger("max-bitrate");
                    }
                }
            }
            hasMultipleTracks = isVideo && isAudio;
            if (isVideo) {
                float extractorFrameRate = frameRate;
                frameRate = 0;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
                        retriever.setDataSource(filePath);
                        String frameCountStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT);
                        String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                        if (frameCountStr != null && durationStr != null) {
                            long frameCount = Long.parseLong(frameCountStr);
                            long durationMs = Long.parseLong(durationStr);
                            if (frameCount > 0 && durationMs > 0) {
                                frameRate = (float) (frameCount / (durationMs / 1000.0));
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
                if (frameRate == 0) {
                    frameRate = extractorFrameRate;
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            extractor.release();
        }
        if (bitRate == 0 || audioBitRate == 0 || sampleRate == 0) {
            try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
                retriever.setDataSource(filePath);
                if (bitRate == 0 || audioBitRate == 0) {
                    String bitrateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
                    if (bitrateStr != null) {
                        long totalBitrate = Long.parseLong(bitrateStr);
                        String hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO);
                        String hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO);
                        boolean isVideoFile = "yes".equals(hasVideo);
                        boolean isAudioFile = "yes".equals(hasAudio);
                        if (isVideoFile && !isAudioFile && bitRate == 0) {
                            bitRate = totalBitrate;
                        } else if (!isVideoFile && isAudioFile && audioBitRate == 0) {
                            audioBitRate = totalBitrate;
                        } else if (isVideoFile && isAudioFile) {
                            hasMultipleTracks = true;
                            if (bitRate == 0 && audioBitRate > 0) {
                                bitRate = totalBitrate - audioBitRate;
                                if (bitRate < 0) bitRate = totalBitrate;
                            } else if (audioBitRate == 0 && bitRate > 0) {
                                audioBitRate = totalBitrate - bitRate;
                                if (audioBitRate < 0) audioBitRate = 0;
                            }
                        }
                    }
                }
                if (sampleRate == 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    String sampleRateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE);
                    if (sampleRateStr != null) {
                        sampleRate = Integer.parseInt(sampleRateStr);
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        if (bitRate == 0 || (audioBitRate == 0 && sampleRate > 0)) {
            try (MediaMetadataRetriever r = new MediaMetadataRetriever()) {
                r.setDataSource(filePath);
                String durationStr = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                String hasVideo = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO);
                boolean isVideoFile = "yes".equals(hasVideo);
                if (durationStr != null) {
                    long durationMs = Long.parseLong(durationStr);
                    if (durationMs > 0) {
                        long fileSizeBytes = file.length();
                        double durationSeconds = durationMs / 1000.0;
                        long estimatedTotalBitrate = (long) ((fileSizeBytes * 8) / durationSeconds);
                        if (bitRate == 0 && isVideoFile) {
                            bitRate = estimatedTotalBitrate;
                            isBitRateEstimated = true;
                        } else if (audioBitRate == 0 && sampleRate > 0 && !isVideoFile) {
                            audioBitRate = estimatedTotalBitrate;
                            isAudioBitRateEstimated = true;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        updateRows();
        return true;
    }

    @SuppressLint({"NewApi", "RtlHardcoded"})
    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(getString(R.string.MessageDetails));

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

        listView = new RecyclerListView(context);
        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((view, position, x, y) -> {
            if (position == exportRow) {
                try {
                    AndroidUtilities.addToClipboard(gson.toJson(messageObject.messageOwner));
                    BulletinFactory.of(this).createCopyBulletin(LocaleController.formatString(R.string.TextCopied)).show();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            } else if (position != endRow && position != emptyRow) {
                TextDetailSettingsCell textCell = (TextDetailSettingsCell) view;
                try {
                    AndroidUtilities.addToClipboard(textCell.getValueTextView().getText());
                    BulletinFactory.of(this).createCopyBulletin(LocaleController.formatString(R.string.TextCopied)).show();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }

        });
        listView.setOnItemLongClickListener((view, position) -> {
            if (position == idRow) {
                if (ChatObject.isChannel(fromChat)) {
                    TLRPC.TL_channels_exportMessageLink req = new TLRPC.TL_channels_exportMessageLink();
                    req.id = messageObject.getId();
                    req.channel = MessagesController.getInputChannel(fromChat);
                    req.thread = false;
                    getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        if (response != null) {
                            TLRPC.TL_exportedMessageLink exportedMessageLink = (TLRPC.TL_exportedMessageLink) response;
                            try {
                                AndroidUtilities.addToClipboard(exportedMessageLink.link);
                                BulletinFactory.of(this).createCopyLinkBulletin(exportedMessageLink.link.contains("/c/")).show();
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        } else if (error != null) {
                            BulletinFactory.of(this).createErrorBulletin(error.text).show();
                        }
                    }));
                } else if (fromUser != null) {
                    try {
                        String link = "tg://openmessage?user_id=" + fromUser.id + "&message_id=" + messageObject.messageOwner.id;
                        AndroidUtilities.addToClipboard(link);
                        BulletinFactory.of(this).createCopyBulletin(LocaleController.formatString(R.string.TextCopied)).show();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                return true;
            } else if (position == filePathRow) {
                AndroidUtilities.runOnUIThread(() -> {
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("application/octet-stream");
                    try {
                        intent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(getParentActivity(), BuildConfig.APPLICATION_ID + ".provider", new File(filePath)));
                        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception ignore) {
                        intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(filePath)));
                    }
                    startActivityForResult(Intent.createChooser(intent, getString(R.string.ShareFile)), 500);
                });
            } else if (position == channelRow || position == groupRow) {
                if (fromChat != null) {
                    Bundle args = new Bundle();
                    args.putLong("chat_id", fromChat.id);
                    ProfileActivity fragment = new ProfileActivity(args);
                    presentFragment(fragment);
                }
            } else if (position == fromRow) {
                if (fromUser != null) {
                    Bundle args = new Bundle();
                    args.putLong("user_id", fromUser.id);
                    ProfileActivity fragment = new ProfileActivity(args);
                    presentFragment(fragment);
                }
            } else if (position == restrictionReasonRow) {
                ArrayList<TLRPC.RestrictionReason> reasons = messageObject.messageOwner.restriction_reason;
                LinearLayout ll = new LinearLayout(getParentActivity());
                ll.setOrientation(LinearLayout.VERTICAL);

                AlertDialog dialog = new AlertDialog.Builder(getParentActivity())
                        .setView(ll)
                        .create();

                for (TLRPC.RestrictionReason reason : reasons) {
                    TextDetailSettingsCell cell = new TextDetailSettingsCell(getParentActivity());
                    cell.setBackground(Theme.getSelectorDrawable(false));
                    cell.setMultilineDetail(true);
                    cell.setOnClickListener(v1 -> {
                        dialog.dismiss();
                        AndroidUtilities.addToClipboard(cell.getValueTextView().getText());
                        BulletinFactory.of(this).createCopyBulletin(LocaleController.formatString(R.string.TextCopied)).show();
                    });
                    cell.setTextAndValue(reason.reason + "-" + reason.platform, reason.text, false);

                    ll.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                }

                showDialog(dialog);
            } else {
                return false;
            }
            return true;
        });

        UndoView copyTooltip = new UndoView(context);
        copyTooltip.setInfoText(getString(R.string.TextCopied));
        frameLayout.addView(copyTooltip, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 8, 0, 8, 8));

        return fragmentView;
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void updateRows() {
        rowCount = 0;
        idRow = rowCount++;
        scheduledRow = messageObject.scheduled ? rowCount++ : -1;
        messageRow = TextUtils.isEmpty(messageObject.messageText) ? -1 : rowCount++;
        captionRow = !TextUtils.isEmpty(messageObject.caption) || messageGroup != null && messageGroup.findCaptionMessageObject() != null && !TextUtils.isEmpty(messageGroup.findCaptionMessageObject().caption) ? rowCount++ : -1;
        groupRow = fromChat != null && !fromChat.broadcast ? rowCount++ : -1;
        channelRow = fromChat != null && fromChat.broadcast ? rowCount++ : -1;
        fromRow = fromUser != null || messageObject.messageOwner.post_author != null ? rowCount++ : -1;
        botRow = fromUser != null && fromUser.bot ? rowCount++ : -1;
        dateRow = messageObject.messageOwner.date != 0 ? rowCount++ : -1;
        editedRow = messageObject.messageOwner.edit_date != 0 ? rowCount++ : -1;
        forwardRow = messageObject.isForwarded() ? rowCount++ : -1;
        restrictionReasonRow = messageObject.messageOwner.restriction_reason.isEmpty() ? -1 : rowCount++;
        fileNameRow = TextUtils.isEmpty(fileName) ? -1 : rowCount++;
        filePathRow = TextUtils.isEmpty(filePath) ? -1 : rowCount++;
        fileSizeRow = messageObject.getSize() != 0 ? rowCount++ : -1;
        fileMimeTypeRow = !TextUtils.isEmpty(messageObject.getMimeType()) ? rowCount++ : -1;
        mediaRow = (width > 0 && height > 0) || !TextUtils.isEmpty(video_codec) || frameRate > 0 || bitRate > 0 || audioBitRate > 0 || sampleRate > 0 ? rowCount++ : -1;
        dcRow = dc != 0 ? rowCount++ : -1;
        languageRow = TextUtils.isEmpty(MessageHelper.getMessagePlainText(messageObject, messageGroup)) ? -1 : rowCount++;
        buttonsRow = messageObject.messageOwner.reply_markup instanceof TLRPC.TL_replyInlineMarkup ? rowCount++ : -1;
        emptyRow = rowCount++;
        jsonTextRow = rowCount++;
        exportRow = rowCount++;
        endRow = rowCount++;
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{EmptyCell.class, TextSettingsCell.class, TextCheckCell.class, HeaderCell.class, TextDetailSettingsCell.class, NotificationsCheckCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_avatar_backgroundActionBarBlue));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_avatar_backgroundActionBarBlue));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_avatar_actionBarIconBlue));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_avatar_actionBarSelectorBlue));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUBACKGROUND, null, null, null, null, Theme.key_actionBarDefaultSubmenuBackground));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM, null, null, null, null, Theme.key_actionBarDefaultSubmenuItem));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextDetailSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextDetailSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));

        return themeDescriptions;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiLoaded) {
            if (listView != null) {
                listView.invalidateViews();
            }
        }
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        fragmentDestroyed = true;
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
    }

    private static class ByteArrayToBase64TypeAdapter implements JsonSerializer<byte[]>, JsonDeserializer<byte[]> {
        public byte[] deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return Base64.decode(json.getAsString(), Base64.NO_WRAP);
        }

        public JsonElement serialize(byte[] src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(Base64.encodeToString(src, Base64.NO_WRAP));
        }
    }

    private static class CustomExclusionStrategy implements com.google.gson.ExclusionStrategy {
        @Override
        public boolean shouldSkipField(com.google.gson.FieldAttributes f) {
            return "parentRichText".equals(f.getName()) || "mChangingConfigurations".equals(f.getName());
        }

        @Override
        public boolean shouldSkipClass(Class<?> clazz) {
            return false;
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            position = holder.getAdapterPosition();
            switch (holder.getItemViewType()) {
                case 1: {
                    if (position == endRow) {
                        holder.itemView.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else {
                        holder.itemView.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
                case 2: {
                    TextDetailSettingsCell textCell = (TextDetailSettingsCell) holder.itemView;
                    textCell.setMultilineDetail(true);
                    boolean divider = position + 1 != emptyRow;
                    if (position == idRow) {
                        textCell.setTextAndValue("ID", String.valueOf(messageObject.messageOwner.id), divider);
                    } else if (position == messageRow) {
                        textCell.setTextAndValue("Message", messageObject.messageText.toString(), divider);
                    } else if (position == captionRow) {
                        if (!TextUtils.isEmpty(messageObject.caption)) {
                            textCell.setTextAndValue("Caption", messageObject.caption.toString(), divider);
                        } else if (messageGroup != null) {
                            MessageObject captionMessageObject = messageGroup.findCaptionMessageObject();
                            if (!TextUtils.isEmpty(captionMessageObject.caption)) {
                                textCell.setTextAndValue("Caption", captionMessageObject.caption.toString(), divider);
                            }
                        }
                    } else if (position == channelRow || position == groupRow) {
                        StringBuilder builder = new StringBuilder();
                        builder.append(fromChat.title);
                        builder.append("\n");
                        if (!TextUtils.isEmpty(fromChat.username)) {
                            builder.append("@");
                            builder.append(fromChat.username);
                            builder.append("\n");
                        }
                        builder.append(fromChat.id);
                        textCell.setTextAndValue(position == channelRow ? "Channel" : "Group", builder.toString(), divider);
                    } else if (position == fromRow) {
                        StringBuilder builder = new StringBuilder();
                        if (fromUser != null) {
                            builder.append(ContactsController.formatName(fromUser.first_name, fromUser.last_name));
                            builder.append("\n");
                            if (!TextUtils.isEmpty(fromUser.username)) {
                                builder.append("@");
                                builder.append(fromUser.username);
                                builder.append("\n");
                            }
                            builder.append(fromUser.id);
                        } else {
                            builder.append(messageObject.messageOwner.post_author);
                        }
                        textCell.setTextAndValue("From", builder.toString(), divider);
                    } else if (position == botRow) {
                        textCell.setTextAndValue("Bot", "Yes", divider);
                    } else if (position == dateRow) {
                        long date = (long) messageObject.messageOwner.date * 1000;
                        textCell.setTextAndValue(messageObject.scheduled ? "Scheduled date" : "Date", messageObject.messageOwner.date == 0x7ffffffe ? "When online" : LocaleController.formatString(R.string.formatDateAtTime, LocaleController.getInstance().getFormatterYear().format(new Date(date)), LocaleController.getInstance().getFormatterDay().format(new Date(date))), divider);
                    } else if (position == editedRow) {
                        long date = (long) messageObject.messageOwner.edit_date * 1000;
                        textCell.setTextAndValue("Edited", LocaleController.formatString(R.string.formatDateAtTime, LocaleController.getInstance().getFormatterYear().format(new Date(date)), LocaleController.getInstance().getFormatterDay().format(new Date(date))), divider);
                    } else if (position == forwardRow) {
                        StringBuilder builder = new StringBuilder();
                        if (messageObject.messageOwner.fwd_from.from_id == null) {
                            builder.append(messageObject.messageOwner.fwd_from.from_name).append('\n');
                        } else {
                            if (messageObject.messageOwner.fwd_from.from_id.channel_id != 0) {
                                TLRPC.Chat chat = getMessagesController().getChat(messageObject.messageOwner.fwd_from.from_id.channel_id);
                                builder.append(chat.title);
                                builder.append("\n");
                                if (!TextUtils.isEmpty(chat.username)) {
                                    builder.append("@");
                                    builder.append(chat.username);
                                    builder.append("\n");
                                }
                                builder.append(chat.id);
                            } else if (messageObject.messageOwner.fwd_from.from_id.user_id != 0) {
                                TLRPC.User user = getMessagesController().getUser(messageObject.messageOwner.fwd_from.from_id.channel_id);
                                if (user != null) {
                                    builder.append(ContactsController.formatName(user.first_name, user.last_name));
                                    builder.append("\n");
                                    if (!TextUtils.isEmpty(user.username)) {
                                        builder.append("@");
                                        builder.append(user.username);
                                        builder.append("\n");
                                    }
                                    builder.append(user.id);
                                } else builder.append("null user");
                            } else if (!TextUtils.isEmpty(messageObject.messageOwner.fwd_from.from_name)) {
                                builder.append(messageObject.messageOwner.fwd_from.from_name);
                            }
                        }
                        textCell.setTextAndValue("Forward From", builder.toString(), divider);
                    } else if (position == fileNameRow) {
                        textCell.setTextAndValue("File Name", fileName, divider);
                    } else if (position == filePathRow) {
                        textCell.setTextAndValue("File Path", filePath, divider);
                    } else if (position == fileSizeRow) {
                        textCell.setTextAndValue("File Size", AndroidUtilities.formatFileSize(messageObject.getSize()), divider);
                    } else if (position == fileMimeTypeRow) {
                        textCell.setTextAndValue("MIME Type", messageObject.getMimeType(), divider);
                    } else if (position == mediaRow) {
                        StringBuilder mediaInfo = new StringBuilder();
                        if (width > 0 && height > 0) {
                            mediaInfo.append(String.format(Locale.US, "%dx%d", width, height));
                        }
                        if (!TextUtils.isEmpty(video_codec)) {
                            if (mediaInfo.length() > 0) mediaInfo.append(", ");
                            mediaInfo.append(video_codec);
                        }
                        if (frameRate > 0) {
                            if (mediaInfo.length() > 0) mediaInfo.append(", ");
                            mediaInfo.append(String.format(Locale.US, "%.2f fps", frameRate));
                        }
                        if (bitRate > 0) {
                            if (mediaInfo.length() > 0) mediaInfo.append(", ");
                            String trackPrefix = hasMultipleTracks ? "V: " : "";
                            String estimatedPrefix = isBitRateEstimated ? "~" : "";
                            String bitRateStr = String.format(Locale.US, "%s%s%.0f Kbps", trackPrefix, estimatedPrefix, bitRate / 1000.0);
                            mediaInfo.append(bitRateStr);
                        }
                        if (audioBitRate > 0) {
                            if (mediaInfo.length() > 0) mediaInfo.append(", ");
                            String trackPrefix = hasMultipleTracks ? "A: " : "";
                            String estimatedPrefix = isAudioBitRateEstimated ? "~" : "";
                            String audioBitRateStr = String.format(Locale.US, "%s%s%.0f Kbps", trackPrefix, estimatedPrefix, audioBitRate / 1000.0);
                            mediaInfo.append(audioBitRateStr);
                        }
                        if (sampleRate > 0) {
                            if (mediaInfo.length() > 0) mediaInfo.append(", ");
                            mediaInfo.append(String.format(Locale.US, "%d Hz", sampleRate));
                        }
                        textCell.setTextAndValue("Media", mediaInfo.toString(), divider);
                    } else if (position == dcRow) {
                        String value = String.format(Locale.US, "DC%d %s, %s", dc, getDCName(dc), getDCLocation(dc));
                        textCell.setTextAndValue("DC", value, divider);
                    } else if (position == restrictionReasonRow) {
                        ArrayList<TLRPC.RestrictionReason> reasons = messageObject.messageOwner.restriction_reason;
                        StringBuilder value = new StringBuilder();
                        for (TLRPC.RestrictionReason reason : reasons) {
                            value.append(reason.reason);
                            value.append("-");
                            value.append(reason.platform);
                            if (reasons.indexOf(reason) != reasons.size() - 1) {
                                value.append(", ");
                            }
                        }
                        textCell.setTextAndValue("Restriction Reason", value, divider);
                    } else if (position == scheduledRow) {
                        textCell.setTextAndValue("Scheduled", "Yes", divider);
                    } else if (position == languageRow) {
                        String originalLanguage = messageObject.messageOwner.originalLanguage;
                        if (originalLanguage != null) {
                            textCell.setTextAndValue("Language", originalLanguage, divider);
                        } else {
                            LanguageDetector.detectLanguage(String.valueOf(MessageHelper.getMessagePlainText(messageObject, messageGroup)),
                                    lang -> textCell.setTextAndValue("Language", lang, divider),
                                    e -> textCell.setTextAndValue("Language", TranslateController.UNKNOWN_LANGUAGE, divider)
                            );
                        }
                    } else if (position == buttonsRow) {
                        textCell.setTextAndValue("Buttons", gson.toJson(messageObject.messageOwner.reply_markup), divider);
                    } else if (position == jsonTextRow) {
                        try {
                            String jsonTempString = gson.toJson(messageObject.messageOwner);
                            JsonElement jsonElement = JsonParser.parseString(jsonTempString);
                            String jsonString = prettyGson.toJson(jsonElement);
                            final SpannableString[] sb = new SpannableString[1];
                            new CountDownTimer(300, 100) {
                                @Override
                                public void onTick(long millisUntilFinished) {
                                    sb[0] = CodeHighlighting.getHighlighted(jsonString, "json");
                                }

                                @Override
                                public void onFinish() {
                                    textCell.setTextAndValue("JSON", sb[0], divider);
                                }
                            }.start();
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                    break;
                }
                case 3: {
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    if (position == exportRow) {
                        textCell.setText(getString(R.string.ExportAsJson), false);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
                    }
                    break;
                }
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position != endRow && position != emptyRow;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = null;
            switch (viewType) {
                case 1:
                    view = new ShadowSectionCell(mContext);
                    break;
                case 2:
                    view = new TextDetailSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 3:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            // noinspection ConstantConditions
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == endRow || position == emptyRow) {
                return 1;
            } else if (position == exportRow) {
                return 3;
            } else {
                return 2;
            }
        }
    }
}
