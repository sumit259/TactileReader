package org.opencv.samples.colorblobdetect;

/**
 * Created by Nikhil on 4/22/2016.
 */

import android.content.Context;
import android.content.res.AssetManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Environment;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Display;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;
import org.opencv.utils.Converters;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.zip.ZipEntry;

public class Utility {
    static Context cont;

    /*
        Corner[0]           Corner[1]
            *------------------*
            |                  |
            |                  |
            |                  |
            |                  |
            *------------------*
        Corner[3]           Corner[2]
     */
    static Point[] Corners;
    static List<String> titles;
    static List<String> descriptions;
    static List<List<String>> descriptionStatements;
    static List<Integer> lastLocation;
    static List<Integer> lastLocationAudio;
    static List<List<Point>> regionPoints;
    static String audioFormat = ".wav";
    public static int orientation;
    public static MediaPlayer mp = new MediaPlayer();

//    static String envpath = Environment.getDataDirectory().getPath() + File.separator + "Tactile Reader";
    static String envpath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + "/Tactile Reader";



    public Utility(Context currCont) {
        this.cont = currCont;
        Corners = new Point[4];
        titles = new ArrayList<String>();
        descriptions = new ArrayList<String>();
        regionPoints = new ArrayList<List<Point>>();
        descriptionStatements = new ArrayList<List<String>>();
        lastLocation = new ArrayList<Integer>();
        lastLocationAudio = new ArrayList<Integer>();
        mp = new MediaPlayer();
    }

    public void changeLastLocation(int polygon, int location) {
        lastLocation.set(polygon, location);
    }
    public static void changeLastLocationAudio(int polygon, int position) {
        lastLocationAudio.set(polygon, position);
    }
    private static Point getPoint(String line) {
        line = line.trim();
        StringTokenizer st = new StringTokenizer(line);
        float x = Float.parseFloat(st.nextToken());
        float y = Float.parseFloat(st.nextToken());
        Point P = new Point(x, y);

        return P;
    }

    /*
        Coordinate axis of file:
            -------------- X
            |
            |
            |
            |
            | Y
     */

    // Old context format parser, without audio files
    public static void parseFile2(String filename) {
        try {
//            String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + "/Tactile Reader";
            String path = Environment.getDataDirectory() + File.separator + "Tactile Reader";
            File file = new File(path, filename);

            Log.wtf("MTP", "parsing: " + path + File.separator + filename);

            BufferedReader br = new BufferedReader(new FileReader(file));

            // Skip first line
            String line = br.readLine();
            // Fill corners
            int t = 0;
            while (t < 4 && (line = br.readLine()) != null) {
                Log.i("Line", line + '\n');
                Corners[t] = getPoint(line);
                t++;
            }
            double xOffset = Corners[0].x;
            double yOffset = Corners[0].y;

            for (int i = 0; i < 4; i++) {
                Corners[i].x -= xOffset;
                Corners[i].y -= yOffset;
            }

            List<Point> contour = new ArrayList<Point>();
            boolean firstTime = true;
            // Skip the first empty line
            while ((line = br.readLine()) != null) {
                if (line.equals("=")) {
                    line = br.readLine();
                    titles.add(line.trim());
                    Log.wtf("Title", titles.get(titles.size() - 1));
                    Log.wtf("Title", titles.get(titles.size() - 1));

                    if (!firstTime) {
                        regionPoints.add(contour);
                        // Mat m = Converters.vector_Point_to_Mat(contour);
                        // statesContours.add(new MatOfPoint2f(m));
                        contour = new ArrayList<Point>();
                    }
                    firstTime = false;
                } else {
                    Point gP = getPoint(line);
                    gP.x -= xOffset;
                    gP.y -= yOffset;
                    contour.add(gP);
                }
            }
            regionPoints.add(contour);
            br.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.wtf("MTP", "error in parsing");
        }
    }

