# TV Music

面向 Android TV / 电视盒子的音乐播放器骨架，遥控器优先，大屏 960×540dp 基准设计，支持 4K 缩放与三档字号。

当前版本 **0.1.0 · M0**：完成可运行的导航、焦点系统、搜索演示页、播放页骨架与设置持久化。**M0 不包含真实音源与音频播放引擎**，播放页使用演示数据与计时器模拟进度。

## 已实现（M0）

- 首页：问候语、时钟、网络状态、顶部搜索框（默认焦点）、5 个快捷入口、继续听 / 猜你喜欢 / 热门榜单三条 Rail。
- 焦点系统：FocusGroup 记忆、跨 Rail 保持列索引、上下移动自动滚动目标 Rail、返回页面恢复上次焦点、冷启动默认落在搜索框。
- 浏览 Rail 时自动隐藏底部迷你播放条；回到顶部区域自动恢复。
- 搜索：T9 九宫格、热搜与最近搜索、加载骨架、结果列表；数字键直接输入，MENU 提示预留输入法切换。
- 播放页：布局 A 全屏歌词、封面主色背景（可关闭模糊）、进度条、控制条、播放队列侧栏；支持控制条/队列焦点记忆。
- 返回栈：短返回上一级，长按返回回到首页（700ms）。
- 设置：音源 / 账号 / 本地库 / 外观 / 播放 / 关于六组；主题色 5 种预设 + 跟随封面、界面动画开关、模糊背景开关、歌词字号；DataStore 持久化，即时生效。
- 基础设施：TvScaleProvider（960×540dp 基准 + 4K 缩放）、安全区、崩溃日志本地落盘、顶部胶囊通知替代 Toast。

## 遥控器操作

| 按键 | 作用 |
| --- | --- |
| 方向键 | 移动焦点；在 Rail 中上下切换行并保持列索引 |
| OK | 进入 / 播放 / 暂停 / 切换设置项 |
| 返回 | 关闭队列 → 返回上一级 |
| 长按返回 | 回到首页 |
| 播放页 ← / → | 快退 / 快进 10 秒 |
| 播放页长按 ← / → | 上一首 / 下一首 |
| 播放页 ↑ | 显示控制条 |
| 播放页 ↓ | 收起控制条 / 打开队列 |

## 构建

环境：JDK 17、Android SDK Platform 36、Android SDK Build-Tools 36。

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

产物：

- Debug：`app/build/outputs/apk/debug/app-debug.apk`（包名 `tv.music.player.debug`）
- Release：`app/build/outputs/apk/release/app-release.apk`（包名 `tv.music.player`）

安装：

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

Release 目前沿用 debug 签名配置，仅用于自测与真机试用；正式分发前请配置自己的签名，并重新构建。

## 目录结构

```
app/src/main/java/tv/music/player/
├── MainActivity.kt            边到边、系统栏、返回键短按/长按
├── core/
│   ├── crash/                 本地崩溃日志
│   ├── focus/                 FocusGroup、FocusRequester 记忆、TvSurface
│   ├── scale/                 960×540dp 基准与缩放
│   └── theme/                 颜色、字体、主题色与持久化设置
├── data/
│   ├── DemoCatalog.kt         演示曲目/榜单数据
│   ├── DemoPlaybackController.kt 计时器模拟播放
│   └── settings/              DataStore 设置仓库
└── ui/
    ├── components/            Rail、卡片、胶囊、骨架、空态、MarqueeText
    ├── home/                 首页
    ├── search/               搜索
    ├── player/               播放页与队列
    ├── settings/             设置主页与二级分组页
    └── navigation/           NavHost 与路由
```

## 路线图

- **M0（当前）**：工程骨架、焦点、四页导航、搜索演示、播放页骨架、设置持久化。
- **M1**：LX 插件式音源、搜索真实曲库、歌词时间轴。
- **M2**：自定义播放引擎、音效、无缝切歌、后台播放。
- **M3**：收藏、歌单、历史、下载与缓存。
- **M4**：本地库（USB / SMB）增量索引，本地与在线混合队列。
- **M5**：歌词动效、K 歌、发布与更新。

## 已知限制（M0）

- 无真实音源与播放引擎，进度与歌词为模拟。
- 设置分组内的部分操作为占位（音源安装、账号绑定、本地扫描等）。
- R8/资源压缩暂未启用，Release APK 体积偏大。
- “关于”页的许可与崩溃日志入口为占位，声明文本以仓库根目录 `LICENSE` / `NOTICE` 为准。
- 已在 1080p 横屏模拟器（SwiftShader 软件渲染）验证交互；4K 缩放逻辑已接入，尚未在 4K 真机逐项验收。

## 许可

本项目以 **GNU General Public License v3.0** 发布，见 [LICENSE](LICENSE)。
参考项目与第三方组件声明见 [NOTICE](NOTICE)。

仓库不包含任何音源插件、LX 脚本、音频或歌词文件；页面中的曲名、歌手与榜单为演示占位数据。
