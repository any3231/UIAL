package com.example.kinnplh.uialserver;

import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Created by kinnplh on 2019/4/8.
 */

public class AccessibilityNodeInfoRecord {
    public static AccessibilityNodeInfoRecord root = null;
    public static Map<String, AccessibilityNodeInfoRecord> idToRecord = new HashMap<>();

    public static void buildTree(){
        clearTree();
        UIALServer server = UIALServer.self;

        for(AccessibilityWindowInfo w: server.getWindows()){
            if(w.isActive()) {
                System.out.println(w.getTitle());
            }
            w.recycle();
        }

        AccessibilityNodeInfo root = server.getRootInActiveWindow();
        if(root == null){
            Log.i("warning", "buildTree: null root");
            AccessibilityNodeInfoRecord.root = null;
            return;
        }
        AccessibilityNodeInfoRecord.root = new AccessibilityNodeInfoRecord(root, null, 0);
        AccessibilityNodeInfoRecord.root.ignoreUselessChild(false);
        removeInvisibleChildrenInList(AccessibilityNodeInfoRecord.root);
        AccessibilityNodeInfoRecord.root.refreshIndex(0);
        AccessibilityNodeInfoRecord.root.refreshAbsoluteId();
    }


    public static void removeInvisibleChildrenInList(AccessibilityNodeInfoRecord crtNode){
        if(crtNode.isScrollable() || crtNode.getClassName().toString().contains("RecyclerView") || crtNode.getClassName().toString().contains("GridView")){
            for(int i = crtNode.getChildCount() - 1; i >= 0; -- i){
                Rect r = new Rect();
                crtNode.getChild(i).getBoundsInScreen(r);
                if(r.height() <= 0 || r.width() <= 0){
                    if(crtNode.getChild(i).absoluteId != null && idToRecord.containsKey(crtNode.getChild(i).absoluteId)){
                        idToRecord.remove(crtNode.getChild(i).absoluteId);
                    }
                    clearSubTree(crtNode.getChild(i));
                    crtNode.children.remove(i);
                }
            }
        }
        for(AccessibilityNodeInfoRecord child: crtNode.children){
            removeInvisibleChildrenInList(child);
        }

    }

    public static void clearTree(){
        idToRecord.clear();
        clearSubTree(root);
        root = null;
    }

    private static void clearSubTree(AccessibilityNodeInfoRecord record){
        if(record == null){
            return;
        }
        for(AccessibilityNodeInfoRecord child: record.children){
            clearSubTree(child);
        }
        if(record.nodeInfo != null) {
            record.nodeInfo.recycle();
        } else {
            Utility.assertTrue(record instanceof AccessibilityNodeInfoRecordFromFile);
        }
        record.children.clear();
        record.parent = null;
    }


    public AccessibilityNodeInfo nodeInfo;
    public List<AccessibilityNodeInfoRecord> children;
    public AccessibilityNodeInfoRecord parent;
    public int index;
    public String absoluteId;
    public boolean isImportant;
    private List<AccessibilityNodeInfoRecord> uselessChildren;

    public String allTexts;
    public String allContents;


    AccessibilityNodeInfoRecord(AccessibilityNodeInfo nodeInfo, AccessibilityNodeInfoRecord parent, int index) {
        this.nodeInfo = nodeInfo;
        if(this.nodeInfo == null){
            Log.i("warning", "AccessibilityNodeInfoRecord: null node info");
        }
        this.children = new ArrayList<>();
        this.uselessChildren = new ArrayList<>();
        this.parent = parent;
        this.index = index;
        if(nodeInfo != null) {
            for (int i = 0; i < nodeInfo.getChildCount(); ++i) {
                AccessibilityNodeInfo crtNode = nodeInfo.getChild(i);
                if (crtNode == null) {
                    continue;
                }
                children.add(new AccessibilityNodeInfoRecord(crtNode, this, i));

            }
        }
    }

    public boolean ignoreUselessChild(boolean isForceUseless){
        if(getClassName() != null && getClassName().toString().contains("WebView")){
            // 删除所有的 webview
            children.clear();
            return true;
        }


        isImportant = false;
        //boolean isRefresh = getViewIdResourceName() != null && Objects.equals("uik_refresh_header", getViewIdResourceName().toString());
        boolean isRefresh = false;
        for(AccessibilityNodeInfoRecord child: children){
            if(child.ignoreUselessChild(isRefresh)){
                isImportant = true;
            }
        }

        if(!isImportant){
            isImportant = isClickable() || isCheckable() || isScrollable() || isEditable()
                    || isLongClickable() || (getText() != null && getText().length() > 0)
                    || (getContentDescription() != null && getContentDescription().length() > 0);
        }

        isImportant = isImportant && !isForceUseless && !isRefresh;
        // 把所有不重要的节点从 children 里转移到 uselessChild 里
        uselessChildren.addAll(children);
        for(AccessibilityNodeInfoRecord child: children){
            if(child.isImportant){
                uselessChildren.remove(child);
            }
        }

        children.removeAll(uselessChildren);

        return isImportant;
    }


