# R8 混淆与压缩规则（专注计时 App）
#
# 本工程代码里没有反射、没有 getIdentifier 按名取资源，
# 也没有 Gson/kotlinx.serialization 这类按字段名反序列化的库
# （备份导入导出走 org.json，键名是字符串字面量，混淆不影响）。
# 因此原则上不需要业务 keep 规则，这里只做"组件入口 + 可读堆栈"的兜底，
# 避免以后新增组件时因为写在 Manifest 之外被裁掉。

# 1) 四大组件与 AppWidgetProvider：AGP 会按 Manifest 自动生成 keep 规则，
#    这里再显式钉一遍，零成本保险。
-keep class com.xijie.focustimer.App { *; }
-keep class com.xijie.focustimer.ui.MainActivity { *; }
-keep class com.xijie.focustimer.widget.WidgetConfigActivity { *; }
-keep class com.xijie.focustimer.timer.TimerService { *; }
-keep class com.xijie.focustimer.timer.ChimeReceiver { *; }
-keep class com.xijie.focustimer.widget.*Provider { *; }

# 2) 枚举的 values()/valueOf() 若将来被反射调用，保留一下
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 3) 保留行号与源文件名（堆栈可读，体积代价很小）
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 4) 音效资源 res/raw/chime_*.wav / chime_*.ogg 在代码里以 R.raw.* 直接引用，
#    shrinkResources 会正常保留；额外的资源保留声明放在 res/raw/keep.xml
#    （-keepresourcefiles 不是 R8 指令，写在 proguard 规则里会导致 minifyReleaseWithR8 失败）。
