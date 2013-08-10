package com.robclouth.photode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Timer;
import java.util.TimerTask;

import android.os.AsyncTask;
import android.util.Log;
import android.widget.TextView;

/*
 * This class is there to hook into the System.out.println messages coming from the Processing sketch so that
 * they can be displayed. It's a bit hacky but it seems to work.
 * 
 */

public class ProgressLogger extends OutputStream {
	MainActivity main;
	private ByteArrayOutputStream bos = new ByteArrayOutputStream();
	String currentMessage;
	public TextView view;
	private boolean isActive = false;
	Timer progressChecker;

	public ProgressLogger(MainActivity main) {
		this.main = main;

		System.setOut(new PrintStream(this));

		view = ((TextView) main.findViewById(R.id.progressText));
	}

	@Override
	public void write(int b) throws IOException {
		if (b == (int) '\n') {
			if (isActive) {
				currentMessage = new String(this.bos.toByteArray());
				this.bos = new ByteArrayOutputStream();
			}
		} else {
			this.bos.write(b);
		}
	}
	
	public void start(){
		isActive = true;
		progressChecker = new Timer();
		progressChecker.schedule(new TimerTask() {
			@Override
			public void run() {
				main.runOnUiThread(new Runnable() {
					public void run() {
						if (view != null)
							view.setText(currentMessage);
					}
				});
			}
		}, 0, 200);
	}
	
	public void stop(){
		isActive = false;
		currentMessage = "Processing...";
		progressChecker.cancel();
	}
}