    public void refreshIndex(int newIndex){
        // 修改了树之后进行更新
        index = newIndex;
        for(int i = 0; i < getChildCount(); ++ i){
            getChild(i).refreshIndex(i);
        }
    }

    public void refreshAbsoluteId(){
        if(absoluteId != null){
            AccessibilityNodeInfoRecord.idToRecord.remove(absoluteId);
        }
        if(parent == null){
            absoluteId = getClassName().toString();
        } else {
            absoluteId = parent.absoluteId + "|" + String.valueOf(index) + ";" + getClassName().toString();
        }
        AccessibilityNodeInfoRecord.idToRecord.put(absoluteId, this);
        for(AccessibilityNodeInfoRecord child: children){
            child.refreshAbsoluteId();
        }
    }

    public AccessibilityNodeInfoRecord getParent(){
        return parent;
    }

    public boolean isClickable(){
        return nodeInfo.isClickable();
    }

    public boolean isScrollable(){
        return nodeInfo.isScrollable();
    }

    public boolean isLongClickable(){
        return nodeInfo.isLongClickable();
    }

    public boolean isEditable(){
        return nodeInfo.isEditable();
    }

    public boolean isCheckable(){
        return nodeInfo.isCheckable();
    }

    public int getChildCount(){
        return children.size();
    }

    public AccessibilityNodeInfoRecord getChild(int index){
        return children.get(index);
    }

    public CharSequence getText(){
        return nodeInfo.getText();
    }

    public CharSequence getContentDescription(){
        return nodeInfo.getContentDescription();
    }

    public boolean performAction(int action){
        return nodeInfo.performAction(action);
    }

    public boolean performAction(int action, Bundle info){
        return nodeInfo.performAction(action, info);
    }

    public CharSequence getClassName(){
        if (nodeInfo != null)
            return nodeInfo.getClassName();
        return null;
    }

    public AccessibilityWindowInfo getWindow(){
        return nodeInfo.getWindow();
    }

    public void getBoundsInScreen(Rect r){
        nodeInfo.getBoundsInScreen(r);
    }

    public List<AccessibilityNodeInfoRecord> findAccessibilityNodeInfosByText(String str){
        List<AccessibilityNodeInfoRecord> res = new ArrayList<>();
        if(Objects.equals(getText().toString(), str)){
            res.add(this);
        }

        for(AccessibilityNodeInfoRecord child: children){
            res.addAll(child.findAccessibilityNodeInfosByText(str));
        }

        return res;
    }

    public boolean isSelected(){
        return nodeInfo.isSelected();
    }

    public CharSequence getPackageName(){
        return nodeInfo.getPackageName();
    }

/*    public int getDrawingOrder(){
        return nodeInfo.getDrawingOrder();
    }*/

    public CharSequence getViewIdResourceName(){
        return nodeInfo.getViewIdResourceName();
    }

    public boolean isVisibleToUser(){
        return nodeInfo.isVisibleToUser();
    }

    public boolean isFocusable(){
        return nodeInfo.isFocusable();
    }

    public boolean isDismissable(){
        return nodeInfo.isDismissable();
    }

    public AccessibilityNodeInfo getNodeInfo(){
        return nodeInfo;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        if(this.nodeInfo != null && ((AccessibilityNodeInfoRecord) obj).nodeInfo != null){
            return this.nodeInfo.equals(((AccessibilityNodeInfoRecord) obj).nodeInfo);
        } else if(this.nodeInfo == null && ((AccessibilityNodeInfoRecord) obj).nodeInfo == null){
            return this == obj;
        } else {
            return false;
        }

    }

    public int getIndex(){
        return index;
    }

    public List<AccessibilityNodeInfoRecord> getChildren() {
        return children;
    }

