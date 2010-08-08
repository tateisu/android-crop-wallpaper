package jp.juggler.util;
import android.util.Log;

public final class LogCategory {
	public static final boolean enabled = true;
	
	String tag;
	public LogCategory(String tag){
		this.tag = tag;
	}
	public final void log(String fmt,Object[] args,int level){
		if(enabled){
			if(args.length>0) fmt = String.format(fmt,args);
			Log.println(level,tag,fmt);
		}
	}
	public final void d(String fmt,Object... args){ if(enabled) log(fmt,args,Log.DEBUG);}
	public final void e(String fmt,Object... args){ if(enabled) log(fmt,args,Log.ERROR);}
	public final void i(String fmt,Object... args){ if(enabled) log(fmt,args,Log.INFO);}
	public final void v(String fmt,Object... args){ if(enabled) log(fmt,args,Log.VERBOSE);}
	public final void w(String fmt,Object... args){ if(enabled) log(fmt,args,Log.WARN);}
}
