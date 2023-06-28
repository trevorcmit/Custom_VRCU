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

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.diasemi.bleremote.Constants;
import com.diasemi.bleremote.R;
import com.diasemi.bleremote.ui.main.MainActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

/**
 * Processes audio data received from the BleRemoteService.
 */
public class AudioManager {
    private static final String TAG = "AudioManager";

    private static final String AUDIO_FILE_NAME = "/bleaudio.wav";
    private static final String AUDIO_SAVE_PATH = "/Dialog Semiconductor/Voice RCU/";
    private static final String AUDIO_SAVE_PREFIX = "BleAudio_";
    private static final String AUDIO_RECORD_PREFIX = "RecAudio_";
    private static final SimpleDateFormat AUDIO_SAVE_DATE_FORMAT = new SimpleDateFormat("ddMMyy_HHmmss");
    private static final boolean TEST_DECODER_WITH_RAW_DATA = false;
    private static final String TEST_DECODER_RAW_DATA_FILE = "adpcm-raw";

    private boolean streamActive;
    private boolean doLiveAudio;
    private boolean doSpeechRecognition;
    private boolean audioRecordMode;
    private SpeechRecGoogle speechRecGoogle;
    private AudioGraph audioGraph;
    private AudioDecoder audioDecoder;
    private AudioTrack audioTrack;
    private WavFile wavFile = new WavFile();
    private MediaPlayer mediaPlayer;
    private String filesDir;
    private String currentFileName;
    private String audioSavePath;
    private boolean doStoreAudio = true;
    private boolean mPlayback;
    private Activity context;
    private int decodeMode;
    private boolean inbandAudioControl;
    private byte[] pendingData;
    private int totalAudioBytes;
    private int escapedAudioBytes;

    /**
     * Handler to receive the audio samples from BleRemoteService.
     * This handler is used to transfer the audio bytes from the BleRemoteService to the audio graph.
     * The mHandler is registered in the service, and for every new BLE audio packet a message is sent.
     * Processing is done on a separate thread.
     */
    private Handler mHandler;

    private class AudioHandler extends Handler {

        AudioHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(final Message msg) {
            switch (msg.what) {
                case Constants.AUDIOMANAGER_STREAM_ON:
                    if (audioRecordMode)
                        break;
                    start();
                    break;
                case Constants.AUDIOMANAGER_STREAM_OFF:
                    if (audioRecordMode)
                        break;
                    stop();
                    break;
                case Constants.AUDIOMANAGER_STREAM_IMA:
                    if (audioRecordMode)
                        break;
                    byte[] imaBytes = (byte[]) msg.obj;
                    process(imaBytes);
                    break;
                case Constants.AUDIOMANAGER_TOGGLE_PLAYBACK:
                    togglePlayback();
                    break;
                case Constants.AUDIOMANAGER_SET_MODE:
                    setDecodeMode(msg.arg1);
                    break;
                case Constants.AUDIOMANAGER_SET_FEATURES:
                    setFeatures(msg.arg1);
                    break;
                case Constants.AUDIOMANAGER_RECORDING_START:
                    if (!audioRecordMode)
                        break;
                    start();
                    break;
                case Constants.AUDIOMANAGER_RECORDING_STOP:
                    if (!audioRecordMode)
                        break;
                    stop();
                    break;
                case Constants.AUDIOMANAGER_RECORDING_DATA:
                    if (!audioRecordMode)
                        break;
                    short[] data = (short[]) msg.obj;
                    addSampleData(data);
                    break;
                case Constants.AUDIOMANAGER_RELEASE:
                    release();
                    break;
                default:
                    break;
            }
        }
    }

    public Handler getHandler() {
        return mHandler;
    }

    private void stopHandler() {
        mHandler.getLooper().quitSafely();
    }

