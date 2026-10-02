# Changelog / 变更日志

## 1.9.1-neoforge

### 新增功能 / New Features

- 新增日文语言包，日记正文按当前分页排版；配置界面、部分结构实体与战利品名、《女仆与前文明研究记录》等内容暂以英文显示 /
  Added a Japanese translation with journals laid out to the current pagination; the configuration screen,
  some structure entities and loot names, Maids and the Previous Civilization: Research Records and other
  content still fall back to English

### 修复 / Bug Fixes

- 修复酒狐 Boss 的最大生命值读数与实际不一致的问题 /
  Fixed the Winefox boss reporting a maximum health that differed from its actual value

### 优化 / Improvements

- 星落之庭不再限制生物群系；旧存档补生成改为分多个 tick 写入，开服不再长时间卡顿，写入失败或崩服累计 3 次后不再重试 /
  Starfall Garden is no longer restricted by biome; retroactive generation for existing worlds now writes over
  several ticks so server startup no longer stalls, and gives up after three failed or crashed attempts

### 升级说明 / Upgrade Notes

- 梦云水晶不再支持玩家通过 Curios 佩戴，仅女仆可用；玩家饰品栏中的梦云水晶请在升级前取下 /
  Dreamcloud Crystal can no longer be worn by players through Curios and is now maid-only; remove it from
  player curio slots before upgrading

---

## 1.9.0-neoforge

### 新增功能 / New Features

- 新增「星之魔女」Boss：两阶段挑战、自定义血条与音乐、星芒短剑挑战配置、奖励及誓约女仆 /
  Added the Stellar Witch boss with a two-phase challenge, custom boss bar and music, Starglint Dagger challenge
  settings, rewards and an oath that grants a Stellar Witch maid
- 新增观星术士与观星罗盘：观星术士提供交易，罗盘可在末地寻找星途终岸 /
  Added the Astro Mancer merchant and Starwatch Compass, which locates Stellar Endshore in the End
- 新增末地结构「观星塔」「星途终岸」，包含观星术士、星之魔女、战利品和日记 /
  Added Starwatch Tower and Stellar Endshore structures in the End, featuring the Astro Mancer, Stellar Witch,
  loot and journals
- 新增主世界结构「星落之庭」与「启程之地」「星途终岸」进度；每份存档只生成一座庭院，并支持旧存档补生成 /
  Added Starfall Garden in the Overworld and the Where the Journey Begins and Stellar Endshore advancements;
  one garden generates per save, with retroactive generation supported for existing worlds
- 新增 9 个星之魔女系列铁魔法法术，其中「伴星黑洞」「破法回响」可正常获取，其余 7 个为专属法术 /
  Added nine Stellar Witch spells for Iron's Spells 'n Spellbooks: Companion Black Hole and Spellbreaking Echo
  are normally obtainable, while the other seven are exclusive spells
- 新增星影长剑、星影法杖、星之魔女法帽、星芒短剑，以及星锚珍珠、星云核心、星陨石、仪式剑柄与归星 /
  Added Starshadow Longsword, Starshadow Staff, Stellar Witch's Hat, Starglint Dagger,
  Staranchor Pearl, Nebula Core, Star Meteorite, Ritual Hilt and Returning Star
- 梦云水晶支持玩家通过 Curios 佩戴，提供属性强化、伤害保护、冷却清除、复活、耐久修复与饰品组合效果；祭坛配方改用星云核心 /
  Dreamcloud Crystal can now be worn by players through Curios, granting attribute bonuses, damage protection,
  cooldown resets, revival, durability repair and bauble synergies; its altar recipes now use Nebula Core
- 末影腰包支持视距外与跨维度远程管理女仆、滚动列表，以及女仆状态栏、位置编辑与逐只显示开关；装备锚定核心的女仆可作为传送目标 /
  Ender Pocket now supports maid management at long distances and across dimensions, a scrollable list,
  a maid HUD with position editing and per-maid visibility, and teleportation to maids equipped with an Anchor Core
- 新增魔女足迹-绿洲、天体／流星／魔法酒狐／星空花海四幅画作、星荧花簇，以及女仆手册里的「万法皆通」分类与条目 /
  Added Enchantress' Footsteps - Oasis, four paintings (Astronomical Object, Falling Star, Magic Wine Fox and
  Starry Flower Sea), Starshine Flower Cluster and the Mastery of All Spells category and entries in the maid handbook

### 修复 / Bug Fixes

