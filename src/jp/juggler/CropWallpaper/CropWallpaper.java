package jp.juggler.CropWallpaper;

import java.io.InputStream;

import jp.juggler.util.LogCategory;
import jp.juggler.util.WorkerBase;
import android.app.Activity;
import android.app.ProgressDialog;
import android.app.WallpaperManager;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

public final class CropWallpaper extends Activity {
	static LogCategory log = new LogCategory("CropWallpaper");
	
	// UI部品
	FrameLayout flOuter;
	ImageView ivImage;
	ImageView ivSelection;
	Button btnOk;
	Button btnCancel;
	Handler ui_handler;

	// 壁紙の出力サイズ
	int wall_w;
	int wall_h;
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
	
	void init_resource(){
        setContentView(R.layout.crop_wallpaper_screen);

        // UI部品への参照を検索
    	flOuter =(FrameLayout)findViewById(R.id.flOuter);
    	ivImage =(ImageView)findViewById(R.id.ivImage);
    	ivSelection =(ImageView)findViewById(R.id.ivSelection);
    	btnOk =(Button)findViewById(R.id.btnSetWallPaper);
    	btnCancel =(Button)findViewById(R.id.btnCancel);
    	
    	// グリップ幅を計算
        DisplayMetrics metrics = new DisplayMetrics();
    	getWindowManager().getDefaultDisplay().getMetrics(metrics);
    	border_grip = metrics.density * 20;

    	//
    	ui_handler = new Handler();

    	//
        wpm = WallpaperManager.getInstance(this);
		wall_w = wpm.getDesiredMinimumWidth();
		wall_h = wpm.getDesiredMinimumHeight();
		wp_aspect = wall_w/(float)wall_h;

    	// 選択範囲の移動と拡大
    	ivSelection.setOnTouchListener(new OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				try{
					// 初期化がまだなら処理しない
					if(bLoading) return false;

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
				}catch(Throwable ex){
					ex.printStackTrace();
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
        super.onCreate(savedInstanceState);
        init_resource();
        init_page(getIntent());
    }

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if(src_image !=null) src_image.recycle();
	}

	@Override
	protected void onNewIntent(Intent intent) {
		// TODO Auto-generated method stub
		super.onNewIntent(intent);
		init_page(intent);
	}

	@Override
	protected void onResume() {
		super.onResume();
		tracking_mode = TRACK_NONE;
		if(bLoading){
			loader_worker = new ImageLoaderWorker();
			loader_worker.start();
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		if( wp_task != null ) wp_task.joinLoop(log,"wp_task");
		if( dialog != null ) dialog.dismiss();
		if( loader_worker != null ) loader_worker.joinLoop(log,"loader_worker");
	}

	/////////////////////////////////////////////////////////////
	// 内部処理
	
	// ページ構成パラメータの解釈
	void init_page(Intent intent){
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
			int x,y;
			try{
				if(uri==null) return;
				
				if(src_image != null ) return;

				log.d("loading image..");
				try{
					ContentResolver cr = getContentResolver();
					InputStream is = cr.openInputStream(uri);
					BitmapFactory.Options option = new BitmapFactory.Options();
					option.inTargetDensity =0;
					option.inDensity = 0;
					option.inScaled =false;
					src_image = BitmapFactory.decodeStream(is,null,option);
					if( src_image == null ){
						log.e("load failed.");
						return;
					}
				}catch(Throwable ex){
					ex.printStackTrace();
					return;
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
				}else{
					if( src_aspect >= frame_aspect ){
						shown_w = frame_w;
						shown_h = (int)( 0.5f + ( src_h * frame_w ) / (float)src_w);
					}else{
						// 画像は表示枠より縦長.上下ベースで合わせる
						shown_h = frame_h;
						shown_w = (int)( 0.5f + ( src_w * frame_h ) / (float)src_h);
					}	
				}
				// 表示画像の位置(表示枠基準)
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
				
				if(bCancelled) return;
				
				ui_handler.post(new Runnable() {
					@Override
					public void run() {
						if(bCancelled) return;
						try{
							// 画像を表示
							ivImage.setImageDrawable(new BitmapDrawable(getResources(),shown_image));

							// 選択範囲を設定
							setSelection(
								 prev_selection.left
								,prev_selection.top 
								,prev_selection.width()
								,prev_selection.height()
							);
							bLoading =false;
						}catch(Throwable ex){
							ex.printStackTrace();
							finish();
							return;
						}
					}
				});
			}catch(Throwable ex){
				ex.printStackTrace();
			}
		}
	}

	void setSelection(int new_x,int new_y,int new_w,int new_h){
		// 表示枠のサイズ
		int frame_w = flOuter.getWidth();
		int frame_h = flOuter.getHeight();
		// 幅と高さをクリップ
		new_w =  new_w > frame_w ? frame_w : new_w < 0 ? 0 : new_w;
		new_h =  new_h > frame_h ? frame_h : new_h < 0 ? 0 : new_h;
		// 移動可能範囲
		int x_min = (int)shown_image_rect.left;
		int y_min = (int)shown_image_rect.top;
		int x_max = (int)shown_image_rect.right  - new_w;
		int y_max = (int)shown_image_rect.bottom - new_h;
		// 位置を移動可能範囲でクリップ
		new_x = new_x < x_min ? x_min : new_x > x_max ? x_max : new_x;
		new_y = new_y < y_min ? y_min : new_y > y_max ? y_max : new_y;
		// 選択範囲を更新
		LinearLayout.LayoutParams lpSelection = (LinearLayout.LayoutParams) ivSelection.getLayoutParams();
		lpSelection.setMargins(
			 new_x
			,new_y
			,frame_w -new_w -new_x
			,frame_h -new_h -new_y
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
			try{
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
				Bitmap wall_image = Bitmap.createBitmap(wall_w,wall_h,src_image.getConfig());
				RectF wall_rect = new RectF(0,0,wall_w,wall_h);
				Canvas c = new Canvas(wall_image);
				c.drawARGB(255,0,0,0);
				Paint paint = new Paint();
				paint.setFilterBitmap(true);
				c.drawBitmap(src_image,selection,wall_rect,paint);
				// 
				src_image.recycle();
				log.d("set wallpaper:%d,%d,%s",wall_image.getWidth(),wall_image.getHeight(),wall_image.getConfig());
				wpm.clear();
				wpm.setBitmap(wall_image);
			}catch(Throwable ex){
				ex.printStackTrace();
			}
			dialog.dismiss();
			finish();
		}
	}
}