    /**
     * Constructor
     */
    public AudioManager(Activity context) {
        Log.i(TAG, "Constructing Audio Manager");
        this.context = context;
        HandlerThread handlerThread = new HandlerThread("AudioHandler");
        handlerThread.start();
        filesDir = context.getExternalFilesDir(null).getAbsolutePath();
        audioSavePath = Environment.getExternalStorageDirectory().getAbsolutePath() + AUDIO_SAVE_PATH;
        audioDecoder = new AudioDecoder();
        audioGraph = new AudioGraph(context);
        speechRecGoogle = new SpeechRecGoogle(context);
        audioTrack = new AudioTrack(android.media.AudioManager.STREAM_MUSIC, 16000,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, 16000,
                AudioTrack.MODE_STREAM);
        mHandler = new AudioHandler(handlerThread.getLooper());
        if (TEST_DECODER_WITH_RAW_DATA)
            testDecoderWithRawData();
    }

    public void release() {
        stopHandler();
        audioTrack.release();
        if (mediaPlayer != null)
            mediaPlayer.release();
    }

    public View getAudioGraphView() {
        return audioGraph.getView();
    }

    public void repaint() {
        audioGraph.refreshGraph(true);
    }

    private void setFeatures(int features) {
        Log.d(TAG, String.format("Features: %#04x", features));
        inbandAudioControl = (features & Constants.CONTROL_FEATURES_INBAND) != 0;
        audioDecoder.setUsePartialSamples((features & Constants.CONTROL_FEATURES_NOT_PACKET_BASED) != 0);
    }

    private void setDecodeMode(int mode) {
        decodeMode = mode;
        if (mode != Constants.AUDIO_MODE_AUTOMATIC)
            audioDecoder.setMode(mode);
    }

    public void setDoLiveAudio(final boolean doLiveAudio) {
        this.doLiveAudio = doLiveAudio;
    }

    public void setDoSpeechRecognition(final boolean doSpeechRecognition) {
        this.doSpeechRecognition = doSpeechRecognition;
    }

    public void setAudioRecordMode(boolean audioRecordMode) {
        this.audioRecordMode = audioRecordMode;
    }

    public boolean isPlaybackRunning() {
        return mPlayback;
    }

    /**
     * Start of Audio Streaming
     */
    private void start() {
        Log.d(TAG, "Audio Start");
        streamActive = true;
        stopPlayback();
        pendingData = null;
        if (doStoreAudio) {
            currentFileName = filesDir + AUDIO_FILE_NAME;
            wavFile.openw(currentFileName);
        }
        if (doSpeechRecognition) {
            speechRecGoogle.start();
        }
        if (doLiveAudio) {
            audioTrack.play();
        }
        audioGraph.start();
        if (!inbandAudioControl)
            audioDecoder.reset();
        if (decodeMode != Constants.AUDIO_MODE_AUTOMATIC)
            audioDecoder.setMode(decodeMode);
    }

