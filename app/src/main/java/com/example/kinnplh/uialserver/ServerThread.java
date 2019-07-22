package com.example.kinnplh.uialserver;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.util.Log;
import android.util.Pair;
import android.view.ContextMenu;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Created by kinnplh on 2019/4/9.
 */

public class ServerThread extends Thread {
    final int SERVER_PORT = 10087;

    ServerSocket serverSocket;
    Socket socket;
    BufferedReader reader;
    PrintStream writer;
    boolean threadRunning;
    public static Map<String, List<UIAuto.TargetFromFile>> intentToTarget;

    public ServerThread(){
        try {
            init();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void init() throws IOException {
        intentToTarget = new HashMap<>();

        InputStreamReader inputStreamReader = new InputStreamReader(
                UIALServer.self.getAssets().open("intentToTarget.txt"));
        BufferedReader reader = new BufferedReader(inputStreamReader);
        String s = null;
        while ((s = reader.readLine()) != null){
            List<UIAuto.TargetFromFile> targetFromFiles = new ArrayList<>();
            String[] split_s = s.split(":");
            Utility.assertTrue(split_s.length == 2);
            String intentName = split_s[0];
            String actionStr = split_s[1];
            String[] actions = actionStr.split(";");
            for(String a: actions){
                String[] split_a = a.split("#");
                int pageIndex = Integer.valueOf(split_a[0]);
                int stateIndex = Integer.valueOf(split_a[1]);
                String targetNodeClickStr = split_a.length == 3? split_a[2] : null;
                targetFromFiles.add(new UIAuto.TargetFromFile(pageIndex, stateIndex, targetNodeClickStr));
            }
            intentToTarget.put(intentName, targetFromFiles);
            System.out.println(intentName+" "+targetFromFiles);
        }
    }

    @Override
    public void run() {
        Utility.LuisRes res = Utility.getLuisRes("告诉李家惠明天晚上一起吃饭");
        System.out.println(res == null? "error": res.intent);

        threadRunning = true;
        try {
            serverSocket = new ServerSocket(SERVER_PORT);
        } catch (IOException e) {
            e.printStackTrace();
        }
        while (threadRunning){
            try {
                Log.i("SocketInfo", "Listening...");
                socket = serverSocket.accept();
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                writer = new PrintStream(socket.getOutputStream());
                Log.i("SocketInfo", "Accepted");
            } catch (IOException e){
                e.printStackTrace();
            }

            if(socket == null || reader == null){
                continue;
            }

            while (threadRunning){
                try {
                    String line = reader.readLine();
                    if(line == null){
                        break;
                    }
                    String [] split_line = line.split("#");
                    switch (split_line[0]){
                        case "GET_FIT_RES":
                            handleGetFitRes();
                            break;
                        case "TEST_FILE":
                            handleTestFile(Integer.valueOf(split_line[1]), Integer.valueOf(split_line[2]), Integer.valueOf(split_line[3]));
                            break;
                        case "JUMP_TO_TARGET_PAGE_STATE": {
                            List<String> contexts = new ArrayList<>();
                            for (int i = 3; i < split_line.length; ++i) {
                                contexts.add(split_line[i]);
                            }
                            handleJumpToTargetPageState(Integer.valueOf(split_line[1]), Integer.valueOf(split_line[2]), contexts, writer);
                            break;
                        }
                        case "JUMP_BY_CMD": {
                            List<String> contexts = new ArrayList<>();
                            for (int i = 4; i < split_line.length; ++i) {
                                contexts.add(split_line[i]);
                            }
                            handleJumpByCmd(Integer.valueOf(split_line[1]), Integer.valueOf(split_line[2]), contexts, split_line[3], writer);
                            break;
                        }
                        case "JUMP_ACCORDING_TO_STORED_FILES":
                            handleJumpAccordingToStoredFiles(split_line[1]);
                            break;
                        case "NL_CMD":
                            handleNLCMD(split_line[1], writer);
                            break;
                        default:
                            Log.e("CMD ERROR", String.format("Unknown cmd %s", line));
                            writer.print("UNKNOWN-CMD\n");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    writer.println("something wrong");
                    continue;
                }

            }


        }

    }

    void handleNLCMD(final String nl, final PrintStream stream){
        Thread th = new Thread(){
            @Override
            public void run() {
                if (nl == "answer") {
                    handleAnswerPhone();
                    return;
                }
                if (nl == "hangon") {
                    handleHangOnPhone();
                    return;
                }
                if (nl == "music") {
                    return;
                }
                String nnl = "";
                if (nl.startsWith("call")) {
                    nnl = "给"+nl.substring(5)+"打电话";
                }
                if (nl.startsWith("send")) {
                    nnl = "告诉";
                }
                Utility.LuisRes res = Utility.getLuisRes(nnl);
                if(res == null){
                    stream.println("invalid input " + nnl);
                    return;
                }
                Log.i("luis", "intent: " + res.intent);
                stream.println("intent: " + res.intent);
                if(!intentToTarget.containsKey(res.intent)){
                    stream.println("not supported cmd!!!");
                    return;
                }
                List<UIAuto.TargetFromFile> fileActions = intentToTarget.get(res.intent);
                if (res.intent.equals("music")) {
                    //TODO: control music player
                    handleMusicControl();
                }
                else {
                    UIAuto.jumpToApp("com.tencent.mm");

                    for (UIAuto.TargetFromFile oneAction : fileActions) {
                        if (oneAction.targetNodeToClickStr == null) {
                            stream.println("handleJumpToTargetPageState" + res.context);
                            handleJumpToTargetPageState(oneAction.pageIndex, oneAction.stateIndex, res.context, System.out);
                        } else {
                            stream.println("handleJumpByCmd" + res.context);
                            handleJumpByCmd(oneAction.pageIndex, oneAction.stateIndex, res.context, oneAction.targetNodeToClickStr, System.out);
                        }
                    }
                }

                stream.println("finished");
            }
        };
        if(Utility.isMainThread()){
            th.start();
        } else {
            th.run();
        }

    }

    void handleJumpByCmd(final int pageIndex, final int stateIndex, final List<String> contexts, final String lastActNodeStr, final PrintStream stream){
        Thread th = new Thread(){
            @Override
            public void run() {
                int leftTryTime = 10;
                Set<UIAuto.Action> visitedActions = new HashSet<>();
                List<MergedNode> targetNodes = null;
                while (leftTryTime > 0) {
                    leftTryTime -= 1;

                    AccessibilityNodeInfoRecord.buildTree();
                    if(AccessibilityNodeInfoRecord.root == null){
                        stream.println("current app not supported");
                        return;
                    }
                    String crtPackageName = AccessibilityNodeInfoRecord.root.getPackageName().toString();
                    MergedApp app = UIALServer.self.packageToMergedApp.get(crtPackageName);
                    if (app == null) {
                        stream.println("current app not supported");
                        return;
                    }
                    if(targetNodes == null) {
                        // 确定对应的target region
                        List<MergedState> states = new ArrayList<>();
                        if(stateIndex >= 0) {
                            MergedState targetState = app.mergedPages.get(pageIndex).mergedStates.get(stateIndex);
                            states.add(targetState);
                        } else if(pageIndex > 0) {
                            states.addAll(app.mergedPages.get(pageIndex).mergedStates);
                        } else {
                            states = null;
                        }

                        List<Pair<MergedNode, Float>> res = Utility.findMergedNodeByText(app, lastActNodeStr, states);
                        if(res.isEmpty()){
                            stream.println("no target node in given state");
                            return;
                        }

                        targetNodes = new ArrayList<>();
                        for(Pair<MergedNode, Float> oneRes: res){
                            targetNodes.add(oneRes.first);
                        }
                    }

                    Pair<Pair<Pair<MergedState, FitResultRegion>, Float>, Map<AccessibilityNodeInfoRecord, Pair<FitResultRegion, MergedNode>>> res = FitResultRegion.getFitResult(app, AccessibilityNodeInfoRecord.root);
                    if (res == null) {
                        stream.println("Unknown page reached");
                        return;
                    }

//                    long startTime1 = System.currentTimeMillis();
//                    Pair<List<UIAuto.Action>, MergedNode> actions_target = UIAuto.findActionListFromSrcToTarget(res.first.first.second, targetNodes, visitedActions);
//                    long endTime1 = System.currentTimeMillis();
                    Pair<List<UIAuto.Action>, MergedNode> actions_target/*_bi*/ = UIAuto.twoDirectionalFindActionListFromSrcToTarget(res.first.first.second, targetNodes, visitedActions);
//                    long endTime2 = System.currentTimeMillis();
//                    Log.i("cmp_search", String.format(Locale.CHINA, "time1: %d, length1: %d, time2: %d, length2: %d",
//                            endTime1 - startTime1, actions_target == null? -1: actions_target.first.size(), endTime2 - endTime1, actions_target_bi == null? -1: actions_target_bi.first.size()));
                    
                    if(actions_target == null){
                        stream.println("failed");
                        return;
                    }
                    Pair<List<UIAuto.Action>, Boolean> execRes = UIAuto.execActions(actions_target.first, contexts, null, 2000, actions_target.second);
                    if (execRes.second) {
                        List<UIAuto.Action> oneLast = new ArrayList<>();
                        oneLast.add(new UIAuto.Action(Utility.getClickableParentNode(actions_target.second), UIAuto.Action.CLICK, null));
                        List<String> lastContext = new ArrayList<>();
                        lastContext.add(lastActNodeStr);

                        Pair<List<UIAuto.Action>, Boolean> lastExecRes = UIAuto.execActions(oneLast , lastContext, null, 2000, null);
                        visitedActions.addAll(lastExecRes.first);
                        if(lastExecRes.second) {
                            stream.println("success");
                            return;
                        } else {
                            Log.i("failed", "last action failed");
                        }
                    }
                    visitedActions.addAll(execRes.first);
                }
                stream.println("failed");
            }
        };
        if(Utility.isMainThread()){
            th.start();
        } else {
            th.run();
        }
    }


    void handleJumpToTargetPageState(final int pageIndex, final int stateIndex, final List<String> contexts, final PrintStream stream){
        Thread th = new Thread(){
            @Override
            public void run() {
                int leftTryTime = 10;
                Set<UIAuto.Action> visitedActions = new HashSet<>();

                while (leftTryTime > 0) {
                    leftTryTime -= 1;

                    AccessibilityNodeInfoRecord.buildTree();
                    String crtPackageName = AccessibilityNodeInfoRecord.root.getPackageName().toString();
                    stream.println("crtPackageName: "+crtPackageName);
                    MergedApp app = UIALServer.self.packageToMergedApp.get(crtPackageName);
                    if (app == null) {
                        stream.println("current app not supported");
                        return;
                    }
                    MergedRegion targetRegion = app.mergedPages.get(pageIndex).mergedStates.get(stateIndex).rootRegion;
                    Pair<Pair<Pair<MergedState, FitResultRegion>, Float>, Map<AccessibilityNodeInfoRecord, Pair<FitResultRegion, MergedNode>>> res = FitResultRegion.getFitResult(app, AccessibilityNodeInfoRecord.root);
                    if (res == null) {
                        stream.println("Unknown page reached");
                        return;
                    }
                    List<MergedNode> oneNode = new ArrayList<>();
                    oneNode.add(targetRegion.root);

                    Pair<List<UIAuto.Action>, MergedNode> actions = UIAuto.twoDirectionalFindActionListFromSrcToTarget(res.first.first.second, oneNode, visitedActions);
                    Pair<List<UIAuto.Action>, Boolean> execRes = UIAuto.execActions(actions.first, contexts, null, 2000, targetRegion.root);
                    if (execRes.second) {
                        stream.println("success");
                        return;
                    }
                    visitedActions.addAll(execRes.first);
                }
                stream.println("failed");
            }
        };
        if(Utility.isMainThread()){
            th.start();
        } else {
            th.run();
        }
    }



    void handleGetFitRes(){
        // 确定当前页面具体是哪个页面
        AccessibilityNodeInfoRecord.buildTree();
        String crtPackageName = AccessibilityNodeInfoRecord.root.getPackageName().toString();
        MergedApp app = UIALServer.self.packageToMergedApp.get(crtPackageName);

        if(app == null) {
            writer.print("FAILED\n");
            return;
        }
        Pair<Pair<Pair<MergedState, FitResultRegion>, Float>, Map<AccessibilityNodeInfoRecord, Pair<FitResultRegion, MergedNode>>> res = FitResultRegion.getFitResult(app, AccessibilityNodeInfoRecord.root);
        if(res == null) {
            writer.print("FAILED\n");
            return;
        }

        MergedState state = res.first.first.first;
        int pageIndex = state.pageBelongTo.pageIndex;
        int stateIndex = state.stateIndex;
        float similarRatio = 1 - res.first.second;
        writer.print(String.format("%d-%d-%f\n", pageIndex, stateIndex, similarRatio));
    }

    void handleTestFile(int pageIndex, int stateIndex, int instanceIndex){
        try {
            AccessibilityNodeInfoRecordFromFile.buildTreeFromFile(String.format("/sdcard/PageInfo/Page%d/PageState%d/PageInstance%d.json", pageIndex, stateIndex, instanceIndex));
            String crtPackageName = AccessibilityNodeInfoRecord.root.getPackageName().toString();
            MergedApp app = UIALServer.self.packageToMergedApp.get(crtPackageName);

            if(app == null) {
                if(writer != null)
                    writer.print("FAILED\n");
                return;
            }
            Pair<Pair<Pair<MergedState, FitResultRegion>, Float>, Map<AccessibilityNodeInfoRecord, Pair<FitResultRegion, MergedNode>>> res = FitResultRegion.getFitResult(app, AccessibilityNodeInfoRecord.root);
            if(res == null) {
                if(writer != null)
                    writer.print("FAILED\n");
                return;
            }

            MergedState state = res.first.first.first;
            int pageIndexRes = state.pageBelongTo.pageIndex;
            int stateIndexRes = state.stateIndex;
            float similarRatio = 1 - res.first.second;
            if(writer != null)
                writer.print(String.format("%d-%d-%f\n", pageIndexRes, stateIndexRes, similarRatio));
            else{
                System.out.print(String.format("%d-%d-%f\n", pageIndexRes, stateIndexRes, similarRatio));
            }

        } catch (IOException | JSONException e) {
            if(writer != null)
                writer.print("error\n");
            e.printStackTrace();
        }
    }


    void handleJumpAccordingToStoredFiles(String rootPath){
        try {
            Pair<List<UIAuto.Action>, List<String>> listFromFile = UIAuto.generateActionFromDir(rootPath);  // "/sdcard/pbd_blind/ele/"
            /*UIAuto.openTargetApp(AccessibilityNodeInfoRecord.root.getPackageName().toString(),
                    UIALServer.self.packageToActivity.get(AccessibilityNodeInfoRecord.root.getPackageName().toString()));*/

            if(listFromFile != null) {
                // 跳转到对应到页面
                boolean jumpToApp = UIAuto.jumpToApp(listFromFile.first.get(0).actionNode.packageName);
                if(!jumpToApp){
                    writer.println("failed");
                    return;
                }

                // 跳转到对应到页面
                boolean jumpToStart = UIAuto.jumpToTargetNodeFromCurrent(listFromFile.first.get(0).actionNode);
                if(!jumpToStart){
                    writer.println("failed");
                    return;
                }

                Pair<List<UIAuto.Action>, Boolean> res = UIAuto.execActions(listFromFile.first, listFromFile.second, null, 5000, null, true);
                if(res.second) {
                    writer.println("success");
                } else {
                    writer.println("failed");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    void handleMusicControl() {
        /*Context context = getActivity().getApplicationContext()
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager.isMusicActive()) {

        }*/

    }

    void handleAnswerPhone() {
        AccessibilityNodeInfoRecord.buildTree();
        List<AccessibilityNodeInfoRecord> actionNodes = AccessibilityNodeInfoRecord.root.findAccessibilityNodeInfosByContentdesc("接听");
        if (actionNodes.isEmpty()) {
            System.out.println("NOT FOUND");
        }
        else {
            for (AccessibilityNodeInfoRecord actionNode : actionNodes)
                UIAuto.performAction(actionNode,new UIAuto.Action(null,UIAuto.Action.CLICK,null));
        }
    }

    void handleHangOnPhone() {
        AccessibilityNodeInfoRecord.buildTree();
        List<AccessibilityNodeInfoRecord> actionNodes = AccessibilityNodeInfoRecord.root.findAccessibilityNodeInfosByContentdesc("挂断");
        if (actionNodes.isEmpty()) {
            System.out.println("NOT FOUND");
        }
        else {
            for (AccessibilityNodeInfoRecord actionNode : actionNodes)
                UIAuto.performAction(actionNode,new UIAuto.Action(null,UIAuto.Action.CLICK,null));
        }
    }

}
