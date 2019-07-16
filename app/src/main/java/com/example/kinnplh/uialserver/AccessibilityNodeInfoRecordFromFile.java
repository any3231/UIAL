package com.example.kinnplh.uialserver;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Created by kinnplh on 2019/4/10.
 */

public class AccessibilityNodeInfoRecordFromFile extends AccessibilityNodeInfoRecord {
    public static void buildTreeFromFile(String jsonFilePath) throws IOException, JSONException {
        InputStreamReader inputStreamReader = new InputStreamReader(
                new FileInputStream(jsonFilePath));
        BufferedReader reader = new BufferedReader(inputStreamReader, 5 * 1024);
        char[] buffer = new char[5 * 1024];
        int length;
        StringBuilder builder = new StringBuilder();

        while ((length = reader.read(buffer)) != -1){
            builder.append(buffer, 0, length);
        }

        reader.close();
        inputStreamReader.close();

        JSONObject wholeTree = new JSONObject(builder.toString());
        clearTree();
        AccessibilityNodeInfoRecordFromFile.root = buildSubTreeFromJsonObject(null, 0, wholeTree);
        removeInvisibleChildrenInList(AccessibilityNodeInfoRecord.root);
        AccessibilityNodeInfoRecord.root.refreshIndex(0);
        AccessibilityNodeInfoRecord.root.refreshAbsoluteId();
    }

    public static void buildFromTreeWhole(String jsonFilePath) throws IOException, JSONException{
        InputStreamReader inputStreamReader = new InputStreamReader(
                new FileInputStream(jsonFilePath));
        BufferedReader reader = new BufferedReader(inputStreamReader, 5 * 1024);
        char[] buffer = new char[5 * 1024];
        int length;
        StringBuilder builder = new StringBuilder();

        while ((length = reader.read(buffer)) != -1){
            builder.append(buffer, 0, length);
        }

        reader.close();
        inputStreamReader.close();

        JSONObject wholeTree = new JSONObject(builder.toString());
        clearTree();
        AccessibilityNodeInfoRecordFromFile.root = buildSubTreeFromJsonObject(null, 0, wholeTree);
    }

    public static void removeRedundantNodes(){
        AccessibilityNodeInfoRecord.root.ignoreUselessChild(false);
        removeInvisibleChildrenInList(AccessibilityNodeInfoRecord.root);
        AccessibilityNodeInfoRecord.root.refreshIndex(0);
        AccessibilityNodeInfoRecord.root.refreshAbsoluteId();
    }


