/*
 * This is the source code of OctoGram for Android
 * It is licensed under GNU GPL v2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright OctoGram, 2023-2025.
 */

package tw.nekomimi.nekogram.transtale.source.raw;

import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.telegram.messenger.BuildVars;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

public class DeepLTranslatorRaw {
    private static final String NAX = "DeepLTranslatorRaw";

    private static final String API_URL = "https://www2.deepl.com/jsonrpc";
    private static final String REFERER = "https://www.deepl.com/";
    private static final String DEFAULT_SPLITTING = "newlines";

    private final AtomicLong requestId = new AtomicLong(ThreadLocalRandom.current().nextLong(10_000_000_000L));
    private static volatile String cookie;
    private static int retry429Count = 3;
    private static int retryTimeoutCount = 2;
    private static long initialRetry429Delay = 1000L;
    private static long maxRetry429Delay = 5000L;
    private static final Pattern iCountPattern = Pattern.compile("i");
    private static final String instanceId = UUID.randomUUID().toString();

    /**
     * Translates text using the DeepL API.
     *
     * @param text         The text to be translated.
     * @param fromLanguage The source language code (e.g., "en").
     * @param toLanguage   The target language code (e.g., "es").
     * @return The translated text.
     * @throws IOException   if a network error occurs.
     * @throws JSONException if there is an error parsing the JSON response.
     */
    public String translate(String text, String fromLanguage, String toLanguage) throws IOException, JSONException {
        this.requestId.incrementAndGet();

        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");

        String regionalVariant = null;
        fromLanguage = fromLanguage.toLowerCase();
        toLanguage = toLanguage.toLowerCase();
        if (toLanguage.contains("-")) {
            String[] parts = toLanguage.split("-");
            toLanguage = parts[0];
            regionalVariant = parts[0] + "-" + parts[1].toUpperCase();
        }

        JSONObject payload = new JSONObject();
        payload.put("jsonrpc", "2.0");
        payload.put("method", "LMT_handle_texts");
        payload.put("id", this.requestId.get());

        JSONObject params = new JSONObject();
        params.put("splitting", DEFAULT_SPLITTING);
        params.put("timestamp", calculateTimestamp(text));

        JSONObject lang = new JSONObject();
        lang.put("source_lang_user_selected", fromLanguage);
        lang.put("target_lang", toLanguage);
        params.put("lang", lang);

        JSONArray texts = new JSONArray();
        JSONObject textObj = new JSONObject();
        textObj.put("text", text);
        textObj.put("requestAlternatives", 0);
        texts.put(textObj);
        params.put("texts", texts);

        JSONObject commonJobParams = new JSONObject();
        commonJobParams.put("regionalVariant", regionalVariant == null ? JSONObject.NULL : regionalVariant);
        commonJobParams.put("formality", JSONObject.NULL); // TODO: add formality?
        commonJobParams.put("wasSpoken", false);
        params.put("commonJobParams", commonJobParams);

        payload.put("params", params);

        String adjustedPayload = adjustPayloadFormat(payload);

        String response = sendRequest(adjustedPayload);

        if (TextUtils.isEmpty(response)) {
            Log.e(NAX, "Empty response from DeepL API");
            throw new IOException("Empty response from DeepL API");
        }

        return extractTranslation(response);
    }

    /**
     * Calculates the timestamp for the request based on the number of 'i'
     * characters in the text.
     *
     * @param text The input text.
     * @return The calculated timestamp.
     */
    private Long calculateTimestamp(String text) {
        int iCount = 1;
        Matcher matcher = iCountPattern.matcher(text);
        while (matcher.find()) {
            iCount++;
        }
        long currentTime = System.currentTimeMillis();
        return currentTime + iCount - currentTime % iCount;
    }

    /**
     * Adjusts the format of the JSON payload based on the request ID.
     *
     * @param payload The original JSON payload.
     * @return The adjusted JSON payload as a string.
     */
    private String adjustPayloadFormat(JSONObject payload) {
        long idValue = this.requestId.get();
        if ((idValue + 3) % 13 != 0 && (idValue + 5) % 29 != 0) {
            return payload.toString().replace("hod\":\"", "hod\": \"");
        } else {
            return payload.toString().replace("hod\":\"", "hod\" : \"");
        }
    }

