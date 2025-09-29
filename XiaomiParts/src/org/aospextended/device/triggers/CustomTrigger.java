package org.aospextended.device.triggers;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import androidx.fragment.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Slog;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.KeyEvent;
import android.widget.Switch;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.Preference.OnPreferenceClickListener;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import com.android.settingslib.widget.MainSwitchPreference;
import com.android.internal.os.DeviceKeyHandler;
import android.widget.CompoundButton;

import org.aospextended.device.util.Action;
import org.aospextended.device.util.Utils;
import org.aospextended.device.KeyHandler;

import org.aospextended.device.R;
import org.aospextended.device.util.ShortcutPickerHelper;

public class CustomTrigger extends PreferenceFragmentCompat implements
        OnPreferenceChangeListener, CompoundButton.OnCheckedChangeListener,
	OnPreferenceClickListener, ShortcutPickerHelper.OnPickListener {

    private static final String TAG = "CustomTrigger";

    private static final String SETTINGS_METADATA_NAME = "com.android.settings";

    public static final String PREF_CUSTOM_TRIGGER_ENABLE = "custom_trigger_enable";
    public static final String PREF_LEFT_TRIGGER_DOUBLE_CLICK = "custom_left_trigger_double_click";
    public static final String PREF_RIGHT_TRIGGER_DOUBLE_CLICK = "custom_right_trigger_double_click";
    public static final String PREF_LEFT_TRIGGER_LONGPRESS = "custom_left_trigger_longpress";
    public static final String PREF_RIGHT_TRIGGER_LONGPRESS = "custom_right_trigger_longpress";
    public static final String KEY_TRIGGER_HAPTIC_FEEDBACK = "custom_trigger_haptic_feedback";

    private static final int DLG_SHOW_ACTION_DIALOG  = 0;
    private static final int DLG_RESET_TO_DEFAULT    = 1;

    private static final int MENU_RESET = Menu.FIRST;

    private MainSwitchPreference mEnableCustomTrigger;

    private Preference mLeftTriggerDoubleClick, mRightTriggerDoubleClick, mLeftTriggerLongpress, mRightTriggerLongpress;

    private SwitchPreference mHapticFeedback;

    private boolean mCheckPreferences;

    private ShortcutPickerHelper mPicker;
    private String mPendingkey;

    private String[] mActionEntries;
    private String[] mActionValues;

    private TriggerUtils mTriggerUtils;
    long mPrevEventTime;
    int mKeycode, mEventAction;

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {

        mPicker = new ShortcutPickerHelper(requireActivity(), this);

        mActionValues = getResources().getStringArray(R.array.action_screen_off_values);
        mActionEntries = getResources().getStringArray(R.array.action_screen_off_entries);

        initPrefs();

        setHasOptionsMenu(true);
    }

    private PreferenceScreen initPrefs() {
        PreferenceScreen prefs = getPreferenceScreen();
        if (prefs != null) {
            prefs.removeAll();
        }

        addPreferencesFromResource(R.xml.custom_trigger);

        prefs = getPreferenceScreen();

	boolean enableCustomTrigger = Utils.getIntSystem(requireActivity(), PREF_CUSTOM_TRIGGER_ENABLE, 1) == 1;

	mEnableCustomTrigger = (MainSwitchPreference) findPreference(PREF_CUSTOM_TRIGGER_ENABLE);
        mEnableCustomTrigger.setOnCheckedChangeListener(this);
        mEnableCustomTrigger.setChecked(enableCustomTrigger);

	mLeftTriggerDoubleClick = (Preference) prefs.findPreference(PREF_LEFT_TRIGGER_DOUBLE_CLICK);
	mRightTriggerDoubleClick = (Preference) prefs.findPreference(PREF_RIGHT_TRIGGER_DOUBLE_CLICK);
        mLeftTriggerLongpress = (Preference) prefs.findPreference(PREF_LEFT_TRIGGER_LONGPRESS);
        mRightTriggerLongpress = (Preference) prefs.findPreference(PREF_RIGHT_TRIGGER_LONGPRESS);

        PreferenceCategory haptic = (PreferenceCategory) prefs.findPreference("haptic");
        mHapticFeedback = (SwitchPreference) findPreference(KEY_TRIGGER_HAPTIC_FEEDBACK);
	mHapticFeedback.setEnabled(enableCustomTrigger);
        mHapticFeedback.setChecked(Utils.getIntSystem(requireActivity(), KEY_TRIGGER_HAPTIC_FEEDBACK, 1) != 0);
        mHapticFeedback.setOnPreferenceChangeListener(this);

	setPref(mLeftTriggerDoubleClick, Utils.getStringSystem(requireActivity(), PREF_LEFT_TRIGGER_DOUBLE_CLICK,
                Action.ACTION_NULL));
	setPref(mRightTriggerDoubleClick, Utils.getStringSystem(requireActivity(), PREF_RIGHT_TRIGGER_DOUBLE_CLICK,
		Action.ACTION_NULL));
        setPref(mLeftTriggerLongpress, Utils.getStringSystem(requireActivity(), PREF_LEFT_TRIGGER_LONGPRESS,
                Action.ACTION_NULL));
        setPref(mRightTriggerLongpress, Utils.getStringSystem(requireActivity(), PREF_RIGHT_TRIGGER_LONGPRESS,
                Action.ACTION_NULL));
        return prefs;
    }

    private void setPref(Preference preference, String action) {
        if (preference == null || action == null) {
            return;
        }
        preference.setSummary(getDescription(action));
        preference.setOnPreferenceClickListener(this);
    }

    private String getDescription(String action) {
        if (action == null) {
            return null;
        }
        int i = 0;
        for (String val : mActionValues) {
            if (action.equals(val)) {
                return mActionEntries[i];
            }
            i++;
        }
        return null;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key = null;
        int title = 0;
	if (preference == mLeftTriggerDoubleClick) {
            key = PREF_LEFT_TRIGGER_DOUBLE_CLICK;
            title = R.string.custom_left_trigger_double_click_title;
        } else if (preference == mRightTriggerDoubleClick) {
            key = PREF_RIGHT_TRIGGER_DOUBLE_CLICK;
            title = R.string.custom_right_trigger_double_click_title;
        } else if (preference == mLeftTriggerLongpress) {
            key = PREF_LEFT_TRIGGER_LONGPRESS;
            title = R.string.custom_left_trigger_longpress_title;
        } else if (preference == mRightTriggerLongpress) {
            key = PREF_RIGHT_TRIGGER_LONGPRESS;
            title = R.string.custom_right_trigger_longpress_title;
        }
	if (key != null) {
            showDialogInner(DLG_SHOW_ACTION_DIALOG, key, title);
            return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final String key = preference.getKey();
        if (KEY_TRIGGER_HAPTIC_FEEDBACK.equals(key)) {
                final boolean value = (boolean) newValue;
                Utils.putIntSystem(requireActivity(), KEY_TRIGGER_HAPTIC_FEEDBACK, value ? 1 : 0);
                return true;
        }
        return false;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        mEnableCustomTrigger.setChecked(isChecked);
        Utils.putIntSystem(requireActivity(), PREF_CUSTOM_TRIGGER_ENABLE, isChecked ? 1 : 0);
    }

    // Reset all entries to default.
    private void resetToDefault() {

        Utils.putIntSystem(requireActivity(), PREF_CUSTOM_TRIGGER_ENABLE, 1);

	Utils.putStringSystem(requireActivity(), PREF_LEFT_TRIGGER_DOUBLE_CLICK,
                Action.ACTION_NULL);
        Utils.putStringSystem(requireActivity(), PREF_RIGHT_TRIGGER_DOUBLE_CLICK,
                Action.ACTION_NULL);
        Utils.putStringSystem(requireActivity(), PREF_LEFT_TRIGGER_LONGPRESS,
                Action.ACTION_NULL);
        Utils.putStringSystem(requireActivity(), PREF_RIGHT_TRIGGER_LONGPRESS,
                Action.ACTION_NULL);
	mHapticFeedback.setChecked(true);
	initPrefs();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void shortcutPicked(String action,
                String description, Bitmap bmp, boolean isApplication) {
        if (mPendingkey == null || action == null) {
            return;
        }
        Utils.putStringSystem(requireActivity(), mPendingkey, action);
        initPrefs();
        mPendingkey = null;
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == ShortcutPickerHelper.REQUEST_PICK_SHORTCUT
                    || requestCode == ShortcutPickerHelper.REQUEST_PICK_APPLICATION
                    || requestCode == ShortcutPickerHelper.REQUEST_CREATE_SHORTCUT) {
                mPicker.onActivityResult(requestCode, resultCode, data);

            }
        } else {
            mPendingkey = null;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_RESET:
                    showDialogInner(DLG_RESET_TO_DEFAULT, null, 0);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.add(0, MENU_RESET, 0, R.string.reset)
                .setIcon(R.drawable.ic_settings_reset)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
    }

    private void showDialogInner(int id, String key, int title) {
        DialogFragment newFragment =
                MyAlertDialogFragment.newInstance(id, key, title);
        newFragment.setTargetFragment(this, 0);
        newFragment.show(getParentFragmentManager(), "dialog " + id);
    }

    public static class MyAlertDialogFragment extends DialogFragment {

        public static MyAlertDialogFragment newInstance(
                int id, String key, int title) {
            MyAlertDialogFragment frag = new MyAlertDialogFragment();
            Bundle args = new Bundle();
            args.putInt("id", id);
            args.putString("key", key);
            args.putInt("title", title);
            frag.setArguments(args);
            return frag;
        }

        CustomTrigger getOwner() {
            return (CustomTrigger) getTargetFragment();
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            int id = getArguments().getInt("id");
            final String key = getArguments().getString("key");
            int title = getArguments().getInt("title");
            switch (id) {
                case DLG_SHOW_ACTION_DIALOG:
                    return new AlertDialog.Builder(requireActivity())
                    .setTitle(title)
                    .setNegativeButton(R.string.cancel, null)
                    .setItems(getOwner().mActionEntries,
                        new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int item) {
                            if (getOwner().mActionValues[item]
                                    .equals(Action.ACTION_APP)) {
                                if (getOwner().mPicker != null) {
                                    getOwner().mPendingkey = key;
                                    getOwner().mPicker.pickShortcut(getOwner().getId());
                                }
                            } else {
                                    Utils.putStringSystem(getOwner().requireActivity(), key,
                                        getOwner().mActionValues[item]);
                                getOwner().initPrefs();
                            }
                        }
                    })
                    .create();
                case DLG_RESET_TO_DEFAULT:
                    return new AlertDialog.Builder(requireActivity())
                    .setTitle(R.string.reset)
                    .setMessage(R.string.reset_message)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.dlg_ok,
                        new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            getOwner().resetToDefault();
                        }
                    })
                    .create();
            }
            throw new IllegalArgumentException("unknown id " + id);
        }

        @Override
        public void onCancel(DialogInterface dialog) {
        }
    }

}
