package com.lhy.createpackage.client.render;

import java.util.List;

import com.lhy.createpackage.content.distributor.PackageDistributorBlockEntity;
import com.lhy.createpackage.content.pattern.MachineRouteData;
import com.lhy.createpackage.content.pattern.MechanicalPackagePatternData;
import com.lhy.createpackage.registry.ModComponents;
import com.lhy.createpackage.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class MechanicalPackagePatternHighlighter {
    private static final double BOX_INSET = 0.02;
    private static final double MAX_RENDER_DISTANCE_SQR = 128.0 * 128.0;

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        var level = minecraft.level;
        if (player == null || level == null) {
            return;
        }

        List<BlockPos> route = heldRoute(level, player.getMainHandItem(), player.getOffhandItem());
        if (route.isEmpty()) {
            return;
        }

        var cameraPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        var buffer = minecraft.renderBuffers().bufferSource();
        var lines = buffer.getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        for (int i = 0; i < route.size(); i++) {
            BlockPos pos = route.get(i);
            if (!level.isLoaded(pos) || pos.distToCenterSqr(cameraPos) > MAX_RENDER_DISTANCE_SQR) {
                continue;
            }
            boolean first = i == 0;
            boolean last = i == route.size() - 1;
            float red = last ? 1.0f : first ? 0.2f : 0.1f;
            float green = last ? 0.55f : first ? 0.45f : 0.65f;
            float blue = last ? 0.05f : first ? 1.0f : 0.25f;
            AABB box = new AABB(pos).deflate(BOX_INSET);
            LevelRenderer.renderLineBox(poseStack, lines, box, red, green, blue, 1.0f);
        }
        poseStack.popPose();

        buffer.endBatch(RenderType.lines());
    }

    private static List<BlockPos> heldRoute(Level level, ItemStack mainHand, ItemStack offHand) {
        List<BlockPos> mainRoute = routeFrom(level, mainHand);
        if (!mainRoute.isEmpty()) {
            return mainRoute;
        }
        return routeFrom(level, offHand);
    }

    private static List<BlockPos> routeFrom(Level level, ItemStack stack) {
        if (stack.is(ModItems.MECHANICAL_PACKAGE_PATTERN.get())) {
            MechanicalPackagePatternData data = MechanicalPackagePatternData.from(stack);
            return data == null ? List.of() : data.route();
        }
        if (stack.is(ModItems.MECHANICAL_PATTERN_CONVERTER.get())) {
            MachineRouteData data = stack.get(ModComponents.MECHANICAL_ROUTE.get());
            return data == null ? List.of() : data.positions();
        }
        if (stack.is(ModItems.MACHINE_LINKER.get())) {
            GlobalPos distributorPos = stack.get(ModComponents.LINKED_DISTRIBUTOR.get());
            if (distributorPos == null || !level.dimension().equals(distributorPos.dimension())
                    || !level.isLoaded(distributorPos.pos())) {
                return List.of();
            }
            if (level.getBlockEntity(distributorPos.pos()) instanceof PackageDistributorBlockEntity distributor
                    && distributor.usesStoredMachineLinks()) {
                return distributor.getLinkedMachines();
            }
        }
        return List.of();
    }

    private MechanicalPackagePatternHighlighter() {}
}
