package jp.juggler.CropWallpaper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

import jp.juggler.util.LogCategory;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;

public class ImageListScreen extends Activity {
	static LogCategory log = new LogCategory("ImageListScreen");

	GridView lvThumbnailView;
	ThumbnailLoader loader;
	SQLiteDatabase db;

	int thum_size;
	
	void initUI(){
        setContentView(R.layout.thumbnail_list_screen);
        lvThumbnailView = (GridView)findViewById(R.id.lvGrid);
        float density = getResources().getDisplayMetrics().density;
        thum_size = ( int)(0.5 + density * 150);
        log.d("density=%s thum_size=%s",density,thum_size);
        lvThumbnailView.setColumnWidth (thum_size);
        
        
        loader = new ThumbnailLoader(this,thum_size);
        
        lvThumbnailView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int pos,long id) {
				Intent intent = new Intent();

				ImageListAdapter adapter = (ImageListAdapter)parent.getAdapter();
				ImageInfo info = adapter.getItem(pos);
				Uri base_uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
				Uri item_uri = ContentUris.withAppendedId(base_uri, info.id);
				intent.setAction(Intent.ACTION_SEND);
				intent.putExtra(Intent.EXTRA_STREAM, item_uri);

				Cursor cur = managedQuery(item_uri,null, null, null, null);
				if (cur.moveToFirst()) {
					int col_mimetype = cur.getColumnIndex( MediaStore.Images.ImageColumns.MIME_TYPE);
					intent.setDataAndType(item_uri,cur.getString(col_mimetype));
					
				}
				cur.close();
				adapter.notifyDataSetChanged();
				startActivity(intent);
			}
        	
		});


        registerForContextMenu(lvThumbnailView);
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		initUI();
        
		MyDBOpenHelper helper = new MyDBOpenHelper(this);
        db = helper.getWritableDatabase();

        init_page_args(getIntent());
	}
	@Override
	protected void onDestroy() {
		super.onDestroy();
		db.close();
	}

	@Override
	protected void onPause() {
		loader.worker_stop();
		super.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();
		loader.worker_start( (ImageListAdapter)lvThumbnailView.getAdapter() );
		
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.thumbnail_context, menu);
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		switch(item.getItemId()){
		default:
			return super.onContextItemSelected(item);
		case R.id.favorite:
			{
				AdapterContextMenuInfo menu_info = (AdapterContextMenuInfo) item.getMenuInfo();
				int pos = menu_info.position;
				if( pos != -1 ){
					ImageListAdapter adapter = (ImageListAdapter)lvThumbnailView.getAdapter();
					ImageInfo info = adapter.getItem(pos);
					// toggle value
					boolean bFavorited = info.favorited = !info.favorited;
					// update db
					if(bFavorited){
						try{
							db.execSQL("insert into favorites(_id)values(?)",new Object[]{info.id});
						}catch(Throwable ex){
							ex.printStackTrace();
						}
					}else{
						try{
							db.execSQL("delete from favorites where _id=?",new Object[]{info.id});
						}catch(Throwable ex){
							ex.printStackTrace();
						}
					}
					// update view
					adapter.notifyDataSetChanged();
				}
			}
			return true;
		}
	}



	////////////////////////////////////////////
	void init_page_args(Intent intent){
		ArrayList<ImageInfo> list = new ArrayList<ImageInfo>();

		String dirname = intent.getStringExtra("dirname");
		if( dirname != null ){
			boolean bDirectoryFilter = ( dirname.length() > 0 );
			setTitle( bDirectoryFilter ? dirname : getText(R.string.all_images) );

			HashMap<Long,ImageInfo> map = new HashMap<Long,ImageInfo>();
			// load image info
			Uri base_uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
			Cursor cur = managedQuery(base_uri,null, null, null, "_data");
			if (cur.moveToFirst()) {
/*
				String[] col_meta = new String[]{
	        			 "t", MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME // The bucket display name of the image.
	        			,"t", MediaStore.Images.ImageColumns.BUCKET_ID  // The bucket id of the image.
	        			,"i", MediaStore.Images.ImageColumns.DATE_TAKEN // The date & time that the image was taken in units of milliseconds since jan 1, 1970.
	        			,"t", MediaStore.Images.ImageColumns.DESCRIPTION // TEXT: The description of the image
	        			,"i", MediaStore.Images.ImageColumns.IS_PRIVATE // INTEGER: Whether the video should be published as public or private
	        			,"t", MediaStore.Images.ImageColumns.LATITUDE // The latitude where the image was captured.
	        			,"t", MediaStore.Images.ImageColumns.LONGITUDE // The longitude where the image was captured.
	        			,"t", MediaStore.Images.ImageColumns.MINI_THUMB_MAGIC // The mini thumb id.
	        			,"i", MediaStore.Images.ImageColumns.ORIENTATION // The orientation for the image expressed as degrees.
	        			,"t", MediaStore.Images.ImageColumns.PICASA_ID // TEXT: The picasa id of the image
	        			,"i", MediaStore.Images.ImageColumns._COUNT // The count of rows in a directory.
	        			,"i", MediaStore.Images.ImageColumns._ID // The unique ID for a row.
	        			,"t", MediaStore.Images.ImageColumns.DATA // DATA STREAM: The data stream for the file
	        			,"i", MediaStore.Images.ImageColumns.DATE_ADDED // The time the file was added to the media provider Units are seconds since 1970.
	        			,"i", MediaStore.Images.ImageColumns.DATE_MODIFIED // The time the file was last modified Units are seconds since 1970.
	        			,"t", MediaStore.Images.ImageColumns.DISPLAY_NAME // TEXT: The display name of the file
	        			,"t", MediaStore.Images.ImageColumns.MIME_TYPE // The MIME type of the file
	        			,"i", MediaStore.Images.ImageColumns.SIZE // INTEGER (long) : The size of the file in bytes
	        			,"t", MediaStore.Images.ImageColumns.TITLE // TEXT: The title of the content
	        	};
	    		int end = col_meta.length;
	        	int[] col_meta_int = new int[col_meta.length];
	    		for(int i=0;i<end;i+=2){
	    			col_meta_int[i]   = (col_meta[i].equals("t")?0:1);
	    			col_meta_int[i+1] = cur.getColumnIndex(col_meta[i+1]);
	    		}
*/
	    		int idx_id = cur.getColumnIndex(MediaStore.Images.ImageColumns._ID);
	    		int idx_data = cur.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
	    		int idx_name = cur.getColumnIndex(MediaStore.Images.ImageColumns.TITLE);
	        	do{
/*
 * 	        		// Uri item_uri = ContentUris.withAppendedId(base_uri, cur.getLong(idx_id));
	        		log.d("--------------------------");
	        		log.d("itemURI=%s",item_uri);
	    			String vt;
	    			long vi;
	        		for(int i=0;i<end;i+=2){
	        			String name = col_meta[i+1];
	        			int typeint = col_meta_int[i];
	        			int nameint = col_meta_int[i+1];
	        			if(nameint==-1) continue;
	        			try{
		        			switch(typeint){
		        			default:
		        			case 0: // text
		    					vt = cur.getString(nameint);
		    					log.d("%s=(text)%s",name,vt);
		        				break;
		        			case 1: // 64-bit integer
		        				vi = cur.getLong(nameint);
		    					log.d("%s=(int)%d",name,vi);
		        				break;
		        			}
	        			}catch(Throwable ex){
	        				log.d("%s=(error)%s",name,ex.getMessage());
		        			}
		        		}
 */
	        		String path = cur.getString(idx_data);
	        		if( bDirectoryFilter ){
	        			int pos = path.lastIndexOf('/');
	        			if(! dirname.equals( path.substring(0,pos))) continue;
	        		}
	        		
	        		ImageInfo info = new ImageInfo();
	        		info.id = cur.getLong(idx_id);
	        		info.datapath = path;
	        		info.name = cur.getString(idx_name);
	        		info.showState = 0;
	        		list.add(info);
	        		map.put(info.id,info);
	        	}while(cur.moveToNext());
	        	
			}
		    cur.close();
			// check favorites
			cur = db.query("favorites",null,null,null,null,null,null);
			if (cur.moveToFirst()) {
				int id_col = cur.getColumnIndex("_id");
				do{
					long id = cur.getLong(id_col);
					ImageInfo info = map.get(id);
					if(info!=null){
						info.favorited = true;
					}
				}while(cur.moveToNext());
			}
			cur.close();
		}else{
			setTitle(getString(R.string.favorite));
			// list of favorites
			Cursor cur1 = db.query("favorites",null,null,null,null,null,null);
			if (cur1.moveToFirst()) {
				int id_col = cur1.getColumnIndex("_id");
				do{
					long id = cur1.getLong(id_col);
					
					Uri base_uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
					Uri item_uri = ContentUris.withAppendedId(base_uri,id);
					Cursor cur = managedQuery(item_uri,null, null, null, null);
					if (cur.moveToFirst()) {
			    		int idx_id = cur.getColumnIndex(MediaStore.Images.ImageColumns._ID);
			    		int idx_data = cur.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
			    		int idx_name = cur.getColumnIndex(MediaStore.Images.ImageColumns.TITLE);
		        		String path = cur.getString(idx_data);
		        		if( dirname != null ){
		        			int pos = path.lastIndexOf('/');
		        			if(! dirname.equals( path.substring(0,pos))) continue;
		        		}
		        		ImageInfo info = new ImageInfo();
		        		info.id = cur.getLong(idx_id);
		        		info.datapath = path;
		        		info.name = cur.getString(idx_name);
		        		info.favorited = true;
		        		list.add(info);
					}
				    cur.close();
				}while(cur1.moveToNext());
			}
			cur1.close();
			// sort
			Collections.sort(list,new Comparator<ImageInfo>(){
				@Override
				public int compare(ImageInfo a, ImageInfo b) {
					return a.datapath.compareTo(b.datapath);
				}
			});
		}
		// make grid view 
		lvThumbnailView.setAdapter(new ImageListAdapter(this,loader,list,thum_size,thum_size));
	}
}
