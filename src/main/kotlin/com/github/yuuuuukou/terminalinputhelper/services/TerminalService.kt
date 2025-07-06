package com.github.yuuuuukou.terminalinputhelper.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import java.io.*
import java.util.concurrent.TimeUnit
import java.nio.charset.StandardCharsets

@Service(Service.Level.PROJECT)
class TerminalService(private val project: Project) {
    private var process: Process? = null
    private var processWriter: PrintWriter? = null
    private var outputCallback: ((String) -> Unit)? = null
    private var readerThread: Thread? = null

    companion object {
        private val logger = thisLogger()
    }
    
    enum class ShellType(val displayName: String, val command: List<String>) {
        CMD("Command Prompt", listOf("cmd.exe", "/K")),  // /K オプションを追加
        POWERSHELL("PowerShell", listOf("powershell.exe", "-NoExit", "-NonInteractive")),
        GIT_BASH("Git Bash", listOf("bash.exe", "-i")),  // 対話モードを追加
        WSL("WSL Bash", listOf("wsl.exe")),
        BASH("Bash", listOf("/bin/bash", "-i"));  // 対話モードを追加

        companion object {
            fun getAvailableShells(): List<ShellType> {
                return when {
                    SystemInfo.isWindows -> listOf(CMD)
                    SystemInfo.isLinux || SystemInfo.isMac -> listOf(BASH)
                    else -> emptyList()
                }
            }
        }
    }
    
    fun startTerminal(shellType: ShellType, outputHandler: (String) -> Unit) {
        stopTerminal()
        outputCallback = outputHandler
        
        try {
            val processBuilder = ProcessBuilder(shellType.command)
            processBuilder.directory(project.basePath?.let { java.io.File(it) })
            processBuilder.redirectErrorStream(true)
            
            // 環境変数を設定して対話的モードを確実にする
            val env = processBuilder.environment()
            env["TERM"] = "dumb"

            process = processBuilder.start()

            // autoFlushを有効にしたPrintWriter
            processWriter = PrintWriter(OutputStreamWriter(process!!.outputStream, StandardCharsets.UTF_8), true)

            // 出力読み取りスレッドの改善
            readerThread = Thread {
                try {
                    val inputStream = process!!.inputStream
                    val buffer = ByteArray(4096)

                    while (!Thread.currentThread().isInterrupted && process?.isAlive == true) {
                        val available = inputStream.available()
                        if (available > 0) {
                            val bytesRead = inputStream.read(buffer, 0, Math.min(available, buffer.size))
                            if (bytesRead > 0) {
                                val text = String(buffer, 0, bytesRead, StandardCharsets.UTF_8)
                                outputCallback?.invoke(text)
                            }
                        } else {
                            // データがない場合は短時間待機
                            Thread.sleep(10)
                        }
                    }
                } catch (e: InterruptedException) {
                    // スレッド停止
                } catch (e: IOException) {
                    if (process?.isAlive == true) {
                        logger.error("Terminal output error", e)
                    }
                }
            }
            readerThread?.start()

        } catch (e: Exception) {
            logger.error("Failed to start terminal", e)
            outputCallback?.invoke("Error: ${e.message}\n")
        }
    }
    
    fun executeCommand(command: String) {
        try {
            processWriter?.println(command)
            processWriter?.flush()  // 明示的にフラッシュ
            logger.info("Executed command: $command")
        } catch (e: Exception) {
            logger.error("Failed to execute command", e)
        }
    }

    fun stopTerminal() {
        try {
            readerThread?.interrupt()
            processWriter?.close()
            process?.destroyForcibly()
            process = null
            processWriter = null
            outputCallback = null
        } catch (e: Exception) {
            logger.error("Failed to stop terminal", e)
        }
    }

    fun isRunning(): Boolean = process?.isAlive == true
}
