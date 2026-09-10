# 震动感知过渡声回归

两段声音由 `generate_audio.py` 合成，没有引用外部录音。进入声长约 0.65 秒，波纹由快渐慢；退出声使用逆序采样。运行时音量为 0.5，并遵循原版主音量与玩家声音设置。原始素材已留出音量余量。

## 声音资源

需要 Python、NumPy 和支持 Vorbis 的 FFmpeg。默认只校验仓库内资源；指定 `--write` 才会重新生成两个 OGG。

```powershell
python tests/sense-transition/generate_audio.py --ffmpeg '<FFmpeg 路径>'
python tests/sense-transition/generate_audio.py --ffmpeg '<FFmpeg 路径>' --write
```

检查包含事件与文件对应关系、单声道解码、时长、采样有限值、音量余量、首尾淡化及与合成源的一致性。

## 播放与取消

```powershell
./tests/sense-transition/run-tests.ps1 -DependencyClasspathFile '<现成 named 依赖清单>' -OutputDirectory '<仓库外的全新实验目录>'
```

入口沿用本机 JDK 路径，使用 Java 17 目标编译。移到其他开发机时需调整脚本中的 JDK 位置；不下载依赖，不运行 Gradle 或启动游戏。

`EarthSenseTransitionTest` 覆盖状态去重、音量和音高、相对听者播放、取消标记及真实 Minecraft 挂接位置。`EarthSenseTransitionNativeTest` 使用真实 OGG 解码器和无声 OpenAL 回环，检查取消后晚到的解码能静音结束并回收声源。

服务器确认、迟到消息、快速切换与换维度组合另见 `tests/sense-client`。这些检查不代替游戏内的听感与模组组合验收。
