/*
 * logcat | grep "file:///storage/extSdCard/Download"
 * logcat | grep "downloads2sd"
 * tail -f -n 100 /data/data/de.robv.android.xposed.installer/log/debug.log
 */

package com.egingell.downloads2sd;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.regex.Pattern;

import org.xmlpull.v1.XmlPullParser;

import android.content.ContentValues;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class XHook implements IXposedHookLoadPackage, IXposedHookZygoteInit {
	public static String PATH = null;
	final static File sDir = new File(Environment.getDataDirectory(), "data/com.egingell.downloads2sd/shared_prefs/prefs.xml");
	final static XSharedPreferences xprefs = new XSharedPreferences(sDir);
	String hint;
	public XC_MethodHook externalSdCardAccessHook;

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
		
		externalSdCardAccessHook = new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(MethodHookParam param)
					throws Throwable {
				prefs.reload();
				String permission = (String) param.args[1];
				if (permission
						.equals("android.permission.WRITE_EXTERNAL_STORAGE")
						|| permission
								.equals("android.permission.ACCESS_ALL_EXTERNAL_STORAGE")) {
					Class<?> process = XposedHelpers.findClass(
							"android.os.Process", null);
					int gid = (Integer) XposedHelpers.callStaticMethod(process,
							"getGidForName", "media_rw");
					Object permissions = null;
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
						permissions = XposedHelpers.getObjectField(
								param.thisObject, "mPermissions");
					} else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT) {
						Object settings = XposedHelpers.getObjectField(
								param.thisObject, "mSettings");
						permissions = XposedHelpers.getObjectField(settings,
								"mPermissions");
					}
					Object bp = XposedHelpers.callMethod(permissions, "get",
							permission);
					int[] bpGids = (int[]) XposedHelpers.getObjectField(bp,
							"gids");
					XposedHelpers.setObjectField(bp, "gids",
							appendInt(bpGids, gid));
				}
			}
		};
	}
	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
		if ("android".equals(lpparam.packageName)
				&& "android".equals(lpparam.processName)) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				XposedHelpers.findAndHookMethod(
						XposedHelpers.findClass(
								"com.android.server.SystemConfig",
								lpparam.classLoader), "readPermission",
						XmlPullParser.class, String.class,
						externalSdCardAccessHook);
			} else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT) {
				XposedHelpers.findAndHookMethod(XposedHelpers.findClass(
						"com.android.server.pm.PackageManagerService",
						lpparam.classLoader), "readPermission",
						XmlPullParser.class, String.class,
						externalSdCardAccessHook);
			}
		}

		try {
			Class<?> clazz = Environment.class;
			try {
				XposedHelpers.findAndHookMethod(clazz, "getExternalStoragePublicDirectory", String.class, new XC_MethodHook() {
					@Override
					protected void afterHookedMethod(MethodHookParam param) throws Throwable {
						xprefs.reload();
						Interplanetary.prefsMap = xprefs.getAll();
						File oldFile = (File) param.getResult(),
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
						try {
							if (Interplanetary.splitColon.matcher(key).find()) {
								String[] split = Interplanetary.splitColon.split(key);
								if (split[1].equals("orig")) {
									param.args[0] = split[0];
									return;
									//return ((File) XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args));
								}
							}
						} catch (Throwable e) {
							XposedBridge.log(e);
						}
						String perms = (exists ? "e" : "-") + (isDirectory ? "d" : "-") + (canRead ? "r" : "-") + (canWrite ? "w" : "-") + (canExecute ? "x" : "-");
						XposedBridge.log(key + " : " + oldPath + " => " + newPath + " (" + perms + ")");
						if (newFile == null ||! isDirectory ||! exists ||! canRead ||! canWrite) {
							param.setResult(oldFile);
						} else {
							param.setResult(newFile);
						}
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
					XposedHelpers.findAndHookMethod("com.android.providers.downloads.DownloadProvider", lpparam.classLoader, "checkFileUriDestination", ContentValues.class, new XC_MethodHook() {
						@Override
						protected void afterHookedMethod(MethodHookParam param) throws Throwable {
							Log.d(Interplanetary.ME, "Calling com.android.providers.downloads.DownloadProvider#checkFileUriDestination#after");
							
							ContentValues values = (ContentValues) param.args[0];
							if (values.containsKey("oldhint")) {
								values.remove(hint);
								values.put(hint, values.getAsString("oldhint"));
								values.remove("oldhint");
							}							
						}
						@Override
						protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
							Log.d(Interplanetary.ME, "Calling com.android.providers.downloads.DownloadProvider#checkFileUriDestination#before");
							ContentValues values = (ContentValues) param.args[0];
							String fileUri = values.getAsString(hint);
							values.put("oldhint", fileUri);
							final String path = Uri.parse(fileUri).getPath();
							if (path == null) {
								return;
							}
							final String canonicalPath = new File(path).getCanonicalPath();
							final String externalPath = Environment.getExternalStorageDirectory().getAbsolutePath();
							if (!canonicalPath.startsWith(externalPath)) {
								values.remove(hint);
								values.put(hint, "file://" + externalPath);
							}
							XposedBridge.log(Interplanetary.ME + ": before " + fileUri);
							XposedBridge.log(Interplanetary.ME + ": after " + values.getAsString(hint));
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
	final Pattern pattern = Pattern.compile("[\\s]+");
	final File partitions = new File("/proc/partitions"),
			   mounts = new File("/proc/mounts");
	public File[] findExtSdCards() {
		String line, line2;
		ArrayList<File> out = new ArrayList<File>();
		final ArrayList<String> partitionsLines = new ArrayList<String>(),
						  mountsLines = new ArrayList<String>();
		File[] files = null;
		try {
			FileInputStream partitionsIs = new FileInputStream(partitions),
								  mountsIs = new FileInputStream(mounts);
			BufferedReader partitionsReader = new BufferedReader(new InputStreamReader(partitionsIs)),
						   		 mountsReader = new BufferedReader(new InputStreamReader(mountsIs));
			while ((line = partitionsReader.readLine()) != null) {
				partitionsLines.add(line);
			}
			while ((line2 = mountsReader.readLine()) != null) {
				mountsLines.add(line2);
			}
			partitionsReader.close();
			mountsReader.close();
			for (String s : partitionsLines) {
				line = s.trim();
				String[] info = pattern.split(line);
				if (info.length < 4) continue;
				Log.d(Interplanetary.ME, Interplanetary.date() + ": info[0] = '" + info[0] + "'");
				Log.d(Interplanetary.ME, Interplanetary.date() + ": info[1] = '" + info[1] + "'");
				Log.d(Interplanetary.ME, Interplanetary.date() + ": info[3] = '" + info[3] + "'");
				String vold = "/dev/block/vold/" + info[0] + ":" + info[1],
					   device = "/dev/block/" + info[3];
				for (String s2 : mountsLines) {
					line2 = s2.trim();
					String[] info2 = pattern.split(line2);
					if (info2.length < 6) continue;
					if (info2[0].equals(vold) || info2[0].equals(device)) {
						out.add(new File(info2[1]));
					}
					Log.d(Interplanetary.ME, Interplanetary.date() + ": info2[0] = '" + info2[0] + "'");
					Log.d(Interplanetary.ME, Interplanetary.date() + ": info2[1] = '" + info2[1] + "'");
				}
			}
			if (out.size() > 0) {
				files = new File[out.size()];
				int i = 0;
				for (File f : out) {
					files[i++] = f;
	        		Log.d(Interplanetary.ME, Interplanetary.date() + ": " + f.getPath());
				}
			} else {
				Log.e(Interplanetary.ME, Interplanetary.date() + ": no directories added.");
			}
		} catch (Throwable e) {
			Log.e(Interplanetary.ME, Interplanetary.date(), e);
		}
		return files;
	}
	

	public int[] appendInt(int[] cur, int val) {
		if (cur == null) {
			return new int[] { val };
		}
		final int N = cur.length;
		for (int i = 0; i < N; i++) {
			if (cur[i] == val) {
				return cur;
			}
		}
		int[] ret = new int[N + 1];
		System.arraycopy(cur, 0, ret, 0, N);
		ret[N] = val;
		return ret;
	}
}
