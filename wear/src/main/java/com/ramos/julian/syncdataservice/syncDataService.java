package com.ramos.julian.syncdataservice;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

public class syncDataService extends WearableListenerService {
    String TAG = "syncDataService";
    GoogleApiClient mGoogleApiClient;
    Timer timer;
    String folder=MainActivity.folder;
    String pathStorage = Environment.getExternalStorageDirectory().toString()+folder;
    String pathLog=Environment.getExternalStorageDirectory().toString()+"/log";
    FileOutputStream input;
    PrintWriter prnt;
    Thread thread;
    String receivedPath, receivedMessage="",t,message;
    int counter=0;
    boolean stored=false;
    Thread one;



    @Override
    public void onCreate() {
        super.onCreate();

        for (int i=0;i<100;i++){
            File fil= new File(Environment.getExternalStorageDirectory()+folder+String.format("/randomFile%d.txt",i));
            try {

                //The next code is only for testing purposes
                input = new FileOutputStream(fil);
                prnt = new PrintWriter(input);
                prnt.println("Something new "+Long.toString(System.currentTimeMillis()));
                int cont=0;
                while(cont < 10) {
                    prnt.println("Something new "+String.valueOf(cont));
                    cont++;
                }
                prnt.close();
                //End of testing code

                Log.d(TAG,"Timestamp after closing file"+String.valueOf(System.currentTimeMillis()));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }



        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle connectionHint) {
                        Log.d(TAG, "onConnected: " + connectionHint);
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

        //This one is crucial
        //if the GoogleApiClient is istantiated but it is not connected it won't work
        mGoogleApiClient.connect();



        one = new Thread(new Runnable() {
            public void run() {
                //                    hardSyncData();
                
                hardSyncDatanoCheck();
            }
        });

        one.start();
        // THE NEXT PIECE OF CODE IS FOR HAVING A TIMER CHECKING ON FILES ON
        // THE STORAGE DIRECTORY AUTONATICALLY EVERY N SECONDS
        // Only problem of the code right now is that if the file is open
        // There is no way to now except checking the last modified time for
        // changes
//        timer = new Timer();
//        timer.scheduleAtFixedRate( new checkTask(),0, 60000);

    }

    void hardSyncDatanoCheck(){
        //This function simply check for the data in a file and sends it through the data
        // API without any regard for whether the file was synced or not before
        String filename;
        File rootDir,file, storageDir;
        BufferedReader input;
        PrintWriter output;
        File filesList[];
        String filesListStr[];
        FileInputStream fileInputStream = null;

        storageDir = new File(pathStorage);

        if (storageDir.exists()){
            filesList=storageDir.listFiles();
            filesListStr= new  String[filesList.length];
            for (int i=0;i<filesList.length;i++){
                filesListStr[i]=filesList[i].toString();
                Log.d(TAG,filesList[i].toString());
                Log.d(TAG,"reading file");
                byte[] bFile = new byte[(int) filesList[i].length()];
                try {
                    fileInputStream = new FileInputStream(filesList[i]);
                    fileInputStream.read(bFile);
                    fileInputStream.close();
                } catch (Exception e) {
                }
                Log.d(TAG,"File read preparing to send");
                // Create an Asset from the byte array, and send it via the DataApi

                checkConnect();


                if (mGoogleApiClient.hasConnectedApi(Wearable.API)){


                    //Sending first a message with the name of the file that is coming afterwards
                    t = Long.toString(System.currentTimeMillis());
                    String temp[]=filesListStr[i].split("/");
                    message=temp[temp.length-1];

                    message = message+","+t;
                    Log.d(TAG,message);

                    while (!receivedMessage.equalsIgnoreCase(message) && counter<40){
                        Log.d(TAG,"sending message");
                        sendMessage("filename",message);
                        SystemClock.sleep(100);
                        counter++;
                        if (receivedMessage.equalsIgnoreCase(t)){
                            receivedMessage="";
                            counter=0;
                            Log.d(TAG,"ACK received breaking while loop");
                            break;
                        }
                    }
                    if (counter>40){
                        Log.d(TAG,"Didn't get response after many trials");
                        counter=0;
                        return;
                    }




                    Log.d(TAG, "Sending data");
                    Asset asset = Asset.createFromBytes(bFile);
                    Log.d(TAG, "Asset created");
                    PutDataMapRequest dataMap = PutDataMapRequest.create("/txt");
                    Log.d(TAG, "Putting Asset");
                    dataMap.getDataMap().putAsset("com.ramos.julian.dataSync.TXT", asset);
                    PutDataRequest request = dataMap.asPutDataRequest();
                    Log.d(TAG, "Data request put");
                    PendingResult<DataApi.DataItemResult> pendingResult = Wearable.DataApi
                            .putDataItem(mGoogleApiClient, request);

                    while(counter<300){
                        Log.d(TAG,"Waiting for data to be stored");
                        counter++;
                        SystemClock.sleep(50);
                        if(stored){
                            break;
                        }


                    }
                    if (!stored){
                        Log.d(TAG,"The data was not stored by mobile");
                        return;
                    }
                    else{
                        stored=false;
                        Log.d(TAG,"The data was stored by mobile");
                    }



                }
                else{
                    Log.d(TAG,"wearable API did not connect");
                }



            }









        }else{
            Log.d(TAG,"Directory does not exist, nothing to sync");
        }


    }

    void hardSyncData() throws IOException {
        //This function is going to push to the phone every single file in the folder that has not been synced before
        String filename;
        File rootDir,file, storageDir;
        BufferedReader input;
        PrintWriter output;
        File filesList[];
        String filesListStr[];


        storageDir = new File(pathStorage);

        if (storageDir.exists()){
            filesList=storageDir.listFiles();
            filesListStr= new  String[filesList.length];
            for (int i=0;i<filesList.length;i++){
                filesListStr[i]=filesList[i].toString();
                Log.d(TAG,filesList[i].toString());
            }

            rootDir = new File(pathLog);
            if (!rootDir.exists()){
                Log.d(TAG,"Log dir did not exist creating");
                rootDir.mkdir();
            }

            filename=pathLog+"/transactionsLog.txt";

            file = new File(filename);

            if (!file.exists() & filesList.length>0){
                Log.d(TAG,"File does not exist and there are some files");
                //Since the file does not exist create it and mark all files as not synced
                try {
                    output = new PrintWriter(new FileWriter(file,true));
                    for (int i=0;i<filesList.length;i++){
                        output.println(filesList[i].toString()+",notSynced, "+String.valueOf(filesList[i].lastModified()));
                    }
                    output.close();


                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }

            }


            //We made sure in the previous step that the log exists so now I'm going to read it
            // and check first if its synced, if it is not sync, if it was synced before check
            // the last modified date with the current if it is different sync again
            try {

                if (file.exists()){
                    input = new BufferedReader(new FileReader(file));
                    for(String line; (line = input.readLine()) != null; ) {
                        String temp[]=line.split(",");
                        Log.d(TAG,"searching \n"+temp[0]+" "+Arrays.asList(filesListStr).indexOf(temp[0]));
                        for (int i=0;i<filesList.length;i++){
                            Log.d(TAG,filesList[i].toString());
                        }

                        if (Arrays.asList(filesListStr).contains(temp[0])){
                            int index = Arrays.asList(filesListStr).indexOf(temp[0]);
                            //Now we compare the last Modified date of each file
                            long tempo=Long.valueOf(temp[2].trim());
                            if (Long.parseLong(temp[2].trim())<filesList[index].lastModified()||
                                    temp[1].equalsIgnoreCase("notSynced")){
                                Log.d(TAG," The file "+filesListStr[index].toString()+" needs sync");
                            }
                            else{
                                Log.d(TAG," The file "+filesListStr[index].toString()+" does not need sync");
                            }


                            /*TODO
                            *
                            * -Now that the files that are new, old or just not synced are detected
                            * we can start sending files to the phone however we need to find out whether the
                            * file has been received by the phone or not
                            *
                            * It seems like the only way to do this is to send a message when the file is sent
                            * and then wait for the phone app to answer with an OK after certain time and then
                            * wear can mark as sync
                            * */


                        }

                    }


                }




            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }


        }else{
            Log.d(TAG,"Directory does not exist, nothing to sync");
        }














    }


    //TODO
    /*
    * Maybe a way to check whether a file is not open by any other application is by
    * checking for a couple of seconds if the timestamp of last modified has not changed
    * if so then flag as new file and then sync
    *
    * */

    private class checkTask extends TimerTask{
        public void run(){

            Log.d(TAG, "Path: " + pathStorage);

            File f = new File(pathStorage);
            File file[] = f.listFiles();


            if (file!=null) {
                Log.d("Files", "Size: " + file.length);
                for (int i = 0; i < file.length; i++) {
                    Log.d(TAG, "FileName:" + file[i].getName());
                    Log.d(TAG," last modified "+ file[i].lastModified());
                    Log.d(TAG,"Can read "+file[i].canRead());
                    Log.d(TAG,"Can write "+file[i].canWrite());

                }
            }
            else{
                Log.d(TAG,"Directory is empty");
            }
        }

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mGoogleApiClient.disconnect();
        if (one.isAlive()) one.interrupt();


        try {
            input.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    boolean checkConnect(){
        int count=0;
        if (!mGoogleApiClient.hasConnectedApi(Wearable.API)){
            Log.d(TAG,"GoogleApi not connected");
            try {
                while (!mGoogleApiClient.hasConnectedApi(Wearable.API)){
                    Thread.sleep(10);
                    count++;
                    if (count>=1000){
                        Log.d(TAG,"could not connect to the API finishing Thread");
                        return false;
                    }
                }
                Log.d(TAG,"Google API connected after "+count+" trials");

            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }
        return true;
    }
    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.d(TAG,"got message from mobile");
        if( messageEvent.getPath().equalsIgnoreCase( "filename" ) ) {
            try {
                Log.d(TAG, String.format("Received the message! %s with the next data: %s", messageEvent.getPath(),new String(messageEvent.getData(),"UTF-8") ));
                receivedMessage=new String(messageEvent.getData(),"UTF-8");
                Log.d(TAG,"Got this message "+ receivedMessage);



            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }


        }
        else if( messageEvent.getPath().equalsIgnoreCase( "status" ) ) {
            Log.d(TAG,"Got a change of status");
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            try {
                Log.d(TAG, String.format("Received the message! %s with the next data: %s", messageEvent.getPath(),new String(messageEvent.getData(),"UTF-8") ));
                if(new String(messageEvent.getData(),"UTF-8").trim().equalsIgnoreCase("file stored")){
                    stored=true;
                }




            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }

        else {
            super.onMessageReceived(messageEvent);
        }


    }

    private void sendMessage( final String path, final String text ) {
        new Thread( new Runnable() {
            @Override
            public void run() {
                NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes( mGoogleApiClient ).await();
                for(Node node : nodes.getNodes()) {
                    MessageApi.SendMessageResult result = Wearable.MessageApi.sendMessage(
                            mGoogleApiClient, node.getId(), path, text.getBytes() ).await();
                }


            }
        }).start();
    }

}
