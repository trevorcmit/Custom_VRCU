/*
 *******************************************************************************
 *
 * Copyright (C) 2017-2018 Dialog Semiconductor.
 * This computer program includes Confidential, Proprietary Information
 * of Dialog Semiconductor. All Rights Reserved.
 *
 *******************************************************************************
 */

package com.diasemi.bleremote.audio;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.diasemi.bleremote.Constants;
import com.diasemi.bleremote.R;

import org.apache.http.util.ByteArrayBuffer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;

import javax.net.ssl.HttpsURLConnection;

/**
 * Converts audio to FLAC and sends request to Google speech recognition API.
 *
 * More info at:
 * https://github.com/gillesdemey/google-speech-v2
 * Python implementation: https://pypi.python.org/pypi/SpeechRecognition/
 *
 * FLAC Encoding: Currently using built-in Android MediaCodec.
 * Other options:
 * - ffmpeg
 * - Java implementation of flac encoder: http://javaflacencoder.sourceforge.net/
 * - http://stackoverflow.com/questions/21804390/android-mediacodec-pcm-aac-encoder-pcmdecoder-in-real-time-with-correc
 */
@SuppressWarnings("deprecation")
public class SpeechRecGoogle {
    private static final String TAG = "SpeechRec";

    private static final String API_URL = "https://www.google.com/speech-api/v2/recognize?client=chromium";
    private static final String API_URL_UP = "https://www.google.com/speech-api/full-duplex/v1/up?client=chromium&interim";
    private static final String API_URL_DOWN = "https://www.google.com/speech-api/full-duplex/v1/down?client=chromium";
    private static final String API_KEY = "AIzaSyBOti4mM-6x9WDnZIjIeyEU21OpBXqWBgw";
    private static final boolean USE_REQUEST_HANDLER = true;
    private static final boolean SAVE_FLAC_OUTPUT = false;
    private static final String SAVE_FLAC_OUTPUT_NAME = "/bleaudio.flac";

    private Activity context;
    private SharedPreferences preferences;
    private MediaCodec flacEncoder;
    private ByteBuffer[] inputBuffers;
    private ByteBuffer[] outputBuffers;
    private MediaCodec.BufferInfo bufferInfo;
    private short[] samplesBuffer;
    private ByteArrayBuffer flacBuffer;
    private int currRequestID;
    private HandlerThread httpHandlerThread;
    private Handler httpHandler;

    /**
     * Constructor
     */
    public SpeechRecGoogle(Activity context) {
        this.context = context;
        preferences = context.getSharedPreferences(Constants.PREFERENCES_NAME, Context.MODE_PRIVATE);
        flacBuffer = new ByteArrayBuffer(100000);
    }