    public static AccessibilityNodeInfoRecordFromFile buildSubTreeFromJsonObject(
            AccessibilityNodeInfoRecordFromFile parent, int index, JSONObject uiNodeJsonObject) throws JSONException {
        AccessibilityNodeInfoRecordFromFile crtNode = new AccessibilityNodeInfoRecordFromFile(parent, index);

        crtNode._isClickable = uiNodeJsonObject.getBoolean("@clickable");
        crtNode._isScrollable = uiNodeJsonObject.getBoolean("@scrollable");
        crtNode._isLongClickable = uiNodeJsonObject.getBoolean("@long-clickable");
        crtNode._isEditable = uiNodeJsonObject.getBoolean("@editable");
        crtNode._isCheckable = uiNodeJsonObject.getBoolean("@checkable");
        if (uiNodeJsonObject.has("@text"))
            crtNode._text = uiNodeJsonObject.getString("@text");
        if(uiNodeJsonObject.has("@content-desc"))
            crtNode._contentDescription = uiNodeJsonObject.getString("@content-desc");
        crtNode._className = uiNodeJsonObject.getString("@class");
        if(uiNodeJsonObject.has("@index")){
            crtNode.index = uiNodeJsonObject.getInt("@index");
        }

        crtNode._boundInScreen = new Rect();
        String boundStr = uiNodeJsonObject.getString("@bounds");
        boundStr = boundStr.substring(1, boundStr.length() - 1);
        String [] splitStr = boundStr.split("]\\[");
        String [] split0 = splitStr[0].split(",");
        crtNode._boundInScreen.left = Integer.valueOf(split0[0]);
        crtNode._boundInScreen.top = Integer.valueOf(split0[1]);
        String [] split1 = splitStr[1].split(",");
        crtNode._boundInScreen.right = Integer.valueOf(split1[0]);
        crtNode._boundInScreen.bottom = Integer.valueOf(split1[1]);

        crtNode._isSelected = uiNodeJsonObject.getBoolean("@selected");
        crtNode._packageName = uiNodeJsonObject.getString("@package");
        if(uiNodeJsonObject.has("@resource-id"))
            crtNode._viewIdResourceName = uiNodeJsonObject.getString("@resource-id");
        crtNode._isVisibleToUser = crtNode._boundInScreen.width() >= 0 && crtNode._boundInScreen.height() >= 0;
        crtNode._isFocusable = uiNodeJsonObject.getBoolean("@focusable");
        crtNode._isDismissable = uiNodeJsonObject.getBoolean("@dismissable");

        if(uiNodeJsonObject.has("node")) {
            Object node = uiNodeJsonObject.get("node");
            if (node instanceof JSONArray) {
                JSONArray nodeArray = (JSONArray) node;
                for (int i = 0; i < nodeArray.length(); ++i) {
                    if(nodeArray.getJSONObject(i).length() > 0)
                        crtNode.children.add(AccessibilityNodeInfoRecordFromFile.buildSubTreeFromJsonObject(crtNode, i, nodeArray.getJSONObject(i)));
                }
            } else if (node instanceof JSONObject) {
                crtNode.children.add(AccessibilityNodeInfoRecordFromFile.buildSubTreeFromJsonObject(crtNode, 0, (JSONObject) node));
            } else {
                Utility.assertTrue(false);
            }
        }

        return crtNode;
    }




    // 从文件中载入 ui 树 用来对程序进行验证
    boolean _isClickable;
    boolean _isScrollable;
    boolean _isLongClickable;
    boolean _isEditable;
    boolean _isCheckable;
    CharSequence _text;  // nullable
    CharSequence _contentDescription;  // nullable
    CharSequence _className;
    Rect _boundInScreen;
    boolean _isSelected;
    CharSequence _packageName;
    CharSequence _viewIdResourceName;  // nullable

    boolean _isVisibleToUser;
    boolean _isFocusable;
    boolean _isDismissable;


    AccessibilityNodeInfoRecordFromFile(AccessibilityNodeInfoRecordFromFile parent, int index) {
        super(null, parent, index);
    }

    @Override
    public boolean isClickable() {
        return _isClickable;
    }

    @Override
    public boolean isScrollable() {
        return _isScrollable;
    }

    @Override
    public boolean isLongClickable() {
        return _isLongClickable;
    }

    @Override
    public boolean isEditable() {
        return _isEditable;
    }

    @Override
    public boolean isCheckable() {
        return _isCheckable;
    }

    @Override
    public CharSequence getText() {
        return _text;
    }

    @Override
    public CharSequence getContentDescription() {
        return _contentDescription;
    }

    @Override
    public CharSequence getClassName() {
        return _className;
    }

    @Override
    public void getBoundsInScreen(Rect r) {
        r.left = _boundInScreen.left;
        r.right = _boundInScreen.right;
        r.top = _boundInScreen.top;
        r.bottom = _boundInScreen.bottom;
    }

    @Override
    public boolean isSelected() {
        return _isSelected;
    }

    @Override
    public CharSequence getPackageName() {
        return _packageName;
    }

    @Override
    public CharSequence getViewIdResourceName() {
        return _viewIdResourceName;
    }

    @Override
    public boolean isVisibleToUser() {
        return _isVisibleToUser;
    }

    @Override
    public boolean isFocusable() {
        return _isFocusable;
    }

    @Override
    public boolean isDismissable() {
        return _isDismissable;
    }
}
