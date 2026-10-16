package com.github.yimeng261.maidspell.stagewright.client;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.loading.FileLoader;
import software.bernie.geckolib.loading.json.raw.Model;
import software.bernie.geckolib.loading.json.typeadapter.KeyFramesAdapter;
import software.bernie.geckolib.loading.object.BakedAnimations;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.GeometryTree;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 客户端资源读取：GeckoLib 模型和动画每次都重新烘焙成新对象，不碰渲染器共用的缓存。
 * <p>只能在客户端线程上调用。
 */
public final class ClientAssets {
    /** 星之魔女内置模型包的资源根（嵌在模组资源里，不经过资源管理器）。 */
    public static final String WITCH_PACK = "/assets/touhou_little_maid_spell/tlm_custom_pack/star_witch_winefox-1.0.0/assets/touhou_little_maid_spell/";

    private ClientAssets() {
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, path);
    }

    /** 按 GeckoLib 自己的加载流程把 geo 文件烘焙成新的模型。 */
    public static BakedGeoModel geoModel(ResourceLocation file) {
        Model raw = FileLoader.loadModelFile(file, Minecraft.getInstance().getResourceManager());
        return BakedModelFactory.getForNamespace(file.getNamespace()).constructGeoModel(GeometryTree.fromModel(raw));
    }

    public static BakedAnimations animations(ResourceLocation file) {
        return FileLoader.loadAnimationsFile(file, Minecraft.getInstance().getResourceManager());
    }

    /** 星之魔女模型包里的 geo 文件，用 GeckoLib 的解析器烘焙。 */
    public static BakedGeoModel witchGeoModel(String path) {
        try (Reader reader = new InputStreamReader(classpath(WITCH_PACK + path), StandardCharsets.UTF_8)) {
            Model raw = KeyFramesAdapter.GEO_GSON.fromJson(reader, Model.class);
            return BakedModelFactory.getForNamespace(MaidSpellMod.MOD_ID).constructGeoModel(GeometryTree.fromModel(raw));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static JsonObject witchJson(String path) {
        try (Reader reader = new InputStreamReader(classpath(WITCH_PACK + path), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static BufferedImage witchImage(String path) {
        try (InputStream stream = classpath(WITCH_PACK + path)) {
            return ImageIO.read(stream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] bytes(ResourceLocation file) {
        try (InputStream stream = Minecraft.getInstance().getResourceManager().open(file)) {
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static JsonObject json(ResourceLocation file) {
        return JsonParser.parseString(new String(bytes(file), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    public static BufferedImage image(ResourceLocation file) {
        try (InputStream stream = Minecraft.getInstance().getResourceManager().open(file)) {
            return ImageIO.read(stream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 骨骼及其全部子骨骼。 */
    public static void collect(GeoBone bone, List<GeoBone> result) {
        result.add(bone);
        bone.getChildBones().forEach(child -> collect(child, result));
    }

    private static InputStream classpath(String path) {
        InputStream stream = MaidSpellMod.class.getResourceAsStream(path);
        if (stream == null) {
            throw new IllegalStateException("找不到资源 " + path);
        }
        return stream;
    }
}