    /**
     * Stop of streaming
     */
    private void stop() {
        Log.d(TAG, "Audio Stop");
        if (!streamActive)
            return;
        streamActive = false;
        if (doStoreAudio) {
            wavFile.close();
        }
        if (doSpeechRecognition) {
            speechRecGoogle.stop();
        }
        if (doLiveAudio) {
            audioTrack.stop();
            audioTrack.flush();
        }
        audioGraph.stop();
        if (context.getSharedPreferences(Constants.PREFERENCES_NAME, Context.MODE_PRIVATE).getBoolean(Constants.PREF_AUTO_SAVE_AUDIO, Constants.DEFAULT_AUTO_SAVE_AUDIO)) {
            context.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    saveAudioFile();
                }
            });
        }
    }

    /**
     * Process incoming encoded audio samples.
     *
     * @param data data
     */
    private void process(final byte[] data) {
        // Ignore audio data if not active
        if (!streamActive)
            return;

        // No inband control, decode all at once
        if (!inbandAudioControl) {
            short[] imaSamples = audioDecoder.process(data);
            addSampleData(imaSamples);
            return;
        }

        // Prepend any unprocessed data to the buffer (probably an incomplete command)
        int pendingDataLength = pendingData != null ? pendingData.length : 0;
        byte[] buffer = new byte[data.length + pendingDataLength];
        if (pendingData != null) {
            System.arraycopy(pendingData, 0, buffer, 0, pendingDataLength);
            pendingData = null;
        }
        System.arraycopy(data, 0, buffer, pendingDataLength, data.length);

        // Calculate max decoded samples (3bps plus partial with upsample)
        int maxOutSamples = (buffer.length * 8 / 3 + 1) * 2;
        byte[] input = new byte[buffer.length];
        short[] output = new short[maxOutSamples];
        int inputLength = 0, outputLength = 0;
        ArrayList<Byte> command = new ArrayList<>();

        for (int i = 0; i < buffer.length; ++i) {
            int b = buffer[i] & 0xff;
            if (b == Constants.AUDIO_CONTROL_ESCAPE) {
                // Check for incomplete command
                if (!readCommand(buffer, i, command)) {
                    pendingData = Arrays.copyOfRange(buffer, i, buffer.length);
                    Log.d(TAG, "Incomplete command: " + Arrays.toString(pendingData));
                    break;
                }
                i += command.size();
                // Check for escaped audio byte
                if ((command.get(0) & 0xff) != Constants.AUDIO_CONTROL_ESCAPE) {
                    // Decode data until the control, then execute
                    if (inputLength != 0) {
                        short[] imaSamples = audioDecoder.process(Arrays.copyOf(input, inputLength));
                        System.arraycopy(imaSamples, 0, output, outputLength, imaSamples.length);
                        outputLength += imaSamples.length;
                        inputLength = 0;
                    }
                    processCommand(command);
                    continue;
                } else {
                    ++escapedAudioBytes;
                    Log.d(TAG, "Found escaped audio byte. Total: " + escapedAudioBytes + " out of " + totalAudioBytes);
                }
            }
            input[inputLength++] = buffer[i];
        }

        // Decode remaining data
        if (inputLength != 0) {
            short[] imaSamples = audioDecoder.process(Arrays.copyOf(input, inputLength));
            System.arraycopy(imaSamples, 0, output, outputLength, imaSamples.length);
            outputLength += imaSamples.length;
        }
        // Process decoded samples
        if (outputLength != 0) {
            totalAudioBytes += outputLength;
            addSampleData(Arrays.copyOf(output, outputLength));
        }
    }

    private boolean readCommand(byte[] buffer, int offset, ArrayList<Byte> command) {
        command.clear();
        if (++offset >= buffer.length)
            return false;
        byte op = buffer[offset];
        command.add(op);
        return true;
    }

    private void processCommand(ArrayList<Byte> command) {
        Log.d(TAG, "Process audio command: " + command);
        int operation = (command.get(0) & Constants.AUDIO_CONTROL_OP_MASK) >> Constants.AUDIO_CONTROL_OP_SHIFT;
        switch (operation) {
            case Constants.AUDIO_CONTROL_OP_RESET:
                audioDecoder.reset();
                break;

            case Constants.AUDIO_CONTROL_OP_SETMODE:
                int mode = command.get(0) & Constants.AUDIO_CONTROL_OP_DATA_MASK;
                audioDecoder.setMode(mode);
                break;

            default:
                Log.e(TAG, "Unknown command received");
                break;
        }
    }

    private void addSampleData(final short[] samples) {
        audioGraph.addSampleData(samples);
        if (doLiveAudio) {
            if (Build.VERSION.SDK_INT >= 23) {
                audioTrack.write(samples, 0, samples.length, AudioTrack.WRITE_NON_BLOCKING);
            } else if (Build.VERSION.SDK_INT >= 21) {
                ByteBuffer data = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
                data.asShortBuffer().put(samples).rewind();
                audioTrack.write(data, samples.length * 2, AudioTrack.WRITE_NON_BLOCKING);
            } else {
                audioTrack.write(samples, 0, samples.length);
            }
        }
        if (doStoreAudio) {
            wavFile.write(samples);
        }
        if (doSpeechRecognition) {
            speechRecGoogle.addSampleData(samples);
        }
    }

    /**
     * Playback the last audio capture.
     */
    private void togglePlayback() {
        if (currentFileName == null)
            return;
        if (!mPlayback) {
            Log.i(TAG, "Playback start: " + this.currentFileName);
            startPlayback();
        } else {
            Log.i(TAG, "Playback stop");
            stopPlayback();
        }
    }

    private void startPlayback() {
        mPlayback = true;
        broadcastUpdate(Constants.ACTION_PLAYBACK_STATE);
        try {
            if (mediaPlayer != null)
                mediaPlayer.release();
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioStreamType(android.media.AudioManager.STREAM_MUSIC);
            FileInputStream inputStream = new FileInputStream(new File(currentFileName));
            mediaPlayer.setDataSource(inputStream.getFD());
            inputStream.close();
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    if (mediaPlayer != null)
                        mediaPlayer.start();
                }
            });
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    Log.i(TAG, "Playback end");
                    stopPlayback();
                }
            });
            mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    Log.e(TAG, "Playback error: " + what + ", " + extra);
                    context.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, R.string.playback_error, Toast.LENGTH_SHORT).show();
                        }
                    });
                    return false;
                }
            });
            mediaPlayer.prepareAsync();
        } catch (IOException e) {
            Log.e(TAG, "Failed to start playback", e);
        }
    }

    private void stopPlayback() {
        mPlayback = false;
        broadcastUpdate(Constants.ACTION_PLAYBACK_STATE);
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private void broadcastUpdate(final String action) {
        Intent intent = new Intent(action);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    private boolean copyFile(File src, File dst) {
        try {
            InputStream in = new FileInputStream(src);
            OutputStream out = new FileOutputStream(dst);
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0)
                out.write(buf, 0, len);
            in.close();
            out.close();
        } catch (Exception e) {
            Log.e(TAG, "Error copying file", e);
            return false;
        }
        return true;
    }

    private class SaveAudioFileAsync extends AsyncTask<File, Void, Boolean> {

        @Override
        protected Boolean doInBackground(File... params) {
            if (params.length != 2)
                return false;
            File src = params[0];
            File dst = params[1];
            boolean copy = copyFile(src, dst);
            if (copy)
                Log.d(TAG, "Saved audio capture to: " + dst.getAbsolutePath());
            return copy;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            Toast.makeText(context, success ? R.string.audio_save_success : R.string.audio_save_failure, Toast.LENGTH_SHORT).show();
        }
    }

    public void saveAudioFile() {
        File src = currentFileName != null ? new File(currentFileName) : null;
        if (src == null || !src.exists()) {
            Log.e(TAG, "Missing audio capture file: " + currentFileName);
            return;
        }
        if (!((MainActivity)context).checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            Log.e(TAG, "Missing WRITE_EXTERNAL_STORAGE permission");
            Toast.makeText(context, R.string.audio_save_failure, Toast.LENGTH_SHORT).show();
            return;
        }
        File savePath = new File(audioSavePath);
        if (!savePath.exists() && !savePath.mkdirs()) {
            Log.e(TAG, "Failed to create audio save path: " + audioSavePath);
            return;
        }
        File dst = new File(audioSavePath + (!audioRecordMode ? AUDIO_SAVE_PREFIX : AUDIO_RECORD_PREFIX) + AUDIO_SAVE_DATE_FORMAT.format(new Date()) + ".wav");
        new SaveAudioFileAsync().execute(src, dst);
    }

    private void testDecoderWithRawData() {
        try {
            FileInputStream raw = new FileInputStream(audioSavePath + TEST_DECODER_RAW_DATA_FILE);
            Log.d(TAG, "Test audio decoder with raw data");
            setFeatures(0x0f);
            start();
            byte buf[] = new byte[raw.available()];
            raw.read(buf);
            raw.close();
            process(buf);
            stop();
        } catch (FileNotFoundException e) {
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
