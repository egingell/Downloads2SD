package com.egingell.downloads2sd;

import java.io.File;

import android.os.Environment;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class XHook implements IXposedHookLoadPackage, IXposedHookZygoteInit {
	public static String PATH = null;
	final static File sDir = new File(Environment.getDataDirectory(), "data/com.egingell.downloads2sd/shared_prefs");
	final static File prefsFile = new File(sDir, "prefs.xml"); 
	final static XSharedPreferences xprefs = new XSharedPreferences(prefsFile);
	public XHook() {
		xprefs.makeWorldReadable();
		Interplanetary.init(this.getClass().getName());
		Interplanetary.prefsMap = xprefs.getAll();
	}
	@Override
	public void initZygote(StartupParam startupParam) throws Throwable {
		PATH = startupParam.modulePath;
	}
	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
		try {
			Class<?> clazz = Environment.class;
			try {
				XposedHelpers.findAndHookMethod(clazz, "getExternalStoragePublicDirectory", String.class, new XC_MethodReplacement() {
					@Override
					protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
						xprefs.reload();
						Interplanetary.prefsMap = xprefs.getAll();
						String returnObject = ((File) XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args)).getPath(),
							key = Interplanetary.revCatalog.get((String) param.args[0]),
							path = (String) Interplanetary.prefsMap.get(key);
						XposedBridge.log(key + " : " + returnObject + " => " + path);
						return new File(path == null ? returnObject : path);
					}
				});
			} catch(Throwable e) {
				XposedBridge.log(e);
			}
		} catch(Throwable e) {
			XposedBridge.log(e);
		}
	}
}
