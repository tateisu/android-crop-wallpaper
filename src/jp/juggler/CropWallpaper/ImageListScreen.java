package jp.juggler.CropWallpaper;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

import jp.juggler.util.LogCategory;
import jp.juggler.util.WorkerBase;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;

public class ImageListScreen extends Activity {
	static LogCategory log = new LogCategory("CW_ImageList");

	GridView lvThumbnailView;
	ThumbnailLoader image_loader;
	SQLiteDatabase ui_db;
	ImageListAdapter adapter;
	Intent opener;
	MyDBOpenHelper db_helper;
	int thum_size;
	Handler ui_handler ;
	
	void initUI(){
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.thumbnail_list_screen);
        setProgressBarIndeterminateVisibility(true);
        lvThumbnailView = (GridView)findViewById(R.id.lvGrid);
        float density = getResources().getDisplayMetrics().density;
        thum_size = ( int)(0.5 + density * 150);
        log.d("density=%s thum_size=%s",density,thum_size);
        lvThumbnailView.setColumnWidth (thum_size);
        ui_handler = new Handler();
        
        
        image_loader = new ThumbnailLoader(this,thum_size);
        
		// make grid view 
        adapter = new ImageListAdapter(this,image_loader,new ArrayList<ImageInfo>(),thum_size,thum_size);
		lvThumbnailView.setAdapter(adapter);
        
