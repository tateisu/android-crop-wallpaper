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

	static class ViewHolder{
		ImageView thumb;
		TextView caption;
		ImageView star;
	}
	
	static final int tag_idx = 1;
	@Override
	public View getView(int idx, View view, ViewGroup parent) {
		ViewHolder holder;
		
		
		if(view == null){
			view = inflater.inflate(R.layout.thumbnail_item, null);
			view.setLayoutParams(new GridView.LayoutParams(w,h));
			holder = new ViewHolder();
			holder.thumb =(ImageView)view.findViewById(R.id.image);
			holder.caption=(TextView)view.findViewById(R.id.name);
			holder.star=(ImageView)view.findViewById(R.id.star);
			view.setTag(holder);
		}else{
			holder = (ViewHolder)view.getTag();
		}

		GridView gv = (GridView)parent;
		int n = gv.getLastVisiblePosition() - gv.getFirstVisiblePosition(); 

		ImageInfo info = loader.update_view(holder.thumb,idx,n);
		holder.caption.setText(info.name);
		holder.star.setVisibility( info.favorited ? View.VISIBLE : View.GONE );
		return view;
	}
}
