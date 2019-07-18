package com.example.kinnplh.uialserver;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.util.Log;
import android.util.Pair;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.function.LongUnaryOperator;

public class UIAuto {
    public static class Action{
        public static final int CLICK = 1;
        public static final int LONG_CLICK = 2;
        public static final int SCROLL = 3;
        public static final int ENTER_TEXT = 4;
        public static final int GLOBAL_BACK = 5;

        public MergedNode actionNode;
        public int actionType;
        public Object actionAttr;

        public Action(MergedNode node, int type, Object attr){
            actionNode = node;
            actionType = type;
            actionAttr = attr;
            if(actionType == CLICK || actionType == LONG_CLICK || actionType == GLOBAL_BACK){
                Utility.assertTrue(actionAttr == null);
            } else if(actionType == SCROLL){
                Utility.assertTrue(actionAttr != null && actionAttr instanceof Integer);
            } else if(actionType == ENTER_TEXT){
                Utility.assertTrue(actionAttr != null && actionAttr instanceof String);
            }
        }

        @Override
        public boolean equals(Object obj) {
            if(obj == null){
                return false;
            }
            if(!(obj instanceof Action)){
                return false;
            }
            Action in = (Action)obj;
            return actionNode == in.actionNode && actionType == in.actionType && (actionAttr == in.actionAttr || actionType != Action.SCROLL);
        }

        @Override
        public int hashCode() {
            int code =  actionNode.hashCode() + ((Integer)actionType).hashCode();
            if(actionAttr != null && actionType == Action.SCROLL){
                code += actionAttr.hashCode();
            }
            return code;
        }
    }

    public static class AttributeConstrain{
        public Boolean isClickable;
        public Boolean isScrollable;
        public Boolean isEditable;
        public Boolean isLongClickable;
        public String viewIdResourceName;
        public String className;

        // 上面的这些参数，在进行动静区域划分的时候已经设置好了，理论上不应该再作为相关的限制
        public Set<MergedNode> targetNodes;

        // 下面的这些参数，不仅仅针对当前的节点，还包括子节点了
        public String containedTexts;
        public String containedDescs;

        public AttributeConstrain(){
            targetNodes = new HashSet<>();
            isClickable = null;
            isScrollable = null;
            isEditable = null;
            isLongClickable = null;
            viewIdResourceName = null;  // 如果要求没有resourceId 的话，需要被设置成空字符串
            className = null;

            containedDescs = null;  // 这边应该是需要以上下文的形式进行提供的
            containedTexts = null;  // 这里的null都意味着不进行限制
            // 其他相关的参数请手动进行设置
        }


        public Pair<Boolean, List<String>> canMatchUINodes(AccessibilityNodeInfoRecord uiNode){
            return canMatchUINodes(uiNode, null);
        }

