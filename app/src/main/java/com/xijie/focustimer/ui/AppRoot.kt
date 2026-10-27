package com.xijie.focustimer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

sealed class Screen {
    data object Home : Screen()
    data class Edit(val projectId: Long) : Screen()
    data class Detail(val projectId: Long) : Screen()
    data object Backup : Screen()
}

@Composable
fun AppRoot() {
    var stack by remember { mutableStateOf(listOf<Screen>(Screen.Home)) }
    var version by remember { mutableIntStateOf(0) }

    val current = stack.last()

    fun push(s: Screen) {
        stack = stack + s
    }

    fun pop() {
        if (stack.size > 1) stack = stack.dropLast(1)
    }

    fun resetTo(vararg list: Screen) {
        stack = list.toList()
    }

    // 系统返回键 / 侧滑返回：回到上一级，而不是退出到桌面
    BackHandler(enabled = stack.size > 1) { pop() }

    when (current) {
        is Screen.Home -> HomeScreen(
            version = version,
            onOpen = { push(Screen.Detail(it)) },
            onNew = { push(Screen.Edit(0L)) },
            onBackup = { push(Screen.Backup) }
        )

        is Screen.Edit -> ProjectEditScreen(
            projectId = current.projectId,
            onDone = {
                version++
                if (current.projectId > 0L) {
                    val id = current.projectId
                    resetTo(Screen.Home, Screen.Detail(id))
                } else {
                    resetTo(Screen.Home)
                }
            },
            onCancel = { pop() }
        )

        is Screen.Detail -> ProjectDetailScreen(
            projectId = current.projectId,
            version = version,
            onBack = {
                version++
                pop()
            },
            onEdit = { push(Screen.Edit(current.projectId)) }
        )

        is Screen.Backup -> BackupScreen(
            onBack = {
                version++
                pop()
            }
        )
    }
}
