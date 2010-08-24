package jp.juggler.CropWallpaper;

import java.util.ArrayList;

import jp.juggler.util.LogCategory;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

public class ImageListAdapter extends ArrayAdapter<ImageInfo> {
	static LogCategory log = new LogCategory("ImageListAdapter");
	LayoutInflater inflater;
	int w,h;
	ThumbnailLoader loader;
	
	public ImageListAdapter(Context context,ThumbnailLoader loader,ArrayList<ImageInfo> list,int w,int h){
		super(context,R.layout.thumbnail_item,list);
		inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		this.loader = loader;
		//
		this.w =  w;
		this.h =  h;
	}

	static final int tag_idx = 1;
	@Override
	public View getView(int idx, View view, ViewGroup parent) {
		ImageView iv;

		GridView gv = (GridView)parent;
		int n = gv.getLastVisiblePosition() - gv.getFirstVisiblePosition(); 
		
		if(view == null){
			// if(idx!=0) log.d("create view %d",idx);
			view = inflater.inflate(R.layout.thumbnail_item, null);
			view.setLayoutParams(new GridView.LayoutParams(w,h));
			iv = (ImageView)view.findViewById(R.id.image);
		}else{
			iv = (ImageView)view.findViewById(R.id.image);
		}

		ImageInfo info = loader.update_view(iv,idx,n);
		updateCaption(view,info);
		return view;
	}
	
	public void updateCaption(View view,ImageInfo info){
		TextView caption = (TextView)view.findViewById(R.id.name);
		caption.setText(info.name);
		ImageView star = (ImageView)view.findViewById(R.id.star);
		star.setVisibility( info.favorited ? View.VISIBLE : View.GONE );
	}
	
}
