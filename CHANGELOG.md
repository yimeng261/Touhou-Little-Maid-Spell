# Changelog / 变更日志

## 1.9.1-neoforge

### 新增功能 / New Features

- 女仆饰品栏可装备 Goety 的帽子、冠冕与长袍，Goety 能识别女仆身上的套装效果；不洁圣冠的光环版与普通版效果相同，光环按优先级渲染 /
  Maid bauble slots now accept Goety hats, crowns and robes, and Goety recognizes their set effects on maids;
  the halo variant of the Unholy Hat works the same as the regular one, with its halo rendered by priority
- 女仆施放 Goety 法术时会按聚晶与法术从背包中挑选法杖，整个施法过程都使用这根法杖 /
  Maids casting Goety spells now pick a wand from their inventory based on the focus and spell, and use that
  wand for the whole cast
- 新增法术射表：女仆佩戴后坐下或骑乘时也会索敌施法 /
  Added the Spell Range Table: maids wearing it also find targets and cast spells while sitting or riding
- 新增结构落樱厨舍（需农夫乐事）与下界魔女足迹系列：玄武岩锻坊、绯红林屋、魂谷一隅、诡林栖所（需铁魔法）；未装铁魔法或 Goety 时改用不含其内容的模板，战利品中的可选模组物品缺失时整张战利品表仍然可用 /
  Added the Cherry Cabin (requires Farmer's Delight) and the Nether Enchantress' Footsteps structures: Basalt
  Forge, Crimson Cabin, Soul Valley Nook and Warped Perch (require Iron's Spells 'n Spellbooks); without Iron's
  Spells 'n Spellbooks or Goety a template without their content is used, and missing optional-mod loot entries
  no longer break the whole loot table
- 下界魔女足迹附带中英日三语的旅行日记，可以和其他旅行日记一样与星之魔女交换星陨石 /
  The Nether Enchantress' Footsteps come with travel journals in Chinese, English and Japanese, which can be
  exchanged with the Stellar Witch for a Star Meteorite like the other journals
- 妖精女仆咖啡厅换用新模板，安装妖怪们的飨宴（Youkais' Feasts）或妖怪们的归家（含 1.21 非官方移植版）之一即可生成 /
  The Fairy Maid Cafe uses a new template and generates with either Youkais' Feasts or Youkai's Homecoming
  (including its unofficial 1.21 port) installed
- 隐世女仆按玩家记录归属，每名玩家只会遇到一次隐世女仆；女仆转为物品或重新放出后保留身份；管理员可用 `/maidspell_retreat_maid status|reset <玩家 UUID>` 查询或重置 /
  Hidden Retreat maids are now tracked per player, so each player meets one only once; the maid keeps her identity
  after being turned into an item and released again; operators can query or reset a player's claim with
  `/maidspell_retreat_maid status|reset <player UUID>`
- 兼容 Explorer's Compass 与 FTB Quests：探险家指南针中独立归隐之地显示为可读名称；FTB Quests 中以归隐之地为目标的维度任务在任意玩家的独立归隐之地里都算完成 /
  Explorer's Compass and FTB Quests compatibility: private Retreat dimensions show a readable name in Explorer's
  Compass, and FTB Quests dimension tasks targeting The Retreat also complete in any player's private Retreat
- 星之魔女新增法术「掌心黑洞」「七星引路」「三垣连星」：掌心黑洞为她的专属法术；七星引路不能合成或从战利品获得，可向星之魔女交易卷轴；三垣连星可正常获取。交易卷轴固定为 1 级 /
  The Stellar Witch gains three new spells: Palm Black Hole, Seven-Star Guidance and Three Enclosures. Palm Black
  Hole is exclusive to her; Seven-Star Guidance cannot be crafted or looted, but its scroll can be traded from the
  Stellar Witch; Three Enclosures can be obtained normally. Traded scrolls are always level 1
- 星之魔女新增动态减伤：短时间内承受的伤害越高，后续伤害削减越多，受到高伤一击后的下一击也会减轻；护甲、动态减伤与隐身目标搜索可在配置中调整，挑战配置界面新增护甲选项 /
  The Stellar Witch gains adaptive damage reduction: the more damage she takes in a short time, the more later hits
  are reduced, and the hit after a heavy blow is lessened; her armor, adaptive reduction and invisible-target search
  are configurable, and the challenge configuration screen adds an armor option
- 星之魔女挑战胜利给予经验；誓约所需的常规胜利从三次改为两次 /
  Defeating the Stellar Witch in a challenge now awards experience; the oath now requires two regular victories
  instead of three
- 星锚珍珠使用时眩晕 6 格内的敌人 6 秒并打断其施法，女仆佩戴时挡下虚空伤害会眩晕 3 格内的敌人；虚空漫步延长至 35 秒 /
  Using the Staranchor Pearl stuns enemies within 6 blocks for 6 seconds and interrupts their spellcasting, and when
  a maid wearing it blocks void damage, enemies within 3 blocks are stunned; Void Walk now lasts 35 seconds
- 每个世界按种子在末地四个方向之一保证生成一座星途终岸，末地荒地也可自然生成 /
  Every world now guarantees one Stellar Endshore at one of four End locations chosen by seed, and it can also
  generate naturally in End Barrens
- 新增日文语言包，日记正文按当前分页排版；配置界面、部分结构实体与战利品名、《女仆与前文明研究记录》等内容暂以英文显示 /
  Added a Japanese translation with journals laid out to the current pagination; the configuration screen,
  some structure entities and loot names, Maids and the Previous Civilization: Research Records and other
  content still fall back to English
- 适配妖怪们的归家 1.21 非官方移植版：女仆可用弹幕、激光与各角色符卡战斗；梦云水晶联动配方在安装该模组时加入灵梦的大蝴蝶结 /
  Added support for the unofficial 1.21 port of Youkai's Homecoming: maids can fight with danmaku, lasers and each
  character's spell cards, and the Dreamcloud Crystal crossover recipe adds the Reimu Hairband when it is installed

### 修复 / Bug Fixes

- 修复寻风之铃飞行中存档重载后飞向世界原点的问题 /
  Fixed the Wind Seeking Bell flying toward the world origin after a save is reloaded mid-flight
- 修复喋血之心按原始伤害而非实际扣血量回复的问题 /
  Fixed Bleeding Heart healing based on raw damage instead of the health actually lost
- 修复女仆身上的效果被拦截后，下一个效果的属性修改丢失的问题 /
  Fixed the next effect's attribute modifiers being lost after an effect on a maid was blocked
- 修复法术战斗中女仆把友方或坐下待机的女仆当作目标的问题 /
  Fixed maids in spell combat targeting friendly maids or maids sitting on standby
- 混沌之书、烬血之戒、春花-返的结算时机与 1.20 版一致：混沌之书拆分伤害后取消原伤害，春花-返改为减免伤害 /
  Chaos Book, Ember Blood Ring and Spring Bloom: Return now resolve at the same point as in the 1.20 version:
  Chaos Book cancels the original damage after splitting it, and Spring Bloom: Return now reduces damage
- 修复发簪把被盾牌或受击间隔挡下的伤害转给主人的问题 /
  Fixed the Hairpin transferring damage to the owner even when it was blocked by a shield or hit cooldown
- 修复可选模组未安装时含其材料的配方报错的问题；梦云水晶跨模组祭坛配方补回纯净邪血与宝刀「付丧」结月 /
  Fixed recipes with optional-mod ingredients erroring when that mod is not installed; the Dreamcloud Crystal
  crossover altar recipe again requires Pure Unholy Blood and Noble -Tukumo- Violet
- 梦云水晶随机正面效果白名单默认值补回农夫乐事、Alex 系列、Cataclysm 等模组（仅影响新生成的配置） /
  Restored Farmer's Delight, Alex's mods, Cataclysm and other namespaces to the default Dreamcloud Crystal random
  beneficial effect whitelist (only affects newly generated configs)
- 修复主人变更后，依赖主人女仆列表的饰品效果仍按旧主人查找女仆的问题 /
  Fixed bauble effects that rely on an owner's maid list still looking up maids under the previous owner after
  the owner changed
- 紫荆银冠对其他模组直接改写血量造成的掉血同样生效 /
  Silver Cercis Crown now also applies to health loss caused by other mods writing health directly
- 修复女仆施放新生魔艺法术成功后没有音效的问题 /
  Fixed maids casting Ars Nouveau spells without playing the spell sound
- 修复流转核心与法术强化核心的动画速度与 1.20 版不一致的问题 /
  Fixed the Flow Core and Spell Enhancement Core animation speeds differing from the 1.20 version
- 修复幻化项链切换款式时未校验背包槽位的问题 /
  Fixed the Transmog Necklace not validating inventory slots when switching styles
- 进出归隐之地的落点与 1.20 版一致：原坐标可站立就原地落下，否则落到地表，地表也不可站立时垫一块平台 /
  Entering and leaving The Retreat now places you as in the 1.20 version: you land in place if the original
  position is standable, otherwise on the surface, with a platform placed if the surface is not standable either
- 修复锚定核心在女仆失效或不在票据区块后仍保留强加载的问题；无主女仆也会被强加载 /
  Fixed the Anchor Core keeping chunks force-loaded after its maid became invalid or left the ticketed chunk;
  maids without an owner are now force-loaded as well
- 修复共享归隐之地多名玩家同时搜索隐世之境时结果可能串号的问题，搜索改为依次排队 /
  Fixed Hidden Retreat search results possibly going to the wrong player when several players searched at once in
  a shared Retreat; searches are now queued one at a time
- 修复寻风之铃在搜索结果返回前再次使用会另起一次搜索的问题，现在只提示仍在搜索 /
  Fixed using the Wind Seeking Bell again before its search finished starting another search; it now only reports
  that the search is still running
- 修复烬血之戒在生命超出上限时仍给增伤的问题，现在只按缺失生命加成 /
  Fixed the Ember Blood Ring granting bonus damage while health exceeded the maximum; the bonus now scales only with
  missing health
- 修复星落之庭补生成与其他结构重叠的问题，现在会避让其他结构的计划落点与已有结构 /
  Fixed retroactive Starfall Garden generation overlapping other structures; it now avoids planned and existing
  structures
- 修复星之魔女的最大生命值读数与实际不一致的问题 /
  Fixed the Stellar Witch reporting a maximum health that differed from its actual value
- 修复用刷怪蛋生成的星之魔女只有 1 点生命的问题 /
  Fixed the Stellar Witch spawned from a spawn egg having only 1 health
- 修复结构中生成的星之魔女战斗结束后归位到错误位置的问题 /
  Fixed the Stellar Witch generated in structures returning to the wrong position after combat
- 修复安装 FreeTerraForged 等改写主世界地形的模组后，归隐之地变成一片水的问题；隐世之境选址时确认脚下是实心地面 /
  Fixed The Retreat turning into open water with FreeTerraForged or other mods that rewrite overworld terrain;
  Hidden Retreats now check for solid ground when choosing a site
- 修复妖精女仆咖啡厅在坡地边缘悬空的问题 /
  Fixed the Fairy Maid Cafe floating over the edge of slopes
- 修复与 Legendary Monsters 同时安装时，其小型湮灭弹命中非生物实体导致崩溃的问题 /
  Fixed a crash when Legendary Monsters' small annihilation projectile hit a non-living entity

### 优化 / Improvements

- 星之魔女二阶段剑技连段更连贯：连段中途提前接下一式，最后一击播完整收招；动作没起就不造成近战伤害；每套连段收招后先放一次法术再起下一套 /
  The Stellar Witch's phase-two sword combos are smoother: mid-combo strikes chain early and only the final strike
  plays the full recovery; melee damage is dealt only when the attack animation actually starts; after each combo
  she casts a spell before starting the next one
- 星之魔女在非战斗状态或转阶段期间，不再因其他模组直接改写血量而掉血；非战斗状态下不会被其他生物当作目标 /
  The Stellar Witch no longer loses health from other mods writing her health directly while out of combat or
  during a phase transition, and other mobs no longer target her while she is out of combat
- 星之魔女正式挑战中不再消耗玩家护甲与副手物品的耐久 /
  Formal Stellar Witch challenges no longer wear down the player's armor and off-hand item durability
- 星影剑阵改由单一控制器管理，剑数多时更流畅，并加入停顿、闪现与斩击连段；起手动画改为铁魔法的单手横斩 /
  Starshadow Sword Array is now driven by a single controller, runs more smoothly with many swords, and adds
  pauses, blinks and slash combos; its casting animation is now Iron's Spells 'n Spellbooks' one-handed slash
- 女仆佩戴梦云水晶攻击时不再使目标时停 1 秒，相关配置项移除；旧版本遗留的定身生物进入世界时自动恢复行动 /
  Maids wearing the Dreamcloud Crystal no longer freeze their targets for 1 second, and the related config option
  is removed; mobs left frozen by older versions regain their AI when they enter the world
- 混沌之书、破愈咒锋、梦云水晶的受伤前置效果对所有重写了受伤逻辑的模组生物生效，不再只限固定清单 /
  The pre-damage effects of Chaos Book, Wound Rime Blade and Dreamcloud Crystal now apply to every modded mob
  that overrides damage handling, instead of a fixed list
- 星落之庭不再限制生物群系；旧存档补生成改为分多个 tick 写入，开服不再长时间卡顿，写入失败或崩服累计 3 次后不再重试 /
  Starfall Garden is no longer restricted by biome; retroactive generation for existing worlds now writes over
  several ticks so server startup no longer stalls, and gives up after three failed or crashed attempts

### 升级说明 / Upgrade Notes

- 旧配置中未改动过的梦云水晶与馥郁巧思随机正面效果黑名单会自动补上 goety:explosive，自定义过的黑名单保持不变 /
  Unmodified Dreamcloud Crystal and Fragrant Ingenuity random beneficial effect blacklists in existing configs
  automatically gain goety:explosive; customized blacklists are left unchanged
- 如安装 Psi，需要 1.21.1-110 或更新版本 /
  If Psi is installed, version 1.21.1-110 or newer is required
- 如安装 UsefulMagic，需要 3.1.0 或更新版本；UsefulMagic 3.1.1 附带的 CooParticlesAPI 2.5.5.3 会导致专用服务器启动崩溃，属该库自身问题 /
  If UsefulMagic is installed, version 3.1.0 or newer is required; CooParticlesAPI 2.5.5.3, bundled with
  UsefulMagic 3.1.1, crashes dedicated servers on startup, which is an issue in that library
- 如安装农夫乐事，需要 1.3.0 或更新版本 /
  If Farmer's Delight is installed, version 1.3.0 or newer is required
- 如安装妖怪们的飨宴，需要 1.1.1 或更新版本 /
  If Youkais' Feasts is installed, version 1.1.1 or newer is required
- 梦云水晶不再支持玩家通过 Curios 佩戴，仅女仆可用；玩家饰品栏中的梦云水晶请在升级前取下 /
  Dreamcloud Crystal can no longer be worn by players through Curios and is now maid-only; remove it from
  player curio slots before upgrading
- 如安装妖怪们的归家的 1.21 非官方移植版，不能同时安装妖怪们的飨宴 /
  The unofficial 1.21 port of Youkai's Homecoming cannot be installed together with Youkais' Feasts
- 配置文件按功能拆分到 `config/touhou_little_maid_spell/` 下的 `combat.toml`、`baubles.toml`、`retreat.toml`、`compat.toml`、`boss.toml` 与 `client/ender_pocket.toml`；旧配置会自动迁移并保留备份 /
  Config files are split by feature into `combat.toml`, `baubles.toml`, `retreat.toml`, `compat.toml`, `boss.toml`
  and `client/ender_pocket.toml` under `config/touhou_little_maid_spell/`; old configs are migrated automatically
  and backed up
- 网络协议已更新，客户端与服务端需一起升级 /
  The network protocol has changed, so clients and servers must upgrade together

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
