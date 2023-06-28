/*
 *******************************************************************************
 *
 * Copyright (C) 2017-2018 Dialog Semiconductor.
 * This computer program includes Confidential, Proprietary Information
 * of Dialog Semiconductor. All Rights Reserved.
 *
 *******************************************************************************
 */

package com.diasemi.bleremote.ui.main.input;

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.drawable.TransitionDrawable;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.diasemi.bleremote.BusProvider;
import com.diasemi.bleremote.Constants;
import com.diasemi.bleremote.R;
import com.diasemi.bleremote.Utils;
import com.diasemi.bleremote.audio.AudioManager;
import com.diasemi.bleremote.ui.main.MainActivity;
import com.mikepenz.google_material_typeface_library.GoogleMaterial;
import com.mikepenz.iconics.view.IconicsImageView;
import com.squareup.otto.Subscribe;

import java.util.ArrayList;
import java.util.Arrays;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;

public class InputFragment extends Fragment implements OnItemSelectedListener, OnClickListener {
    private static final String TAG = "InputFragment";
    private static final int ANDROID_SPEECH_REC_REQUEST = 1;

    @InjectView(R.id.graph_container)
    RelativeLayout mGraphContainer;
    @InjectView(R.id.save_audio_button)
    IconicsImageView saveAudioButton;
    @InjectView(R.id.pause_play_button)
    IconicsImageView pausePlayButton;
    @InjectView(R.id.spinner_variable_rate)
    Spinner mVariableRateSpinner;
    @InjectView(R.id.stream_button)
    IconicsImageView mStreamButton;
    @InjectView(R.id.spinner_language)
    Spinner languageSpinner;
    @InjectView(R.id.spinner_mode)
    Spinner mModeSpinner;
    @InjectView(R.id.container_command_options)
    LinearLayout mModeOptionsContainer;
    @InjectView(R.id.spinner_command)
    Spinner mCommandSpinner;
    @InjectView(R.id.checkbox_first_match)
    CheckBox mFirstMatchCheckBox;
    @InjectView(R.id.container_voice_rec)
    LinearLayout mVoiceRecContainer;
    @InjectView(R.id.checkbox_voice_rec)
    CheckBox mVoiceRecCheckBox;
    @InjectView(R.id.container_speech_rec)
    LinearLayout speechRecContainer;
    @InjectView(R.id.checkbox_speech_rec_dialog)
    CheckBox useSpeechRecDialogCheckBox;
    @InjectView(R.id.checkbox_process_speech_rec_result)
    CheckBox processSpeechRecResultCheckBox;
    @InjectView(R.id.voice_rec_text)
    TextView mVoiceRecText;
    @InjectView(R.id.bit_rate_text)
    TextView mBitRateText;
    @InjectView(R.id.audio_record_timer)
    TextView audioRecordTimer;
    @InjectView(R.id.container_audio_rec)
    LinearLayout audioRecordContainer;
    @InjectView(R.id.checkbox_audio_rec_use_ptt)
    CheckBox audioRecordUsePttCheckBox;
    private boolean viewCreated;

    private AudioManager audioManager;
    private boolean hidAudio;
    private boolean speechRecMode;
    private boolean pendingSpeechRecRequest;
    private boolean speechRecRunning;
    private boolean speechRecErrorShown;
    private SpeechRecognizer speechRecognizer;
    private boolean audioRecordMode;
    private AudioRecord audioRecord;
    private boolean recording;
    private boolean stopRecording;
    private int recordedSamples;
    private final Object recordStopSync = new Object();

