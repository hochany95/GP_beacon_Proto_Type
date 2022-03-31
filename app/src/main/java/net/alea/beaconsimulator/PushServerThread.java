package net.alea.beaconsimulator;

import android.support.v4.view.ViewPager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class PushServerThread extends Thread {

    private String URL = "http://gpdetectionguide.ml/Insert.php";
    //Driver : 0, Pedestrian : 1
    //"'V-UUID', 'P-UUID', 'V-lat', 'V-lng', 'P-lat', 'P-lng', 'distance'"


    private String data;
    private boolean isStop = true;
    private String lat, lng;

    private String V_UUID = "null";
    private String P_UUID = "null";

    public void setLocation(Double lat, Double lng){
        this.lat = Double.toString(lat);
        this.lng = Double.toString(lng);
    }
    public void setUUID(String V, String P){
        this.V_UUID = V;
        this.P_UUID = P;
    }
    public void setIsStop(boolean stop){
        this.isStop = stop;
    }
    public boolean getIsStop(){
        return this.isStop;
    }


    public void setData(String data) {
        this.data = data;
    }
    public String getData(){
        return data;
    }


    @Override
    public void run() {
        while(true){
            Log.d("TAG", "setDataServer / running");
            while(isStop){
                try {
                    Thread.sleep(1000);
                    Log.d("TAG", "SetServerThread / isStop:"+isStop);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            if(URL==null) return;

            // Currently, only forward P_location.
            // This part will be updated later.
            data = String.format("'%s', '%s', '%s', '%s', '%s', '%s', '%s'", V_UUID, P_UUID, "0", "0", lat, lng, "0");


            String postParameters = "DATA=" + data;
            try {
                URL url = new URL(URL);
                HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();

                httpURLConnection.setReadTimeout(5000);
                httpURLConnection.setConnectTimeout(5000);
                httpURLConnection.setRequestMethod("POST");
                httpURLConnection.connect();

                OutputStream outputStream = httpURLConnection.getOutputStream();
                outputStream.write(postParameters.getBytes("UTF-8"));
                outputStream.flush();
                outputStream.close();

                int responseStatusCode = httpURLConnection.getResponseCode();
                Log.d("TAG", "setDataThread / response code-" + responseStatusCode);
                Thread.sleep(500);


            } catch (Exception e) {
                Log.d("TAG", "In setServerThread / error"+e.toString());
            }
        }

    }

}