- 修复女仆施法中收纳、卸载、跨维度、死亡或复活后的法术状态与冷却清理，避免状态串到其他存档；修复部分施法动画 /
  Fixed spell-state and cooldown handling when maids are stored, unloaded, change dimensions, die or revive,
  prevented state leaking between saves and corrected casting animations
- 修复铁魔法白名单增益法术在战斗中指向敌人、施放后未恢复攻击目标的问题 /
  Fixed whitelisted Iron's Spells 'n Spellbooks buffs targeting enemies during combat and failing to restore
  the combat target afterward
- 修复女仆佩戴梦云水晶时在受击间隔内重复追加伤害、时停生物卸载或重启后永久失去 AI 的问题 /
  Fixed maid Dreamcloud Crystal attacks adding damage during blocked hits and frozen mobs permanently losing AI
  after unloading or restarting
- 修复饰品卸下、女仆离开世界与真正死亡后的残留状态；被复活救下时保留仍应有效的饰品状态 /
  Fixed residual bauble state after unequipping, leaving the world or actual death, while preserving valid
  bauble state when revival prevents death
- 修复锚定女仆存盘、跨维度与重启后的数据和区块加载；主人离线时也能更新女仆位置，并正确移除失效锚定记录 /
  Fixed anchored maid data and chunk loading across saves, dimension changes and restarts; maid positions
  now update while owners are offline, and stale anchor records are removed correctly
- 修复私人归隐之地随机种子未独立生效、隐世之境重复生成、寻风之铃搜索中心错误与强加载区块残留；修正共享模式首次进入提示 /
  Fixed private Retreat seeds not taking effect independently, duplicate Hidden Retreat generation,
  incorrect Wind Seeking Bell search origins and lingering forced chunks; corrected shared-mode entry messages
- 修复已有结构模板中的物品、流体、女仆步高、效果属性与椅子数据，修正隐世樱花树高度、魔女足迹地基层与挂画位置 /
  Fixed item, fluid, maid step-height, effect-attribute and chair data in existing structure templates,
  and corrected Hidden Cherry Tree height, Enchantress' Footsteps foundations and painting positions

### 优化 / Improvements

- 适配 Goety 3.1.4，完善六种法术联动的兼容与生命周期管理 /
  Added compatibility with Goety 3.1.4 and improved compatibility and lifecycle handling across six spell integrations
- 完善玩家、女仆、宠物、召唤物与法术实体的友方识别，支持实用魔法召唤物；铁魔法黑洞与雷暴遵循友方归属 /
  Improved ally resolution for players, maids, pets, summons and spell entities, including UsefulMagic summons;
  Iron's Spells 'n Spellbooks Black Hole and Thunderstorm now respect ally ownership
- 完善可选模组缺席时的结构、配方、标签与战利品加载；未安装铁魔法时，隐世之境相关箱子提供替代战利品 /
  Improved structure, recipe, tag and loot handling when optional mods are absent; relevant Hidden Retreat chests
  provide fallback loot without Iron's Spells 'n Spellbooks
- 完善结构、物品与配置的中英文文本，补充旅行日记、入魔骑士日记和《女仆与前文明研究记录》的本地化与分页 /
  Improved English and Chinese structure, item and configuration text, and localized and repaginated travel journals,
  the Corrupted Knight diary and Maids and the Previous Civilization: Research Records
- 创造模式物品栏拆分为「饰品」「杂项」两页；优化结构搜索缓存、结构检测频率与归隐数据回写 /
  Split creative inventory content into Baubles and Miscellaneous tabs; improved structure-search caching,
  structure detection frequency and Retreat data updates
- 新增专用服、集成服、共享归隐、最小依赖与真实客户端回归测试，覆盖法术、饰品、结构及远程管理 /
  Added dedicated-server, integrated-server, shared-Retreat, minimal-dependency and real-client regression
  tests covering spells, baubles, structures and remote management

### 升级说明 / Upgrade Notes

- 星之魔女、观星术士、观星塔、星途终岸及新增铁魔法装备与法术需要安装铁魔法；星落之庭和画作不依赖铁魔法 /
  Stellar Witch, Astro Mancer, Starwatch Tower, Stellar Endshore and the associated equipment and spells require
  Iron's Spells 'n Spellbooks; Starfall Garden and paintings do not
- 车万女仆最低版本为 1.5.3；使用其自带的精灵酒狐与圣女酒狐模型，并自动清理旧版内置模型包；新增「星之魔女酒狐」模型包 /
  Touhou Little Maid 1.5.3 or newer is required; its built-in Elf Wine Fox and Saint Wine Fox models replace
  the old bundled pack, which is cleaned up automatically; a Stellar Witch Winefox model pack is added
