package com.xijie.focustimer.ui

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
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var version by remember { mutableIntStateOf(0) }

    when (val s = screen) {
        is Screen.Home -> HomeScreen(
            version = version,
            onOpen = { screen = Screen.Detail(it) },
            onNew = { screen = Screen.Edit(0L) },
            onBackup = { screen = Screen.Backup }
        )

        is Screen.Edit -> ProjectEditScreen(
            projectId = s.projectId,
            onDone = {
                version++
                screen = if (s.projectId > 0L) Screen.Detail(s.projectId) else Screen.Home
            },
            onCancel = { screen = Screen.Home }
        )

        is Screen.Detail -> ProjectDetailScreen(
            projectId = s.projectId,
            version = version,
            onBack = {
                version++
                screen = Screen.Home
            },
            onEdit = { screen = Screen.Edit(s.projectId) }
        )

        is Screen.Backup -> BackupScreen(
            onBack = {
                version++
                screen = Screen.Home
            }
        )
    }
}
