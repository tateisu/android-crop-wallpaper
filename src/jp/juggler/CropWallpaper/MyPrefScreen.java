package jp.juggler.CropWallpaper;

import jp.juggler.util.LogCategory;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceChangeListener;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;
import android.content.SharedPreferences.Editor;
import android.graphics.Rect;
import android.os.Bundle;

public class MyPrefScreen extends PreferenceActivity {
	static final LogCategory log = new LogCategory("MyPrefScreen");
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		MyApp.pref_init(this);
		addPreferencesFromResource(R.xml.preference_screen);
		


		
		{
			EditTextPreference pref = (EditTextPreference)findPreference("image_ram_limit");
			EditText et = pref.getEditText();
			et.setHorizontallyScrolling(true);
			et.setSingleLine(true);
			et.setInputType(InputType.TYPE_CLASS_NUMBER);
			
			pref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					if(!MyApp.checkInteger(newValue,5,100)){
						Toast.makeText(MyPrefScreen.this,R.string.image_ram_limit_error,Toast.LENGTH_SHORT).show();
						return false;
					}else{
						preference.setSummary(String.format(
								getResources().getString(R.string.image_ram_limit_desc)
								,MyApp.parseInt(newValue.toString(),-1,5,100)
							));
						return true;
					}
				}
			});
			pref.getOnPreferenceChangeListener().onPreferenceChange(pref,pref.getText());
		}
		{
			EditTextPreference pref = (EditTextPreference)findPreference("thumbnail_fetch_count");
			EditText et = pref.getEditText();
			et.setHorizontallyScrolling(true);
			et.setSingleLine(true);
			et.setInputType(InputType.TYPE_CLASS_NUMBER);

			pref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					if(!MyApp.checkInteger(newValue,15,9999)){
						Toast.makeText(MyPrefScreen.this,R.string.thumbnail_fetch_count_error,Toast.LENGTH_SHORT).show();
						return false;
					}else{
						preference.setSummary(String.format(
								getResources().getString(R.string.thumbnail_fetch_count_desc)
								,MyApp.parseInt(newValue.toString(),-1,15,9999)
							));
						return true;
					}
				}
			});
			pref.getOnPreferenceChangeListener().onPreferenceChange(pref,pref.getText());
		}
		{
			ListPreference pref = (ListPreference)findPreference("thumbnail_singletap_action");
			pref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
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
	@Override protected void onPause() {
		super.onPause();

    	Rect rect= new Rect();
		getWindow().getDecorView().getWindowVisibleDisplayFrame(rect);
		if(rect.top > 0 ){
	    	Editor edit = PreferenceManager.getDefaultSharedPreferences(this).edit();
	    	edit.putInt("status_bar_height",rect.top);
	    	edit.commit();
	    	log.d("status bar height=%d",rect.top);	
		}

    }
}
