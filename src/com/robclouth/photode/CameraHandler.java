package com.robclouth.photode;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import processing.core.PGraphics;
import processing.core.PGraphicsAndroid2D;
import processing.core.PImage;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.Size;
import android.hardware.SensorManager;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class CameraHandler {
	public static final int pictureSize = 1024;
	MainActivity main;
	private CameraPreview mPreview;
	Camera mCamera;
	int numberOfCameras;
	int cameraCurrentlyLocked;
	private boolean isTakingPicture = false;
	int defaultCameraId;

	ProcessImageTask processImageTask;
	

	private OrientationEventListener mOrientationEventListener;
	private int mOrientation = -1;
	private int pictureOrientation = -1;

	private TextView progressTextView;
	private ImageView resultView;
	Bitmap imageResult;

	private static final int ORIENTATION_PORTRAIT_NORMAL = 1;
	private static final int ORIENTATION_PORTRAIT_INVERTED = 2;
	private static final int ORIENTATION_LANDSCAPE_NORMAL = 3;
	private static final int ORIENTATION_LANDSCAPE_INVERTED = 4;

	public CameraHandler(final MainActivity main) {
		this.main = main;
		
		// create screen touch listener
		((FrameLayout) main.findViewById(R.id.cameraFrame)).setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent e) {
				if (main.sketchHandler.selectedSketch != null) {
					if (!isTakingPicture) {
						isTakingPicture = true;
						mCamera.autoFocus(new Camera.AutoFocusCallback() {
							@Override
							public void onAutoFocus(boolean success, Camera camera) {
								if (success) {
									//show the 'processing image' view
									main.viewAnimator.setDisplayedChild(4);
									progressTextView.setText("Processing...");
									mCamera.takePicture(null, null, mPicture);
								} else
									isTakingPicture = false;
							}
						});

					}
				} else {
					Toast.makeText(main, "No sketch selected.", Toast.LENGTH_LONG).show();
				}

				return false;
			}
		});

		mPreview = new CameraPreview(main);
		((FrameLayout) main.findViewById(R.id.cameraFrame)).addView(mPreview);
		// ((RelativeLayout) findViewById(R.id.cameraOverlay)).bringToFront();

		// Find the total number of cameras available
		numberOfCameras = Camera.getNumberOfCameras();

		// Find the ID of the default camera
		CameraInfo cameraInfo = new CameraInfo();
		for (int i = 0; i < numberOfCameras; i++) {
			Camera.getCameraInfo(i, cameraInfo);
			if (cameraInfo.facing == CameraInfo.CAMERA_FACING_BACK) {
				defaultCameraId = i;
			}
		}

		progressTextView = ((TextView) main.findViewById(R.id.progressText));

		resultView = ((ImageView) main.findViewById(R.id.resultView));
		//on result view close...
		((ImageView) main.findViewById(R.id.resultCloseButton)).setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent e) {
				//change view back to camera view
				main.viewAnimator.setDisplayedChild(0);
				
				//set the result image to null then recycle everything to conserve memory
				resultView.setImageBitmap(null);
				Log.d("photode", "destroying outBitmap " + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()));
				if (imageResult != null)
					imageResult.recycle();
				imageResult = null;
				
				// just in case
				System.gc();

				return true;
			}
		});
	}

	private PictureCallback mPicture = new PictureCallback() {
		@Override
		public void onPictureTaken(byte[] data, Camera camera) {
			// start the logger to get the println messages
			main.logger.start();
			
			//create and start the AyncTask to process the image
			processImageTask = new ProcessImageTask();
			processImageTask.execute(data);

			isTakingPicture = false;
			mCamera.startPreview();
		}
	};

	@SuppressLint("SimpleDateFormat")
	private File getOutputMediaFile() {
		if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {

			File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "photode");
			if (!mediaStorageDir.exists()) {
				if (!mediaStorageDir.mkdirs()) {
					Log.d("photode", "Failed to create directory.");
					return null;
				}
			}

			//save image with filename: <selected sketch name>_<time stamp>.jpg 
			String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
			File mediaFile = new File(mediaStorageDir.getPath() + File.separator + main.sketchHandler.selectedSketch.name + "_" + timeStamp + ".jpg");

			return mediaFile;
		} else {
			Toast.makeText(main, "Save failed. External storage is unmounted.", Toast.LENGTH_LONG).show();
			return null;
		}
	}

	private class ProcessImageTask extends AsyncTask<byte[], String, Bitmap> {
		@Override
		protected Bitmap doInBackground(byte[]... params) {
			// change thread priority to supposedly speed up the processing a bit (but the difference isn't much it seems)
			android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND + android.os.Process.THREAD_PRIORITY_MORE_FAVORABLE);
			byte[] data = params[0];
			Options opts = new Options();
			opts.inPurgeable = true;

			Log.d("photode", "creating inBitmap " + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()));
			
			// decode the jpeg byte array from the camera
			Bitmap inBitmap = BitmapFactory.decodeByteArray(data, 0, data.length, opts);

			//scale and crop to make it a square the size of pictureSize 
			Matrix matrix = new Matrix();
			Bitmap scaledBitmap;
			if (inBitmap.getWidth() >= inBitmap.getHeight()) {
				matrix.postScale(pictureSize / (float) inBitmap.getHeight(), pictureSize / (float) inBitmap.getHeight());
				scaledBitmap = Bitmap.createBitmap(inBitmap, inBitmap.getWidth() / 2 - inBitmap.getHeight() / 2, 0, inBitmap.getHeight(),
						inBitmap.getHeight(), matrix, true);
			} else {
				matrix.postScale(pictureSize / (float) inBitmap.getHeight(), pictureSize / (float) inBitmap.getHeight());
				scaledBitmap = Bitmap.createBitmap(inBitmap, 0, inBitmap.getHeight() / 2 - inBitmap.getWidth() / 2, inBitmap.getWidth(),
						inBitmap.getWidth(), matrix, true);
			}
			
			// dispose of the unscaled bitmap
			inBitmap.recycle();
			inBitmap = scaledBitmap;
			Log.d("photode", "destroying inBitmap " + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()));
			System.gc();

			Log.d("photode", "creating PImage from scaledBitmap " + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()));
			PImage image = new PImage(scaledBitmap);
			image.loadPixels();

			//create the PGraphics canvas
			Log.d("photode", "creating PGraphics " + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()));
			PGraphics canvas = new PGraphicsAndroid2D();
			canvas.setPrimary(false);
			canvas.setSize(image.width, image.height);
			canvas.beginDraw();
			canvas.background(0);
			canvas.endDraw();

			// do the actual processing
			Log.d("photode", "processing  " + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()));
			canvas = main.sketchHandler.selectedSketch.processBitmap(image, canvas);
			
			// processing has finished so stop the logger
			main.logger.stop();

			if (canvas == null) {
				return null;
			}
			
			//processing is finished, destroy the original image data
			Log.d("photode", "destroying PImage " + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()));
			image = null;
			System.gc();

			publishProgress("Saving...");

			File pictureFile = getOutputMediaFile();
			if (pictureFile == null) {
				Log.d("CameraHandler", "Error creating media file, check storage permissions.");
				return null;
			}

			Bitmap outBitmap = null;

			// save to sd card
			try {
				Log.d("photode", "creating outBitmap " + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()));
				OutputStream output = new BufferedOutputStream(new FileOutputStream(pictureFile), 16 * 1024);
				outBitmap = Bitmap.createBitmap(canvas.pixels, canvas.width, canvas.height, Config.ARGB_8888);
				boolean success = outBitmap.compress(CompressFormat.JPEG, 100, output);

				output.flush();
				output.close();

				Log.d("photode", "destroying PGraphics " + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()));
				canvas = null;
				System.gc();

				if (!success) {
					Toast.makeText(main, "Save failed.", Toast.LENGTH_LONG).show();
					return outBitmap;
				}

				// set rotation
				ExifInterface exif = new ExifInterface(pictureFile.getAbsolutePath());
				switch (pictureOrientation) {
				case ORIENTATION_PORTRAIT_NORMAL:
					exif.setAttribute(ExifInterface.TAG_ORIENTATION, "" + ExifInterface.ORIENTATION_ROTATE_90);
					break;
				case ORIENTATION_LANDSCAPE_NORMAL:
					exif.setAttribute(ExifInterface.TAG_ORIENTATION, "" + ExifInterface.ORIENTATION_NORMAL);
					break;
				case ORIENTATION_PORTRAIT_INVERTED:
					exif.setAttribute(ExifInterface.TAG_ORIENTATION, "" + ExifInterface.ORIENTATION_ROTATE_270);
					break;
				case ORIENTATION_LANDSCAPE_INVERTED:
					exif.setAttribute(ExifInterface.TAG_ORIENTATION, "" + ExifInterface.ORIENTATION_ROTATE_180);
					break;
				}

				exif.saveAttributes();

				Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
				Uri contentUri = Uri.fromFile(pictureFile);
				mediaScanIntent.setData(contentUri);
				main.sendBroadcast(mediaScanIntent);

			} catch (FileNotFoundException e) {
				Log.d("CameraHandler", "File not found: " + e.getMessage());
			} catch (IOException e) {
				Log.d("CameraHandler", "Error accessing file: " + e.getMessage());
			}

			return outBitmap;
		}

		@Override
		protected void onProgressUpdate(String... progressText) {
			progressTextView.setText(progressText[0]);
		}

		@Override
		protected void onPostExecute(Bitmap result) {
			imageResult = result;
			processImageTask = null;

			if (result == null) {
				Toast.makeText(main, "Processing failed.", Toast.LENGTH_LONG).show();
				main.viewAnimator.setDisplayedChild(0);
			} else {
				resultView.setImageBitmap(result);
				main.viewAnimator.setDisplayedChild(1);
			}
		}
	}

	public void onResume() {
		if (imageResult != null) {
			main.viewAnimator.setDisplayedChild(1);
			imageResult = null;
			System.gc();
		}

		// Open the default i.e. the first rear facing camera.
		mCamera = Camera.open();
		cameraCurrentlyLocked = defaultCameraId;
		mPreview.setCamera(mCamera);
		Camera.Parameters params = mCamera.getParameters();
		Size size = findBestPictureSize(params);
		params.setPictureSize(size.width, size.height);
		mCamera.setParameters(params);

		if (mOrientationEventListener == null) {
			mOrientationEventListener = new OrientationEventListener(main, SensorManager.SENSOR_DELAY_NORMAL) {

				@Override
				public void onOrientationChanged(int orientation) {

					// determine our orientation based on sensor response
					int lastOrientation = mOrientation;

					if (orientation >= 315 || orientation < 45) {
						if (mOrientation != ORIENTATION_PORTRAIT_NORMAL) {
							mOrientation = ORIENTATION_PORTRAIT_NORMAL;
						}
					} else if (orientation < 315 && orientation >= 225) {
						if (mOrientation != ORIENTATION_LANDSCAPE_NORMAL) {
							mOrientation = ORIENTATION_LANDSCAPE_NORMAL;
						}
					} else if (orientation < 225 && orientation >= 135) {
						if (mOrientation != ORIENTATION_PORTRAIT_INVERTED) {
							mOrientation = ORIENTATION_PORTRAIT_INVERTED;
						}
					} else { // orientation <135 && orientation > 45
						if (mOrientation != ORIENTATION_LANDSCAPE_INVERTED) {
							mOrientation = ORIENTATION_LANDSCAPE_INVERTED;
						}
					}
				}
			};
		}
		if (mOrientationEventListener.canDetectOrientation()) {
			mOrientationEventListener.enable();
		}
	}

	private Size findBestPictureSize(Parameters params) {
		List<Size> sizes = params.getSupportedPictureSizes();

		float minArea = Float.MAX_VALUE;
		Size idealSize = null;
		for (Size size : sizes) {
			if (size.width > pictureSize && size.height > pictureSize) {
				float area = size.width * size.height;
				if (area < minArea) {
					minArea = area;
					idealSize = size;
				}
			}
		}

		return idealSize;
	}

	public void onPause() {
		if (mCamera != null) {
			mPreview.setCamera(null);
			mCamera.release();
			mCamera = null;
		}

		mOrientationEventListener.disable();
	}

	class CameraPreview extends ViewGroup implements SurfaceHolder.Callback {
		private final String TAG = "CameraPreview";

		SurfaceView mSurfaceView;
		SurfaceHolder mHolder;
		Size mPreviewSize;
		List<Size> mSupportedPreviewSizes;
		Camera mCamera;

		CameraPreview(Context context) {
			super(context);

			mSurfaceView = new SurfaceView(context);
			addView(mSurfaceView);

			mHolder = mSurfaceView.getHolder();
			mHolder.addCallback(this);
			mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		}

		public void setCamera(Camera camera) {
			mCamera = camera;
			if (mCamera != null) {

				mSupportedPreviewSizes = mCamera.getParameters().getSupportedPreviewSizes();
				requestLayout();
			}
		}

		@Override
		protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
			final int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
			final int height = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
			setMeasuredDimension(width, height);

			if (mSupportedPreviewSizes != null) {
				mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes, width, height);
			}
		}

		@Override
		protected void onLayout(boolean changed, int l, int t, int r, int b) {
			if (changed && getChildCount() > 0) {
				final View child = getChildAt(0);

				final int width = r - l;
				final int height = b - t;

				int previewWidth = width;
				int previewHeight = height;
				if (mPreviewSize != null) {
					previewWidth = mPreviewSize.width;
					previewHeight = mPreviewSize.height;
				}

				// Center the child SurfaceView within the parent.
				if (width * previewHeight > height * previewWidth) {
					final int scaledChildWidth = previewWidth * height / previewHeight;
					child.layout((width - scaledChildWidth) / 2, 0, (width + scaledChildWidth) / 2, height);
				} else {
					final int scaledChildHeight = previewHeight * width / previewWidth;
					child.layout(0, (height - scaledChildHeight) / 2, width, (height + scaledChildHeight) / 2);
				}
			}
		}

		public void surfaceCreated(SurfaceHolder holder) {
			try {
				if (mCamera != null) {
					mCamera.setPreviewDisplay(holder);
				}
			} catch (IOException exception) {
				Log.e(TAG, "IOException caused by setPreviewDisplay()", exception);
			}
		}

		public void surfaceDestroyed(SurfaceHolder holder) {
			if (mCamera != null) {
				mCamera.stopPreview();
			}
		}

		private Size getOptimalPreviewSize(List<Size> sizes, int w, int h) {

			final double ASPECT_TOLERANCE = 0.1;
			double targetRatio = (double) w / h;
			if (sizes == null)
				return null;

			Size optimalSize = null;
			double minDiff = Double.MAX_VALUE;

			int targetHeight = h;

			for (Size size : sizes) {
				double ratio = (double) size.width / size.height;
				if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE)
					continue;
				if (Math.abs(size.height - targetHeight) < minDiff) {
					optimalSize = size;
					minDiff = Math.abs(size.height - targetHeight);
				}
			}

			if (optimalSize == null) {
				minDiff = Double.MAX_VALUE;
				for (Size size : sizes) {
					if (Math.abs(size.height - targetHeight) < minDiff) {
						optimalSize = size;
						minDiff = Math.abs(size.height - targetHeight);
					}
				}
			}
			return optimalSize;
		}

		public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
			if (mCamera == null)
				return;
			Camera.Parameters parameters = mCamera.getParameters();
			parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);

			requestLayout();

			// mCamera.setDisplayOrientation(90);

			mCamera.setParameters(parameters);
			mCamera.startPreview();
		}
	}
}