        public Pair<Boolean, List<String>> canMatchUINodes(AccessibilityNodeInfoRecord uiNode, MergedNode mgNode){
            // false, null  无法匹配
            // true，null 成功匹配
            // false，not-null 暂时无法匹配；如果text包含第二个参数的话，才有可能匹配
            if(mgNode != null && !mgNode.canMatchUINode(uiNode)){
                Utility.assertTrue(false);
            }

            if(isClickable != null){
                if(isClickable != uiNode.isClickable()){
                    return new Pair<>(false, null);
                }
            }

            if(isScrollable != null){
                if(isScrollable != uiNode.isScrollable()){
                    return new Pair<>(false, null);
                }
            }

            if(isEditable != null){
                if(isEditable != uiNode.isEditable()){
                    return new Pair<>(false, null);
                }
            }

            if(isLongClickable != null){
                if(isLongClickable != uiNode.isLongClickable()){
                    return new Pair<>(false, null);
                }
            }

            if(viewIdResourceName != null){
                String uiNodeResourceId = uiNode.getViewIdResourceName() == null? "": uiNode.getViewIdResourceName().toString();
                if(!Objects.equals(uiNodeResourceId, viewIdResourceName)){
                    return new Pair<>(false, null);
                }
            }

            if (className != null){
                if(!Objects.equals(className, uiNode.getClassName().toString())){
                    return new Pair<>(false, null);
                }
            }

            if (containedDescs != null){
                if(!uiNode.getAllContents().contains(containedDescs)){
                    return new Pair<>(false, null);
                }
            }

            if (containedTexts != null){
                if(!uiNode.getAllTexts().contains(containedTexts)){
                    return new Pair<>(false, null);
                }
            }

            if(targetNodes.size() > 0){
                if(mgNode == null){
                    return new Pair<>(false, null);
                }

                for (MergedNode targetNode: targetNodes){
                    if(!mgNode.targetNodeToTypeWithInfo.keySet().contains(targetNode)){
                        return new Pair<>(false, null);
                    }

                    List<Pair<Pair<Integer, Object>, String>> allPossibleActions = mgNode.targetNodeToTypeWithInfo.get(targetNode);
                    boolean appearedInCollectedData = false;
                    for(String collectedStr: mgNode.allTexts){
                        if(collectedStr.contains(uiNode.getAllTexts()) || uiNode.getAllTexts().contains(collectedStr)){
                            appearedInCollectedData = true;
                            break;
                        }
                    }

                    if(allPossibleActions.size() / (float)mgNode.allTexts.size() > 0.5 && mgNode.allTexts.size() > 2 && !appearedInCollectedData){
                        // 说明有四分之三的节点都能到  我们就不具体在意是什么节点了
                        // 并且没有在已经有的数据中出现过。。。
                        return new Pair<>(true, null);
                    }
                    boolean hasFound = false;
                    List<String> allTextsCanRes = new ArrayList<>();
                    for(Pair<Pair<Integer, Object>, String> action: allPossibleActions){
                        if(action.second.contains("#TL#") || action.second.contains("#NI#") || uiNode.getAllTexts().contains(action.second) || action.second.contains(uiNode.getAllTexts())){
                            hasFound = true;
                            break;
                        } else {
                            allTextsCanRes.add(action.second);
                        }
                    }

                    if(!hasFound){
                        return new Pair<>(false, allTextsCanRes);
                    }
                }
            }

            return new Pair<>(true, null);
        }
    }

    public static class TargetFromFile{
        int pageIndex;
        int stateIndex;
        String targetNodeToClickStr;

        public TargetFromFile(int pIndex, int sIndex, String str){
            pageIndex = pIndex;
            stateIndex = sIndex;
            targetNodeToClickStr = str;
        }
    }



    public static List<AccessibilityNodeInfoRecord> getSatisfiedNodes(List<AccessibilityNodeInfoRecord> uiNodes, MergedNode mgNode, AttributeConstrain constrain){
        // 这里的目的是确定一个（返回长度为1的列表）；或者要求程序通过自己的方式进行选择（返回长度大于1的列表）。不保证返回的数据一定是能够匹配的
        if(uiNodes.size() == 0 || uiNodes.size() == 1){
            return new ArrayList<>(uiNodes);
        }

        List<AccessibilityNodeInfoRecord> canMatch = new ArrayList<>();
        List<AccessibilityNodeInfoRecord> canMatchOnlyWithSpecificString = new ArrayList<>();
        List<AccessibilityNodeInfoRecord> cannotMatch = new ArrayList<>();

        for (AccessibilityNodeInfoRecord crtNode: uiNodes){
            Pair<Boolean, List<String>> res = constrain.canMatchUINodes(crtNode, mgNode);
            if(res.first){
                canMatch.add(crtNode);
            } else if(res.second != null){
                canMatchOnlyWithSpecificString.add(crtNode);
            } else {
                cannotMatch.add(crtNode);
            }
        }
        if(canMatch.size() > 0){
            return canMatch;
        } else if(canMatchOnlyWithSpecificString.size() > 0){
            return canMatchOnlyWithSpecificString;
        }
        return cannotMatch;

    }

    public static List<Action> findActionListFromSrcToTarget(FitResultRegion src, MergedNode target, Set<Action> triedActions){
        List<MergedNode> targets = new ArrayList<>();
        targets.add(target);
        Pair<List<Action>, MergedNode> res = findActionListFromSrcToTarget(src, targets, triedActions);
        if(res == null)
            return null;
        return res.first;
    }