- 网络协议已更新，客户端与服务端需一起升级；旧版自动结盟配置已移除，遗留的自动结盟队伍会自动清理 /
  The network protocol has changed, so clients and servers must upgrade together; automatic-alliance
  configuration was removed, and legacy automatically created alliance teams are cleaned up

---

## 1.8.4-neoforge

### 新增功能 / New Features

- 新增「UsefulMagic」法术联动：女仆可携带法术袋（法术书）与法杖，自动为法术球配对并装载法杖后施放弹幕/魔法阵类法术，冷却依法杖真实数值计算 /
  Added UsefulMagic integration — maids can carry spell bags and wands, automatically pairing and loading magic orbs to
  cast danmaku/magic-circle spells, with cooldowns derived from the wand's real values

---

## 1.8.3-neoforge

### 新增功能 / New Features

- 新增弧光十字饰品：女仆装备时持续获得 10 级雷暴与神圣守护 / Added the Arc Cross bauble — the equipped maid continuously
  gains Thunderstorm X and Holy Protection X

### 修复 / Bug Fixes

- 修复馥郁巧思导致女仆无法放出的问题 / Fixed Fragrant Ingenuity preventing maids from being deployed
- 修复 Carry On 删除同步与锚定核心区块加载问题 / Fixed Carry On removal syncing and Anchor Core chunk loading
- 清理女仆死亡与复活时的残留状态 / Cleaned up residual state left on maid death and revival
- 清理运行时残留的法术数据与缓存 / Cleaned up leftover runtime spell data and caches

### 优化 / Improvements

- Goety 依赖切换到官方发布版（`maven.modrinth:goety` 3.0.3），并适配官方版的类与包路径变更 /
  Switched the Goety dependency to the official release (`maven.modrinth:goety` 3.0.3) and adapted the integration to its
  class and package changes
- 优化通用盟友归属判定 / Optimized the shared ally-ownership resolution
- 缓存月铃兰结构搜索结果以降低开销 / Cached Yue Linglan structure-search results to reduce overhead
- 调整圣遗礼拜堂（Relic Sanctum）结构 / Adjusted the Relic Sanctum structure

---

## 1.8.2-neoforge

### 修复 / Bug Fixes

- 修复浮波狐叶和融岩狐叶无法合成的问题 / Fixed Floating Fox Leaf and Molten Fox Leaf being uncraftable
- 修正 minecraft tag 目录命名为 1.21 单数形式（`block`/`item`），修复花朵、花盆相关标签不生效的问题 / Renamed minecraft tag
  directories to the 1.21 singular form (`block`/`item`), fixing flower and flower-pot tags not taking effect
- 移除客户端启动时的二次异步资源重载，避免与初始 reload 产生竞态导致客户端卡死 / Removed the redundant async resource reload
  on client startup to avoid a race with the initial reload that could freeze the client

### 新增功能 / New Features

- 双心链新增最大生效距离配置（默认 32 格），超出距离时伤害不再分摊给主人 / Double Heart Chain now has a configurable max
  effective distance (default 32 blocks) — damage is no longer shared with the owner beyond this range
- 新增伤害分摊致死的死亡消息 / Added death messages for damage-sharing kills

### 优化 / Improvements

- 锚定核心放行 Carry On 模组的拾取行为，允许被携带 / Anchor Core now allows Carry On pickup behavior

---

## 1.8.1-neoforge

### 新增功能 / New Features

- 新增「拔刀剑：重逢」联动，女仆可装备拔刀剑自动施放连段或直接剑技 / Added Slash Blade: Resharpened integration — maids can
  wield SlashBlades and auto-cast combo or direct techniques

### 修复 / Bug Fixes

- 修复专用服务端清理女仆铁魔法 recast 时的崩溃 / Fixed dedicated-server crash when cleaning up maid Iron's Spells 'n
  Spellbooks recasts
- 解除对 Curios 的强依赖，将 Curios 相关逻辑迁移至独立 compat 模块，未安装 Curios 时不再影响梦云水晶等饰品功能 / Removed
  hard dependency on Curios — Curios-related logic moved to a dedicated compat module so Dreamcloud Crystal and other
  baubles work without Curios installed
- 修复入魔骑士和暗影刺客战利品表加载异常 / Fixed loot table loading errors for Corrupted Knight and Shadow Assassin

### 优化 / Improvements

- 更新圣遗礼拜堂结构文件 / Updated Relic Sanctum structure file

---

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