    public static void parseFile(String filename) {
        try {
//            envpath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + "/Tactile Reader";

            File file = new File(envpath + File.separator + filename, filename + ".txt");

            Log.wtf("MTP", "parsing: " + envpath + "/" + filename + "/" + filename + ".txt");

            BufferedReader br = new BufferedReader(new FileReader(file));

            // Skip first line
            String line = br.readLine();
            // Fill corners
            int t = 0;
            while (t < 4 && (line = br.readLine()) != null) {
                Log.i("Line", line + '\n');
                Corners[t] = getPoint(line);
                t++;
            }
            double xOffset = Corners[0].x;
            double yOffset = Corners[0].y;

            for (int i = 0; i < 4; i++) {
                Corners[i].x -= xOffset;
                Corners[i].y -= yOffset;
            }
            // Skip line = "="
            line = br.readLine();
            while ((line = br.readLine()) != null) {
                titles.add(line.trim());
                line = br.readLine();
                if (line.startsWith("$AUDIO$")) {
                    descriptions.add(line.trim());
                    line = br.readLine();
                } else {
                    String desc = "";
                    // Skip line = "$TEXT$"
                    line = br.readLine();
                    while (!line.equals("=")) {
                        desc += line;
                        line = br.readLine();
                    }
                    descriptions.add(desc);
                }
                // Skip line = "="
                line = br.readLine();
                List<Point> contour = new ArrayList<Point>();
                while (!line.equals("=")) {
                    Point gP = getPoint(line);
                    gP.x -= xOffset;
                    gP.y -= yOffset;
                    contour.add(gP);
                    line = br.readLine();
                }
                regionPoints.add(contour);
            }
            br.close();

            // break text into lines
            BreakIterator breakIterator = BreakIterator.getSentenceInstance(Locale.US);
            int idx = 0, start;

            for(String desc : descriptions) {
                breakIterator.setText(desc);
                start = breakIterator.first();
                List<String> statements = new ArrayList<>();
                for(int end = breakIterator.next(); end != BreakIterator.DONE; start = end, end = breakIterator.next()) {
                    statements.add(desc.substring(start, end));
                }
                descriptionStatements.add(idx, statements);
                idx++;
                lastLocation.add(0);
                lastLocationAudio.add(0);
            }

        } catch (
                IOException e
                ) {
            e.printStackTrace();
            Log.wtf("MTP", "error in parsing");
        }

    }

    public static boolean isPulse() {
        return (ColorBlobDetectionActivity.pulseState == 2);
    }

