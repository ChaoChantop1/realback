# 回真 RealBack

> 让手机回归工具，让人回归现实。
> An open-source digital wellbeing app: intervene screen time, grow real-life habits.

**RealBack = 屏幕干预 × 习惯成长 双引擎闭环。** 它帮你看清自己如何使用手机（提醒式干预，而非硬性锁机），并把省下来的时间导入习惯与目标，让成长可视化。

## 项目状态

**MVP（M0–M5）全部完成。** 路线图：

| 里程碑 | 内容 | 状态 |
|--------|------|------|
| M0 | 多模块骨架 + core 领域模型 + Room schema + CI | ✅ |
| M1 | 用量采集（UsageStatsManager）+ 屏幕时间仪表盘 | ✅ |
| M2 | 应用限额 + 提醒式干预（呼吸确认页）+ 电池优化引导 | ✅ |
| M3 | 习惯引擎（稀疏打卡→稠密历史/连续天数/强度/周进度）+ 打卡 UI | ✅ |
| M4 | 专注计时（时间戳驱动，退后台不丢）+ 成长值/等级/成就闭环 | ✅ |
| M5 | JSON 备份导出/导入 + 自适应图标 + 发布流水线 | ✅ |

### 下载与发布

- 每次 push 到 main：CI 构建的 debug APK 在 Actions 产物中
- 推送 `v*` 标签：CI 自动构建 release APK（R8 混淆）并附到 GitHub Release
- 当前 release APK 为 debug 签名（便于安装体验）；正式商店分发前需替换为专属 keystore（见 `app-android/build.gradle.kts` 注释）
- **首个可用版本是 v0.1.1**。v0.1.0 的 Release 没有附带 APK，因为它的构建实际失败了（见下文「CI 教训」），请勿使用

### CI 教训：管道吞掉了构建失败（已修复）

**v0.1.0 期间的所有"CI 全绿"都是假象**：Android 模块自 M1 起从未真正编译成功过。

根因：CI 步骤写作 `gradle ... | tee build.log`，而 GitHub Actions 默认 shell（无 `pipefail`）的管道退出码取自**最后一个命令**（tee，永远成功）。Gradle 编译失败被静默吞掉，job 报绿——直到发布时发现 Release 没有附带任何 APK 才暴露。

连锁修复（共 5 轮，全部是此前被掩盖的真实错误）：

1. CI 步骤加 `set -o pipefail`，失败如实传播
2. `Icons.Filled.Schedule` 不在 material-icons-core 基础图标集 → 换 `DateRange`
3. `FocusScreen` 缺 `height` import
4. `LimitsScreen` 对话框 lambda 误用 `)` 收尾；测试 Long→Int 类型不匹配
5. core 属性初始化顺序（`ticker` 在使用之后声明）；习惯强度分窗口缺陷——固定 180 天回溯把习惯创建前的日子算作"未完成"，新习惯分数被惩罚，改为从最早打卡记录起算

**沉淀的规则**（已写入本仓库的 CI 配置）：

- 凡 `命令 | tee 日志` 的 CI 步骤必须 `set -o pipefail`，否则退出码不可信
- 验收标准是「**产物存在**」（APK 出现在 artifacts/Release 附件中），不是绿色对勾
- tag 触发的 Release job 直接以 APK 路径为上传输入——构建失败时无产物可传，发布不可能再假绿

### 备份格式

导出为版本化 JSON（`version: 1`），包含习惯、打卡、限额规则、成长值；用量明细不含（可由系统重统计）。未来版本迁移时保持向后兼容。

## 架构

```
realback/
├── core/          # 纯 Kotlin，零 Android 依赖（为 Kotlin Multiplatform / iOS 预留）
│   ├── habit/     #   习惯模型与强度算法（M3 将替换为 uhabits-core）
│   ├── usage/     #   用量聚合与干预规则引擎（只做提醒式干预）
│   ├── growth/    #   成长值/等级闭环
│   └── repo/      #   仓库接口（由平台层实现）
└── app-android/   # Android 应用（Compose + Room + 前台服务）
    ├── data/db/   #   Room 实体/DAO 与领域模型映射
    ├── service/   #   UsageTrackingService（对抗 OEM 杀后台）
    └── ui/        #   Compose 界面
```

**核心约束**：`core` 模块禁止任何 Android 依赖，保证 Phase 4 可以用 KMP 平移到 iOS。

## 构建

需要 JDK 17 + Android SDK 35。在 Android Studio 中打开根目录即可，或命令行：

```bash
# 仅运行 core 单元测试（不需要 Android SDK）
./gradlew :core:test

# 构建 Android APK
./gradlew :app-android:assembleDebug
```

> 注：Gradle wrapper (`gradlew`) 会在你首次用 Android Studio 打开项目或本地安装 Gradle 后生成并提交。

## 设计红线（来自竞品 Issue 实证）

1. **不做内容级拦截/网站过滤** —— 没有稳定的 Android API（参考 Mindful 在 Firefox 上失效的教训）
2. **不用 AccessibilityService 做硬拦截** —— Google Play 审核雷区
3. **干预一律提醒式**（呼吸确认页），尊重用户自主权
4. **离线优先**，MVP 无账号体系

## 许可证

[GPL-3.0](LICENSE)

本项目 M3 将集成 [uhabits-core](https://github.com/iSoron/uhabits)（GPL-3.0，© iSoron），按许可证要求整个项目以 GPL-3.0 发布。

## 致谢

- [Loop Habit Tracker](https://github.com/iSoron/uhabits) — 架构范式与习惯引擎
- [Mindful](https://github.com/akaMrNagar/Mindful) — 屏幕干预的产品形态与避坑清单
- [Habitica](https://github.com/HabitRPG/habitica) — 社交问责机制的产品灵感（未复用其代码与资产）
