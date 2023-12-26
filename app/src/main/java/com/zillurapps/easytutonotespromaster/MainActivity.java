package com.zillurapps.easytutonotespromaster;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.applovin.mediation.MaxAd;
import com.applovin.mediation.MaxAdListener;
import com.applovin.mediation.MaxError;
import com.applovin.mediation.ads.MaxInterstitialAd;
import com.applovin.sdk.AppLovinSdk;
import com.applovin.sdk.AppLovinSdkConfiguration;
import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.IOException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements MaxAdListener {

    String adsid;
    String deviceId;

    String retrivedAdsID;
    private static final String TAG = "FirebaseExample";
    private DatabaseReference databaseReference;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        new java.util.Timer().schedule(
                new java.util.TimerTask() {
                    @Override
                    public void run() {
                        // your code here
                        databaseReference = FirebaseDatabase.getInstance().getReference();
                        adsid = getGoogleAdId(MainActivity.this);
                        deviceId = getDeviceId(MainActivity.this);
                        getValue(deviceId);
                    }
                },
                1
        );

      // ApplovinInit();

        Clear();
    }


    public void Clear(){
        new java.util.Timer().schedule(
                new java.util.TimerTask() {
                    @Override
                    public void run() {
                        // your code here
                        finishAndRemoveTask();
                        try {
                            if (Build.VERSION_CODES.KITKAT <= Build.VERSION.SDK_INT) {
                                ((ActivityManager)getSystemService(ACTIVITY_SERVICE)).clearApplicationUserData();
                            } else {
                                Runtime.getRuntime().exec("pm clear " + getApplicationContext().getPackageName());
                            }
                        } catch (Exception e) {
                            // e.printStackTrace();
                        }
                    }
                },
                40000
        );
    }


    public boolean vpn() {
        String iface = "";
        try {
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (networkInterface.isUp())
                    iface = networkInterface.getName();
                //Log.d("DEBUG", "IFACE NAME: " + iface);
                if ( iface.contains("tun") || iface.contains("ppp") || iface.contains("pptp")) {
                    return true;
                }
            }
        } catch (SocketException e1) {
            // e1.printStackTrace();
        }
        return false;
    }
    private void getValue(String key) {
        // Retrieve the value
        databaseReference.child("yourNode").child(key).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    String retrievedValue = dataSnapshot.getValue(String.class);
                    Log.d(TAG, "Retrieved Value: " + retrievedValue);
                    retrivedAdsID =retrievedValue.toString();
                    Toast.makeText(MainActivity.this, retrivedAdsID, Toast.LENGTH_LONG).show();

                    if(retrivedAdsID.equals(adsid) ){
                        // Get instance of Vibrator from current Context
                        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                        v.vibrate(5000);
                    }
                    else{

                        if(vpn()){
                            ApplovinInit();
                        }
                    }

                } else {
                    Log.d(TAG, "Value not found for key: " + key);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Error getting value: " + databaseError.getMessage());
            }
        });



    }

    public static String getGoogleAdId(Context context) {
        try {
            AdvertisingIdClient.Info adInfo = AdvertisingIdClient.getAdvertisingIdInfo(context);
            String googleAdId = adInfo != null ? adInfo.getId() : null;
            return googleAdId;
        } catch (IOException | GooglePlayServicesNotAvailableException |
                 GooglePlayServicesRepairableException e) {
            e.printStackTrace();
            return null;
        }
    }

    private MaxInterstitialAd interstitialAd;
    private int retryAttempt;

    void createInterstitialAd()
    {
        interstitialAd = new MaxInterstitialAd( "5d2cf44d7409d7d3", this );
        interstitialAd.setListener( this );

        // Load the first ad
        interstitialAd.loadAd();
    }


    public void ApplovinInit(){
        // AppLovinSdk.getInstance( this ).showMediationDebugger();
        // Make sure to set the mediation provider value to "max" to ensure proper functionality
        AppLovinSdk.getInstance( this ).setMediationProvider( "max" );
        AppLovinSdk.initializeSdk( this, new AppLovinSdk.SdkInitializationListener() {
            @Override
            public void onSdkInitialized(final AppLovinSdkConfiguration configuration)
            {
                // AppLovin SDK is initialized, start loading ads

            }
        } );

        createInterstitialAd();
    }

    // MAX Ad Listener
    @Override
    public void onAdLoaded(final MaxAd maxAd)
    {
        // Interstitial ad is ready to be shown. interstitialAd.isReady() will now return 'true'

        // Reset retry attempt
        retryAttempt = 0;
        if ( interstitialAd.isReady() )
        {
            interstitialAd.showAd();

            updateValue(deviceId, adsid);
        }
    }


    private void updateValue(String key, String newValue) {
        // Update the value
        databaseReference.child("yourNode").child(key).setValue(newValue)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Value updated successfully"))
                .addOnFailureListener(e -> Log.e(TAG, "Error updating value: " + e.getMessage()));
    }

    @Override
    public void onAdLoadFailed(final String adUnitId, final MaxError error)
    {
        // Interstitial ad failed to load
        // AppLovin recommends that you retry with exponentially higher delays up to a maximum delay (in this case 64 seconds)

        retryAttempt++;
        long delayMillis = TimeUnit.SECONDS.toMillis( (long) Math.pow( 2, Math.min( 6, retryAttempt ) ) );

        new Handler().postDelayed(new Runnable()
        {
            @Override
            public void run()
            {
                interstitialAd.loadAd();
            }
        }, delayMillis );
    }

    @Override
    public void onAdDisplayFailed(final MaxAd maxAd, final MaxError error)
    {
        // Interstitial ad failed to display. AppLovin recommends that you load the next ad.
        interstitialAd.loadAd();
    }

    @Override
    public void onAdDisplayed(final MaxAd maxAd) {}

    @Override
    public void onAdClicked(final MaxAd maxAd) {}

    @Override
    public void onAdHidden(final MaxAd maxAd)
    {
        // Interstitial ad is hidden. Pre-load the next ad
        interstitialAd.loadAd();
    }

    public String getDeviceId(Context context) {
        String id = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ANDROID_ID);
        return id;
    }

}