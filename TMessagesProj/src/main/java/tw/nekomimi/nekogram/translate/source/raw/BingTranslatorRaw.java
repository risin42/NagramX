package tw.nekomimi.nekogram.translate.source.raw;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

public class BingTranslatorRaw {
    private static final String NAX = "BingTranslatorRaw";

    private static final String PREF_NAME = "bing_translator_config";
    private static final String TRANSLATOR_URL = "https://www.bing.com/translator";
    private static final String TRANSLATE_API_URL = "https://www.bing.com/ttranslatev3";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36 Edg/122.0.0.0";

    private int count = 0; // EPT

    private String ig;
    private String iid;
    private String key;
    private String token;
    private long tokenTs;
    private long tokenExpiryInterval;

    public String translate(String text, String from, String to) throws IOException {
        if (BuildVars.LOGS_ENABLED) Log.d(NAX, "Starting translation from " + from + " to " + to + ", text length: " + text.length());

        loadConfigFromPrefs();

        if (isTokenExpired()) {
            if (BuildVars.LOGS_ENABLED) Log.d(NAX, "Token expired, fetching new config");
            fetchConfig();
        }

        if (BuildVars.LOGS_ENABLED) Log.d(NAX, "performTranslation parameters - ig: " + ig + ", iid: " + iid + ", key: " + key + ", token: " + token);

        String jsonResponse = performTranslation(ig, iid, from, to, text, key, token);
        if (jsonResponse == null) {
            if (BuildVars.LOGS_ENABLED) Log.e(NAX, "Translation failed, received null response");
            throw new IOException("Failed to get translation result");
        }

        String translatedText = extractTranslatedText(jsonResponse);
        return translatedText;
    }

