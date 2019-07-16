package com.example.kinnplh.uialserver;

import android.content.Context;
import android.util.Log;
import android.util.LongSparseArray;
import android.util.Pair;
import android.util.SparseArray;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Node;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Created by kinnplh on 2019/4/7.
 */

public class MergedApp {
    public static final int ACTION_INIT = 0;
    public static final int ACTION_CLICK = 1;
    public static final int ACTION_LONG_CLICK = 2;
    public static final int ACTION_SCROLL = 3;
    public static final int ACTION_ENTER_TEXT = 4;
    public static final int ACTION_GLOBAL_BACK = 5;


    public String packageName;
    public List<MergedPage> mergedPages;

    LongSparseArray<MergedNode> idToMergedNode;
    LongSparseArray<MergedRegion> idToMergedRegion;

    public MergedApp(Context context, String fileNameInAssets) throws IOException, JSONException {
        InputStreamReader inputStreamReader = new InputStreamReader(
                context.getAssets().open(fileNameInAssets));
        BufferedReader reader = new BufferedReader(inputStreamReader, 5 * 1024);
        char[] buffer = new char[5 * 1024];
        int length;
        StringBuilder builder = new StringBuilder();

        long time1 = System.currentTimeMillis();

        while ((length = reader.read(buffer)) != -1){
            builder.append(buffer, 0, length);
        }

        reader.close();
        inputStreamReader.close();

        long time2 = System.currentTimeMillis();
        Log.i("time", "read file time " + String.valueOf(time2 - time1));

        JSONObject mainObject = new JSONObject(builder.toString());
        long time3 = System.currentTimeMillis();
        Log.i("time", "get json obj time " + String.valueOf(time3 - time2));


        packageName = mainObject.getString("package_name");
        mergedPages = new ArrayList<>();
        JSONArray mergedPageArray = mainObject.getJSONArray("merged_pages");
        for(int i = 0; i < mergedPageArray.length(); ++ i){
            mergedPages.add(new MergedPage(mergedPageArray.getJSONObject(i)));
        }


        idToMergedNode = new LongSparseArray<>();
        idToMergedRegion = new LongSparseArray<>();

        for(MergedPage page: mergedPages){
            for(MergedState state: page.mergedStates){

                for(int i = 0; i < state.idToMergedRegion.size(); ++ i){
                    Long id = state.idToMergedRegion.keyAt(i);
                    MergedRegion region = state.idToMergedRegion.valueAt(i);
                    idToMergedRegion.put(id, region);
                }

                for(int i = 0; i < state.idToMergedNode.size(); ++ i){
                    Long id = state.idToMergedNode.keyAt(i);
                    MergedNode node = state.idToMergedNode.valueAt(i);
                    idToMergedNode.put(id, node);
                }

            }
        }

        for(MergedPage page: mergedPages){
            for(MergedState state: page.mergedStates){

                for(int i = 0; i < state.idToMergedRegion.size(); ++ i){
                    MergedRegion region = state.idToMergedRegion.valueAt(i);
                    region.linkObjectsById(idToMergedRegion, idToMergedNode);
                }

                for(int i = 0; i < state.idToMergedNode.size(); ++ i){
                    MergedNode node = state.idToMergedNode.valueAt(i);
                    node.linkObjectsById(idToMergedRegion, idToMergedNode);
                }
            }
        }

        long time4 = System.currentTimeMillis();
        Log.i("time", "change into java obj " + String.valueOf(time4 - time3));
    }

}


class MergedPage {
    public int pageIndex;
    public List<MergedState> mergedStates;

    public MergedPage(JSONObject jsonObject) throws JSONException {
        String type = jsonObject.getString("type");
        Utility.assertTrue(Objects.equals(type, "merged_page"));
        pageIndex = jsonObject.getInt("page_index");

        mergedStates = new ArrayList<>();
        JSONArray mergedStateJsonArray = jsonObject.getJSONArray("all_states");
        for(int i = 0; i < mergedStateJsonArray.length(); ++ i){
            MergedState crtState = new MergedState(i, this, mergedStateJsonArray.getJSONObject(i));
            mergedStates.add(crtState);
        }
    }
}

class MergedState {
    public int stateIndex;
    public MergedPage pageBelongTo;
    public LongSparseArray<MergedNode> idToMergedNode;
    public LongSparseArray<MergedRegion> idToMergedRegion;

    public MergedRegion rootRegion;

