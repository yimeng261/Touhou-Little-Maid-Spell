package com.github.yimeng261.maidspell.worldgen.structure;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptionalModTemplateStructureTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        SharedConstants.tryDetectVersion();
        // Match the other worldgen unit tests: Forge's network bootstrap needs ModLauncher.
        Field bootstrapped = Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrapped.setAccessible(true);
        bootstrapped.setBoolean(null, true);
        if (!BuiltInRegistries.PAINTING_VARIANT.containsKey(new ResourceLocation("minecraft", "kebab"))) {
            BuiltInRegistries.bootStrap();
        }
        // Forge initializes these caches during registry freeze, which this headless
        // bootstrap skips. Without them even lava reports an empty fluid state.
        for (var block : List.of(Blocks.AIR, Blocks.NETHERRACK, Blocks.BEDROCK, Blocks.LAVA, Blocks.WATER)) {
            block.getStateDefinition().getPossibleStates().forEach(BlockState::initCache);
        }
    }

    @Test
    void selectsCaveFloorInsteadOfNetherBedrockRoof() {
        assertEquals(42, find(column(42), 11, 1, 3));
    }

    @Test
    void skipsLowCeilingAndFindsAnEarlierOpenCave() {
        BlockState[] blocks = terrain(40);
        blocks[64] = Blocks.NETHERRACK.defaultBlockState();
        Arrays.fill(blocks, 72, 123, Blocks.NETHERRACK.defaultBlockState());
        assertEquals(40, find(new NoiseColumn(0, blocks), 11, 1, 3));
    }

    @Test
    void rejectsRoofAndLavaEvenWhenThereIsSpaceAboveThem() {
        BlockState[] roof = terrain(20);
        Arrays.fill(roof, 123, 128, Blocks.BEDROCK.defaultBlockState());
        assertTrue(OptionalModTemplateStructure.findNetherFloor(
                new NoiseColumn[]{new NoiseColumn(0, roof)}, 32, 122, 4, 1, 3).isEmpty());

        BlockState[] lava = terrain(40);
        Arrays.fill(lava, 41, 61, Blocks.LAVA.defaultBlockState());
        assertTrue(OptionalModTemplateStructure.findNetherFloor(
                new NoiseColumn[]{new NoiseColumn(0, lava)}, 32, 122, 4, 1, 3).isEmpty());
    }

    @Test
    void acceptsSmallSlopeButRejectsCliffUnderTheFootprint() {
        assertEquals(43, OptionalModTemplateStructure.findNetherFloor(
                new NoiseColumn[]{column(40), column(43), column(41)}, 32, 122, 11, 1, 3).orElseThrow());
        assertTrue(OptionalModTemplateStructure.findNetherFloor(
                new NoiseColumn[]{column(40), column(44)}, 32, 122, 11, 1, 3).isEmpty());
    }

    @Test
    void checksEverySampleForLavaOrMissingSupport() {
        BlockState[] lava = terrain(39);
        lava[40] = Blocks.LAVA.defaultBlockState();
        assertTrue(OptionalModTemplateStructure.findNetherFloor(
                new NoiseColumn[]{column(40), new NoiseColumn(0, lava)}, 32, 122, 8, 0, 3).isEmpty());
    }

    @Test
    void rejectsFloodedSpaceAndLavaHangingFromTheCeiling() {
        BlockState[] flooded = terrain(40);
        Arrays.fill(flooded, 41, 51, Blocks.WATER.defaultBlockState());
        assertTrue(OptionalModTemplateStructure.findNetherFloor(
                new NoiseColumn[]{new NoiseColumn(0, flooded)}, 32, 122, 8, 1, 3).isEmpty());

        BlockState[] drippingLava = terrain(40);
        drippingLava[48] = Blocks.LAVA.defaultBlockState();
        assertTrue(OptionalModTemplateStructure.findNetherFloor(
                new NoiseColumn[]{column(40), new NoiseColumn(0, drippingLava)}, 32, 122, 8, 0, 3).isEmpty());
    }

    @Test
    void fitsTheEntireTemplateAndItsGroundOffsetBelowTheRoof() {
        assertEquals(110, find(column(110), 11, 1, 3));
        assertTrue(OptionalModTemplateStructure.findNetherFloor(
                new NoiseColumn[]{column(113)}, 32, 122, 11, 1, 3).isEmpty());
        assertEquals(112, find(column(112), 10, 0, 3));
        assertTrue(OptionalModTemplateStructure.findNetherFloor(
                new NoiseColumn[]{column(112)}, 32, 122, 10, 1, 3).isEmpty());
    }

    private static int find(NoiseColumn column, int height, int offset, int maxStep) {
        return OptionalModTemplateStructure.findNetherFloor(
                new NoiseColumn[]{column}, 32, 122, height, offset, maxStep).orElseThrow();
    }

    private static NoiseColumn column(int groundY) {
        return new NoiseColumn(0, terrain(groundY));
    }

    private static BlockState[] terrain(int groundY) {
        BlockState[] blocks = new BlockState[256];
        Arrays.fill(blocks, Blocks.AIR.defaultBlockState());
        Arrays.fill(blocks, 0, groundY + 1, Blocks.NETHERRACK.defaultBlockState());
        Arrays.fill(blocks, 123, 128, Blocks.BEDROCK.defaultBlockState());
        return blocks;
    }
}
