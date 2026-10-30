# TV Music

面向 Android TV / 电视盒子的音乐播放器，遥控器优先，大屏 960×540dp 基准设计，支持 4K 缩放与三档字号。

当前版本 **0.2.0 · M1a**：在 M0 的焦点与四页骨架之上，接入 **LX QuickJS 音源引擎、QQ 音乐原生搜索、Media3 真实播放**，形成“能搜、能取流、能播、能连播”的最小闭环。

> 本仓库不包含任何音源脚本、音频或歌词文件。本地构建会把用户放在仓库根目录 `音源/` 下的 `*.js` 注入 APK 供自用；开源构建不注入脚本。README 末尾有版权与许可说明。

## M1a 已实现

- **LX QuickJS 引擎**：移植自参考项目，支持多脚本独立上下文、`lx.request` 异步 HTTP、crypto/buffer、定时器、musicUrl 动作、同名源多脚本轮询。
- **本地音源**：构建时自动把 `音源/*.js` 同步到 assets，首次启动复制到应用私有目录；设置 → 音源可查看已加载脚本、注册源，并一键重新加载。
- **QQ 音乐搜索**：原生 zzcSign + QQ 搜索接口，返回歌名/歌手/专辑/时长/封面/ songmid 等元数据，30 首一页。
- **真实播放**：LX 脚本解析 URL → URL 探活 → Media3 ExoPlayer 播放；320k 失效自动尝试 flac / 128k；播放失败有顶部胶囊提示。
- **连续播放**：搜索结果的 30 首自动成为播放队列，下一首/上一首/队列切歌都会重新解析音源。
- **播放页**：真实进度/时长、暂停/续播、±10s、控制条、队列；在线曲目不再显示占位歌词，歌词时间轴留待 M2。
- M0 能力保留：焦点记忆、跨 Rail 列索引、设置持久化、搜索/播放返回栈、长按返回首页、迷你播放条自动隐藏。

## 遥控器操作

| 按键 | 作用 |
| --- | --- |
| 方向键 | 移动焦点；Rail 上下切换并保持列索引 |
| OK | 进入 / 播放 / 暂停 / 切换设置项 |
| 返回 | 关闭队列 → 返回上一级 |
| 长按返回 | 回到首页 |
| 播放页 ← / → | 快退 / 快进 10 秒 |
| 播放页长按 ← / → | 上一首 / 下一首 |
| 播放页 ↑ / ↓ | 显示 / 收起控制条；↓ 再按打开队列 |

搜索页当前可通过热搜、历史词或九宫格数字发起搜索；拼音/中文 T9 词典尚未接入（见待办）。

## 构建与安装

环境：JDK 17、Android SDK Platform 36、Android SDK Build-Tools 36。

```bash
# 可选：把自用音源放到仓库根目录 音源/*.js，构建时会自动注入
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

产物：

- Debug：`app/build/outputs/apk/debug/app-debug.apk`（包名 `tv.music.player.debug`）
- Release：`app/build/outputs/apk/release/app-release.apk`（包名 `tv.music.player`）

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

`音源/` 与同步生成的 `app/src/main/assets/lx/` 均已加入 `.gitignore`，不会进入开源仓库。Release 目前沿用 debug 签名，仅用于自测与真机试用；正式分发前请配置自己的签名。

## 目录结构

```
app/src/main/java/tv/music/player/
├── MainActivity.kt            边到边、系统栏、返回键短按/长按
├── core/
│   ├── lx/                    LX QuickJS 引擎、HTTP、脚本存储、模型
│   ├── search/                QQ 搜索、在线歌曲模型、结果编解码
│   ├── playback/              Media3 播放控制器、演示曲目
│   ├── focus/                 FocusGroup、焦点记忆、TvSurface
│   ├── settings/              DataStore 设置
│   ├── crash/                 本地崩溃日志
│   └── theme/                 颜色、字体、主题色
└── ui/
    ├── components/            Rail、卡片、胶囊、骨架、空态
    ├── home/                  首页
    ├── search/                搜索与结果
    ├── player/                播放页与队列
    ├── settings/              设置主页、分组页、音源状态卡
    └── navigation/            NavHost 与路由
```

## 路线图

- **M0**：工程骨架、焦点、四页导航、搜索演示、播放页骨架、设置持久化。已完成。
- **M1a（当前）**：LX 引擎、QQ 搜索、真实播放与连续播放、音源状态。
- **M1b**：中文拼音 T9、分页、歌词时间轴与歌词源、MediaSessionService/通知/媒体键、收藏与 Room、音源导入与源测试。
- **M2**：歌词全样式（含逐字/QRC）、布局切换、歌词偏移。
- **M3**：收藏/歌单/历史/下载、手机伴侣。
- **M4**：本地库（USB/SMB）、音源市场与安装校验。
- **M5**：发布、更新与打磨。

## 已知限制（M1a）

- 搜索仅支持热搜/历史/数字；**拼音首字母、全拼与中文 T9 尚未实现**。
- 只展示第一页 30 首，无分页/加载更多。
- 歌词为占位，未接入 LX lyric 与时间轴。
- 播放器为 Activity 内 ExoPlayer，尚无 MediaSessionService、通知栏与媒体键；退到后台的长期播放未做专项验证。
- 音源导入（U 盘/URL）、启停排序、源测试、音源市场仍为占位。
- 收藏、最近播放、播放次数未做 Room 持久化。
- Release 使用 debug 签名、R8 关闭，包体偏大；4K 缩放逻辑已接入，尚未在 4K 真机逐项验收。
- 已在 1080p 横屏模拟器（SwiftShader 软件渲染）验证交互与播放；模拟器帧率数据不代表真机。

## 版权与许可

本项目代码以 **GNU General Public License v3.0** 发布，见 [LICENSE](LICENSE)。第三方组件与参考项目声明见 [NOTICE](NOTICE)。

音源脚本由用户自行提供，版权与许可归脚本作者；本仓库不分发脚本。页面中的演示曲名/歌手/榜单为占位数据。在线音源可能涉及版权与平台风控，请仅在合法授权范围内使用。
