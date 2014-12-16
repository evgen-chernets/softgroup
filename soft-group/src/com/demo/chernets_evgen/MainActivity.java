package com.demo.chernets_evgen;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.os.*;
import android.util.Log;
import android.view.View;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import android.content.pm.*;
import android.widget.*;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

public class MainActivity extends Activity implements SeekBar.OnSeekBarChangeListener {
    private final String TAG = "com.demo.chernets_evgen.MainActivity";
    private final int CAPTURE_ERROR = 100;
    private final int CAPTURE_PICTURE = 101;
    private final int OPENCV_ERROR = 102;
    private String photoDirectoryName = "photoTest";
    private boolean overwriteResultFile = false;
    private boolean openCVLoaderFinished = false;
    private boolean openCVLoaded = false;
	private Handler handler;
	private Timer captureTimer;
    private Camera camera;
    private CameraPreview cameraPreview;
    private FrameLayout previewContainer;
    private Button startButton;
    private SeekBar sbDifference, sbCapturePeriod;
    private TextView tvDifference, tvCapturePeriod;
    private byte[] oldImageHashArray;
    private int difference = 10;
    private int capturePeriod = 10;


    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_6, this, mLoaderCallback);
        setContentView(R.layout.main);
        previewContainer = (FrameLayout) findViewById(R.id.camera_preview);
        startButton = (Button)findViewById(R.id.start);
		if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA))
			startButton.setEnabled(false);
        sbCapturePeriod = (SeekBar)findViewById(R.id.sb_capture_period);
        sbCapturePeriod.setProgress(capturePeriod - 10);
        sbCapturePeriod.setOnSeekBarChangeListener(this);
        sbDifference = (SeekBar)findViewById(R.id.sb_similarity_threshold);
        sbDifference.setProgress(difference);
        sbDifference.setOnSeekBarChangeListener(this);
        tvCapturePeriod = (TextView)findViewById(R.id.tv_capture_period);
        tvCapturePeriod.setText(getResources().getString(R.string.capture_period, capturePeriod));
        tvDifference = (TextView)findViewById(R.id.tv_similarity_threshold);
        tvDifference.setText(getResources().getString(R.string.difference, difference));

        handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case CAPTURE_PICTURE :
                        Toast.makeText(MainActivity.this, R.string.toast_take_picture, Toast.LENGTH_SHORT).show();
                        break;
                    case CAPTURE_ERROR :
                        startButton.setText(getResources().getString(R.string.btn_start));
                        Toast.makeText(MainActivity.this, R.string.toast_camera_error, Toast.LENGTH_LONG).show();
                        break;
                    case OPENCV_ERROR :
                        startButton.setText(getResources().getString(R.string.btn_start));
                        captureTimer.cancel();
                        Toast.makeText(MainActivity.this, R.string.toast_opencv_error, Toast.LENGTH_LONG).show();
                        break;
                }
            }
        };

        ((Button)findViewById(R.id.help_button)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle(getResources().getString(R.string.alert_help_title));
                builder.setMessage(getResources().getString(R.string.alert_help_message));
                AlertDialog dialog = builder.create();
                dialog.setCanceledOnTouchOutside(true);
                dialog.show();
            }
        });
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                    Log.i(TAG, "OpenCV loaded successfully");
                    openCVLoaded = true;
                 break;
                default:
                    super.onManagerConnected(status);
                break;
            }
            openCVLoaderFinished = true;
        }
    };

    private Camera.AutoFocusCallback mAutoFocusCallback = new Camera.AutoFocusCallback() {
        @Override public void onAutoFocus(boolean focused, Camera camera) {
            if (focused)
                camera.takePicture(null, null, mPictureCallback);
        }
    };

    private Camera.PictureCallback mPictureCallback = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(final byte[] data, Camera camera) {
            camera.stopPreview();
            byte[] newImageHashArray = calculateHashArray(data);
            if (oldImageHashArray == null) {
                oldImageHashArray = newImageHashArray;
                saveImageToFile(data);
            } else {
                //todo change condition
                int distance = calculateDistance(oldImageHashArray, newImageHashArray);
                if (distance > difference) {
                    oldImageHashArray = newImageHashArray;
                    saveImageToFile(data);
                }
            }

            camera.startPreview();
        }
    };

    private byte[] calculateHashArray(byte[] imageData) {
        byte[] data = null;
        if (openCVLoaded) {
            Mat imageMat = new Mat();
            Utils.bitmapToMat(BitmapFactory.decodeByteArray(imageData, 0, imageData.length), imageMat);
            Imgproc.resize(imageMat, imageMat, new Size(8, 8));
            Imgproc.cvtColor(imageMat, imageMat, Imgproc.COLOR_RGB2GRAY);
            Scalar sc = Core.mean(imageMat);
            Imgproc.threshold(imageMat, imageMat, sc.val[0], 1, Imgproc.THRESH_BINARY);
            data = new byte[(int) (imageMat.total())];
            imageMat.get(0,  0, data);
        } else {
            Message msg = new Message();
            msg.what = OPENCV_ERROR;
            handler.sendMessage(msg);
        }
        return data;
    }

    private int calculateDistance(byte[] oldImageHashArray, byte[] newImageHashArray) {
        int res = 0;
        for (int i = 0; i < oldImageHashArray.length; i++)
            res += (oldImageHashArray[i] + newImageHashArray[i] == 1) ? 1 : 0;
        return res;
    }

    private long convertHashArrayToLong(byte[] data) {
        long res = 0;
        long l;
        for (int i = 0; i < data.length; i++) {
            l = data[i];
            res += l << (data.length - i - 1);
        }
        return res;
    }

    private void saveImageToFile(final byte[] data) {
        File pictureFile = getOutputMediaFile();
        if (pictureFile == null){
            Log.d(TAG, "Error creating media file, check storage permissions.");
            return;
        }
        try {
            FileOutputStream fos = new FileOutputStream(pictureFile);
            fos.write(data);
            fos.close();
            String message = getResources().getString(R.string.toast_picture_saved) + " " + pictureFile.getPath();
            Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
        } catch (FileNotFoundException e) {
            Log.d(TAG, "File not found: " + e.getMessage());
        } catch (IOException e) {
            Log.d(TAG, "Error accessing file: " + e.getMessage());
        }
    }

    private File getOutputMediaFile(){
        File outputDirectory = getOutputDirectory();
        if (outputDirectory != null) {
            if (overwriteResultFile) {
                File mediaFile = new File(outputDirectory + File.separator + "result.jpg");
                return mediaFile;
            } else {
                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                File mediaFile = new File(outputDirectory + File.separator + "IMG_" + timeStamp + ".jpg");
                return mediaFile;
            }
        } else {
            Log.d(TAG, "failed to create file");
            return null;
        }
    }

    private File getOutputDirectory(){
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            File externalStorage = Environment.getExternalStorageDirectory();
            if (externalStorage.exists()) {
                File outputDirectory = new File(externalStorage + File.separator + photoDirectoryName);
                outputDirectory.mkdirs();
                if (outputDirectory.exists() && outputDirectory.isDirectory()) {
                    return outputDirectory;
                } else {
                    Log.d(TAG, "failed to create directory");
                    return null;
                }
            } else {
                Log.d(TAG, "external storage does not exist");
                return null;
            }
        } else {
            Log.d(TAG, "external storage media not mounted");
            return null;
        }
    }

    public void onStartButtonClicked(View v) {
        if (!openCVLoaded) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getResources().getString(R.string.alert_error_title));
            builder.setMessage(getResources().getString(R.string.alert_error_message));
            builder.setPositiveButton(R.string.alert_error_ok_button, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    MainActivity.this.finish();
                }
            });
            builder.create().show();
        }
		if (captureTimer == null) {
            if (camera != null) {
                sbCapturePeriod.setEnabled(false);
                sbDifference.setEnabled(false);
                startButton.setText(getResources().getString(R.string.btn_stop));
                camera.startPreview();
                captureTimer = new Timer();
                captureTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        Message msg = new Message();
                        msg.what = CAPTURE_PICTURE;
                        handler.sendMessage(msg);
                        camera.autoFocus(mAutoFocusCallback);
                    }
                }, capturePeriod * 1000, capturePeriod * 1000);
            } else {
                Message msg = new Message();
                msg.what = CAPTURE_ERROR;
                handler.sendMessage(msg);
            }
		} else {
			captureTimer.cancel();
			captureTimer = null;
            camera.stopPreview();
            startButton.setText(getResources().getString(R.string.btn_start));
            sbDifference.setEnabled(true);
            sbCapturePeriod.setEnabled(true);
			Toast.makeText(MainActivity.this, R.string.toast_stop_taking, Toast.LENGTH_LONG).show();
		}
    }

    private Camera getCamera() {
        Camera cam = null;
        try {
            cam = Camera.open();
            cam.setDisplayOrientation(90);
            Camera.Parameters params = cam.getParameters();
            params.setJpegQuality(40);
        } catch (Exception e){
            Log.d(TAG, "in getCamera(): " + e.getMessage());
            Message msg = new Message();
            msg.what = CAPTURE_ERROR;
            handler.sendMessage(msg);
            if (cam != null)
                cam.release();
        }
        return cam;
    }

    @Override
    protected void onResume() {
        super.onResume();
        camera = getCamera();
        if (camera != null) {
            cameraPreview = new CameraPreview(this, camera);
            previewContainer.addView(cameraPreview);
        }

    }

    @Override
    protected void onPause() {
        if (camera != null) {
            camera.stopPreview();
            camera.release();
        }
        previewContainer.removeAllViews();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (captureTimer != null) {
            captureTimer.cancel();
            captureTimer = null;
        }
        if (camera != null) {
            camera.release();
            camera = null;
        }
        super.onDestroy();
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        switch (seekBar.getId()) {
            case R.id.sb_capture_period:
                capturePeriod = progress + 10;
                tvCapturePeriod.setText(getResources().getString(R.string.capture_period, capturePeriod));
                break;
            case R.id.sb_similarity_threshold:
                difference = progress;
                tvDifference.setText(getResources().getString(R.string.difference, difference));
                break;
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }
}
