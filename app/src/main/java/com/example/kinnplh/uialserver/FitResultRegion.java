package com.example.kinnplh.uialserver;

import android.util.Pair;

import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

/**
 * Created by kinnplh on 2019/4/8.
 */

public class FitResultRegion {

    public static Pair<Pair<Pair<MergedState, FitResultRegion>, Float>, Map<AccessibilityNodeInfoRecord, Pair<FitResultRegion, MergedNode>>>
    getFitResult(MergedApp app, AccessibilityNodeInfoRecord root){
        return getFitResult(app, root, new ArrayList<MergedNode>());
    }

    public static Pair<Pair<Pair<MergedState, FitResultRegion>, Float>, Map<AccessibilityNodeInfoRecord, Pair<FitResultRegion, MergedNode>>>
        getFitResult(MergedApp app, AccessibilityNodeInfoRecord root, List<MergedNode> targetNodes){
        if(!Objects.equals(root.getPackageName().toString(), app.packageName)){
            return null;
        }

        int minDiffNum = 100000;
        MergedState stateWhenDiffMin = null;
        FitResultRegion fitResWhenDiffMin = null;
        Map<AccessibilityNodeInfoRecord, Pair<FitResultRegion, MergedNode>> fitResWhenDiffMinForUiNode = null;

        for(MergedPage page: app.mergedPages){
            for(MergedState state: page.mergedStates){
                Map<AccessibilityNodeInfoRecord, Pair<FitResultRegion, MergedNode>> tmpForUiNode = new HashMap<>();
                Pair<FitResultRegion, Integer> crtRes = getFitResultByCrtMergedRegion(state.rootRegion, root, null, null, tmpForUiNode, targetNodes);
                if(crtRes.second < minDiffNum){
                    minDiffNum = crtRes.second;
                    stateWhenDiffMin = state;
                    fitResWhenDiffMin = crtRes.first;
                    fitResWhenDiffMinForUiNode = tmpForUiNode;
                }
            }
        }

        int totalNodeNum = Utility.countNodeNum(root);


        return new Pair<>(new Pair<>(new Pair<>(stateWhenDiffMin, fitResWhenDiffMin), (minDiffNum + 0.01f) / (totalNodeNum + 0.01f)), fitResWhenDiffMinForUiNode);
    }

