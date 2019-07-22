package com.example.kinnplh.uialserver;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Toast;

import org.json.JSONException;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by kinnplh on 2018/5/14.
 */

public class UIALServer extends AccessibilityService {
    public static UIALServer self;
    Map<String, MergedApp> packageToMergedApp;
    ServerThread thread;

    @Override
    public void onCreate() {
        super.onCreate();
        self = this;
        packageToMergedApp = new HashMap<>();

        VUIInstance.init(self);
        try {
            AssetManager manager = getAssets();
            String[] mergedAppNames = manager.list("merged_apps");
            System.out.println("get_merged_apps");

            for(String fileName: mergedAppNames) {
                if(!fileName.endsWith(".json"))
                    continue;
                MergedApp mergedApp = new MergedApp(this, "merged_apps/" + fileName);
                packageToMergedApp.put(mergedApp.packageName, mergedApp);
                System.out.println("packageName:"+mergedApp.packageName);
            }
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }

        if(thread == null) {
            thread = new ServerThread();
            thread.start();
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
    }

    @Override
    public void onInterrupt() {

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        self = null;
    }

    boolean isListening = false;
    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP) {
            /*if(!isListening){
                Toast.makeText(UIALServer.self, "开始录音", Toast.LENGTH_SHORT).show();
            }*/
            new Thread() {
                @Override
                public void run() {
                    /*if (!isListening) {
                        isListening = true;
                        VUIInstance.start();
                        Log.i("vui", "res: " + "start");
                        Utility.vibrate(100);
                    } else {
                        isListening = false;
                        String nl = VUIInstance.stop();
                        Log.i("vui", "res: " + nl);
                        Utility.vibrate(10);
                        thread.handleNLCMD(nl, System.out);
                    }*/
                    String nl = "hangon";
                    System.out.println("hang on the phone");
                    thread.handleNLCMD(nl, System.out);
                }
            }.start();
        }
        else if (event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN) {
            new Thread() {
                @Override
                public void run() {
                    /*if (!isListening) {
                        isListening = true;
                        VUIInstance.start();
                        Log.i("vui", "res: " + "start");
                        Utility.vibrate(100);
                    } else {
                        isListening = false;
                        String nl = VUIInstance.stop();
                        Log.i("vui", "res: " + nl);
                        Utility.vibrate(10);
                        thread.handleNLCMD(nl, System.out);
                    }*/
                    String nl = "answer";
                    System.out.println("answer the phone");
                    thread.handleNLCMD(nl, System.out);

                }
            }.start();
        }
        return super.onKeyEvent(event);
    }
}
