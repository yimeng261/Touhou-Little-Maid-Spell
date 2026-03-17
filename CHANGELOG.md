# Changelog / 变更日志

## 1.7.0-neoforge

### 新增功能 / New Features

- 更新隐世之境结构，修改女仆模型 / Updated Hidden Retreat structure, modified maid model
- 诡厄巫法兼容（仅结构，聚晶功能暂未实现）/ Goety compatibility (structure only, focus crystal feature not yet implemented)
- 添加构建流水线 / Added build pipeline
- 寻风之铃渲染优化，修复不飞出问题 / Wind Seeking Bell rendering optimization, fixed projectile not launching issue
- 寻风之铃限制最大飞行高度 / Added maximum height limit to Wind Seeking Bell
- 归隐之地支持配置结构生成白名单 / The Retreat dimension now supports configurable structure generation whitelist
- 添加配置用于禁用归隐之地中敌对生物生成 / Added config option to disable hostile mob spawning in The Retreat
- 兼容 C2ME，保证隐世之境区块分配并发安全 / Added C2ME compatibility with concurrency-safe chunk allocation for Hidden
  Retreat

### 修复 / Bug Fixes

- 修复药水效果缺少引用问题 (#37) / Fixed missing potion effect reference issue (#37)
- 修复女仆妖精咖啡厅依赖 / Fixed Fairy Maid Cafe dependencies
- 修复寻风之铃不消耗的问题 / Fixed Wind Seeking Bell not being consumed
- 避免隐世之境在其他地方生成，允许隐世樱花树在隐世之境内生成 / Prevented Hidden Retreat from generating in unintended
  locations, allowed Hidden Cherry Trees to generate inside it
- 避免 locate 指令搜索隐世之境（避免可能的崩服）/ Prevented locate command from searching Hidden Retreat (avoids potential
  server crash)
- 避免可选模组未安装而发生类加载错误 / Prevented class loading errors when optional mods are not installed
- 处理 ServerLevelAccessor 避开渲染用的逻辑世界，修复与 Create 模组的兼容问题 / Handle ServerLevelAccessor to skip
  render-only logic levels, fixing compatibility with Create mod
- 移植 main 分支的关键 bug 修复 / Ported critical bug fixes from main branch

### 优化 / Improvements

- 优化隐世之境地形检测与寻风之铃搜索性能 / Optimized Hidden Retreat terrain detection and Wind Seeking Bell search
  performance
- 隐世樱花树生成添加水面检测，避免生成在水面上 / Added water surface detection when generating Hidden Cherry Trees to
  prevent floating on water
- 优化水域检测逻辑，优化 C2ME 兼容 / Optimized water area detection logic and C2ME compatibility
- 目标管理统一使用 Brain 的 ATTACK_TARGET 记忆 / Unified target management using Brain's ATTACK_TARGET memory
- 锚定核心白名单更新 / Updated Anchor Core whitelist
- 归隐之地维度参数调整 / Adjusted The Retreat dimension parameters
- 补充缺少的翻译键 / Added missing translation keys

---

## 1.6.5.2-neoforge

### 新增功能 / New Features

- 更新妖精女仆咖啡厅结构 / Updated Fairy Maid Cafe structure
- 更新远程法术图标 / Updated ranged spell icons

### 修复 / Bug Fixes

- 修复锚定核心女仆跟随消失问题 (#33) / Fixed issue where maids disappeared when following with Anchor Core (#33)
- 修复寻风之铃不消耗的问题 / Fixed Wind Seeking Bell not being consumed
- 避免女仆妖精咖啡厅女仆模型替换，修复结构 / Prevented Fairy Maid Cafe maid model replacement, fixed structure
- 避免隐世之境在其他地方生成，允许隐世樱花树在隐世之境生成 / Prevented Hidden Retreat from generating elsewhere, allowed
  Hidden Cherry Trees to generate in Hidden Retreat
- 避免 locate 指令搜索隐世之境（避免可能的崩服） / Prevented locate command from searching Hidden Retreat (avoiding
  potential server crashes)
- 避免可选模组未安装而发生类加载错误 / Prevented class loading errors when optional mods are not installed

### 优化 / Improvements

- 归隐之地维度调整 / Adjusted The Retreat dimension
- 传送到归隐之地时异步区块加载 / Asynchronous chunk loading when teleporting to The Retreat
- 锚定核心白名单更新 / Updated Anchor Core whitelist

---
