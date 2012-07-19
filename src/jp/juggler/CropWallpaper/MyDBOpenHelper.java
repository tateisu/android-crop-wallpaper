package jp.juggler.CropWallpaper;

import jp.juggler.util.LogCategory;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.provider.MediaStore;

public class MyDBOpenHelper extends SQLiteOpenHelper {
	static final LogCategory log=new LogCategory("CW_DBOpenHelper");
    static final int DATABASE_VERSION = 2;
    static final String DATABASE_NAME ="favorites";
    Activity context;
    
    public MyDBOpenHelper(Activity context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context;
    }

	@Override
	public void onCreate(SQLiteDatabase db) {

	}

	

	@Override
	public void onOpen(SQLiteDatabase db) {
		super.onOpen(db);
		
		if(!db.isReadOnly()){
			// create table
	        db.execSQL(
        		"create table if not exists favorites("
        		+" _id integer unique not null"
        		+" ,_data text"
        		+")"
            );
	          
	        
			// append _data column
			try{
	        	db.execSQL("alter table favorites add _data text");
	        }catch(SQLiteException ex){}
	
	        // append index
	        db.execSQL("create index if not exists favorites_data on favorites(_data)");
	
	        // supply _data 
	        ContentResolver cr = context.getContentResolver();
			Uri base_uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
			String[] cols = new String[]{ MediaStore.Images.ImageColumns.DATA };
			String[] bind2 = new String[2];
			String[] bind1 = new String[1];
			db.beginTransaction();
			try{
				Cursor cur1 = db.rawQuery("select _id from favorites where _data is null", null);
				try{
					if(cur1.moveToFirst() ){
						int idx_id = cur1.getColumnIndex("_id");
						do{
							long id = cur1.getLong(idx_id);
							Uri item_uri = ContentUris.withAppendedId(base_uri,id);
							Cursor cur2 = cr.query(item_uri,cols,null,null,null);
							try{
								if( cur2.moveToFirst() ){
									bind2[0] = cur2.getString(0);
									bind2[1] = Long.toString(id);
									db.execSQL("update favorites set _data=? where _id=?",bind2);
									log.d("fix id=%d,data=%s",id,bind2[0]);
								}else{
									bind1[0] = Long.toString(id);
									db.execSQL("delete from favorites where _id=?",bind1);
									log.d("delete id=%d, missing contents",id);
								}
							}finally{
								cur2.close();
							}
						}while(cur1.moveToNext());
					}
					db.setTransactionSuccessful();
				}finally{
					cur1.close();
				}
			}finally{
				db.endTransaction();
			}
		}
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int v_old, int v_new) {
		if(v_old==1){
			v_old=2;
		}
	}
}
