package com.example.root.monerotest.Services;


import android.app.Activity;
import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.widget.Toast;

import com.example.root.monerotest.SettingActivity;
import com.example.root.monerotest.Utils.NotificationUtils;

import java.io.File;


public class SyncWalletService extends Service {

    static {
        System.loadLibrary("c++_shared");
        System.loadLibrary("native-lib");
    }

    public static final String ACTION_SYNC_DONE = "com.example.root.monerotest.SYNC_DONE";
    private Notification mNotification;
    private Callbacks mCallbacks;
    private Thread mThread;

    public boolean checkNetworkPref() {
       SharedPreferences pref =  getSharedPreferences(SettingActivity.PREF_FILE, MODE_PRIVATE);
        // 0 means WIFI only
        // 1 means Cell Data.
        int net_pref =  pref.getInt(SettingActivity.EXTRA_NETWORK_PREF, 0);

        ConnectivityManager cm =
                (ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

        boolean isWifiOn = activeNetwork.getType() == ConnectivityManager.TYPE_WIFI;

        //If no wifi available and net_pref is set to Wifi-only.
        if(!isWifiOn && net_pref == 0){
            Toast.makeText(this, "No wifi available. Don't have permission to use cell data",
                            Toast.LENGTH_LONG).show();
            //Exit since we don't have permission to use cell data.
            stopSelf();
            return false;
        }

        //TODO: check if the address is local. it can crash when address is local and wifi is off, but
        //TODO: seeting is set to sync with cell data. JNI crashes since exceptions are not handle yet.

        //Continue since we have permission
        return true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopForeground(true);
        mNotification = null;
        if(mThread != null)
            mThread.interrupt();
        mThread = null;
        mCallbacks = null;
    }

    public void checkHeight(){

        if(!checkNetworkPref()){
            return;
        }

        int walletHeight = 0;
        int daemonHeight = 0;

        walletHeight = WalletHeight();
        daemonHeight = DaemonHeight();

        if(daemonHeight == 0)
            return;

        if(mCallbacks == null)
            return;

        //If wallet height is not up-to-date with node.
        if(walletHeight < daemonHeight){
            //Update progress bar
            mCallbacks.updateProgressBar(walletHeight, daemonHeight);
            syncWalletToDaemon();
        }else{
            Toast.makeText(this, "wallet height not less than daemonHeight",Toast.LENGTH_SHORT).show();
        }
    }


    public void syncWalletToDaemon(){
        //Create a foreground service attach to a notification.
        mNotification = NotificationUtils.getSyncWalletNotification(getApplicationContext());
        startForeground(0x101, mNotification);

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                //Needs its own thread. otherwise it will block the main thread for 5 min at least.
                WalletRefresh();

                //Report back when syncing is complete.
                Intent syncCompleted = new Intent();
                syncCompleted.setAction(ACTION_SYNC_DONE);

                //Send a broadcast to update
                sendBroadcast(syncCompleted);

                //Stop foreground service no longer needed.
                stopForeground(true);
            }
        };

        mThread = new Thread(runnable);
        mThread.start();
    }

    public void registerClient(Activity activity){
        mCallbacks = (Callbacks)activity;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new SyncServiceBinder();
    }

    /**
     * JNI CALLS
     */
    //public native boolean InitWallet(String path);
    public native int WalletHeight();
    public native int DaemonHeight();
    public native void WalletRefresh();
    /**
     * Interface to communicate Service-Client.
     */
    public interface Callbacks{
        void updateProgressBar(int current, int max);
    }
    /**
     * Binder to hook client-server  between main activity and service.
     */
    public class SyncServiceBinder extends Binder {
        public SyncWalletService getService(){
            return SyncWalletService.this;
        }
    }
}
