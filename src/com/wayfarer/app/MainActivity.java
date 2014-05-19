/**
 * Warfarer introduction:
 * ==========================
 *
 * The workflow of this app works like the following:
 * Initialization:
 *  The user is given a list to choose the correponding wearable deivce
 *  according to mac address. After initialization, the app automatically
 *  connect to the device chosen at start up.
 *
 * Normal workflow(after initialization):
 * At startup:
 *  if bluetooth is not enabled, it prompts user to enable bluetooth. If the
 *  user does not permit the action, permission of bluetooth will be asked each
 *  time it is needed.(This part could be changed since I am not responsible for
 *  the bluetooth part and the function to communicate with arduino is not implemented yet.)
 *
 *  Then the main interface is loaded, which is a map that gives the user a
 *  visual display on map of where she or he is.
 *
 *  In this interface, user can decide to start navigation by clicking
 *  'navigation' button.
 *
 * When user decide to do navigation:
 *  After the 'navigation' is clicked, the user is prompted to input a start
 *  location and a destination. Autocompletion will happen in this phase to give
 *  right address decription to user as typing. User confirms the input by
 *  clicking the button 'ok' 
 *
 * Upon confirmation:
 *  After getting two address, utilizing Google Direction API service, all
 *  information needed to travel from origin to destination is fetched and
 *  stored in the {@link Route} class. At the same time, route will be displayed
 *  on the map.
 *  @Andrew, this is where you should take over. Route class have all the
 *  information needed.
 *  For now, location service is buggy. More specifically, the availability of
 *  Google Play Service is not solid. When I disable location service, the app
 *  crashes. And bluetooth is not functioning at all. You can remove any code
 *  concerning bluetooth.
 */
