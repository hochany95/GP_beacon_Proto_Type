package net.alea.beaconsimulator;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import net.alea.beaconsimulator.bluetooth.BeaconStore;
import net.alea.beaconsimulator.bluetooth.model.BeaconModel;
import net.alea.beaconsimulator.bluetooth.model.BeaconType;
import net.alea.beaconsimulator.component.BeaconModelEditor;
import net.alea.beaconsimulator.component.ViewEditAltBeacon;
import net.alea.beaconsimulator.component.ViewEditEddystoneEid;
import net.alea.beaconsimulator.component.ViewEditEddystoneTlm;
import net.alea.beaconsimulator.component.ViewEditEddystoneUid;
import net.alea.beaconsimulator.component.ViewEditEddystoneUrl;
import net.alea.beaconsimulator.component.ViewEditIBeacon;
import net.alea.beaconsimulator.component.ViewEditSettings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class FragmentBeaconEdit extends Fragment {

    private static final Logger sLogger = LoggerFactory.getLogger(FragmentBeaconEdit.class);

    private boolean mIsNewModel = false;
    private boolean mEditMode = false;
    private BeaconModel mBeaconModel;
    private BeaconModelEditor mBeaconSpecificView;
    private ViewEditSettings mSettingsView;
    private BeaconStore mBeaconStore;
    private EditText mBeaconNameField;
    private Toolbar mToolbar;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBeaconStore = ((App)getActivity().getApplication()).getBeaconStore();
        setRetainInstance(true);
        setHasOptionsMenu(true);
        final Bundle bundle = getArguments();
        // Edit existing beacon
        if (bundle.containsKey(ActivityBeaconEdit.EXTRA_ID)) {
            UUID id = (UUID)bundle.get(ActivityBeaconEdit.EXTRA_ID);
            sLogger.debug("Editing beacon {}", id);
            mBeaconModel = mBeaconStore.getBeacon(id);
            // TODO If beaconModel null error
            mEditMode = false;
            mIsNewModel = false;
        }
        // make New Beacon
        else {
            BeaconType type = (BeaconType) bundle.get(ActivityBeaconEdit.EXTRA_TYPE);
            mBeaconModel = new BeaconModel(type);
            mEditMode = true;
            mIsNewModel = true;
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_beacon_edit, container, false);

        mBeaconNameField = (EditText)view.findViewById(R.id.beaconedit_textinput_name);
        if (mIsNewModel) {
//            mBeaconNameField.setHint(mBeaconModel.generateBeaconName());
            mBeaconNameField.setHint("registered User ID");
        }

        mToolbar = (Toolbar) view.findViewById(R.id.beaconedit_toolbar);
        ((AppCompatActivity)getActivity()).setSupportActionBar(mToolbar);
        ActionBar actionBar =  ((AppCompatActivity)getActivity()).getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(getString(R.string.beaconedition_title));
        }

        ViewGroup cardListView = (ViewGroup)view.findViewById(R.id.beaconedit_linearlayout_cardlist);
        View beaconModelView = null;
        beaconModelView = new ViewEditAltBeacon(getContext());

        if (beaconModelView != null) {
            mBeaconSpecificView = (BeaconModelEditor)beaconModelView;
        }

        inflater.inflate(R.layout.view_space, cardListView);
        mSettingsView = new ViewEditSettings(getContext());
        cardListView.addView(mSettingsView);
        inflater.inflate(R.layout.view_space, cardListView);
        enableEditMode(mEditMode);
        loadBeacon();
        ///
        boolean success = saveBeacon();
        if (success) {
            enableEditMode(false);
            mIsNewModel = false;

        }
        if (getView() != null) {
            getView().clearFocus();
        }

        return view;
    }

    @Override
    public void onPause() {
        super.onPause();
        sLogger.debug("onPause() called");
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.clear();
        if (mEditMode) {
            mToolbar.inflateMenu(R.menu.fragment_beacon_edit_edition);
        }
        else {
            mToolbar.inflateMenu(R.menu.fragment_beacon_edit_readonly);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_edit:
                enableEditMode(true);
                return true;
            case R.id.action_cancel: {
                if (mIsNewModel) {
                    leaveActivity();
                }
                else {
                    loadBeacon();
                    enableEditMode(false);
                }
                if (getView() != null) {
                    getView().clearFocus();
                }
                return true;
            }
            case R.id.action_save: {
                boolean success = saveBeacon();
                if (success) {
                    enableEditMode(false);
                    mIsNewModel = false;
                }
                if (getView() != null) {
                    getView().clearFocus();
                }
                return true;
            }
            case R.id.action_delete: {
                new AlertDialog.Builder(getContext())
                        .setTitle(R.string.beacondelete_title)
                        .setMessage(R.string.beacondelete_message)
                        .setPositiveButton(R.string.all_yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                mBeaconStore.deleteBeacon(mBeaconModel);
                                leaveActivity();
                            }
                        })
                        .setNegativeButton(R.string.all_no, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        })
                        .show();
                return true;
            }
        }
        return false;
    }

    public void onBackPressed() {
        boolean success = saveBeacon();
        leaveActivity();
    }

    private void leaveActivity() {
        getActivity().finish();
        getActivity().overridePendingTransition(R.anim.slide_right_in, R.anim.slide_right_out);
    }

    public void enableEditMode(boolean editMode) {
        mEditMode = editMode;
        mSettingsView.setEditMode(editMode);
        mBeaconSpecificView.setEditMode(editMode);
        mBeaconNameField.setEnabled(editMode);
        ActivityCompat.invalidateOptionsMenu(getActivity());
    }

    private boolean saveBeacon() {
        boolean isValid = mBeaconSpecificView.saveModelTo(mBeaconModel);
        isValid = isValid & mSettingsView.saveModelTo(mBeaconModel);
        if (! isValid) {
            new AlertDialog.Builder(getContext())
                    .setTitle("Cannot save")
                    .setMessage("Invalid values, please correct or cancel edition.")
                    .setPositiveButton(R.string.all_ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                        }
                    })
                    .show();
            return false;
        }
        String nameValue = "Registered User Id: (To be updated later)";
        if (nameValue.isEmpty() && mIsNewModel) {//임이 지정으로 삭제 예정
            nameValue = mBeaconModel.generateBeaconName();
            mBeaconNameField.setText(nameValue);
        }
        mBeaconModel.setName(nameValue);
        mBeaconStore.saveBeacon(mBeaconModel);
        mIsNewModel = false;
        return true;
    }

    private void loadBeacon() {
        mBeaconNameField.setText(mBeaconModel.getName());
        mBeaconSpecificView.loadModelFrom(mBeaconModel);
        mSettingsView.loadModelFrom(mBeaconModel);
    }

}