    /**
     * Sends the HTTP POST request to the DeepL API and handles retries with
     * exponential backoff.
     *
     * @param payload The JSON payload as a string.
     * @return The response body as a string.
     * @throws IOException if a network error occurs.
     */
    private String sendRequest(String payload) throws IOException {
        int timeoutRetries = retryTimeoutCount;
        int error429Retries = retry429Count;
        long currentRetry429Delay = initialRetry429Delay;
        boolean retryNeeded;

        do {
            retryNeeded = false;
            try {
                return executeRequest(payload);
            } catch (ConnectException | SocketTimeoutException e) {
                if (BuildVars.LOGS_ENABLED) Log.w(NAX, "Connection or timeout error", e);
                retryNeeded = true;
                if (timeoutRetries-- <= 0) {
                    throw e;
                }
            } catch (IOException e) {
                Log.e(NAX, "Error sending request", e);
                if (Objects.requireNonNull(e.getMessage()).contains("429")) {
                    retryNeeded = true;
                    if (error429Retries-- <= 0) {
                        throw e;
                    }
                    if (BuildVars.LOGS_ENABLED) Log.w(NAX, "Received 429 error, retrying in " + currentRetry429Delay + "ms");
                    try {
                        Thread.sleep(currentRetry429Delay);
                    } catch (InterruptedException ie) {
                        if (BuildVars.LOGS_ENABLED) Log.e(NAX, "Thread sleep interrupted", ie);
                        Thread.currentThread().interrupt();
                    }
                    currentRetry429Delay = Math.min(currentRetry429Delay * 2, maxRetry429Delay);
                    long jitter = (long) (currentRetry429Delay * (ThreadLocalRandom.current().nextDouble() - 0.5));
                    currentRetry429Delay = Math.max(initialRetry429Delay, currentRetry429Delay + jitter);
                } else {
                    throw e;
                }
            }
        } while (retryNeeded);

        return null; // Should ideally not reach here
    }

    /**
     * Extracts the translated text from the DeepL API response.
     *
     * @param response The response body as a string.
     * @return The translated text.
     * @throws IOException   if the response is not in the expected format.
     * @throws JSONException if there is an error parsing the JSON response.
     */
    private static String extractTranslation(String response) throws IOException, JSONException {
        JSONObject responseJson = new JSONObject(response);
        if (responseJson.has("result")) {
            JSONObject result = responseJson.getJSONObject("result");
            if (result.has("texts")) {
                JSONArray texts = result.getJSONArray("texts");
                if (texts.length() > 0) {
                    JSONObject firstText = texts.getJSONObject(0);
                    if (firstText.has("text")) {
                        return firstText.getString("text");
                    }
                }
            }
        }
        Log.e(NAX, "Invalid response format: No translation found");
        throw new IOException("Invalid response format: No translation found");
    }

    /**
     * Executes the HTTP POST request to the DeepL API.
     *
     * @param payload The JSON payload as a string.
     * @return The response body as a string.
     * @throws IOException if a network error occurs.
     */
    private String executeRequest(String payload) throws IOException {
        boolean error = false;
        HttpURLConnection connection = createConnection();
        connection.getOutputStream().write(payload.getBytes(StandardCharsets.UTF_8));
        connection.getOutputStream().flush();
        connection.getOutputStream().close();

        InputStream inputStream;
        try {
            inputStream = decompressStream(connection.getInputStream());
        } catch (IOException e) {
            error = true;
            inputStream = decompressStream(connection.getErrorStream());
        }

        if (!error) {
            Map<String, List<String>> headers = connection.getHeaderFields();
            if (cookie == null) {
                synchronized (this) {
                    if (cookie == null) {
                        cookie = Objects.requireNonNull(headers.get("Set-Cookie")).get(0);
                        cookie = cookie.substring(0, cookie.indexOf(";"));
                    }
                }
            }
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[32768];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) > 0) {
            outputStream.write(buffer, 0, bytesRead);
        }

        String responseString = outputStream.toString();
        inputStream.close();
        outputStream.close();

        if (error) {
            throw new IOException(connection.getResponseCode() + ": " + responseString);
        }

        return responseString;
    }

    /**
     * Creates and configures the HTTP connection for the DeepL API.
     *
     * @return The configured HttpURLConnection object.
     * @throws IOException if an I/O error occurs.
     */
    @NonNull
    private static HttpURLConnection createConnection() throws IOException {
        URL url = new URL(API_URL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(10000);
        connection.setRequestProperty("Referer", REFERER);
        connection.setRequestProperty("x-Instance", instanceId);
        connection.setRequestProperty("User-Agent", "DeepL-Android/1.0.1 Android 10 (aarch64)");
        connection.setRequestProperty("x-App-OS-Name", "Android");
        connection.setRequestProperty("x-App-OS-Version", "10");
        connection.setRequestProperty("x-App-Version", "1.0.1");
        connection.setRequestProperty("x-App-Build", "13");
        connection.setRequestProperty("x-App-Device", "Pixel 5");
        connection.setRequestProperty("x-App-Instance-Id", instanceId);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept-Encoding", "gzip");

        if (cookie != null) {
            connection.setRequestProperty("Cookie", cookie);
        }

        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        return connection;
    }

    /**
     * Decompresses the input stream if it is GZIP compressed.
     *
     * @param input The input stream.
     * @return The decompressed input stream.
     * @throws IOException if an I/O error occurs.
     */
    public static InputStream decompressStream(InputStream input) throws IOException {
        PushbackInputStream pushbackInputStream = new PushbackInputStream(input, 2);
        byte[] signature = new byte[2];
        int bytesRead = pushbackInputStream.read(signature);
        pushbackInputStream.unread(signature, 0, bytesRead);
        if (signature[0] == (byte) 0x1f && signature[1] == (byte) 0x8b) {
            return new GZIPInputStream(pushbackInputStream);
        } else {
            return pushbackInputStream;
        }
    }
}