    public static List<Action> findActionListFromSrcToTarget(FitResultRegion src, MergedRegion target, Set<Action> triedActions){
        return findActionListFromSrcToTarget(src, target.root, triedActions);
    }
    public static Pair<List<Action>, MergedNode> findActionListFromSrcToTarget(FitResultRegion src, List<MergedNode> targets, Set<Action> triedActions){
        // 和python不一样的是，对文本的限制是在运行时才确定的事情
        Set<Action> visitedAction = new HashSet<>(triedActions);
        Deque<Pair<Action, List<Action>>> searchDeque = new LinkedList<>();

        List<MergedNode> allSrcMergedNodes = src.getAllMergedNodes();
        for(MergedNode node: allSrcMergedNodes){
            if(targets.contains(node)){
                return new Pair<List<Action>, MergedNode>(new ArrayList<Action>(), node);
            }
            Set<Action> actionsForNode = getAllPossibleActionFromNode(node);
            for(Action crtAction: actionsForNode){
                if(visitedAction.contains(crtAction)){
                    continue;
                }

                visitedAction.add(crtAction);
                searchDeque.addLast(new Pair<Action, List<Action>>(crtAction, new ArrayList<Action>()));
            }
        }

        while (!searchDeque.isEmpty()){
            Pair<Action, List<Action>> crt = searchDeque.pollFirst();
            if(triedActions.contains(crt.first)){
                continue;
            }
            List<MergedNode> resultNodes = crt.first.actionNode.actionTypeToResultNodes.get(new Pair<>(crt.first.actionType, crt.first.actionAttr));
            for(MergedNode node: resultNodes){
                Set<Action> actionsForNodes = getAllPossibleActionFromNode(node);
                List<Action> crtActionList = new ArrayList<>(crt.second);
                crtActionList.add(crt.first);

                boolean isAllBack = true;
                for(Action inList: crtActionList){
                    if(inList.actionType != Action.GLOBAL_BACK){
                        isAllBack = false;
                        break;
                    }
                }
                if(crt.first.actionType == Action.GLOBAL_BACK && !isAllBack){
                    continue;
                }

                if(targets.contains(node)){
                    return new Pair<>(crtActionList, node);
                }

                for(Action crtAction: actionsForNodes){
                    /*if(crtAction.actionNode == target){  // ??? crtAction.actionNode 不就是 node 吗  // 会导致node要是没有被操作就无法正确地找到跳转路径
                        return  crtActionList;
                    }*/

                    if(visitedAction.contains(crtAction)){
                        continue;
                    }

                    visitedAction.add(crtAction);
                    searchDeque.addLast(new Pair<>(crtAction, crtActionList));
                }
            }
        }

        return null;  // 表示没有找到对应的节点
    }

