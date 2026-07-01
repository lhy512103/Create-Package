package com.lhy.createpackage.client.render;

import java.util.List;

import com.lhy.createpackage.content.distributor.PackageDistributorBlockEntity;
import com.lhy.createpackage.content.kinetic.KineticPatternProviderBlockEntity;
import com.lhy.createpackage.content.pattern.MachineRouteData;
import com.lhy.createpackage.content.pattern.MechanicalPackagePatternData;
import com.lhy.createpackage.registry.ModComponents;
import com.lhy.createpackage.registry.ModItems;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class MechanicalPackagePatternHighlighter {
    private static final double BOX_INFLATE = 0.01;
    private static final double MAX_RENDER_DISTANCE_SQR = 128.0 * 128.0;

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
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
        var lines = buffer.getBuffer(CreatePackageRenderTypes.routeHighlightLines());

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder fill = null;
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
            AABB box = new AABB(pos).inflate(BOX_INFLATE);
            if (fill == null) {
                fill = Tesselator.getInstance()
                        .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            }
            renderFilledBox(poseStack, fill, box, red, green, blue, last ? 0.42f : 0.30f);
            LevelRenderer.renderLineBox(poseStack, lines, box, red, green, blue, 1.0f);
        }
        if (fill != null) {
            BufferUploader.drawWithShader(fill.buildOrThrow());
        }
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        poseStack.popPose();

        buffer.endBatch(CreatePackageRenderTypes.routeHighlightLines());
    }

    private static void renderFilledBox(PoseStack poseStack, VertexConsumer consumer, AABB box,
            float red, float green, float blue, float alpha) {
        var pose = poseStack.last().pose();
        addQuad(consumer, pose, box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ,
                box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, red, green, blue, alpha, 0, 0, 1);
        addQuad(consumer, pose, box.maxX, box.minY, box.minZ, box.minX, box.minY, box.minZ,
                box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, red, green, blue, alpha, 0, 0, -1);
        addQuad(consumer, pose, box.minX, box.minY, box.minZ, box.minX, box.minY, box.maxZ,
                box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, red, green, blue, alpha, -1, 0, 0);
        addQuad(consumer, pose, box.maxX, box.minY, box.maxZ, box.maxX, box.minY, box.minZ,
                box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, red, green, blue, alpha, 1, 0, 0);
        addQuad(consumer, pose, box.minX, box.maxY, box.maxZ, box.maxX, box.maxY, box.maxZ,
                box.maxX, box.maxY, box.minZ, box.minX, box.maxY, box.minZ, red, green, blue, alpha, 0, 1, 0);
        addQuad(consumer, pose, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ,
                box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, red, green, blue, alpha, 0, -1, 0);
    }

    private static void addQuad(VertexConsumer consumer, org.joml.Matrix4f pose,
            double x1, double y1, double z1, double x2, double y2, double z2,
            double x3, double y3, double z3, double x4, double y4, double z4,
            float red, float green, float blue, float alpha, int normalX, int normalY, int normalZ) {
        addVertex(consumer, pose, x1, y1, z1, red, green, blue, alpha, normalX, normalY, normalZ);
        addVertex(consumer, pose, x2, y2, z2, red, green, blue, alpha, normalX, normalY, normalZ);
        addVertex(consumer, pose, x3, y3, z3, red, green, blue, alpha, normalX, normalY, normalZ);
        addVertex(consumer, pose, x4, y4, z4, red, green, blue, alpha, normalX, normalY, normalZ);
    }

    private static void addVertex(VertexConsumer consumer, org.joml.Matrix4f pose,
            double x, double y, double z, float red, float green, float blue, float alpha,
            int normalX, int normalY, int normalZ) {
        consumer.addVertex(pose, (float) x, (float) y, (float) z)
                .setColor(red, green, blue, alpha);
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
            if (level.getBlockEntity(distributorPos.pos()) instanceof KineticPatternProviderBlockEntity provider) {
                return provider.getLinkedMachines();
            }
        }
        return List.of();
    }

    private MechanicalPackagePatternHighlighter() {}
}