    /**
     * Send new samples to FLAC encoder. Get encoded data.
     *
     * @param samples samples
     */
    @SuppressLint("NewApi")
    public void addSampleData(short[] samples) {
        if (flacEncoder == null)
            return;
        boolean endOfStream = samples == null;
        boolean endOfStreamFlag = false;
        ByteBuffer inputBuffer;
        ByteBuffer outputBuffer;
        ByteArrayBuffer dataBuffer = new ByteArrayBuffer(10000);

        // Prepend any previous samples
        if (samplesBuffer != null) {
            short[] newSamples = null;
            if (samples != null) {
                newSamples = Arrays.copyOf(samplesBuffer, samplesBuffer.length + samples.length);
                System.arraycopy(samples, 0, newSamples, samplesBuffer.length, samples.length);
            }
            samples = newSamples != null ? newSamples : samplesBuffer;
            samplesBuffer = null;
        }

        // Write raw data to encoder
        int inputBufferIndex = -1;
        if (samples != null) {
            inputBufferIndex = flacEncoder.dequeueInputBuffer(1000);
            // Keep data if no available buffer
            if (inputBufferIndex < 0)
                samplesBuffer = samples.clone();
        }
        while (inputBufferIndex >= 0) {
            inputBuffer = getInputBuffer(inputBufferIndex);
            if (samples != null && samples.length > 0) {
                // Add data to buffer
                int length = Math.min(samples.length, inputBuffer.limit() / 2);
                for (int i = 0; i < length; i++)
                    inputBuffer.putShort(samples[i]);
                flacEncoder.queueInputBuffer(inputBufferIndex, 0, length * 2, 0, 0);
                if (length == samples.length)
                    break;
                // Send/Keep excess data
                samples = samplesBuffer = Arrays.copyOfRange(samples, length, samples.length);
                inputBufferIndex = flacEncoder.dequeueInputBuffer(1000);
            }
        }

        // Read encoded data from encoder
        // After setting the BUFFER_FLAG_END_OF_STREAM flag in the input buffer, the encoder truncates
        // the stream and we don't get an output buffer with the last data. In order to mitigate this, we
        // add some silence at the end of the stream until we get an encoded output buffer.
        int attempts = 0;
        int silence = 0;
        int outputBufferIndex = flacEncoder.dequeueOutputBuffer(bufferInfo, 0);
        while (outputBufferIndex >= 0 || endOfStream) {
            // Add silence at the end of the stream (or any remaining data)
            if (endOfStream && outputBufferIndex < 0) {
                if (!endOfStreamFlag) { // don't add anything after the end of stream flag
                    inputBufferIndex = flacEncoder.dequeueInputBuffer(1000);
                    if (inputBufferIndex >= 0) {
                        inputBuffer = getInputBuffer(inputBufferIndex);
                        int length = inputBuffer.limit() / 2;
                        for (int i = 0; i < length; i++) {
                            if (samplesBuffer != null && i < samplesBuffer.length) {
                                inputBuffer.putShort(samplesBuffer[i]);
                            } else {
                                ++silence;
                                inputBuffer.putShort((short) 0);
                            }
                        }
                        samplesBuffer = null;
                        int flags = silence > 8000 ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0;
                        flacEncoder.queueInputBuffer(inputBufferIndex, 0, length * 2, 0, flags);
                        if (flags != 0) {
                            Log.d(TAG, "End of input stream (" + silence + " silence samples)");
                            endOfStreamFlag = true;
                        }
                    }
                }
                // Retry getting an output buffer
                outputBufferIndex = flacEncoder.dequeueOutputBuffer(bufferInfo, 1000);
                if (attempts++ < 10)
                    continue;
                else
                    break;
            }

            // Read data from buffer
            Log.d(TAG, "FLAC data: " + bufferInfo.size);
            outputBuffer = getOutputBuffer(outputBufferIndex, bufferInfo.offset, bufferInfo.offset + bufferInfo.size);
            byte[] outData = new byte[bufferInfo.size];
            outputBuffer.get(outData);
            flacBuffer.append(outData, 0, outData.length);
            if (USE_REQUEST_HANDLER)
                dataBuffer.append(outData, 0, outData.length);
            flacEncoder.releaseOutputBuffer(outputBufferIndex, false);

            // Check for end of stream
            if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                Log.d(TAG, "End of output stream");
                break;
            }

            // Signal end of stream (ensure some silence has been added)
            if (endOfStream && silence > 0) {
                inputBufferIndex = flacEncoder.dequeueInputBuffer(1000);
                if (inputBufferIndex < 0)
                    break;
                flacEncoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                Log.d(TAG, "End of input stream (" + silence + " silence samples)");
                endOfStreamFlag = true;
            }

            outputBufferIndex = flacEncoder.dequeueOutputBuffer(bufferInfo, 0);
        }

