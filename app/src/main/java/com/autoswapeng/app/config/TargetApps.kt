package com.autoswapeng.app.config

/**
 * 目标应用配置
 * 
 * 在此添加需要自动操作的应用包名
 */
object TargetApps {
    /**
     * 支持的应用包名列表
     * 
     * 常见英语学习应用包名：
     * - 扇贝单词: com.shanbay.words
     * - 百词斩: com.baicizhan.main
     * - 微信（含小程序）: com.tencent.mm
     * - 不背单词: cn.com.langeasy.LangEasyLexis
     * - 墨墨背单词: com.maimemo.android.momo
     * - 知米背单词: com.zhimi.www
     */
    val PACKAGE_NAMES = setOf(
        "com.shanbay.words",  // 扇贝单词
        "com.baicizhan.main", // 百词斩
        "com.tencent.mm"      // 微信（包含翻转外语小程序）
        // 根据需要添加更多应用包名
    )
}