    public static Pair<List<Action>, MergedNode> twoDirectionalFindActionListFromSrcToTarget(FitResultRegion src, List<MergedNode> targets, Set<Action> triedActions){
        // 单纯地用来进行剪枝
        Set<Action> visitedActionsFromSrc = new HashSet<>(triedActions);
        Set<Action> visitedActionsFromDes = new HashSet<>(triedActions);

        // 用来记录所有从起点出发能够到达的节点，以及到达所需要的路径
        Map<MergedNode, List<Action>> nodeFromSrcToPath = new HashMap<>();
        // 记录所有能够到达终点的节点，以及从这个节点到终点的路径，以及所到达的最终的节点是什么
        Map<MergedNode, Pair<List<Action>, MergedNode>> nodeReachDesToPath = new HashMap<>();

        List<MergedNode> allSrcMergedNodes = src.getAllMergedNodes();
        for(MergedNode nodeInSrc: allSrcMergedNodes){
            nodeFromSrcToPath.put(nodeInSrc, new ArrayList<Action>());
        }

        for(MergedNode targetNode: targets){
            nodeReachDesToPath.put(targetNode, new Pair<List<Action>, MergedNode>(new ArrayList<Action>(), targetNode));
        }

        Deque<MergedNode> nodeFromSrcDeque = new LinkedList<>(allSrcMergedNodes);
        Deque<MergedNode> nodeReachDesDeque = new LinkedList<>(targets);
        while (true){
            Set<MergedNode> nodesFromSrc = nodeFromSrcToPath.keySet();
            Set<MergedNode> nodesReachDes = nodeReachDesToPath.keySet();

            for(MergedNode nodeFS: nodesFromSrc){
                if(nodesReachDes.contains(nodeFS)){
                    MergedNode finalTarget = nodeReachDesToPath.get(nodeFS).second;
                    List<Action> searchedPath = new ArrayList<>(nodeFromSrcToPath.get(nodeFS));
                    searchedPath.addAll(nodeReachDesToPath.get(nodeFS).first);

                    boolean notBackMeet = false;
                    boolean isValid = true;
                    for(Action a: searchedPath){
                        if(a.actionType == Action.GLOBAL_BACK){
                            if(notBackMeet){
                                isValid = false;
                                break;
                            }
                        } else {
                            notBackMeet = true;
                        }
                    }
                    if(!isValid){
                        continue;
                    }
                    return new Pair<>(searchedPath, finalTarget);
                }
            }

            if(nodeFromSrcDeque.isEmpty() && nodeReachDesDeque.isEmpty()){
                break;
            }

            if(!nodeFromSrcDeque.isEmpty()){
                MergedNode nodeToExtend = nodeFromSrcDeque.pollFirst();
                for(Map.Entry<Pair<Integer, Object>, List<MergedNode>> entry: nodeToExtend.actionTypeToResultNodes.entrySet()){
                    UIAuto.Action crtAction = new Action(nodeToExtend, entry.getKey().first, entry.getKey().second);
                    if(visitedActionsFromSrc.contains(crtAction)){
                        continue;
                    } else {
                        visitedActionsFromSrc.add(crtAction);
                    }

                    List<Action> newActions = new ArrayList<>(nodeFromSrcToPath.get(nodeToExtend));
                    newActions.add(crtAction);
                    for(MergedNode extendedResNode: entry.getValue()){
                        if(nodeFromSrcToPath.containsKey(extendedResNode)){
                            continue;
                        }
                        nodeFromSrcToPath.put(extendedResNode, newActions);
                        // 把数据加入到对应到队列中去
                        nodeFromSrcDeque.addLast(extendedResNode);
                    }
                }
            }

            if(!nodeReachDesDeque.isEmpty()){
                MergedNode nodeToBackwardExtend = nodeReachDesDeque.pollFirst();
                for(Pair<MergedNode, List<Pair<Pair<Integer, Object>, String>>> reachInfo: nodeToBackwardExtend.sourceNodeAndAction){
                    if(nodeReachDesToPath.containsKey(reachInfo.first)){
                        continue;
                    }

                    for(Pair<Pair<Integer, Object>, String> actionInfo: reachInfo.second){
                        UIAuto.Action crtAction = new Action(reachInfo.first, actionInfo.first.first, actionInfo.first.second);
                        if(visitedActionsFromDes.contains(crtAction)){
                            continue;
                        } else {
                            visitedActionsFromDes.add(crtAction);
                        }

                        List<Action> newActions = new ArrayList<>();
                        newActions.add(crtAction);
                        newActions.addAll(nodeReachDesToPath.get(nodeToBackwardExtend).first);
                        nodeReachDesToPath.put(reachInfo.first, new Pair<>(newActions, nodeReachDesToPath.get(nodeToBackwardExtend).second));
                        nodeReachDesDeque.addLast(reachInfo.first);
                        break;
                    }
                }
            }

        }

        return null;
    }

    private static Set<Action> getAllPossibleActionFromNode(MergedNode mgNode){
        Set<Action> res = new HashSet<>();
        for(Map.Entry<MergedNode, List<Pair<Pair<Integer, Object>, String>>> entry: mgNode.targetNodeToTypeWithInfo.entrySet()){
            for(Pair<Pair<Integer, Object>, String> actionInfo: entry.getValue()){
                Action crtAction = new Action(mgNode, actionInfo.first.first, actionInfo.first.second);
                res.add(crtAction);
                Utility.assertTrue(mgNode.actionTypeToResultNodes.get(actionInfo.first).contains(entry.getKey()));
            }
        }
        return res;
    }

    public interface GetContextValue{
        AccessibilityNodeInfoRecord getUINode(
                 MergedNode crtFocusMgNode, List<AccessibilityNodeInfoRecord> candidates);
    }

