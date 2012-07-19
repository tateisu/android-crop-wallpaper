package jp.juggler.CropWallpaper;

import jp.juggler.util.ExceptionHandler;
import jp.juggler.util.LogCategory;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.preference.PreferenceManager;

public class MyApp extends android.app.Application {
	static final LogCategory log = new LogCategory("MyApp");
	static final boolean debug = false;
	
	static void pref_init(Context context){
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
		Editor edit = pref.edit();
		if( ! pref.contains("image_ram_limit") ) edit.putString("image_ram_limit","10");
		if( ! pref.contains("thumbnail_fetch_count") ) edit.putString("thumbnail_fetch_count","23");
		if( ! pref.contains("thumbnail_singletap_action") ) edit.putString("thumbnail_singletap_action",Intent.ACTION_VIEW);
		if( ! pref.contains("padding_color") ) edit.putInt("padding_color",Color.BLACK);
		if( ! pref.contains("output_width") ) edit.putString("output_width","0");
		if( ! pref.contains("output_height") ) edit.putString("output_height","0");

		if( ! pref.contains("padding_left") ) edit.putString("padding_left","0");
		if( ! pref.contains("padding_right") ) edit.putString("padding_right","0");
		if( ! pref.contains("padding_top") ) edit.putString("padding_top","0");
		if( ! pref.contains("padding_bottom") ) edit.putString("padding_bottom","0");
		edit.commit();
	}
	static final int parseInt(Object v,int def,int min,int max){
		try{
			int n = Integer.parseInt(v.toString());
			return n<min?min:n>max?max:n;
		}catch(NumberFormatException ex){
			return def;
		}
	}
	static final boolean checkInteger(Object v,int min,int max){
		try{
			int n= Integer.parseInt(v.toString());
			return (n<min||n>max)?false:true;
		}catch(NumberFormatException ex){
			return false;
		}
	}
	
	static final Bitmap.Config getBitmapConfig(Bitmap image,Bitmap.Config defval){
		if(image!=null){
			Bitmap.Config config = image.getConfig();
			if(config!=null) return config;
		}
		return defval;
	}

	
	@Override
	public void onCreate() {
		super.onCreate();
		if(debug) log.d("onCreate");
		
		ExceptionHandler.regist();
		Thread.yield();
	}	
	
	String a = null;
	void test(){
		log.d("this line raises error %d",a.length());
	}


}
