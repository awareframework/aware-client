package com.aware.plugin.ambient_noise;

import android.content.*;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import com.aware.plugin.ambient_noise.Provider.AmbientNoise_Data;
import com.aware.utils.IContextCard;

public class ContextCard implements IContextCard {

    private TextView frequency;
    private TextView decibels;
    private TextView ambient_noise;

    /**
     * Constructor for Stream reflection
     */
    public ContextCard() {
    }

    public View getContextCard(final Context context) {

        View card = LayoutInflater.from(context).inflate(R.layout.ambient_layout, null);

        frequency = card.findViewById(R.id.frequency_hz);
        decibels = card.findViewById(R.id.decibels);
        ambient_noise = card.findViewById(R.id.ambient_noise);
        ambient_noise.setText("");

        Plugin.setSensorObserver(new Plugin.AWARESensorObserver() {
            @Override
            public void onRecording(ContentValues data) {
                context.sendBroadcast(new Intent("AMBIENT_NOISE").putExtra("data", data));
            }
        });

        IntentFilter filter = new IntentFilter("AMBIENT_NOISE");
        context.registerReceiver(audioUpdater, filter);

        return card;
    }

    private AmbientNoiseUpdater audioUpdater = new AmbientNoiseUpdater();
    public class AmbientNoiseUpdater extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            ContentValues data = intent.getParcelableExtra("data");
            frequency.setText(String.format("%.1f", data.getAsDouble(AmbientNoise_Data.FREQUENCY)) + " Hz");
            decibels.setText(String.format("%.1f", data.getAsDouble(AmbientNoise_Data.DECIBELS)) + " dB");
            ambient_noise.setText(data.getAsBoolean(AmbientNoise_Data.IS_SILENT) ? "Silent" : "Noisy");
        }
    }
}

