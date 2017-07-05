package org.opencv.samples.colorblobdetect;

import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * Created by sumit on 7/5/17.
 */

interface myAsyncTaskCompletedListener {
    void onMyAsyncTaskCompleted(int responseCode, List<String> result);
}

public class DownloadService extends AsyncTask<HashMap<String, Object>, Void, List<String>>{

    private myAsyncTaskCompletedListener listener;
    private int responseCode = 0;

    public DownloadService(myAsyncTaskCompletedListener listener, int responseCode){
        this.listener = listener;
        this.responseCode = responseCode;
    }

    private int calculate_match_score(String[] query_parts, String[] title_parts){
        int num_matches = 0;
        for(int i = 0; i < query_parts.length; i++){
            String query_part = query_parts[i];
            for(int j = 0; j < title_parts.length; j++){
                String title_part = title_parts[j];
                if(title_part.equalsIgnoreCase(query_part))
                    num_matches++;
            }
        }
        return num_matches;
    }

    private String getDesc(String context_file, String query) throws Exception {
        String desc = "";
        final String USER_AGENT = "Mozilla/5.0";
//        final String API_KEY = "AIzaSyCzTM3ETD4MaSSQ0qAoKzSYyAZcfRMd3o8";
        final String API_KEY = "AIzaSyC4wsy3HeVgGkSVB709cgWkbnECDysCwHQ";
        String expectedLink = "en.wikipedia.org";
        String google_API = "https://www.googleapis.com/customsearch/v1?cref=&key=%s&q=%s&amp;cx=017576662512468239146:omuauf_lfve&amp;q=cars&amp;callback=hndlr";
        URL obj = new URL(String.format(google_API, API_KEY, context_file.replace("_", "+")+"+"+query.replace(" ", "+")+"+wiki"));
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("User-Agent", USER_AGENT);
        BufferedReader in = new BufferedReader(
                new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        String search_results = response.toString();
        JSONObject json_res = new JSONObject(search_results);
        JSONArray items = json_res.getJSONArray("items");
        int best_score = -1;
        String best_title = "";
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            if(item.get("displayLink").equals(expectedLink)){
                String link = (String) item.get("link");
                String title = link.substring(link.lastIndexOf("/")+1);
                String[] query_parts = query.split(" ");
                String[] title_parts = title.split("_");
                int score = calculate_match_score(query_parts, title_parts);
                if(score > best_score){
                    best_score = score;
                    best_title = title;
                }else if(score == best_score && query_parts.length == title_parts.length){
                    best_score = score;
                    best_title = title;
                }
            }
        }

        String url = "https://en.wikipedia.org/w/api.php?action=query&prop=extracts&format=json&exintro=&explaintext=&exsectionformat=plain&titles="+best_title;

        obj = new URL(url);
        con = (HttpURLConnection) obj.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("User-Agent", USER_AGENT);
        in = new BufferedReader(
                new InputStreamReader(con.getInputStream()));
        response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        String content = response.toString();
        JSONObject json_obj = new JSONObject(content);
        JSONObject pages = json_obj.getJSONObject("query").getJSONObject("pages");
        Iterator<?> keys = pages.keys();
        while( keys.hasNext() ) {
            String key = (String)keys.next();
            if ( pages.get(key) instanceof JSONObject ) {
                String toSpeak = pages.getJSONObject(key).getString("extract");
                desc = toSpeak;
            }
        }
        return desc;
    }

    @Override
    protected List<String> doInBackground(HashMap<String, Object>... params) {
        HashMap<String, Object> map_titles = params[0];
        Log.wtf("DS", "in doInBackground");
        Log.wtf("DS", ""+map_titles);
        List<String> titles = (List<String>) map_titles.get("titles");
        List<String> desc = (List<String>) map_titles.get("descriptions");
        String filename = (String) map_titles.get("file");
        for(String title: titles){
            Log.wtf("DS", "--> "+title);
            try {
                String description = getDesc(filename, title);
                desc.add(description.replace('\n',' '));
            } catch (Exception e) {
                e.printStackTrace();
                desc.add("error");
            }
        }
        return titles;
    }

    protected void onPostExecute(List<String> result) {
        Log.wtf("DS", "in onPostExecute");
        if(listener != null){
            listener.onMyAsyncTaskCompleted(responseCode, result);
        }
    }
}
