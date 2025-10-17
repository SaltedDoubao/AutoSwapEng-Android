package com.autoswapeng.app.log

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 日志管理器
 * 用于收集和显示应用运行日志
 */
object LogManager {
    private const val TAG = "AutoSwapEng"
    private const val MAX_LOGS = 500
    
    private var logFile: File? = null
    private var logWriter: PrintWriter? = null
    private var externalLogFile: File? = null
    private var externalLogWriter: PrintWriter? = null
    
    data class LogEntry(
        val timestamp: Long,
        val level: Level,
        val tag: String,
        val message: String
    ) {
        enum class Level(val priority: Int) {
            DEBUG(0),
            INFO(1),
            WARN(2),
            ERROR(3)
        }
        
        fun format(): String {
            val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
            val levelStr = when (level) {
                Level.DEBUG -> "D"
                Level.INFO -> "I"
                Level.WARN -> "W"
                Level.ERROR -> "E"
            }
            return "$time [$levelStr/$tag] $message"
        }
    }
    
    private val logs = ConcurrentLinkedQueue<LogEntry>()
    private val _logsFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val logsFlow: StateFlow<List<LogEntry>> = _logsFlow
    
    // 日志过滤级别，默认为 INFO
    private val _minLevel = MutableStateFlow(LogEntry.Level.INFO)
    val minLevel: StateFlow<LogEntry.Level> = _minLevel
    
    /**
     * 初始化日志管理器
     * @param context 应用上下文
     */
    fun initialize(context: Context) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        
        // 1. 初始化内部存储日志
        initializeInternalLog(context, timestamp)
        
