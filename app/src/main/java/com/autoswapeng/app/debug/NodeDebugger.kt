package com.autoswapeng.app.debug

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.autoswapeng.app.log.LogManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * 节点调试工具
 * 用于检测和分析无障碍节点
 */
object NodeDebugger {
    private const val TAG = "NodeDebugger"
    
    data class NodeInfo(
        val className: String?,
        val text: String?,
        val contentDescription: String?,
        val viewIdResourceName: String?,
        val bounds: Rect,
        val isClickable: Boolean,
        val isEnabled: Boolean,
        val isFocusable: Boolean,
        val isScrollable: Boolean,
        val childCount: Int,
        val depth: Int,
        val packageName: String?
    )
    
    /**
     * 检测当前页面的所有节点
     */
    fun detectNodes(rootNode: AccessibilityNodeInfo?): List<NodeInfo> {
        if (rootNode == null) {
            LogManager.w(TAG, "根节点为空，无法检测")
            return emptyList()
        }
        
        val nodes = mutableListOf<NodeInfo>()
        traverseNodes(rootNode, 0, nodes)
        
        LogManager.i(TAG, "检测到 ${nodes.size} 个节点")
        return nodes
    }
    
    /**
     * 递归遍历节点树
     */
    private fun traverseNodes(
        node: AccessibilityNodeInfo?,
        depth: Int,
        result: MutableList<NodeInfo>
    ) {
        if (node == null) return
        
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        
        val nodeInfo = NodeInfo(
            className = node.className?.toString(),
            text = node.text?.toString(),
            contentDescription = node.contentDescription?.toString(),
            viewIdResourceName = node.viewIdResourceName,
            bounds = bounds,
            isClickable = node.isClickable,
            isEnabled = node.isEnabled,
            isFocusable = node.isFocusable,
            isScrollable = node.isScrollable,
            childCount = node.childCount,
            depth = depth,
            packageName = node.packageName?.toString()
        )
        
        result.add(nodeInfo)
        
        // 遍历子节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            traverseNodes(child, depth + 1, result)
            child?.recycle()
        }
    }
    
    /**
     * 将节点信息记录到日志
     */
    fun logNodes(nodes: List<NodeInfo>) {
        LogManager.i(TAG, "========== 节点树结构 ==========")
        LogManager.i(TAG, "总计: ${nodes.size} 个节点")
        LogManager.i(TAG, "")
        
        nodes.forEachIndexed { index, node ->
            val indent = "  ".repeat(node.depth)
            val text = node.text ?: node.contentDescription ?: ""
            val textInfo = if (text.isNotEmpty()) " \"$text\"" else ""
            val className = node.className?.split(".")?.lastOrNull() ?: "Unknown"
            
            LogManager.i(TAG, "#$index $indent[$className]$textInfo")
            
            // 详细信息
            if (node.viewIdResourceName != null) {
                LogManager.d(TAG, "$indent  ID: ${node.viewIdResourceName}")
            }
            LogManager.d(TAG, "$indent  位置: ${node.bounds}")
            LogManager.d(TAG, "$indent  可点击:${node.isClickable} 可用:${node.isEnabled} 子节点:${node.childCount}")
        }
        
        LogManager.i(TAG, "========== 检测完成 ==========")
    }
    
    /**
     * 导出为 JSON 格式
     */
    fun toJson(nodes: List<NodeInfo>): String {
        val jsonArray = JSONArray()
        
        nodes.forEach { node ->
            val jsonObj = JSONObject().apply {
                put("className", node.className ?: "")
                put("text", node.text ?: "")
                put("contentDescription", node.contentDescription ?: "")
                put("viewId", node.viewIdResourceName ?: "")
                put("bounds", JSONObject().apply {
                    put("left", node.bounds.left)
                    put("top", node.bounds.top)
                    put("right", node.bounds.right)
                    put("bottom", node.bounds.bottom)
                })
                put("isClickable", node.isClickable)
                put("isEnabled", node.isEnabled)
                put("isFocusable", node.isFocusable)
                put("isScrollable", node.isScrollable)
                put("childCount", node.childCount)
                put("depth", node.depth)
                put("packageName", node.packageName ?: "")
            }
            jsonArray.put(jsonObj)
        }
        
        return jsonArray.toString(2)  // 缩进2个空格
    }
    
    /**
     * 过滤有文本的节点
     */
    fun filterTextNodes(nodes: List<NodeInfo>): List<NodeInfo> {
        return nodes.filter { 
            !it.text.isNullOrEmpty() || !it.contentDescription.isNullOrEmpty()
        }
    }
    
    /**
     * 过滤可点击的节点
     */
    fun filterClickableNodes(nodes: List<NodeInfo>): List<NodeInfo> {
        return nodes.filter { it.isClickable }
    }
    
    /**
     * 按类型分组
     */
    fun groupByClassName(nodes: List<NodeInfo>): Map<String, List<NodeInfo>> {
        return nodes.groupBy { it.className ?: "Unknown" }
    }
    
    /**
     * 生成统计报告
     */
    fun generateReport(nodes: List<NodeInfo>) {
        LogManager.i(TAG, "========== 节点统计报告 ==========")
        LogManager.i(TAG, "总节点数: ${nodes.size}")
        
        val textNodes = filterTextNodes(nodes)
        LogManager.i(TAG, "有文本节点: ${textNodes.size}")
        
        val clickableNodes = filterClickableNodes(nodes)
        LogManager.i(TAG, "可点击节点: ${clickableNodes.size}")
        
        val grouped = groupByClassName(nodes)
        LogManager.i(TAG, "")
        LogManager.i(TAG, "按类型统计:")
        grouped.entries.sortedByDescending { it.value.size }.take(10).forEach { (className, list) ->
            val shortName = className.split(".").lastOrNull() ?: className
            LogManager.i(TAG, "  $shortName: ${list.size} 个")
        }
        
        LogManager.i(TAG, "")
        LogManager.i(TAG, "有文本的节点:")
        textNodes.take(20).forEach { node ->
            val text = node.text ?: node.contentDescription ?: ""
            val className = node.className?.split(".")?.lastOrNull() ?: "Unknown"
            LogManager.i(TAG, "  [$className] \"$text\"")
        }
        
        LogManager.i(TAG, "========== 报告结束 ==========")
    }
}

