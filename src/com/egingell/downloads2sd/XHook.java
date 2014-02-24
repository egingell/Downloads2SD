package com.egingell.downloads2sd;

import java.io.File;

import android.content.ContentValues;
import android.net.Uri;
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
	final static File sDir = new File(Environment.getDataDirectory(), "data/com.egingell.downloads2sd/shared_prefs/prefs.xml");
	final static XSharedPreferences xprefs = new XSharedPreferences(sDir);
	String hint;
	public XHook() throws Throwable {
		xprefs.makeWorldReadable();
		Interplanetary.init(this.getClass().getName());
		Interplanetary.prefsMap = xprefs.getAll();
		try {
			hint = (String) Class.forName("android.provider.Downloads$Impl").getField("COLUMN_FILE_NAME_HINT").get(null);
		} catch (Throwable e) {
			hint = "hint";
		}
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
						File oldFile = ((File) XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args)),
							 newFile = null;
						String oldPath = oldFile.getPath(),
							   key = Interplanetary.revCatalog.get((String) param.args[0]),
							   newPath = (String) Interplanetary.prefsMap.get(key);
						boolean isDirectory, exists, canRead, canWrite, canExecute;
						isDirectory = exists = canRead = canWrite =	canExecute = false;
						if (newPath != null) {
							newFile = new File(newPath).getCanonicalFile();
							isDirectory = newFile.isDirectory();
							exists = newFile.exists();
							canRead = newFile.canRead();
							canWrite = newFile.canWrite();
							canExecute = newFile.canExecute();
						}
						String perms = (exists ? "e" : "-") + (isDirectory ? "d" : "-") + (canRead ? "r" : "-") + (canWrite ? "w" : "-") + (canExecute ? "x" : "-");
						XposedBridge.log(key + " : " + oldPath + " => " + newPath + " (" + perms + ")");
						return (newFile == null ||! isDirectory ||! exists ||! canRead ||! canWrite) ? oldFile : newFile;
					}
				});
			} catch(Throwable e) {
				XposedBridge.log(e);
			}
		} catch(Throwable e) {
			XposedBridge.log(e);
		}
		try {
			if (lpparam.packageName.equals("com.android.providers.downloads")) {
				try {
					XposedHelpers.findAndHookMethod("com.android.providers.downloads.DownloadProvider", lpparam.classLoader, "checkFileUriDestination", ContentValues.class, new XC_MethodReplacement() {
						@Override
						protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
							ContentValues values = (ContentValues) param.args[0];
							String fileUri = values.getAsString(hint);
					        if (fileUri == null) {
					            throw new IllegalArgumentException("DESTINATION_FILE_URI must include a file URI under COLUMN_FILE_NAME_HINT");
					        }
					        Uri uri = Uri.parse(fileUri);
					        String scheme = uri.getScheme();
					        if (scheme == null || !scheme.equals("file")) {
					            throw new IllegalArgumentException("Not a file URI: " + uri);
					        }
					        final String path = uri.getPath();
					        if (path == null) {
					            throw new IllegalArgumentException("Invalid file URI: " + uri);
					        }
					        /*
					        try {
					            final String canonicalPath = new File(path).getCanonicalPath();
					            final String externalPath = Environment.getExternalStorageDirectory().getAbsolutePath();
					            if (!canonicalPath.startsWith(externalPath)) {
					                throw new SecurityException("Destination must be on external storage: " + uri);
					            }
					        } catch (Throwable e) {
					            throw new SecurityException("Problem resolving path: " + uri);
					        }
					        */
							return null;
						}
					});
				} catch(Throwable e) {
					XposedBridge.log(e);
				}
			}
		} catch(Throwable e) {
			XposedBridge.log(e);
		}
	}
}
