# Changelog / 变更日志

## 1.8.0-neoforge

### 新增功能 / New Features

- 新增 Iron的法术与魔法书（铁魔法）联动 NPC：入魔骑士、暗影刺客、精灵守卫、圣素构造体，包含渲染、AI、生成、刷怪蛋、掉落与日记文本 /
  Added Iron's Spells 'n Spellbooks compatibility NPCs: Corrupted Knight, Shadow Assassin, Elf Templar and Holy
  Construct, with rendering, AI, spawning, spawn eggs, loot and diary text
- 新增大型结构：圣遗礼拜堂、精灵秘境、堕天圣堂，并适配 1.21 结构数据 / Added large structures: Relic Chapel, Elven Realm and
  Fallen Sanctum, with 1.21 structure data support
- 更新魔女足迹系列结构：蘑菇岛屋、林间栖所、墓园 / Updated Enchantress' Footsteps structures: Mushroom Island House, Woods
  Perch and Graveyard
- 新增默认精灵酒狐/圣女酒狐女仆模型包，供结构生成使用 / Added default Elf Wine Fox/Saint Wine Fox maid model pack for
  structure spawns
- 新增饰品：浮波狐叶、熔岩狐叶，支持女仆踏水/踏熔岩、轨迹方块、火焰保护与主人共享效果 / Added Floating Fox Leaf and Molten
  Fox Leaf baubles, supporting maid water/lava walking, trail blocks, fire protection and owner-shared effects
- 新增花朵与功能方块：猩红朱华、月铃兰、净墟幽兰、镇石及盆栽变体 / Added flowers and utility blocks: Scarlet Zhuhua, Yue
  Linglan, Jingxu Youlan, Suppression Stone and potted variants
-
月铃兰可为女仆提供恢复/抗性并指引精灵秘境，净墟幽兰可净化女仆负面效果并清除周围生物药水效果，镇石可阻止周围区块敌对生物自然生成 /
Yue Linglan grants maid Regeneration/Resistance and guides to Elven Realm, Jingxu Youlan cleanses maid harmful effects
and nearby mob effects, and Suppression Stone prevents nearby hostile natural spawns
- 新增全局友军识别系统，统一玩家、女仆、召唤物与投射物的归属判断 / Added a global ally resolver for players, maids, summons
  and projectiles
- 锚定核心新增非常规移除保护，可拦截异常移除、外部捕捉和实体转换并恢复被保护女仆 / Anchor Core now protects against hard
  removals, external capture and entity conversion, restoring protected maids

### 修复 / Bug Fixes

- 修复女仆对死亡或已移除实体继续施法的问题 / Fixed maids continuing to cast at dead or removed entities
- 修复新生魔艺（Ars Nouveau）、Psi 与铁魔法的女仆代理施法、召唤施法和重复施法兼容问题 / Fixed maid proxy casting, summon
  casting and recasting compatibility for Ars Nouveau, Psi and Iron's Spells 'n Spellbooks

### 优化 / Improvements

- 法术白名单命名统一（原蓝音符），调整铁魔法新版兼容与法术白名单目标逻辑 / Unified Spell Whitelist naming (formerly Blue
  Note) and adjusted new Iron's Spells 'n Spellbooks compatibility and target logic
- 优化多个模组兼容，包括铁魔法、诡厄巫法、新生魔艺与 Psi，并兼容最新版高版本巫术学 / Improved compatibility across multiple
  mods, including Iron's Spells 'n Spellbooks, Goety, Ars Nouveau and Psi, with support for the latest high-version Ars
  Nouveau
- 优化梦云水晶功能，增强非正面效果拦截和状态保护 / Improved Dreamcloud Crystal functionality, including harmful-effect
  blocking and state protection

---

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
