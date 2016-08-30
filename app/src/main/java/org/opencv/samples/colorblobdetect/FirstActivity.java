package org.opencv.samples.colorblobdetect;

import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.Toast;

import org.opencv.samples.colorblobdetect.R;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FirstActivity extends Activity {

    Button button;
    WebView webview;

    String url = "http://textfiles.com/100/";
//    String url = "http://10.192.51.225:8080/blob-upload-master/view.php";
//    String url = "https://www.webscorer.com/resources/templatestart";

    String curr_url;

//    String envpath = Environment.getDataDirectory() + File.separator + "Tactile Reader";
     String envpath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + "/Tactile Reader";

    String file_name;

    ProgressDialog prog;

    BroadcastReceiver onComplete;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

//        this.getWindow().requestFeature(Window.FEATURE_PROGRESS);
        setContentView(R.layout.activity_first);

//        getWindow().setFeatureInt(Window.FEATURE_PROGRESS, Window.PROGRESS_VISIBILITY_ON);

        button = (Button)findViewById(R.id.button);

        webview = (WebView)findViewById(R.id.webView);
        webview.getSettings().setLoadsImagesAutomatically(true);
        webview.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);

        prog = new ProgressDialog(this);


//        webview.getSettings().setLoadWithOverviewMode(true);
//        webview.loadUrl(url);
//        webview.setWebViewClient(new MyBrowser());
//        final Activity MyActivity = this;
//        webview.setWebChromeClient(new WebChromeClient() {
//            public void onProgressChanged(WebView view, int progress) {
//                //Make the bar disappear after URL is loaded, and changes string to Loading...
//                int currProg = progress*100;
//                prog.setMessage(currProg + "");
////                MyActivity.setTitle("Loading...");
////                MyActivity.setProgress(progress * 100); //Make the bar disappear after URL is loaded
//
//                // Return the app name after finish loading
//                if (progress == 100) {
////                    MyActivity.setTitle(R.string.app_name);
//                    prog.dismiss();
//                }
//            }
//        });

        prog.setMessage("Loading...");
        prog.show();

        webview.setWebViewClient(new MyBrowser());
        webview.getSettings().setJavaScriptEnabled(true);
        webview.loadUrl(url);

        webview.setDownloadListener(new DownloadListener() {
            public void onDownloadStart(String url, String userAgent,
                                        String contentDisposition, String mimetype,
                                        long contentLength) {

//                Uri uri = Uri.parse(url);
//                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
//                startActivity(intent);
                if(!prog.isShowing()){
                    prog.setMessage("Downloading...");
                    prog.show();
                }

                registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

                file_name = URLUtil.guessFileName(url, contentDisposition, mimetype);
                Log.wtf("MTP", "downloading fileName:" + file_name);
                deleteDir(file_name);

                int pos = file_name.lastIndexOf(".");
                String justName = pos > 0 ? file_name.substring(0, pos) : file_name;
                deleteDir(justName);

                DownloadManager.Request request = new DownloadManager.Request(
                        Uri.parse(url));

                request.allowScanningByMediaScanner();
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED); //Notify client once download is completed!
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOCUMENTS + "/Tactile Reader", file_name);
//                request.setDestinationInExternalFilesDir(getApplicationContext(), getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) + "/Tactile Reader", filename);
                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                dm.enqueue(request);
                Log.wtf("MTP", "DOWNLOAD CATCH..!!!");
//                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT); //This is important!
//                intent.addCategory(Intent.CATEGORY_OPENABLE); //CATEGORY.OPENABLE
//                intent.setType("*/*");//any application,any extension
//                Toast.makeText(getApplicationContext(), "Downloading File", Toast.LENGTH_LONG).show();
            }
        });

        onComplete = new BroadcastReceiver() {
            public void onReceive(Context ctxt, Intent intent) {
                // your code

                Log.wtf("MTP", "DOWNLOAD COMPLETE");
                if(prog.isShowing()){
                    prog.dismiss();
                }

                try {
                    unzip(envpath + file_name, envpath);
                } catch (IOException e) {
                    e.printStackTrace();
                }


            }
        };

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                Intent i = new Intent(Intent.ACTION_VIEW);
//                i.setData(Uri.parse(url));
//                startActivity(i);
                prog = new ProgressDialog(FirstActivity.this);
                prog.setMessage("Loading...");
                prog.show();
                webview.loadUrl(url);

            }
        });




    }

    @Override
    public void onBackPressed()
    {
        Intent intent = new Intent(this, StartActivity.class);
        startActivity(intent);
        finish();
//        super.onBackPressed();  // optional depending on your needs

    }

    public void unzip(String fullFilePath, String target) throws IOException {
        File zipFile = new File(fullFilePath);
        File targetDirectory = new File(target);
        ZipInputStream zis = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(zipFile)));
        try {
            ZipEntry ze;
            int count;
            byte[] buffer = new byte[8192];
            while ((ze = zis.getNextEntry()) != null) {
                File file = new File(targetDirectory, ze.getName());
                File dir = ze.isDirectory() ? file : file.getParentFile();
                if (!dir.isDirectory() && !dir.mkdirs())
                    throw new FileNotFoundException("Failed to ensure directory: " +
                            dir.getAbsolutePath());
                if (ze.isDirectory())
                    continue;
                FileOutputStream fout = new FileOutputStream(file);
                try {
                    while ((count = zis.read(buffer)) != -1)
                        fout.write(buffer, 0, count);
                } finally {
                    fout.close();
                }
            /* if time should be restored as well
            long time = ze.getTime();
            if (time > 0)
                file.setLastModified(time);
            */
            }
        } finally {
            zis.close();
        }
    }

    private void deleteDir(String dirName){
        File dir = new File(envpath + dirName);
        if(dir.exists()){
            if (dir.isDirectory())
            {
                String[] children = dir.list();
                for (int i = 0; i < children.length; i++)
                {
                    new File(dir, children[i]).delete();
                }
            }

            dir.delete();
        }

    }



    private class MyBrowser extends WebViewClient {


        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            curr_url = url;
            if(url.endsWith(".txt") || url.endsWith(".zip") || url.endsWith(".rar")){
                if(!prog.isShowing()){
                    prog.setMessage("Downloading...");
                    prog.show();
                }

                registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

                file_name = url.substring(url.lastIndexOf('/') + 1);
                deleteDir(file_name);

                int pos = file_name.lastIndexOf(".");
                String justName = pos > 0 ? file_name.substring(0, pos) : file_name;
                deleteDir(justName);

                DownloadManager.Request request = new DownloadManager.Request(
                        Uri.parse(url));

                request.allowScanningByMediaScanner();
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED); //Notify client once download is completed!
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOCUMENTS + "/Tactile Reader", file_name);
//                request.setDestinationInExternalFilesDir(getApplicationContext(), getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) + "/Tactile Reader", filename);
                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                dm.enqueue(request);

            }
            else{
                view.loadUrl(url);
                if (!prog.isShowing()) {
                    prog.show();
                }
            }
            return true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            System.out.println("on finish");
            if (prog.isShowing()) {
                prog.dismiss();
            }

        }
    }



    private class MyWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            view.loadUrl(url);

            if (!prog.isShowing()) {
                prog.show();
            }

            return true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            System.out.println("on finish");
            if (prog.isShowing()) {
                prog.dismiss();
            }

        }
    }

}
