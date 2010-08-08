package jp.juggler.util;

import java.io.InputStream;

public interface HTTPClientReceiver {
	byte[] onHTTPClientStream(CancelChecker cancel_checker,InputStream in,int content_length);
}
/*

	HTTPClientに対して、カスタム化したバッファ管理をおこないたい場合に
	このインタフェースを実装したものをgetHTTP()の第二引数に指定する。

*/