package jp.juggler.CropWallpaper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map.Entry;

import jp.juggler.util.LogCategory;
import android.app.Activity;
import android.content.ComponentName;
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
    	ArrayList<DirInfo> list = new ArrayList<DirInfo>();
        try{
            HashMap<String,DirInfo> dir_map = new HashMap<String,DirInfo>();
        	Uri base_uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        	Cursor cur = getContentResolver().query(
    			base_uri
    			,new String[]{
    				MediaStore.Images.ImageColumns.DATA
    			}
	    		, null
	    		, null
	    		, null
	        );
        	try{
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
        	}finally{
		        cur.close();
        	}

        	// お気に入り
        	{
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
        	}
        
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
	    }catch(Throwable ex){
	    	ex.printStackTrace();
	    }
	    // データを設定
	    lvDirectory.setAdapter(new DirListAdapter(this,list));
	}
}