/****************************************************************************************
 * Copyright (c) 2016, 2017, 2019 Vincent Hiribarren                                    *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * Linking Beacon Simulator statically or dynamically with other modules is making      *
 * a combined work based on Beacon Simulator. Thus, the terms and conditions of         *
 * the GNU General Public License cover the whole combination.                          *
 *                                                                                      *
 * As a special exception, the copyright holders of Beacon Simulator give you           *
 * permission to combine Beacon Simulator program with free software programs           *
 * or libraries that are released under the GNU LGPL and with independent               *
 * modules that communicate with Beacon Simulator solely through the                    *
 * net.alea.beaconsimulator.bluetooth.AdvertiseDataGenerator and the                    *
 * net.alea.beaconsimulator.bluetooth.AdvertiseDataParser interfaces. You may           *
 * copy and distribute such a system following the terms of the GNU GPL for             *
 * Beacon Simulator and the licenses of the other code concerned, provided that         *
 * you include the source code of that other code when and as the GNU GPL               *
 * requires distribution of source code and provided that you do not modify the         *
 * net.alea.beaconsimulator.bluetooth.AdvertiseDataGenerator and the                    *
 * net.alea.beaconsimulator.bluetooth.AdvertiseDataParser interfaces.                   *
 *                                                                                      *
 * The intent of this license exception and interface is to allow Bluetooth low energy  *
 * closed or proprietary advertise data packet structures and contents to be sensibly   *
 * kept closed, while ensuring the GPL is applied. This is done by using an interface   *
 * which only purpose is to generate android.bluetooth.le.AdvertiseData objects.        *
 *                                                                                      *
 * This exception is an additional permission under section 7 of the GNU General        *
 * Public License, version 3 (“GPLv3”).                                                 *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package net.alea.beaconsimulator;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;


import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import net.alea.beaconsimulator.bluetooth.model.AltBeacon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

public class ActivityMain extends ActivityDrawer implements FragmentScanner.OnScannerActionDelegate {

    private static final Logger sLogger = LoggerFactory.getLogger(ActivityMain.class);

    //TextView for indicate current sending state
    private TextView runningText;

    enum Feature {broadcast, scan}

    //Location points are stacked in LatLng type
    private ArrayList<LatLng> locPoints = new ArrayList<>();
    private double lat, lng; // location of current user
    private String UUID;

    // objects for displaying maps
    SupportMapFragment mapFragment;
    GoogleMap map;
    MarkerOptions markerOptions;
    private SeekBar mapSizeBar;
    public final int MAP_SIZE_MIN = 10;
    public final int MAP_SIZE_MAX = 21;
    // vibrator for warning between driver and pedestrians
    public Vibrator vibrator;
    private boolean inVibeRunning = false;

    //Thread for sending data to server
    private PushServerThread pushServerThread;
    //Thread for receiving data from server
    private GetServerThread getDataThread;
    //Handler for control data received from server
    //defined at bottom of current activity main class
    public ServerHandler handler;

    private static final String EXTRA_FEATURE = "EXTRA_FEATURE";

    private ViewPager mViewPager;
    private ViewPagerAdapter mViewPagerAdapter;

    //Thread and beacon are controlled through floating button
    private FloatingActionButton mSharedFab;
    private ObjectAnimator mFabAnimator;

    private FragmentSimulator fragmentSimulator;
    private FragmentScanner fragmentScanner;


    //stop scan when bluetooth of device state changed to OFF
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case BluetoothAdapter.ACTION_STATE_CHANGED: {
                    final int btState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
                    switch (btState) {
                        case BluetoothAdapter.STATE_TURNING_OFF:
                            final FragmentScanner fragmentScanner = mViewPagerAdapter.getFragmentScanner();
                            if (fragmentScanner != null) {
                                fragmentScanner.stopBeaconScan();
                            }
                            break;
                    }
                    break;
                }
            }
        }
    };


    public static void displayActivityFeature(Context context, Feature feature) {
        final Intent activityIntent = new Intent(context, ActivityMain.class);
        activityIntent.putExtra(EXTRA_FEATURE, feature);
        context.startActivity(activityIntent);
    }

    //control mode(driver/pedestrian) change
    @Override
    protected void onNewIntent(Intent intent) {
        if (intent == null || intent.getExtras() == null) {
            return;
        }
        Bundle extras = intent.getExtras();
        Feature feature = (Feature) extras.getSerializable(EXTRA_FEATURE);
        if (feature == null) {
            return;
        }
        switch (feature) {
            case broadcast:
                mViewPager.setCurrentItem(ViewPagerAdapter.PAGE_INDEX_DRIVER);
                break;
            case scan:
                mViewPager.setCurrentItem(ViewPagerAdapter.PAGE_INDEX_PEDESTRIAN);
                break;
        }
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sLogger.debug("onCreate()");
        setContentView(R.layout.activity_main);


        // floatingActionButton in each mode
        // Currently, when this button is pressed, the add and map are executed.
        mSharedFab = (FloatingActionButton) findViewById(R.id.main_fab_shared);
        mSharedFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (mViewPager.getCurrentItem()) {
                    case ViewPagerAdapter.PAGE_INDEX_DRIVER:

                        //check whether simulator or scanner are null
                        if (fragmentSimulator == null) {
                            Log.d("TAG", "fragmentSimulator points null");
                            fragmentSimulator = mViewPagerAdapter.getFragmentSimulator();
                        } else if (fragmentScanner == null) {
                            Log.d("TAG", "fragmentScanner points null");
                            fragmentScanner = mViewPagerAdapter.getFragmentScanner();
                        } else {
                            Log.d("TAG", "fragment Simulator and Scanner Working..");
                        }
                        if (fragmentSimulator != null) {
                            //Create beacon on first run
                            fragmentSimulator.actionCreateBeacon();
                            AltBeacon driverBeacon = fragmentSimulator.getDiverBeacon();
                            if (driverBeacon != null) {
                                UUID = driverBeacon.getBeaconNamespace().toString();
                                Log.d("TAG", "current UUID" + UUID);

                                //if setDataThread object points null, reassign new SetServerThread
                                if (pushServerThread == null) {
                                    Log.d("TAG", "In activityMain / reassign new SetDataThread");
                                    pushServerThread = new PushServerThread();
                                    pushServerThread.setUUID(UUID, null);
                                    pushServerThread.setLocation(lat, lng);
                                    pushServerThread.start();
                                } else {

                                    pushServerThread.setUUID(UUID, null);
                                    pushServerThread.setLocation(lat, lng);
                                    pushServerThread.setIsStop(!pushServerThread.getIsStop());

                                    runningText.setText("Data Sending..:" + !pushServerThread.getIsStop());
                                }
                                Toast.makeText(getApplicationContext(), pushServerThread.getIsStop() + UUID, Toast.LENGTH_SHORT).show();

                                if (getDataThread == null) {
                                    getDataThread = new GetServerThread();
                                    getDataThread.start();
                                } else if (!getDataThread.isAlive())
                                    getDataThread.start();
                            }
                        }
                        break;
                    case ViewPagerAdapter.PAGE_INDEX_PEDESTRIAN:

                        if (fragmentSimulator == null) {
                            Log.d("TAG", "fragmentSimulator points null");
                            fragmentSimulator = mViewPagerAdapter.getFragmentSimulator();
                        } else if (fragmentScanner == null) {
                            Log.d("TAG", "fragmentScanner points null");
                            fragmentScanner = mViewPagerAdapter.getFragmentScanner();
                        }

                        if (fragmentSimulator != null) {
                            fragmentSimulator.actionCreateBeacon();
                        }
                        if (fragmentScanner != null) {
                            fragmentScanner.actionScanToggle();
                            AltBeacon driverBeacon = fragmentSimulator.getDiverBeacon();
                            if (driverBeacon != null) {
                                fragmentScanner.setSetServerThread(pushServerThread);
                                UUID = driverBeacon.getBeaconNamespace().toString();

                                if (pushServerThread == null) {
                                    Log.d("TAG", "In activityMain / reassign new SetDataThread");
                                    pushServerThread = new PushServerThread();
                                    pushServerThread.setUUID(null, UUID);
                                    pushServerThread.setLocation(lat, lng);
                                    pushServerThread.start();
                                } else {
                                    pushServerThread.setUUID(null, UUID);
                                    pushServerThread.setLocation(lat, lng);
                                    pushServerThread.setIsStop(!pushServerThread.getIsStop());

                                    runningText.setText("Data Sending..:" + !pushServerThread.getIsStop());
                                }
                                Toast.makeText(getApplicationContext(), pushServerThread.getIsStop() + UUID, Toast.LENGTH_SHORT).show();

                                if (getDataThread == null) {
                                    getDataThread = new GetServerThread();
                                    getDataThread.start();
                                } else if (!getDataThread.isAlive())
                                    getDataThread.start();

                            }
                        }
                        break;

                    default:
                        throw new IndexOutOfBoundsException(String.format("Cannot have more than %s items", ViewPagerAdapter.PAGE_COUNT));
                }
            }
        });

        mSharedFab.setImageResource(R.drawable.ic_menu_add); // To init FAB with icon

        Toolbar toolbar = (Toolbar) findViewById(R.id.main_toolbar);
        setSupportActionBar(toolbar);

        mViewPager = (ViewPager) findViewById(R.id.main_viewpager);
        mViewPagerAdapter = new ViewPagerAdapter(getSupportFragmentManager(), mViewPager, this);

        mViewPager.setAdapter(mViewPagerAdapter);
        mViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                final FragmentSimulator fragmentSimulator = mViewPagerAdapter.getFragmentSimulator();
                if (fragmentSimulator == null) {
                    return;
                }
                if (position != ViewPagerAdapter.PAGE_INDEX_DRIVER && fragmentSimulator.isEditMode()) {
                    fragmentSimulator.finishEditMode();
                }
                switch (position) {
                    case ViewPagerAdapter.PAGE_INDEX_DRIVER:
                        selectNavigationItem(ITEM_BROADCAST);
                        if (pushServerThread != null && UUID != null) {
                            pushServerThread.setUUID(UUID, null);
                            pushServerThread.setLocation(lat, lng);
                        }
                        break;
                    case ViewPagerAdapter.PAGE_INDEX_PEDESTRIAN:
                        selectNavigationItem(ITEM_SCAN);
                        if (pushServerThread != null && UUID != null) {
                            pushServerThread.setUUID(null, UUID);
                            pushServerThread.setLocation(lat, lng);
                        }
                        break;
                    case -1:
                    default:
                        selectNavigationItem(ITEM_NONE);
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                switch (state) {
                    case ViewPager.SCROLL_STATE_DRAGGING:
                        mSharedFab.hide();
                        pushServerThread.setIsStop(true);

                        break;
                    case ViewPager.SCROLL_STATE_IDLE:
                        switch (mViewPager.getCurrentItem()) {
                            case ViewPagerAdapter.PAGE_INDEX_DRIVER:
                                mSharedFab.setImageResource(R.drawable.ic_menu_add);

                                break;
                            case ViewPagerAdapter.PAGE_INDEX_PEDESTRIAN:
                            default:
                                mSharedFab.setImageResource(R.drawable.ic_menu_search);
                                break;
                        }

                        Toast.makeText(getApplicationContext(), "ModeChanged:" + pushServerThread.getIsStop(), Toast.LENGTH_SHORT).show();
                        mSharedFab.show();
                        break;
                }
            }
        });
        fragmentSimulator = mViewPagerAdapter.getFragmentSimulator();
        fragmentScanner = mViewPagerAdapter.getFragmentScanner();

        TabLayout tabLayout = (TabLayout) findViewById(R.id.main_tablayout);
        tabLayout.setupWithViewPager(mViewPager);
        tabLayout.getTabAt(ViewPagerAdapter.PAGE_INDEX_DRIVER).setIcon(R.drawable.ic_menu_broadcast_on);
        tabLayout.getTabAt(ViewPagerAdapter.PAGE_INDEX_PEDESTRIAN).setIcon(R.drawable.ic_menu_search);

        initNavigationDrawer(findViewById(android.R.id.content), toolbar);
        onNewIntent(getIntent()); // Beware: intent can be null after an app update from Android Studio


        boolean ok = checkPermission();
        // google map setting
        if (ok) {
            mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
            mapFragment.getMapAsync(new OnMapReadyCallback() {
                @Override
                public void onMapReady(GoogleMap googleMap) {
                    map = googleMap;
                    if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                       Toast.makeText(getApplicationContext(), "please check permissions", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    map.setMyLocationEnabled(true);
                    startLocationService();
                }
            });
            try {
                MapsInitializer.initialize(this);
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
        mapSizeBar = findViewById(R.id.mapSizeBar);
        mapSizeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float zoom = (MAP_SIZE_MAX - MAP_SIZE_MIN) * progress / 100 + MAP_SIZE_MIN;
                Log.d("MAP", String.valueOf(zoom));
                LatLng curPoint = new LatLng(lat, lng);
                //camera position and zoom scale
                map.animateCamera(CameraUpdateFactory.zoomTo(zoom));
                showLocationMarker(curPoint);//add markers
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        pushServerThread = new PushServerThread();
        pushServerThread.setLocation(lat, lng);

        pushServerThread.start();

        handler = new ServerHandler();
        getDataThread = new GetServerThread();
        getDataThread.setHandler(handler);
        runningText = findViewById(R.id.Running);
        runningText.setText("Data Sending..:" + !pushServerThread.getIsStop());
    }


    public String getMainUUID() {
        return this.UUID;
    }

    public Double getLat() {
        return this.lat;
    }

    public Double getLng() {
        return this.lng;
    }

    public PushServerThread getPushServerThread() {
        return this.pushServerThread;
    }

    public void addLocPoints(double lat, double lon) {
        if (lat > 0 && lon > 0) {
            this.locPoints.add(new LatLng(lat, lon));
        }
    }


    private void startLocationService() {
        LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        try {
            // get current my position
            Location location = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (location != null) {
                lat = location.getLatitude();
                lng = location.getLongitude();
                showCurrentLocation(lat, lng);
            }
            GPSListener gpsListener = new GPSListener();
            long minTime = 2000;
            float minDistance = 0;
            manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, minTime, minDistance, gpsListener);
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    private void showCurrentLocation(Double latitude, Double longitude) {

        LatLng curPoint = new LatLng(latitude, longitude);
        //camera position and zoom scale
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(curPoint, 18));
        showLocationMarker(curPoint);//add markers
    }

    private void showLocationMarker(LatLng curPoint) {
        if (markerOptions == null) {
            markerOptions = new MarkerOptions();
            markerOptions.position(curPoint);
            markerOptions.title("My Position\n");
            markerOptions.snippet("confirm with GPS");//Additional Description
            markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.location));
            map.addMarker(markerOptions);

            if (locPoints.size() > 0) {
                for (LatLng point : locPoints) {
                    markerOptions.position(point);
                    markerOptions.title("other Position");
                    markerOptions.snippet("confirm with GPS");
                    markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.location2));
                    map.addMarker(markerOptions);
                }
            }

        } else {
            markerOptions.position(curPoint);
            if (locPoints.size() > 0) {
                for (LatLng point : locPoints) {
                    markerOptions.position(point);
                    markerOptions.title("other Position");
                    markerOptions.snippet("confirm with GPS");
                    markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.location2));
                    map.addMarker(markerOptions);
                }
            }
        }
    }


    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        // In this method, so we are sure the internal state of mViewPager is loaded after config change
        switch (mViewPager.getCurrentItem()) {
            case ViewPagerAdapter.PAGE_INDEX_PEDESTRIAN:
                mSharedFab.setImageResource(R.drawable.ic_menu_search);
                // Trick to ensure setUserVisibilityHint is not called with false after orientation change
                final FragmentScanner fragmentScanner = mViewPagerAdapter.getFragmentScanner();
                if (fragmentScanner != null) {
                    mViewPager.getAdapter().setPrimaryItem(null, ViewPagerAdapter.PAGE_INDEX_PEDESTRIAN, fragmentScanner);
                }
                break;
            case ViewPagerAdapter.PAGE_INDEX_DRIVER:
            default:
                mSharedFab.setImageResource(R.drawable.ic_menu_add);
                // Trick to ensure setUserVisibilityHint is not called with false after orientation change
                final FragmentSimulator fragmentSimulator = mViewPagerAdapter.getFragmentSimulator();
                if (fragmentSimulator != null) {
                    mViewPager.getAdapter().setPrimaryItem(null, ViewPagerAdapter.PAGE_INDEX_DRIVER, fragmentSimulator);
                }
        }
    }


    @Override
    protected void onStart() {
        super.onStart();
        sLogger.debug("onStart()");
        registerReceiver(mBroadcastReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        checkPermission();
        if (pushServerThread.isAlive()) {
            Log.d("TAG", "onStart / setDataThread is alive");
        }
        if (getDataThread.isAlive()) {
            Log.d("TAG", "onStart / getDataThread is alive");
        }

    }

    @Override
    public void onResume() {
        super.onResume();
        sLogger.debug("onResume()");
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (map != null) {
            map.setMyLocationEnabled(true);
        }
        if (pushServerThread.isAlive()) {
            Log.d("TAG", "onResume / setDataThread is alive");
        }
        if (getDataThread.isAlive()) {
            Log.d("TAG", "onResume / getDataThread is alive");
        }

    }

    @Override
    protected void onStop() {
        super.onStop();
        sLogger.debug("onStop()");
        unregisterReceiver(mBroadcastReceiver);


    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pushServerThread.interrupt();
        getDataThread.interrupt();
    }

    @Override
    public void onBackPressed() {
        final FragmentSimulator fragmentSimulator = mViewPagerAdapter.getFragmentSimulator();
        if (fragmentSimulator != null
                && mViewPager.getCurrentItem() == ViewPagerAdapter.PAGE_INDEX_DRIVER
                && fragmentSimulator.isEditMode()) {
            fragmentSimulator.finishEditMode();
        } else {
            super.onBackPressed();
        }
    }

    public void onScanStatusUpdate(boolean isScanning) {
        if (mViewPager.getCurrentItem() != ViewPagerAdapter.PAGE_INDEX_PEDESTRIAN) {
            return;
        }
        if (isScanning) {
            if (mSharedFab != null) mSharedFab.setImageResource(R.drawable.ic_menu_pause);
            int whiteColor = 0xFFFFFFFF;
            int accentColor = ContextCompat.getColor(this, R.color.colorAccent);
            mFabAnimator = ObjectAnimator.ofArgb(mSharedFab.getDrawable(), "tint", accentColor, whiteColor);
            mFabAnimator.setDuration(500);
            mFabAnimator.setRepeatCount(ObjectAnimator.INFINITE);
            mFabAnimator.setRepeatMode(ObjectAnimator.REVERSE);
            mFabAnimator.start();
        } else {
            if (mSharedFab != null) mSharedFab.setImageResource(R.drawable.ic_menu_search);
            if (mFabAnimator != null) {
                mFabAnimator.setRepeatCount(0);
                mFabAnimator = null;
            }
        }
    }

    public boolean checkPermission() {
        boolean needLocation = false;

        // Check Android permission
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        0);
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        0);
            }
        }

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION)) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        0);
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        0);
            }
        }

        //Is an internet permission necessary?
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.INTERNET)) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.INTERNET},
                        0);
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.INTERNET},
                        0);
            }
        }
        // Check if location enable
        int locationMode = Settings.Secure.getInt(this.getContentResolver(), Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF);
        if (locationMode == Settings.Secure.LOCATION_MODE_OFF) {
            needLocation = true;
        }

        if (needLocation) {
            Toast.makeText(this, "Need Location permissions", Toast.LENGTH_SHORT).show();
            return false;
        } else {
            return true;
        }
    }

    private static class ViewPagerAdapter extends FragmentPagerAdapter {

        private final static int PAGE_COUNT = 2;
        private final static int PAGE_INDEX_DRIVER = 0;
        private final static int PAGE_INDEX_PEDESTRIAN = 1;

        private final int _containerId;
        private final Context _context;
        private final FragmentManager _fragmentManager;

        public ViewPagerAdapter(FragmentManager manager, ViewPager container, Context context) {
            super(manager);
            _containerId = container.getId();
            _context = context;
            _fragmentManager = manager;
        }

        @Nullable
        public Fragment getRegisteredFragment(int position) {
            return _fragmentManager.findFragmentByTag(getFragmentTag(_containerId, position));
        }

        @Nullable
        public FragmentSimulator getFragmentSimulator() {
            return (FragmentSimulator) getRegisteredFragment(PAGE_INDEX_DRIVER);
        }

        @Nullable
        public FragmentScanner getFragmentScanner() {
            return (FragmentScanner) getRegisteredFragment(PAGE_INDEX_PEDESTRIAN);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case PAGE_INDEX_DRIVER:
                    return new FragmentSimulator();
                case PAGE_INDEX_PEDESTRIAN:
                    return new FragmentScanner();
                default:
                    throw new IndexOutOfBoundsException(String.format("Cannot have more than %s items", PAGE_COUNT));
            }
        }

        @Override
        public int getCount() {
            return PAGE_COUNT;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case PAGE_INDEX_DRIVER:
                    return _context.getString(R.string.main_tab_simulator);
                case PAGE_INDEX_PEDESTRIAN:
                    return _context.getString(R.string.main_tab_scanner);
                default:
                    throw new IndexOutOfBoundsException(String.format("Cannot have more than %s items", PAGE_COUNT));
            }
        }

        private String getFragmentTag(int viewPagerId, int fragmentPosition) {
            return "android:switcher:" + viewPagerId + ":" + fragmentPosition;
        }

    }

    class GPSListener implements LocationListener {
        @Override
        public void onLocationChanged(Location location) {
            //whenever time change, call showCurrentLocation with new location values
            lat = location.getLatitude();
            lng = location.getLongitude();
            showCurrentLocation(lat, lng);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
        }
    }

    //by updating later, parsing using json will be done
    class ServerHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            Bundle bundle = msg.getData();
            String result = bundle.getString("info");
            double minDistance = 9999;
            //임시로 split 해서 parsing, 후에 json 으로 파일 예정
            String[] lines = result.split("<br>");
            for (String line : lines) {
                String[] values = line.split(" ");
                if (values.length < 4) break;
                String V_UUID = values[1];
                String P_UUID = values[2].split(":")[1];
                double latitude = Double.valueOf(values[5]);
                double longitude = Double.valueOf(values[9]);

                switch (mViewPager.getCurrentItem()) {
                    case ViewPagerAdapter.PAGE_INDEX_DRIVER:
                        if (V_UUID.equals(UUID) && !V_UUID.startsWith("null")) {
//                            Log.d("TAG", "GPS handler/Driver");
//                            Log.d("TAG", V_UUID+"/"+P_UUID+"/"+values[5]+"/"+values[9]);
                            addLocPoints(latitude, longitude);
                            double dis = distanceInKilometerByHaversine(lat, lng, latitude, longitude);
                            if (minDistance > dis) {
                                minDistance = dis;
                                runningText.setText("Min distance: " + minDistance);
                            }
                        }
                        break;
                    case ViewPagerAdapter.PAGE_INDEX_PEDESTRIAN:
                        if (P_UUID.startsWith("null")) {
//                            Log.d("TAG", "GPS handler/Pedestrian");
//                            Log.d("TAG", V_UUID+"/"+P_UUID+"/"+values[5]+"/"+values[9]);
                            addLocPoints(latitude, longitude);
                            double dis = distanceInKilometerByHaversine(lat, lng, latitude, longitude);
                            if (minDistance > dis) {
                                minDistance = dis;
                                runningText.setText("Min distance: " + minDistance);
                            }
                        }
                        break;
                }

            }

//            if(minDistance<10){
//                vibrator.vibrate(500);
//            }
        }

        // calculating distance using two GPS points
        public double distanceInKilometerByHaversine(double x1, double y1, double x2, double y2) {
            double distance;
            double radius = 6371; // 지구 반지름(km)
            double toRadian = Math.PI / 180;

            double deltaLatitude = Math.abs(x1 - x2) * toRadian;
            double deltaLongitude = Math.abs(y1 - y2) * toRadian;

            double sinDeltaLat = Math.sin(deltaLatitude / 2);
            double sinDeltaLng = Math.sin(deltaLongitude / 2);
            double squareRoot = Math.sqrt(
                    sinDeltaLat * sinDeltaLat +
                            Math.cos(x1 * toRadian) * Math.cos(x2 * toRadian) * sinDeltaLng * sinDeltaLng);

            distance = 2 * radius * Math.asin(squareRoot);

            return distance * 1000;
        }
    }

}
