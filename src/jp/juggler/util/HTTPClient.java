package jp.juggler.util;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import android.os.SystemClock;

public class HTTPClient {
	static final LogCategory log = new LogCategory("HTTPClient");
	static final boolean debug_http =false;

	public String[] extra_header;
	public HashMap<String,String> cookie_pot;
	public int max_try;
	public int timeout_http;
	public int timeout_dns = 1000*3;
	public String caption;
	

	
	public HTTPClient(int timeout,int max_try,String caption,CancelChecker cancel_checker){
		this.cancel_checker = cancel_checker;
		this.timeout_http = timeout;
		this.max_try = max_try;
		this.caption = caption;
	}

	public void setCookiePot(boolean enabled){
		if( enabled == (cookie_pot!=null) ) return;
		cookie_pot = (enabled? new HashMap<String,String>() : null );
	}

	// check DNS resolver
	protected boolean checkDNSResolver(URL url){
		final URL closure_url = url; 
		final Boolean[] result = new Boolean[]{false};

		// 裏スレッドで実行する
		Thread t = new Thread(new Runnable(){
			public void run(){
				boolean b = false;
				try{
					InetAddress.getByName(closure_url.getHost());
					b=true;
				}catch(Throwable ex){
					log.e("[%s,name]%s",ex.getMessage());
				}
				synchronized(HTTPClient.this){
					result[0]=b;
					try{
						HTTPClient.this.notify();
					}catch(Throwable ex){
						ex.printStackTrace();
					}
				}
			}
		});
		t.start();		
		// 少し待つ
		synchronized(this){
			try{
				wait(this.timeout_dns);
			}catch(InterruptedException ex){
				// エラー通知は必要ない
			}catch(Throwable ex){
				ex.printStackTrace();
			}
		}
		// スレッドを殺す
		// HTC Desire だと interruptしても  getByName() が止まってくれない。タイムアウトさせてごまかす
		for(int i=0;i<10;++i){
			//
			try{ t.interrupt(); }catch(Throwable ex){}
			//
			try{ t.join(10); }catch(Throwable ex){}
			//
			if( ! t.isAlive() ) break;
		}
		// もう止まってるはずなので、もう一度呼ぶ
		try{ t.join(10); }catch(Throwable ex){}
		// 結果を返す
		return result[0];
	}
		
	
	///////////////////////////////
	// デフォルトの入力ストリームハンドラ
	
	HTTPClientReceiver default_receiver = new HTTPClientReceiver(){
		byte[] buf = new byte[2048];
		ByteArrayOutputStream bao = new ByteArrayOutputStream(0);

		public byte[] onHTTPClientStream(CancelChecker cancel_checker,InputStream in,int content_length){
			try{
				bao.reset();
			  	for(;;){
					if( cancel_checker.isCancelled() ){
						if(debug_http) log.w(
							"[%s,read]cancelled!"
							,caption
						);
						return null;
					}
					int delta = in.read(buf);
					if(delta<=0) break;
					bao.write(buf,0,delta);
			  	}
			  	// content_length が有効ならサイズをチェックする
			  	if( content_length > 0 
			  	&& bao.size() != content_length 
			  	){
					if(debug_http) log.w(
						"[%s,read] bad data size"
						,caption
					);
			  		return null;
			  	}
		  		return bao.toByteArray();
			}catch(Throwable ex){
				log.e(
					"[%s,read] %s:%s"
					,caption
					,ex.getClass().getSimpleName()
					,ex.getMessage()
				);
			}
			return null;
		}
	};

	///////////////////////////////
	// 別スレッドからのキャンセル処理

	public CancelChecker cancel_checker;
	volatile Thread io_thread;
	
	public boolean isCancelled(){
		return cancel_checker.isCancelled();
	}
	public synchronized void cancel(){
		Thread t = io_thread;
		if(t==null) return;
		log.i(
			"[%s,cancel] %s"
			,caption
			,t
		);
		try{
			t.interrupt();
		}catch(Throwable ex){
			ex.printStackTrace();
		}
	}

