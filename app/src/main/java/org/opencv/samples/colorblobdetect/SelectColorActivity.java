package org.opencv.samples.colorblobdetect;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.app.Activity;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.samples.colorblobdetect.R;

import java.util.List;

public class SelectColorActivity extends Activity implements View.OnTouchListener, CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String  TAG              = "OCVSample::Activity";

    private boolean              mIsColorSelected = false;
    private Mat mRgba;
    private Scalar mBlobColorRgba;
    private Scalar               mBlobColorHsv;

    // private Scalar               mBlackColorHsv;

    private ColorBlobDetector    mDetector;
    private ColorBlobDetector    ppDetector;

    // private ColorBlobDetector    mBlackDetector;
    private Utility              mUtility;

    private Mat                  mSpectrum;
    private Size SPECTRUM_SIZE;
    private Scalar               CONTOUR_COLOR;

    private int previousState = -1;
    private TextToSpeech tts;

    private boolean isMain;

    private CameraBridgeViewBase mOpenCvCameraView;
    Button done;
    SharedPreferences sp;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                    mOpenCvCameraView.setOnTouchListener(SelectColorActivity.this);
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    public SelectColorActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);

        Intent thisIntent = getIntent();
        isMain = thisIntent.getBooleanExtra("isMain", true);
        Log.i(TAG, "isMain = " + isMain);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_select_color);

        sp = PreferenceManager.getDefaultSharedPreferences(this);

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.select_color_activity);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);

        done = (Button)findViewById(R.id.colorDone);
        done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(SelectColorActivity.this, StartActivity.class);
                startActivity(i);
                finish();
            }
        });
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mDetector = new ColorBlobDetector();
        ppDetector = new ColorBlobDetector();
        // mBlackDetector = new ColorBlobDetector();
        mSpectrum = new Mat();
        mBlobColorRgba = new Scalar(255);
        mBlobColorHsv = new Scalar(255);
        // mBlackColorHsv = new Scalar(0,0,0,255);
        SPECTRUM_SIZE = new Size(200, 64);
        CONTOUR_COLOR = new Scalar(0,255,0,255);
    }

    @Override
    public void onCameraViewStopped() {
        mRgba.release();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();

        if (mIsColorSelected) {
            mDetector.process(mRgba);
            // mBlackDetector.process(mRgba);
            List<MatOfPoint> contours = mDetector.getContours();
            // List<MatOfPoint> blackContours = mBlackDetector.getContours();
            // Log.e(TAG, "Contours count: " + contours.size());
            Imgproc.drawContours(mRgba, contours, -1, CONTOUR_COLOR);

            Mat colorLabel = mRgba.submat(4, 68, 4, 68);
            colorLabel.setTo(mBlobColorRgba);
        }

        return mRgba;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        int cols = mRgba.cols();
        int rows = mRgba.rows();

        int xOffset = (mOpenCvCameraView.getWidth() - cols) / 2;
        int yOffset = (mOpenCvCameraView.getHeight() - rows) / 2;

        int x = (int)event.getX() - xOffset;
        int y = (int)event.getY() - yOffset;

        Log.i(TAG, "Touch image coordinates: (" + x + ", " + y + ")");

        if ((x < 0) || (y < 0) || (x > cols) || (y > rows)) return false;

        Rect touchedRect = new Rect();

        touchedRect.x = (x>4) ? x-4 : 0;
        touchedRect.y = (y>4) ? y-4 : 0;

        touchedRect.width = (x+4 < cols) ? x + 4 - touchedRect.x : cols - touchedRect.x;
        touchedRect.height = (y+4 < rows) ? y + 4 - touchedRect.y : rows - touchedRect.y;

        Mat touchedRegionRgba = mRgba.submat(touchedRect);

        Mat touchedRegionHsv = new Mat();
        Imgproc.cvtColor(touchedRegionRgba, touchedRegionHsv, Imgproc.COLOR_RGB2HSV_FULL);

        // Calculate average color of touched region
        mBlobColorHsv = Core.sumElems(touchedRegionHsv);
        int pointCount = touchedRect.width*touchedRect.height;
        for (int i = 0; i < mBlobColorHsv.val.length; i++)
            mBlobColorHsv.val[i] /= pointCount;

        mBlobColorRgba = converScalarHsv2Rgba(mBlobColorHsv);

        Log.i(TAG, "Touched rgba color: (" + mBlobColorRgba.val[0] + ", " + mBlobColorRgba.val[1] +
                ", " + mBlobColorRgba.val[2] + ", " + mBlobColorRgba.val[3] + ")");


        JSONArray hsv = new JSONArray();

        for (int i = 0; i < mBlobColorHsv.val.length; i++){
            try {
                hsv.put(i, mBlobColorHsv.val[i]);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        JSONArray rgba = new JSONArray();
        try {
            rgba.put(0, mBlobColorRgba.val[0]);
            rgba.put(1, mBlobColorRgba.val[1]);
            rgba.put(2, mBlobColorRgba.val[2]);
            rgba.put(3, mBlobColorRgba.val[3]);

        } catch (JSONException e) {
            e.printStackTrace();
        }

        // see if main color or play/pause
//        Intent thisIntent = getIntent();
//        boolean isMain = thisIntent.getBooleanExtra("isMain", true);
        if(isMain) {
            Log.i(TAG, "isMain = " + isMain +". Storing data in non-pp");
            sp.edit().putString("touched_color_hsv", hsv.toString()).apply();
            sp.edit().putString("touched_color_rgba", rgba.toString()).apply();
        }
        else {
            Log.i(TAG, "isMain = " + isMain +". Storing data in pp");
            sp.edit().putString("touched_color_hsv_pp", hsv.toString()).apply();
            sp.edit().putString("touched_color_rgba_pp", rgba.toString()).apply();
        }

        mDetector.setHsvColor(mBlobColorHsv);

        Imgproc.resize(mDetector.getSpectrum(), mSpectrum, SPECTRUM_SIZE);

        // mBlackDetector.setHsvColor(mBlackColorHsv);

        /*
        Log.i("BLACK", mBlackDetector.getmLowerBound().val[0]+" "
                +mBlackDetector.getmLowerBound().val[1]+" "
                +mBlackDetector.getmLowerBound().val[2]+" "
                +mBlackDetector.getmLowerBound().val[3]);
        Log.i("BLACK", mBlackDetector.getmUpperBound().val[0]+" "
                +mBlackDetector.getmUpperBound().val[1]+" "
                +mBlackDetector.getmUpperBound().val[2]+" "
                +mBlackDetector.getmUpperBound().val[3]);
        */

        // Imgproc.resize(mBlackDetector.getSpectrum(), mSpectrum, SPECTRUM_SIZE);

        mIsColorSelected = true;

        touchedRegionRgba.release();
        touchedRegionHsv.release();

        return false; // don't need subsequent touch events
    }

    private Scalar converScalarHsv2Rgba(Scalar hsvColor) {
        Mat pointMatRgba = new Mat();
        Mat pointMatHsv = new Mat(1, 1, CvType.CV_8UC3, hsvColor);
        Imgproc.cvtColor(pointMatHsv, pointMatRgba, Imgproc.COLOR_HSV2RGB_FULL, 4);

        return new Scalar(pointMatRgba.get(0, 0));
    }

    @Override
    public void onBackPressed()
    {
        Intent intent = new Intent(this, StartActivity.class);
        startActivity(intent);
        finish();
//        super.onBackPressed();  // optional depending on your needs

    }



}
