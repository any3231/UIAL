package com.example.kinnplh.uialserver;

import android.app.Activity;
import android.app.AppOpsManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    Button b;
    Button btn_start;
    Button btn_stop;
    Button btn_higher;
    Button btn_lower;
    Button btn_quite;
    Button btn_next;
    Button btn_pre;
    EditText editText;
    AudioManager aManager;
    MediaPlayer mePlayer;
    int flag = 1;
    boolean isPauseMusic = false;
    public static MainActivity self;
    private static boolean f=false;
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE" };
    private static String[] PERMISSIONS_RECORD = {
            "android.permission.RECORD_AUDIO"
    };
    public static void verifyStoragePermissions(Activity activity) {

        try {
            //检测是否有写的权限
            int permission = ActivityCompat.checkSelfPermission(activity,
                    "android.permission.WRITE_EXTERNAL_STORAGE");
            if (permission != PackageManager.PERMISSION_GRANTED) {
                // 没有写的权限，去申请写的权限，会弹出对话框
                ActivityCompat.requestPermissions(activity, PERMISSIONS_STORAGE,REQUEST_EXTERNAL_STORAGE);
            }

            int p1 = ActivityCompat.checkSelfPermission(activity, "android.permission.RECORD_AUDIO");
            if(p1 != PackageManager.PERMISSION_GRANTED){
                ActivityCompat.requestPermissions(activity, PERMISSIONS_RECORD, 1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        self = this;
        setContentView(R.layout.activity_main);
        verifyStoragePermissions(this);
        if (!hasPermission()){
            startActivityForResult(
                    new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
                    MY_PERMISSIONS_REQUEST_PACKAGE_USAGE_STATS);
        }

        Log.i("accessibilityAccessible", "onCreate: can i access accessibility service??" + (UIALServer.self != null));
        aManager = (AudioManager) getSystemService(Service.AUDIO_SERVICE);
        //mePlayer = MediaPlayer.create(MainActivity.this, R.raw.countingstars);
        b = findViewById(R.id.button);
        editText = findViewById(R.id.editText);
        btn_start = (Button) findViewById(R.id.btn_start);
        btn_stop = (Button) findViewById(R.id.btn_stop);
        btn_higher = (Button) findViewById(R.id.btn_higher);
        btn_lower = (Button) findViewById(R.id.btn_lower);
        btn_quite = (Button) findViewById(R.id.btn_quite);
        btn_next = (Button) findViewById(R.id.btn_next);
        btn_pre = (Button) findViewById(R.id.btn_pre);
        btn_start.setOnClickListener(this);
        btn_stop.setOnClickListener(this);
        btn_higher.setOnClickListener(this);
        btn_lower.setOnClickListener(this);
        btn_quite.setOnClickListener(this);
        btn_next.setOnClickListener(this);
        btn_pre.setOnClickListener(this);
        b.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        KeyEvent kevent;
        long eventTime;
        switch (v.getId()) {
            case R.id.button:
                Log.i("accessibilityAccessible", "onCreate: can i access accessibility service??" + (UIALServer.self != null));
                if (UIALServer.self != null && UIALServer.self.getRootInActiveWindow() != null) {
                    // b.setText(String.valueOf(UIALServer.self.getRootInActiveWindow().getPackageName()));
                    final String nl = editText.getText().toString();
                    if(nl.length() == 0){
                        Toast.makeText(MainActivity.self, "请先输入指令", Toast.LENGTH_SHORT).show();
                    } else {
                        new Thread(){
                            @Override
                            public void run() {
                                UIALServer.self.thread.handleNLCMD(nl, System.out);
                            }
                        }.start();
                    }
                }
                else{
                    Toast.makeText(MainActivity.self, "请先在设置中打开无障碍服务", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.btn_start:
                btn_stop.setEnabled(true);
                System.out.println("start");
                if (isPauseMusic) {
                    aManager.abandonAudioFocus(null);
                    isPauseMusic = false;
                }
                btn_start.setEnabled(false);
                break;
            case R.id.btn_stop:
                btn_start.setEnabled(true);
                System.out.println("stop");
                if(aManager.isMusicActive()){
                    aManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC,
                            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
                    isPauseMusic = true;
                }
                btn_stop.setEnabled(false);
                break;
            case R.id.btn_higher:
                aManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                break;
            case R.id.btn_lower:
                // 指定调节音乐的音频，降低音量，只有声音,不显示图形条
                aManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_LOWER, AudioManager.FLAG_PLAY_SOUND);
                break;
            case R.id.btn_quite:
                // 指定调节音乐的音频，根据isChecked确定是否需要静音
                flag *= -1;
                if (flag == -1) {
                    aManager.setStreamMute(AudioManager.STREAM_MUSIC, true);   //API 23过期- -
//                    aManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE,
//                            AudioManager.FLAG_SHOW_UI);   //23以后的版本用这个
                    btn_quite.setText("取消静音");
                } else {
                    aManager.setStreamMute(AudioManager.STREAM_MUSIC, false);//API 23过期- -
//                    aManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE,
//                            AudioManager.FLAG_SHOW_UI);  //23以后的版本用这个
                    aManager.setMicrophoneMute(false);
                    btn_quite.setText("静音");
                }
                break;
            case R.id.btn_next:
                System.out.println("next");
                eventTime = SystemClock.uptimeMillis();
                kevent = new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT, 0);
                dispatchMediaKeyToAudioService(kevent);
                dispatchMediaKeyToAudioService(KeyEvent.changeAction(kevent, KeyEvent.ACTION_UP));
                break;
            case R.id.btn_pre:
                System.out.println("previous");
                eventTime = SystemClock.uptimeMillis();
                kevent = new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS, 0);
                dispatchMediaKeyToAudioService(kevent);
                dispatchMediaKeyToAudioService(KeyEvent.changeAction(kevent, KeyEvent.ACTION_UP));
                break;
        }


    }

    private void dispatchMediaKeyToAudioService(KeyEvent event) {
        aManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        System.out.println("dispatchMediaKeyToAudioService");
        aManager.dispatchMediaKeyEvent(event);
    }

    private boolean hasPermission() {
        AppOpsManager appOps = (AppOpsManager)
                getSystemService(Context.APP_OPS_SERVICE);
        int mode = 0;
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
            mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), getPackageName());
        }
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private static final int MY_PERMISSIONS_REQUEST_PACKAGE_USAGE_STATS = 1101;
    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        if (requestCode == MY_PERMISSIONS_REQUEST_PACKAGE_USAGE_STATS) {
            if (!hasPermission()) {
                //若用户未开启权限，则引导用户开启“Apps with usage access”权限
                startActivityForResult(
                        new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
                        MY_PERMISSIONS_REQUEST_PACKAGE_USAGE_STATS);
            }
        }
    }


}
