package com.egingell.downloads2sd;

import java.io.File;
import java.util.HashMap;

import com.egingell.downloads2sd.R;
import com.lamerman.FileDialog;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;

public class DownloadsHook extends Activity {

	public DownloadsHook() throws Throwable {/** Moooooooooooooo! **/}
	
	private LinearLayout top = null;
	private TextView header;
	private SharedPreferences prefs;
	private int listCount = 0;

	@SuppressLint("WorldReadableFiles")
	@SuppressWarnings("deprecation")
	@Override
	protected void onCreate(Bundle b) {
		super.onCreate(b);
		setContentView(R.layout.folders_ui);
		Interplanetary.init(this.getClass().getName());
		header = (TextView) findViewById(R.id.header);
		top = (LinearLayout) findViewById(R.id.top);
		top.removeAllViews();
		try {
			prefs = getSharedPreferences("prefs", Context.MODE_WORLD_READABLE);
			prefs.edit().putBoolean("prefsmade", true).commit();
			listCount = 0;
			for (String s : Interplanetary.tags) {
				addLayout(s);
			}
		} catch (Throwable e) {
			Log.e(Interplanetary.ME, Interplanetary.date(), e);
		}
	}
	private OnClickListener function = new OnClickListener() {
		@Override
		public void onClick(View button) {
			try {
				Intent intent = new Intent(getBaseContext(), FileDialog.class);
				String key = (String) button.getTag();
				EditText ev = (EditText) findViewByTag(key + "_edit");
				String path = prefs.getString(key, ev.getText().toString());
                intent.putExtra(FileDialog.START_PATH, path);
                intent.putExtra("whichDialog", key);
                
                //can user select directories or not
                intent.putExtra(FileDialog.CAN_SELECT_DIR, true);
                intent.putExtra(FileDialog.CAN_ONLY_SELECT_DIRS, true);
                
                //alternatively you can set file filter
                //intent.putExtra(FileDialog.FORMAT_FILTER, new String[] { "png" });
                
                startActivityForResult(intent, 1);
			} catch (Throwable e) {
				Log.e(Interplanetary.ME, Interplanetary.date(), e);
			}
		}
	};
	private OnClickListener restoreDefaults = new OnClickListener() {
		@Override
		public void onClick(View button) {
			String tag = (String) button.getTag();
			File oldFile = Environment.getExternalStoragePublicDirectory(tag + ":orig");
			EditText ev = (EditText) findViewByTag(tag + "_edit");
			ev.setText(oldFile.getPath());
			prefs.edit().putString(tag, oldFile.getPath()).commit();
			Interplanetary.prefsMap = prefs.getAll();
		}
	};
	@Override
	public synchronized void onActivityResult(final int requestCode, int resultCode, final Intent data) {
		if (resultCode == Activity.RESULT_OK) {
            String filePath = data.getStringExtra(FileDialog.RESULT_PATH);
            File dir = new File(filePath);
            if (dir.isFile()) {
				try {
					throw new Throwable(filePath + " is not a directory.");
				} catch (Throwable e) {}
            } else {
            	if (!dir.mkdirs()) {
            		try {
    					throw new Throwable(filePath + " unable to create directory.");
    				} catch (Throwable e) {}
            	}
            }
            final String whichDialog = data.getStringExtra("whichDialog");
			EditText ev = (EditText) findViewByTag(whichDialog + "_edit");
			ev.setText(filePath);
			prefs.edit().putString(whichDialog, filePath).commit();
			Interplanetary.prefsMap = prefs.getAll();
		} else if (resultCode == Activity.RESULT_CANCELED) {
            Log.w(Interplanetary.ME, Interplanetary.date() + ": file not selected");
		}
    }
	private HashMap<String,View> mViews = new HashMap<String,View>();
	@SuppressLint("NewApi")
	private void addLayout(String tag) {
		//Log.d(common.ME, common.date() + ": attempting to add " + text + " to listview");
		try {
			int index;
			final Display display = getWindowManager().getDefaultDisplay();
			Point size = new Point();
			display.getSize(size);
			int width = size.x - 10;
			final LayoutParams layouts = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
							   buttonLayouts = new LayoutParams(width / 2, ViewGroup.LayoutParams.WRAP_CONTENT);
			LinearLayout layout = new LinearLayout(top.getContext()), buttons = new LinearLayout(top.getContext());
			
			layout.setOrientation(LinearLayout.VERTICAL);
			buttons.setOrientation(LinearLayout.HORIZONTAL);
			
			// Browse
			Button browse = new Button(layout.getContext());
			browse.setText(R.string.browse);
			browse.setLayoutParams(buttonLayouts);
			browse.setTag(tag);
			browse.setOnClickListener(function);
			mViews.put(tag, browse);
			
			// Reset
			Button restore = new Button(layout.getContext());
			restore.setText(R.string.restore);
			restore.setLayoutParams(buttonLayouts);
			restore.setTag(tag);
			restore.setOnClickListener(restoreDefaults);
			mViews.put(tag, restore);
			
			index = 0;
			buttons.addView(browse, index++);
			buttons.addView(restore, index++);
			
            TextView appText = new TextView(layout.getContext());
            appText.setText(Interplanetary.catalog.get(tag));
            appText.setTextSize(TypedValue.COMPLEX_UNIT_PX, header.getTextSize());
            appText.setTypeface(header.getTypeface());
            appText.setTextColor(header.getTextColors());
            appText.setLayoutParams(layouts);
            mViews.put(tag + "_text", appText);
            
            EditText ev = new EditText(layout.getContext());
            ev.setTag(tag + "_edit");
            ev.setLayoutParams(layouts);
            ev.setFocusable(false);
            try {
            	ev.setText(prefs.getString(tag, Environment.getExternalStoragePublicDirectory((String) Environment.class.getField(tag).get(null)).getPath()));
            } catch (Throwable e) {}
            ev.setSingleLine();
            mViews.put(tag + "_edit", ev);

            index = 0;
			layout.addView(appText, index++);
			layout.addView(ev, index++);
			layout.addView(buttons, index++);
			
			top.addView(layout, listCount++);
			//Log.d(common.ME, common.date() + ": adding " + text + ":" + tag + " to listview at position " + Integer.toString(listCount));
		} catch (Throwable e) {
			Log.e(Interplanetary.ME, Interplanetary.date(), e);
		}
	}
	
	private View findViewByTag(String tag) {
		return mViews.get(tag);
	}
}