    public MergedState(int stateIndex, MergedPage pageBelongTo, JSONObject mergedStateJson) throws JSONException {
        this.stateIndex = stateIndex;
        this.pageBelongTo = pageBelongTo;
        idToMergedNode = new LongSparseArray<>();
        idToMergedRegion = new LongSparseArray<>();

        JSONObject idToMergedRegionJsonObject = mergedStateJson.getJSONObject("id_to_merged_region");
        Iterator<String> keyIterator = idToMergedRegionJsonObject.keys();
        while (keyIterator.hasNext()){
            String crtKey = keyIterator.next();
            idToMergedRegion.append(Long.valueOf(crtKey), new MergedRegion(
                    idToMergedRegionJsonObject.getJSONObject(crtKey), Long.valueOf(crtKey), this));
        }


        JSONObject idToMergedNodeJsonObject = mergedStateJson.getJSONObject("id_to_merged_node");
        Iterator<String> nodeIdIterator = idToMergedNodeJsonObject.keys();
        while (nodeIdIterator.hasNext()){
            String crtKey = nodeIdIterator.next();
            idToMergedNode.append(Long.valueOf(crtKey), new MergedNode(idToMergedNodeJsonObject.getJSONObject(crtKey)));
        }


        long rootRegionIndex = mergedStateJson.getLong("root_region");
        rootRegion = idToMergedRegion.get(rootRegionIndex);
    }
}

class MergedRegion {
    long id;

    long rootId;
    MergedNode root;  // 延后确定
    MergedState stateBelongsTo;

    String dynamicEntranceBelongToId;

    Long parentId;
    MergedRegion parent;  // 延后确定

    Map<String, List<Long>> entranceIdToSubRegionIds;
    Map<String, List<MergedRegion>> entranceIdToSubRegions;  // 延后确定

    public MergedRegion(JSONObject mergedRegionJsonObject, long id, MergedState state) throws JSONException {
        String type = mergedRegionJsonObject.getString("type");
        Utility.assertTrue(Objects.equals(type, "merged_region"));
        this.id = mergedRegionJsonObject.getLong("region_pointer");
        Utility.assertTrue(this.id == id);
        stateBelongsTo = state;

        rootId = mergedRegionJsonObject.getLong("merged_root");

        Object parentRegionId = mergedRegionJsonObject.get("parent_region");
        if(parentRegionId instanceof Long){
            parentId = (Long)parentRegionId;
        } else {
            parentId = null;
        }

        dynamicEntranceBelongToId = mergedRegionJsonObject.getString("dynamic_entrance_belong_to_id");
        entranceIdToSubRegionIds = new HashMap<>();

        JSONObject entranceIdToSubRegionJsonObject = mergedRegionJsonObject.getJSONObject("entrance_id_to_sub_region");
        Iterator<String> entranceIdIterator = entranceIdToSubRegionJsonObject.keys();
        while (entranceIdIterator.hasNext()){
            String crtEntranceId = entranceIdIterator.next();
            JSONArray idsJsonArray = entranceIdToSubRegionJsonObject.getJSONArray(crtEntranceId);
            List<Long> subRegionIds = new ArrayList<>();
            for(int i = 0; i < idsJsonArray.length(); ++ i){
                subRegionIds.add(idsJsonArray.getLong(i));
            }
            entranceIdToSubRegionIds.put(crtEntranceId, subRegionIds);
        }
    }

    public void linkObjectsById(LongSparseArray<MergedRegion> idToMergedRegion, LongSparseArray<MergedNode> idToMergedNode){
        root = idToMergedNode.get(rootId);

        if(parentId == null){
            parent = null;
        } else {
            parent = idToMergedRegion.get(parentId);
        }

        entranceIdToSubRegions = new HashMap<>();
        for(String entranceId: entranceIdToSubRegionIds.keySet()){
            List<MergedRegion> mergedRegions = new ArrayList<>();
            List<Long> mergedRegionIds = entranceIdToSubRegionIds.get(entranceId);
            for(Long id: mergedRegionIds){
                mergedRegions.add(idToMergedRegion.get(id));
            }

            entranceIdToSubRegions.put(entranceId, mergedRegions);
        }
    }
}


class MergedNode {
    long id;
    boolean isDynamicEntrance;
    int index;
    String absoluteIdToRegionRoot;
    String nodeClass;

    Long parentId;
    MergedNode parent;  // 延后确定