    // LIFE CYCLE METHOD(S)

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_input, container, false);
        ButterKnife.inject(this, rootView);
        MainActivity activity = (MainActivity) getActivity();
        hidAudio = !activity.getBleService().isCustomProfile() && Utils.isHidDriverAvailable()
                && Utils.isHidDriverSoundCardAvailable() && Utils.isHidDriverHalAvailable();
        // Initialize UI
        SharedPreferences preferences = getActivity().getSharedPreferences(Constants.PREFERENCES_NAME, Context.MODE_PRIVATE);
        String[] values = getResources().getStringArray(R.array.bitrate_titles);
        int index = getIndex(preferences, values, Constants.PREF_LIST_BITRATE, getString(R.string.text_bitrate_default_value));
        mVariableRateSpinner.setSelection(index);
        values = getResources().getStringArray(R.array.mode_titles);
        if (!hidAudio)
            values = Arrays.copyOf(values, 2); // truncate not applicable modes
        index = getIndex(preferences, values, hidAudio ? Constants.PREF_MODE_HID_AUDIO : Constants.PREF_MODE_CUSTOM, values[0]);
        mModeSpinner.setAdapter(new ArrayAdapter<>(getActivity(), android.R.layout.simple_spinner_dropdown_item, values));
        mModeSpinner.setSelection(index);
        values = getResources().getStringArray(R.array.voice_rec_lang_codes);
        index = getIndex(preferences, values, Constants.PREF_VOICE_REC_LANG, values[0]);
        languageSpinner.setSelection(index);
        values = getResources().getStringArray(R.array.voice_command_values);
        index = getIndex(preferences, values, Constants.PREF_LIST_SEARCH_ENGINE, values[0]);
        mCommandSpinner.setSelection(index);
        boolean checked = preferences.getBoolean(Constants.PREF_CHECKBOX_VOICE_REC, false);
        mVoiceRecCheckBox.setChecked(checked);
        checked = preferences.getBoolean(Constants.PREF_CHECKBOX_FIRST_MATCH, false);
        mFirstMatchCheckBox.setChecked(checked);
        checked = preferences.getBoolean(Constants.PREF_USE_SPEECH_REC_DIALOG, true);
        useSpeechRecDialogCheckBox.setChecked(checked);
        checked = preferences.getBoolean(Constants.PREF_PROCESS_SPEECH_REC_RESULT, false);
        processSpeechRecResultCheckBox.setChecked(checked);
        checked = preferences.getBoolean(Constants.PREF_AUDIO_RECORD_USE_PTT, false);
        audioRecordUsePttCheckBox.setChecked(checked);
        return rootView;
    }

    @Override
    public void onViewCreated(final View view, final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewCreated = true;
        mVariableRateSpinner.setOnItemSelectedListener(this);
        mModeSpinner.setOnItemSelectedListener(this);
        mCommandSpinner.setOnItemSelectedListener(this);
        languageSpinner.setOnItemSelectedListener(this);
        BusProvider.getInstance().register(this);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        audioManager = ((MainActivity)getActivity()).getAudioManager();
        mGraphContainer.addView(audioManager.getAudioGraphView());
        mBitRateTextHandler = new Handler(getActivity().getMainLooper());
    }

    @Override
    public void onDestroyView() {
        viewCreated = false;
        BusProvider.getInstance().unregister(this);
        resetAudioRecord();
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        boolean connected = ((MainActivity) getActivity()).getBleService().isConnected();
        boolean streamActive = ((MainActivity) getActivity()).isStreamActive();
        if (!audioRecordMode) {
            updateStreamButton(connected, streamActive);
            mStreamButton.setEnabled(connected);
            updateUiStreamState(streamActive);
        } else {
            updateStreamButton(true, recording);
            updateUiStreamState(recording || streamActive);
        }
        audioManager.repaint();
        pausePlayButton.getIcon().icon(audioManager.isPlaybackRunning() ? GoogleMaterial.Icon.gmd_pause_circle_filled : GoogleMaterial.Icon.gmd_play_circle_filled);
    }

    public void onDeviceReady() {
        if (!viewCreated)
            return;
        if (!audioRecordMode) {
            updateStreamButton(true, false);
            mStreamButton.setEnabled(true);
        }
    }

    public void onDeviceDisconnected() {
        if (!viewCreated)
            return;
        if (!audioRecordMode) {
            updateStreamButton(false, false);
            mStreamButton.setEnabled(false);
            updateUiStreamState(false);
        }
    }

    private void checkHidDriverConnection() {
        MainActivity activity = (MainActivity) getActivity();
        if (!hidAudio || !activity.getBleService().isConnected() || Utils.getHidDriverVersion().isEmpty())
            return;
        if (!Utils.isConnectedToHidDriver(activity.getBleService().getBluetoothDeviceAddress())) {
            Log.w(TAG, "RCU is not connected to the HID driver");
            Toast.makeText(activity, R.string.no_hid_connection, Toast.LENGTH_SHORT).show();
        }
    }

    private void updateUiStreamState(boolean streamActive) {
        mVariableRateSpinner.setEnabled(!streamActive);
        mModeSpinner.setEnabled(!streamActive);
        mVoiceRecCheckBox.setEnabled(!streamActive);
        useSpeechRecDialogCheckBox.setEnabled(!streamActive);
        saveAudioButton.setEnabled(!streamActive);
        pausePlayButton.setEnabled(!streamActive);
    }

    private void updateStreamButton(boolean connected, boolean streamActive) {
        TransitionDrawable bg = (TransitionDrawable) mStreamButton.getBackground();
        final int transitionDelay = 200;
        if (connected) {
            if (streamActive) {
                mStreamButton.getIcon().icon(GoogleMaterial.Icon.gmd_mic);
                if (bg.getLevel() != 1)
                    bg.startTransition(transitionDelay);
            } else {
                mStreamButton.getIcon().icon(GoogleMaterial.Icon.gmd_mic_none);
                if (bg.getLevel() != 0)
                    bg.reverseTransition(transitionDelay);
            }
        } else {
            mStreamButton.getIcon().icon(GoogleMaterial.Icon.gmd_mic_off);
            bg.resetTransition();
        }
        bg.setLevel(connected && streamActive ? 1 : 0); // use level as indicator of current state
    }

    // Speech Recognizer

    private Intent createSpeechRecIntent() {
        SharedPreferences preferences = getActivity().getSharedPreferences(Constants.PREFERENCES_NAME, Context.MODE_PRIVATE);
        String defaultLanguage = getResources().getStringArray(R.array.voice_rec_lang_codes)[0];
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, preferences.getString(Constants.PREF_VOICE_REC_LANG, defaultLanguage));
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        //intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Press PTT button");
        return intent;
    }

    private void startSpeechRec(boolean streamActive) {
        if (!((MainActivity)getActivity()).isVisible())
            return;
        if (useSpeechRecDialogCheckBox.isChecked()) {
            if (streamActive && !pendingSpeechRecRequest) {
                Log.d(TAG, "SpeechRec: show system dialog");
                checkHidDriverConnection();
                pendingSpeechRecRequest = true;
                speechRecRunning = true;
                startActivityForResult(createSpeechRecIntent(), ANDROID_SPEECH_REC_REQUEST);
            }
        } else {
            if (streamActive) {
                // The missing permission case is handled in the error callback.
                if (!((MainActivity) getActivity()).checkPermission(Manifest.permission.RECORD_AUDIO))
                    Log.e(TAG, "Missing RECORD_AUDIO permission");
                Log.d(TAG, "SpeechRec: start");
                checkHidDriverConnection();
                speechRecRunning = true;
                speechRecErrorShown = false;
                speechRecognizer.startListening(createSpeechRecIntent());
            } else {
                if (speechRecRunning)
                    speechRecognizer.stopListening();
            }
            languageSpinner.setEnabled(!streamActive);
        }
    }

    private void stopSpeechRecStream() {
        speechRecRunning = false;
        updateStreamButton(((MainActivity)getActivity()).getBleService().isConnected(), false);
        updateUiStreamState(false);
        languageSpinner.setEnabled(true);
        if (((MainActivity)getActivity()).isStreamActive())
            BusProvider.getInstance().post(new StreamButtonEvent(false));
    }

    private void processSpeechRecResult(ArrayList<String> results) {
        if (results == null)
            return;
        Log.d(TAG, "SpeechRec results: " + results);
        String speechRecResult = results.get(0);
        mVoiceRecText.setText(speechRecResult);
        if (processSpeechRecResultCheckBox.isChecked()) {
            Intent intent = new Intent(Constants.ACTION_SPEECHREC_RESULT);
            intent.putExtra(Constants.EXTRA_DATA, speechRecResult);
            intent.putExtra(Constants.EXTRA_VALUE, "1");
            LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(intent);
        } else {
            Utils.logMessage(getActivity(), "Voice Recognition: " + speechRecResult);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ANDROID_SPEECH_REC_REQUEST) {
            pendingSpeechRecRequest = false;
            stopSpeechRecStream();
            mVoiceRecText.setText(null);
            if (resultCode == Activity.RESULT_OK) {
                processSpeechRecResult(data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS));
            } else {
                Log.d(TAG, "SpeechRec: cancelled");
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private RecognitionListener recognitionListener = new RecognitionListener() {
        @Override
        public void onReadyForSpeech(Bundle params) {
            Log.d(TAG, "SpeechRec: onReadyForSpeech");
        }

        @Override
        public void onBeginningOfSpeech() {
            Log.d(TAG, "SpeechRec: onBeginningOfSpeech");
        }

        @Override
        public void onRmsChanged(float rmsdB) {
        }

        @Override
        public void onBufferReceived(byte[] buffer) {
        }

        @Override
        public void onEndOfSpeech() {
            Log.d(TAG, "SpeechRec: onEndOfSpeech");
            stopSpeechRecStream();
        }

        @Override
        public void onError(int error) {
            Log.d(TAG, "SpeechRec: ERROR " + error);
            mVoiceRecText.setText(null);
            int msgRes = R.string.speech_rec_error;
            if (error == SpeechRecognizer.ERROR_NO_MATCH)
                msgRes = R.string.speech_rec_error_no_match;
            else if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                msgRes = ((MainActivity)getActivity()).isStreamActive() ? R.string.speech_rec_error_timeout : 0;
            else if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
                msgRes = R.string.speech_rec_error_permission;
            if (msgRes != 0 && !speechRecErrorShown) {
                speechRecErrorShown = true;
                Toast.makeText(getActivity(), msgRes, Toast.LENGTH_SHORT).show();
            }
            stopSpeechRecStream();
        }

        @Override
        public void onResults(Bundle results) {
            mVoiceRecText.setText(null);
            processSpeechRecResult(results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION));
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            ArrayList<String> speechRec = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (speechRec != null) {
                Log.d(TAG, "SpeechRec partial: " + speechRec);
                mVoiceRecText.setText(speechRec.get(0));
            }
        }

        @Override
        public void onEvent(int eventType, Bundle params) {
        }
    };

    // Audio Recorder

    private void startAudioRecord() {
        // Init AudioManager and audio recording
        final Handler handler = audioManager.getHandler();
        handler.sendMessage(handler.obtainMessage(Constants.AUDIOMANAGER_RECORDING_START));
        final int sampleRate = 16000;
        final int channels = AudioFormat.CHANNEL_IN_MONO;
        final int format = AudioFormat.ENCODING_PCM_16BIT;
        final int bufferSize = AudioRecord.getMinBufferSize(sampleRate, channels, format);
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, sampleRate, channels, format, bufferSize);
        if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED)
            audioRecord.startRecording();
        if (audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
            Toast.makeText(getActivity(), R.string.recording_failed, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Recording failed to start");
            audioRecord.release();
            audioRecord = null;
            return;
        }
        recording = true;
        stopRecording = false;
        // Audio recording in separate thread
        new Thread(new Runnable() {
            @Override
            public void run() {
                recordedSamples = 0;
                int timerUpdate = -1600; // force first update
                short data[] = new short[320];
                while (!stopRecording) {
                    if (audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                        Log.e(TAG, "Recording stopped unexpectedly");
                        break;
                    }
                    // Send recorded data to AudioManager
                    int count = audioRecord.read(data, 0, data.length);
                    recordedSamples += count;
                    handler.sendMessage(handler.obtainMessage(Constants.AUDIOMANAGER_RECORDING_DATA, Arrays.copyOf(data, count)));
                    // Update recording timer
                    if (recordedSamples - timerUpdate >= 1600) {
                        timerUpdate = recordedSamples;
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                double sec = (double) recordedSamples / sampleRate;
                                int min = (int) Math.floor(sec / 60);
                                sec -= min * 60;
                                audioRecordTimer.setText(String.format("%02d:%04.1f", min, sec));
                            }
                        });
                    }
                }
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
                synchronized (recordStopSync) {
                    recording = false;
                    recordStopSync.notifyAll();
                }
            }
        }).start();
    }

    private void stopAudioRecord() {
        stopRecording = true;
        synchronized (recordStopSync) {
            while (recording)
                try {
                    recordStopSync.wait();
                } catch (InterruptedException e) {}
        }
        audioManager.getHandler().sendMessage(audioManager.getHandler().obtainMessage(Constants.AUDIOMANAGER_RECORDING_STOP));
    }

    private void toggleAudioRecord() {
        if (!recording && !((MainActivity) getActivity()).checkPermission(Manifest.permission.RECORD_AUDIO)) {
            Log.e(TAG, "Missing RECORD_AUDIO permission");
            Toast.makeText(getActivity(), R.string.audio_rec_missing_permission, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!recording) {
            checkHidDriverConnection();
            startAudioRecord();
        } else {
            stopAudioRecord();
        }
        updateStreamButton(true, recording);
        updateUiStreamState(recording || ((MainActivity) getActivity()).isStreamActive());
    }

    private void resetAudioRecord() {
        if (!audioRecordMode)
            return;
        stopAudioRecord();
        audioRecordMode = false;
        audioManager.setAudioRecordMode(false);
        boolean connected = ((MainActivity)getActivity()).getBleService().isConnected();
        updateStreamButton(connected, false);
        mStreamButton.setEnabled(connected);
        updateUiStreamState(false);
        audioRecordTimer.setText(null);
        audioRecordTimer.setVisibility(View.GONE);
    }

    @Override
    public void onItemSelected(final AdapterView<?> adapterView, final View view, final int position, final long id) {
        SharedPreferences preferences = getActivity().getSharedPreferences(Constants.PREFERENCES_NAME, Context.MODE_PRIVATE);
        Editor editor = preferences.edit();
        String[] values;
        switch (adapterView.getId()) {
            case R.id.spinner_variable_rate:
                values = getResources().getStringArray(R.array.bitrate_titles);
                editor.putString(Constants.PREF_LIST_BITRATE, values[position]);
                ((MainActivity)getActivity()).setDecodeMode(position);
                break;
            case R.id.spinner_mode:
                values = getResources().getStringArray(R.array.mode_titles);
                editor.putString(hidAudio ? Constants.PREF_MODE_HID_AUDIO : Constants.PREF_MODE_CUSTOM, values[position]);
                mVoiceRecText.setText(null);
                speechRecMode = false;
                resetAudioRecord();
                switch (position) {
                    case 0: // Live capture
                        mModeOptionsContainer.setVisibility(View.GONE);
                        mVoiceRecContainer.setVisibility(View.VISIBLE);
                        speechRecContainer.setVisibility(View.GONE);
                        audioRecordContainer.setVisibility(View.GONE);
                        audioManager.setDoLiveAudio(true);
                        audioManager.setDoSpeechRecognition(mVoiceRecCheckBox.isChecked());
                        break;
                    case 1: // Voice command
                        mModeOptionsContainer.setVisibility(View.VISIBLE);
                        mVoiceRecContainer.setVisibility(View.GONE);
                        speechRecContainer.setVisibility(View.GONE);
                        audioRecordContainer.setVisibility(View.GONE);
                        audioManager.setDoLiveAudio(false);
                        audioManager.setDoSpeechRecognition(true);
                        break;
                    case 2: // Speech recognition
                        mModeOptionsContainer.setVisibility(processSpeechRecResultCheckBox.isChecked() ? View.VISIBLE : View.GONE);
                        mVoiceRecContainer.setVisibility(View.GONE);
                        speechRecContainer.setVisibility(View.VISIBLE);
                        audioRecordContainer.setVisibility(View.GONE);
                        audioManager.setDoLiveAudio(false);
                        audioManager.setDoSpeechRecognition(false);
                        speechRecMode = true;
                        if (speechRecognizer != null)
                            speechRecognizer.destroy();
                        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(getActivity());
                        speechRecognizer.setRecognitionListener(recognitionListener);
                        break;
                    case 3: // Audio record
                        mModeOptionsContainer.setVisibility(View.GONE);
                        mVoiceRecContainer.setVisibility(View.VISIBLE);
                        speechRecContainer.setVisibility(View.GONE);
                        audioRecordContainer.setVisibility(View.VISIBLE);
                        audioRecordMode = true;
                        audioManager.setAudioRecordMode(true);
                        audioManager.setDoLiveAudio(false);
                        audioManager.setDoSpeechRecognition(mVoiceRecCheckBox.isChecked());
                        updateStreamButton(true, false);
                        mStreamButton.setEnabled(true);
                        updateUiStreamState(((MainActivity)getActivity()).isStreamActive());
                        audioRecordTimer.setText(null);
                        audioRecordTimer.setVisibility(View.VISIBLE);
                        break;
                    default:
                        break;
                }
                if (!speechRecMode && speechRecognizer != null) {
                    speechRecognizer.destroy();
                    speechRecognizer = null;
                }
                break;
            case R.id.spinner_language:
                values = getResources().getStringArray(R.array.voice_rec_lang_codes);
                editor.putString(Constants.PREF_VOICE_REC_LANG, values[position]);
                break;
            case R.id.spinner_command:
                values = getResources().getStringArray(R.array.voice_command_values);
                editor.putString(Constants.PREF_LIST_SEARCH_ENGINE, values[position]);
                break;
            default:
                break;
        }
        editor.apply();
    }

    @Override
    public void onNothingSelected(final AdapterView<?> adapterView) {
        switch (adapterView.getId()) {
            case R.id.spinner_variable_rate:
                break;
            case R.id.spinner_mode:
                break;
            case R.id.spinner_language:
                break;
            case R.id.spinner_command:
                break;
            default:
                break;
        }
    }

    // View.OnClickListener METHOD(S)

    @OnClick({R.id.stream_button, R.id.checkbox_first_match, R.id.pause_play_button, R.id.save_audio_button, R.id.checkbox_voice_rec,
            R.id.checkbox_speech_rec_dialog, R.id.checkbox_process_speech_rec_result, R.id.checkbox_audio_rec_use_ptt})
    @Override
    public void onClick(final View view) {
        SharedPreferences preferences = getActivity().getSharedPreferences(Constants.PREFERENCES_NAME, Context.MODE_PRIVATE);
        CheckBox checkBox;
        switch (view.getId()) {
            case R.id.stream_button:
                if (audioRecordMode) {
                    toggleAudioRecord();
                    return;
                }
                boolean streamActive = !((MainActivity)getActivity()).isStreamActive();
                if (speechRecMode)
                    startSpeechRec(streamActive);
                updateStreamButton(true, streamActive);
                updateUiStreamState(streamActive);
                BusProvider.getInstance().post(new StreamButtonEvent(streamActive));
                break;
            case R.id.checkbox_voice_rec:
                checkBox = (CheckBox) view;
                preferences.edit().putBoolean(Constants.PREF_CHECKBOX_VOICE_REC, checkBox.isChecked()).apply();
                audioManager.setDoSpeechRecognition(checkBox.isChecked());
                mVoiceRecText.setText(null);
                break;
            case R.id.checkbox_first_match:
                checkBox = (CheckBox) view;
                preferences.edit().putBoolean(Constants.PREF_CHECKBOX_FIRST_MATCH, checkBox.isChecked()).apply();
                break;
            case R.id.checkbox_speech_rec_dialog:
                checkBox = (CheckBox) view;
                preferences.edit().putBoolean(Constants.PREF_USE_SPEECH_REC_DIALOG, checkBox.isChecked()).apply();
                break;
            case R.id.checkbox_process_speech_rec_result:
                checkBox = (CheckBox) view;
                preferences.edit().putBoolean(Constants.PREF_PROCESS_SPEECH_REC_RESULT, checkBox.isChecked()).apply();
                mModeOptionsContainer.setVisibility(checkBox.isChecked() ? View.VISIBLE : View.GONE);
                break;
            case R.id.checkbox_audio_rec_use_ptt:
                checkBox = (CheckBox) view;
                preferences.edit().putBoolean(Constants.PREF_AUDIO_RECORD_USE_PTT, checkBox.isChecked()).apply();
                break;
            case R.id.pause_play_button:
                audioManager.getHandler().sendEmptyMessage(Constants.AUDIOMANAGER_TOGGLE_PLAYBACK);
                break;
            case R.id.save_audio_button:
                audioManager.saveAudioFile();
                break;
            default:
                break;
        }
    }

    // SUBSCRIBED METHOD(S)

    @Subscribe
    public void onStreamControlEvent(final StreamControlEvent event) {
        boolean enabled = event.getPacket()[Constants.CONTROL_STREAM_ENABLE_OFFSET] != 0;
        if (audioRecordMode) {
            updateUiStreamState(enabled || recording);
            if (audioRecordUsePttCheckBox.isChecked() && (!recording && enabled || recording && !enabled))
                toggleAudioRecord();
            return;
        }
        if (!pendingSpeechRecRequest) {
            updateStreamButton(true, enabled);
            updateUiStreamState(enabled);
        }
        if (speechRecMode)
            startSpeechRec(enabled);
    }

    @Subscribe
    public void onBitRateReport(final BitRateEvent event) {
        mBitRateText.setText(String.format("%d bps", event.getBitRate()));
        mBitRateTextHandler.removeCallbacks(mBitRateClear);
        mBitRateTextHandler.postDelayed(mBitRateClear, BITRATE_DISPLAY_TIME);
    }

    @Subscribe
    public void onPlayBackStateEvent(final PlayBackStateEvent event) {
        pausePlayButton.getIcon().icon(audioManager.isPlaybackRunning() ? GoogleMaterial.Icon.gmd_pause_circle_filled : GoogleMaterial.Icon.gmd_play_circle_filled);
    }

    private int getIndex(SharedPreferences preferences, String[] values, String key, String defaultValue) {
        int index = 0;
        for (int i = 0; i < values.length; i++) {
            String value = preferences.getString(key, defaultValue);
            if (values[i].equals(value)) {
                index = i;
                break;
            }
        }
        return index;
    }

    public void setVoiceRecText(String text) {
        mVoiceRecText.setText(text);
    }

    public boolean isLiveMode() {
        return mModeSpinner.getSelectedItemPosition() == 0 || mModeSpinner.getSelectedItemPosition() == 3;
    }

    private static final int BITRATE_DISPLAY_TIME = 3000;
    private Handler mBitRateTextHandler;
    private Runnable mBitRateClear = new Runnable() {
        @Override
        public void run() {
            mBitRateText.setText(null);
        }
    };
}
