package com.xijie.focustimer.timer

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.xijie.focustimer.R

object ChimePlayer {

    fun resId(sound: String): Int = when (sound) {
        "ding" -> R.raw.chime_ding
        "bell" -> R.raw.chime_bell
        "bowl" -> R.raw.chime_bowl
        else -> 0
    }

    /**
     * 走闹钟音频通道（USAGE_ALARM）：手机静音也会响，音量取"闹钟音量"。
     * 只响一次，响完自动释放，不循环、不重复。
     * onDone 一定会被调用（包括"关闭提示音"和播放失败的情况），调用方据此释放唤醒锁。
     */
    fun play(ctx: Context, sound: String, onDone: (() -> Unit)? = null) {
        val res = resId(sound)
        if (res == 0) {
            onDone?.invoke()
            return
        }
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val mp = MediaPlayer()
            mp.setAudioAttributes(attrs)
            val afd = ctx.resources.openRawResourceFd(res)
            try {
                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                mp.prepare()
            } finally {
                afd.close()
            }
            var released = false
            fun done() {
                if (released) return
                released = true
                try {
                    mp.release()
                } catch (t: Throwable) {
                }
                onDone?.invoke()
            }
            mp.setOnCompletionListener { done() }
            mp.setOnErrorListener { _, _, _ -> done(); true }
            mp.start()
        } catch (t: Throwable) {
            onDone?.invoke()
        }
    }
}
