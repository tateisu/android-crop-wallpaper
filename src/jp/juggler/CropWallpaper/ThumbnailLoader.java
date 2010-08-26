package jp.juggler.CropWallpaper;

import java.util.ArrayList;
import java.util.HashSet;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.widget.ImageView;
import jp.juggler.util.LogCategory;
import jp.juggler.util.WorkerBase;

public class ThumbnailLoader {
	static LogCategory log = new LogCategory("ThumbnailLoader");
	
	Context context;
	ImageListAdapter adapter;
	HashSet<Integer> bitmap_exists;
	HashSet<ImageInfo> wait_updates = new HashSet<ImageInfo>();
	int thum_size;
	Handler ui_handler;
	
	volatile int last_show_index =0;
	public ThumbnailLoader(Context c,int thum_size){
		this.context = c;
		this.thum_size = thum_size;
		ui_handler = new Handler();
	}
	public void worker_start(ImageListAdapter adapter){
		this.adapter = adapter;
		adapter.notifyDataSetChanged();
		// update set of bitmap exists
		bitmap_exists = new HashSet<Integer>();
		// start background worker
		worker = new Worker();
		worker.setPriority(Thread.MIN_PRIORITY);
		worker.start();
	}
	public void worker_stop(){
		if(worker!=null) worker.joinLoop(log,"worker");
		for(int i=0,end=adapter.getCount();i<end;++i){
			ImageInfo info = adapter.getItem(i);
			info.drawable = null;
		}
		log.d("visible_size=%d",visible_size);
	}
	
	int visible_size=1;

	ImageInfo update_view(ImageView view,int new_idx,int visible_size){
		synchronized(this){
			if( this.visible_size < visible_size) this.visible_size = visible_size;
			
			// 古い情報の破棄
			ImageInfo old_info = (ImageInfo)view.getTag();
			if( old_info != null ){
				old_info.view = null;
				view.setTag(null);
			}
			
			//新しい情報の設定
			ImageInfo info = adapter.getItem(new_idx);
		
			// 画像を設定
			if(new_idx!=0) last_show_index = new_idx;
			if(info.drawable !=null){
				view.setImageDrawable(info.drawable);
			}else{
				view.setImageDrawable(null);
				wait_updates.add(info);
			}
			
			// pairing info & view
			view.setTag(info);
			info.view = view;
			info.view_idx = new_idx;
			return info;
		}
	}

	//////////////////////////////////////////

	Worker worker;
	class Worker extends WorkerBase{
		volatile boolean bCancelled = false;
		
