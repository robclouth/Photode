package com.robclouth.photode;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import processing.core.PApplet;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public class SketchHandler {
	public static final String INSTALLED_SKETCH_PATH = "/photode/sketches";
	MainActivity main;
	PApplet applet;
	public ArrayList<Sketch> sketches;
	HashMap<View, Sketch> sketchItemMap;
	HashMap<SeekBar, Parameter> paramItemMap;
	public Sketch selectedSketch;
	private View selectedItem;

	public SketchHandler(MainActivity main) {
		this.main = main;
		sketches = new ArrayList<Sketch>();
		sketchItemMap = new HashMap<View, Sketch>();
		paramItemMap = new HashMap<SeekBar, Parameter>();

		// loadSketch("ImageTexturing");
		(main.findViewById(R.id.hasSketches)).setVisibility(View.INVISIBLE);
		(main.findViewById(R.id.noSketches)).setVisibility(View.VISIBLE);
	}

	public void refreshSketchList() {
		if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
			return;

		sketchItemMap.clear();
		sketches.clear();
		LayoutInflater inflater = (LayoutInflater) main.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		LinearLayout sketchList = (LinearLayout) main.findViewById(R.id.sketchList);
		sketchList.removeAllViews();

		File sketchesPath = new File(Environment.getExternalStorageDirectory() + INSTALLED_SKETCH_PATH);
		File[] sketchFolders = sketchesPath.listFiles(new FileFilter() {
			public boolean accept(File file) {
				return file.isDirectory();
			}
		});

		if (sketchFolders != null) {
			for (File sketchFolder : sketchFolders) {
				// check sketch files are valid
				if (!((new File(sketchFolder + "/sketch.def")).exists() && (new File(sketchFolder + "/sketch.jar")).exists()
						&& (new File(sketchFolder + "/icon_1.jpg")).exists() && (new File(sketchFolder + "/icon_2.jpg")).exists()
						&& (new File(sketchFolder + "/icon_3.jpg")).exists() && (new File(sketchFolder + "/icon_4.jpg")).exists() && (new File(
						sketchFolder + "/icon_small.jpg")).exists())) {
					continue;
				}

				// try and parse the sketch def file
				Sketch sketch = new Sketch(main, sketchFolder);
				if (!sketch.parseSketchInfo())
					continue;

				// load sketch icon
				Bitmap smallIcon = BitmapFactory.decodeFile(sketchFolder + "/icon_small.jpg");
				if (smallIcon == null)
					continue;

				sketches.add(sketch);

				// create the sketch list item
				View sketchItem = inflater.inflate(R.layout.sketch_item, sketchList, false);
				((TextView) sketchItem.findViewById(R.id.sketchName)).setText(sketch.name);
				((ImageView) sketchItem.findViewById(R.id.sketchIcon)).setImageBitmap(smallIcon);
				sketchItem.setBackgroundResource(R.drawable.shadow);

				//add it to the view
				sketchList.addView(sketchItem);

				//put it in a map to allow reverse lookup
				sketchItemMap.put(sketchItem, sketch);

				sketch.listItem = sketchItem;

				// setup interaction
				sketchItem.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View item) {
						Sketch sketch = sketchItemMap.get(item);
						selectSketch(sketch);
					}
				});
			}
		}

		if (sketches.size() == 0) {
			(main.findViewById(R.id.hasSketches)).setVisibility(View.INVISIBLE);
			(main.findViewById(R.id.noSketches)).setVisibility(View.VISIBLE);
			selectSketch(null);
		} else {
			(main.findViewById(R.id.hasSketches)).setVisibility(View.VISIBLE);
			(main.findViewById(R.id.noSketches)).setVisibility(View.INVISIBLE);
		}

	}

	public void selectSketch(Sketch sketch) {
		if (sketch == null) {
			(main.findViewById(R.id.noSketchSelectedParams)).setVisibility(View.VISIBLE);
			((TextView) main.findViewById(R.id.selectedSketchNamePreview)).setText("None");
			return;
		} else {
			(main.findViewById(R.id.noSketchSelectedParams)).setVisibility(View.INVISIBLE);
		}

		// try to initialize the sketch
		if (sketch.init()) {
			// unselect previous item
			if (selectedItem != null)
				selectedItem.setBackgroundResource(R.drawable.shadow);
			sketch.listItem.setBackgroundResource(R.drawable.innershadow);

			//update gui
			selectedItem = sketch.listItem;
			((TextView) main.findViewById(R.id.sketchNameParamView)).setText(sketch.name);
			((TextView) main.findViewById(R.id.selectedSketchNamePreview)).setText(sketch.name);
			((TextView) main.findViewById(R.id.selectedSketchName)).setText(sketch.name);
			((TextView) main.findViewById(R.id.selectedSketchAuthor)).setText(sketch.author);
			((TextView) main.findViewById(R.id.selectedSketchDescription)).setText(sketch.description);
			Bitmap icon = BitmapFactory.decodeFile(sketch.sketchFolder + "/icon_1.jpg");
			((ImageView) main.findViewById(R.id.selectedSketchIcon)).setImageBitmap(icon);

			selectedSketch = sketch;
			updateParamList();
		}
	}

	public void updateParamList() {
		if (selectedSketch == null)
			return;

		paramItemMap.clear();

		LayoutInflater inflater = (LayoutInflater) main.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		LinearLayout paramList = (LinearLayout) main.findViewById(R.id.parameterList);
		paramList.removeAllViews();

		for (Parameter parameter : selectedSketch.parameters) {
			LinearLayout parameterItem = (LinearLayout) inflater.inflate(R.layout.parameter_item, paramList, false);
			((TextView) parameterItem.findViewById(R.id.parameterName)).setText(parameter.name + ":");
			((TextView) parameterItem.findViewById(R.id.parameterDescription)).setText(parameter.description);
			SeekBar seekBar = ((SeekBar) parameterItem.findViewById(R.id.parameterSlider));
			seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
				@Override
				public void onStopTrackingTouch(SeekBar seekBar) {

				}

				@Override
				public void onStartTrackingTouch(SeekBar seekBar) {

				}

				@Override
				public void onProgressChanged(SeekBar seekBar, int sliderValue, boolean fromUser) {
					if (fromUser) {
						Parameter parameter = paramItemMap.get(seekBar);
						parameter.setValueFromSlider(sliderValue);
					}
				}
			});

			parameter.slider = seekBar;
			parameter.textView = ((TextView) parameterItem.findViewById(R.id.parameterValue));
			parameter.updateUI();

			paramList.addView(parameterItem);

			paramItemMap.put(seekBar, parameter);
		}
	}

	public static void installSketch(File photodeFile) {
		try {
			installSketch(photodeFile, new FileInputStream(photodeFile));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	public static void installSketch(File photodeFile, InputStream is) {
		File sketchesPath = new File(Environment.getExternalStorageDirectory() + "/photode/sketches");
		File outPath = new File(sketchesPath + "/" + photodeFile.getName().replace(".photode", ""));
		outPath.mkdirs();
		(new File(outPath + "/source")).mkdir();

		ZipInputStream zis = null;
		try {

			zis = new ZipInputStream(new BufferedInputStream(is));
			ZipEntry ze;
			while ((ze = zis.getNextEntry()) != null) {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				byte[] buffer = new byte[1024];
				int count;
				while ((count = zis.read(buffer)) != -1) {
					baos.write(buffer, 0, count);
				}
				String filename = ze.getName();
				byte[] bytes = baos.toByteArray();

				OutputStream output = new BufferedOutputStream(new FileOutputStream(new File(outPath + "/" + filename)), 16 * 1024);
				output.write(bytes);

				output.flush();
				output.close();

			}
		} catch (Exception e) {

		} finally {
			if (zis != null) {
				try {
					zis.close();
				} catch (IOException e) {

				}
			}
		}
	}
}
