package jp.juggler.CropWallpaper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map.Entry;

import jp.juggler.util.LogCategory;
import jp.juggler.util.WorkerBase;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.AdapterView.OnItemClickListener;

public class FolderListScreen extends Activity {
	static LogCategory log = new LogCategory("CW_FolderList");

	ListView lvDirectory;
	SQLiteDatabase ui_db;
	Intent opener;
	Handler ui_handler;
	MyDBOpenHelper helper;
	DirListAdapter adapter;
	
	void initUI(){
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.folder_list_screen);
        ui_handler = new Handler();
        lvDirectory = (ListView)findViewById(R.id.lvDirectory);

        
        adapter = new DirListAdapter(this,new ArrayList<DirInfo>());
        lvDirectory.setAdapter(adapter);
        lvDirectory.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int pos,long id) {
				DirListAdapter adapter = (DirListAdapter)parent.getAdapter();
				DirInfo info = adapter.getItem(pos);
				// open grid view
				Intent intent = new Intent();
				intent.setAction(opener.getAction());
				intent.setComponent(new ComponentName(getPackageName(),ImageListScreen.class.getName()));
				if( ! info.bFavorites ) intent.putExtra("dirname",info.name);
				startActivityForResult(intent,1);
			}
		});
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		try{
			log.d("onActivityResult req="+requestCode+",result="+resultCode);
			log.d("onActivityResult data="+(data==null?"null":data.toString()));
		}catch(Throwable ex){
			ex.printStackTrace();
		}
		if( data != null ){
			if( Intent.ACTION_GET_CONTENT.equals(opener.getAction() )
			||  Intent.ACTION_PICK.equals(opener.getAction() )
			){
				setResult(-1, data);
				finish();
			}
		}
	}

	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	log.d("onCreate");
        super.onCreate(savedInstanceState);
        initUI();
        helper = new MyDBOpenHelper(this);
        ui_db = helper.getWritableDatabase();
        init_page(getIntent());
    }
	@Override
	protected void onDestroy() {
    	log.d("onDestroy");
		super.onDestroy();
		ui_db.close();
		if(list_loader!=null){
    		list_loader.close();
    		list_loader = null;
    	}
	}

	
	////////////////////////////////////////////
	
	@Override
	protected void onResume() {
    	log.d("onResume");
		super.onResume();
		list_loader.resume();
	}


	@Override
	protected void onPause() {
    	log.d("onPause");
		super.onPause();
		list_loader.pause();
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
    	log.d("onConfigurationChanged");
		super.onConfigurationChanged(newConfig);
	}

	@Override public boolean onCreateOptionsMenu(Menu menu) {
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.option_menu, menu);
	    return true;
	}
	@Override public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	    default:
	        return super.onOptionsItemSelected(item);
	    case R.id.menuPref:
			startActivity(new Intent(this,MyPrefScreen.class));
			return true;
	    }
	}
	
	////////////////////////////////////////////
    void init_page(Intent intent){
    	opener = intent;
    	if(list_loader!=null){
    		list_loader.close();
    		list_loader = null;
    	}
    	list_loader = new Listloader();
    	list_loader.open();
    }
    
    Listloader list_loader;
    class Listloader {
    	SQLiteDatabase worker_db;
    	Cursor cur;
    	HashMap<String,DirInfo> dir_map = new HashMap<String,DirInfo>();
    	boolean bComplete;
    	int idx_data;
    	void open(){
    		worker_db = helper.getWritableDatabase();
    		// お気に入り
        	{
		        DirInfo favorites = new DirInfo();
		        favorites.bFavorites=true;
		        favorites.name=getResources().getString(R.string.favorite);
		        {
		        	cur = worker_db.rawQuery("select count(*) from favorites",null);
		        	if(cur.moveToFirst()){
		        		favorites.count = cur.getInt(0);
		        	}
		        	cur.close();
		        }
		        adapter.add(favorites);
        	}
        	cur = getContentResolver().query(
        			MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    			,new String[]{
    				MediaStore.Images.ImageColumns.DATA
    			}
	    		, null
	    		, null
	    		, null
	        );
        	if( !cur.moveToFirst() ){
        		bComplete = true;
        		cur.close();
        		cur = null;
        	}else{
        		idx_data = cur.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
        	}
    	}
    	void close(){
    		if(cur!=null){
    			cur.close();
    			cur = null;
    		}
    		worker_db.close();
    	}
    	void pause(){
    		if(worker!=null){
    			worker.joinLoop(log, "list_loader");
    			worker=null;
    		}
    	}
    	void resume(){
    		if(bComplete){
				setProgressBarIndeterminateVisibility(false);
				return;
    		}
    		setProgressBarIndeterminateVisibility(true);
    		if(worker==null){
    			worker = new Worker();
    			worker.start();
    		}
    	}

    	Worker worker;
    	class Worker extends WorkerBase{
    		volatile boolean bCancelled = false;
			@Override
			public void cancel() {
				bCancelled = true;
			}
			public void run(){
				try{
					while(!bCancelled && !bComplete){
		            	String path = cur.getString(idx_data);
		            	int end = path.lastIndexOf('/');
		            	String dirname = path.substring(0,end);
		            	DirInfo d = dir_map.get(dirname);
		            	if(d==null){
		            		d = new DirInfo();
		            		d.name = dirname;
		            		dir_map.put(dirname,d);
		            	}
			            ++d.count;
			            
			            if(!cur.moveToNext()){
			            	bComplete = true;
			            	ui_handler.post(new Runnable() {
								@Override
								public void run() {
									if(isFinishing()) return;
									ArrayList<DirInfo> list = new ArrayList<DirInfo>();
							        for(Entry<String,DirInfo> entry : dir_map.entrySet() ){
							        	list.add( entry.getValue() ); 
							        }
							        Collections.sort(list,new Comparator<DirInfo>(){
										@Override public int compare(DirInfo a, DirInfo b) {
											return a.name.compareToIgnoreCase(b.name);
										}
							        });
							        for(DirInfo item : list ){
							        	adapter.add(item);
							        }
							        setProgressBarIndeterminateVisibility(false);
								}
							});
			            }
					}
				}catch (Throwable ex) {
					ex.printStackTrace();
				}
			}
    	}
    }
}