package com.wayfarer.app;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.*;
import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.FragmentManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends Activity implements
GooglePlayServicesClient.ConnectionCallbacks,
GooglePlayServicesClient.OnConnectionFailedListener, LocationListener{

	
	private final static String TAG = MainActivity.class.getSimpleName();
	public static final String LOG_TAG = "Wayfarer: MainActivity";

//---------------------------TIMER------------------------------------//	
   	private static long updateFrequencyDefault = 20000;
    private static long listenTimeInterval = 5000;
    private static long updateScalar = 15000;
    private static long currentTime;
    private static long startTime;
    private static boolean enoughTimeHasPassed=false;
	
	
//-------------------------STICKY GLOBAL HANDLER ---------------------------------//
	private ApplicationController AC = null;

//-----------------------LOCATION GLOBALS-----------------------------------------//
	private int deltaOn = 0;
	private int distanceOn = 1;
	private int thresholdForLost =30;
	private int currentIndex;
	private Location currentLocation = null;
	private Location currentDestination;
	private Location finalDestination;
	private LocationRequest mLocationRequest;
	private static final long locationUpdateInterval = 8000;
	public static String currentAddr = null;
	String startAddr = null;
	String destAddr  = null;
	Route currentRoute = null;
	private static final int TWO_MINUTES = 1000 * 60 * 2;
	private static final double GPS_RADIUS = 35;


	
	
//-----------------------BLUETOOTH GLOBALS----------------------------------------//
	private ArrayList<LatLng> waypoints = null;
	private String mDeviceAddress =  "20:CD:39:9E:F1:40";

	private BluetoothLeService mBluetoothLeService;
	private BluetoothGattCharacteristic characteristicTx;
	private BluetoothGattCharacteristic mNotifyCharacteristic;

//------------------------GUI OBJECTS------------------------------------------------//
	private Button btButton;
	private Button exitButton;
	private Button startNavButton;
	private FragmentManager fragmentManager = null;
	private Handler handler = new Handler();


//------------------------DEVICE COMMUNICATION BOOLEANS---------------------------------//
	private static boolean listenToNavigationUpdates = false;
	private static boolean canStartNav = false;
	private static boolean canStartSendingData = false;
	private static boolean deviceFound = false;
	private static boolean mConnected = false;

	// Requestion code for user interaction activities.
	private static final int FETCH_START_AND_DESTINATION_REQUEST    = 2;
	private static final int CONNECTION_FAILURE_RESOLUTION_REQUEST  = 3;

//-------------------------STATE STRINGS----------------------------------------------//
	private static final String HERE = "here";
	public static final String PAUSE = "PAUSE";
	
	private static final String FOUND = "found";
	private static final String CONNECTED = "connected";
	private static final String DISCONNECTED = "dis";
	private static final String DISCOVERED = "discovered";
	protected static final String NAV_MODE = "navigating";
	private static final String SERVICE_DISCONNECTED = "service disconnected";
	private static final String NOT_FOUND = "not found";
	private static final String SCANNING = "scanning";
	private static final String STOP_NAV_MODE = "stop nav mode";
	private static final String PAUSE_NAV_MODE = "pause nav mode";
	private static final String NAV_MODE_ENABLED = "navigation mode enabled";

	// Necessity class for Google services.
	private GoogleMap map                   = null;
	private LocationClient locationClient   = null;

//-------------------------------------COMMAND STRINGS AND ACTIONS FOR BT SIGNAL -----------------------------------//
	public static final String LOCATION_UPDATE = "LOCATION UPDATE";
	public static final String ARRIVED_CURRENT = "ARRIVED AT CURRENT WAYPOINT";
	public static final String ARRIVED_FINAL = "ARRIVED AT DESTINATION";
	public static final String BEGIN_NAV = "BEGINNING NAVIGATION";
	public static final String BEGIN_NAV_COMMAND = "#5#1#";
	public static final String ARRIVED_FINAL_COMMAND = "#3#1#";
	public static final String ARRIVED_CURRENT_COMMAND = "#4#1#";
	
//--------------------------------GUI----------------------------------------------------------------------//

	public void onNavigate(View view)
	{
		Intent intent = new Intent(this, LocationFetcherActivity.class);
		startActivityForResult(intent, FETCH_START_AND_DESTINATION_REQUEST);
	}
	
	
	private void updateConnectionState(final String action) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if(action.equals(CONNECTED)){
					mConnected = true;
					btButton.setEnabled(true);
					btButton.setClickable(true);
					deviceFound = true;
					btButton.setText("Found");
					startNavButton.setEnabled(false);
					startNavButton.setEnabled(false);
					exitButton.setEnabled(false);
					exitButton.setEnabled(false);
				}else if(action.equals(SERVICE_DISCONNECTED)){
					mBluetoothLeService = null;
					btButton.setText("Connect");
					btButton.setClickable(true);
					btButton.setEnabled(true);
					startNavButton.setEnabled(false);
					startNavButton.setEnabled(false);
					exitButton.setEnabled(false);
					exitButton.setEnabled(false);
					deviceFound = false;
					canStartSendingData = false;
				}else if(action.equals(DISCONNECTED)){
					mConnected = false;
					btButton.setClickable(true);
					btButton.setEnabled(true);
					btButton.setText("Connect");
					startNavButton.setEnabled(false);
					startNavButton.setClickable(false);
					exitButton.setEnabled(false);
					exitButton.setClickable(false);
					deviceFound = false;
					canStartSendingData = false;
				}else if(action.equals(DISCOVERED)){
					btButton.setEnabled(false);
					btButton.setEnabled(false);
					btButton.setText("Connected");
					startNavButton.setEnabled(true);
					startNavButton.setClickable(true);
					exitButton.setEnabled(false);
					exitButton.setClickable(false);
					canStartSendingData = true;
					deviceFound = true;
				}else if(action.equals(NAV_MODE)){
					btButton.setEnabled(false);
					btButton.setClickable(false);
					startNavButton.setEnabled(false);
					startNavButton.setClickable(false);
					exitButton.setEnabled(true);
					exitButton.setClickable(true);
					btButton.setText("Navigating");		
					listenToNavigationUpdates = true;
				}else if(action.equals(FOUND)){
					btButton.setText("Found");
					btButton.setEnabled(true);
					btButton.setClickable(true);
					canStartSendingData= false;
					deviceFound = true;
				}else if(action.equals(NOT_FOUND)){
					btButton.setEnabled(true);
					btButton.setClickable(true);
					btButton.setText("Connect");
					deviceFound = false;
					canStartSendingData = false;
				}else if(action.equals(SCANNING)){
					canStartSendingData = false;
					deviceFound = false;
					btButton.setText("Scanning...");
					btButton.setEnabled(false);
					btButton.setClickable(false);
				}else if(action.equals(PAUSE_NAV_MODE)){
					btButton.setText("Paused");
					startNavButton.setEnabled(true);
					startNavButton.setClickable(true);
					exitButton.setEnabled(false);
					exitButton.setClickable(false);
					listenToNavigationUpdates = false;	
				}else if(action.equals(STOP_NAV_MODE)){
					btButton.setText("Stopped");
					startNavButton.setEnabled(true);
					startNavButton.setClickable(true);
					exitButton.setEnabled(false);
					exitButton.setClickable(false);
					listenToNavigationUpdates = false;	
				}else if(action.equals(NAV_MODE_ENABLED)){
					canStartNav = true;
					startNavButton.setEnabled(true);
					startNavButton.setClickable(true);

				}
			}
		});
	}
	


	// Do a null check to confirm that we have initiated the map.
	// During app's lifetime, This prevents map being destroyed after suspended.
	private void setUpMapIfNeeded(){
		// Get the map if not.
		if(map == null)
		{
			map = ((MapFragment) fragmentManager.findFragmentById(R.id.map))
					.getMap();
			// If we cannot get the map, prompt user to fix the problem.
			// Otherwise functions concerning map may not work.
			if(map == null)
			{
				Log.d(LOG_TAG, "Failed to instantiate google map");
				// TODO: Give prompt to let user fix the problem to let the map
				// running. For instance, enable network.
			}
			else
				Log.d(LOG_TAG, "Successfully instantiate google map.");
			
		
		}
	}

	/**
	 * Initial rendering of Google Map at app startup.
	 */
	private void renderMap()
	{
		setUpMapIfNeeded();
		map.setMyLocationEnabled(true);
	}

	// Unfinished.

	
	private void addListenersOnButtons() {
		bluetoothButton();
		exitNavigationButton();
		startUpNavigationButton();
	}
	
	private void bluetoothButton(){
		btButton = (Button)findViewById(R.id.scanButton);
		btButton.setOnClickListener(new OnClickListener () {

			
			@Override
			public void onClick(View view){		
				/*The first click looks to find the device by calling the application scan
				 * If the device isn't found, the button state remains the same
				 * 
				 */
				if(!deviceFound){
					updateConnectionState(SCANNING);
					scan();
					return;
				}
				/*If the service has discovered BLE_TX services on the device, then we change the 
				 * UI to notify the user that they can begin navigation if they want to 
				 * 	
				 */

				if(deviceFound &&mConnected&& canStartSendingData &&characteristicTx!=null){
					updateConnectionState(DISCOVERED);
					return;
				}

				if(deviceFound && !mConnected){
					if(mBluetoothLeService.connect(mDeviceAddress)){
						updateConnectionState(CONNECTED);
						return;
					}
				}
				
			
				

			}
		});
	}
	
	private void startUpNavigationButton(){
		startNavButton = (Button)findViewById(R.id.startButton);
		startNavButton.setEnabled(false);
		startNavButton.setClickable(false);
		startNavButton.setOnClickListener(new OnClickListener () {

			@Override
			public void onClick(View view){
				if(canStartSendingData && characteristicTx!=null &&deviceFound && navigationOkay()&&mConnected){
					startNavigation();
					updateConnectionState(NAV_MODE);
				}else if(!navigationOkay()||!canStartNav){
					tellUserToSearch();
				}else if(!deviceFound||!canStartSendingData||characteristicTx==null)
					tellUserToUpdateConnectionState();

			}

		});
	}
	
	private void exitNavigationButton(){
		exitButton = (Button)findViewById(R.id.exitButton);
		exitButton.setEnabled(false);
		exitButton.setClickable(false);
		exitButton.setOnClickListener(new OnClickListener(){

			@Override
			public void onClick(View view){
				pauseNavigation();
			}
		});

	}


	private void tellUserToUpdateConnectionState(){
		//updateConnectionState(DISCONNECTED);
		Log.d(LOG_TAG, "Device found = " + deviceFound);
		Log.d(LOG_TAG,"Can Start Sending data = "+ canStartSendingData);
		Log.d(LOG_TAG, "TX is null = " + (characteristicTx==null));
		Toast.makeText(this, "Trying to find device....", Toast.LENGTH_SHORT).show();
		updateConnectionState(SCANNING);
		scan();
		
	}
	private void tellUserToSearch(){
		Toast.makeText(this, "Choose a route by pressing \"Search\"", Toast.LENGTH_SHORT).show();
	}
	

