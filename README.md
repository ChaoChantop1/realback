# 回真 RealBack

> 让手机回归工具，让人回归现实。
> An open-source digital wellbeing app: intervene screen time, grow real-life habits.

**RealBack = 屏幕干预 × 习惯成长 双引擎闭环。** 它帮你看清自己如何使用手机（提醒式干预，而非硬性锁机），并把省下来的时间导入习惯与目标，让成长可视化。

## 项目状态

M0–M3 已完成。路线图：

| 里程碑 | 内容 | 状态 |
|--------|------|------|
| M0 | 多模块骨架 + core 领域模型 + Room schema + CI | ✅ |
| M1 | 用量采集（UsageStatsManager）+ 屏幕时间仪表盘 | ✅ |
| M2 | 应用限额 + 提醒式干预（呼吸确认页）+ 电池优化引导 | ✅ |
| M3 | 习惯引擎（稀疏打卡→稠密历史/连续天数/强度/周进度）+ 打卡 UI | ✅ |
| M4 | 专注模式 + 成长值/成就闭环 | ⏳ |
| M5 | 数据导出/备份、F-Droid/GitHub 分发 | |

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