        if (USE_REQUEST_HANDLER) {
            if (!dataBuffer.isEmpty() && httpHandlerThread != null && httpHandlerThread.isAlive())
                httpHandler.sendMessage(httpHandler.obtainMessage(HTTP_REQUEST_DATA, dataBuffer.toByteArray()));
        }
    }

    private ByteBuffer getInputBuffer(int index) {
        ByteBuffer buffer;
        if (Build.VERSION.SDK_INT >= 21) {
            buffer = flacEncoder.getInputBuffer(index);
        } else {
            buffer = inputBuffers[index];
            buffer.clear();
        }
        return buffer;
    }

    private ByteBuffer getOutputBuffer(int index, int position, int limit) {
        ByteBuffer buffer;
        if (Build.VERSION.SDK_INT >= 21) {
            buffer = flacEncoder.getOutputBuffer(index);
        } else {
            buffer = outputBuffers[index];
            buffer.position(position);
            buffer.limit(limit);
        }
        return buffer;
    }

    /**
     * Start the speech recognition. Initialize the FLAC Encoder.
     */
    public void start() {
        try {
            flacBuffer.clear();
            samplesBuffer = null;

            // Initialize the Codec
            flacEncoder = MediaCodec.createEncoderByType("audio/flac");
            MediaFormat format = new MediaFormat();
            format.setString(MediaFormat.KEY_MIME, "audio/flac");
            format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
            format.setInteger(MediaFormat.KEY_SAMPLE_RATE, 16000);
            //format.setInteger(MediaFormat.KEY_FLAC_COMPRESSION_LEVEL, 8);
            flacEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            flacEncoder.start();
            Log.d(TAG, "FLAC codec started");
            if (Build.VERSION.SDK_INT < 21) {
                inputBuffers = flacEncoder.getInputBuffers();
                outputBuffers = flacEncoder.getOutputBuffers();
            }
            bufferInfo = new MediaCodec.BufferInfo();

            // Initialize request handler
            if (USE_REQUEST_HANDLER) {
                httpHandlerThread = new HandlerThread("SpeechRecRequest");
                httpHandlerThread.start();
                if (preferences.getBoolean(Constants.PREF_VOICE_REC_PARTIAL, Constants.DEFAULT_VOICE_REC_PARTIAL)) {
                    httpHandler = new HttpRequestHandlerPartialResults(httpHandlerThread.getLooper());
                } else {
                    httpHandler = new HttpRequestHandler(httpHandlerThread.getLooper());
                }
                httpHandler.sendEmptyMessage(HTTP_REQUEST_START);
            }
        } catch (IOException | IllegalStateException e) {
            Log.e(TAG, "FLAC encoder init error", e);
            if (flacEncoder != null) {
                flacEncoder.release();
                flacEncoder = null;
            }
            context.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, R.string.speech_rec_codec_error, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    /**
     * End of speech recognition. Flush the FLAC encoder and send speech API HTTP request.
     */
    public void stop() {
        addSampleData(null); // flush the encoder
        if (flacEncoder != null) {
            flacEncoder.stop();
            flacEncoder.release();
            flacEncoder = null;
        }
        byte[] flacData = flacBuffer.toByteArray();
        Log.d(TAG, "FLAC data size: " + flacData.length);

        synchronized (this) { ++currRequestID; }
        if (USE_REQUEST_HANDLER) {
            if (httpHandlerThread != null && httpHandlerThread.isAlive())
                httpHandler.sendMessage(httpHandler.obtainMessage(HTTP_REQUEST_END, currRequestID, 0));
            httpHandlerThread = null;
            httpHandler = null;
        } else {
            sendRequest(flacData, currRequestID);
        }

        if (SAVE_FLAC_OUTPUT) {
            try {
                FileOutputStream flacFile = new FileOutputStream(context.getExternalFilesDir(null).getAbsolutePath() + SAVE_FLAC_OUTPUT_NAME);
                flacFile.write(flacData);
                flacFile.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String prepareGoogleSpeechRecUrl() {
        StringBuilder url = new StringBuilder(API_URL);
        String defaultLanguage = context.getResources().getStringArray(R.array.voice_rec_lang_codes)[0];
        String language = preferences.getString(Constants.PREF_VOICE_REC_LANG, defaultLanguage);
        url.append("&lang=").append(language);
        url.append("&key=").append(API_KEY);
        return url.toString();
    }

    private String prepareGoogleSpeechRecUrlUp(String pair) {
        StringBuilder url = new StringBuilder(API_URL_UP);
        String defaultLanguage = context.getResources().getStringArray(R.array.voice_rec_lang_codes)[0];
        String language = preferences.getString(Constants.PREF_VOICE_REC_LANG, defaultLanguage);
        url.append("&lang=").append(language);
        url.append("&key=").append(API_KEY);
        url.append("&pair=").append(pair);
        return url.toString();
    }

    private String prepareGoogleSpeechRecUrlDown(String pair) {
        StringBuilder url = new StringBuilder(API_URL_DOWN);
        url.append("&key=").append(API_KEY);
        url.append("&pair=").append(pair);
        return url.toString();
    }

    /**
     * Parse the JSON result struct, and get the first result returned.
     * Results are in the form:
     * {"result":[{"alternative":[{"transcript":"hello","confidence":0.98762906}],"final":true}],"result_index":0}
     * {"result":[{"alternative":[{"transcript":"123","confidence":0.92055374},{"transcript":"1 2 3"},
     *      {"transcript":"one two three"},{"transcript":"1 2 /3"},{"transcript":"12 /3"}],"final":true}],"result_index":0}
     */
    void processResult(final String result) {
        Log.d(TAG, "Google speech API response: " + (result.isEmpty() ? "EMPTY" : result));
        if (result.isEmpty()) {
            // If no result is returned, send the broadcast anyway with no data
            Intent intent = new Intent(Constants.ACTION_SPEECHREC_RESULT);
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
            return;
        }
        String speechRecResult = null;
        String speechRecConfidence = null;
        boolean speechRecFinal = false;
        try {
            JSONObject jObject = new JSONObject(result);
            // First result
            JSONArray resultArray = jObject.getJSONArray("result");
            if (resultArray.length() > 0) {
                JSONObject resultObject = resultArray.getJSONObject(0);
                JSONArray alternativeArray = resultObject.getJSONArray("alternative");
                speechRecFinal = resultObject.has("final") && resultObject.getBoolean("final");
                if (alternativeArray.length() > 0) {
                    JSONObject transcript = alternativeArray.getJSONObject(0);
                    speechRecResult = transcript.getString("transcript");
                    speechRecConfidence = transcript.has("confidence") ? transcript.getString("confidence") : "";
                }
            }
            Log.i(TAG, "Speech recognition result: " + speechRecResult);
        } catch (JSONException e) {
            Log.e(TAG, "JSONException", e);
        }

        Intent intent = new Intent(Constants.ACTION_SPEECHREC_RESULT);
        intent.putExtra(Constants.EXTRA_DATA, speechRecResult);
        intent.putExtra(Constants.EXTRA_VALUE, speechRecConfidence);
        intent.putExtra(Constants.EXTRA_PARTIAL, !speechRecFinal);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    private HttpsURLConnection createConnection() {
        String url = prepareGoogleSpeechRecUrl();
        Log.d(TAG, "Request URL: " + url);
        try {
            HttpsURLConnection connection = (HttpsURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "audio/x-flac; rate=16000");
            connection.setDoOutput(true);
            connection.setChunkedStreamingMode(0);
            connection.setInstanceFollowRedirects(true);
            return connection;
        } catch (IOException e) {
            Log.e(TAG, "createConnection failed", e);
            return null;
        }
    }

    private HttpsURLConnection[] createPartialResultsConnections() {
        String pair = String.format("%016X", new Random().nextLong());
        String urlUp = prepareGoogleSpeechRecUrlUp(pair);
        String urlDown = prepareGoogleSpeechRecUrlDown(pair);
        HttpsURLConnection[] connections = new HttpsURLConnection[2];
        Log.d(TAG, "Request URL up: " + urlUp);
        Log.d(TAG, "Request URL down: " + urlDown);
        try {
            // Audio connection
            connections[0] = (HttpsURLConnection) new URL(urlUp).openConnection();
            connections[0].setRequestMethod("POST");
            connections[0].setRequestProperty("Content-Type", "audio/x-flac; rate=16000");
            connections[0].setDoOutput(true);
            connections[0].setChunkedStreamingMode(0);
            connections[0].setInstanceFollowRedirects(true);
            // Results connection
            connections[1] = (HttpsURLConnection) new URL(urlDown).openConnection();
            connections[1].setRequestMethod("GET");
            connections[1].setInstanceFollowRedirects(true);
            return connections;
        } catch (IOException e) {
            if (connections[0] != null)
                connections[0].disconnect();
            if (connections[1] != null)
                connections[1].disconnect();
            Log.e(TAG, "createPartialResultsConnections failed", e);
            return null;
        }
    }

    private boolean checkResponse(HttpsURLConnection connection) {
        try {
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "Google speech API response code: " + responseCode);
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "HTTP error response");
                return false;
            }
            return true;
        } catch (IOException e) {
            Log.e(TAG, "checkResponse failed", e);
            return false;
        }
    }

    private String getRecognitionResult(HttpsURLConnection connection) {
        try {
            StringBuilder result = new StringBuilder();
            BufferedReader input = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line;
            for (int n = 1; (line = input.readLine()) != null; n++) {
                Log.d(TAG, "Response line " + n + ": " + line);
                // First line contains an empty result
                if (n != 1)
                    result.append(line);
            }
            input.close();
            return result.toString();
        } catch (IOException e) {
            Log.e(TAG, "getRecognitionResult failed", e);
            return "";
        }
    }

    /**
     * Sends request to Google speech API and gets the recognition result.
     *
     * @param data FLAC encoded audio data for recognition. Duration longer than 10-15 seconds not recommended.
     */
    private void sendRequest(final byte[] data, final int requestID) {
        new Thread() {
            public void run() {
                HttpsURLConnection connection = createConnection();
                if (connection == null)
                    return;
                try {
                    OutputStream output = connection.getOutputStream();
                    output.write(data);
                    output.close();

                    if (!checkResponse(connection))
                        return;

                    String result = getRecognitionResult(connection);
                    synchronized (SpeechRecGoogle.this) {
                        if (requestID == currRequestID)
                            processResult(result);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Request failed: " + e.getMessage());
                } finally {
                    connection.disconnect();
                }
            }
        }.start();
    }

    private static final int HTTP_REQUEST_START = 0;
    private static final int HTTP_REQUEST_DATA = 1;
    private static final int HTTP_REQUEST_END = 2;
    private static final int HTTP_REQUEST_STOP = 3;

    private class HttpRequestHandler extends Handler {

        private static final int TIMEOUT = 60000;

        HttpsURLConnection connection;
        OutputStream output;

        HttpRequestHandler(Looper looper) {
            super(looper);
        }

        void stop() {
            if (connection != null)
                connection.disconnect();
            getLooper().quit();
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case HTTP_REQUEST_START:
                    // Create request to Google speech API
                    Log.d(TAG, "Request start");
                    connection = createConnection();
                    if (connection == null) {
                        stop();
                        break;
                    }
                    // Timeout in case the request isn't ended properly
                    sendEmptyMessageDelayed(HTTP_REQUEST_STOP, TIMEOUT);
                    break;

                case HTTP_REQUEST_DATA:
                    // Send FLAC encoded audio data for recognition
                    try {
                        if (output == null)
                            output = connection.getOutputStream();
                        output.write((byte[]) msg.obj);
                    } catch (IOException e) {
                        Log.e(TAG, "Request failed: " + e.getMessage());
                        context.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(context, R.string.speech_rec_network_failure, Toast.LENGTH_SHORT).show();
                            }
                        });
                        stop();
                    }
                    break;

                case HTTP_REQUEST_END:
                    // Get recognition result
                    try {
                        int requestID = msg.arg1;
                        Log.d(TAG, "Request end: " + requestID);
                        if (output == null) {
                            Log.d(TAG, "No data sent");
                            stop();
                            break;
                        }
                        output.close();

                        if (!checkResponse(connection)) {
                            stop();
                            break;
                        }

                        String result = getRecognitionResult(connection);
                        synchronized (SpeechRecGoogle.this) {
                            if (requestID == currRequestID)
                                processResult(result);
                        }
                        stop();
                    } catch (IOException e) {
                        Log.e(TAG, "Request failed: " + e.getMessage());
                        stop();
                    }
                    break;

                case HTTP_REQUEST_STOP:
                    Log.d(TAG, "Request stop");
                    stop();
                    break;
            }
        }
    }

    private class HttpRequestHandlerPartialResults extends Handler {

        private static final int TIMEOUT = 60000;

        HttpsURLConnection connectionUp;
        HttpsURLConnection connectionDown;
        OutputStream output;
        int requestID;

        HttpRequestHandlerPartialResults(Looper looper) {
            super(looper);
        }

        void stop() {
            if (connectionUp != null)
                connectionUp.disconnect();
            if (connectionDown != null)
                connectionDown.disconnect();
            getLooper().quit();
        }

        // Results connection
        Runnable getRecognitionResults = new Runnable() {
            @Override
            public void run() {
                try {
                    if (!checkResponse(connectionDown)) {
                        sendEmptyMessage(HTTP_REQUEST_STOP);
                        return;
                    }
                    BufferedReader input = new BufferedReader(new InputStreamReader(connectionDown.getInputStream()));
                    String line;
                    for (int n = 1; (line = input.readLine()) != null; n++) {
                        Log.d(TAG, "Response line " + n + ": " + line);
                        synchronized (SpeechRecGoogle.this) {
                            if (requestID == 0 || requestID == currRequestID)
                                processResult(line);
                        }
                    }
                    Log.d(TAG, "Response end");
                    input.close();
                    sendEmptyMessage(HTTP_REQUEST_STOP);
                } catch (IOException e) {
                    Log.e(TAG, "getRecognitionResults failed", e);
                    sendEmptyMessage(HTTP_REQUEST_STOP);
                }
            }
        };

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case HTTP_REQUEST_START:
                    // Create request to Google speech API
                    Log.d(TAG, "Request start");
                    HttpsURLConnection[] connections = createPartialResultsConnections();
                    if (connections == null || connections[0] == null || connections[1] == null) {
                        stop();
                        break;
                    }
                    connectionUp = connections[0];
                    connectionDown = connections[1];
                    // Timeout in case the request isn't ended properly
                    sendEmptyMessageDelayed(HTTP_REQUEST_STOP, TIMEOUT);
                    break;

                case HTTP_REQUEST_DATA:
                    // Send FLAC encoded audio data for recognition
                    try {
                        if (output == null) {
                            output = connectionUp.getOutputStream();
                            new Thread(getRecognitionResults).start();
                        }
                        output.write((byte[]) msg.obj);
                    } catch (IOException e) {
                        Log.e(TAG, "Request failed: " + e.getMessage());
                        context.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(context, R.string.speech_rec_network_failure, Toast.LENGTH_SHORT).show();
                            }
                        });
                        stop();
                    }
                    break;

                case HTTP_REQUEST_END:
                    try {
                        requestID = msg.arg1;
                        Log.d(TAG, "Request end: " + requestID);
                        if (output == null) {
                            Log.d(TAG, "No data sent");
                            stop();
                            break;
                        }
                        output.close();

                        if (!checkResponse(connectionUp)) {
                            stop();
                            break;
                        }
                        connectionUp.disconnect();
                    } catch (IOException e) {
                        Log.e(TAG, "Request failed: " + e.getMessage());
                        stop();
                    }
                    break;

                case HTTP_REQUEST_STOP:
                    Log.d(TAG, "Request stop");
                    stop();
                    break;
            }
        }
    }
}
