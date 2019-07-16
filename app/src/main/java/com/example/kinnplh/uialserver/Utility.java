package com.example.kinnplh.uialserver;

import android.app.Service;
import android.os.Build;
import android.os.Looper;
import android.os.Vibrator;
import android.util.Pair;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * Created by kinnplh on 2019/4/7.
 */

public class Utility {

    public static Vibrator vibrator;

    public static void vibrate(long milliseconds){
        if(vibrator == null){
            vibrator = (Vibrator) UIALServer.self.getSystemService(Service.VIBRATOR_SERVICE);
        }
        vibrator.vibrate(milliseconds);
    }


    public static void assertTrue(boolean cond){
        if(BuildConfig.DEBUG && !cond){
            throw new AssertionError();
        }
    }

    public static int countMergedNodeNum(MergedNode node){
        if(node == null){
            return 0;
        }
        int res = 1;
        for(MergedNode c: node.childrenNode){
            res += countMergedNodeNum(c);
        }
        return res;

    }

    public static int countNodeNum(AccessibilityNodeInfoRecord root){
        if(root == null){
            return 0;
        }
        int res = 1;
        /*int res = 0;
        if(root.isClickable() || root.isLongClickable()
                || root.isScrollable() || root.isEditable() || root.isCheckable()
                || (root.getText() != null && root.getText().length() > 0)
                || (root.getContentDescription() != null && root.getContentDescription().length() > 0)){
            res += 1;
        }*/
        for(int i = 0; i < root.getChildCount(); ++ i){
            res += countNodeNum(root.getChild(i));
        }
        return res;
    }

    public static List<Pair<MergedNode, AccessibilityNodeInfoRecord>> lcsNodes(List<MergedNode> mgNodes, List<AccessibilityNodeInfoRecord> uiNodes){
        int l1 = mgNodes.size();
        int l2 = uiNodes.size();

        if(l1 == 0 || l2 == 0){
            return new ArrayList<>();
        }

        int [][] dp = new int[l1][l2];
        List<List<List<Pair<MergedNode, AccessibilityNodeInfoRecord>>>> record = new ArrayList<>();
        for(int i = 0; i < l1; ++ i){
            List<List<Pair<MergedNode, AccessibilityNodeInfoRecord>>> subList = new ArrayList<>();
            for(int j = 0; j < l2; ++ j){
                subList.add(new ArrayList<Pair<MergedNode, AccessibilityNodeInfoRecord>>());
            }
            record.add(subList);
        }

        for(int mgNodeIndex = 0; mgNodeIndex < l1; ++ mgNodeIndex){
            MergedNode mgNode = mgNodes.get(mgNodeIndex);
            for(int uiNodeIndex = 0; uiNodeIndex < l2; ++ uiNodeIndex){
                AccessibilityNodeInfoRecord uiNode = uiNodes.get(uiNodeIndex);
                if(mgNode.canMatchUINode(uiNode)){
                    if(mgNodeIndex > 0 && uiNodeIndex > 0){
                        dp[mgNodeIndex][uiNodeIndex] = dp[mgNodeIndex - 1][uiNodeIndex - 1] + 1;
                        record.get(mgNodeIndex).get(uiNodeIndex).addAll(record.get(mgNodeIndex - 1).get(uiNodeIndex - 1));
                        record.get(mgNodeIndex).get(uiNodeIndex).add(new Pair<>(mgNode, uiNode));
                    } else {
                        dp[mgNodeIndex][uiNodeIndex] = 1;
                        record.get(mgNodeIndex).get(uiNodeIndex).add(new Pair<>(mgNode, uiNode));
                    }
                } else {
                    if(mgNodeIndex != 0 && uiNodeIndex != 0){
                        if(dp[mgNodeIndex - 1][uiNodeIndex] > dp[mgNodeIndex][uiNodeIndex - 1]){
                            dp[mgNodeIndex][uiNodeIndex] = dp[mgNodeIndex - 1][uiNodeIndex];
                            record.get(mgNodeIndex).get(uiNodeIndex).addAll(record.get(mgNodeIndex - 1).get(uiNodeIndex));
                        } else {
                            dp[mgNodeIndex][uiNodeIndex] = dp[mgNodeIndex][uiNodeIndex - 1];
                            record.get(mgNodeIndex).get(uiNodeIndex).addAll(record.get(mgNodeIndex).get(uiNodeIndex - 1));
                        }
                    } else if(mgNodeIndex != 0){
                        dp[mgNodeIndex][uiNodeIndex] = dp[mgNodeIndex - 1][uiNodeIndex];
                        record.get(mgNodeIndex).get(uiNodeIndex).addAll(record.get(mgNodeIndex - 1).get(uiNodeIndex));
                    } else if(uiNodeIndex != 0){
                        dp[mgNodeIndex][uiNodeIndex] = dp[mgNodeIndex][uiNodeIndex - 1];
                        record.get(mgNodeIndex).get(uiNodeIndex).addAll(record.get(mgNodeIndex).get(uiNodeIndex - 1));
                    }
                }

            }
        }


        return record.get(l1 - 1).get(l2 - 1);
    }

    public static boolean isMainThread() {
        return Looper.getMainLooper().getThread() == Thread.currentThread();
    }

    public static String getCurrentPkg(){
        AccessibilityNodeInfo root = UIALServer.self.getRootInActiveWindow();
        if(root == null){
            return null;
        }
        String res = root.getPackageName().toString();
        root.recycle();
        return res;
    }

