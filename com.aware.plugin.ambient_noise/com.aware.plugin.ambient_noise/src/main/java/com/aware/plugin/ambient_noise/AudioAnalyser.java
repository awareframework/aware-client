package com.aware.plugin.ambient_noise;

import android.app.IntentService;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Log;

import com.aware.Aware;
import com.aware.Aware_Preferences;

import java.io.File;
import java.nio.ByteBuffer;

/**
 * Created by denzil on 31/07/16.
 */
public class AudioAnalyser extends IntentService {
    public static double sound_frequency;
    public static double sound_db;
    public static boolean is_silent;
    public static double sound_rms;

    public AudioAnalyser() {
        super(Aware.TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {

        //Check if microphone is available right now
        if(!isMicrophoneAvailable(getApplicationContext())) return;

        //Get minimum size of the buffer for pre-determined audio setup and minutes
        int buffer_size = AudioRecord.getMinBufferSize(AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_SYSTEM), AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 10;

        //Initialize audio recorder. Use MediaRecorder.AudioSource.VOICE_RECOGNITION to disable Automated Voice Gain from microphone and use DSP if available
        AudioRecord recorder = new AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_SYSTEM),
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                buffer_size);

        while (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
            //no-op while waiting microphone to initialise
        }

        if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_STOPPED) {
            recorder.startRecording();
        }

        Log.d("AWARE::Ambient Noise", "Collecting audio sample...");

        double now = System.currentTimeMillis();
        double elapsed = 0;
        while (elapsed < Integer.parseInt(Aware.getSetting(getApplicationContext(), Settings.PLUGIN_AMBIENT_NOISE_SAMPLE_SIZE)) * 1000) {
            elapsed = System.currentTimeMillis() - now;

            int realtime_buffer = AudioRecord.getMinBufferSize(AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_SYSTEM), AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 10;
            short[] realtime = new short[realtime_buffer];
            recorder.read(realtime, 0, realtime_buffer);

            AudioAnalysis audio_analysis = new AudioAnalysis(this, realtime);
            sound_rms = audio_analysis.getRMS();
            sound_frequency = audio_analysis.getFrequency();
            sound_db = audio_analysis.getdB();
            is_silent = audio_analysis.isSilent(sound_db);

            ContentValues data = new ContentValues();
            data.put(Provider.AmbientNoise_Data.TIMESTAMP, System.currentTimeMillis());
            data.put(Provider.AmbientNoise_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
            data.put(Provider.AmbientNoise_Data.FREQUENCY, sound_frequency);
            data.put(Provider.AmbientNoise_Data.DECIBELS, sound_db);
            data.put(Provider.AmbientNoise_Data.RMS, sound_rms);
            data.put(Provider.AmbientNoise_Data.IS_SILENT, is_silent);

            Log.d("AWARE::Ambient Noise", "Realtime: " + data.toString());

            data.put(Provider.AmbientNoise_Data.SILENCE_THRESHOLD, Aware.getSetting(getApplicationContext(), Settings.PLUGIN_AMBIENT_NOISE_SILENCE_THRESHOLD));
            getContentResolver().insert(Provider.AmbientNoise_Data.CONTENT_URI, data);

            if (Plugin.getSensorObserver() != null)
                Plugin.getSensorObserver().onRecording(data);
        }

        //Release microphone and stop recording
        recorder.stop();
        recorder.release();

        Log.d("AWARE::Ambient Noise", "Finished audio sample...");
    }

    /**
     * Check if the microphone is available or not
     * @param context
     * @return
     */
    public static boolean isMicrophoneAvailable(Context context) {
        MediaRecorder recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        recorder.setOutputFile(new File(context.getCacheDir(), "MediaUtil#micAvailTestFile").getAbsolutePath());
        boolean available = true;
        try {
            recorder.prepare();
            recorder.start();
        } catch (Exception exception) {
            available = false;
        }
        recorder.release();
        return available;
    }
}
