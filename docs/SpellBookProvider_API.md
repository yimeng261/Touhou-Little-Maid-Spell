# 法术书提供者 API 文档

本文档介绍如何为女仆法术系统添加自定义法术书提供者。

## 概述

女仆法术系统现在支持自动发现和加载法术书提供者，使得添加新的魔法模组支持变得更加简单。

## 创建自定义提供者

### 1. 实现 ISpellBookProvider 接口

### 2. 注册提供者

有两种注册方式：

#### 方式1：使用工厂函数注册

```java
package com.yourmod;

import com.github.yimeng261.maidspell.api.SpellBookProviderRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod("yourmod")
public class YourMod {
    
    public void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // 注册你的法术书提供者
            SpellBookProviderRegistry.registerProvider("yourmod", 
                maid -> new YourModSpellBookProvider(maid));
        });
    }
}
```

#### 方式2：使用类注册

```java
public void onCommonSetup(FMLCommonSetupEvent event) {
    event.enqueueWork(() -> {
        // 使用类注册（要求有EntityMaid参数的构造函数）
        SpellBookProviderRegistry.registerProvider("yourmod", YourModSpellBookProvider.class);
    });
}
```

## 最佳实践

### 1. 模组兼容性检查

提供者会自动检查模组是否加载，但建议在提供者内部也进行额外的检查：

```java
@Override
public boolean isSpellBook(ItemStack itemStack) {
    if (itemStack == null || itemStack.isEmpty()) {
        return false;
    }
    
    // 检查模组是否加载
    if (!ModList.get().isLoaded("yourmod")) {
        return false;
    }
    
    return itemStack.getItem() instanceof YourModSpellBook;
}
```

### 2. 错误处理

在施法方法中添加适当的错误处理：

```java
@Override
public boolean castSpell() {
    try {
        // 你的施法逻辑
        return performSpellCast();
    } catch (Exception e) {
        LOGGER.error("Failed to cast spell: {}", e.getMessage(), e);
        stopCasting(); // 确保清理状态
        return false;
    }
}
```

### 3. 资源管理

确保在适当的时候清理资源：

```java
@Override
public void stopCasting() {
    isCasting = false;
    // 清理任何创建的实体、效果等
    cleanupSpellEffects();
}

@Override
public void updateMaidReference(EntityMaid newMaid) {
    stopCasting(); // 停止当前施法
    // 更新引用
    this.maid = newMaid;
    // 重置状态
    this.target = null;
    this.spellBook = null;
}
```

## API 工具方法

### 检查注册状态

```java
// 检查模组是否已注册提供者
boolean isRegistered = SpellBookProviderRegistry.isProviderRegistered("yourmod");

// 获取所有注册的模组
List<String> registeredMods = SpellBookProviderRegistry.getRegisteredMods();

// 获取提供者数量
int providerCount = SpellBookProviderRegistry.getProviderCount();
```

## 注意事项

1. **模组ID必须匹配**：注册时使用的模组ID必须与你的实际模组ID完全一致
2. **早期注册**：建议在`FMLCommonSetupEvent`或更早的阶段注册提供者
3. **线程安全**：提供者可能在不同线程中被调用，确保实现是线程安全的
4. **构造函数要求**：如果使用类注册方式，提供者类必须有一个接收`EntityMaid`参数的公共构造函数

## 示例集成

参考现有的提供者实现：
- `IronsSpellbooksProvider` - 铁魔法模组集成
- `ArsNouveauProvider` - 新生魔艺模组集成

这些实现展示了如何处理不同类型的法术系统和施法机制。 