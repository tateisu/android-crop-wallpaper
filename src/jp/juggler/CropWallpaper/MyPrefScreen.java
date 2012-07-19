package jp.juggler.CropWallpaper;

import jp.juggler.util.LogCategory;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceChangeListener;
import android.text.InputType;
import android.view.Display;
import android.view.View;
import android.view.ViewParent;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;
import android.content.Context;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.IBinder;

public class MyPrefScreen extends PreferenceActivity {
	static final LogCategory log = new LogCategory("MyPrefScreen");
	
	void initNumberEdit(String key,int min,int max,int def,int desc_string_id){
		EditTextPreference pref = (EditTextPreference)findPreference(key);
		EditText et = pref.getEditText();
		et.setHorizontallyScrolling(true);
		et.setSingleLine(true);
		et.setInputType(InputType.TYPE_CLASS_NUMBER);

		final int _min = min;
		final int _max = max;
		final int _def = def;
		final int _desc_string_id = desc_string_id;
		
		pref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				if(!MyApp.checkInteger(newValue,_min,_max)){
					Toast.makeText(MyPrefScreen.this,R.string.number_range_error,Toast.LENGTH_SHORT).show();
					return false;
				}else{
					preference.setSummary(String.format(
							getResources().getString(_desc_string_id)
							,MyApp.parseInt(newValue.toString(),-_def,_min,_max)
						));
					return true;
				}
			}
		});
		
		pref.getOnPreferenceChangeListener().onPreferenceChange(pref,pref.getText());
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// マルチプロセスで問題がでないようにする
		getPreferenceManager().setSharedPreferencesMode(4); // Context.MULTI_PROCESS_MODE
		
		
	    InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
	    IBinder token = getListView().getRootView().getWindowToken();
	    imm.hideSoftInputFromInputMethod(token,InputMethodManager.HIDE_IMPLICIT_ONLY);
	    imm.hideSoftInputFromWindow(token,InputMethodManager.HIDE_IMPLICIT_ONLY);

		
		MyApp.pref_init(this);
		addPreferencesFromResource(R.xml.preference_screen);
		
		initNumberEdit("image_ram_limit",5,100,-1,R.string.image_ram_limit_desc);
		initNumberEdit("thumbnail_fetch_count",15,9999,-1,R.string.thumbnail_fetch_count_desc);
		initNumberEdit("output_width",0,99999,0,R.string.output_width_desc);
		initNumberEdit("output_height",0,99999,0,R.string.output_height_desc);
		initNumberEdit("padding_left",0,99999,0,R.string.padding_desc);
		initNumberEdit("padding_right",0,99999,0,R.string.padding_desc);
		initNumberEdit("padding_top",0,99999,0,R.string.padding_desc);
		initNumberEdit("padding_bottom",0,99999,0,R.string.padding_desc);

		
		{
			ListPreference pref = (ListPreference)findPreference("thumbnail_singletap_action");
			pref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					ListPreference pref = (ListPreference)preference;
					int idx = pref.findIndexOfValue(newValue.toString());
					CharSequence[] entry_list = pref.getEntries();
					preference.setSummary(String.format(
						 getResources().getString(R.string.thumbnail_singletap_action_desc)
						,entry_list[idx]
					));
					return true;
				}
			});
			pref.getOnPreferenceChangeListener().onPreferenceChange(pref,pref.getValue());
		}
		
		
	}
	
	public static View getViewRoot(View v){
		ViewParent parent = v.getParent();
		if( parent != null && parent instanceof View ){
			return getViewRoot((View)parent);
		}
		return v;
	}
	
	@Override protected void onPause() {
		super.onPause();

    	View root = getViewRoot(getListView());
    	if( root != null ){
    		int view_height = root.getHeight();
    		if( view_height != 0 ){
				Window window = getWindow();
		    	Rect rect= new Rect();
		    	window.getDecorView().getWindowVisibleDisplayFrame(rect);
				int screen_bar_height = rect.top;
				
    			Display display = getWindowManager().getDefaultDisplay();
    			int display_height_no_navi =  display.getHeight();
    			int display_width_no_navi = display.getWidth();
    			
    			int display_height = display_height_no_navi;
    			try{
    				int rw = (Integer)Display.class.getMethod("getRawWidth").invoke(display);
    				int rh = (Integer)Display.class.getMethod("getRawHeight").invoke(display);
    				switch(getResources().getConfiguration().orientation){
    				case Configuration.ORIENTATION_PORTRAIT:
    					if( rw > rh ){ int tmp = rw; rw = rh; rh = tmp; }
    					break;
    				case Configuration.ORIENTATION_LANDSCAPE:
    					if( rw < rh ){ int tmp = rw; rw = rh; rh = tmp; }
    					break;
    				default:
    					break;
    				}
    				if( rw != display_width_no_navi ){
    					// ナビゲーションバーが右に出ていると思われる。この状態ではナビゲーションバーの高さを測定できない。
    				}else if( rh != display_height_no_navi ){
    					// ナビゲーションバーが下に出ていると思われる。
    					display_height = rh;
    				}
    				
    			}catch(NoSuchMethodException ex){
    				// 2.x まではここを通る。ナビゲーションばーはないので問題ない
    			}catch(Throwable ex){
    				ex.printStackTrace();
    			}
				
				int navigation_bar_height = 0;
				if(  display_height > display_height_no_navi ){
					navigation_bar_height = display_height -display_height_no_navi;
    			}
				log.d("display_height=%s",display_height );
				log.d("view_height=%s",view_height );

		    	log.d("status bar height=%d",screen_bar_height);	
				log.d("navigation_bar_height=%s",navigation_bar_height );
				
				if( screen_bar_height != 0 ){
					Editor edit = PreferenceManager.getDefaultSharedPreferences(this).edit();
					edit.putInt("status_bar_height",screen_bar_height);
					if(navigation_bar_height!=0)  edit.putInt("navigation_bar_height",navigation_bar_height);
					edit.commit();
				}
    		}
    	}
    	

    }
}