    List<Long> childrenNodeId;
    List<MergedNode> childrenNode;  // 延后确定

    List<Long> childrenRegionId;
    List<MergedRegion> childrenRegion; // 延后确定

    long regionBelongToId;
    MergedRegion regionBelongTo;  // 延后确定

    Map<Pair<Integer, Object>, List<Long>> actionTypeToResultNodeIds;
    Map<Pair<Integer, Object>, List<MergedNode>> actionTypeToResultNodes;  // 延后确定

    List<String> allTexts;
    List<String> allContents;
    List<String> allSelfTexts;
    List<String> allSelfContents;

    Map<String, Integer> textToCount;
    Map<String, Integer> contentToCount;

    LongSparseArray<List<Pair<Pair<Integer, Object>, String>>> targetNodeIdToTypeWithInfo;
    Map<MergedNode, List<Pair<Pair<Integer, Object>, String>>> targetNodeToTypeWithInfo; // 延后确定

    Set<Pair<MergedNode, List<Pair<Pair<Integer, Object>, String>>>> sourceNodeAndAction; // 延后确定，但是并不进行延后创建

    String resourceId;
    boolean checkable;
    boolean clickable;
    boolean focusable;
    boolean scrollable;
    boolean longClickable;

    String packageName;
    int totalInstanceNum;


    public boolean canMatchUINode(AccessibilityNodeInfoRecord uiNode){
        if(!Objects.equals(nodeClass, uiNode.getClassName().toString())){
            return false;
        }

        // 暂时不要求resource id 相同吧
        /*if(!Objects.equals(resourceId, uiNode.getViewIdResourceName() == null? "": uiNode.getViewIdResourceName().toString())){
            return false;
        }*/

        if(checkable != uiNode.isCheckable()){
            return false;
        }

        if(clickable != uiNode.isClickable()){
            return false;
        }

        if(focusable != uiNode.isFocusable()){
            return false;
        }

        /*if(scrollable != uiNode.isScrollable()){
            return false;
        }*/

        if(longClickable != uiNode.isLongClickable()){
            return false;
        }

        return true;
    }