    @WorkerThread
    public static Pair<List<Action>, Boolean> execActions(List<Action> actions,
                                                          List<String> contexts,
                                                          GetContextValue contextValue,
                                                          long maxWaitTimeMs,
                                                          MergedNode finalTarget) {
        return execActions(actions, contexts, contextValue, maxWaitTimeMs, finalTarget, false);
    }
    @WorkerThread
    public static Pair<List<Action>, Boolean> execActions(List<Action> actions,
                                                          List<String> contexts,
                                                          GetContextValue contextValue,
                                                          long maxWaitTimeMs,
                                                          MergedNode finalTarget, boolean contextRemoveEveryStep){
        Utility.assertTrue(!Utility.isMainThread()); // 不允许在主线程中调用！！
        List<Action> actedActions = new ArrayList<>();
        List<String> inputContexts = new ArrayList<>(contexts);

        boolean canTryScrollIfContextNotMatched = true;

        for(int i = 0; i < actions.size(); ++ i) {
            Action crtAction = actions.get(i);
            if(crtAction == null){
                Log.i("failed", "execActions: null action");
                return new Pair<>(actedActions, false);
            }

            long startTime = System.currentTimeMillis();
            boolean reachedTargetNode = false;
            List<AccessibilityNodeInfoRecord> uiNodesOfMgNode = null;
            Pair<Pair<Pair<MergedState, FitResultRegion>, Float>, Map<AccessibilityNodeInfoRecord, Pair<FitResultRegion, MergedNode>>> fitResult = null;
            while (System.currentTimeMillis() - startTime <= maxWaitTimeMs && !reachedTargetNode) {
                AccessibilityNodeInfoRecord.buildTree();
                if(AccessibilityNodeInfoRecord.root == null){
                    return new Pair<>(actedActions, false);
                }
                String crtPackageName = AccessibilityNodeInfoRecord.root.getPackageName().toString();
                MergedApp app = UIALServer.self.packageToMergedApp.get(crtPackageName);
                if(app == null){
                    Log.i("failed", "execActions: into unknown app. backing to wechat");
                    UIAuto.jumpToApp("com.tencent.mm");
                    return new Pair<>(actedActions, false);
                }
                List<MergedNode> oneNodeList = new ArrayList<>();
                oneNodeList.add(crtAction.actionNode);

                fitResult = FitResultRegion.getFitResult(app, AccessibilityNodeInfoRecord.root, oneNodeList);
                if(fitResult == null){
                    continue;
                }
                Log.i("fitResult", String.format(Locale.CHINA, "%d-%d %f",
                        fitResult.first.first.first.pageBelongTo.pageIndex, fitResult.first.first.first.stateIndex, 1 - fitResult.first.second));
                FitResultRegion coreRes = fitResult.first.first.second;
                uiNodesOfMgNode = coreRes.getUINodeByMergedNode(crtAction.actionNode);
                if(uiNodesOfMgNode.size() > 0){
                    reachedTargetNode = true;
                }
            }

            int target_state_index = crtAction.actionNode.regionBelongTo.stateBelongsTo.stateIndex;
            int target_page_index = crtAction.actionNode.regionBelongTo.stateBelongsTo.pageBelongTo.pageIndex;

            int crt_state_index = fitResult != null? fitResult.first.first.first.stateIndex: -1;
            int crt_page_index = fitResult != null? fitResult.first.first.first.pageBelongTo.pageIndex: -1;

            Log.i("res", String.format(Locale.CHINA, "want %d-%d, actual %d-%d", target_page_index, target_state_index, crt_page_index, crt_state_index));
            if(target_page_index == crt_page_index && target_state_index == crt_state_index) {
                actedActions.add(crtAction);
            }

            if(!reachedTargetNode){
                Log.i("failed","target node not reached!");
                return new Pair<>(actedActions, false);
            }

            // 如果有多个对应多节点多话，需要在其中选择一个
            AttributeConstrain constrain = new AttributeConstrain();
            if(i != actions.size() - 1){
                constrain.targetNodes.add(actions.get(i + 1).actionNode);
            } else if(finalTarget != null){
                constrain.targetNodes.add(finalTarget);
            }

            List<AccessibilityNodeInfoRecord> filteredNodes = getSatisfiedNodes(uiNodesOfMgNode, crtAction.actionNode, constrain);
            if(filteredNodes.size() == 0){
                Log.i("failed","target node not reached!");
                return new Pair<>(actedActions, false);
            }
            AccessibilityNodeInfoRecord uiNodeToAct = null;
            if(filteredNodes.size() == 1){
                uiNodeToAct = filteredNodes.get(0);
            }

            boolean isContextRemoved = false;
            if(uiNodeToAct == null && inputContexts.size() > 0){
                String crtContextValue = inputContexts.get(0);


                for(AccessibilityNodeInfoRecord uiNode: filteredNodes){
                    if(uiNode.getAllTexts().contains(crtContextValue)){
                        uiNodeToAct = uiNode;
                        inputContexts.remove(0);
                        isContextRemoved = true;
                        System.out.println("context："+crtContextValue);
                        //return new Pair<>(null, false);
                        break;
                    }
                }
                //return new Pair<>(null, false);
                if(!isContextRemoved && canTryScrollIfContextNotMatched){
                    // 如果可以滚动的话，对页面进行滚动
                    canTryScrollIfContextNotMatched = false;
                    AccessibilityNodeInfoRecord scrollableParent = Utility.getScrollableParent(filteredNodes.get(0));
                    if(scrollableParent != null){
                        boolean tryScrollForward = scrollableParent.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                        if(tryScrollForward){
                            i -= 1;
                            continue;
                        }

                        boolean tryScrollBackward = scrollableParent.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
                        if(tryScrollBackward){
                            i -= 1;
                            continue;
                        }
                    }
                }
            }

            canTryScrollIfContextNotMatched = true;
            if(contextRemoveEveryStep && !isContextRemoved && inputContexts.size() > 0){
                inputContexts.remove(0);
            }

            if(uiNodeToAct == null && contextValue != null){
                uiNodeToAct = contextValue.getUINode(crtAction.actionNode, filteredNodes);
            }

            if(uiNodeToAct == null){
                // uiNodeToAct = filteredNodes.get(0);
                Log.i("failed", "context not filled");
                return new Pair<>(actedActions, false);
            }

            if(crtAction.actionType == Action.ENTER_TEXT && !contextRemoveEveryStep){
                if(((String)(crtAction.actionAttr)).length() > 0) {
                    String textToEnter = null;
                    if (contexts.size() > 0) {
                        textToEnter = contexts.remove(contexts.size() - 1);
                    } else {
                        textToEnter = "的";
                    }
                    crtAction.actionAttr = textToEnter;
                }
            }

            boolean actionResult = performAction(uiNodeToAct, crtAction);
            if(!actionResult){
                Log.i("failed", "perform action failed");
                return new Pair<>(actedActions, false);
            }
        }
        return new Pair<>(actedActions, true);
    }


