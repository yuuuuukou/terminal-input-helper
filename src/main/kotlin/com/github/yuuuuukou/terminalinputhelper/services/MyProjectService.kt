package com.github.yuuuuukou.terminalinputhelper.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.github.yuuuuukou.terminalinputhelper.MyBundle

@Service(Service.Level.PROJECT)
class MyProjectService(private val project: Project) {

    init {
        thisLogger().info(MyBundle.message("projectService", project.name))
    }
}