    public MergedNode(JSONObject mergedNodeJsonObject) throws JSONException {
        String type = mergedNodeJsonObject.getString("type");
        Utility.assertTrue(Objects.equals(type, "merged_node"));

        id = mergedNodeJsonObject.getLong("node_pointer");
        isDynamicEntrance = mergedNodeJsonObject.getBoolean("is_dynamic_entrance");
        index = mergedNodeJsonObject.getInt("index");
        absoluteIdToRegionRoot = mergedNodeJsonObject.getString("absolute_id_to_region_root");
        nodeClass = mergedNodeJsonObject.getString("node_class");
        resourceId = mergedNodeJsonObject.getString("resource_id");
        checkable = mergedNodeJsonObject.getBoolean("checkable");
        clickable = mergedNodeJsonObject.getBoolean("clickable");
        focusable = mergedNodeJsonObject.getBoolean("focusable");
        scrollable = mergedNodeJsonObject.getBoolean("scrollable");
        longClickable = mergedNodeJsonObject.getBoolean("long_clickable");
        packageName = mergedNodeJsonObject.getString("package");
        totalInstanceNum = mergedNodeJsonObject.getInt("total_instance_num");

        Object parentIdObject = mergedNodeJsonObject.get("parent");
        if(parentIdObject instanceof Long){
            parentId = (Long) parentIdObject;
        } else {
            parentId = null;
        }

        childrenNodeId = new ArrayList<>();
        JSONArray childrenNodeIdJsonArray = mergedNodeJsonObject.getJSONArray("children_node");
        for(int i = 0; i < childrenNodeIdJsonArray.length(); ++ i){
            childrenNodeId.add(childrenNodeIdJsonArray.getLong(i));
        }

        childrenRegionId = new ArrayList<>();
        JSONArray childrenRegionIdJsonArray = mergedNodeJsonObject.getJSONArray("children_region");
        for(int i = 0; i < childrenRegionIdJsonArray.length(); ++ i){
            childrenRegionId.add(childrenRegionIdJsonArray.getLong(i));
        }

        regionBelongToId = mergedNodeJsonObject.getLong("region_belong_to");

        allTexts = new ArrayList<>();
        JSONArray allTextsJsonArray = mergedNodeJsonObject.getJSONArray("all_text");
        for(int i = 0; i < allTextsJsonArray.length(); ++ i){
            allTexts.add(allTextsJsonArray.getString(i));
        }

        allContents = new ArrayList<>();
        JSONArray allContentsJsonArray = mergedNodeJsonObject.getJSONArray("all_content");
        for(int i = 0; i < allContentsJsonArray.length(); ++ i){
            allContents.add(allContentsJsonArray.getString(i));
        }

        allSelfTexts = new ArrayList<>();
        JSONArray allSelfTextJsonArray = mergedNodeJsonObject.getJSONArray("all_self_text");
        for(int i = 0; i < allSelfTextJsonArray.length(); ++ i){
            allSelfTexts.add(allSelfTextJsonArray.getString(i));
        }

        allSelfContents = new ArrayList<>();
        JSONArray allSelfContentJsonArray = mergedNodeJsonObject.getJSONArray("all_self_content");
        for(int i = 0; i < allSelfContentJsonArray.length(); ++ i){
            allSelfContents.add(allSelfContentJsonArray.getString(i));
        }

        textToCount = new HashMap<>();
        JSONObject textToCountJsonObject = mergedNodeJsonObject.getJSONObject("text_to_count");
        Iterator<String> allAppearedTexts = textToCountJsonObject.keys();
        while (allAppearedTexts.hasNext()){
            String crtText = allAppearedTexts.next();
            int count = textToCountJsonObject.getInt(crtText);
            textToCount.put(crtText, count);
        }

        contentToCount = new HashMap<>();
        JSONObject contentToCountJsonObject = mergedNodeJsonObject.getJSONObject("content_to_count");
        Iterator<String> allAppearedContents = contentToCountJsonObject.keys();
        while (allAppearedContents.hasNext()){
            String crtContent = allAppearedContents.next();
            int count = contentToCountJsonObject.getInt(crtContent);
            contentToCount.put(crtContent, count);
        }

        actionTypeToResultNodeIds = new HashMap<>();
        JSONObject actionTypeToResultNodeIdsJsonObject = mergedNodeJsonObject.getJSONObject("action_type_to_result_nodes");
        Iterator<String> actionTypeIterator = actionTypeToResultNodeIdsJsonObject.keys();
        while (actionTypeIterator.hasNext()){
            String crtActionTypeStr = actionTypeIterator.next();
            String[] splitRes = crtActionTypeStr.split("#");
            Utility.assertTrue(splitRes.length == 2 || (splitRes.length == 1 && Integer.valueOf(splitRes[0]) == UIAuto.Action.ENTER_TEXT));
            int actionType = Integer.valueOf(splitRes[0]);
            Object actionAttr;
            switch (actionType){
                case MergedApp.ACTION_ENTER_TEXT:
                    actionAttr = splitRes.length == 2?splitRes[1]: "";
                    break;
                case MergedApp.ACTION_SCROLL:
                    actionAttr = Integer.valueOf(splitRes[1]);
                    break;
                default:
                    actionAttr = null;
            }
            Pair<Integer, Object> actionTypeAndAttr = new Pair<>(actionType, actionAttr);

            List<Long> allResultNodeIds = new ArrayList<>();
            JSONArray allResultNodeIdsJsonArray = actionTypeToResultNodeIdsJsonObject.getJSONArray(crtActionTypeStr);
            for(int i = 0; i < allResultNodeIdsJsonArray.length(); ++ i){
                allResultNodeIds.add(allResultNodeIdsJsonArray.getLong(i));
            }
            actionTypeToResultNodeIds.put(actionTypeAndAttr, allResultNodeIds);
        }

        targetNodeIdToTypeWithInfo = new LongSparseArray<>();
        JSONObject targetNodeIdToTypeWithInfoJsonObject = mergedNodeJsonObject.getJSONObject("target_node_to_type_with_info");
        Iterator<String> targetNodeIdStrIterator = targetNodeIdToTypeWithInfoJsonObject.keys();

        while (targetNodeIdStrIterator.hasNext()){
            String crtTargetNodeIdStr = targetNodeIdStrIterator.next();
            long targetNodeId = Long.valueOf(crtTargetNodeIdStr);
            List<Pair<Pair<Integer, Object>, String>> typeWithInfoList = new ArrayList<>();
            JSONArray typeWithInfoListJsonArray = targetNodeIdToTypeWithInfoJsonObject.getJSONArray(crtTargetNodeIdStr);
            for(int i = 0; i < typeWithInfoListJsonArray.length(); ++ i){
                JSONArray crtTypeWithInfo = typeWithInfoListJsonArray.getJSONArray(i);
                Utility.assertTrue(crtTypeWithInfo.length() == 2);
                JSONArray crtType = crtTypeWithInfo.getJSONArray(0);
                String textInfo = crtTypeWithInfo.getString(1);

                int actionType = crtType.getInt(0);
                Object attr;
                switch (actionType){
                    case MergedApp.ACTION_ENTER_TEXT:
                        attr = crtType.getString(1);
                        break;
                    case MergedApp.ACTION_SCROLL:
                        attr = crtType.getInt(1);
                        break;
                    default:
                        attr = null;
                }
                typeWithInfoList.add(new Pair<>(new Pair<>(actionType, attr), textInfo));
            }

            targetNodeIdToTypeWithInfo.append(targetNodeId, typeWithInfoList);
        }

        sourceNodeAndAction = new HashSet<>();
    }

