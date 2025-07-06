package com.github.yuuuuukou.terminalinputhelper.toolWindow

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import com.github.yuuuuukou.terminalinputhelper.services.TerminalService
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*


class MyToolWindowFactory : ToolWindowFactory {

    init {
        thisLogger().warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val myToolWindow = MyToolWindow(toolWindow)
        val content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
        
        // ツールウィンドウが閉じられた時のクリーンアップ
        content.setDisposer {
            myToolWindow.dispose()
        }
    }

    override fun shouldBeAvailable(project: Project) = true

    class MyToolWindow(toolWindow: ToolWindow) {

        private val terminalService = toolWindow.project.service<TerminalService>()
        private val outputArea = JTextArea()
        private val inputField = JTextField()
        private var currentShell: TerminalService.ShellType? = null

        fun getContent() = JBPanel<JBPanel<*>>().apply {
            layout = BorderLayout()
            
            // ツールバー（上部）
            val toolbarPanel = JPanel(BorderLayout()).apply {
                val shellCombo = JComboBox(TerminalService.ShellType.getAvailableShells().toTypedArray())
                
                val controlPanel = JPanel().apply {
                    add(JLabel("Shell: "))
                    add(shellCombo)
                    add(JButton("Start").apply {
                        addActionListener {
                            val selected = shellCombo.selectedItem as? TerminalService.ShellType
                            selected?.let { startTerminal(it) }
                        }
                    })
                    add(JButton("Stop").apply {
                        addActionListener {
                            stopTerminal()
                        }
                    })
                }
                
                add(controlPanel, BorderLayout.WEST)
            }
            
            // 出力エリア（中央）
            val outputScrollPane = JScrollPane(outputArea).apply {
                outputArea.isEditable = false
                outputArea.font = java.awt.Font("Monospaced", java.awt.Font.PLAIN, 14)
                preferredSize = Dimension(600, 400)
            }
            
            // 入力エリア（下部）
            val inputPanel = JPanel(BorderLayout()).apply {
                add(JLabel("Input: "), BorderLayout.WEST)
                add(inputField.apply {
                    font = java.awt.Font("Monospaced", java.awt.Font.PLAIN, 14)
                    addActionListener {
                        sendCommand()
                    }
                }, BorderLayout.CENTER)
                add(JButton("Send").apply {
                    addActionListener {
                        sendCommand()
                    }
                }, BorderLayout.EAST)
            }
            
            add(toolbarPanel, BorderLayout.NORTH)
            add(outputScrollPane, BorderLayout.CENTER)
            add(inputPanel, BorderLayout.SOUTH)
        }
        
        private fun startTerminal(shellType: TerminalService.ShellType) {
            currentShell = shellType
            outputArea.text = ""
            terminalService.startTerminal(shellType) { output ->
                appendToOutput(output)
            }
        }
        
        private fun stopTerminal() {
            terminalService.stopTerminal()
            appendToOutput("\n--- Terminal stopped ---")
        }
        
        private fun sendCommand() {
            val input = inputField.text.trim()
            if (input.isNotBlank() && terminalService.isRunning()) {
                terminalService.executeCommand(input)
                inputField.text = ""
            } else if (!terminalService.isRunning()) {
                appendToOutput("Terminal is not running. Please start a shell first.")
            }
        }
        
        private fun appendToOutput(text: String) {
            SwingUtilities.invokeLater {
                // 対話モード対応：改行を追加せずそのまま出力
                outputArea.append(text)
                // 自動スクロールを最適化
                val doc = outputArea.document
                outputArea.caretPosition = doc.length
            }
        }
        
        fun dispose() {
            terminalService.stopTerminal()
        }
    }
}