//---------------------------------ACTIVITY LIFECYCLE--------------------------------------------------//

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		fragmentManager = getFragmentManager();
		locationServiceInitialization();
		addListenersOnButtons();
		Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
		bindService(gattServiceIntent,mServiceConnection, BIND_AUTO_CREATE);
		renderMap();
		Log.d(LOG_TAG, "Map render finishes.");

		Log.d(LOG_TAG, "MainActivity initialized.");
		AC = (ApplicationController)getApplicationContext();
		
		mLocationRequest = LocationRequest.create();
		mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
		mLocationRequest.setInterval(locationUpdateInterval);

	}
	
	@Override
	protected void onResume()
	{
		
		super.onResume();
		registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
		if (mBluetoothLeService != null) {
			final boolean result = mBluetoothLeService.connect(mDeviceAddress);
			Log.d(TAG, "Connect request result=" + result);
			//if(result)updateConnectionState(CONNECTED);
		}
		setUpMapIfNeeded();
		
		
		
		
	}

	// Called when the Activity becomes visible.



	@Override
	protected void onStart()
	{
		super.onStart();
		locationClient.connect();
		Route route = AC.getCurrentRoute();
		if(route!=null)currentRoute =route;
		
	}


	@Override
	protected void onPause() {
		super.onPause();
		unregisterReceiver(mGattUpdateReceiver);
		
		if(listenToNavigationUpdates)pauseNavigation();
		
		
	}

	@Override
	protected void onDestroy(){
		super.onDestroy();
		if(listenToNavigationUpdates)stopNavigation();

		unbindService(mServiceConnection);
		mBluetoothLeService = null;
		if(locationClient.isConnected()){
			locationClient.removeLocationUpdates(this);
			locationClient.disconnect();
		}



	}

	/*
	 * Called when the Activity is no longer visible.
	 */
	@Override
	protected void onStop() {
		AC.setRoute(currentRoute);
		// Disconnecting the client invalidates it.
		locationClient.removeLocationUpdates(this);
		locationClient.disconnect();

		super.onStop();
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		switch (requestCode)
		{

		case FETCH_START_AND_DESTINATION_REQUEST:
		{
			// Fetch the start location and destination from user input.
			Log.d(LOG_TAG, "Location fetcher returned.");
			if(resultCode != RESULT_OK)
			{
				Log.d(LOG_TAG, "There is something wrong with location fetcher.");
				// TODO: code the error handler.
				break;
			}
			Log.d(LOG_TAG, "Location fetcher finished successfully.");
			Bundle bundle   = data.getExtras();
			startAddr    = bundle.getString(LocationFetcherActivity.START_ADDR_STRING);

			Log.d(LOG_TAG, "Start location fetched: " + startAddr);
			destAddr     = bundle.getString(LocationFetcherActivity.DEST_ADDR_STRING);
			Log.d(LOG_TAG, "Destination fetched: " + destAddr);

			if(startAddr.equals(HERE)){
				startAddr = currentAddr;
			}
			if(!startAddr.equals("")&&!destAddr.equals("")){
				destAddr = destAddr.replace(" ", "%20");
				startAddr = startAddr.replace(" ", "%20");
				getRouteByRequestingGoogle();
			}else{
				tellUserToSearch();
			}

			break;
		}
		case CONNECTION_FAILURE_RESOLUTION_REQUEST:
		{
			// If google play service resolves the problem, do the
			// request again.
			if(resultCode == RESULT_OK)
				servicesConnected();
			else
			{
				// Show the dialog to inform user google play service
				// must be present to use the app and quit.
				Dialog dialog = new AlertDialog.Builder(this)
				.setTitle(R.string.googleplay_service_prompt)
				.setPositiveButton(R.string.prompt_dialog_quit,
						new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface dialog, int whichButton)
					{
						finish();
					}
				}
						)
						.create();

				WayfarerDialogFragment fragment = new WayfarerDialogFragment();
				fragment.setDialog(dialog);

				fragment.show(fragmentManager, "google_play_service_prompt");
			}
		}
		default:
			Log.e(LOG_TAG, "Activity result out of range.");
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu items for use in the action bar
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_activity_actions, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int itemId = item.getItemId();
		if (itemId == R.id.action_search) {
			// openSearch();
			return true;
		} else if (itemId == R.id.action_settings) {
			// openSettings();
			return true;
		} else {
			return super.onOptionsItemSelected(item);
		}
	}

	
	
	
	


