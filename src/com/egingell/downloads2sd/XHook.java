/*
 * logcat | grep "file:///storage/extSdCard/Download"
 * logcat | grep "download2sd"
 */

package com.egingell.downloads2sd;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.regex.Pattern;

import android.content.ContentValues;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
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
					final Class<?> cStorageManager = Class.forName("com.android.providers.downloads.StorageManager");
					final Class<?> cConstants = Class.forName("com.android.providers.downloads.Constants");
					final Class<?> cDownloadsImpl = Class.forName("android.provider.Downloads$Impl");
					XposedBridge.hookAllConstructors(cStorageManager, new XC_MethodHook() {
						final Pattern pattern = Pattern.compile("[\\s]+");
						final File partitions = new File("/proc/partitions"),
								   mounts = new File("/proc/mounts");
						private File[] findExtSdCards() {
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
									line = s;
									String[] info = pattern.split(line);
									int i = 0;
									for (String d : info) {
										Log.d("downloads2sd", Interplanetary.date() + ": info[" + (i++) + "] = '" + d + "'");
									}
									String vold = "/dev/block/vold/" + info[1] + ":" + info[2],
										   device = "/dev/block/" + info[4];
									for (String s2 : mountsLines) {
										line2 = s2;
										String[] info2 = pattern.split(line2);
										if (info2[0].equals(vold) || info2[0].equals(device)) {
											out.add(new File(info[1]));
										}
										int i1 = 0;
										for (String d : info2) {
											Log.d("downloads2sd", Interplanetary.date() + ": info2[" + (i1++) + "] = '" + d + "'");
										}
									}
								}
								if (out.size() > 0) {
									files = new File[out.size()];
									int i = 0;
									for (File f : out) {
										files[i++] = f;
						        		Log.d("downloads2sd", Interplanetary.date() + ": " + f.getPath());
									}
								} else {
									Log.d("downloads2sd", Interplanetary.date() + ": no files added.");
								}
							} catch (Throwable e) {
								XposedBridge.log(e);
							}
							return files;
						}
						@Override
		                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
							XposedHelpers.setAdditionalInstanceField(cStorageManager, "mExternalStoragePublicDir", findExtSdCards());
						}
					});
					XposedHelpers.findAndHookMethod(cStorageManager, "verifySpace", int.class, String.class, long.class, new XC_MethodReplacement() {
						@Override
						protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
							XposedHelpers.callMethod(param.thisObject, "resetBytesDownloadedSinceLastCheckOnSpace");
							int destination = (Integer) param.args[0];
							String path = (String) param.args[1];
							long length = (Long) param.args[2];
							
					        File dir = null;
					        if (XposedHelpers.getStaticBooleanField(cConstants, "LOGV")) {
					        	
					            Log.i((String) XposedHelpers.getStaticObjectField(cConstants, "TAG"), "in verifySpace, destination: " + destination +
					                    ", path: " + path + ", length: " + length);
					        }
					        if (path == null) {
					            throw new IllegalArgumentException("path can't be null");
					        }
					        File mExternalStorageDir = (File) XposedHelpers.getObjectField(param.thisObject, "mExternalStorageDir"),
					        	 mSystemCacheDir = (File) XposedHelpers.getObjectField(param.thisObject, "mSystemCacheDir"),
					        	 mDownloadDataDir = (File) XposedHelpers.getObjectField(param.thisObject, "mDownloadDataDir");
					        File[] mExternalStoragePublicDir = (File[]) XposedHelpers.getAdditionalInstanceField(cStorageManager, "mExternalStoragePublicDir");
					        final int DESTINATION_CACHE_PARTITION = XposedHelpers.getStaticIntField(cDownloadsImpl, "DESTINATION_CACHE_PARTITION"),
					        		  DESTINATION_CACHE_PARTITION_NOROAMING = XposedHelpers.getStaticIntField(cDownloadsImpl, "DESTINATION_CACHE_PARTITION_NOROAMING"),
					        		  DESTINATION_CACHE_PARTITION_PURGEABLE = XposedHelpers.getStaticIntField(cDownloadsImpl, "DESTINATION_CACHE_PARTITION_PURGEABLE"),
					        		  DESTINATION_EXTERNAL = XposedHelpers.getStaticIntField(cDownloadsImpl, "DESTINATION_EXTERNAL"),
							          DESTINATION_SYSTEMCACHE_PARTITION = XposedHelpers.getStaticIntField(cDownloadsImpl, "DESTINATION_SYSTEMCACHE_PARTITION"),
							       	  DESTINATION_FILE_URI = XposedHelpers.getStaticIntField(cDownloadsImpl, "DESTINATION_FILE_URI");
					        if (destination == DESTINATION_CACHE_PARTITION || destination == DESTINATION_CACHE_PARTITION_NOROAMING || destination == DESTINATION_CACHE_PARTITION_PURGEABLE) {
				                dir = mDownloadDataDir;
					        } else if (destination == DESTINATION_EXTERNAL) {
					            dir = mExternalStorageDir;
					        } else if (destination == DESTINATION_SYSTEMCACHE_PARTITION) {
				                dir = mSystemCacheDir;
					        } else if (destination == DESTINATION_FILE_URI) {
				                if (path.startsWith(mExternalStorageDir.getPath())) {
				                    dir = mExternalStorageDir;
				                } else if (path.startsWith(mDownloadDataDir.getPath())) {
				                    dir = mDownloadDataDir;
				                } else if (path.startsWith(mSystemCacheDir.getPath())) {
				                    dir = mSystemCacheDir;
				                }
					        }
					        if (mExternalStoragePublicDir != null) {
					        	for (File f : mExternalStoragePublicDir) {
					        		if (path.startsWith(f.getPath())) {
					        			dir = f;
					        		}
					        	}
					        }
					        if (dir == null) {
					            throw new IllegalStateException("invalid combination of destination: " + destination + ", path: " + path);
					        }
					        XposedHelpers.callMethod(param.thisObject, "findSpace", dir, length, destination);
					        return null;
						}
					});
				} catch(Throwable e) {
					XposedBridge.log(e);
				}
				try {
					XposedHelpers.findAndHookMethod("com.android.providers.downloads.DownloadProvider", lpparam.classLoader, "checkFileUriDestination", ContentValues.class, new XC_MethodReplacement() {
						@Override
						protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
							Log.d("Downloads2SD", "Calling com.android.providers.downloads.DownloadProvider#checkFileUriDestination");
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