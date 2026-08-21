package com.maxwell.hyperdamagelib.client.renderer;

import com.maxwell.hyperdamagelib.entity.MeasurementDummyEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

@SuppressWarnings("removal")
public class MeasurementDummyRenderer extends HumanoidMobRenderer<MeasurementDummyEntity, HumanoidModel<MeasurementDummyEntity>> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/entity/player/wide/steve.png");

    public MeasurementDummyRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(MeasurementDummyEntity entity) {
        return TEXTURE;
    }
}