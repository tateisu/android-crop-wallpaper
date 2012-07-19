package jp.juggler.CropWallpaper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Color;
import android.os.IBinder;
import android.preference.DialogPreference;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ToggleButton;

public class ColorPreference extends DialogPreference {

    SharedPreferences pref;

    SeekBar sbRed;
    SeekBar sbGreen;
    SeekBar sbBlue;
    SeekBar sbGrey;
    
    TextView tvRed;
    TextView tvGreen;
    TextView tvBlue;
    TextView tvGrey;

    TextView tvSample;
    EditText etSampleHex;
    ToggleButton tbColor;
    ToggleButton tbHex;
    

    int defaultColor = 0;
    
    public ColorPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        pref = PreferenceManager.getDefaultSharedPreferences(context);

        setDialogLayoutResource(R.layout.dlg_color);
    }

	@Override
    protected void onBindDialogView(View v) {
        super.onBindDialogView(v);

/*
       
*/
        tvSample = (TextView)v.findViewById(R.id.tvSample);
        etSampleHex = (EditText)v.findViewById(R.id.etSampleHex);
        tbColor = (ToggleButton) v.findViewById(R.id.tbColor);
        tbHex = (ToggleButton) v.findViewById(R.id.tbHex);

        sbRed = (SeekBar)v.findViewById(R.id.sbRed);
        sbGreen = (SeekBar)v.findViewById(R.id.sbGreen);  
        sbBlue = (SeekBar)v.findViewById(R.id.sbBlue); 
        sbGrey = (SeekBar)v.findViewById(R.id.sbGrey); 

        tvRed = (TextView)v.findViewById(R.id.tvRed);
        tvGreen = (TextView)v.findViewById(R.id.tvGreen);
        tvBlue = (TextView)v.findViewById(R.id.tvBlue);
        tvGrey = (TextView)v.findViewById(R.id.tvGrey);

        tbColor.requestFocus();
/*
       
*/
        // 値をUIに設定する
        int color = pref.getInt(getKey(), defaultColor);
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        
        sbRed.setProgress(r);
        sbGreen.setProgress(g);
        sbBlue.setProgress(b);
        sbGrey.setProgress((r+g+b+1)/3);
        
    	setColorMode( r != g || r != b );

        // 表示の更新
        update();

        // リスナ登録は最後に行うこと
        sbRed.setOnSeekBarChangeListener(sb_listener);  
        sbGreen.setOnSeekBarChangeListener(sb_listener);  
        sbBlue.setOnSeekBarChangeListener(sb_listener);  
        sbGrey.setOnSeekBarChangeListener(sb_listener_mono);  

        etSampleHex.addTextChangedListener(new TextWatcher() {
        	Pattern p = Pattern.compile("#([0-9A-Fa-f]{2})([0-9A-Fa-f]{2})([0-9A-Fa-f]{2})");
        	
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {}
			
			@Override public void beforeTextChanged(CharSequence s, int start, int count,int after) {}

			@Override public void afterTextChanged(Editable s) {
				if(in_update) return;

				Matcher m = p.matcher(etSampleHex.getText());
				if( ! m.find() ) return;

				int r = Integer.parseInt(m.group(1),16);
				int g = Integer.parseInt(m.group(2),16);
				int b = Integer.parseInt(m.group(3),16);
				int v = (r+g+b+1)/3;
				
				boolean bUpdate = false;

				in_update = true;
				if( r != sbRed.getProgress() ){
					sbRed.setProgress(r);
					bUpdate = true;
				}
				if( g != sbGreen.getProgress() ){
					sbGreen.setProgress(g);
					bUpdate = true;
				}
				if( b != sbBlue.getProgress() ){
					sbBlue.setProgress(b);
					bUpdate = true;
				}
				if( v != sbGrey.getProgress() ){
					sbGrey.setProgress(v);
					bUpdate = true;
				}
				in_update = false;

				if( ! tbColor.isChecked() && ( r != g || r != b ) ){
					setColorMode(true);
				}
				
				if(bUpdate) update();
			}
		});
        
        tbColor.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				setColorMode(isChecked);
			}
		});
        
        tbHex.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				etSampleHex.setVisibility(isChecked? View.VISIBLE : View.INVISIBLE);
				if(!isChecked){
					 getDialog().getWindow().setSoftInputMode(
			        		WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
			        		| WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
			        );
					 // EditText が自動的にsoft keyboard を開くのを抑止する
					 Context c = etSampleHex.getContext();
					 IBinder token = etSampleHex.getWindowToken();
				     InputMethodManager imm = (InputMethodManager)c.getSystemService(Context.INPUT_METHOD_SERVICE);
				     imm.hideSoftInputFromInputMethod(token,InputMethodManager.HIDE_IMPLICIT_ONLY);
				     imm.hideSoftInputFromWindow(token,0);
				}
			}
		});
    }

    SeekBar.OnSeekBarChangeListener sb_listener = new SeekBar.OnSeekBarChangeListener(){
    	@Override
        public void onProgressChanged(SeekBar seekBar, int progress,boolean fromUser) {
    		if(in_update) return;
    		int v = (sbRed.getProgress() + sbGreen.getProgress() + sbBlue.getProgress() )/3;
    		in_update=true;
    		sbGrey.setProgress(v);
    		in_update=false;
            update();
        }
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {
        	update_text();
        }
    };
    
    SeekBar.OnSeekBarChangeListener sb_listener_mono = new SeekBar.OnSeekBarChangeListener(){
    	@Override
        public void onProgressChanged(SeekBar seekBar, int progress,boolean fromUser) {
    		if(in_update) return;
    		int v = sbGrey.getProgress();
    		in_update=true;
    		sbRed.setProgress(v);
    		sbGreen.setProgress(v);
    		sbBlue.setProgress(v);
    		in_update=false;
            update();
        }
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {
        	update_text();
        }
    };
    
    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if( ! positiveResult ) return;
        
        int r = sbRed.getProgress();
    	int g = sbGreen.getProgress();
    	int b = sbBlue.getProgress();

    	Editor e = pref.edit();
        e.putInt(getKey(), Color.rgb(r,g,b));
        e.commit();
    }
    
    public void setDefault(int col) {
        defaultColor = col;
    }
   
    // 
    void setColorMode(boolean colored){
    	tbColor.setChecked(colored);

    	sbGrey.setVisibility( colored ? View.INVISIBLE : View.VISIBLE);
    	tvGrey.setVisibility( colored ? View.INVISIBLE : View.VISIBLE);
    	
    	sbRed.setVisibility( colored ? View.VISIBLE : View.INVISIBLE);
    	tvRed.setVisibility( colored ? View.VISIBLE : View.INVISIBLE );
    	sbGreen.setVisibility( colored ? View.VISIBLE : View.INVISIBLE );
    	tvGreen.setVisibility( colored ? View.VISIBLE : View.INVISIBLE );
    	sbBlue.setVisibility( colored ? View.VISIBLE : View.INVISIBLE );
    	tvBlue.setVisibility( colored ? View.VISIBLE : View.INVISIBLE );
    	
    	if(! colored ){
    		int v = (sbRed.getProgress() + sbGreen.getProgress() + sbBlue.getProgress() + 1 ) /3;
    		sbRed.setProgress(v);
    		sbGreen.setProgress(v);
    		sbBlue.setProgress(v);
    		update();
    	}
    }

    // イベント抑制用
    boolean in_update = false;

    // 表示の更新
    private void update(){
    	int r = sbRed.getProgress();
    	int g = sbGreen.getProgress();
    	int b = sbBlue.getProgress();
    	int v = sbGrey.getProgress();
    	tvSample.setBackgroundColor(Color.rgb(r,g,b));
    	tvRed.setText(Integer.toString(r));
    	tvGreen.setText(Integer.toString(g));
    	tvBlue.setText(Integer.toString(b));
    	tvGrey.setText( Integer.toString(v) );
    	in_update = true;
    	String hex = String.format("#%02x%02x%02x",r,g,b);
    	if( ! hex.equals( etSampleHex.getText().toString() )){
    		etSampleHex.setText(hex);
    		etSampleHex.setSelection(hex.length());
    	}
    	in_update = false;
    }
    
    void update_text(){
    	if(in_update)return;
    	int r = sbRed.getProgress();
    	int g = sbGreen.getProgress();
    	int b = sbBlue.getProgress();
    	in_update = true;
    	String hex = String.format("#%02x%02x%02x",r,g,b);
    	if( ! hex.equals( etSampleHex.getText().toString() )){
    		etSampleHex.setText(hex);
    		etSampleHex.setSelection(hex.length());
    	}
    	in_update = false;
    	
    }
    
    
}
