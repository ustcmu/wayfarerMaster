package com.wayfarer.app;

import java.util.ArrayList;

import com.google.android.gms.maps.model.LatLng;

import android.app.Application;
import android.location.Location;

public class ApplicationController extends Application {

	private static ApplicationController singleton;
	private static Route currentRoute = null;
	private static ArrayList<Location> progress;
	
	public ApplicationController getInstance(){
		return singleton;
	}
	
	public void addProgressPoint(Location wp){
		progress.add(wp);
	}
	
	public void startProgress(){
		progress = new ArrayList<Location>();
	}
	
	public int getProgressIndex(){
		return progress.size();
	}
	
	public boolean iHaveBeenHere(LatLng wp){
		return progress.contains(wp);
	}
	
	
	public Location getLastWaypoint(){
		return progress.get(progress.size()-1);
	}
	
	@Override 
	public void onCreate(){
		super.onCreate();
		singleton = this;
	}
	
	public void setRoute(Route route){
		currentRoute = route;
		
	}
	public Route getCurrentRoute(){
		return currentRoute;
	}
	
	
	
}