//---------------------------------------------NAVIGATION--------------------------------------------------------//
	private void startNavigation(){
		if(currentRoute==null){
			updateConnectionState(STOP_NAV_MODE);
			Toast.makeText(this, "Failed to start navigation", Toast.LENGTH_SHORT).show();
			return;
		}
		AC.startProgress();
		startTime = System.currentTimeMillis();
		finalDestination = makeLocation(currentRoute.getEndLocation());
		waypoints = currentRoute.getPoints();
		btUpdate(BEGIN_NAV, BEGIN_NAV_COMMAND);
		AC.addProgressPoint(makeLocation(currentRoute.getStartLocation()));
		currentIndex = AC.getProgressIndex();
		currentDestination = makeLocation(waypoints.get(currentIndex));
		updateConnectionState(NAV_MODE);
	}
	

	private void pauseNavigation(){
		updateConnectionState(PAUSE_NAV_MODE);
	}
	
	private void stopNavigation(){
		updateConnectionState(STOP_NAV_MODE);
	}

	private boolean navigationOkay() {
		if(currentRoute==null)currentRoute =AC.getCurrentRoute();
		return(currentRoute!=null&&canStartNav);	
		
	}

	private void btUpdate(String action, String command){
		if(action.equals(ARRIVED_FINAL)){
			stopNavigation();
		}
		if(canStartSendingData && characteristicTx!=null&&deviceFound){
			Log.d(LOG_TAG, "Writing:" + command);

			final byte[] bytes = command.getBytes();
			characteristicTx.setValue(bytes);
			mBluetoothLeService.writeCharacteristic(characteristicTx);
			Toast.makeText(this,action +" "+ command , Toast.LENGTH_SHORT).show();	
		}
	}
	
	private void writeUpdate(String action, String command){
		
		if(listenToNavigationUpdates){
	        currentTime = System.currentTimeMillis();
	        if((currentTime-startTime) >=  listenTimeInterval){
	        	startTime = currentTime;
	        	enoughTimeHasPassed = true;

	        }
	        if(enoughTimeHasPassed || command.equals(ARRIVED_FINAL)){
				enoughTimeHasPassed=false;
				btUpdate(action, command);
			}
		}
		
	}
		
	@Override
	public void onLocationChanged(Location location) {
    	if(waypoints!=null)makeUseOfLocation(location);
	}
	
	private boolean checkRangeOf(float heading){
		return heading>=(-180)&& heading<=180;
		
	}
	    protected boolean isBetterLocation(Location location, Location currentBestLocation) {
        if (currentBestLocation == null) {
            // A new location is always better than no location
            return true;
        }

        // Check whether the new location fix is newer or older
        long timeDelta = location.getTime() - currentBestLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
        boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
        boolean isNewer = timeDelta > 0;

        // If it's been more than two minutes since the current location, use the new location
        // because the user has likely moved
        if (isSignificantlyNewer) {
            return true;
            // If the new location is more than two minutes older, it must be worse
        } else if (isSignificantlyOlder) {
            return false;
        }

        // Check whether the new location fix is more or less accurate
        int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;

        // Check if the old and new location are from the same provider
        boolean isFromSameProvider = isSameProvider(location.getProvider(),
                currentBestLocation.getProvider());

        // Determine location quality using a combination of timeliness and accuracy
        if (isMoreAccurate) {
            return true;
        } else if (isNewer && !isLessAccurate) {
            return true;
        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
            return true;
        }
        return false;
    }

    /** Checks whether two providers are the same */
    private boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) {
            return provider2 == null;
        }
        return provider1.equals(provider2);
    }
    private double chooseDistanceTier(float distanceTo){
    	double distanceScalar = 0;
    	if(distanceTo>=250)distanceScalar = 1;
    	else if(distanceTo<250&&distanceTo>=200)distanceScalar =0.8;
    	else if(distanceTo<200&&distanceTo>=150)distanceScalar =0.7;
    	else if(distanceTo<150&&distanceTo>=100)distanceScalar =0.6;
    	else if(distanceTo<100&&distanceTo>=50)distanceScalar =0.5;
    	else if(distanceTo<50)distanceScalar =0.1;
    	return distanceScalar;
    }

    private void updateFrequencyOfLocationUpdate(double distanceScalar,double deltaScalar){
    	listenTimeInterval = (long) (updateFrequencyDefault - (distanceOn*(1-distanceScalar)*updateScalar) -
    	 (deltaOn*deltaScalar*updateScalar));
	}


	
	private void makeUseOfLocation(Location location){
		if(location== null)return; 
		currentLocation = location;
		Location lastKnownLocation = locationClient.getLastLocation();
		if(isBetterLocation(lastKnownLocation, currentLocation)){
			currentLocation = lastKnownLocation;
			location = lastKnownLocation;
		}
		float delta;
		float headingToDestination = location.bearingTo(currentDestination);
		if(!checkRangeOf(headingToDestination))return;
		if(headingToDestination<0)headingToDestination+=360;
		float currentBearing = location.getBearing();
		float distanceTo = location.distanceTo(currentDestination);
		if(distanceOn==1){
			double distanceScalar = chooseDistanceTier(distanceTo);
			updateFrequencyOfLocationUpdate(distanceScalar, 0);
		}
		if(distanceTo <= location.getAccuracy()||distanceTo<=GPS_RADIUS){
			String action = ARRIVED_CURRENT;
			if(currentDestination.equals(finalDestination)){
				action = ARRIVED_FINAL;
			}else{
				updateCurrentDestination();
				String currentArrived = ARRIVED_CURRENT_COMMAND;
				writeUpdate(action, currentArrived);
				return;
			}
			String arrived = ARRIVED_FINAL_COMMAND;
			writeUpdate(action, arrived);
			return;
		}
		String command="";
		if(!location.hasBearing()||currentBearing==0){
			command +="#2#";
			command += String.valueOf(headingToDestination);

			command +="#";
			writeUpdate(LOCATION_UPDATE, command);
			return;
		}
		if(currentBearing>0.0){
			delta = headingToDestination - currentBearing;
			if(delta<0)delta+=360;
			if(delta>thresholdForLost){
				distanceOn = 0;
				deltaOn = 1;
				double deltaScalar = 180-delta;
				if(deltaScalar<0)deltaScalar+=180;
				updateFrequencyOfLocationUpdate(0,(deltaScalar/180));
			}else {
				deltaOn = 0;
				distanceOn = 1;
			}
			command +="#1#";
			command += String.valueOf(delta);
			command +="#";
			writeUpdate(LOCATION_UPDATE, command);
			return;


		}

	}

	public float getCurrentDistance() {
		if(currentLocation!=null && finalDestination!=null){
			return currentLocation.distanceTo(finalDestination); 
		}
		else return -1;
	}


	private void updateCurrentDestination() {
		AC.addProgressPoint(currentDestination);
		currentIndex = AC.getProgressIndex();
		currentDestination = makeLocation(waypoints.get(currentIndex));
	}

	public Location makeLocation(LatLng ll){
		Location location = new Location("");
		location.setLatitude(ll.latitude);
		location.setLongitude(ll.longitude);
		return location;

	}


	
	
	
	private void startFindingCurrentAddr() {
		Location location = locationClient.getLastLocation();
		(new GetAddressTask(this)).execute(location);
	}

	

	// This is the entry point for haptic navigation. It will start an activity
	// to let user specify start location and destination.
	

