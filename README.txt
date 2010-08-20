http://juggler.jp/tateisu/CropWallpaper/
http://instnow.appspot.com/?p=jp.juggler.CropWallpaper
market://search?q=jp.juggler.CropWallpaper

* synopsis
simple application to set wallpaper.
- image chooser that can list-up 2k over images at once.
- image chooser that manages favorite images.
- function to set image as wallpaper with cropping.
- requires android OS 2.0 or later

* motivation
OpenDesire's gallery application always re-scale image and re-scale its again at setting wallpaper. It makes image degradation in many case. My application does one-time rescale to decrease image degradation.

Many image chooser raises error at showing a folder that contains many image. My application was tested with a folder that has 2,000 over images.

* keyword
crop,wallpaper,cropwallpaper,utility,tool

--------------------------------

* 1.11 の目標
** VIEW,SEND,DELETE,EDIT
それぞれIntentのActionを変えて呼び出す
Delete は確認ダイアログをつける

** OuterRect モード
画像を全て囲むモードを用意する。

** 設定画面
- 大きい画像を扱う際のメモリ消費許容量
- サムネイル一覧の先読み量
- サムネイル画面でシングルタップした際のアクション

** バグ修正
- フォルダ一覧の並び順
- フォルダ一覧で、個数の表記がスクロールバーに隠れないようパディングを調整する
- フォルダ一覧で、スクロールバーにノブをつける

