package com.arxyt.sporeperformance.client;

import com.arxyt.sporeperformance.world.StahlRisingBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

public final class StahlRisingBlockRenderer extends EntityRenderer<StahlRisingBlockEntity> {
    private final BlockRenderDispatcher dispatcher;
    public StahlRisingBlockRenderer(EntityRendererProvider.Context context) {
        super(context); dispatcher = context.getBlockRenderDispatcher(); shadowRadius = 0.0F;
    }

    @Override public void render(StahlRisingBlockEntity entity, float yaw, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int light) {
        BlockState state = entity.getBlockState();
        if (state.getRenderShape() != RenderShape.MODEL) return;
        pose.pushPose();
        float age = entity.tickCount + partialTick;
        pose.translate(-0.5D, 0.0D, -0.5D);
        pose.translate(0.5D, 0.5D, 0.5D);
        pose.mulPose(Axis.XP.rotationDegrees(age * entity.spinX));
        pose.mulPose(Axis.YP.rotationDegrees(age * entity.spinY));
        pose.mulPose(Axis.ZP.rotationDegrees(age * entity.spinZ));
        pose.translate(-0.5D, -0.5D, -0.5D);
        BakedModel model = dispatcher.getBlockModel(state);
        BlockPos pos = entity.blockPosition();
        long seed = state.getSeed(pos);
        for (RenderType type : model.getRenderTypes(state, RandomSource.create(seed), ModelData.EMPTY)) {
            dispatcher.getModelRenderer().tesselateBlock(entity.level(), model, state, pos, pose,
                    buffers.getBuffer(type), false, RandomSource.create(seed), seed,
                    OverlayTexture.NO_OVERLAY, ModelData.EMPTY, type);
        }
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override public ResourceLocation getTextureLocation(StahlRisingBlockEntity entity) { return TextureAtlas.LOCATION_BLOCKS; }
}