//--------------------------------BLUETOOTH SERVICES AND RECIEVER---------------------------------------------//

	private static IntentFilter makeGattUpdateIntentFilter() {
		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
		intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
		intentFilter.addAction(BluetoothLeService.FOUND_DEVICE);

		return intentFilter;
	}
	
	

	// Code to manage Service lifecycle.
	private final ServiceConnection mServiceConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName componentName, IBinder service) {
			mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();

			// Automatically connects to the device upon successful start-up initialization.
			if (!mBluetoothLeService.initialize()) {
				Log.e(TAG, "Unable to initialize Bluetooth");
				finish();
			}
			mBluetoothLeService.connect(mDeviceAddress);
			
		}

		@Override
		public void onServiceDisconnected(ComponentName componentName) {
			mBluetoothLeService = null;
			stopNavigation();
			updateConnectionState(SERVICE_DISCONNECTED);

		}
	};


	private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			if(BluetoothLeService.FOUND_DEVICE.equals(action)){
				updateConnectionState(FOUND);

			}
			if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
					updateConnectionState(CONNECTED);
				
			} else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
				stopNavigation();
				updateConnectionState(DISCONNECTED);

			} else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
				updateConnectionState(DISCOVERED);
				getGattService(mBluetoothLeService.getBLEService());
				
			} else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
				//updateConnectionState(DISCONNECTED);
				
				return;
			}
		}
	};
	

	private void getGattService(BluetoothGattService gattService) {
		if (gattService == null){
			Toast.makeText(this, "gattService null", Toast.LENGTH_SHORT).show();
			return;
		}
			
		characteristicTx = gattService.getCharacteristic(BluetoothLeService.UUID_BLE_SHIELD_TX);
		if(characteristicTx ==null){
			Toast.makeText(this, "TX is null", Toast.LENGTH_SHORT).show();
			Log.d(LOG_TAG,"Characterisitc is null");
		}
		BluetoothGattCharacteristic characteristicRx = gattService.getCharacteristic(BluetoothLeService.UUID_BLE_SHIELD_RX);
		mBluetoothLeService.setCharacteristicNotification(characteristicRx,true);
		mBluetoothLeService.readCharacteristic(characteristicRx);
	
	}



	private void scan(){
		mBluetoothLeService.scanForDeviceTry();
		handler.postDelayed(mStopRunnable, 2500);
	}

	private Runnable mStopRunnable = new Runnable() {
		@Override
		public void run() {
			if(!deviceFound){
				updateConnectionState(NOT_FOUND);
			}else if(deviceFound && mBluetoothLeService.connect(mDeviceAddress)){
				updateConnectionState(CONNECTED);
			}
		}
	};

	
	

