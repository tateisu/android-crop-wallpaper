package jp.juggler.CropWallpaper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map.Entry;

import jp.juggler.util.LogCategory;
import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.AdapterView.OnItemClickListener;

public class FolderListScreen extends Activity {
	static LogCategory log = new LogCategory("HugeGallery");

	ListView lvDirectory;
	SQLiteDatabase db;
	
	void initUI(){
        setContentView(R.layout.folder_list_screen);
        lvDirectory = (ListView)findViewById(R.id.lvDirectory);
        
        lvDirectory.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int pos,long id) {
				DirListAdapter adapter = (DirListAdapter)parent.getAdapter();
				DirInfo info = adapter.getItem(pos);
				// open grid view
				Intent intent = new Intent();
				intent.setComponent(new ComponentName(getPackageName(),ImageListScreen.class.getName()));
				if( ! info.bFavorites ) intent.putExtra("dirname",info.name);
				startActivity(intent);
			}
		});
	}
	
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initUI();
        MyDBOpenHelper helper = new MyDBOpenHelper(this);
        db = helper.getWritableDatabase();
        init_page(getIntent());
    }
	@Override
	protected void onDestroy() {
		super.onDestroy();
		db.close();
	}

    void init_page(Intent intent){
        HashMap<String,DirInfo> dir_map = new HashMap<String,DirInfo>();

        Uri base_uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        Cursor cur = managedQuery(
    		base_uri
    		,new String[]{
    			MediaStore.Images.ImageColumns.DATA	
    		}
    		, null
    		, null
    		, null
        );
        if (cur.moveToFirst()) {
        	int idx_data = cur.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
        	do{
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
        	}while(cur.moveToNext());
        }
        cur.close();
        ArrayList<DirInfo> list = new ArrayList<DirInfo>();
        
        // お気に入り
        DirInfo favorites = new DirInfo();
        favorites.bFavorites=true;
        favorites.name=getResources().getString(R.string.favorite);
        {
        	cur = db.rawQuery("select count(*) from favorites",null);
        	if(cur.moveToFirst()){
        		favorites.count = cur.getInt(0);
        	}
        	cur.close();
        }
        list.add(favorites);
        
        // ディレクトリの指定
        for(Entry<String,DirInfo> entry : dir_map.entrySet() ){
        	list.add( entry.getValue() ); 
        }
        
        // ソート
        Collections.sort(list,new Comparator<DirInfo>(){
			@Override public int compare(DirInfo a, DirInfo b) {
				if(a.bFavorites) return -1;
				if(b.bFavorites) return 1;
				return a.name.compareTo(b.name);
			}
        });
        // データを設定
        lvDirectory.setAdapter(new DirListAdapter(this,list));
    }

    void dump(){

        Uri base_uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        log.d("base_uri=%s",base_uri);
    //    ContentResolver resolver = getContentResolver();
        Cursor cur = managedQuery(base_uri,null, null, null, null);
        if (cur.moveToFirst()) {

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
    		int idx_id = cur.getColumnIndex(MediaStore.Images.ImageColumns._ID);
    		String vt;
    		long vi;
    		int lno=0;
        	do{
        		++lno;
        		Uri item_uri = ContentUris.withAppendedId(base_uri, cur.getLong(idx_id));
        		log.d("--------------------------");
        		log.d("itemURI=%s",item_uri);
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
        		if(lno >= 100) break;
        	}while(cur.moveToNext());
        }
        cur.close();
    }
}