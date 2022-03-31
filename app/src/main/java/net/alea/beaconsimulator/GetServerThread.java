package net.alea.beaconsimulator;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;


public class GetServerThread extends Thread {

    private int USER = 0;
    private Handler handler;
    private String result = "default";
    private boolean isRunning = true;


    public void setResult(String string) {
        this.result = string;
    }
    public String getResult(){
        return this.result;
    }


    public void setHandler(Handler handler){
        this.handler = handler;
    }

    public boolean getIsRunning(){
        return isRunning;
    }
    public void setIsRunning(boolean run){
        this.isRunning = run;
    }
    @Override
    public void run() {
        String urls = "http://gpdetectionguide.ml/ReadExample.php";
        while(isRunning){
            Log.d("TAG", "getDataServer / running");
            try {
                URL url = new URL(urls);
                HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();

                httpURLConnection.setReadTimeout(5000);
                httpURLConnection.setConnectTimeout(5000);
                httpURLConnection.connect();


                int responseStatusCode = httpURLConnection.getResponseCode();
                Log.d("TAG", "getDataThread / response code-" + responseStatusCode);

                InputStream inputStream;
                if(responseStatusCode == HttpURLConnection.HTTP_OK) {
                    inputStream = httpURLConnection.getInputStream();
                }
                else{
                    inputStream = httpURLConnection.getErrorStream();
                }


                InputStreamReader inputStreamReader = new InputStreamReader(inputStream, "UTF-8");
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

                StringBuilder sb = new StringBuilder();
                String line;

                while((line = bufferedReader.readLine()) != null){
                    sb.append(line);
                }
                bufferedReader.close();

                setResult(sb.toString().trim());
                Message msg = handler.obtainMessage();
                Bundle bundle = new Bundle();
                bundle.putString("info", getResult());
                msg.setData(bundle);

                handler.sendMessage(msg);

                // time interval of thread
                Thread.sleep(5000);

            } catch (Exception e) {
                e.printStackTrace();
                Log.d("TAG", "background Exception"+e.toString());
            }
        }

    }

}