//-------------------------------------ASYNC AND PLAY SERVICES----------------------------------------------//
	

	private void locationServiceInitialization()
	{
		if(servicesConnected())
			locationClient = new LocationClient(this, this, this);
		else
		{
			Log.e(LOG_TAG, "Cannot connect to Google Play Service. Program should not reach here.");
			return;
		}
	}
	
	
	
	/*
	 * Called by Location Services when the request to connect the client
	 * finishes successfully. At this point, you can request the current
	 * location or start periodic updates
	 */
	@Override
	public void onConnected(Bundle dataBundle) {
		// Display the connection status
		Log.d(LOG_TAG, "Location service connnected.");
		Toast.makeText(this, "Location Service Connected", Toast.LENGTH_SHORT).show();
		startFindingCurrentAddr();
		locationClient.requestLocationUpdates(mLocationRequest, this);

	}
	
	
	/*
	 * Called by Location Services if the connection to the
	 * location client drops because of an error.
	 */
	@Override
	public void onDisconnected() {
		// Display the connection status
		Log.d(LOG_TAG, "Location service disconnnected.");
		Toast.makeText(this, "Location Serice Disconnected. Please re-connect.",
				Toast.LENGTH_SHORT).show();
	}

	/*
	 * Called by Location Services if the attempt to
	 * Location Services fails.
	 * TODO: clean this up when finishing writing route parsing.
	 */
	@Override
	public void onConnectionFailed(ConnectionResult connectionResult)
	{
		/**
		 * Google Play services can resolve some errors it detects.
		 * If the error has a resolution, try sending an Intent to
		 * start a Google Play services activity that can resolve
		 * error.
		 */
		// if (connectionResult.hasResolution()) {
		// try {
		// // Start an Activity that tries to resolve the error
		// connectionResult.startResolutionForResult(
		// this,
		// CONNECTION_FAILURE_RESOLUTION_REQUEST);
		/**
		 * Thrown if Google Play services canceled the original
		 * PendingIntent
		 */
		// } catch (IntentSender.SendIntentException e) {
		// // Log the error
		// e.printStackTrace();
		// }
		// } else {
		/**
		 * If no resolution is available, display a dialog to the
		 * user with the error.
		 */
		// showErrorDialog(connectionResult.getErrorCode());
		// }
	}
	
	
	
	/**
	 * Check and handle the availability of Google Play Service, which is
	 * essential for LocationService provided by android.
	 */
	private boolean servicesConnected()
	{
		// Check that Google Play services is available
		int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
		// If Google Play services is available
		if (ConnectionResult.SUCCESS == resultCode) {
			// In debug mode, log the status
			Log.d("Location Updates",
					"Google Play services is available.");
			// Continue
			return true;
			// Google Play services was not available for some reason
		} else {
			// Get the error code
			int errorCode = resultCode;
			// Get the error dialog from Google Play services
			Dialog errorDialog = GooglePlayServicesUtil.getErrorDialog(
					errorCode,
					this,
					CONNECTION_FAILURE_RESOLUTION_REQUEST);

			// If Google Play services can provide an error dialog
			if (errorDialog != null) {
				// Create a new DialogFragment for the error dialog
				WayfarerDialogFragment errorFragment =
						new WayfarerDialogFragment();
				// Set the dialog in the DialogFragment
				errorFragment.setDialog(errorDialog);
				// Show the error dialog in the DialogFragment
				errorFragment.show(fragmentManager,
						"Location Updates");
			}

			// If no error dialog obtained, just return false. We cannot
			// connect to Google Play Service.
			return false;
		}
	}

	private void drawRoute(){
			// Draw route on the map.
			PolylineOptions routePolylineOptions = new PolylineOptions();
			routePolylineOptions.addAll(currentRoute.getPoints());
			map.addPolyline(routePolylineOptions);

			// Draw marker on origin and destination.
			map.addMarker(new MarkerOptions()
			.position(currentRoute.getStartLocation())
			.title(currentRoute.getStartAddr())
					);
			map.addMarker(new MarkerOptions()
			.position(currentRoute.getEndLocation())
			.title(currentRoute.getDestAddr())
					);

			// Set camera to the route.
			// TODO: adjust the padding when refining.
			// TODO: add animation when moving camera.
			map.moveCamera(CameraUpdateFactory.newLatLngBounds(currentRoute.getBounds(), 0));
			updateConnectionState(NAV_MODE_ENABLED);
			//TRIGGER ABILITY TO TOGGLE START NAV BUTTON IF BLUETOOTH GOOD
	}

	/**
	 * Make request to Google Direction API to get route from start address to
	 * destination address in another thread.
	 *
	 * start_addr, dest_addr and route are all class memebers, so no parameters
	 * are passed.
	 */
	private void getRouteByRequestingGoogle()
	{
		new GetRoutes().execute();
	}

	private class GetRoutes extends AsyncTask<String, Void, Void>
	{
		@Override
		protected void onPreExecute() {

		}

		@Override
		protected Void doInBackground(String... params)
		{
			currentRoute = directions(startAddr, destAddr);

			return null;
		}

		@Override
		protected void onPostExecute(Void result)
		{
			drawRoute();
		}

		private Route directions(String startAddr, String destAddr)
		{
			Route route = null;

			// Construct http request to Google Direction API service.
			String jsonURL = "http://maps.googleapis.com/maps/api/directions/json?";
			StringBuilder sBuilder = new StringBuilder(jsonURL);
			sBuilder.append("origin=");
			sBuilder.append(startAddr);
			sBuilder.append("&destination=");
			sBuilder.append(destAddr);
			sBuilder.append("&sensor=true&mode=walking&key" + Utilities.API_KEY);

			String requestUrl = sBuilder.toString();
			try {
				final GoogleDirectionParser parser = new GoogleDirectionParser(requestUrl);
				route = parser.parse();
			} catch (MalformedURLException e) {
				Log.e(LOG_TAG, "Error when parsing url.");
			}
			return route;
		}
	}

	private class GetAddressTask extends AsyncTask<Location, Void, String>{
		Context mContext;
		public GetAddressTask(Context context) {
			super();
			mContext = context;
		}

		/*
		 * When the task finishes, onPostExecute() displays the address. 
		 */
		@Override
		protected void onPostExecute(String address){
			currentAddr = address;
		}
		@Override
		protected String doInBackground(Location... params) {
			Geocoder geocoder =
					new Geocoder(mContext, Locale.getDefault());
			// Get the current location from the input parameter list
			Location loc = params[0];
			// Create a list to contain the result address
			List<Address> addresses = null;
			try {
				addresses = geocoder.getFromLocation(loc.getLatitude(),
						loc.getLongitude(), 1);
			} catch (IOException e1) {
				Log.e("LocationSampleActivity", 
						"IO Exception in getFromLocation()");
				e1.printStackTrace();
				return ("IO Exception trying to get address");
			} catch (IllegalArgumentException e2) {
				// Error message to post in the log
				String errorString = "Illegal arguments " +
						Double.toString(loc.getLatitude()) +
						" , " +
						Double.toString(loc.getLongitude()) +
						" passed to address service";
				Log.e("LocationSampleActivity", errorString);
				e2.printStackTrace();
				return errorString;
			}
			// If the reverse geocode returned an address
			if (addresses != null && addresses.size() > 0) {
				// Get the first address
				Address address = addresses.get(0);
				/*
				 * Format the first line of address (if available),
				 * city, and country name.
				 */
				String addressText = String.format(
						"%s, %s, %s",
						// If there's a street address, add it
						address.getMaxAddressLineIndex() > 0 ?
								address.getAddressLine(0) : "",
								// Locality is usually a city
								address.getLocality(),
								// The country of the address
								address.getCountryName());
				// Return the text
				return addressText;
			} else {
				return "No address found";
			}
		}
	}// AsyncTask class

}


   


    
