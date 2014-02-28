package com.egingell.downloads2sd;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import android.os.Environment;
import android.text.format.DateFormat;
import android.util.Log;

public class Interplanetary {
	public final static CharSequence format = "E MMM dd, kk:mm";
	public final static Calendar mCalendar = Calendar.getInstance(TimeZone.getDefault());
	public final static String ME = "downloads2sd";
	public final static ArrayList<String> tags = new ArrayList<String>();
	public final static HashMap<String,String> catalog = new HashMap<String,String>();
	public final static HashMap<String,String> revCatalog = new HashMap<String,String>();
	public static Map<String,?> prefsMap;
	static public Class<?> constants;
	public static void init(String callingClassName) {
		tags.clear();
		for (String s : new String[] {
			"DIRECTORY_MUSIC",
			"DIRECTORY_PODCASTS",
			"DIRECTORY_RINGTONES",
			"DIRECTORY_ALARMS",
			"DIRECTORY_NOTIFICATIONS",
			"DIRECTORY_PICTURES",
			"DIRECTORY_MOVIES",
			"DIRECTORY_DOWNLOADS", 
			"DIRECTORY_DCIM",
		}) {
			tags.add(s);
			try {
				catalog.put(s, (String) Environment.class.getField(s).get(null));
				revCatalog.put((String) Environment.class.getField(s).get(null), s);				
			} catch (Throwable e) {}
		}
		Log.d(ME, date() + ": " + callingClassName + " instanciated " + Interplanetary.class.getClass().getName() + ".");
	}
	
	public static String date() {
		return DateFormat.format(format, mCalendar).toString();
	}
}