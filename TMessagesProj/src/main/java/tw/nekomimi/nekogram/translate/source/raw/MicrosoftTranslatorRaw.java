package tw.nekomimi.nekogram.translate.source.raw;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class MicrosoftTranslatorRaw {
    private static final String NAX = "MicrosoftTranslatorRaw";

    private static final String PREF_NAME = "microsoft_translator_config";
    private static final String API_AUTH = "https://edge.microsoft.com/translate/auth";
    private static final String API_TRANSLATE = "https://api.cognitive.microsofttranslator.com/translate";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36 Edg/122.0.0.0";

    private String globalToken;
    private long tokenTimestamp;
    private static final long TOKEN_EXPIRY = 10 * 60 * 1000; // 10 minutes

    public String translate(String text, String from, String to) throws IOException {
        if (BuildVars.LOGS_ENABLED) Log.d(NAX, "Starting translation from " + from + " to " + to + ", text length: " + text.length());

        if (text == null || text.isEmpty()) {
            return "";
        }

        loadTokenFromPrefs();
        if (isTokenExpired()) {
            if (BuildVars.LOGS_ENABLED) Log.d(NAX, "Token expired, fetching new token");
            globalToken = fetchGlobalToken();
            saveTokenToPrefs();
        }

        String url = API_TRANSLATE + "?api-version=3.0&to=" + to;
        if (from != null && !from.isEmpty()) {
            url += "&from=" + from;
        }

        if (BuildVars.LOGS_ENABLED) Log.d(NAX, "Request URL: " + url);

        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("User-Agent", USER_AGENT);
        con.setRequestProperty("Authorization", "Bearer " + globalToken);
        con.setRequestProperty("Content-Type", "application/json");
        con.setDoOutput(true);

        String requestBody = "[{\"Text\":\"" + text.replace("\"", "\\\"") + "\"}]";
        if (BuildVars.LOGS_ENABLED) Log.d(NAX, "Request Body: " + requestBody);

        try (OutputStream os = con.getOutputStream()) {
            os.write(requestBody.getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = con.getResponseCode();
        if (BuildVars.LOGS_ENABLED) Log.d(NAX, "Response Code: " + responseCode);

        if (responseCode == HttpURLConnection.HTTP_OK) {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }

                if (BuildVars.LOGS_ENABLED) Log.d(NAX, "Response Body: " + response.toString());

                try {
                    JSONArray jsonArray = new JSONArray(response.toString());
                    if (jsonArray.length() > 0) {
                        return jsonArray.getJSONObject(0)
                                .getJSONArray("translations")
                                .getJSONObject(0)
                                .getString("text");
                    }
                    return "";
                } catch (JSONException e) {
                    if (BuildVars.LOGS_ENABLED) Log.e(NAX, "Failed to parse JSON response", e);
                    throw new IOException("Failed to parse translation response: " + e.getMessage());
                }
            }
        } else {
            String errorBody = "";
            try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getErrorStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                errorBody = response.toString();
            }
            if (BuildVars.LOGS_ENABLED) Log.e(NAX, "Error Response: " + errorBody);
            throw new IOException("HTTP error " + responseCode + ": " + errorBody);
        }
    }

    private String fetchGlobalToken() throws IOException {
        if (BuildVars.LOGS_ENABLED) Log.d(NAX, "Fetching global token...");

        URL obj = new URL(API_AUTH);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("User-Agent", USER_AGENT);

        int responseCode = con.getResponseCode();
        if (BuildVars.LOGS_ENABLED) Log.d(NAX, "Auth Response Code: " + responseCode);

        if (responseCode == HttpURLConnection.HTTP_OK) {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                String token = in.readLine();
                if (BuildVars.LOGS_ENABLED) Log.d(NAX, "New token received");
                tokenTimestamp = System.currentTimeMillis();
                return token;
            }
        }
        throw new IOException("Failed to fetch auth token: " + responseCode);
    }

    private void loadTokenFromPrefs() {
        SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        globalToken = prefs.getString("token", null);
        tokenTimestamp = prefs.getLong("tokenTimestamp", 0);
        if (BuildVars.LOGS_ENABLED) Log.d(NAX, "Loaded token from prefs, timestamp: " + tokenTimestamp);
    }

    private void saveTokenToPrefs() {
        SharedPreferences.Editor editor = ApplicationLoader.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit();
        editor.putString("token", globalToken);
        editor.putLong("tokenTimestamp", tokenTimestamp);
        editor.apply();
        if (BuildVars.LOGS_ENABLED) Log.d(NAX, "Saved token to prefs");
    }

    private boolean isTokenExpired() {
        return globalToken == null || System.currentTimeMillis() - tokenTimestamp > TOKEN_EXPIRY;
    }
}
