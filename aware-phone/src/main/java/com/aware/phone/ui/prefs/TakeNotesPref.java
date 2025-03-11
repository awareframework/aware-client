package com.aware.phone.ui.prefs;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Handler;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.phone.R;
import com.aware.phone.ui.TakeNoteActivity;

public class TakeNotesPref extends Preference {
    private static final int REQUEST_TAKE_NOTE = 1001;

    public TakeNotesPref(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.pref_take_notes);
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        super.onCreateView(parent);
        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.pref_take_notes, parent, false);



        view.findViewById(R.id.btn_take_notes).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (getContext() instanceof Activity) {
                    Intent intent = new Intent(getContext(), TakeNoteActivity.class);
                    ((Activity) getContext()).startActivityForResult(intent, REQUEST_TAKE_NOTE);
                }
            }
        });
        return view;
    }

}