    public String debug_print_info(int depth, Map<AccessibilityNodeInfoRecord, Pair<FitResultRegion, MergedNode>> info){
        // '%sresource-id=\'%s\' classname=\'%s\' index=\'%d\' text=\'%s\' content=\'%s\' bound=\'[%d, %d, %d, %d]\' dismissable=\'%s\' clickable=\'%s\'  scrollable=\'%s\' \n' \
        Rect r = new Rect();
        getBoundsInScreen(r);

        String depthTab = "";
        for(int i = 0; i < depth; ++ i){
            depthTab += "\t";
        }
        char fitResChar = '?';
        String mergedNodeId = "";
        if(info != null){
            if(info.containsKey(this)){
                fitResChar = '=';
                mergedNodeId = String.valueOf(info.get(this).second.id);
            } else {
                fitResChar = '!';
            }
        }

        String res = String.format(Locale.CHINA, "%s %c %d resource-id='%s' classname='%s' index='%d' text='%s' content='%s' bound='[%d, %d, %d, %d]' dismissable='%s' clickable='%s'  scrollable='%s' %s\n",
                depthTab, fitResChar, depth, getViewIdResourceName() == null? "": getViewIdResourceName().toString(),
                getClassName().toString(),
                getIndex(),
                getText() == null? "": getText().toString(),
                getContentDescription() == null? "": getContentDescription().toString(),
                r.left, r.top, r.right, r.bottom,
                String.valueOf(isDismissable()), String.valueOf(isClickable()), String.valueOf(isScrollable()), mergedNodeId);
        for(AccessibilityNodeInfoRecord child: children){
            res += child.debug_print_info(depth + 1, info);
        }
        return res;
    }

    public String debug_print_info(){
        return debug_print_info(0, null);
    }
    public String debug_print_info(Map<AccessibilityNodeInfoRecord, Pair<FitResultRegion, MergedNode>> info){
        return debug_print_info(0, info);
    }

    public List<AccessibilityNodeInfoRecord> findNodeByViewIdResourceName(String str){
        List<AccessibilityNodeInfoRecord> res = new ArrayList<>();
        String crtId = getViewIdResourceName() == null? "": getViewIdResourceName().toString();
        if(Objects.equals(crtId, str)){
            res.add(this);
        }
        for(AccessibilityNodeInfoRecord child: children){
            res.addAll(child.findNodeByViewIdResourceName(str));
        }
        return res;

    }

    public boolean isMeaningful(){
        if(isCheckable() || isEditable() || isScrollable() || isLongClickable() || isClickable()){
            return true;
        }
        if(getText() != null && getText().length() > 0){
            return true;
        }
        if(getContentDescription() != null && getContentDescription().length() > 0){
            return true;
        }
        if(getViewIdResourceName() != null && getViewIdResourceName().length() > 0){
            return true;
        }
        if(children.size() != 1){
            return true;
        }
        return false;
    }

    public Pair<AccessibilityNodeInfoRecord, Integer> moveToMeaningfulChild(){
        AccessibilityNodeInfoRecord crtNode = this;
        int countSkipNum = 0;
        while (!crtNode.isMeaningful()){
            countSkipNum += 1;
            crtNode = crtNode.getChild(0);
        }
        return new Pair<>(crtNode, countSkipNum);
    }

    public String getAllTexts() {
        if (allTexts != null)
            return allTexts;
        allTexts = getText() == null? "": getText().toString();
        for(AccessibilityNodeInfoRecord child: children){
            allTexts += child.getAllTexts();
        }
        return allTexts;
    }

    public String getAllContents(){
        if (allContents != null)
            return allContents;
        allContents = getContentDescription() == null? "": getContentDescription().toString();
        for(AccessibilityNodeInfoRecord child: children){
            allContents += child.getAllContents();
        }
        return allContents;
    }

    public AccessibilityNodeInfoRecord getNodeByRelativeId(String relativeId){
        String[] subIdList = relativeId.split(";");
        AccessibilityNodeInfoRecord crtNode = this;
        for(int i = 0; i < subIdList.length - 1; ++ i){
            String subId = subIdList[i];
            String[] subIdSplited = subId.split("\\|");
            if(!crtNode.getClassName().toString().equals(subIdSplited[0])){
                return null;
            }

            int intendedIndex = Integer.valueOf(subIdSplited[1]);
            AccessibilityNodeInfoRecord targetChild = null;
            for(AccessibilityNodeInfoRecord child: crtNode.children){
                if (child.index == intendedIndex){
                    targetChild = child;
                    break;
                }
            }

            if(targetChild == null){
                return null;
            }
            crtNode = targetChild;
        }
        if(!crtNode.getClassName().toString().equals(subIdList[subIdList.length - 1])){
            return null;
        }
        return crtNode;
    }
}