    private static Pair<FitResultRegion, Integer> getFitResultByCrtMergedRegion(
            MergedRegion mergedRegion, AccessibilityNodeInfoRecord regionRoot, FitResultRegion parentFitResult, AccessibilityNodeInfoRecord dynamicEntranceInParent,
            Map<AccessibilityNodeInfoRecord, Pair<FitResultRegion, MergedNode>> fitResultForUINode, List<MergedNode> targetNodes){
        int diffNum = 0;
        FitResultRegion fitRes = new FitResultRegion();
        fitRes.correspondingMergedRegion = mergedRegion;
        fitRes.parentRegion = parentFitResult;
        fitRes.entranceBelongsTo = dynamicEntranceInParent;

        // 对于所有的静态区域的节点，要找到对应的 merged node
        // 对于所有的动态区域入口，还要进行额外的标记以便递归
        Queue<Pair<MergedNode, AccessibilityNodeInfoRecord>> pairQueue = new LinkedList<>();
        if(!mergedRegion.root.canMatchUINode(regionRoot)){
            return new Pair<>(fitRes, Utility.countNodeNum(regionRoot));
        }

        pairQueue.add(new Pair<>(mergedRegion.root, regionRoot));

        while (!pairQueue.isEmpty()){
            Pair<MergedNode, AccessibilityNodeInfoRecord> crtPair = pairQueue.poll();
            MergedNode mgNode = crtPair.first;
            AccessibilityNodeInfoRecord uiNode = crtPair.second;
            Utility.assertTrue(mgNode.canMatchUINode(uiNode));
            Utility.assertTrue(!fitRes.uiNodeToMergedNode.containsKey(uiNode));
            fitRes.uiNodeToMergedNode.put(uiNode, mgNode);
            if(targetNodes.contains(mgNode)){
                diffNum -= 1000;
            }

            // 这里的匹配模式在这个语境下不会再更改了。所以直接记录是没有问题的
            Utility.assertTrue(!fitResultForUINode.containsKey(uiNode));
            fitResultForUINode.put(uiNode, new Pair<>(fitRes, mgNode));

            if(mgNode.isDynamicEntrance){
                Utility.assertTrue(!fitRes.dynamicEntranceToSubRegions.containsKey(uiNode));
                fitRes.dynamicEntranceToSubRegions.put(uiNode, new ArrayList<FitResultRegion>());
            } else {
                Utility.assertTrue(mgNode.childrenRegion.isEmpty());

                List<MergedNode> childrenMgNode = mgNode.childrenNode;
                List<AccessibilityNodeInfoRecord> childrenUiNode = uiNode.getChildren();
                List<Pair<MergedNode, AccessibilityNodeInfoRecord>> lcsRes = Utility.lcsNodes(childrenMgNode, childrenUiNode);
                pairQueue.addAll(lcsRes);

                // 不递归地删除没有用的节点之后再进行匹配。所有没有被匹配上的 merged node 和 ui node 都会不被递归地删除"无意义"的节点。
                List<AccessibilityNodeInfoRecord> leftUINodes = new ArrayList<>();
                List<MergedNode> leftMergedNodes = new ArrayList<>();
                leftUINodes.addAll(uiNode.children);
                leftMergedNodes.addAll(mgNode.childrenNode);

                for(Pair<MergedNode, AccessibilityNodeInfoRecord> p: lcsRes){
                    leftMergedNodes.remove(p.first);
                    leftUINodes.remove(p.second);
                }

                List<AccessibilityNodeInfoRecord> leftUINodesMovedToMeaningful = new ArrayList<>();
                List<MergedNode> leftMergedNodesMovedToMeaningful = new ArrayList<>();
                for(AccessibilityNodeInfoRecord oriNode: leftUINodes){
                    Pair<AccessibilityNodeInfoRecord, Integer> moveRes = oriNode.moveToMeaningfulChild();
                    diffNum += moveRes.second;
                    leftUINodesMovedToMeaningful.add(moveRes.first);
                }

                for(MergedNode oriNode: leftMergedNodes){
                    Pair<MergedNode, Integer> moveRes = oriNode.moveToMeaningfulChild();
                    leftMergedNodesMovedToMeaningful.add(moveRes.first);
                    diffNum += moveRes.second;
                }

                List<Pair<MergedNode, AccessibilityNodeInfoRecord>> lcsResForMoved = Utility.lcsNodes(leftMergedNodesMovedToMeaningful, leftUINodesMovedToMeaningful);
                pairQueue.addAll(lcsResForMoved);

                // 将没有被匹配上的 ui node 的数量加入到 diff num 中去
                for(Pair<MergedNode, AccessibilityNodeInfoRecord> lcsPair: lcsResForMoved){
                    leftMergedNodesMovedToMeaningful.remove(lcsPair.first);
                    leftUINodesMovedToMeaningful.remove(lcsPair.second);
                }

                for(AccessibilityNodeInfoRecord nodeNotMatched: leftUINodesMovedToMeaningful){
                    diffNum += Utility.countNodeNum(nodeNotMatched);
                }

                for(MergedNode mgNodeNotMatched: leftMergedNodesMovedToMeaningful){
                    diffNum += Utility.countMergedNodeNum(mgNodeNotMatched);
                }

            }
        }

        // 对所有的子区域进行处理。所有的子区域都应该是找到一个最符合条件的。在寻找的过程中注意不应该对数据进行最终的更新
        for(AccessibilityNodeInfoRecord dynamicEntrance: fitRes.dynamicEntranceToSubRegions.keySet()){
            MergedNode mgDynamicEntrance = fitRes.uiNodeToMergedNode.get(dynamicEntrance);
            for(int i = 0; i < dynamicEntrance.getChildCount(); ++ i){
                AccessibilityNodeInfoRecord crtSubRegionUiRoot = dynamicEntrance.getChild(i);
                // 对这个区域找到一个最接近的区域匹配
                int minDiffNum = 100000;
                FitResultRegion fitResWhenMinDiff = null;
                Map<AccessibilityNodeInfoRecord, Pair<FitResultRegion, MergedNode>> fitResultForUINodeWhenMinDiffNum = null;

                for(MergedRegion subMergedRegion: mgDynamicEntrance.childrenRegion){
                    Map<AccessibilityNodeInfoRecord, Pair<FitResultRegion, MergedNode>> tmp = new HashMap<>();
                    Pair<FitResultRegion, Integer> tmpFitRes = getFitResultByCrtMergedRegion(subMergedRegion, crtSubRegionUiRoot, fitRes, dynamicEntrance, tmp, targetNodes);
                    if(tmpFitRes.second < minDiffNum){
                        minDiffNum = tmpFitRes.second;
                        fitResWhenMinDiff = tmpFitRes.first;
                        fitResultForUINodeWhenMinDiffNum = tmp;
                    }
                }

                // 得到 diff 最小的
                Utility.assertTrue(fitResultForUINodeWhenMinDiffNum != null && fitResWhenMinDiff != null);
                diffNum += minDiffNum;

                fitResultForUINode.putAll(fitResultForUINodeWhenMinDiffNum);
                fitRes.dynamicEntranceToSubRegions.get(dynamicEntrance).add(fitResWhenMinDiff);
            }
        }
        return new Pair<>(fitRes, diffNum);
    }



    AccessibilityNodeInfoRecord regionRoot;
    Map<AccessibilityNodeInfoRecord, List<FitResultRegion>> dynamicEntranceToSubRegions;
    MergedRegion correspondingMergedRegion;
    Map<AccessibilityNodeInfoRecord, MergedNode> uiNodeToMergedNode;
    FitResultRegion parentRegion;
    AccessibilityNodeInfoRecord entranceBelongsTo;


    private FitResultRegion(){
        dynamicEntranceToSubRegions = new HashMap<>();
        uiNodeToMergedNode = new HashMap<>();
    }

    public List<MergedNode> getAllMergedNodes(){
        List<MergedNode> res = new ArrayList<>();
        res.addAll(uiNodeToMergedNode.values());
        for(List<FitResultRegion> subRegionList: dynamicEntranceToSubRegions.values()){
            for(FitResultRegion subRegion: subRegionList){
                res.addAll(subRegion.getAllMergedNodes());
            }
        }

        return res;
    }

    public List<AccessibilityNodeInfoRecord> getUINodeByMergedNode(MergedNode mergedNode){
        List<AccessibilityNodeInfoRecord> res = new ArrayList<>();
        MergedRegion regionBelongTo = mergedNode.regionBelongTo;
        if(regionBelongTo == correspondingMergedRegion){
            for(Map.Entry<AccessibilityNodeInfoRecord, MergedNode> entry: uiNodeToMergedNode.entrySet()){
                if(entry.getValue() == mergedNode){
                    res.add(entry.getKey());
                }
            }
        }

        for(List<FitResultRegion> subRegionList: dynamicEntranceToSubRegions.values()){
            for(FitResultRegion subRegion: subRegionList){
                res.addAll(subRegion.getUINodeByMergedNode(mergedNode));
            }
        }
        return res;
    }

}
