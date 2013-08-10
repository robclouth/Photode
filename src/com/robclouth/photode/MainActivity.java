package com.robclouth.photode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.ViewAnimator;

public class MainActivity extends Activity {
	ViewAnimator viewAnimator;
	SketchHandler sketchHandler;
	CameraHandler cameraHandler;
	ProgressLogger logger;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// handle intents
		Intent intent = getIntent();

		String action = intent.getAction();
		Log.w("photode", action);
		if (action == Intent.ACTION_VIEW) {
			// To get the data use
			Uri data = intent.getData();
			Log.w("photode", data.getPath());
			File file = new File(data.getPath());
			SketchHandler.installSketch(file);
		}

		

		if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
			Toast.makeText(MainActivity.this, "Photode can't run while the device is connected via USB. Please disconnect and try again.",
					Toast.LENGTH_LONG).show();
			finish();
			return;
		}

		// inflate views and setup view transitions
		setContentView(R.layout.main_view);
		viewAnimator = ((ViewAnimator) findViewById(R.id.ViewAnimator));
		LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		viewAnimator.addView(inflater.inflate(R.layout.camera_view, viewAnimator, false));
		viewAnimator.addView(inflater.inflate(R.layout.image_view, viewAnimator, false));
		viewAnimator.addView(inflater.inflate(R.layout.sketch_list_view, viewAnimator, false));
		viewAnimator.addView(inflater.inflate(R.layout.parameters_view, viewAnimator, false));
		viewAnimator.addView(inflater.inflate(R.layout.processing, viewAnimator, false));
		viewAnimator.setDisplayedChild(0);

		// setup interactions...
		
		//sketch selector view open button
		((ImageView) findViewById(R.id.sketchListButton)).setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent e) {
				viewAnimator.setDisplayedChild(2);
				return true;
			}
		});

		//sketch selector view close button
		((ImageView) findViewById(R.id.sketchListCloseButton)).setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent e) {
				viewAnimator.setDisplayedChild(0);
				return true;
			}
		});

		//parameters view open button
		((ImageView) findViewById(R.id.parametersButton)).setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent e) {
				viewAnimator.setDisplayedChild(3);
				return true;
			}
		});
		
		//parameters view close button
		((ImageView) findViewById(R.id.parameterCloseButton)).setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent e) {
				viewAnimator.setDisplayedChild(0);
				return true;
			}
		});
		

		//create the logger that hooks into System.out to display println messages in the loaded sketch
		logger = new ProgressLogger(this);

		// if first open install sketches from assets
		installBundledSketches();

		cameraHandler = new CameraHandler(this);
		sketchHandler = new SketchHandler(this);
		sketchHandler.refreshSketchList();

		loadPreferences();
	}

	private void installBundledSketches() {
		SharedPreferences settings = getPreferences(Context.MODE_PRIVATE);
		boolean firstRun = settings.getBoolean("firstRun", true);
		if (!firstRun)
			return;

		File sketchesPath = new File(Environment.getExternalStorageDirectory() + "/photode/sketches/");

		if (!sketchesPath.exists()) {
			if (!sketchesPath.mkdirs()) {
				return;
			}
		}

		AssetManager assetManager = getAssets();
		String[] sketches;
		try {
			sketches = assetManager.list("sketches");
		} catch (IOException e) {
			return;
		}

		for (String photodeFilePath : sketches) {
			try {
				InputStream is = assetManager.open("sketches/" + photodeFilePath);
				File photodeFile = new File(photodeFilePath);
				SketchHandler.installSketch(photodeFile, is);
			} catch (IOException e) {
			}
		}

		SharedPreferences.Editor editor = settings.edit();
		editor.putBoolean("firstRun", false);
		editor.commit();
	}

	boolean doubleBackToExitPressedOnce;

	@Override
	public void onBackPressed() {
		if (doubleBackToExitPressedOnce) {
			super.onBackPressed();
			return;
		}
		this.doubleBackToExitPressedOnce = true;
		Toast.makeText(this, "Please press BACK again to exit", Toast.LENGTH_SHORT).show();
		new Handler().postDelayed(new Runnable() {
			@Override
			public void run() {
				doubleBackToExitPressedOnce = false;
			}
		}, 2000);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		// ugly hack to kill the asyncTask if it's still running: there must
		// be a better way
		if (cameraHandler != null && cameraHandler.processImageTask != null)
			android.os.Process.killProcess(android.os.Process.myPid());
	}

	@Override
	protected void onResume() {
		super.onResume();
		cameraHandler.onResume();
	}

	@Override
	protected void onPause() {
		super.onPause();
		cameraHandler.onPause();
	}

	@Override
	protected void onStop() {
		super.onStop();
		savePreferences();
	}

	@Override
	protected void onStart() {
		super.onStart();
	}

	@Override
	protected void onRestart() {
		super.onStart();
	}

	private void loadPreferences() {
		// load selected sketch
		SharedPreferences settings = getPreferences(Context.MODE_PRIVATE);
		String selectedSketchId = settings.getString("selectedSketchId", "");
		if (!selectedSketchId.equals("")) {
			for (Sketch sketch : sketchHandler.sketches) {
				boolean foundSketch = false;
				if (sketch.guid.equals(selectedSketchId)) {
					sketchHandler.selectSketch(sketch);

					// load parameter settings
					String parameterValuesString = settings.getString("parameterValues", "");
					if (!parameterValuesString.equals("")) {
						String[] parameterValues = parameterValuesString.split(",");

						for (int i = 0; i < sketchHandler.selectedSketch.parameters.size(); i++) {
							Parameter parameter = sketchHandler.selectedSketch.parameters.get(i);
							parameter.setValue(Float.parseFloat(parameterValues[i]));
						}
					}

					foundSketch = true;
					break;
				}

				if (!foundSketch && sketchHandler.sketches.size() > 0)
					sketchHandler.selectSketch(sketchHandler.sketches.get(0));
			}

		} else {
			if (sketchHandler.sketches.size() > 0)
				sketchHandler.selectSketch(sketchHandler.sketches.get(0));
		}
	}

	private void savePreferences() {
		if (sketchHandler.selectedSketch != null) {
			SharedPreferences settings = getPreferences(Context.MODE_PRIVATE);
			SharedPreferences.Editor editor = settings.edit();
			editor.putString("selectedSketchId", sketchHandler.selectedSketch.guid);
			String parameterValuesString = "";
			for (Parameter parameter : sketchHandler.selectedSketch.parameters) {
				parameterValuesString += Float.toString(parameter.value) + ",";
			}
			editor.putString("parameterValues", parameterValuesString);
			editor.commit();
		}
	}
}
