package tw.nekomimi.nekogram.helpers;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.text.TextUtils;
import android.util.Base64;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.google.gson.Gson;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import xyz.nextalone.nagram.NaConfig;

public class TranscribeHelper {
    private static OkHttpClient okHttpClient;
    private static final Gson gson = new Gson();
    private static final ExecutorService executorService = Executors.newCachedThreadPool();
    public static final int TRANSCRIBE_AUTO = 0;
    public static final int TRANSCRIBE_PREMIUM = 1;
    public static final int TRANSCRIBE_WORKERSAI = 2;
    public static final int TRANSCRIBE_GEMINI = 3;
    public static final int TRANSCRIBE_OPENAI = 4;
    private static final String GEMINI_API_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/" + getString(R.string.LlmModelNameDefault) + ":generateContent?key=%s";
    private static final String GEMINI_PROMPT = """
    Your task is to create a detailed, verbatim transcription of the provided audio, formatted like closed captions for the hard of hearing. Follow these instructions strictly:

    1.  **Transcribe Speech:** Transcribe all spoken dialogue verbatim. Do NOT include speaker names or labels (like "Speaker 1:", "Person A:", etc.).
    2.  **Include Sounds:** Include relevant non-speech sounds, actions, and descriptions in square brackets `[]`.
    3.  **Format Sounds:** Place bracketed sound descriptions on their own line when they occur between dialogue segments, or inline within the dialogue when appropriate.
    4.  **Output Only:** Output ONLY the formatted transcription. Do not include any introductory text, explanations, or anything other than the transcription.

    **Example Output Format:**
    [footsteps approaching]
    Did you hear that?
    [distant siren wailing]
    Hear what? I didn't hear anything except that siren.
    No, before that. A kind of scraping sound. [chair creaks]
    [sighs] You're probably just imagining things again.
    [knocking on door]
    See! I told you!
    """.trim();

    private static final String OPENAI_COMPATIBLE_DEFAULT_PROMPT = GEMINI_PROMPT;

    public static boolean useTranscribeAI(int account) {
        int provider = NaConfig.INSTANCE.getTranscribeProvider().Int();
        return provider == TRANSCRIBE_WORKERSAI || provider == TRANSCRIBE_GEMINI || provider == TRANSCRIBE_OPENAI ||
                (!UserConfig.getInstance(account).isRealPremium() && provider == TRANSCRIBE_AUTO);
    }