    public static boolean isSpeaking() {
        Log.i("MP", "MediaPlayer Status: " + mp.isPlaying());
        return mp.isPlaying();
    }
    public static void playAudio(String filePath, final String fileName, final int lastLocation) {
        Context appContext = cont;
        mp = new MediaPlayer();
        try {
            //File mediaFile_mp4_android = new File(filePath + File.separator + fileName + audioFormat);
            //String filePath_mp4_android = String.valueOf(mediaFile_mp4_android);
            //File file_mp4_android = new File(filePath_mp4_android);
            //Uri contentUri = Uri.fromFile(file_mp4_android);
            //mp.setDataSource(String.valueOf(contentUri));
            mp.setDataSource(filePath + File.separator + fileName + audioFormat);
            mp.prepareAsync();
        } catch (IOException e) {
            Log.i("MTP", "Audio File cannot be played");
            e.printStackTrace();
        }
        //mp3 will be started after completion of preparing...
        mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            final int ll = lastLocation;
            @Override
            public void onPrepared(MediaPlayer player) {
                mp.seekTo(ll);
                Log.i("Play_Pause Audio", "Starting audio from location: " + ll);
                mp.start();
            }
        });
        mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            final String idx = fileName.substring(fileName.length()-1, fileName.length());
            final Integer polygon = Integer.parseInt(idx);
            @Override
            public void onCompletion(MediaPlayer mp) {
                changeLastLocationAudio(polygon, 0);
            }
        });
        // mp.start();
    }
    public static void playAudio1(String filePath, String fileName, int lastLocation) {
        Context appContext = cont;
        try {
            //File mediaFile_mp4_android = new File(filePath + File.separator + fileName + audioFormat);
            //String filePath_mp4_android = String.valueOf(mediaFile_mp4_android);
            //File file_mp4_android = new File(filePath_mp4_android);
            //Uri contentUri = Uri.fromFile(file_mp4_android);
            //mp.setDataSource(String.valueOf(contentUri));
            mp.setDataSource(filePath + File.separator + fileName + audioFormat);
            mp.prepareAsync();
        } catch (IOException e) {
            Log.i("MTP", "Audio File cannot be played");
            e.printStackTrace();
        }
        mp.seekTo(lastLocation);
        Log.i("Play_Pause Audio", "Starting audio from location: " + lastLocation);
        mp.start();
    }
    public static void pauseAudio(int polygon) {
        if(mp.isPlaying()) {
            int location = mp.getCurrentPosition();
            changeLastLocationAudio(polygon, location);
            Log.i("Play_Pause Audio", "Pausing audio of polygon: " + polygon + " at location = " + location);
            mp.stop();
        }
    }
    public static void stopAudio() {
        if(mp.isPlaying()) {
            Log.i("Play_Pause Audio", "Audio stopped.");
            mp.stop();
        }
    }
    public static void fastForwardAudio(int duration) {
        if(mp.isPlaying()) {
            Log.i("FAst_Forwarding", "Fast forwarding " + " milliseconds");
            int location = mp.getCurrentPosition();
            if(location + duration < mp.getDuration()) {
                location = location + duration;
                mp.pause();
                mp.seekTo(location);
                mp.start();
            }
        }
    }

    Comparator<Point> compY = new Comparator<Point>() {
        @Override
        public int compare(Point lhs, Point rhs) {
            return (int) (lhs.y - rhs.y);
        }
    };

    Comparator<Point> compX = new Comparator<Point>() {
        @Override
        public int compare(Point lhs, Point rhs) {
            return (int) (lhs.x - rhs.x);
        }
    };

    public Point[] getCentroid(List<MatOfPoint> Contour, Comparator comp) {
        if (Contour.size() == 2) {
            return getCentroid2(Contour, comp);
        } else {
            return getCentroid3(Contour, comp);
        }
    }

    public Point[] getCentroid2(List<MatOfPoint> Contour, Comparator comp) {
        Point[] centroids = new Point[Contour.size()];
        for (int i = 0; i < Contour.size(); i++) {
            Moments p = Imgproc.moments(Contour.get(i), false);
            int cX = (int) (p.get_m10() / p.get_m00());
            int cY = (int) (p.get_m01() / p.get_m00());
            //Imgproc.circle(mRgba, new Point(cX, cY), 10, CONTOUR_COLOR);
            centroids[i] = new Point(cX, cY);
        }
        Arrays.sort(centroids, comp);

        return centroids;
    }

    public Point[] getCentroid3(List<MatOfPoint> Contour, Comparator comp) {
        Point[] centroids = new Point[Contour.size()];
        for (int i = 0; i < Contour.size(); i++) {
            Moments p = Imgproc.moments(Contour.get(i), false);
            int cX = (int) (p.get_m10() / p.get_m00());
            int cY = (int) (p.get_m01() / p.get_m00());
            //Imgproc.circle(mRgba, new Point(cX, cY), 10, CONTOUR_COLOR);
            centroids[i] = new Point(cX, cY);
        }
        Arrays.sort(centroids, comp);

        if (getOrientation() == 1 || getOrientation() == 4) {
            centroids[0].x -= centroids[2].x;
            centroids[0].y -= centroids[2].y;
            centroids[1].x -= centroids[2].x;
            centroids[1].y -= centroids[2].y;
            centroids[2].x -= centroids[2].x;
            centroids[2].y -= centroids[2].y;
        } else {
            centroids[1].x -= centroids[0].x;
            centroids[1].y -= centroids[0].y;
            centroids[2].x -= centroids[0].x;
            centroids[2].y -= centroids[0].y;
            centroids[0].x -= centroids[0].x;
            centroids[0].y -= centroids[0].y;
        }

        return centroids;
    }

    public Scalar convertScalarHsv2Rgba(Scalar hsvColor) {
        Mat pointMatRgba = new Mat();
        Mat pointMatHsv = new Mat(1, 1, CvType.CV_8UC3, hsvColor);
        Imgproc.cvtColor(pointMatHsv, pointMatRgba, Imgproc.COLOR_HSV2RGB_FULL, 4);

        return new Scalar(pointMatRgba.get(0, 0));
    }

    public boolean polygonTest(Point test, List<Point> points) {
        Log.i("POLYGON_TEST", "polygonTestRunning");
        int i;
        int j;
        boolean result = false;

//        for (int a = 0; a < points.size(); a++) {
//            Log.i("POLYGON_TEST", "Points " + a + ": " + points.get(a).x + "  " + points.get(a).y);
//        }
        for (i = 0, j = points.size() - 1; i < points.size(); j = i++) {

            if ((points.get(i).y > test.y) != (points.get(j).y > test.y) &&
                    (test.x < (points.get(j).x - points.get(i).x) * (test.y - points.get(i).y) / (points.get(j).y - points.get(i).y) + points.get(i).x)) {
                result = !result;
            }
        }
        return result;
    }

    /*
    The orientation of the app is landscape.
    If the orientation of the phone is portrait : +----------+
                                                  |1        2|
                                                  |          |
                                                  |          |
                                                  |          |
                                                  |          |
                                                  |4        3|
                                                  +----------+

     Then relative to it the orientation of the tactile can be 4, assuming the tags are the
     uppermost left & right corner of the diagram, the 4 orientations w.r.t. tags are, clockwise:
     1. left tag @ 1 & right tag @ 2
     2. left tag @ 2 & right tag @ 3
     3. left tag @ 3 & right tag @ 4
     4. left tag @ 4 & right tag @ 1
     */
    // Retuns orientation number between 1 to 4
    public static int getOrientation() {
        return orientation;
    }

    public static boolean isFingerStatic(Point normalizedFinger) {
        if (ColorBlobDetectionActivity.fingerApprovalCount > 0) {
            ColorBlobDetectionActivity.fingerApprovalCount--;
            return true;
        }
        if (ColorBlobDetectionActivity.previousFingerPosition == null) {
            ColorBlobDetectionActivity.previousFingerPosition = new Point(normalizedFinger.x, normalizedFinger.y);
            ColorBlobDetectionActivity.fingerStaticCount = 0;
            return false;
        } else {
            if (normalizedFinger.x < (ColorBlobDetectionActivity.previousFingerPosition.x + ColorBlobDetectionActivity.allowedFingerMovement)
                    && normalizedFinger.x > (ColorBlobDetectionActivity.previousFingerPosition.x - ColorBlobDetectionActivity.allowedFingerMovement)
                    && normalizedFinger.y < (ColorBlobDetectionActivity.previousFingerPosition.y + ColorBlobDetectionActivity.allowedFingerMovement)
                    && normalizedFinger.y > (ColorBlobDetectionActivity.previousFingerPosition.y - ColorBlobDetectionActivity.allowedFingerMovement)) {
                ColorBlobDetectionActivity.fingerStaticCount++;
            } else {
                ColorBlobDetectionActivity.previousFingerPosition = new Point(normalizedFinger.x, normalizedFinger.y);
                ColorBlobDetectionActivity.fingerStaticCount = 0;
            }
            if (ColorBlobDetectionActivity.fingerStaticCount > ColorBlobDetectionActivity.fingerStaticLimit) {
                ColorBlobDetectionActivity.fingerStaticCount = 0;
                ColorBlobDetectionActivity.fingerApprovalCount = 5;
                return true;
            } else {
                return false;
            }
        }
    }

}
