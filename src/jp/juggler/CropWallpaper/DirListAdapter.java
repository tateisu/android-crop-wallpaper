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
	
	static class ViewHolder{
		TextView name;
		TextView count;
	}

	@Override
	public View getView(int idx, View view, ViewGroup parent) {
		ViewHolder holder;
		if(view==null){
			view = inflater.inflate(R.layout.folder_item, null);
			holder = new ViewHolder();
			view.setTag(holder);
			holder.name = (TextView)view.findViewById(R.id.name);
			holder.count = (TextView)view.findViewById(R.id.count);
		}else{
			holder = (ViewHolder)view.getTag();
		}

		DirInfo src = getItem(idx);
    	holder.name.setText(src.name);
    	holder.count.setText(Integer.toString(src.count));

    	return view;
	}
	

	
}