    private static EditTextBoldCursor createAndSetupEditText(Context context, Theme.ResourcesProvider resourcesProvider, String initialText, String hintText, int imeOptions, boolean requestFocus) {
        var editText = new EditTextBoldCursor(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(64), View.MeasureSpec.EXACTLY));
            }
        };
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        editText.setHintColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText, resourcesProvider));
        editText.setHeaderHintColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader, resourcesProvider));
        editText.setSingleLine(true);
        editText.setFocusable(true);
        editText.setTransformHintToHeader(true);
        editText.setLineColors(Theme.getColor(Theme.key_windowBackgroundWhiteInputField, resourcesProvider), Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated, resourcesProvider), Theme.getColor(Theme.key_text_RedRegular, resourcesProvider));
        editText.setBackground(null);
        editText.setPadding(0, 0, 0, 0);
        editText.setText(initialText != null ? initialText : "");
        editText.setHintText(hintText);
        editText.setImeOptions(imeOptions);
        if (requestFocus) {
            editText.requestFocus();
        }
        return editText;
    }

    public static void showCfCredentialsDialog(BaseFragment fragment) {
        var resourcesProvider = fragment.getResourceProvider();
        var context = fragment.getParentActivity();
        var builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(getString(R.string.CloudflareCredentials));
        builder.setMessage(AndroidUtilities.replaceSingleTag(getString(R.string.CloudflareCredentialsDialog),
                -1,
                AndroidUtilities.REPLACING_TAG_TYPE_LINKBOLD,
                () -> {
                    fragment.dismissCurrentDialog();
                    Browser.openUrl(context, "https://developers.cloudflare.com/workers-ai/get-started/rest-api");
                },
                resourcesProvider));
        builder.setCustomViewOffset(0);

        var ll = new LinearLayout(context);
        ll.setOrientation(LinearLayout.VERTICAL);

        var editTextAccountId = createAndSetupEditText(
                context,
                resourcesProvider,
                NaConfig.INSTANCE.getTranscribeProviderCfAccountID().String(),
                getString(R.string.CloudflareAccountID),
                EditorInfo.IME_ACTION_NEXT,
                true
        );
        ll.addView(editTextAccountId, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 24, 0, 24, 0));

        var editTextApiToken = createAndSetupEditText(
                context,
                resourcesProvider,
                NaConfig.INSTANCE.getTranscribeProviderCfApiToken().String(),
                getString(R.string.CloudflareAPIToken),
                EditorInfo.IME_ACTION_DONE,
                false
        );
        ll.addView(editTextApiToken, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 24, 0, 24, 0));

        builder.setView(ll);
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.setPositiveButton(getString(R.string.OK), null);
        var dialog = builder.create();
        fragment.showDialog(dialog);
        var button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (button != null) {
            button.setOnClickListener(v -> {
                var accountId = editTextAccountId.getText();
                if (!TextUtils.isEmpty(accountId) && accountId.length() != 32) {
                    AndroidUtilities.shakeViewSpring(editTextAccountId, -6);
                    BotWebViewVibrationEffect.APP_ERROR.vibrate();
                    return;
                }
                var apiToken = editTextApiToken.getText();
                if (!TextUtils.isEmpty(apiToken) && apiToken.length() != 40) {
                    AndroidUtilities.shakeViewSpring(editTextApiToken, -6);
                    BotWebViewVibrationEffect.APP_ERROR.vibrate();
                    return;
                }
                NaConfig.INSTANCE.getTranscribeProviderCfAccountID().setConfigString(accountId == null ? "" : accountId.toString());
                NaConfig.INSTANCE.getTranscribeProviderCfApiToken().setConfigString(apiToken == null ? "" : apiToken.toString());
                dialog.dismiss();
            });
        }
    }

    public static void showGeminiApiKeyDialog(BaseFragment fragment) {
        var resourcesProvider = fragment.getResourceProvider();
        var context = fragment.getParentActivity();
        var builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(getString(R.string.LlmProviderGeminiKey));
        builder.setMessage(AndroidUtilities.replaceSingleTag(getString(R.string.GeminiApiKeyDialog),
                -1,
                AndroidUtilities.REPLACING_TAG_TYPE_LINKBOLD,
                () -> {
                    fragment.dismissCurrentDialog();
                    Browser.openUrl(context, "https://aistudio.google.com/app/apikey");
                },
                resourcesProvider));
        builder.setCustomViewOffset(0);

        var ll = new LinearLayout(context);
        ll.setOrientation(LinearLayout.VERTICAL);

        var editTextApiKey = createAndSetupEditText(
                context,
                resourcesProvider,
                NaConfig.INSTANCE.getTranscribeProviderGeminiApiKey().String(),
                getString(R.string.LlmApiKey),
                EditorInfo.IME_ACTION_DONE,
                true
        );
        ll.addView(editTextApiKey, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 24, 0, 24, 0));

        var editTextPrompt = createAndSetupEditText(
                context,
                resourcesProvider,
                NaConfig.INSTANCE.getTranscribeProviderGeminiPrompt().String(),
                getString(R.string.TranscribeProviderGeminiPrompt),
                EditorInfo.IME_ACTION_DONE,
                false
        );
        ll.addView(editTextPrompt, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 24, 0, 24, 0));

        builder.setView(ll);
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.setPositiveButton(getString(R.string.OK), null);
        var dialog = builder.create();
        fragment.showDialog(dialog);
        var button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (button != null) {
            button.setOnClickListener(v -> {
                var apiKey = editTextApiKey.getText();
                if (!TextUtils.isEmpty(apiKey) && (apiKey.length() != 39 || !apiKey.toString().startsWith("AIzaSy"))) {
                    AndroidUtilities.shakeViewSpring(editTextApiKey, -6);
                    BotWebViewVibrationEffect.APP_ERROR.vibrate();
                    return;
                }
                NaConfig.INSTANCE.getTranscribeProviderGeminiApiKey().setConfigString(apiKey == null ? "" : apiKey.toString());
                if (NaConfig.INSTANCE.getLlmProviderGeminiKey().String().isEmpty()) {
                    NaConfig.INSTANCE.getLlmProviderGeminiKey().setConfigString(apiKey == null ? "" : apiKey.toString());
                }
                var prompt = editTextPrompt.getText();
                NaConfig.INSTANCE.getTranscribeProviderGeminiPrompt().setConfigString(prompt == null ? "" : prompt.toString());
                dialog.dismiss();
            });
        }
    }

    public static void showOpenAiCredentialsDialog(BaseFragment fragment) {
        if (fragment == null || fragment.getParentActivity() == null) return;
        var resourcesProvider = fragment.getResourceProvider();
        var context = fragment.getParentActivity();
        var builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(getString(R.string.TranscribeProviderOpenAI));
        builder.setMessage(AndroidUtilities.replaceSingleTag(getString(R.string.OpenAiApiCredentialsDialog),
                -1,
                AndroidUtilities.REPLACING_TAG_TYPE_LINKBOLD,
                () -> {
                    fragment.dismissCurrentDialog();
                    Browser.openUrl(context, "https://ai.google.dev/gemini-api/docs/openai#audio-understanding");
                },
                resourcesProvider));
        builder.setCustomViewOffset(0);

        var ll = new LinearLayout(context);
        ll.setOrientation(LinearLayout.VERTICAL);

        var editTextApiBase = createAndSetupEditText(
                context,
                resourcesProvider,
                NaConfig.INSTANCE.getTranscribeProviderOpenAiApiBase().String(),
                getString(R.string.OpenAiApiBaseUrlHint),
                EditorInfo.IME_ACTION_NEXT,
                true
        );
        ll.addView(editTextApiBase, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 24, 0, 24, 0));

        var editTextModel = createAndSetupEditText(
                context,
                resourcesProvider,
                NaConfig.INSTANCE.getTranscribeProviderOpenAiModel().String(),
                getString(R.string.LlmModelName),
                EditorInfo.IME_ACTION_NEXT,
                false
        );
        ll.addView(editTextModel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 24, 0, 24, 0));

        var editTextApiKey = createAndSetupEditText(
                context,
                resourcesProvider,
                NaConfig.INSTANCE.getTranscribeProviderOpenAiApiKey().String(),
                getString(R.string.LlmApiKey),
                EditorInfo.IME_ACTION_DONE,
                false
        );
        ll.addView(editTextApiKey, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 24, 0, 24, 0));

        var editTextPrompt = createAndSetupEditText(
                context,
                resourcesProvider,
                NaConfig.INSTANCE.getTranscribeProviderOpenAiPrompt().String(),
                getString(R.string.TranscribeProviderGeminiPrompt),
                EditorInfo.IME_ACTION_DONE,
                false
        );
        ll.addView(editTextPrompt, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 24, 0, 24, 0));

        builder.setView(ll);
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.setPositiveButton(getString(R.string.OK), null);
        var dialog = builder.create();
        fragment.showDialog(dialog);
        var button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (button != null) {
            button.setOnClickListener(v -> {
                var apiBase = editTextApiBase.getText();
                var model = editTextModel.getText();
                var apiKey = editTextApiKey.getText();
                var prompt = editTextPrompt.getText();

                NaConfig.INSTANCE.getTranscribeProviderOpenAiApiBase().setConfigString(apiBase == null ? "" : apiBase.toString());
                NaConfig.INSTANCE.getTranscribeProviderOpenAiModel().setConfigString(model == null ? "" : model.toString());
                NaConfig.INSTANCE.getTranscribeProviderOpenAiApiKey().setConfigString(apiKey == null ? "" : apiKey.toString());
                NaConfig.INSTANCE.getTranscribeProviderOpenAiPrompt().setConfigString(prompt == null ? "" : prompt.toString());
                dialog.dismiss();
            });
        }
    }

    private static OkHttpClient getOkHttpClient() {
        if (okHttpClient == null) {
            var builder = new OkHttpClient.Builder();
            builder.connectTimeout(120, TimeUnit.SECONDS);
            builder.readTimeout(120, TimeUnit.SECONDS);
            builder.writeTimeout(120, TimeUnit.SECONDS);
            okHttpClient = builder.build();
        }
        return okHttpClient;
    }

    private static void extractAudio(String inputFilePath, String outputFilePath) throws IOException {
        var extractor = new MediaExtractor();
        extractor.setDataSource(inputFilePath);

        MediaFormat audioFormat = null;
        int audioTrackIndex = -1;
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            var format = extractor.getTrackFormat(i);
            var mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) {
                audioFormat = format;
                audioTrackIndex = i;
                break;
            }
        }

        if (audioFormat == null) {
            throw new IOException("No audio track found in " + inputFilePath);
        }

        var muxer = new MediaMuxer(outputFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        var trackIndex = muxer.addTrack(audioFormat);
        muxer.start();

        extractor.selectTrack(audioTrackIndex);

        var bufferInfo = new MediaCodec.BufferInfo();
        var buffer = ByteBuffer.allocate(65536);

        while (true) {
            var sampleSize = extractor.readSampleData(buffer, 0);
            if (sampleSize < 0) {
                break;
            }

            bufferInfo.offset = 0;
            bufferInfo.size = sampleSize;
            bufferInfo.presentationTimeUs = extractor.getSampleTime();
            bufferInfo.flags = 0;

            muxer.writeSampleData(trackIndex, buffer, bufferInfo);
            extractor.advance();
        }

        muxer.stop();
        muxer.release();
        extractor.release();
    }

    public static void sendRequest(String path, boolean video, BiConsumer<String, Exception> callback) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O &&
                (NaConfig.INSTANCE.getTranscribeProvider().Int() == TRANSCRIBE_GEMINI ||
                NaConfig.INSTANCE.getTranscribeProvider().Int() == TRANSCRIBE_OPENAI)) {
            callback.accept(null, new Exception(getString(R.string.ApiNotSupportedOnAndroidVersion)));
            return;
        }

        switch (NaConfig.INSTANCE.getTranscribeProvider().Int()) {
            case TRANSCRIBE_AUTO:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                        (!TextUtils.isEmpty(NaConfig.INSTANCE.getTranscribeProviderGeminiApiKey().String()) ||
                        !TextUtils.isEmpty(NaConfig.INSTANCE.getLlmProviderGeminiKey().String()))) {
                    requestGeminiAi(path, video, callback);
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                        !TextUtils.isEmpty(NaConfig.INSTANCE.getTranscribeProviderOpenAiApiBase().String()) &&
                        !TextUtils.isEmpty(NaConfig.INSTANCE.getTranscribeProviderOpenAiModel().String()) &&
                        !TextUtils.isEmpty(NaConfig.INSTANCE.getTranscribeProviderOpenAiApiKey().String())) {
                    requestOpenAiCompatible(path, video, callback);
                }
                else {
                    requestWorkersAi(path, video, callback);
                }
                break;
            case TRANSCRIBE_GEMINI:
                requestGeminiAi(path, video, callback);
                break;
            case TRANSCRIBE_OPENAI:
                requestOpenAiCompatible(path, video, callback);
                break;
            default:
                requestWorkersAi(path, video, callback);
        }
    }

    private static void requestWorkersAi(String path, boolean video, BiConsumer<String, Exception> callback) {
        if (TextUtils.isEmpty(NaConfig.INSTANCE.getTranscribeProviderCfAccountID().String()) || TextUtils.isEmpty(NaConfig.INSTANCE.getTranscribeProviderCfApiToken().String())) {
            callback.accept(null, new Exception(getString(R.string.CloudflareCredentialsNotSet)));
            return;
        }
        executorService.submit(() -> {
            String audioPath;
            if (video) {
                var audioFile = new File(path + ".m4a");
                try {
                    extractAudio(path, audioFile.getAbsolutePath());
                } catch (IOException e) {
                    FileLog.e(e);
                }
                audioPath = audioFile.exists() ? audioFile.getAbsolutePath() : path;
            } else {
                audioPath = path;
            }
            var client = getOkHttpClient();
            var request = new Request.Builder()
                    .url("https://api.cloudflare.com/client/v4/accounts/" + NaConfig.INSTANCE.getTranscribeProviderCfAccountID().String() + "/ai/run/@cf/openai/whisper")
                    .header("Authorization", "Bearer " + NaConfig.INSTANCE.getTranscribeProviderCfApiToken().String())
                    .post(RequestBody.create(new File(audioPath), MediaType.get(video ? "audio/m4a" : "audio/ogg")));
            try (var response = client.newCall(request.build()).execute()) {
                var body = response.body().string();
                var whisperResponse = gson.fromJson(body, WhisperResponse.class);
                if (whisperResponse.success && whisperResponse.result != null) {
                    callback.accept(whisperResponse.result.text, null);
                } else {
                    var errors = whisperResponse.errors;
                    callback.accept(null, new Exception(errors.size() == 1 ? errors.get(0).message : errors.toString()));
                }
            } catch (Exception e) {
                callback.accept(null, e);
            }
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private static void requestGeminiAi(String path, boolean video, BiConsumer<String, Exception> callback) {
        String apiKey = NaConfig.INSTANCE.getTranscribeProviderGeminiApiKey().String();
        if (TextUtils.isEmpty(apiKey)) {
            apiKey = NaConfig.INSTANCE.getLlmProviderGeminiKey().String().split(",")[0].trim();
        }
        if (TextUtils.isEmpty(apiKey)) {
            callback.accept(null, new Exception(getString(R.string.GeminiApiKeyNotSet)));
            return;
        }
        String customPrompt = NaConfig.INSTANCE.getTranscribeProviderGeminiPrompt().String();
        final String finalApiKey = apiKey;
        final String finalPrompt = customPrompt.isEmpty() ? GEMINI_PROMPT : customPrompt;
        executorService.submit(() -> {
            String audioPath;
            try {
                if (video) {
                    var audioFile = new File(path + ".m4a");
                    try {
                        extractAudio(path, audioFile.getAbsolutePath());
                    } catch (IOException e) {
                        FileLog.e(e);
                    }
                    audioPath = audioFile.exists() ? audioFile.getAbsolutePath() : path;
                } else {
                    audioPath = path;
                }
                File audioFile = new File(audioPath);
                if (!audioFile.exists()) {
                    throw new IOException("Audio file not found: " + audioPath);
                }
                byte[] audioBytes = Files.readAllBytes(audioFile.toPath());
                String base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP);
                GeminiRequest.InlineData inlineData = new GeminiRequest.InlineData(video ? "audio/m4a" : "audio/ogg", base64Audio);
                GeminiRequest.Part audioPart = new GeminiRequest.Part(null, inlineData);
                GeminiRequest.Part textPart = new GeminiRequest.Part(finalPrompt, null);
                GeminiRequest.Content content = new GeminiRequest.Content(List.of(textPart, audioPart));
                GeminiRequest geminiRequest = new GeminiRequest(List.of(content));
                String jsonRequest = gson.toJson(geminiRequest);

                OkHttpClient client = getOkHttpClient();
                MediaType JSON = MediaType.get("application/json; charset=utf-8");
                RequestBody requestBody = RequestBody.create(jsonRequest, JSON);
                Request request = new Request.Builder()
                        .url(String.format(GEMINI_API_ENDPOINT, finalApiKey))
                        .post(requestBody)
                        .build();
                try (Response response = client.newCall(request).execute()) {
                    String responseBody = response.body().string();
                    if (!response.isSuccessful()) {
                        throw new IOException("Gemini API request failed: " + response.code() + " " + response.message() + "\nBody: " + responseBody);
                    }
                    GeminiResponse geminiResponse = gson.fromJson(responseBody, GeminiResponse.class);
                    if (geminiResponse != null && geminiResponse.candidates != null && !geminiResponse.candidates.isEmpty()) {
                        GeminiResponse.Candidate firstCandidate = geminiResponse.candidates.get(0);
                        if (firstCandidate.content != null && firstCandidate.content.parts != null && !firstCandidate.content.parts.isEmpty()) {
                            String transcribedText = firstCandidate.content.parts.stream()
                                    .filter(part -> !TextUtils.isEmpty(part.text))
                                    .map(part -> part.text)
                                    .findFirst()
                                    .orElse(null);
                            if (transcribedText != null) {
                                callback.accept(transcribedText.trim(), null);
                            } else {
                                String finishReason = firstCandidate.finishReason;
                                List<GeminiResponse.SafetyRating> safetyRatings = firstCandidate.safetyRatings;
                                String errorMsg = "Gemini response did not contain text.";
                                if (finishReason != null) errorMsg += " Finish reason: " + finishReason;
                                if (safetyRatings != null) errorMsg += " Safety Ratings: " + safetyRatings;
                                callback.accept(null, new Exception(errorMsg));
                            }
                        } else {
                            callback.accept(null, new Exception("Gemini response structure invalid (no content parts). Finish Reason: " + firstCandidate.finishReason));
                        }
                    } else if (geminiResponse != null && geminiResponse.promptFeedback != null) {
                        callback.accept(null, new Exception("Gemini prompt feedback: " + geminiResponse.promptFeedback));
                    } else {
                        callback.accept(null, new Exception("Invalid or empty response from Gemini API: " + responseBody));
                    }
                }
            } catch (Exception e) {
                FileLog.e("Gemini transcription error", e);
                callback.accept(null, e);
            }
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private static void requestOpenAiCompatible(String path, boolean video, BiConsumer<String, Exception> callback) {
        String apiBaseUrl = NaConfig.INSTANCE.getTranscribeProviderOpenAiApiBase().String();
        String model = NaConfig.INSTANCE.getTranscribeProviderOpenAiModel().String();
        String apiKey = NaConfig.INSTANCE.getTranscribeProviderOpenAiApiKey().String();
        String customPrompt = NaConfig.INSTANCE.getTranscribeProviderOpenAiPrompt().String();

        if (TextUtils.isEmpty(apiBaseUrl) || TextUtils.isEmpty(model) || TextUtils.isEmpty(apiKey)) {
            callback.accept(null, new Exception(getString(R.string.OpenAiCredentialsNotSet)));
            return;
        }

        final String finalPrompt = TextUtils.isEmpty(customPrompt) ? OPENAI_COMPATIBLE_DEFAULT_PROMPT : customPrompt;
        final String endpointUrl = apiBaseUrl.endsWith("/") ? apiBaseUrl + "chat/completions" : apiBaseUrl + "/chat/completions";

        executorService.submit(() -> {
            String audioPathToUse;
            String audioFormatForApi = "wav"; // mp3 or wav

            try {
                if (video) {
                    var audioFile = new File(path + ".m4a");
                    try {
                        extractAudio(path, audioFile.getAbsolutePath());
                    } catch (IOException e) {
                        FileLog.e("OpenAI Compatible: Audio extraction failed", e);
                    }
                    audioPathToUse = audioFile.exists() ? audioFile.getAbsolutePath() : path;
                } else {
                    audioPathToUse = path;
                }

                File audioFile = new File(audioPathToUse);
                if (!audioFile.exists()) {
                    throw new IOException("Audio file not found: " + audioPathToUse);
                }
                byte[] audioBytes = Files.readAllBytes(audioFile.toPath());
                String base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP);

                List<String> modalities = null;
                if (model.toLowerCase().startsWith("gpt")) {
                    modalities = Collections.singletonList("text");
                }

                OpenAiChatRequest.Message userMessage = getUserMessage(base64Audio, audioFormatForApi, finalPrompt);
                OpenAiChatRequest openAiRequest = new OpenAiChatRequest(model, modalities, Collections.singletonList(userMessage));
                String jsonRequest = gson.toJson(openAiRequest);

                OkHttpClient client = getOkHttpClient();
                MediaType JSON = MediaType.get("application/json; charset=utf-8");
                RequestBody requestBody = RequestBody.create(jsonRequest, JSON);
                Request request = new Request.Builder()
                        .url(endpointUrl)
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .post(requestBody)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    String responseBody = response.body().string();
                    if (!response.isSuccessful()) {
                        try {
                            OpenAiErrorResponse errorResponse = gson.fromJson(responseBody, OpenAiErrorResponse.class);
                            if (errorResponse != null && errorResponse.error != null && !TextUtils.isEmpty(errorResponse.error.message)) {
                                throw new IOException("OpenAI API request failed: " + response.code() + " " + response.message() + " - " + errorResponse.error.message);
                            }
                        } catch (Exception ignored) {}
                        throw new IOException("OpenAI API request failed: " + response.code() + " " + response.message() + "\nBody: " + responseBody);
                    }

                    OpenAiChatResponse openAiResponse = gson.fromJson(responseBody, OpenAiChatResponse.class);
                    if (openAiResponse != null && openAiResponse.choices != null && !openAiResponse.choices.isEmpty()) {
                        OpenAiChatResponse.Choice firstChoice = openAiResponse.choices.get(0);
                        if (firstChoice.message != null && !TextUtils.isEmpty(firstChoice.message.content)) {
                            callback.accept(firstChoice.message.content.trim(), null);
                        } else {
                            callback.accept(null, new Exception("OpenAI response structure invalid (no message content). Finish Reason: " + firstChoice.finishReason));
                        }
                    } else if (openAiResponse != null && openAiResponse.error != null) {
                        callback.accept(null, new Exception("OpenAI API Error: " + openAiResponse.error.message));
                    } else {
                        callback.accept(null, new Exception("Invalid or empty response from OpenAI API: " + responseBody));
                    }
                }
            } catch (Exception e) {
                FileLog.e("OpenAI compatible transcription error", e);
                callback.accept(null, e);
            }
        });
    }

    private static OpenAiChatRequest.Message getUserMessage(String base64Audio, String audioFormatForApi, String finalPrompt) {
        OpenAiChatRequest.InputAudio inputAudio = new OpenAiChatRequest.InputAudio(base64Audio, audioFormatForApi);
        OpenAiChatRequest.ContentPart textPart = new OpenAiChatRequest.ContentPart("text", finalPrompt, null);
        OpenAiChatRequest.ContentPart audioPart = new OpenAiChatRequest.ContentPart("input_audio", null, inputAudio);

        List<OpenAiChatRequest.ContentPart> contentParts = new ArrayList<>();
        contentParts.add(textPart);
        contentParts.add(audioPart);

        return new OpenAiChatRequest.Message("user", contentParts);
    }

    private static class Result {
        @SerializedName("text")
        @Expose
        public String text;
    }

    private static class WhisperResponse {
        @SerializedName("result")
        @Expose
        public Result result;
        @SerializedName("success")
        @Expose
        public Boolean success;
        @SerializedName("errors")
        @Expose
        public List<Error> errors;
    }

    private static class Error {
        @SerializedName("message")
        @Expose
        public String message;

        @NonNull
        @Override
        public String toString() {
            return message != null ? message : "Unknown error";
        }
    }

    private static class GeminiRequest {
        @SerializedName("contents")
        @Expose
        public List<Content> contents;

        public GeminiRequest(List<Content> contents) {
            this.contents = contents;
        }

        public static class Content {
            @SerializedName("parts")
            @Expose
            public List<Part> parts;

            public Content() {}
            public Content(List<Part> parts) {
                this.parts = parts;
            }
        }

        public static class Part {
            @SerializedName("text")
            @Expose
            public String text;

            @SerializedName("inlineData")
            @Expose
            public InlineData inlineData;

            public Part(String text, InlineData inlineData) {
                this.text = text;
                this.inlineData = inlineData;
            }
        }

        public static class InlineData {
            @SerializedName("mimeType")
            @Expose
            public String mimeType;

            @SerializedName("data")
            @Expose
            public String data;

            public InlineData(String mimeType, String data) {
                this.mimeType = mimeType;
                this.data = data;
            }
        }
    }

    private static class GeminiResponse {
        @SerializedName("candidates")
        @Expose
        public List<Candidate> candidates;

        @SerializedName("promptFeedback")
        @Expose
        public PromptFeedback promptFeedback;

        public static class Candidate {
            @SerializedName("content")
            @Expose
            public GeminiRequest.Content content;

            @SerializedName("finishReason")
            @Expose
            public String finishReason;

            @SerializedName("index")
            @Expose
            public Integer index;

            @SerializedName("safetyRatings")
            @Expose
            public List<SafetyRating> safetyRatings;
        }

        public static class SafetyRating {
            @SerializedName("category")
            @Expose
            public String category;
            @SerializedName("probability")
            @Expose
            public String probability;

            @NonNull
            @Override
            public String toString() {
                return category + ": " + probability;
            }
        }

        public static class PromptFeedback {
            @SerializedName("blockReason")
            @Expose
            public String blockReason;

            @SerializedName("safetyRatings")
            @Expose
            public List<SafetyRating> safetyRatings;

            @NonNull
            @Override
            public String toString() {
                return "BlockReason: " + blockReason + ", Ratings: " + safetyRatings;
            }
        }
    }

    private static class OpenAiChatRequest {
        @SerializedName("model")
        @Expose
        public String model;

        @SerializedName("modalities")
        @Expose
        @Nullable
        public List<String> modalities;

        @SerializedName("messages")
        @Expose
        public List<Message> messages;

        public OpenAiChatRequest(String model, @Nullable List<String> modalities, List<Message> messages) {
            this.model = model;
            this.modalities = modalities;
            this.messages = messages;
        }

        public static class Message {
            @SerializedName("role")
            @Expose
            public String role;

            @SerializedName("content")
            @Expose
            public List<ContentPart> content;

            public Message(String role, List<ContentPart> content) {
                this.role = role;
                this.content = content;
            }
        }

        public static class ContentPart {
            @SerializedName("type")
            @Expose
            public String type;

            @SerializedName("text")
            @Expose
            public String text;

            @SerializedName("input_audio")
            @Expose
            public InputAudio inputAudio;

            public ContentPart(String type, String text, InputAudio inputAudio) {
                this.type = type;
                this.text = text;
                this.inputAudio = inputAudio;
            }
        }

        public static class InputAudio {
            @SerializedName("data")
            @Expose
            public String data;

            @SerializedName("format")
            @Expose
            public String format;

            public InputAudio(String data, String format) {
                this.data = data;
                this.format = format;
            }
        }
    }

    private static class OpenAiChatResponse {
        @SerializedName("choices")
        @Expose
        public List<Choice> choices;

        @SerializedName("error")
        @Expose
        public OpenAiErrorResponse.ErrorDetails error;

        public static class Choice {
            @SerializedName("index")
            @Expose
            public Integer index;

            @SerializedName("message")
            @Expose
            public ResponseMessage message;

            @SerializedName("finish_reason")
            @Expose
            public String finishReason;
        }

        public static class ResponseMessage {
            @SerializedName("role")
            @Expose
            public String role;

            @SerializedName("content")
            @Expose
            public String content;
        }
    }

    private static class OpenAiErrorResponse {
        @SerializedName("error")
        @Expose
        public ErrorDetails error;

        public static class ErrorDetails {
            @SerializedName("message")
            @Expose
            public String message;
            @SerializedName("type")
            @Expose
            public String type;
            @SerializedName("param")
            @Expose
            public String param;
            @SerializedName("code")
            @Expose
            public String code;
        }
    }
}