    private void fetchConfig() throws IOException {
        if (BuildVars.LOGS_ENABLED) Log.d(NAX, "Fetching config from Bing translator");
        HttpURLConnection connection = null;
        try {
            URL url = new URL(TRANSLATOR_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", USER_AGENT);

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line);
                }
                if (BuildVars.LOGS_ENABLED) Log.d(NAX, "Config fetched successfully");

                String html = content.toString();
                ig = extractValue(html, "(?<=IG:\")[^\"]*");
                iid = extractValue(html, "(?<=data-iid=\")[^\"]*");
                String params = extractValue(html, "(?<=params_AbusePreventionHelper = )\\[[^\\]]+\\]");
                key = extractArrayValue(params, 0);
                token = extractArrayValue(params, 1);
                tokenTs = Long.parseLong(key);
                tokenExpiryInterval = Long.parseLong(extractArrayValue(params, 2));

                saveConfigToPrefs();
            }
        } catch (IOException e) {
            if (BuildVars.LOGS_ENABLED) Log.e(NAX, "Config fetch failed, received null response", e);
            throw e;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String extractValue(String text, String regex) {
        if (BuildVars.LOGS_ENABLED) Log.d(NAX, "Extracting value using regex: " + regex);
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            String result = matcher.group(0);
            if (BuildVars.LOGS_ENABLED) Log.d(NAX, "Extracted value: " + result);
            return result;
        }
        if (BuildVars.LOGS_ENABLED) Log.e(NAX, "Failed to extract value using regex: " + regex);
        return null;
    }

    private static String extractArrayValue(String text, int index) {
        if (text == null) return null;
        try {
            String[] parts = text.substring(1, text.length() - 1).split(",");
            if (index < parts.length) {
                return parts[index].replaceAll("\"", "").trim();
            }
        } catch (Exception e) {
            Log.e(NAX, "Failed to extract array value at index " + index + " from string: '" + text + "'", e);
        }
        return null;
    }

    private String performTranslation(String ig, String iid, String from, String to, String text, String key, String token) throws IOException {
        HttpURLConnection connection = null;
        try {
            String eptIid = iid + "." + (++count);
            URL url = new URL(TRANSLATE_API_URL + "?isVertical=1&IG=" + ig + "&IID=" + eptIid + "&ref=TThis&edgepdftranslator=1");
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Referer", TRANSLATOR_URL);
            connection.setRequestProperty("Accept-Encoding", "gzip");
            connection.setDoOutput(true);

            String postData = "fromLang=" + URLEncoder.encode(from, StandardCharsets.UTF_8) +
                "&to=" + URLEncoder.encode(to, StandardCharsets.UTF_8) +
                "&text=" + URLEncoder.encode(text, StandardCharsets.UTF_8) +
                "&token=" + URLEncoder.encode(token, StandardCharsets.UTF_8) +
                "&key=" + URLEncoder.encode(key, StandardCharsets.UTF_8) +
                "&tryFetchingGenderDebiasedTranslations=true";
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = postData.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
                os.flush();
            }

            InputStream inputStream;
            boolean error = false;
            try {
                inputStream = decompressStream(connection.getInputStream());
            } catch (IOException e) {
                error = true;
                inputStream = decompressStream(connection.getErrorStream());
                if (inputStream == null) {
                    throw e;
                }
            }

            try (ByteArrayOutputStream result = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[32768];
                int length;
                while ((length = inputStream.read(buffer)) != -1) {
                    result.write(buffer, 0, length);
                }
                String response = result.toString(StandardCharsets.UTF_8.name());

                if (error) {
                    if (BuildVars.LOGS_ENABLED) Log.e(NAX, "Server returned error: " + connection.getResponseCode() + ", " + response);
                    throw new IOException("Server returned " + connection.getResponseCode() + ": " + response);
                }

                return response;
            } finally {
                inputStream.close();
            }
        } catch (SocketTimeoutException e) {
            if (BuildVars.LOGS_ENABLED) Log.e(NAX, "Connection timeout", e);
            throw new IOException("Connection timeout", e);
        } catch (IOException e) {
            if (BuildVars.LOGS_ENABLED) Log.e(NAX, "Error during translation request", e);
            throw e;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static InputStream decompressStream(InputStream input) throws IOException {
        if (input == null) return null;
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

    private String extractTranslatedText(String jsonResponse) throws IOException {
        try {
            JSONArray jsonArray = new JSONArray(jsonResponse);
            JSONObject firstObject = jsonArray.getJSONObject(0);
            JSONArray translations = firstObject.getJSONArray("translations");
            JSONObject translation = translations.getJSONObject(0);
            return translation.getString("text");
        } catch (JSONException e) {
            if (BuildVars.LOGS_ENABLED) Log.e(NAX, "Failed to parse translation response: ", e);
            throw new IOException("Failed to parse translation response", e);
        }
    }

    private boolean isTokenExpired() {
        return System.currentTimeMillis() - tokenTs > tokenExpiryInterval;
    }

    private void loadConfigFromPrefs() throws IOException {
        SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        ig = prefs.getString("ig", null);
        iid = prefs.getString("iid", null);
        key =  prefs.getString("key", null);
        token = prefs.getString("token", null);
        tokenTs = prefs.getLong("tokenTs", 0);
        tokenExpiryInterval = prefs.getLong("tokenExpiryInterval", 0);
        if (ig == null) fetchConfig();
        if (BuildVars.LOGS_ENABLED) Log.d(NAX, "loadConfigFromPrefs, ig: " + ig + ", iid: " + iid + ", key: " + key + ", token: " + token + ", tokenTs: " + tokenTs + ", tokenExpiryInterval:" + tokenExpiryInterval);
    }

    private void saveConfigToPrefs() {
        SharedPreferences.Editor editor = ApplicationLoader.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit();
        editor.putString("ig", ig);
        editor.putString("iid", iid);
        editor.putString("key", key);
        editor.putString("token", token);
        editor.putLong("tokenTs", tokenTs);
        editor.putLong("tokenExpiryInterval", tokenExpiryInterval);
        editor.apply();
        if (BuildVars.LOGS_ENABLED) Log.d(NAX, "saveConfigToPrefs, ig: " + ig + ", iid: " + iid + ", key: " + key + ", token: " + token + ", tokenTs: " + tokenTs + ", tokenExpiryInterval:" + tokenExpiryInterval);
    }
}