    public static List<Pair<MergedNode, Float>> findMergedNodeByText(MergedApp appToSearch, String str){
        return findMergedNodeByText(appToSearch, str, null);
    }


    public static List<Pair<MergedNode, Float>> findMergedNodeByText(MergedApp appToSearch, String str, List<MergedState> specificStates){
        List<Pair<MergedNode, Float>> result = new ArrayList<>();
        for(MergedPage mgPage: appToSearch.mergedPages){
            for(MergedState mgState: mgPage.mergedStates){
                if(specificStates != null && !specificStates.contains(mgState)){
                    continue;
                }
                Deque<MergedNode> nodeDeque = new LinkedList<>();
                nodeDeque.addLast(mgState.rootRegion.root);
                while (!nodeDeque.isEmpty()){
                    MergedNode crtNode = nodeDeque.pollFirst();
                    int count = 0;
                    for(String containedText: crtNode.allSelfTexts){
                        if(containedText.contains(str)){
                            count += (crtNode.textToCount.get(containedText) * str.length() / (float)containedText.length());
                        }
                    }
                    for(String containedContents: crtNode.allSelfContents){
                        if(containedContents.contains(str)){
                            count += (crtNode.contentToCount.get(containedContents) * str.length() / (float)containedContents.length());
                        }
                    }
                    if(count != 0){
                        result.add(new Pair<>(crtNode, count / (float)crtNode.totalInstanceNum));
                    }

                    // 将子节点加入到队列中去
                    for(MergedNode childNode: crtNode.childrenNode){
                        nodeDeque.addLast(childNode);
                    }
                    for(MergedRegion subRegion: crtNode.childrenRegion){
                        nodeDeque.addLast(subRegion.root);
                    }
                }
            }
        }

        Collections.sort(result, new Comparator<Pair<MergedNode, Float>>() {
            @Override
            public int compare(Pair<MergedNode, Float> o1, Pair<MergedNode, Float> o2) {
                if(o1.second > o2.second){
                    return -1;
                } else if(o1.second < o2.second){
                    return 1;
                } else {
                    return 0;
                }
            }
        });

        return  result;
    }

    public static MergedNode getClickableParentNode(MergedNode node){
        MergedNode crt = node;
        while (crt != null && !crt.clickable){
            crt = crt.parent;
        }
        return crt;
    }

    public static String httpRequestGet(String urlStr){
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        try {
            URL url = new URL(urlStr);
            connection = (HttpURLConnection) url.openConnection();
            //设置请求方法
            connection.setRequestMethod("GET");
            //设置连接超时时间（毫秒）
            connection.setConnectTimeout(5000);
            //设置读取超时时间（毫秒）
            connection.setReadTimeout(5000);

            //返回输入流
            InputStream in = connection.getInputStream();

            //读取输入流
            reader = new BufferedReader(new InputStreamReader(in));
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
            return result.toString();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (ProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;

    }

    public static class LuisRes{
        String intent;
        List<String> context;
        public LuisRes(String jsonStr) throws JSONException {
            JSONObject wholeRes = new JSONObject(jsonStr);
            context = new ArrayList<>();

            intent = wholeRes.getJSONObject("topScoringIntent").getString("intent");
            if(!intent.equals("sendMessage")){
                JSONArray entityObjects = wholeRes.getJSONArray("entities");
                for(int i = 0; i < entityObjects.length(); ++ i){
                    JSONObject crtEntity = entityObjects.getJSONObject(i);
                    String crtEntityValue = crtEntity.getString("entity");
                    String crtType = crtEntity.getString("type");
                    if(Objects.equals(crtType, "builtin.personName")){
                        int nameStartIndex = crtEntity.getInt("startIndex");
                        int nameEndIndex = crtEntity.getInt("endIndex") + 1;
                        if(nameEndIndex - nameStartIndex >= 4){
                            nameEndIndex = nameStartIndex + 3;
                        }
                        crtEntityValue = wholeRes.getString("query").substring(nameStartIndex, nameEndIndex);
                    }
                    context.add(crtEntityValue);
                }
            } else {
                JSONArray entityObjects = wholeRes.getJSONArray("entities");
                int nameStartIndex = entityObjects.getJSONObject(0).getInt("startIndex");
                int nameEndIndex = entityObjects.getJSONObject(0).getInt("endIndex") + 1;
                if(nameEndIndex - nameStartIndex >= 4){
                    nameEndIndex = nameStartIndex + 3;
                }

                String name = wholeRes.getString("query").substring(nameStartIndex, nameEndIndex);
                String msg = wholeRes.getString("query").substring(nameEndIndex);
                context.add(name);
                context.add(msg);
            }

        }
    }

    public static LuisRes getLuisRes(String query){
        String wholeURL = "https://westus.api.cognitive.microsoft.com/luis/v2.0/apps/45d32165-a34d-45e5-93db-5f2f332c4c82?timezoneOffset=-360&subscription-key=eb3577c9adb0405f8f4044a20afba33b&q=";
        try {
            wholeURL = wholeURL + URLEncoder.encode(query, "UTF-8");
            return new LuisRes(httpRequestGet(wholeURL));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static AccessibilityNodeInfoRecord getScrollableParent(AccessibilityNodeInfoRecord node){
        AccessibilityNodeInfoRecord crt = node;
        while (crt != null && !crt.isScrollable()){
            crt = crt.parent;
        }

        return crt;
    }
}
