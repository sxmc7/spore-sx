package com.Harbinger.Spore.Client.Layers;

import com.Harbinger.Spore.Sentities.Experiments.Plagued;
import com.Harbinger.Spore.Spore;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class PlaguedSiringeLayer<T extends Plagued> extends TranslucentLayer<T> {
    private static final ResourceLocation SYRINGE = new ResourceLocation(Spore.MODID,
            "textures/entity/plagued_syringe.png");

    public PlaguedSiringeLayer(RenderLayerParent<T, EntityModel<T>> p_117346_) {
        super(p_117346_);
    }

    @Override
    public ResourceLocation getTexture(T type) {
        return SYRINGE;
    }
}
