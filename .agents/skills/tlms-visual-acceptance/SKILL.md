---
name: tlms-visual-acceptance
description: >-
  Touhou Little Maid: Spell 移植验收里「要看画面才能判」的项目：模型/贴图是不是紫黑格、物品栏平面图标与手持 3D 模型、
  投枪蓄力朝向、末影腰包状态栏外观、Boss 血条样式、成就页签背景、手册配图、罗盘指针、坡地上的结构观感等。
  用户说「截图验收」「看画面验收」「跑视觉验收」「worlddriver 验收」，或要逐项确认 docs/移植验收.md 里标 WD 的项目时使用。
  靠 worlddriver RPC 摆场景、截图，由你看图判定；不写代码、不改模组。
---

# 视觉验收（worlddriver 截图）

StageWright 场景和 clientTest 管得了数据和交互，管不了「看起来对不对」。这里的每一项都是：
用命令摆好场景 → 把镜头对准 → 截图 → 你看图按判定标准给结论。

## 准备

1. 起一个带客户端的 hold（集成服，装全套可选模组）：
   ```bash
   ./gradlew stagewrightIntegratedServerHold
   ```
   客户端跑在无头 KWin（`:1`），见记忆里的集成拓扑显示环境；桌面显示器休眠会卡住 GLFW。
2. RPC 客户端用 `worlddriver-rpc` skill 自带的 `rpc.py`，在它的目录下执行（下文 `RPC=python3 .claude/skills/worlddriver-rpc/rpc.py`）。
   先探活：`$RPC mc.system.version`。
3. 进入世界后先做一次：
   ```bash
   $RPC mc.action.runCommand '{"cmd":"gamemode creative @p"}'
   $RPC mc.action.runCommand '{"cmd":"time set noon"}'
   $RPC mc.action.runCommand '{"cmd":"weather clear"}'
   $RPC mc.action.runCommand '{"cmd":"gamerule doDaylightCycle false"}'
   ```

## 截图

```bash
shot() {  # shot <文件名>：等下一帧后截图，存到 build/visual-acceptance/
  mkdir -p build/visual-acceptance
  $RPC mc.client.screenshot '{"maxWidth":1600,"format":"png"}' --compact --jq base64 \
    | tr -d '"' | base64 -d > "build/visual-acceptance/$1.png"
}
```

- 截图前先 `mc.bot.lookAt`（`{"pos":{x,y,z}}` 或 `{"yaw":…,"pitch":…}`）对准目标；需要第三人称时按 `F5`：
  `$RPC mc.client.input.key '{"key":"F5"}'`（按两次回第一人称）。
- 截完用 Read 打开 PNG 看。结果里 `frameWaited:false` 表示拿到的是旧帧，重截一次。
- 同一项中英文都要看时，切语言：`$RPC mc.script.eval` 里调 `Minecraft.getInstance().getLanguageManager().setSelected("zh_cn")`
  再 `reloadResourcePacks()`，或者直接在选项界面切，截两张。

## 判定通则

- **紫黑方格 = 贴图缺失**，一律判不通过；纯白/纯黑的方块模型同样可疑。
- 模型「站着不动」「朝向反了」「悬空/陷地」都记下来，附截图文件名。
- 看不清就拉近再截，不要猜。

## 项目清单

每项格式：**摆场景** → **镜头** → **判定**。坐标用玩家当前位置附近的空地，下面写的是相对玩家的摆法。

### 物品外观（§19、§20、§14）
1. **星之魔女装备**：`give @p touhou_little_maid_spell:star_shadow_longsword`、`star_shadow_staff`、`star_witch_hat`，打开背包截一张；
   逐个拿在主手截第一人称和第三人称（F5）；再 `/item replace entity @p armor.head with touhou_little_maid_spell:star_witch_hat` 截第三人称正面。
   判定：背包里是平面图标；手上是 3D 模型并在动；法杖是铁魔法法杖的持握姿势；头顶有帽子模型。
2. **星影投枪**：`give @p touhou_little_maid_spell:star_shadow_spear`。背包、手持、扔在地上（`Q`）各截一张。
   按住右键蓄力时第三人称截图：`$RPC mc.client.input.keybind '{"name":"key.use","action":"press"}'`，等一秒截图，
   再 `{"name":"key.use","action":"release"}` 投出。
   扔出后插在方块上截一张。判定：都是 3D 投枪；蓄力时枪头朝前；插地时枪身朝向与原版三叉戟一致。
