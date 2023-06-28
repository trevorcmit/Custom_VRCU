/*
 *******************************************************************************
 *
 * Copyright (C) 2017 Dialog Semiconductor.
 * This computer program includes Confidential, Proprietary Information
 * of Dialog Semiconductor. All Rights Reserved.
 *
 *******************************************************************************
 */

package com.diasemi.bleremote.manager;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.diasemi.bleremote.Constants;
import com.diasemi.bleremote.Utils;
import com.diasemi.bleremote.ui.searchlist.SearchCallback;
import com.diasemi.bleremote.ui.searchlist.SearchItem;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.protocol.HTTP;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;

@SuppressWarnings("deprecation")
public class JSONManager {
    private static final String TAG = JSONManager.class.getSimpleName();

    private static final String BASE_URL = "https://www.googleapis.com/customsearch/v1";

    public static void search(final Context context, final String apiKey, final String searchQuery,
            final SearchCallback callback) {
        final StringBuilder builder = new StringBuilder();
        builder.append(BASE_URL);
        builder.append(String.format("?key=%s", Constants.CLIENT_ID));
        builder.append(String.format("&cx=%s", apiKey));
        try {
            builder.append(String.format("&q=%s", URLEncoder.encode(searchQuery, "UTF-8")));
        } catch (UnsupportedEncodingException e) {
        }
        AsyncTask<Void, Void, JSONObject> task = new AsyncTask<Void, Void, JSONObject>() {

            @Override
            protected JSONObject doInBackground(final Void... params) {
                return processGETRequest(builder.toString());
            }

            @Override
            protected void onPostExecute(final JSONObject jsonObject) {
                if (jsonObject != null && jsonObject.has("items")) {
                    ArrayList<SearchItem> searchItems = new ArrayList<>();
                    JSONArray jsonArray = jsonObject.optJSONArray("items");
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject json = jsonArray.optJSONObject(i);
                        searchItems.add(new SearchItem(json));
                        callback.onSearchCompleted(true, searchItems, null);
                    }
                } else if (jsonObject != null && jsonObject.has("error")) {
                    ArrayList<SearchItem> searchItems = new ArrayList<>();
                    ArrayList<String> errors = new ArrayList<>();
                    JSONObject error = jsonObject.optJSONObject("error");
                    String message = error.optString("message");
                    if (message != null)
                        errors.add(message);
                    callback.onSearchCompleted(false, searchItems, errors);
                }
            }
        };
        if (Utils.isConnected(context)) {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } else {
            Utils.showSettingsAlert(context, Constants.WIFI_PROVIDER);
        }
    }

    @SuppressWarnings({
            "resource"
    })
    static JSONObject processGETRequest(final String url) {
        JSONObject responseJSON = new JSONObject();
        InputStream inputStream = null;
        try {
            HttpClient client = new DefaultHttpClient(new BasicHttpParams());
            HttpConnectionParams.setConnectionTimeout(client.getParams(), 10000);
            HttpConnectionParams.setSoTimeout(client.getParams(), 10000);
            HttpGet request = new HttpGet(url);
            request.setHeader(new BasicHeader(HTTP.CONTENT_TYPE, "application/json"));
            HttpResponse response = client.execute(request);
            HttpEntity entity = response.getEntity();
            inputStream = entity.getContent();
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream, "UTF-8");
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader, 8);
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                builder.append(line + "\n");
            }
            String responseString = builder.toString()
                    .substring(0, builder.toString().length() - 1);
            responseJSON = new JSONObject(responseString);
        } catch (Exception e) {
            Log.e(TAG, "processGETRequest()", e);
            responseJSON = null;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception e) {
                    Log.e(TAG, "processGETRequest()", e);
                    responseJSON = null;
                }
            }
        }
        return responseJSON;
    }
}