    public static boolean performAction(AccessibilityNodeInfoRecord uiNode, @NonNull Action action){
        //Utility.assertTrue(action.actionNode.canMatchUINode(uiNode));
        boolean result = false;
        if(action.actionType == Action.GLOBAL_BACK){
            Log.i("info", "performAction: global back");
            result = UIALServer.self.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
        } else if(action.actionType == Action.CLICK){
            Log.i("info", "performAction: click");
            result = uiNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        } else if(action.actionType == Action.SCROLL){
            // todo 暂时不处理连续滚动的情况
            Log.i("info", "performAction: scroll " + action.actionAttr);
            result = uiNode.performAction(((Integer) action.actionAttr) > 0? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD: AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
        } else if(action.actionType == Action.ENTER_TEXT){
            Log.i("info", "performAction: enter text " + action.actionAttr);
            Bundle argument = new Bundle();
            argument.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, (String)action.actionAttr);
            result = uiNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, argument);
        }
        Log.i("info", "res " + result + " " + uiNode.getAllTexts() + " " + uiNode.getAllContents());
        return result;
    }

    public static Pair<List<Action>, List<String>> generateActionFromDir(String rootDirPath) throws IOException, JSONException {
        List<Action> resActionList = new ArrayList<>();
        List<String> resContextList = new ArrayList<>();

        File rootDir = new File(rootDirPath);
        int countDirNum = 0;
        for(File subF: rootDir.listFiles()){
            if(subF.isDirectory()){
                countDirNum += 1;
            }
        }
        for(int i = 0; i < countDirNum; ++ i){
            String crtRoot = rootDirPath + i + "/";
            String nodeToActionTextFile = crtRoot + "important_ids.txt";
            String layoutJsonFile = crtRoot + "layout.json";

            InputStreamReader inputStreamReader = new InputStreamReader(new FileInputStream(nodeToActionTextFile));
            BufferedReader reader = new BufferedReader(inputStreamReader);
            String importantNodeId = reader.readLine();
            String info = reader.readLine();
            reader.close();

            String[] infoSplitRes = info.split(" ");
            int actionType = Action.CLICK;
            switch (infoSplitRes[0]){
                case "CLICK":
                    actionType = Action.CLICK;
                    break;
                case "LONG_CLICK":
                    actionType = Action.LONG_CLICK;
                    break;
                case "SCROLL":
                    actionType = Action.SCROLL;
                    break;
                case "ENTER_TEXT":
                    actionType = Action.ENTER_TEXT;
                    break;
                case "GLOBAL_BACK":
                    actionType = Action.GLOBAL_BACK;
                    break;
                default:
                    Utility.assertTrue(false);
            }

            String contextInfo = infoSplitRes.length >= 2? infoSplitRes[1]: null;


            // 判断是什么页面
            AccessibilityNodeInfoRecordFromFile.buildFromTreeWhole(layoutJsonFile);
            AccessibilityNodeInfoRecord nodeToAct = AccessibilityNodeInfoRecordFromFile.root.getNodeByRelativeId(importantNodeId);
            if(nodeToAct == null){
                Log.i("failed", "node not found in ori page");
                return null;
            }

            // 确定了节点之后就可以将无用的节点删掉！
            AccessibilityNodeInfoRecordFromFile.removeRedundantNodes();

            String crtPackageName = AccessibilityNodeInfoRecord.root.getPackageName().toString();
            MergedApp app = UIALServer.self.packageToMergedApp.get(crtPackageName);
            if(app == null){
                return null;
            }
            Pair<Pair<Pair<MergedState, FitResultRegion>, Float>, Map<AccessibilityNodeInfoRecord, Pair<FitResultRegion, MergedNode>>> res = FitResultRegion.getFitResult(app, AccessibilityNodeInfoRecordFromFile.root);
            if(res == null){
                return null;
            }

            Map<AccessibilityNodeInfoRecord, Pair<FitResultRegion, MergedNode>> fitResForUINode = res.second;
            if(!fitResForUINode.containsKey(nodeToAct)){
                Log.i("failed", "node not found after merge");
                break;
                //return null;
            }

            MergedNode targetMergedNode = fitResForUINode.get(nodeToAct).second;

            Object attr = null;
            if(actionType == Action.SCROLL){
                attr = Integer.valueOf(contextInfo);
            } else if(actionType == Action.ENTER_TEXT){
                attr = String.valueOf(contextInfo);
            }
            Action action = new Action(targetMergedNode, actionType, attr);

            resActionList.add(action);
            resContextList.add(contextInfo);
        }

        return new Pair<>(resActionList, resContextList);
    }

    public static void openTargetApp(String pkg, String cls){
        ComponentName componentName = new ComponentName(pkg, cls);
        Intent intent = new Intent();
        intent.setComponent(componentName);
        UIALServer.self.startActivity(intent);
    }


    public static boolean jumpToApp(String pkgName){
        if(Objects.equals(pkgName, Utility.getCurrentPkg())){
            return true;
        }
        PackageManager manager = UIALServer.self.getPackageManager();
        PackageInfo info;
        try {
            info = manager.getPackageInfo(pkgName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            info = null;
            e.printStackTrace();
        }

        if(info == null)
            return false;
        Intent intent = manager.getLaunchIntentForPackage(pkgName);
        if(intent == null)
            return false;

        UIALServer.self.startActivity(intent);

        int WAIT_UNTIL_APP = 3000;
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < WAIT_UNTIL_APP){
            if(Objects.equals(pkgName, Utility.getCurrentPkg())){
                return true;
            }
        }

        return false;
    }

    public static boolean jumpToTargetNodeFromCurrent(MergedNode targetNode){
        int leftTryTime = 10;
        while (leftTryTime > 0) {
            leftTryTime -= 1;
            Set<UIAuto.Action> visitedActions = new HashSet<>();
            AccessibilityNodeInfoRecord.buildTree();
            String crtPackageName = AccessibilityNodeInfoRecord.root.getPackageName().toString();
            MergedApp app = UIALServer.self.packageToMergedApp.get(crtPackageName);
            if (app == null) {
                Log.i("failed", "current app not supported");
                return false;
            }

            Pair<Pair<Pair<MergedState, FitResultRegion>, Float>, Map<AccessibilityNodeInfoRecord, Pair<FitResultRegion, MergedNode>>> res = FitResultRegion.getFitResult(app, AccessibilityNodeInfoRecord.root);
            if (res == null) {
                Log.i("failed", "Unknown page reached");
                return false;
            }
            List<UIAuto.Action> actions = UIAuto.findActionListFromSrcToTarget(res.first.first.second, targetNode, visitedActions);
            Pair<List<UIAuto.Action>, Boolean> execRes = UIAuto.execActions(actions, new ArrayList<String>(), null, 1000, targetNode);
            if (execRes.second) {
                return true;
            }
            visitedActions.addAll(execRes.first);
        }

        return false;
    }

}
