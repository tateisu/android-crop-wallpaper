package jp.juggler.CropWallpaper;

import java.io.IOException;
import java.io.InputStream;

import jp.juggler.util.LogCategory;
import jp.juggler.util.WorkerBase;
import android.app.Activity;
import android.app.ProgressDialog;
import android.app.WallpaperManager;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.widget.CompoundButton.OnCheckedChangeListener;

public final class CropWallpaper extends Activity {
	static LogCategory log = new LogCategory("CropWallpaper");
	
	// UI部品
	FrameLayout flOuter;
	ImageView ivImage;
	ImageView ivSelection;
	Button btnOk;
	Button btnCancel;
	ToggleButton tbOverall;
	
	Handler ui_handler;

	// 壁紙の出力サイズ
	int wall_w;
	int wall_h;
	int wall_h_real;
	float wp_aspect;

	// 入力画像
	Bitmap src_image;

	// 表示画像の位置(表示枠基準)
	Bitmap shown_image;
	RectF shown_image_rect;
	
	// 移動開始時の選択範囲
	Rect prev_selection = new Rect();

	// 移動モード
	int tracking_mode = 0;
	static final int TRACK_NONE = 0;
	static final int TRACK_ZOOM = 1;
	static final int TRACK_MOVE = 2;

	// 選択範囲周辺の拡大操作グリップの幅
	float border_grip;
	
	// ズーム開始時の矩形の中心(raw座標)
	float zoom_center_x;
	float zoom_center_y;

	// ズーム開始時の中心とタッチ位置の距離
	float zoom_start_len;
	
	// 移動開始時のタッチ位置(raw座標)
	float touch_start_x;
	float touch_start_y;

	//
	ProgressDialog dialog;
	WallpaperManager wpm;
	Uri uri;
	boolean bLoading;
	DisplayMetrics metrics;
	boolean bOverall;
	int statusBarHeight;
	