        lvThumbnailView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int pos,long id) {
				startChild(pos,null);
			}
		});


        registerForContextMenu(lvThumbnailView);
	}
	
	////////////////////////////////////////////////////////////////////////////
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		log.d("onCreate");
		MyApp.pref_init(this);
		super.onCreate(savedInstanceState);
		initUI();
        
		db_helper = new MyDBOpenHelper(this);
        ui_db = db_helper.getWritableDatabase();

        init_page_args(getIntent());
	}
	@Override
	protected void onDestroy() {
		log.d("onDestroy");
		super.onDestroy();
		ui_db.close();
		if(list_loader != null ){
			list_loader.close();
			list_loader = null;
		}
	}

	@Override
	protected void onPause() {
		log.d("onPause");
		super.onPause();
		image_loader.worker_stop();
		list_loader.worker_stop();
	}

	@Override
	protected void onResume() {
		log.d("onResume");
		super.onResume();
		image_loader.worker_start( (ImageListAdapter)lvThumbnailView.getAdapter() );
		list_loader.worker_start();
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
    	log.d("onConfigurationChanged");
		super.onConfigurationChanged(newConfig);
	}	
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.thumbnail_context, menu);
	}
	
	static final int dialog_delete_warning =1;
	ImageInfo last_selected_item;

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo menu_info = (AdapterContextMenuInfo) item.getMenuInfo();
		int pos = menu_info.position;
		if(pos < 0 ) return super.onContextItemSelected(item);
		
		switch(item.getItemId()){
		default:
			return super.onContextItemSelected(item);
		case R.id.favorite:
			{
				ImageInfo info = adapter.getItem(pos);
				// toggle value
				boolean bFavorited = info.favorited = !info.favorited;
				// update db
				if(bFavorited){
					try{
						ui_db.execSQL("insert into favorites(_id,_data)values(?,?)",new Object[]{info.id,info.datapath});
					}catch(SQLException ex){
						ex.printStackTrace();
					}
				}else{
					try{
						ui_db.execSQL("delete from favorites where _id=?",new Object[]{info.id});
					}catch(SQLException ex){
						ex.printStackTrace();
					}
				}
				// update view
				adapter.notifyDataSetChanged();
			}
			return true;
		case R.id.view:
			startChild(pos,Intent.ACTION_VIEW);
			return true;
		case R.id.send:
			startChild(pos,Intent.ACTION_SEND);
			return true;
		case R.id.edit:
			startChild(pos,Intent.ACTION_EDIT);
			return true;
		case R.id.delete:
			last_selected_item = adapter.getItem(pos);
			showDialog(dialog_delete_warning);
			return true;
		
		}
	}

	@Override
	protected Dialog onCreateDialog(int id) {
	    switch(id) {
	    default:
	    	return null;
	    case dialog_delete_warning:
			{
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle(R.string.delete_warning_title);
				builder.setMessage("")
				       .setCancelable(true)
				       .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
				           @Override
						public void onClick(DialogInterface dialog, int id) {
				                dialog.dismiss();
				                delete_last_item();
				           }
				       })
				       .setNegativeButton("No", new DialogInterface.OnClickListener() {
				           @Override
						public void onClick(DialogInterface dialog, int id) {
				                dialog.cancel();
				           }
				       });
				AlertDialog alert = builder.create();
				return alert;
			}
	    }
	}
	
	@Override protected void onPrepareDialog(int id, Dialog dialog) {
		switch(id){
		default:
			super.onPrepareDialog(id, dialog);
			break;
		case dialog_delete_warning:
			AlertDialog ad = (AlertDialog)dialog;
			ad.setMessage(String.format(getResources().getString(R.string.delete_warning),last_selected_item.name));
			break;
		}
	}
	
	
	void delete_last_item(){
		log.d("delete_last_item..");
		Uri base_uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
		Uri item_uri = ContentUris.withAppendedId(base_uri, last_selected_item.id);
		ContentResolver cr = getContentResolver();
		cr.delete(item_uri,null,null);
		adapter.remove(last_selected_item);
	}

	////////////////////////////////////////////
	
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
	void init_page_args(Intent intent){
		if( opener != null ){
			String a = opener.getStringExtra("dirname");
			String b = intent.getStringExtra("dirname");
			if( (a==null && b==null) 
			||  (a!=null && b!=null && a.equals(b) )		
			){
				log.d("reuse opener");
				return;
			}
		}
		opener = intent;
		if( list_loader != null ) list_loader.close();
		list_loader = new ListLoader();
		list_loader.open();
	}
	void startChild(int pos ,String action){
		if(pos<0) return;
		ImageInfo info = adapter.getItem(pos);
		
		Intent intent = new Intent();
		Uri base_uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
		Uri item_uri = ContentUris.withAppendedId(base_uri, info.id);
		intent.putExtra(Intent.EXTRA_STREAM, item_uri);
		// contentsからmime type を取れるならdata,type もintentに設定する
		Cursor cur = managedQuery(item_uri,null, null, null, null);
		if( cur.moveToFirst() ){
			int col_mimetype = cur.getColumnIndex( MediaStore.Images.ImageColumns.MIME_TYPE);
			intent.setDataAndType(item_uri,cur.getString(col_mimetype));
		}
		cur.close();
		
		if( action!=null){
			// 行うアクションが明示されている
			intent.setAction(action);
		}else 
		if( Intent.ACTION_GET_CONTENT.equals(opener.getAction())
		||	Intent.ACTION_PICK.equals(opener.getAction())
		){
			// シングルタップで、選択用に呼ばれた場合
			setResult(-1,intent);
			finish();
			return;
		}else{
			// シングルタップで、アクションは設定から読む
			String pref_val = PreferenceManager.getDefaultSharedPreferences(ImageListScreen.this).getString("thumbnail_singletap_action",Intent.ACTION_VIEW);
			intent.setAction(pref_val);
		}

		
		try{
			startActivity(intent);
			adapter.notifyDataSetChanged();
		}catch(ActivityNotFoundException ex ){
			Toast toast = Toast.makeText(this,getText(R.string.no_activity),Toast.LENGTH_SHORT);
			toast.show();
		}
	}	

	ListLoader list_loader = null;
	class ListLoader {
		final Uri base_uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
		String dirname;
		boolean bDirectoryFilter;
		boolean bComplete = false;
		SQLiteDatabase worker_db;
		Cursor cur;
		int idx_id,idx_data,idx_name;
		ConcurrentLinkedQueue<ImageInfo> queue = new ConcurrentLinkedQueue<ImageInfo>();

		void open(){
			log.d("list loader open");
			
			worker_db = db_helper.getReadableDatabase();
			dirname = opener.getStringExtra("dirname");
			if( dirname != null ){
				bDirectoryFilter = ( dirname.length() > 0 );
				setTitle( bDirectoryFilter ? dirname : getText(R.string.all_images) );
	
				// load image info
				cur = getContentResolver().query(base_uri,null, null, null, "_data");
				if(!cur.moveToFirst()){
					bComplete = true;
				}else{
		    		idx_id   = cur.getColumnIndex(MediaStore.Images.ImageColumns._ID);
		    		idx_data = cur.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
		    		idx_name = cur.getColumnIndex(MediaStore.Images.ImageColumns.TITLE);
				}
			}else{
				cur = worker_db.rawQuery("select _id from favorites order by _data",null);
				if(!cur.moveToFirst()){
					bComplete = true;
				}else{
		    		idx_id   = cur.getColumnIndex("_id");
				}
			}
		}
		void close(){
			log.d("list loader close");
			worker_stop();
			if( cur != null) cur.close();
			worker_db.close();
		}
		void worker_start(){
			if(bComplete){
				setProgressBarIndeterminateVisibility(false);
			}else{
				worker = new Worker();
				worker.setPriority(Thread.NORM_PRIORITY);
				worker.start();
			}
		}
		void worker_stop(){
			if(worker!=null) worker.joinLoop(log,"list_loader");
		}
		
		Worker worker;
		class Worker extends WorkerBase{
			boolean bCancelled = false;
			
			@Override
			public void cancel() {
				bCancelled = true;
				notifyEx();
			}

			@Override
			public void run(){
				log.d("list worker start. position=%d",cur.getPosition());
				log.d("list worker folder_mode=%s",(dirname != null ) );

				String[] bind1 = new String[1];
				while(!bCancelled && !bComplete){
					if( dirname != null ){
		        		String path = cur.getString(idx_data);
	        			int pos = path.lastIndexOf('/');
		        		if( bDirectoryFilter && !dirname.equals( path.substring(0,pos)) ){
		        			// not in dir
		        		}else{
			        		ImageInfo info = new ImageInfo();
			        		info.id = cur.getLong(idx_id);
			        		info.datapath = path;
			        		info.name = cur.getString(idx_name);
			        		info.showState = 0;
			        		
			        		// check favorites
			        		bind1[0]=Long.toString(info.id);
			        		Cursor cur2 = worker_db.rawQuery (" select _id from favorites where _id=?",bind1);
			        		try{
			        			if(cur2.moveToFirst()) info.favorited = true;
			        		}finally{
			        			cur2.close();
			        		}
			        		queue.add(info);
		        		}
					}else{
						long id = cur.getLong(idx_id);
						Uri item_uri = ContentUris.withAppendedId(base_uri,id);
						Cursor cur2 = managedQuery(item_uri,null, null, null, null);
						try{
							if(cur2.moveToFirst() ){
					    		int idx_data = cur2.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
					    		int idx_name = cur2.getColumnIndex(MediaStore.Images.ImageColumns.TITLE);
				        		ImageInfo info = new ImageInfo();
				        		info.id = id;
				        		info.datapath = cur2.getString(idx_data);
				        		info.name = cur2.getString(idx_name);
				        		info.favorited = true;
				        		queue.add(info);
							}
						}finally{
							cur2.close();
						}
					}
					if( queue.size() > 0){
						ui_handler.post(new Runnable(){
							@Override
							public void run() {
								if( ImageListScreen.this.isFinishing() ) return;
								for(;;){
									ImageInfo info = queue.poll();
									if(info==null) break;
									adapter.add(info);
								}
							}
						});
					}
					
					if( cur.moveToNext() ) continue;
					
					// 終端
					bComplete = true;
					log.d("list_loader complete!");
					ui_handler.post(new Runnable(){
						@Override
						public void run() {
							if( isFinishing() ) return;
							setProgressBarIndeterminateVisibility(false);
						}
					});
				}
				log.d("list worker breaked. position=%d",cur.getPosition());
			}
		}
	}
	


}