    public void linkObjectsById(LongSparseArray<MergedRegion> idToMergedRegion, LongSparseArray<MergedNode> idToMergedNode){
        if(parentId == null){
            parent = null;
        } else {
            parent = idToMergedNode.get(parentId);
        }

        childrenNode = new ArrayList<>();
        for(Long id: childrenNodeId){
            childrenNode.add(idToMergedNode.get(id));
        }

        childrenRegion = new ArrayList<>();
        for(Long id: childrenRegionId){
            childrenRegion.add(idToMergedRegion.get(id));
        }

        regionBelongTo = idToMergedRegion.get(regionBelongToId);

        actionTypeToResultNodes = new HashMap<>();
        for(Map.Entry<Pair<Integer, Object>, List<Long>> entry: actionTypeToResultNodeIds.entrySet()){
            List<Long> nodeIds = entry.getValue();
            List<MergedNode> nodes = new ArrayList<>();
            for(Long id: nodeIds){
                nodes.add(idToMergedNode.get(id));
            }

            actionTypeToResultNodes.put(entry.getKey(), nodes);
        }

        targetNodeToTypeWithInfo = new HashMap<>();
        for(int i = 0; i < targetNodeIdToTypeWithInfo.size(); ++ i){
            long id = targetNodeIdToTypeWithInfo.keyAt(i);
            List<Pair<Pair<Integer, Object>, String>> value = targetNodeIdToTypeWithInfo.get(id);
            targetNodeToTypeWithInfo.put(idToMergedNode.get(id), value);
            idToMergedNode.get(id).sourceNodeAndAction.add(new Pair<>(this, value));
        }
    }

    String debug_print_info(){
        StringBuilder builder = new StringBuilder();
        debug_print_info(0, builder);
        return builder.toString();
    }

    void debug_print_info(int depth, StringBuilder builder){
        for(int i = 0; i < depth; ++ i)
            builder.append('\t');
        builder.append(depth).append(' ');
        builder.append(isDynamicEntrance? '+' : '-').append(' ');
        builder.append("resource-id='").append(resourceId == null? "": resourceId).append("' ");
        builder.append("classname='").append(nodeClass).append("' ");
        builder.append("checkable='").append(checkable).append("' ");
        builder.append("clickable='").append(clickable).append("' ");
        builder.append("focusable='").append(focusable).append("' ");
        builder.append("scrollable='").append(scrollable).append("' ");
        builder.append("longclickable='").append(longClickable).append("' ");
        builder.append(id).append('\n');

        if(isDynamicEntrance){
            for(MergedRegion subRegion: childrenRegion){
                subRegion.root.debug_print_info(depth + 1, builder);
            }
        } else {
            for(MergedNode subNode: childrenNode){
                subNode.debug_print_info(depth + 1, builder);
            }
        }
    }

    public boolean isMeaningful(){
        if(childrenNode.size() != 1){
            return true;
        }

        if(isDynamicEntrance){
            return true;
        }

        if(clickable || scrollable || longClickable){
            return true;
        }

        if(resourceId != null && resourceId.length() > 0){
            return true;
        }

        return false;
    }

    public Pair<MergedNode, Integer> moveToMeaningfulChild(){
        MergedNode crtNode = this;
        int countSkipNum = 0;
        while (!crtNode.isMeaningful()){
            countSkipNum += 1;
            countSkipNum += 1;
            crtNode = crtNode.childrenNode.get(0);
        }
        return new Pair<>(crtNode, countSkipNum);
    }

}