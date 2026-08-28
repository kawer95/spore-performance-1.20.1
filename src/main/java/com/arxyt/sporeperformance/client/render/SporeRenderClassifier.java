package com.arxyt.sporeperformance.client.render;

import com.Harbinger.Spore.Sentities.BaseEntities.Calamity;
import com.Harbinger.Spore.Sentities.BaseEntities.Hyper;
import com.Harbinger.Spore.Sentities.BaseEntities.Organoid;
import com.Harbinger.Spore.Sentities.Organoids.Proto;
import net.minecraft.world.entity.Entity;

/** Centralized, allocation-free Spore render categories. */
public final class SporeRenderClassifier {
    public enum Category { NORMAL, CALAMITY, ORGANOID, HYPER, PROTO }
    private static final String SPORE_ENTITY_PACKAGE = "com.Harbinger.Spore.Sentities.";
    private static final ClassValue<Boolean> SPORE_ENTITY_CLASSES = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            return type.getName().startsWith(SPORE_ENTITY_PACKAGE);
        }
    };
    private static final ClassValue<Boolean> SPORE_MODEL_CLASSES = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            return type.getName().startsWith("com.Harbinger.Spore.Client.Models.");
        }
    };

    public static boolean isSporeEntity(Entity entity) {
        return entity != null && SPORE_ENTITY_CLASSES.get(entity.getClass());
    }

    public static boolean isSporeModel(Object model) {
        return model != null && SPORE_MODEL_CLASSES.get(model.getClass());
    }

    public static boolean isMajor(Entity entity) {
        return category(entity) != Category.NORMAL;
    }

    public static Category category(Entity entity) {
        if (entity instanceof Proto) return Category.PROTO;
        if (entity instanceof Calamity) return Category.CALAMITY;
        if (entity instanceof Hyper) return Category.HYPER;
        if (entity instanceof Organoid) return Category.ORGANOID;
        return Category.NORMAL;
    }

    private SporeRenderClassifier() {}
}
