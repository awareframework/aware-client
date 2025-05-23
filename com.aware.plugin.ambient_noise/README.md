AWARE: Ambient Noise
====================

This plugin measures the ambient noise (Hz, dB) as noisy or silent moments. It adds the daily noise exposure on the stream, showing the average dB and Hz per hour throughout the day.

# Settings
- **status_plugin_ambient_noise**: (boolean) activate/deactivate ambient noise plugin
- **frequency_plugin_ambient_noise**: (integer) interval between audio data snippets, in minutes. Recommended value is every 5 minutes or higher.
- **plugin_ambient_noise_sample_size**: (integer) For how long we collect data, in seconds
- **plugin_ambient_noise_silence_threshold**: (integer) How many dB is a noisy environment?
- **plugin_ambient_noise_no_raw**: (boolean) to enable/disable raw audio recordings. By default, and for privacy concerns, the plugin does not records the audio snippet (true).

# Broadcasts
**ACTION_AWARE_PLUGIN_AMBIENT_NOISE**
Broadcast as we classify the ambient sound, with the following extras:
- **sound_frequency**: (double) sound frequency in Hz
- **sound_db**: (double) sound decibels in dB
- **sound_rms**: (double) sound RMS (used to classify silent/not silent)
- **is_silent**: (boolean) true or false if it is silent
    
# Providers
##  Ambient Noise Data
> content://com.aware.plugin.ambient_noise.provider.ambient_noise/plugin_ambient_noise

Field | Type | Description
----- | ---- | -----------
_id | INTEGER | primary key auto-incremented
timestamp | REAL | unix timestamp in milliseconds of sample
device_id | TEXT | AWARE device ID
double_frequency | REAL | sound frequency in Hz
double_decibels	| REAL | sound decibels in dB
double_RMS | REAL |	sound RMS
is_silent |	INTEGER | 0 = not silent 1 = is silent
double_silence_threshold | REAL | the used threshold when classifying between silent vs not silent
blob_raw | BLOB | the audio snippet raw data collected
