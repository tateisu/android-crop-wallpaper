package jp.juggler.CropWallpaper;

import java.util.ArrayList;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class DirListAdapter extends ArrayAdapter<DirInfo>{
	LayoutInflater inflater;
	public DirListAdapter(Context context,ArrayList<DirInfo> list){
		super(context,R.layout.folder_item,list);
		inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	}

	@Override
	public View getView(int idx, View view, ViewGroup parent) {
		if(view==null) view = inflater.inflate(R.layout.folder_item, null); 
		DirInfo src = getItem(idx);
    	
    	((TextView)view.findViewById(R.id.name)).setText(src.name);
    	((TextView)view.findViewById(R.id.count)).setText(Integer.toString(src.count));
		return view;
	}
	
}
