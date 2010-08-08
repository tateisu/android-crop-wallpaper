package jp.juggler.CropWallpaper;

import android.graphics.drawable.BitmapDrawable;
import android.widget.ImageView;

public class ImageInfo {
	String datapath;
	String name;
	long id;
	int showState;
	int view_idx =-1;
	boolean favorited = false;
	
	ImageView view;
	BitmapDrawable drawable;
}
