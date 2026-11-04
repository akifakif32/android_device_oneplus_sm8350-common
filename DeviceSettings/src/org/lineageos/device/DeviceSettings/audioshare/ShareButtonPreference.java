/*
 * Audio sharing state line with a Start/Stop sharing button
 */

package org.lineageos.device.DeviceSettings.audioshare;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import org.lineageos.device.DeviceSettings.R;

public class ShareButtonPreference extends Preference {

    private CharSequence mButtonText;
    // Filled accent button to start, outlined to stop
    private boolean mPrimaryStyle = true;
    private Runnable mOnButtonClick;

    public ShareButtonPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_share_button);
        setSelectable(false);
    }

    public void setButton(CharSequence text, boolean primaryStyle) {
        mButtonText = text;
        mPrimaryStyle = primaryStyle;
        notifyChanged();
    }

    public void setOnButtonClickListener(Runnable onClick) {
        mOnButtonClick = onClick;
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        Button button = (Button) holder.findViewById(R.id.share_button);
        if (button == null) return;
        button.setText(mButtonText);
        button.setBackgroundResource(mPrimaryStyle ? R.drawable.btn_primary_m3 : R.drawable.btn_secondary_m3);
        button.setTextColor(getContext().getColor(mPrimaryStyle
                ? android.R.color.white : android.R.color.holo_red_light));
        button.setEnabled(isEnabled());
        button.setAlpha(isEnabled() ? 1.0f : 0.4f);
        button.setOnClickListener(v -> {
            if (mOnButtonClick != null) mOnButtonClick.run();
        });
    }
}