        // 2. 初始化外部存储日志（应用专属目录，不需要权限）
        initializeExternalLog(context, timestamp)
    }
    
    /**
     * 初始化内部存储日志
     */
    private fun initializeInternalLog(context: Context, timestamp: String) {
        try {
            // 关闭之前的日志文件
            try {
                logWriter?.flush()
                logWriter?.close()
            } catch (e: Exception) {
                Log.e(TAG, "关闭旧日志文件失败", e)
            }
            
            // 创建日志目录
            val logDir = File(context.filesDir, "logs")
            Log.d(TAG, "[内部存储] 日志目录路径: ${logDir.absolutePath}")
            
            if (!logDir.exists()) {
                val created = logDir.mkdirs()
                Log.d(TAG, "[内部存储] 创建日志目录: $created")
            } else {
                Log.d(TAG, "[内部存储] 日志目录已存在")
            }
            
            // 创建日志文件
            logFile = File(logDir, "log_$timestamp.txt")
            Log.d(TAG, "[内部存储] 准备创建日志文件: ${logFile?.absolutePath}")
            
            // 创建日志写入器（autoFlush = true 确保立即写入磁盘）
            logWriter = PrintWriter(FileWriter(logFile, true), true)
            
            // 写入文件头
            logWriter?.println("===== AutoSwapEng Log Started at $timestamp =====")
            logWriter?.println("Internal log file: ${logFile?.absolutePath}")
            logWriter?.println()
            logWriter?.flush()
            
            // 验证文件是否创建成功
            if (logFile?.exists() == true) {
                Log.i(TAG, "✓ [内部存储] 日志文件创建成功: ${logFile?.absolutePath}")
                Log.i(TAG, "✓ [内部存储] 文件大小: ${logFile?.length()} bytes")
            } else {
                Log.e(TAG, "✗ [内部存储] 日志文件创建失败！")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[内部存储] 初始化日志文件失败", e)
            e.printStackTrace()
        }
    }
    
    /**
     * 初始化外部存储日志（应用专属目录，无需权限）
     */
    private fun initializeExternalLog(context: Context, timestamp: String) {
        try {
            // 关闭之前的日志文件
            try {
                externalLogWriter?.flush()
                externalLogWriter?.close()
            } catch (e: Exception) {
                Log.e(TAG, "关闭旧外部日志文件失败", e)
            }
            
            // 获取外部存储的应用专属目录（不需要权限）
            val externalLogDir = context.getExternalFilesDir("logs")
            
            if (externalLogDir == null) {
                Log.w(TAG, "[外部存储] 外部存储不可用")
                return
            }
            
            Log.d(TAG, "[外部存储] 日志目录路径: ${externalLogDir.absolutePath}")
            
            if (!externalLogDir.exists()) {
                val created = externalLogDir.mkdirs()
                Log.d(TAG, "[外部存储] 创建日志目录: $created")
            } else {
                Log.d(TAG, "[外部存储] 日志目录已存在")
            }
            
            // 创建日志文件
            externalLogFile = File(externalLogDir, "log_$timestamp.txt")
            Log.d(TAG, "[外部存储] 准备创建日志文件: ${externalLogFile?.absolutePath}")
            
            // 创建日志写入器
            externalLogWriter = PrintWriter(FileWriter(externalLogFile, true), true)
            
            // 写入文件头
            externalLogWriter?.println("===== AutoSwapEng Log Started at $timestamp =====")
            externalLogWriter?.println("External log file: ${externalLogFile?.absolutePath}")
            externalLogWriter?.println("提示: 此文件可通过文件管理器访问")
            externalLogWriter?.println()
            externalLogWriter?.flush()
            
            // 验证文件是否创建成功
            if (externalLogFile?.exists() == true) {
                Log.i(TAG, "✓ [外部存储] 日志文件创建成功: ${externalLogFile?.absolutePath}")
                Log.i(TAG, "✓ [外部存储] 文件大小: ${externalLogFile?.length()} bytes")
                Log.i(TAG, "✓ [外部存储] 用户可通过文件管理器访问此文件")
            } else {
                Log.e(TAG, "✗ [外部存储] 日志文件创建失败！")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[外部存储] 初始化日志文件失败", e)
            e.printStackTrace()
        }
    }
    
    /**
     * 获取日志文件路径（优先返回外部存储路径，方便用户访问）
     */
    fun getLogFilePath(): String? = externalLogFile?.absolutePath ?: logFile?.absolutePath
    
    /**
     * 获取外部存储日志文件路径
     */
    fun getExternalLogFilePath(): String? = externalLogFile?.absolutePath
    
    /**
     * 获取所有日志文件列表
     */
    fun getLogFiles(context: Context): List<File> {
        val logDir = File(context.filesDir, "logs")
        return if (logDir.exists() && logDir.isDirectory) {
            logDir.listFiles()?.sortedByDescending { it.lastModified() }?.toList() ?: emptyList()
        } else {
            emptyList()
        }
    }
    
    /**
     * 添加日志
     */
    private fun addLog(level: LogEntry.Level, tag: String, message: String) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message
        )
        
        logs.add(entry)
        
        // 限制日志数量
        while (logs.size > MAX_LOGS) {
            logs.poll()
        }
        
        // 更新过滤后的日志到 Flow
        updateFilteredLogs()
        
        // 同时输出到 logcat
        when (level) {
            LogEntry.Level.DEBUG -> Log.d(tag, message)
            LogEntry.Level.INFO -> Log.i(tag, message)
            LogEntry.Level.WARN -> Log.w(tag, message)
            LogEntry.Level.ERROR -> Log.e(tag, message)
        }
        
        // 写入日志文件（同时写入内部和外部存储）
        val formattedLog = entry.format()
        try {
            logWriter?.println(formattedLog)
            logWriter?.flush()  // 立即刷新到磁盘，防止崩溃时丢失日志
        } catch (e: Exception) {
            Log.e(TAG, "写入内部日志文件失败: ${e.message}", e)
        }
        
        try {
            externalLogWriter?.println(formattedLog)
            externalLogWriter?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "写入外部日志文件失败: ${e.message}", e)
        }
    }
    
    fun d(tag: String, message: String) = addLog(LogEntry.Level.DEBUG, tag, message)
    fun i(tag: String, message: String) = addLog(LogEntry.Level.INFO, tag, message)
    fun w(tag: String, message: String) = addLog(LogEntry.Level.WARN, tag, message)
    fun e(tag: String, message: String) = addLog(LogEntry.Level.ERROR, tag, message)
    
    /**
     * 清空日志（仅清空内存中的日志，不删除文件）
     */
    fun clear() {
        logs.clear()
        _logsFlow.value = emptyList()
    }
    
    /**
     * 关闭日志文件
     */
    fun close() {
        try {
            logWriter?.flush()
            logWriter?.close()
            Log.d(TAG, "内部日志文件已关闭")
        } catch (e: Exception) {
            Log.e(TAG, "关闭内部日志文件失败", e)
        }
        
        try {
            externalLogWriter?.flush()
            externalLogWriter?.close()
            Log.d(TAG, "外部日志文件已关闭")
        } catch (e: Exception) {
            Log.e(TAG, "关闭外部日志文件失败", e)
        }
    }
    
    /**
     * 设置最小日志级别
     */
    fun setMinLevel(level: LogEntry.Level) {
        _minLevel.value = level
        updateFilteredLogs()
    }
    
    /**
     * 更新过滤后的日志
     */
    private fun updateFilteredLogs() {
        val minLevelValue = _minLevel.value
        _logsFlow.value = logs.filter { it.level.priority >= minLevelValue.priority }
    }
    
    /**
     * 获取所有日志（未过滤）
     */
    fun getAllLogs(): List<LogEntry> = logs.toList()
    
    /**
     * 获取过滤后的日志
     */
    fun getFilteredLogs(): List<LogEntry> {
        val minLevelValue = _minLevel.value
        return logs.filter { it.level.priority >= minLevelValue.priority }
    }
    
    /**
     * 导出日志为文本
     */
    fun exportToText(): String {
        return logs.joinToString("\n") { it.format() }
    }
}