	///////////////////////////////
	// HTTPリクエスト処理
	public byte[] getHTTP(String url){
		return getHTTP(url,default_receiver);
	}
	public byte[] getHTTP(String url,HTTPClientReceiver receiver){
		try{
			synchronized(this){
				this.io_thread = Thread.currentThread();
			}
			URL urlObject = new URL(url);
/*
			// desire だと、どうもリソースリークしているようなので行わないことにした。
			// DNSを引けるか確認する
			if(debug_http) Log.d(logcat,"check hostname "+url);
			if( !checkDNSResolver(urlObject) ){
				Log.w(logcat,"broken name resolver");
				return null;
			}
*/			
			long timeStart = SystemClock.elapsedRealtime();
			for(int nTry=0;nTry<max_try;++nTry){
				try{
					// キャンセルされたか確認
					if( cancel_checker.isCancelled() ) return null;

					// http connection
					HttpURLConnection conn = (HttpURLConnection) urlObject.openConnection();

					// 追加ヘッダがあれば記録する
					if(extra_header !=null){
						for(int i=0;i<extra_header.length;i+=2){
							conn.addRequestProperty(extra_header[i],extra_header[i+1]);
						}
					}

					// クッキーがあれば指定する
					if( cookie_pot != null ){
						StringBuffer sb = new StringBuffer();
						for( Map.Entry<String,String> pair : cookie_pot.entrySet() ){
							if(sb.length()>0) sb.append("; ");
							sb.append(pair.getKey());
							sb.append('=');
							sb.append(pair.getValue());
						}
						conn.addRequestProperty("Cookie",sb.toString());
					}

					// リクエストを送ってレスポンスの頭を読む
					try{
						if(debug_http) log.d("[%s,connect] start",caption);
						conn.setDoInput(true);
						conn.setDoOutput(false);
						conn.setConnectTimeout(this.timeout_http);
						conn.setReadTimeout(this.timeout_http);
						conn.connect();
						int rcode = conn.getResponseCode();
						if( rcode >= 300 ){
							if( rcode >= 400 && rcode < 500 ){
								log.e("[%s,connect] permanent error %d",caption,rcode);
								return null;
							}else{
								log.e("[%s,connect] temporary error %d",caption,rcode);
								continue;
							}
						}
						// クッキーが来ていたら覚える
					  	if( cookie_pot != null ){
					  		String v=conn.getHeaderField("set-cookie");
					  		if(v!=null){
								int pos = v.indexOf('=');
								cookie_pot.put(v.substring(0,pos),v.substring(pos+1));
					  		}
					  	}
					}catch(UnknownHostException ex){
						log.e("[%s,connect] %s:%s"
								,caption
								,ex.getClass().getSimpleName()
								,ex.getMessage()
						);
						// このエラーはリトライしてもムリ
						conn.disconnect();
						return null;
					}catch(Throwable ex){
						log.e("[%s,connect] %s:%s"
								,caption
								,ex.getClass().getSimpleName()
								,ex.getMessage()
						);
						// 他のエラーはリトライしてみよう。キャンセルされたなら次のループの頭で抜けるはず
						conn.disconnect();
						continue;
					}
					InputStream in =null;
					try{
						if(debug_http) log.d("[%s,read] start",caption);
						in = conn.getInputStream();
						int content_length = conn.getContentLength();
						byte[] data =receiver.onHTTPClientStream(cancel_checker,in,content_length);
					  	if( data == null ) continue;
					  	if( data.length >0){
					  		if(nTry>0) log.w("[%s] OK. retry=%d,time=%dms"
			  					,caption
			  					,nTry
			  					,SystemClock.elapsedRealtime() - timeStart
					  		);
					  		return data;
					  	}
					  	if( !cancel_checker.isCancelled() ) log.w(
					  			"[%s,read] empty data."
					  			,caption
					  	);
					}finally{
						if(in!=null) in.close();
						conn.disconnect();
					}
				}catch(Throwable ex){
					ex.printStackTrace();
				}
			}
			log.e("[%s] too many retry.",caption);
		}catch(Throwable ex){
			ex.printStackTrace();
		}finally{
			synchronized(this){
				io_thread = null;
			}
		}
		return null;
	}
}