	void init_resource(){
		if( PreferenceManager.getDefaultSharedPreferences(this).getBoolean("fullcolor",false) ){
			getWindow().setFormat(PixelFormat.RGBA_8888);
		}
    	getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		
		Configuration config = getResources().getConfiguration();

		if( config.orientation == Configuration.ORIENTATION_LANDSCAPE){
	        setContentView(R.layout.crop_wallpaper_landscape);
		}else{
	        setContentView(R.layout.crop_wallpaper_screen);
		}

        // UI部品への参照を検索
    	flOuter =(FrameLayout)findViewById(R.id.flOuter);
    	ivImage =(ImageView)findViewById(R.id.ivImage);
    	ivSelection =(ImageView)findViewById(R.id.ivSelection);
    	btnOk =(Button)findViewById(R.id.btnSetWallPaper);
    	btnCancel =(Button)findViewById(R.id.btnCancel);
    	tbOverall =(ToggleButton)findViewById(R.id.btnOverall);

    	// グリップ幅を計算
        metrics = new DisplayMetrics();
    	getWindowManager().getDefaultDisplay().getMetrics(metrics);
    	border_grip = metrics.density * 20;


    	//
    	ui_handler = new Handler();

    	//
        wpm = WallpaperManager.getInstance(this);

        btnOk.setEnabled(false);
        tbOverall.setEnabled(false);
        tbOverall.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				bOverall = isChecked;
				if( isChecked ){
					setSelection(0,0,0,0);
				}else{
					setSelection(
						 prev_selection.left
						,prev_selection.top 
						,prev_selection.width()
						,prev_selection.height()
					);
				}
			}
		});

    	// 選択範囲の移動と拡大
    	ivSelection.setOnTouchListener(new OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				// 初期化がまだなら処理しない
				if(bLoading) return false;
				// 全体モードなら処理しない
				if(bOverall) return false;

				float x = event.getX();
				float y = event.getY();
				float raw_x = event.getRawX();
				float raw_y = event.getRawY();
				
				switch(event.getAction()){
				case MotionEvent.ACTION_UP:
				case MotionEvent.ACTION_CANCEL:
					// ドラッグ操作の終了
					if( tracking_mode != TRACK_NONE){
						tracking_mode = TRACK_NONE;
						return true;
					}
					break;
				case MotionEvent.ACTION_DOWN: // 移動/拡大の開始
					if( tracking_mode == TRACK_NONE ){
						// 移動開始時の選択範囲の位置と幅を覚える
						LinearLayout.LayoutParams lpSelection = (LinearLayout.LayoutParams) ivSelection.getLayoutParams();
						prev_selection.left =lpSelection.leftMargin;
						prev_selection.top  =lpSelection.topMargin;
						prev_selection.right = prev_selection.left + ivSelection.getWidth();
						prev_selection.bottom = prev_selection.top + ivSelection.getHeight();

						if( x < border_grip || prev_selection.width() - x < border_grip 
						||  y < border_grip || prev_selection.height() - y < border_grip
						){
							// はしっこを掴むと拡大縮小
							tracking_mode = TRACK_ZOOM;
							// タッチ開始時の中心位置(raw座標)
							zoom_center_x = raw_x - x + ivSelection.getWidth()/2;
							zoom_center_y = raw_y - y + ivSelection.getHeight()/2;
							// 中心と現在位置の距離
							zoom_start_len = (float)Math.sqrt(
									  Math.pow(raw_x-zoom_center_x,2)
									+ Math.pow(raw_y-zoom_center_y,2)
							);
						}else{
							// 中央を掴むと移動
							tracking_mode = TRACK_MOVE;
							// タッチ開始時のタッチ位置
							touch_start_x = raw_x;
							touch_start_y = raw_y;
						}
						return true;
					}
					break;
				case MotionEvent.ACTION_MOVE:
					if( tracking_mode == TRACK_ZOOM ){
						// 中心からの距離を調べる
						float len =  (float)Math.sqrt(
							  Math.pow(raw_x-zoom_center_x,2)
							+ Math.pow(raw_y-zoom_center_y,2)
						);
						if(len < border_grip *2) len = border_grip *2;
						
						// 距離の変化に応じてサイズが変化する
						int new_w,new_h;
						if( wp_aspect >= 1 ){
							new_w = (int)(0.5 + prev_selection.width() * len/zoom_start_len);
							new_h = (int)(0.5 + new_w / wp_aspect);
						}else{
							new_h = (int)(0.5 + prev_selection.height() * len/zoom_start_len);
							new_w = (int)(0.5 + new_h * wp_aspect);
						}
						// クリッピング
						if( new_w > shown_image_rect.width() ){
							new_w = (int)shown_image_rect.width();
							new_h = (int)(0.5 + new_w / wp_aspect);
						}
						if( new_h > shown_image_rect.height() ){
							new_h = (int)shown_image_rect.height();
							new_w = (int)(0.5 + new_h * wp_aspect);
						}
						setSelection(
							 (prev_selection.left + prev_selection.right)/2  - new_w/2
							,(prev_selection.top + prev_selection.bottom)/2  - new_h/2
							,new_w
							,new_h
						);
						
						return true;
					} 
					if( tracking_mode == TRACK_MOVE ){
						// 移動モードの位置を更新
						setSelection(
							 prev_selection.left + (int)(0.5+ raw_x - touch_start_x)
							,prev_selection.top  + (int)(0.5+ raw_y - touch_start_y)
							,prev_selection.width()
							,prev_selection.height()
						);
						return true;
					}
					break;
				}
				return false;
			}
		});

    	// キャンセルボタン
    	btnCancel.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				finish();
			}
		});

    	// 壁紙セット
    	btnOk.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if(bLoading) return;
				
				// 既に作業中なら何もしない
				if(dialog !=null || wp_task !=null ) return;
				// 処理中ダイアログを表示
				dialog = ProgressDialog.show(
						CropWallpaper.this
						,getText(R.string.wallpaper_progress_title)
						,getText(R.string.wallpaper_progress_message)
						, true);
				// バックグラウンドで処理
				wp_task = new WallpaperTask();
				wp_task.start();
			}
		});
    	
	}
	
	///////////////////////////////////////////////////
	// 各種イベント
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
		log.d("onCreate");

    	MyApp.pref_init(this);
        super.onCreate(savedInstanceState);
        init_resource();
        init_page(getIntent());
    }

	@Override
	protected void onDestroy() {
		log.d("onDestroy");
		super.onDestroy();
		if(src_image !=null) src_image.recycle();
	}

	@Override
	protected void onNewIntent(Intent intent) {
		log.d("onNewIntent");
		super.onNewIntent(intent);
		init_page(intent);
	}

	@Override
	protected void onResume() {
		log.d("onResume");
		super.onResume();
		tracking_mode = TRACK_NONE;
		if(bLoading){
			loader_worker = new ImageLoaderWorker();
			loader_worker.start();
		}
	}

	@Override
	protected void onPause() {
		log.d("onPause");
		super.onPause();
		if( wp_task != null ) wp_task.joinLoop(log,"wp_task");
		if( dialog != null ) dialog.dismiss();
		if( loader_worker != null ) loader_worker.joinLoop(log,"loader_worker");
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
    	log.d("onConfigurationChanged");
		super.onConfigurationChanged(newConfig);
	}
	
	/////////////////////////////////////////////////////////////
	// 内部処理
	
	// ページ構成パラメータの解釈
	void init_page(Intent intent){
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(CropWallpaper.this);
		bAvoidStatusBar = pref.getBoolean("avoid_status_bar", false);
		statusBarHeight = pref.getInt("status_bar_height", 0);

		bLoading = true;
		src_image = null;
		uri = intent.getData();
		if(uri == null){
			log.d("intent=%s uri=%s type=%s extra=%s"
					,intent
					,intent.getData()
					,intent.getType()
					,intent.getExtras()
			);
			Bundle extra = intent.getExtras();
			
			for( String key : extra.keySet() ){
				log.d("key=%s",key);
			}
	
			uri  = (Uri)extra.get(Intent.EXTRA_STREAM);
			if(uri==null){
				finish();
				return;
			}
		}
		log.d("uri=%s",uri);
	}

	boolean bAvoidStatusBar = true;

	// 画像ロードタスク
	ImageLoaderWorker loader_worker;
	class ImageLoaderWorker extends WorkerBase{
		volatile boolean bCancelled = false;

		@Override
		public void cancel() {
			bCancelled = true;
			notifyEx();
		}
		public void run(){
			if(src_image == null ){
				if( uri == null ){
					ui_handler.post(new Runnable() {
						@Override public void run() {
							if(isFinishing()) return;
							Toast.makeText(CropWallpaper.this,"missing uri in arguments.",Toast.LENGTH_SHORT).show();
						}
					});
					return;
				}
				log.d("loading image..");

				// 壁紙の要求サイズを調べる
				wall_w = wpm.getDesiredMinimumWidth();
				wall_h = wall_h_real = wpm.getDesiredMinimumHeight();
				if( bAvoidStatusBar) wall_h -= statusBarHeight;
		    	wp_aspect = wall_w/(float)wall_h;
				log.d("statusBarHeight=%d,wall_h=%d(%d)",statusBarHeight,wall_h,wall_h_real);

				// 画像サイズチェック用のオプション
				BitmapFactory.Options check_option = new BitmapFactory.Options();
				check_option.inJustDecodeBounds  = true;
				check_option.inDensity = 0;
				check_option.inTargetDensity =0;
				check_option.inDensity = 0;
				check_option.inScaled =false;

				// 画像ロード用のオプション
				BitmapFactory.Options load_option = new BitmapFactory.Options();
				load_option.inPurgeable = true;
				load_option.inDensity = 0;
				load_option.inTargetDensity = 0;
				load_option.inDensity = 0;
				load_option.inScaled =false;

				// ロード時の色深度
				int pixel_bytes;
				if( PreferenceManager.getDefaultSharedPreferences(CropWallpaper.this).getBoolean("fullcolor", false) ){
					check_option.inPreferredConfig =  Bitmap.Config.ARGB_8888;
					load_option.inPreferredConfig =  Bitmap.Config.ARGB_8888;
					pixel_bytes = 4;
				}else{
					pixel_bytes = 2;
					check_option.inPreferredConfig =  Bitmap.Config.RGB_565;
					load_option.inPreferredConfig =  Bitmap.Config.RGB_565;
				}

				ContentResolver cr = getContentResolver();
				InputStream is;

				// 画像サイズを調べる
				try{
					is = cr.openInputStream(uri);
					try{
						check_option.outHeight =0;
						check_option.outWidth =0;
						BitmapFactory.decodeStream(is, null, check_option);
					}finally{
						is.close();
					}
				}catch(IOException ex){
					ex.printStackTrace();
				}
				if( check_option.outWidth < 1 || check_option.outHeight < 1 ){
					log.e("load failed.");
					ui_handler.post(new Runnable() {
						@Override public void run() {
							if(isFinishing()) return;
							Toast.makeText(CropWallpaper.this,"load failed.",Toast.LENGTH_SHORT).show();
						}
					});
					return;
				}

				// データ量を調べて必要ならサンプルサイズを変える
				int data_size = check_option.outWidth * check_option.outHeight * pixel_bytes; // 面積と色深度
				String pref_val = PreferenceManager.getDefaultSharedPreferences(CropWallpaper.this).getString("image_ram_limit", null);
				int pref_n = MyApp.parseInt(pref_val,10,5,100);
				int limit_size = 1024* 1024* pref_n;
				int samplesize =1;
				while( data_size /(float)(samplesize*samplesize) >= limit_size ){
					samplesize++;
				}
				load_option.inSampleSize  = samplesize;

				// load bitmap
				try{
					is = cr.openInputStream(uri);
					try{
						src_image = BitmapFactory.decodeStream(is, null, load_option);
					}finally{
						is.close();
					}
				}catch(IOException ex){
					ex.printStackTrace();
				}
				if( src_image == null ){
					log.e("load failed.");
					ui_handler.post(new Runnable() {
						@Override public void run() {
							if(isFinishing()) return;
							Toast.makeText(CropWallpaper.this,"load failed.",Toast.LENGTH_SHORT).show();
						}
					});
					return;
				}

				int row_bytes = src_image.getRowBytes();
				int pixel_bytes2 = row_bytes/src_image.getWidth();
				log.d("original size=%dx%dx%d(%.2fMB), factor=%s,resized=%dx%dx%d(%.2fMB)"
					,check_option.outWidth
					,check_option.outHeight
					,pixel_bytes
					,data_size/(float)(1024*1024)
					,samplesize
					,src_image.getWidth()
					,src_image.getHeight()
					,pixel_bytes2
					,(src_image.getHeight() * row_bytes )/(float)(1024*1024)
				);
			}

			// レイアウトが完了するのを待つ
			while(!bCancelled){
				if( flOuter.getWidth() > 0 ) break;
				waitEx(100);
			}
			if(bCancelled) return;

			// 表示枠のサイズ
			int frame_w = flOuter.getWidth();
			int frame_h = flOuter.getHeight();
			float frame_aspect = frame_w / (float) frame_h;

			// データのサイズ
			int src_w = src_image.getWidth();
			int src_h = src_image.getHeight();
			float src_aspect = src_w / (float) src_h;
			Rect src_rect = new Rect(0,0,src_w,src_h);

			// 表示画像のサイズ
			int shown_w;
			int shown_h;
			if( src_w <= frame_w && src_h <= frame_h ){
				// スケーリング不要
				shown_w = src_w;
				shown_h = src_h;
			}else if( src_aspect >= frame_aspect ){
				shown_w = frame_w;
				shown_h = (int)( 0.5f + ( src_h * frame_w ) / (float)src_w);
			}else{
				// 画像は表示枠より縦長.上下ベースで合わせる
				shown_h = frame_h;
				shown_w = (int)( 0.5f + ( src_w * frame_h ) / (float)src_h);
			}

			// 表示画像の位置(表示枠基準)
			int x,y;
			x = (frame_w - shown_w) /2;
			y = (frame_h - shown_h) /2;
			shown_image_rect = new RectF(x,y,x + shown_w,y + shown_h);

			// 表示用のbitmapを生成
			shown_image = Bitmap.createBitmap(frame_w,frame_h,src_image.getConfig());
			Paint paint = new Paint();
			paint.setFilterBitmap(true);
			Canvas c = new Canvas(shown_image);
			c.drawARGB(255,0,0,0);
			c.drawBitmap(src_image,src_rect,shown_image_rect,paint);

			// 選択範囲の幅と高さ
			int selection_w;
			int selection_h;
			if( src_aspect <= wp_aspect ){
				// 画像は壁紙の比率より縦長。左右ベースで合わせる
				selection_w = (int)shown_image_rect.width();
				selection_h = (int)(0.5 + selection_w / wp_aspect);
			}else{
				// 画像は壁紙よりも横に長い。上下ベースで合わせる
				selection_h = (int)shown_image_rect.height();
				selection_w = (int)(0.5 + selection_h * wp_aspect);
			}
			x = (frame_w - selection_w) /2;
			y = (frame_h - selection_h) /2;
			prev_selection.set(x, y, x + selection_w, y + selection_h);

			ui_handler.post(new Runnable() {
				@Override
				public void run() {
					if(bCancelled) return;
					// 画像を表示
					ivImage.setImageDrawable(new BitmapDrawable(getResources(),shown_image));

					// ボタンを有効化
			        tbOverall.setEnabled(true);
			        btnOk.setEnabled(true);
					bLoading =false;

					// 選択範囲を設定
					setSelection(
						 prev_selection.left
						,prev_selection.top 
						,prev_selection.width()
						,prev_selection.height()
					);
				}
			});
		}
	}

	void setSelection(int new_x,int new_y,int new_w,int new_h){
		if(bLoading) return;
		if( !bOverall ){
			// 幅と高さをクリップ
			int max_w = (int)shown_image_rect.width();
			int max_h = (int)shown_image_rect.height();
			new_w =  new_w > max_w ? max_w : new_w < 0 ? 0 : new_w;
			new_h =  new_h > max_h ? max_h : new_h < 0 ? 0 : new_h;
			// 移動可能範囲
			int x_min = (int)shown_image_rect.left;
			int y_min = (int)shown_image_rect.top;
			int x_max = (int)shown_image_rect.right  - new_w;
			int y_max = (int)shown_image_rect.bottom - new_h;
			// 位置を移動可能範囲でクリップ
			new_x = new_x < x_min ? x_min : new_x > x_max ? x_max : new_x;
			new_y = new_y < y_min ? y_min : new_y > y_max ? y_max : new_y;
		}else{
			new_w = (int)shown_image_rect.width();
			new_h = (int)shown_image_rect.height();
			new_x = (int)shown_image_rect.left;
			new_y = (int)shown_image_rect.top;
		}
		// 選択範囲を更新
		LinearLayout.LayoutParams lpSelection = (LinearLayout.LayoutParams) ivSelection.getLayoutParams();
		lpSelection.setMargins(
			 new_x
			,new_y
			,flOuter.getWidth() -new_w -new_x
			,flOuter.getHeight() -new_h -new_y
		);
		ivSelection.requestLayout();
	}

	// 壁紙設定タスク
	WallpaperTask wp_task;
	class WallpaperTask extends WorkerBase{
		@Override
		public void cancel() {
			// このタスクはキャンセルできない
		}
		public void run(){
			boolean bDither = PreferenceManager.getDefaultSharedPreferences(CropWallpaper.this).getBoolean("dither",false);
			
			final Bitmap wall_image = Bitmap.createBitmap(wall_w,wall_h_real,bDither ? Bitmap.Config.RGB_565 : src_image.getConfig());
			Canvas c = new Canvas(wall_image);
			c.drawARGB(255,0,0,0);
			Paint paint = new Paint();
			paint.setFilterBitmap(true);
			paint.setDither(bDither);
			if( bOverall ){
				float x_ratio = src_image.getWidth() / (float)wall_w;
				float y_ratio = src_image.getHeight() / (float)wall_h;
				int w,h;
				if( x_ratio >= y_ratio ){
					h = (int)( 0.5f +  wall_w * src_image.getHeight() / (float)src_image.getWidth());
					w = wall_w;
				}else{
					w = (int)( 0.5f +  wall_h * src_image.getWidth() / (float)src_image.getHeight());
					h = wall_h;
				}
				int x = (wall_w - w)/2;
				int y = (wall_h - h)/2;
				// 入力画像をリサイズ
				Rect selection = new Rect(0,0,src_image.getWidth(),src_image.getHeight());
				RectF wall_rect = new RectF(x,y,x+w,y+h);
				if(bAvoidStatusBar){
					wall_rect.top += statusBarHeight;
					wall_rect.bottom += statusBarHeight;
				}
				c.drawBitmap(src_image,selection,wall_rect,paint);
			}else{
				// 表示枠基準での選択範囲を、表示画像基準の選択範囲に変換
				double ratio_x = src_image.getWidth ()/(double)shown_image_rect.width ();
				double ratio_y = src_image.getHeight()/(double)shown_image_rect.height();
				LinearLayout.LayoutParams lpSelection = (LinearLayout.LayoutParams) ivSelection.getLayoutParams();
				int x = lpSelection.leftMargin - (int)shown_image_rect.left;
				int y = lpSelection.topMargin  - (int)shown_image_rect.top;
				Rect selection = new Rect(
						 (int)(0.5 + ratio_x * x)
						,(int)(0.5 + ratio_y * y)
						,(int)(0.5 + ratio_x * (x + ivSelection.getWidth()))
						,(int)(0.5 + ratio_y * (y + ivSelection.getHeight()))
				);
				// 入力画像をリサイズ
				RectF wall_rect = new RectF(0,0,wall_w,wall_h);
				if(bAvoidStatusBar){
					wall_rect.top += statusBarHeight;
					wall_rect.bottom += statusBarHeight;
				}
				c.drawBitmap(src_image,selection,wall_rect,paint);
			}
			// 
			src_image.recycle();
			log.d("set wallpaper:%d,%d,%s",wall_image.getWidth(),wall_image.getHeight(),wall_image.getConfig());
			ui_handler.post(new Runnable() {
				@Override public void run() {
					if(isFinishing()) return;
					try{
						wpm.clear();
						wpm.setBitmap(wall_image);
					}catch(IOException ex){
						Toast.makeText(CropWallpaper.this,ex.getMessage(),Toast.LENGTH_LONG).show();
					}finally{
						dialog.dismiss();
						finish();
					}
				}
			});
		}
	}
}