package com.wayfarer.app;

import android.app.Application;

public class ApplicationController extends Application {

	private static ApplicationController singleton;
	private static Route currentRoute = null;
	
	public ApplicationController getInstance(){
		return singleton;
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
