package jp.juggler.util;

public interface CancelChecker {
	boolean isCancelled();
}

/*

呼び出し元のオブジェクトに対してキャンセルされたかどうかを確認するためのインタフェース。
HTTPClient 等,ワーカースレッドの下で動作する処理から使用される。



*/