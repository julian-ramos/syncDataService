package com.ramos.julian.syncdataservice;

import android.content.Context;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;


import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.TimeUnit;

/**
 * Created by julian on 6/30/15.
 */
public class syncDataService extends WearableListenerService {
    GoogleApiClient mGoogleApiClient;

    private static final String START_ACTIVITY = "/start_activity";
    String TAG="syncDataService";
    
    String receivedFilename="", response="";

    @Override
    public void onCreate() {
        super.onCreate();
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle connectionHint) {

                        Log.d(TAG, "API connected: " );
                        // Now you can use the Data Layer API
                    }
                    @Override
                    public void onConnectionSuspended(int cause) {
                        Log.d(TAG, "onConnectionSuspended: " + cause);
                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult result) {
                        Log.d(TAG, "onConnectionFailed: " + result);
                    }
                })
                        // Request access only to the Wearable API
                .addApi(Wearable.API)
                .build();

        mGoogleApiClient.connect();


    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Wearable.DataApi.removeListener(mGoogleApiClient, this);
        mGoogleApiClient.disconnect();
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        Log.d(TAG, "Got something!!");
        for (DataEvent event : dataEvents) {
            if (event.getType() == DataEvent.TYPE_CHANGED &&
                    event.getDataItem().getUri().getPath().equals("/txt"))
            {
                Log.d(TAG,"got a data object");
                // Get the Asset object
                DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());
                Asset asset = dataMapItem.getDataMap().getAsset("com.ramos.julian.dataSync.TXT");

                ConnectionResult result =
                        mGoogleApiClient.blockingConnect(1000, TimeUnit.MILLISECONDS);
                if (!result.isSuccess()) {return;}

                // Convert asset into a file descriptor and block until it's ready
                InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                        mGoogleApiClient, asset).await().getInputStream();

                if (assetInputStream == null) { return; }

                // Get folder for output
                File sdcard = Environment.getExternalStorageDirectory();
                File dir = new File(sdcard.getAbsolutePath() + "/MyAppFolder/");
                if (!dir.exists()) { dir.mkdirs(); } // Create folder if needed

                // Read data from the Asset and write it to a file on external storage
                if (receivedFilename.equalsIgnoreCase("")) receivedFilename = "randomfile.txt";
                final File file = new File(dir, receivedFilename);
                try {
                    Log.d(TAG,"Starting to receive data");
                    Log.d(TAG,"writing to file "+receivedFilename);
                    FileOutputStream fOut = new FileOutputStream(file);
                    int nRead;
                    byte[] data = new byte[16384];
                    //Erase this variable and the related code later
                    int counter=0;
                    while ((nRead = assetInputStream.read(data, 0, data.length)) != -1) {

                        fOut.write(data, 0, nRead);
                        counter++;
                        if (counter==1000){
                            Log.d(TAG,"Still writing");
                            counter=0;

                        }
                    }
                    fOut.close();
                    Log.d(TAG,"file stored sending message to wearable");
                    sendMessage("status","file stored");
                    Log.d(TAG,"Done writing data");
                    receivedFilename="";
                }
                catch (Exception e)
                {
                    Log.d(TAG,"Something went wrong");
                    Log.d(TAG,e.toString());
                }

                // Rescan folder to make it appear
                try {
                    String[] paths = new String[1];
                    paths[0] = file.getAbsolutePath();
                    MediaScannerConnection.scanFile(this, paths, null, null);
                } catch (Exception e) {
                }
            }
//            if (event.getType() == DataEvent.TYPE_CHANGED &&
//                    event.getDataItem().getUri().getPath().equals("/filename")){
//                Log.d(TAG,"got a filename");
//                // Get the Asset object
//                DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());
//                Asset asset = dataMapItem.getDataMap().getAsset("com.ramos.julian.dataSync.filename");
//
//                ConnectionResult result =
//                        mGoogleApiClient.blockingConnect(100, TimeUnit.MILLISECONDS);
//                if (!result.isSuccess()) {return;}
//
//                // Convert asset into a file descriptor and block until it's ready
//                InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
//                        mGoogleApiClient, asset).await().getInputStream();
//
////                mGoogleApiClient.disconnect();
//
//                if (assetInputStream == null) { return; }
//
//                try {
//                    t0=System.currentTimeMillis();
//
//                    int nRead;
//                    byte[] data = new byte[10];
//                    Log.d(TAG,"Starting to receive data");
//                    while ((nRead = assetInputStream.read(data, 0, data.length)) != -1) {
//                        filename = new String(data);
//                        filename = filename.trim();
//                    }
//                    Log.d(TAG,"file to be received =" +filename);
//                }
//                catch (Exception e)
//                {
//                }
//
//
//            }
        }
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.d(TAG,"got a message");
        if( messageEvent.getPath().equalsIgnoreCase( "filename" ) ) {
            try {
                Log.d(TAG, String.format("Received the message! %s with the next data: %s", messageEvent.getPath(),new String(messageEvent.getData(),"UTF-8") ));
                String temp[]=(new String(messageEvent.getData(),"UTF-8")).split(",");
                receivedFilename=temp[0];
                response=temp[1];
                Log.d(TAG,"sending back the next "+ response);
                sendMessage("filename", response);


            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }


        } else {
            super.onMessageReceived(messageEvent);
        }
    }

    private void sendMessage( final String path, final String text ) {
        if (!mGoogleApiClient.isConnected()){
            Log.d(TAG," Wow!!! APi disconnected reconnecting!!");
            mGoogleApiClient.connect();
            while (!mGoogleApiClient.isConnected()){
                SystemClock.sleep(50);
            }

        }

        new Thread( new Runnable() {
            @Override
            public void run() {
                NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes( mGoogleApiClient ).await();
                for(Node node : nodes.getNodes()) {
                    MessageApi.SendMessageResult result = Wearable.MessageApi.sendMessage(
                            mGoogleApiClient, node.getId(), path, text.getBytes() ).await();
                }
                Log.d(TAG,"sent message "+text+" with path "+path);


            }
        }).start();
    }




}
