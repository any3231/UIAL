package com.example.kinnplh.uialserver;

import android.content.Context;
import android.os.Bundle;
import android.os.Debug;
import android.util.Log;

import com.iflytek.cloud.RecognizerListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.SpeechUtility;

public class VUIInstance
{
    private volatile static String lastResult;
    private volatile static SpeechRecognizer mIat;
    private volatile static RecognizerListener mLis;
    public static void init(Context context)
    {
        SpeechUtility.createUtility(context, SpeechConstant.APPID +"=5cd3b919");
        mIat = SpeechRecognizer.createRecognizer(context, null);
        mIat.setParameter( SpeechConstant.CLOUD_GRAMMAR, null );
        mIat.setParameter( SpeechConstant.SUBJECT, null );
        mIat.setParameter(SpeechConstant.RESULT_TYPE, "plain");
        mIat.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
        mIat.setParameter(SpeechConstant.ACCENT, "mandarin");
        mIat.setParameter(SpeechConstant.VAD_BOS, "4000");
        mIat.setParameter(SpeechConstant.VAD_EOS, "1000");
        mIat.setParameter(SpeechConstant.ASR_PTT,"0");
        mIat.setParameter( SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD );
        mIat.setParameter( "record_force_stop", "true" );

        mLis = new RecognizerListener()
        {
            @Override
            public void onVolumeChanged(int i, byte[] bytes)
            {
            }

            @Override
            public void onBeginOfSpeech()
            {
                Log.i("","begin");
            }

            @Override
            public void onEndOfSpeech()
            {
            }

            @Override
            public void onResult(RecognizerResult recognizerResult, boolean b)
            {
                String tmp = recognizerResult.getResultString();
                if(!tmp.equals(""))
                {
                    lastResult = tmp;
                    Log.i("lastResult", lastResult);
                }
            }

            @Override
            public void onError(SpeechError speechError)
            {
                Log.i("onerror",speechError.toString());
            }

            @Override
            public void onEvent(int i, int i1, int i2, Bundle bundle)
            {
            }
        };
    }

    public static void start()
    {
        if(mIat==null)
        {
            Log.d("record","init error");
            return;
        }
        lastResult = "";
        int code = mIat.startListening(mLis);
        Log.d("code",Integer.toString(code));
    }


    public static String stop()
    {
        if(mIat==null)
        {
            Log.d("record", "init error");
        }
        if(mIat.isListening())
            mIat.stopListening();
        double startTime = System.currentTimeMillis();
        while(lastResult.equals("")){
            if(System.currentTimeMillis()-startTime>2000)
                break;
        }
        return lastResult;
    }

    public static String getLastResult()
    {
        return lastResult;
    }

    public static boolean isResultReady()
    {
        return !lastResult.equals("");
    }
}
