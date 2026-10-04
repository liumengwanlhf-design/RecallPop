# RecallPop · 随时复习一张

一个实验性的 Android AnkiDroid 伴侣：把少量单卡复习放进解锁、持续使用手机和自愿开启的锁屏亮屏时刻。卡片、到期安排、评分、FSRS 和复习历史仍由 AnkiDroid 管理，RecallPop 不维护另一套卡库或调度器。

当前版本为 **0.71-test**（versionCode 8），GitHub 标签为 `v0.71.0-test`。适合愿意自行检查行为的试用者；本项目与 Anki / AnkiDroid 官方没有隶属关系。

[下载 0.71 测试 APK](https://github.com/liumengwanlhf-design/RecallPop/releases/tag/v0.71.0-test) · [Pages 下载站](https://pop-382910.pages.dev/) · [版本记录](CHANGELOG.md) · [0.71 验证说明](docs/0.71-testing.md)

项目原发布名为 AnkiGate，现更名为 RecallPop。安装后的显示名称仍为“Anki 解锁复习”，包名仍为 `dev.ankigate`；下载文件名的变化不影响应用数据。

0.71 增加实时运行状态、最多60条近期运行记录，以及系统允许的开机和覆盖更新恢复入口。0.7 引入的达标门槛和用户主动下载安装更新也保留。版本的详细变化与验证边界见 [CHANGELOG](CHANGELOG.md)。

## 它会做什么

- 解锁优先提醒；已解锁且持续使用时，以随机 2–5 分钟的累计间隔尝试展示一张到期卡。两种模式可分别关闭。
- 可选的熄屏亮屏复习：默认每天 09:00–23:00，熄屏后随机请求 5–10 分钟一次。时段和间隔可在界面修改，系统待机与厂商策略可能推迟实际展示。
- 卡面结束后保护 90 秒，每天自动展示最多 150 次。没有到期卡就不制造新复习，未答关闭只计展示，不评分、不计完成。
- “复习一张”提供主动入口，不依赖自动服务、不消耗自动展示配额。评分由用户点击，并真实提交到 AnkiDroid；确认后才计本应用的完成次数。
- “今日达标复习”默认只计 API 确认成功的“良好”和“简单”；可改为所有评分或仅简单。“困难”和“重来”仍正常提交，新达标统计不回填0.6旧记录。
- 主界面和常驻运行通知均可“停止介入”，停止服务、关闭卡面并取消亮屏任务。停止是全局停用，已发出的评分请求不能保证撤销。

应用使用用户启动的前台服务与普通悬浮窗，**无需无障碍权限**。它不读取其他应用的前台包名、页面或应用切换，因此其他应用的设置页、AnkiDroid 页面也可能被卡面覆盖。本应用的设置、授权配置、主动复习和更新页面期间会暂停自动介入。

## 安装与首次配置

需要 Android 11 或更新版本，以及已安装并有可复习牌组的 [AnkiDroid](https://ankidroid.org/)。当前 API 字段核对以 AnkiDroid 2.24.0 为基准，其他版本的兼容性未完整验证。

1. 从 Releases 下载 APK，在 Android 安装界面允许对应下载来源安装，然后打开“Anki 解锁复习”。首次安装默认停用自动介入。
2. 点击“授权 AnkiDroid / 刷新牌组”，同意 AnkiDroid 数据库 API 权限，并在下拉框中确认要复习的牌组。评分会写入这个牌组的真实复习记录。
3. 如使用图片、声音等媒体，点击“授权现有 collection.media 目录”，通过系统文件选择器选择 AnkiDroid 当前实际使用的媒体目录。这里只申请读取权限，不复制媒体。
4. 授权悬浮窗与运行通知，再点击“启动 / 恢复前台服务”。服务运行时会有常驻通知，可随时停止。
5. 需要熄屏亮屏复习时，再授权“精确闹钟”，明确打开“允许锁屏上亮屏显示我的卡片”。可用“测试亮屏提醒（10秒后）”检查：点击后熄屏；测试仍遵守模式、权限、时段、保护与每日上限。

纯文字卡无需媒体目录；包含媒体的卡在所需文件不可读时不会进入评分流程。卡型、模板脚本和音频的实际兼容性仍需逐项试用。

### 更新应用

0.6 没有应用内更新入口，首次升级请从上方 GitHub 或 Pages 下载0.71 APK并按系统提示覆盖安装，保留原设置。0.7及之后可在应用内检查更新、查看说明，再主动下载；程序校验大小、摘要、包名、版本和签名，用户另行点击后才打开系统安装器，最后由 Android 确认覆盖。首次需为 RecallPop 允许“安装未知应用”。

默认更新说明为 `https://pop.382910.xyz/update.json`，可在应用内修改为其他 HTTPS 地址。离开更新页会取消网络操作，不静默安装；实际手机网络和完整覆盖安装流程尚待验收。自己编译的安装包可能无法覆盖现有包，见下方构建说明。

### 媒体目录与 AnkiDroid 版本

Android 11 起，系统文件选择器限制其他应用访问 `Android/data`。Google Play 版 AnkiDroid 的媒体若位于其受限应用目录，RecallPop 无法通过自己的授权按钮解除这项限制。

媒体已经位于共享且可授权的目录时，直接选择其 `collection.media`。若位于受限目录，需要先查阅 [AnkiDroid 官方完整存储访问说明](https://github.com/ankidroid/Anki-Android/wiki/Full-Storage-Access)，确认当前安装来源、签名与数据位置，并按官方流程处理。官方 GitHub 发布的完整访问安装包标为 `full-universal.apk`；其他来源或 Parallel 版本的安装与数据位置可能不同，不能假定都可直接覆盖。正常配置 RecallPop 不需要开发者调试命令。

更换 AnkiDroid 版本或调整集合位置前，先在 AnkiDroid 完成备份与同步，按官方说明操作并确认卡片及媒体完整。不要仅为本应用直接移动、覆盖或重新导入原集合，也不要把备份包当作媒体目录选择。

### 锁屏与厂商后台限制

亮屏模式会把卡片内容直接显示在系统锁屏上，旁人可能看到；请按自己的卡片内容决定是否开启。系统锁会保留，应用不会解锁设备。实际显示后，无人触摸时最多暂保持亮屏 30 秒并退出；第一次真实触摸会取消这次保持与退出计时，之后由系统管理熄屏。退出卡片不保证立即灭屏。

部分厂商需要分别允许悬浮窗、后台运行、后台弹出界面和锁屏显示。realme UI 7 的测试中，需要检查本应用的“耗电管理 → 完全允许后台行为”“特殊应用权限 → 后台弹出界面”，以及首次锁屏显示提示；AnkiDroid 冷取卡还可能需要其“关联启动”许可。Android 原生电池“无限制”不一定代替这些厂商设置。菜单名称随系统而异，只调整相关应用的许可即可。

0.71 在此前启用、开机后已完成凭据解锁且必要许可满足时，尝试在开机或覆盖更新后请求恢复前台服务。用户停止后保持停止，不绕过系统强行停止；恢复失败仍可手动打开并启动。前台服务不保证永远运行，系统及厂商仍可能延迟或拒绝任务。服务中断不追补离线期间的提醒，也不把离线时间算入持续使用时间。

主页运行状态依据当前服务实例，开启开关和旧运行记录不代表现在存活。“预计下次检查”不保证届时有到期卡或系统准时投递。没有弹卡时，可先检查状态、许可、保护期和每日上限，再查看近期运行记录。

## 为什么尝试这种方式

项目把主动回忆放进手机使用中的短暂机会，间隔练习仍由 AnkiDroid 的调度决定。这里的具体触发频率、随机间隔、锁屏亮屏和每日目标都是实验设置；尚无证据证明这些设置优于正常使用 AnkiDroid，也不保证达到界面的每日完成目标或改善学习效果。

## 当前验证程度

0.71 已有原开发环境的离线构建、主机策略和源码边界检查证据；本次发布整理通过只读源码检查，未重新构建或进行手机操作。0.6 曾在一台 realme UI 7 设备完成锁屏卡显示、真实首触、用户评分并由 API 确认、无人触摸约30秒退出且不评分的短流程，此证据不代表0.71重新验收。见 [0.71说明](docs/0.71-testing.md) 和 [0.6历史说明](docs/0.6-testing.md)。

0.71手机覆盖安装、应用内升级、实际开机及更新恢复、强行停止边界、自然随机投递、拔 USB 后长期待机、全部卡型及音频、AnkiWeb 同步和 Android lint 仍未完整验收。十秒测试或调试触发不能代替自然间隔与长期证据。遇到评分结果未知时在 AnkiDroid 核对，避免重复提交。

## 构建

仓库保留与当前0.71 APK对应的运行源码。配置固定为 Java 17、Android SDK Platform 36、Android Gradle Plugin 9.4.0、Gradle wrapper 9.6.0；本项目没有额外的应用库依赖。需要能访问 Google Maven、Maven Central 和 Gradle 分发服务。

安装符合这些版本要求的 JDK 和 Android SDK，设置 `JAVA_HOME` 与 `ANDROID_HOME`，或在本地 `local.properties` 配置 `sdk.dir`。不要把个人 SDK 路径提交进仓库。原开发环境已成功执行 `assembleDebug`；发布整理时未重新运行构建，新的干净环境构建尚未验证。

Windows PowerShell：

```powershell
.\gradlew.bat assembleDebug
```

Linux / macOS：

```sh
chmod +x gradlew
./gradlew assembleDebug
```

产物为 `app/build/outputs/apk/debug/app-debug.apk`，包名 `dev.ankigate`，versionCode `8`，versionName `0.71-test`。GitHub 标签 `v0.71.0-test` 对应该应用版本；GitHub自动提供源码 ZIP / tar.gz。

Releases 当前提供的是可调试的试用 APK，没有承诺长期固定的正式发布证书。自己构建的 APK 通常使用自己机器上的调试签名；签名不同就不能覆盖安装本仓库发布的同包名 APK。不要为了覆盖安装而直接卸载，卸载会丢失本应用的牌组选择、媒体授权和统计；AnkiDroid 集合由 AnkiDroid 管理。构建 release 版本需要自行配置并妥善保管签名密钥，密钥不属于本仓库。

### 主机检查

安装 Node.js 后可执行只读源码边界检查：

```sh
node tests/check-review-boundaries.mjs
```

`tests/check-anki-contract.mjs` 还需要一个外部的官方 AnkiDroid v2.24.0 `FlashCardsContract.kt` 文件路径作为参数；仓库不附带该文件。`tests/dev/ankigate` 的 Java 检查覆盖策略、随机范围、统计并发与时间窗口，需要用 JDK 编译相应生产类及 `tests/android/content` 替身后运行；替身不打入 APK。参考文件只用于开发分析，不是运行依赖。

## 权限与数据

AnkiDroid 数据库权限用于读取到期卡及写入用户评分；悬浮窗用于普通卡面；通知与前台服务用于明确显示运行状态；精确闹钟与短时 CPU 唤醒锁用于可选亮屏流程。开机权限用于已启用状态的恢复请求，安装来源权限用于用户确认的系统覆盖安装。网络权限用于更新及卡片模板可能引用的网络内容。

本项目没有账户、分析 SDK 或后台服务器，不建立第二份集合数据库，不复制现有媒体。牌组选择、媒体目录授权、开关和本应用统计保存在本机。0.71近期记录最多60条，仅记录运行事件，不保存卡面、媒体名或卡片身份；开发调试输出仍可能包含卡片标识与评分状态，反馈前请清除个人内容、目录与设备标识。

## 许可与参考资料

原创代码与文档使用 [MIT License](LICENSE)。Gradle wrapper 与 `tests/references` 中的 Android 源码保持各自的 Apache-2.0 许可及原始声明，见 [第三方说明](THIRD_PARTY_NOTICES.md)。本项目不附带 AnkiDroid、用户集合或媒体。

- [AnkiDroid 官网](https://ankidroid.org/)
- [AnkiDroid 官方用户手册](https://docs.ankidroid.org/)
- [AnkiDroid 官方发布](https://github.com/ankidroid/Anki-Android/releases)
- [AnkiDroid 2.24.0 官方发布](https://github.com/ankidroid/Anki-Android/releases/tag/v2.24.0)
- [AnkiDroid 完整存储访问说明](https://github.com/ankidroid/Anki-Android/wiki/Full-Storage-Access)
- [AnkiDroid 官方 API](https://github.com/ankidroid/Anki-Android/wiki/AnkiDroid-API)