		@Override
		public void cancel() {
			bCancelled = true;
			notifyEx();
		}
		int load_pref = 30; 
		public void run(){
			String pref_val = PreferenceManager.getDefaultSharedPreferences(context).getString("thumbnail_fetch_count", null);
			load_pref = MyApp.parseInt(pref_val,30,15,9999);
			
			BitmapFactory.Options load_option = new BitmapFactory.Options();
			load_option.inPurgeable = true;
			load_option.inDensity = 0;
			load_option.inPreferredConfig=Bitmap.Config.ARGB_8888;

			BitmapFactory.Options check_option = new BitmapFactory.Options();
			check_option.inJustDecodeBounds  = true;
			check_option.inDensity = 0;

			while(!bCancelled){

				// find load target
				ImageInfo target =null;
				int target_index=0;
				synchronized(ThumbnailLoader.this){
					// UIスレッドに通知を送る
					if( wait_updates.size() > 0 ){
						StringBuffer sb = new StringBuffer();
						for(ImageInfo info : wait_updates){
							sb.append(",");
							sb.append(Integer.toString(info.view_idx));
						}
						wait_updates.clear();
						ui_handler.post(new Runnable() {
							@Override
							public void run() {
								if(bCancelled) return;
								adapter.notifyDataSetChanged();
							}
						});
					}
					
					int load_range = load_pref + visible_size;
					// 周辺にない要素があれば破棄する
					ArrayList<Integer> clear_list = new ArrayList<Integer>();
					for(Integer idx : bitmap_exists ){
						int i = idx.intValue();
						// 0 はなぜか何度も呼ばれるので破棄しない
						if(i==0) continue;
						// 近くにあるデータは破棄しない
						if( i >= last_show_index - load_range && i <= last_show_index + load_range) continue;
						// 表示中か、drawableが設定されていないなら処理しない
						ImageInfo info = adapter.getItem(i);
						if( info.view != null ||  info.drawable == null ) continue;
						// データを破棄する
						info.drawable = null;
						//
						clear_list.add(idx);
					}
					for(Integer idx : clear_list ){
						bitmap_exists.remove(idx);
					}
					clear_list.clear();
					
					// 最後に表示した位置に近い場所から順に、ロードが必要なものを探す
					do{
						int end = adapter.getCount();
						int idx = last_show_index;
						if( idx >= 0 && idx < end){
							ImageInfo info = adapter.getItem(idx);
							if(info.drawable==null){
								target = info;
								target_index = idx;
								break;
							}
						}
						int i=0;
						while(!bCancelled && target==null && ++i <= load_range){
							//
							idx = last_show_index + i;
							if( idx >= 0 && idx < end){
								ImageInfo info = adapter.getItem(idx);
								if(info.drawable==null){
									target = info;
									target_index = idx;
									break;
								}
							}
							//
							idx = last_show_index - i;
							if( idx >= 0 && idx < end){
								ImageInfo info = adapter.getItem(idx);
								if(info.drawable==null){
									target = info;
									target_index = idx;
									break;
								}
							}
						}
					}while(false);
				}
				if(bCancelled) break;
				if( target == null ){
					waitEx(1000);
					continue;
				}
				
				Bitmap src_image;
				
				// check size of image
				check_option.outWidth = 0;
				check_option.outHeight = 0;
				BitmapFactory.decodeFile(target.datapath, check_option);
				if( check_option.outWidth < 1 || check_option.outHeight < 1 ){
					// 画像サイズを取得できない
					src_image = BitmapFactory.decodeResource(context.getResources(),R.raw.test);
				}else{
					// ロード時に小さいイメージを読み込むようにする
					int data_size = check_option.outWidth * check_option.outHeight; // 面積とRGBA
					int limit_size = thum_size * thum_size * 4;
					int samplesize =1;
					while( data_size /(float)(samplesize*samplesize) >= limit_size){
						++samplesize;
					}
					// load bitmap
					load_option.inSampleSize  = samplesize;
					src_image = BitmapFactory.decodeFile(target.datapath, load_option);
					if( src_image == null ){
						src_image = BitmapFactory.decodeResource(context.getResources(),R.raw.test);
					}
				}
				
				// データのサイズ
				int src_xsize = src_image.getWidth();
				int src_ysize = src_image.getHeight();
				// 表示枠のサイズ
				int outer_xsize = thum_size;
				int outer_ysize = thum_size;
				// x,yそれぞれのはみだし率
				float xratio = src_xsize /(float) outer_xsize;
				float yratio = src_ysize /(float) outer_ysize;
				// 表示画像のサイズ
				int shown_xsize;
				int shown_ysize;
				if( xratio <= 1 && yratio <= 1 ){
					// スケーリング不要
					shown_xsize = src_xsize;
					shown_ysize = src_ysize;
				}else if( xratio >= yratio){
					// 横幅基準でリサイズ
					shown_xsize = outer_xsize;
					shown_ysize = (int)( 0.5f + ( src_ysize * outer_xsize ) / (float)src_xsize);
				}else{
					// 縦幅基準でリサイズ
					shown_ysize = outer_ysize;
					shown_xsize = (int)( 0.5f + ( src_xsize * outer_ysize ) / (float)src_ysize);
				}
				// 表示枠からの表示画像の位置
				int image_left   = (outer_xsize - shown_xsize) /2;
				int image_top    = (outer_ysize - shown_ysize) /2;
				
				Bitmap shown_image;
				
				// リサイズされたBitmapを作成する
				shown_image = Bitmap.createBitmap(outer_xsize,outer_ysize,src_image.getConfig());
				Rect src_rect = new Rect(0,0,src_xsize,src_ysize);
				RectF shown_image_rect = new RectF(
					 image_left
					,image_top
					,image_left + shown_xsize
					,image_top  + shown_ysize
				);
				Canvas c = new Canvas(shown_image);
				c.drawARGB(255,0,0,0);
				Paint paint = new Paint();
				paint.setFilterBitmap(true);
				paint.setDither(true);
				c.drawBitmap(src_image,src_rect,shown_image_rect,paint);

				// 元画像を破棄
				src_image.recycle();

				// ImageInfoを更新
				synchronized(ThumbnailLoader.this){
					target.drawable = new BitmapDrawable(shown_image);
					bitmap_exists.add(new Integer(target_index));
				}
			}
		}
	}
}