3. **星锚珍珠、星云核心、星陨石、仪式剑柄、归星**：背包截图，判定贴图正常。
4. **镇石 tooltip**：`give @p touhou_little_maid_spell:suppression_stone`，背包里鼠标悬停（`mc.client.input.mouseMove` 到格子中心），
   分别不按和按住 Shift（`mc.client.input.key {"key":"LEFT_SHIFT","action":"press"}`）截图。判定：平时一行说明，按住 Shift 两行，中英文都不是翻译键。

### 法术（§18）
5. **法术图标**：`give @p irons_spellbooks:scroll[irons_spellbooks:spell_container={…}]`（或创造物品栏铁魔法页签），
   对 9 个法术（逐星飞瀑、魔法霰弹、虚空相变、星影剑阵、星隙闪袭、星影斩击、三矢连星、伴星黑洞、破法回响）各看一次卷轴悬停说明和图标。
6. **星影剑阵**：拿卷轴对着一只 NoAI 的僵尸施放，第三人称截 3–4 张（落剑中、钉地后）。判定：剑的模型不是紫黑块；飞行时剑尖朝飞行方向，钉地后保持入射方向。
7. **星影斩击 / 伴星黑洞 / 破法回响**：施放后截图。判定：刃光 4 帧动画可见；头顶 5 格小黑洞、灰色黑洞外观正常。

### 界面（§10、§12、§21、§25、§27、§33、§1.2）
8. **末影腰包状态栏**：召唤 2–3 只戴末影腰包的自有女仆（带不同血量，其中一只在下界），关掉所有界面截全屏。
   判定：每行右上角有末影腰包图标；别的维度显示 DIM 标记；边框、头像、血条正常；不遮挡原版 HUD。
   再打开状态栏位置编辑界面截一张：预览框、底部工具栏不重叠。
9. **Boss 血条**：`summon touhou_little_maid_spell:stellar_witch ~5 ~ ~`，手持星芒短剑右键她开战，截顶部血条。判定：是自定义样式，不是原版紫色条。
10. **成就页签**：按 `L` 打开进度界面，切到「星之旅程」和「万法皆通」页签各截一张。判定：星之旅程是末地石背景、启程之地为根、星途终岸挂在下面；万法皆通是樱花木板背景，不是黑紫格。
11. **《万法皆通》手册**：给车万女仆的手册（`/give @p patchouli:guide_book[patchouli:book="touhou_little_maid:memorizable_gensokyo"]`），
    翻到「万法皆通」分类和三个条目的第二页。判定：分类图标是梦云水晶；三张配图正常，不是紫黑格。
12. **模型选择界面**：打开一只自有女仆的换装界面，搜 `winefox_elf`、`winefox_saint`、「星之魔女酒狐」各截一张。判定：模型预览正常。

### 世界里的东西（§22、§23、§24、§34）
13. **观星术士**：`summon touhou_little_maid_spell:astro_mancer ~3 ~ ~`，正面截图。判定：模型贴图正常，手持匠师手杖。
14. **观星罗盘指针**：在末地找到星途终岸后（`/locate structure touhou_little_maid_spell:stellar_endshore`，右键罗盘），手持罗盘转身两个方向各截一张。
    判定：指针跟着目标方向转；未绑定的罗盘指针乱转。
15. **星途终岸的秋千**：`/place structure touhou_little_maid_spell:stellar_endshore`（在末地外岛上空），飞过去截星之魔女坐姿。判定：正好坐在秋千上，不飘不陷，无 Boss 血条。
16. **坡地上的魔女足迹**：找缓坡（落差 2–3 格）、陡坡、崖边，`/place structure touhou_little_maid_spell:enchantress_footsteps_graveyard` 和 `…_mushroom_fields` 各放几次，四个方向各截一张。
    判定：低处有土往下填，没有悬空的泥土板或地基；高处削平处不突兀，地形不盖门；蘑菇地看不到菌丝方块板，房子四周没有一圈坑。
17. **画**：观星塔 2 号件（`/place template touhou_little_maid_spell:starwatch_tower/starwatch_tower_2`）里「魔法酒狐」「星空花海」截图。判定：图案正确不是 aztec，星空花海高度与 1.20 一致。

## 结果

把每项的结论写进一张表，交给用户：

| # | 项目 | 结论（通过/不通过/看不清） | 截图 | 备注 |
|---|---|---|---|---|

不通过的项附上截图文件名和看到的现象，不要自己去改代码。
