package jp.juggler.CropWallpaper;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class MyDBOpenHelper extends SQLiteOpenHelper {
    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME ="favorites";
    
    public MyDBOpenHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

	@Override
	public void onCreate(SQLiteDatabase db) {
        db.execSQL(
        		"create table if not exists favorites("
        		+" _id integer unique not null"
        		+")"
            );
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int arg1, int arg2) {
	}
}
