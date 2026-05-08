# Changelog / 变更日志

## 1.7.3-neoforge

### 新增功能 / New Features

- 新增饰品：梦云水晶，包含合成表、效果黑白名单、负面效果免疫和真伤开关 / Added new bauble: Dreamcloud Crystal, with recipe, effect blacklist/whitelist, negative-effect immunity and true damage toggle
- 补充晋升光环的冰雹云转换效果 / Added hailstorm cloud conversion effect for Ascension Halo
- 新增「万法皆通」成就 / Added "Master of All Spells" advancement
- 旧版材质作为可选资源包提供 / Legacy textures provided as optional resource pack
- 新增 /hurt 调试命令和重置隐世之境额度命令 / Added /hurt debug command and Hidden Retreat quota reset command

### 修复 / Bug Fixes

- 修复女仆无法受到任何伤害的问题 (#46) / Fixed maid being unable to take any damage (#46)
- 修复女仆攻击目标不识别召唤物的问题 (#42) / Fixed maid attack target not recognizing summoned mobs (#42)
- 修复未安装铁魔法时无法启动的问题 / Fixed unable to launch without Iron's Spellbooks
- 修复归隐之地相关问题：错误调度主世界函数 (#41)、私人模式下无法限制隐世之境生成、与机械动力和瓦尔基里的兼容性 / Fixed multiple The Retreat issues: incorrect overworld function scheduling (#41), Hidden Retreat structure generation not restricted in private mode, compatibility with Create and Valkyrien Skies
- 修复发簪、双心之链、混沌之书等饰品在真伤场景下的兼容问题 / Fixed compatibility issues for Hairpin, Double Heart Chain, Chaos Book and other baubles under true damage
- 修复部分数据包、翻译键缺失和材质动画问题 / Fixed various datapack, missing translation key and texture animation issues

### 优化 / Improvements

- 同步 1.20 分支的饰品内容与兼容性改动 / Synced bauble content and compatibility changes from 1.20 branch
- 归隐之地传送逻辑优化，不再将玩家重生点设置到该维度 / The Retreat teleportation logic optimized, no longer sets player respawn point to this dimension
- 梦云水晶仇恨、tooltip 与状态追踪优化 / Dreamcloud Crystal aggro, tooltip and state tracking improvements
- 真伤实现调整并迁移至 coremod / True damage implementation refined and moved to coremod
- 一些性能优化和逻辑修复 / Various performance optimizations and logic fixes

---

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
