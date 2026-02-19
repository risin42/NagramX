package tw.nekomimi.nekogram.llm.net;

import static org.telegram.messenger.LocaleController.getString;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.R;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import tw.nekomimi.nekogram.llm.utils.LlmModelUtil;
import tw.nekomimi.nekogram.utils.HttpClient;

public final class OpenAICompatClient {

    private static final OkHttpClient httpClient = HttpClient.INSTANCE.getLlmInstance();
    private static final OkHttpClient testHttpClient = httpClient.newBuilder()
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build();

    private OpenAICompatClient() {
    }

    public record LlmResponse<T>(T data, String error, long durationMs, int httpCode) {

        public boolean isSuccess() {
            return error == null;
        }
    }

    public static LlmResponse<List<String>> fetchModels(String baseUrl, String apiKey) {
        String requestBaseUrl = baseUrl != null ? baseUrl.trim() : "";
        if (requestBaseUrl.isEmpty()) {
            return new LlmResponse<>(null, "Empty base URL", 0, 0);
        }
        String key = apiKey != null ? apiKey.trim() : "";
        if (key.isEmpty()) {
            return new LlmResponse<>(null, getString(R.string.ApiKeyNotSet), 0, 0);
        }

        long start = System.currentTimeMillis();

        Request request = new Request.Builder()
                .url(requestBaseUrl + "/models")
                .header("Authorization", "Bearer " + key)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body().string();
            long duration = System.currentTimeMillis() - start;
            int code = response.code();
            if (!response.isSuccessful()) {
                return new LlmResponse<>(null, formatHttpError(code, body), duration, code);
            }
            List<String> models;
            try {
                models = parseModelIds(body);
            } catch (Exception e) {
                return new LlmResponse<>(null, "Parse error: " + e + " ; raw=" + truncate(body), duration, code);
            }
            if (isGeminiModelsEndpoint(requestBaseUrl)) {
                models = LlmModelUtil.stripModelsPrefix(models);
            }
            if (models.isEmpty()) {
                return new LlmResponse<>(null, "No models found: " + truncate(body), duration, code);
            }
            return new LlmResponse<>(models, null, duration, code);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            return new LlmResponse<>(null, e.toString(), duration, 0);
        }
    }

    public static LlmResponse<String> testChatCompletions(String baseUrl, String apiKey, String model) {
        String modelName = model != null ? model.trim() : "";
        if (modelName.isEmpty()) {
            return new LlmResponse<>(null, "Model is empty", 0, 0);
        }

        JSONObject requestJson;
        try {
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject()
                    .put("role", "user")
                    .put("content", "This is a test. Reply with a single word: OK"));
            requestJson = new JSONObject()
                    .put("model", modelName)
                    .put("messages", messages);
            if (LlmModelUtil.isReasoning(modelName)) {
                requestJson.put("reasoning_effort", LlmModelUtil.getReasoningEffort(modelName));
            } else if (LlmModelUtil.isCerebrasGlm(baseUrl, modelName)) {
                requestJson.put("disable_reasoning", true);
            }
        } catch (Exception e) {
            return new LlmResponse<>(null, e.toString(), 0, 0);
        }

        return chatCompletions(baseUrl, apiKey, requestJson.toString(), testHttpClient);
    }

    public static LlmResponse<String> chatCompletions(String baseUrl, String apiKey, String requestJson) {
        return chatCompletions(baseUrl, apiKey, requestJson, httpClient);
    }

    private static LlmResponse<String> chatCompletions(String baseUrl, String apiKey, String requestJson, OkHttpClient client) {
        String requestBaseUrl = baseUrl != null ? baseUrl.trim() : "";
        if (requestBaseUrl.isEmpty()) {
            return new LlmResponse<>(null, "Empty base URL", 0, 0);
        }
        String key = apiKey != null ? apiKey.trim() : "";
        if (key.isEmpty()) {
            return new LlmResponse<>(null, getString(R.string.ApiKeyNotSet), 0, 0);
        }

        long start = System.currentTimeMillis();

        RequestBody requestBody = RequestBody.create(requestJson, HttpClient.MEDIA_TYPE_JSON);
        Request request = new Request.Builder()
                .url(requestBaseUrl + "/chat/completions")
                .header("Authorization", "Bearer " + key)
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String body = response.body().string();
            long duration = System.currentTimeMillis() - start;
            int code = response.code();
            if (!response.isSuccessful()) {
                return new LlmResponse<>(null, formatHttpError(code, body), duration, code);
            }
            String content = parseFirstMessageContent(body);
            if (content == null || content.trim().isEmpty()) {
                return new LlmResponse<>(null, "Empty content: " + truncate(body), duration, code);
            }
            return new LlmResponse<>(content.trim(), null, duration, code);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            return new LlmResponse<>(null, e.toString(), duration, 0);
        }
    }

    private static String formatHttpError(int code, String body) {
        return String.format(Locale.ROOT, "HTTP %d : %s", code, truncate(body));
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        final int limit = 4096;
        if (s.length() <= limit) {
            return s;
        }
        return s.substring(0, limit) + "\n…(truncated)";
    }

    private static String parseFirstMessageContent(String body) {
        try {
            JSONObject json = new JSONObject(body);
            JSONArray choices = json.optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                return null;
            }
            JSONObject first = choices.getJSONObject(0);
            JSONObject message = first.optJSONObject("message");
            if (message == null) {
                return null;
            }
            return message.optString("content", null);
        } catch (Exception ignore) {
            return null;
        }
    }

    private static List<String> parseModelIds(String body) throws Exception {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        String trimmed = body != null ? body.trim() : "";
        if (trimmed.isEmpty()) {
            return new ArrayList<>();
        }

        if (trimmed.startsWith("[")) {
            JSONArray array = new JSONArray(trimmed);
            extractModelIdsFromArray(array, out);
        } else {
            JSONObject json = new JSONObject(trimmed);
            if (json.has("data") && json.get("data") instanceof JSONArray) {
                extractModelIdsFromArray(json.getJSONArray("data"), out);
            } else if (json.has("models") && json.get("models") instanceof JSONArray) {
                extractModelIdsFromArray(json.getJSONArray("models"), out);
            } else if (json.has("data") && json.get("data") instanceof JSONObject) {
                JSONObject data = json.getJSONObject("data");
                if (data.has("id")) {
                    String id = data.optString("id", "").trim();
                    if (!id.isEmpty()) out.add(id);
                }
            }
        }

        return new ArrayList<>(out);
    }

    private static void extractModelIdsFromArray(JSONArray array, LinkedHashSet<String> out) {
        for (int i = 0; i < array.length(); i++) {
            Object item = array.opt(i);
            if (item instanceof JSONObject obj) {
                String id = obj.optString("id", "").trim();
                if (!id.isEmpty()) {
                    out.add(id);
                }
            } else if (item instanceof String s) {
                String id = s.trim();
                if (!id.isEmpty()) {
                    out.add(id);
                }
            }
        }
    }

    private static boolean isGeminiModelsEndpoint(String baseUrl) {
        return baseUrl != null && baseUrl.toLowerCase(Locale.ROOT).contains("generativelanguage.googleapis.com");
    }
}
