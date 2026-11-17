![PerthFin banner](images/perthfin-banner.png)

# PerthFin

[简体中文](README.md) | [English](README.en.md)

PerthFin 是一个独立的原生 Android [Jellyfin](https://jellyfin.org) 客户端，基于开源项目
[Findroid](https://github.com/jarnedemeulemeester/findroid) 二次开发。

本项目是独立衍生版本，**不是上游仓库的 fork**，与 Jellyfin 项目及
[Findroid](https://github.com/jarnedemeulemeester/findroid) 作者无隶属、赞助或背书关系。
“Jellyfin”仅作描述性使用。

<p align="center">
  <a href="https://github.com/MeowPerth/PerthFin/releases"><img src="https://img.shields.io/github/v/release/MeowPerth/PerthFin?style=flat-square&label=release&color=blue" alt="release"></a>
  <a href="https://github.com/MeowPerth/PerthFin/stargazers"><img src="https://img.shields.io/github/stars/MeowPerth/PerthFin?style=flat-square&label=stars&color=blue" alt="stars"></a>
  <a href="https://github.com/MeowPerth/PerthFin/issues"><img src="https://img.shields.io/github/issues/MeowPerth/PerthFin?style=flat-square&label=issues&color=yellow" alt="issues"></a>
  <a href="https://github.com/MeowPerth/PerthFin/pulls"><img src="https://img.shields.io/github/issues-pr/MeowPerth/PerthFin?style=flat-square&label=pull%20requests&color=yellow" alt="pull requests"></a>
  <a href="https://github.com/MeowPerth/PerthFin/releases"><img src="https://img.shields.io/github/downloads/MeowPerth/PerthFin/total?style=flat-square&label=downloads&color=green" alt="downloads"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0-orange?style=flat-square" alt="license"></a>
</p>

- 当前版本：**1.1.0**
- 包名：`com.wuhaohu.perthfin`
- 许可证：GPLv3
- 上游基线：[Findroid](https://github.com/jarnedemeulemeester/findroid) 1.2.0（commit [`3a06420`](https://github.com/jarnedemeulemeester/findroid/commit/3a06420)）

> 仓库的 Contributors 统计包含 [Findroid](https://github.com/jarnedemeulemeester/findroid) 上游历史作者；PerthFin 的修改由 MeowPerth 维护。

## 截图

### 手机界面（竖屏）

| 首页 | 媒体库（每行 3 个） | 每行数量设置 | 竖屏播放 |
|:---:|:---:|:---:|:---:|
| ![首页](images/screenshots/home.jpg) | ![媒体库](images/screenshots/library.jpg) | ![每行数量设置](images/screenshots/columns.jpg) | ![竖屏播放](images/screenshots/player-portrait.jpg) |

### 横屏、码率与投屏

| 码率调整 | 横屏播放 | 投屏控制 |
|:---:|:---:|:---:|
| ![码率调整](images/screenshots/quality.jpg) | ![横屏播放](images/screenshots/player.jpg) | ![投屏控制](images/screenshots/cast.jpg) |

## 功能

### 媒体库与发现

- Kotlin + Jetpack Compose 原生 Android 界面
- 电影、剧集、季、单集
- 合集 / BoxSet
- 多 Jellyfin 媒体库
- 首页：推荐、继续观看、接下来、最新
- 搜索电影和剧集
- 收藏电影、剧集和单集
- 详情页：简介、演职人员、媒体信息、章节、Trickplay
- 媒体库、搜索结果、收藏和合集支持自定义每行数量

### 播放

- 视频码率不超过所选档位，且播放器支持对应容器/视频/音频/字幕格式时直连
- 超过档位或格式不支持时，服务端转码为 HLS（H.264 视频 / AAC 音频）
- 码率档位：40、30、20、10、4、1.5 Mbps（40 Mbps 取代原「原画」）
- 播放中切换码率，并从当前位置继续
- ExoPlayer（默认）和 libmpv 两种播放后端
- 画中画
- 播放速度选择
- 章节时间轴标记与章节跳转手势
- 片头 / 片尾跳过（按钮和自动跳过）
- 拖动进度时显示 Trickplay 预览
- 横屏和竖屏播放
- 播放器内快速切换横竖屏
- 「默认横屏模式」开关；关闭后沿用上一次播放方向
- 播放器底栏：暂停/播放在左，进度条居中，时间在右；竖屏时时间位于进度条右上方
- 暂停时中央显示播放按钮，播放时自动隐藏
- 双击手势区域按 25% / 50% / 25% 划分：左侧后退 5 秒、中间暂停/继续、右侧前进 15 秒
- 音量、亮度、快进/快退和缩放手势；音量手势条实时跟随手机媒体音量
- 自动从上次播放位置继续

### DLNA 投屏

- 扫描局域网内的 UPnP MediaRenderer（电视、盒子、投影）
- 播放器第二排左侧的投屏按钮；选择电视后推送当前视频
- 推送时沿用当前码率档位、音轨和字幕设置；字幕选中即烧录
- 投屏中支持暂停/继续、拖动进度和退出投屏；转码流拖动后从目标位置重新起播（约 1–2 秒）
- 投屏中从首页播放其他视频时会询问是否重新投屏；选择「否」不执行任何操作
- 投屏中切换音轨、字幕或码率会从当前位置立即重新推送
- 投屏控制层复用本地播放器的 5+3 按钮网格；进度条和时间按顶部按钮中心对齐
- 投屏异常时中央显示警告图标，错误状态集中显示在标题下方
- 修复 Seek/续播时字幕烧录产生负时间戳、电视只显示首帧的问题（`CopyTimestamps`）
- 离开播放器后电视继续播放；App 在后台继续上报进度
- App 重启后会尝试连接上次的电视并恢复当前进度显示
- 设置位于「网络 → DLNA 投屏」：启用、发现超时、转码容器（默认 MPEG-TS）
- 小米等电视不支持 Jellyfin 的渐进式 MP4 转码，请保持 MPEG-TS
- 投屏始终转码为 H.264 + AAC 2 声道，字幕统一烧录
- 分辨率随码率档位自动限制：40/30→2160p，20/10→1080p，4→720p，1.5→480p
- 限制：电视必须能直接访问 Jellyfin 地址；暂不支持 App 本地代理推流；暂不支持 Chromecast

### 音轨与字幕

- 音轨选择
- 字幕选择，包括「无」
- 每部影片分别记忆音轨和字幕选择
- 没有手动选择时，按首选音轨 / 字幕语言匹配
- 转码时把选中的字幕烧录进视频码流
- 直连时支持内嵌和外挂字幕
- 字幕样式跟随系统字幕设置

### 账号、服务器与界面

- 多服务器、多用户
- Jellyfin 服务器发现
- 密码登录和 Quick Connect
- 首页和用户选择页显示用户头像
- 应用语言切换
- 浅色、深色、AMOLED 和动态颜色主题
- 可配置播放器后端和手势行为
- 「界面 → 播放区域设置 → 刘海屏模式」：竖屏始终避开刘海/挖孔；横屏可切换是否补偿安全区
- 首选音轨 / 字幕语言设置

### 1.1.0 不包含

- 下载与离线播放（已移除，PerthFin 为纯在线客户端）
- Android TV（已移除）
- Chromecast / Google Cast

## 编解码支持

实际支持取决于 Android 设备和播放后端。

### ExoPlayer（默认）

- 视频：H.263、H.264、H.265、VP8、VP9、AV1
- 音频：Vorbis、Opus、FLAC、ALAC、PCM、MP3、AAC、AC-3、E-AC-3、DTS、DTS-HD、TrueHD（部分由 FFmpeg 扩展提供）
- 字幕：SRT、VTT、SSA/ASS、PGSSUB（SSA/ASS 样式支持有限）

### libmpv

- 容器：MKV、MOV、MP4、AVI
- 视频：H.264、H.265、H.266、VP8、VP9、AV1
- 音频：Opus、FLAC、MP3、AAC、AC-3、E-AC-3、TrueHD、DTS、DTS-HD
- 字幕：SRT、VTT、SSA/ASS、DVDSUB
- 硬件解码异常时可以强制软件解码

### 转码

源视频码率超过所选档位，或容器/视频/音频/字幕格式不被当前播放器支持时，
PerthFin 会向服务端请求 H.264 + AAC 的 HLS 流，并把选中的音轨、字幕索引传给服务端；
选中的字幕会烧录进码流。视频码率低于档位且格式支持时直接透传。

## 环境要求

- Jellyfin Server 12.0 或更高
- Android 10（API 29）或更高
- 手机或平板；不支持 Android TV

## 安装

从 GitHub Releases 下载对应 ABI 的 APK：

- `perthfin-v1.1.0-libre-arm64-v8a.apk`：大多数现代手机
- `perthfin-v1.1.0-libre-armeabi-v7a.apk`：较老的 32 位设备
- `perthfin-v1.1.0-libre-x86_64.apk` / `x86`：模拟器和 x86 设备

APK 使用 release key 签名，直接安装即可；当前不通过 Google Play 分发。

## 构建

需要 JDK 17、Android SDK platform 37 和 build-tools 37.0.0。

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk

./gradlew :app:phone:assembleDebug
```

Debug APK 输出到 `app/phone/build/outputs/apk/libre/debug/`。

Release 构建：

```bash
./gradlew :app:phone:assembleRelease
```

Release APK 输出到 `app/phone/build/outputs/apk/libre/release/`。
正式分发前请配置自己的签名密钥。

## 版本与路线图

`1.1.0` 在 `1.0.0` 的基础上加入 DLNA 投屏、按码率的直连/转码策略、播放器底栏重做和刘海屏模式。后续将继续完善投屏兼容性、错误提示和播放器交互。

## 许可证与署名

PerthFin 使用 GNU General Public License v3.0 许可，详见 [LICENSE](LICENSE) 和
[NOTICE](NOTICE)。

原 [Findroid](https://github.com/jarnedemeulemeester/findroid) 项目版权归
Jarne Demeulemeester 及贡献者所有。PerthFin 的修改版权归 MeowPerth 所有。

上游 [Findroid](https://github.com/jarnedemeulemeester/findroid) 项目不对本仓库及其发布负责。
