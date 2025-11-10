# 归隐之地维度系统实现说明

## 已完成功能

### 1. 动态维度创建系统
基于ResourceWorld mod的实现方式，使用Mixin注入MinecraftServer类，实现了动态创建和管理玩家专属维度的功能。

#### 核心文件：
- **MinecraftServerDimensionMixin**: Mixin注入类，为MinecraftServer添加创建和删除维度的功能
- **MinecraftServerAccessor**: 访问器接口，暴露Mixin注入的方法
- **PlayerRetreatManager**: 玩家维度管理器，负责创建和管理玩家专属的归隐之地
- **RetreatDimensionData**: 数据持久化，保存维度信息到存档

### 2. 维度特性
每个玩家的归隐之地维度具有以下特性：
- **单一樱花林群系**: 整个维度都是樱花林生物群系
- **坐标同步**: 与主世界坐标完全相同
- **独立性**: 每个玩家有自己独立的维度
- **唯一结构**: 每个维度只生成一个隐世之境结构（在0,0附近512格范围内）

### 3. 寻风之铃功能
- **主世界使用**: 传送到玩家专属的归隐之地
- **归隐之地使用**: 寻找隐世之境结构位置
- **Shift+右键**: 从归隐之地返回主世界

## 实现细节

### Mixin注入
```java
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin implements MinecraftServerAccessor {
    @Override
    public boolean maidspell$createWorld(ResourceKey<Level> key, ResourceLocation dimensionTypeKey) {
        // 动态创建维度的逻辑
        // 1. 获取注册表访问
        // 2. 检查维度类型是否存在
        // 3. 获取LevelStem
        // 4. 创建ServerLevel
        // 5. 添加到服务器的worlds Map
    }
}
```

### 维度命名规则
玩家专属维度的ResourceKey格式：
```
touhou_little_maid_spell:the_retreat_<player_uuid_with_underscores>
```

例如：
```
touhou_little_maid_spell:the_retreat_12345678_1234_1234_1234_123456789012
```

### 数据持久化
维度信息保存在：
```
world/data/touhou_little_maid_spell_retreat_dimensions.dat
```

包含信息：
- 玩家UUID
- 维度创建时间
- 最后访问时间

## 配置文件

### 维度类型配置
`data/touhou_little_maid_spell/dimension_type/the_retreat.json`
- 配置维度的基本属性（类似主世界）

### 维度配置
`data/touhou_little_maid_spell/dimension/the_retreat.json`
- 配置世界生成器
- 固定使用樱花林生物群系

### 结构生成配置
`data/touhou_little_maid_spell/worldgen/biome_modifier/`
- `add_hidden_retreat.json`: 禁用主世界生成（dimensions: []）
- `add_hidden_retreat_to_retreat_dimension.json`: 启用归隐之地生成

## 使用流程

1. **玩家在主世界使用寻风之铃**
   - 检测到是主世界
   - 调用 `PlayerRetreatManager.getOrCreatePlayerRetreat()`
   - 如果维度不存在，通过Mixin创建新维度
   - 传送玩家到归隐之地

2. **玩家在归隐之地使用寻风之铃**
   - 检测到在归隐之地
   - 执行异步结构搜索
   - 找到隐世之境后，创建飞行实体并显示位置

3. **玩家Shift+右键寻风之铃**
   - 检测到在归隐之地且按住Shift
   - 传送玩家回主世界（相同坐标）

## 技术要点

### 1. Mixin配置
确保 `maidspell.mixins.json` 中包含：
```json
{
  "mixins": [
    "MinecraftServerDimensionMixin"
  ]
}
```

### 2. 维度生成时机
- 第一次使用寻风之铃时动态创建
- 维度会持久化保存在存档中
- 服务器重启后自动加载已存在的维度

### 3. 性能考虑
- 维度延迟创建（首次使用时）
- 缓存已创建的维度引用
- 数据持久化避免重复创建

## 注意事项

1. **Mixin依赖**: 项目必须配置Mixin支持
2. **维度持久性**: 一旦创建，维度会永久保存在存档中
3. **内存管理**: 大量玩家可能创建大量维度，注意服务器性能
4. **清理功能**: 可以考虑添加清理长时间未访问维度的功能

## 可能的扩展功能

1. **维度权限管理**: 控制谁可以进入特定玩家的归隐之地
2. **维度重置**: 允许玩家重置自己的归隐之地
3. **维度清理**: 自动清理长时间未访问的维度（参考 `RetreatDimensionData.cleanupOldDimensions()`）
4. **维度共享**: 允许多个玩家共享一个归隐之地

## 测试建议

1. 在单人游戏中测试基本功能
2. 在多人服务器上测试并发创建
3. 测试服务器重启后维度是否正确加载
4. 测试维度间传送的安全性
5. 压力测试：创建大量玩家维度

## 故障排除

如果维度创建失败：
1. 检查日志中的错误信息
2. 确认Mixin正确注入
3. 检查维度配置文件是否存在
4. 确认LevelStem注册正确

## 参考资料

- ResourceWorld mod: 动态维度创建的参考实现
- Minecraft Forge文档: 维度和世界生成相关API
- Mixin文档: Mixin注入技术说明

