package com.autoswapeng.app.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

data class NodeText(
    val text: String,
    val bounds: Rect,
)

fun AccessibilityNodeInfo.allChildren(out: MutableList<AccessibilityNodeInfo> = mutableListOf()): List<AccessibilityNodeInfo> {
    for (i in 0 until childCount) {
        val c = getChild(i) ?: continue
        out.add(c)
        c.allChildren(out)
    }
    return out
}

fun AccessibilityNodeInfo.collectTextNodes(): List<NodeText> {
    val list = mutableListOf<NodeText>()
    val tmp = Rect()
    fun visit(n: AccessibilityNodeInfo?) {
        if (n == null) return
        val t = (n.text?.toString()?.trim()).takeUnless { it.isNullOrEmpty() }
            ?: (n.contentDescription?.toString()?.trim()).takeUnless { it.isNullOrEmpty() }
            ?: (if (android.os.Build.VERSION.SDK_INT >= 26) n.hintText?.toString()?.trim() else null)
        if (!t.isNullOrEmpty()) {
            n.getBoundsInScreen(tmp)
            list.add(NodeText(t, Rect(tmp)))
        }
        for (i in 0 until n.childCount) visit(n.getChild(i))
    }
    visit(this)
    return list
}

fun centerOf(rect: Rect): Pair<Float, Float> =
    (rect.exactCenterX() to rect.exactCenterY())


