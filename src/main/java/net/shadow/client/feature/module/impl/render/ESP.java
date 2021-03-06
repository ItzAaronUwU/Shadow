/*
 * Copyright (c) Shadow client, Saturn5VFive and contributors 2022. All rights reserved.
 */

package net.shadow.client.feature.module.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Vec3d;
import net.shadow.client.ShadowMain;
import net.shadow.client.feature.config.BooleanSetting;
import net.shadow.client.feature.config.DoubleSetting;
import net.shadow.client.feature.config.EnumSetting;
import net.shadow.client.feature.module.Module;
import net.shadow.client.feature.module.ModuleType;
import net.shadow.client.helper.render.Renderer;
import net.shadow.client.helper.util.Utils;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class ESP extends Module {
    public final EnumSetting<Mode> outlineMode = this.config.create(new EnumSetting.Builder<>(Mode.Filled).name("Outline mode").description("How to render the outline").get());
    public final BooleanSetting entities = this.config.create(new BooleanSetting.Builder(false).name("Show entities").description("Render entities").get());
    public final BooleanSetting players = this.config.create(new BooleanSetting.Builder(true).name("Show players").description("Render players").get());
    public final List<double[]> vertexDumps = new ArrayList<>();
    final DoubleSetting range = this.config.create(new DoubleSetting.Builder(64).name("Range").description("How far to render the entities").min(32).max(128).precision(1).get());
    public boolean recording = false;

    public ESP() {
        super("ESP", "Shows where entities are", ModuleType.RENDER);
    }

    @Override
    public void tick() {

    }

    @Override
    public void enable() {

    }

    @Override
    public void disable() {

    }

    @Override
    public String getContext() {
        return null;
    }

    public boolean shouldRenderEntity(Entity e) {
        return ((e instanceof PlayerEntity && players.getValue()) || entities.getValue());
    }

    @Override
    public void onWorldRender(MatrixStack matrices) {
        if (outlineMode.getValue() == Mode.Model) {
            float alpha = 1f;
            List<double[]> vertBuffer = new ArrayList<>();
            List<double[][]> verts = new ArrayList<>();
            for (double[] vertexDump : vertexDumps) {
                if (vertexDump.length == 0) {
                    verts.add(vertBuffer.toArray(double[][]::new));
                    vertBuffer.clear();
                } else {
                    vertBuffer.add(vertexDump);
                }
            }
            verts.add(vertBuffer.toArray(double[][]::new));
            vertBuffer.clear();
            vertexDumps.clear();

            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            double p;
            BufferBuilder buffer = Tessellator.getInstance().getBuffer();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            for (double[][] vert : verts) {


                for (double[] vertexDump : vert) {
                    p = (((/*vertexDump[0]+vertexDump[1]+*/vertexDump[2]) % 10) / 10 + (System.currentTimeMillis() % 2000) / 2000d) % 1;
                    int col = Color.HSBtoRGB((float) p, .6f, 1f);
                    float red = (col >> 16 & 0xFF) / 255f;
                    float green = (col >> 8 & 0xFF) / 255f;
                    float blue = (col & 0xFF) / 255f;
                    buffer.vertex(vertexDump[0], vertexDump[1], vertexDump[2]).color(red, green, blue, alpha).next();
                }

            }
            buffer.end();
            BufferRenderer.draw(buffer);

            GL11.glDepthFunc(GL11.GL_LEQUAL);
            RenderSystem.disableBlend();
        }
        if (ShadowMain.client.world == null || ShadowMain.client.player == null) {
            return;
        }


        for (Entity entity : ShadowMain.client.world.getEntities()) {
            if (entity.squaredDistanceTo(ShadowMain.client.player) > Math.pow(range.getValue(), 2)) {
                continue;
            }
            if (entity.getUuid().equals(ShadowMain.client.player.getUuid())) {
                continue;
            }
            if (shouldRenderEntity(entity)) {
                Color c = Utils.getCurrentRGB();
                Vec3d eSource = Utils.getInterpolatedEntityPosition(entity);
                switch (outlineMode.getValue()) {
                    case Filled ->
                            Renderer.R3D.renderFilled(eSource.subtract(new Vec3d(entity.getWidth(), 0, entity.getWidth()).multiply(0.5)), new Vec3d(entity.getWidth(), entity.getHeight(), entity.getWidth()), Renderer.Util.modify(c, -1, -1, -1, 130), matrices);
                    case Rect -> renderOutline(entity, c, matrices);
                    case Outline ->
                            Renderer.R3D.renderOutline(eSource.subtract(new Vec3d(entity.getWidth(), 0, entity.getWidth()).multiply(0.5)), new Vec3d(entity.getWidth(), entity.getHeight(), entity.getWidth()), Renderer.Util.modify(c, -1, -1, -1, 130), matrices);
                }
            }
        }
    }

    @Override
    public void onHudRender() {

    }

    void renderOutline(Entity e, Color color, MatrixStack stack) {
        Vec3d eSource = new Vec3d(MathHelper.lerp(ShadowMain.client.getTickDelta(), e.prevX, e.getX()), MathHelper.lerp(ShadowMain.client.getTickDelta(), e.prevY, e.getY()), MathHelper.lerp(ShadowMain.client.getTickDelta(), e.prevZ, e.getZ()));
        float red = color.getRed() / 255f;
        float green = color.getGreen() / 255f;
        float blue = color.getBlue() / 255f;
        float alpha = color.getAlpha() / 255f;
        Camera c = ShadowMain.client.gameRenderer.getCamera();
        Vec3d camPos = c.getPos();
        Vec3d start = eSource.subtract(camPos);
        float x = (float) start.x;
        float y = (float) start.y;
        float z = (float) start.z;

        double r = Math.toRadians(-c.getYaw() + 90);
        float sin = (float) (Math.sin(r) * (e.getWidth() / 1.7));
        float cos = (float) (Math.cos(r) * (e.getWidth() / 1.7));
        stack.push();

        Matrix4f matrix = stack.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        GL11.glDepthFunc(GL11.GL_ALWAYS);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableBlend();
        buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        buffer.vertex(matrix, x + sin, y, z + cos).color(red, green, blue, alpha).next();
        buffer.vertex(matrix, x - sin, y, z - cos).color(red, green, blue, alpha).next();
        buffer.vertex(matrix, x - sin, y, z - cos).color(red, green, blue, alpha).next();
        buffer.vertex(matrix, x - sin, y + e.getHeight(), z - cos).color(red, green, blue, alpha).next();
        buffer.vertex(matrix, x - sin, y + e.getHeight(), z - cos).color(red, green, blue, alpha).next();
        buffer.vertex(matrix, x + sin, y + e.getHeight(), z + cos).color(red, green, blue, alpha).next();
        buffer.vertex(matrix, x + sin, y + e.getHeight(), z + cos).color(red, green, blue, alpha).next();
        buffer.vertex(matrix, x + sin, y, z + cos).color(red, green, blue, alpha).next();

        buffer.end();

        BufferRenderer.draw(buffer);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        RenderSystem.disableBlend();
        stack.pop();
    }

    public enum Mode {
        Filled, Rect, Outline, Model
    }
}
