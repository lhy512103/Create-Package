package com.lhy.createpackage.client.render;

import java.util.OptionalDouble;

import com.lhy.createpackage.CreatePackage;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

public final class CreatePackageRenderTypes extends RenderStateShard {
    private static final RenderType ROUTE_HIGHLIGHT_LINES = RenderType.create(
            CreatePackage.MODID + ":route_highlight_lines",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            256,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(3.0d)))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    public static RenderType routeHighlightLines() {
        return ROUTE_HIGHLIGHT_LINES;
    }

    private CreatePackageRenderTypes() {
        super(null, null, null);
    }
}
