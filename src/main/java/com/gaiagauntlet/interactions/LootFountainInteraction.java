package com.gaiagauntlet.interactions;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.VariantRotation;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.modules.item.ItemModule;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockRotationUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/** A block that opens once and launches a rolled droplist out of its mouth in an arc. */
public final class LootFountainInteraction extends SimpleBlockInteraction {

    public static final String ID = "LootFountain";

    public static final BuilderCodec<LootFountainInteraction> CODEC = BuilderCodec
            .builder(LootFountainInteraction.class, LootFountainInteraction::new, SimpleBlockInteraction.CODEC)
            .append(new KeyedCodec<>("Droplist", Codec.STRING), (o, v) -> o.droplist = v, o -> o.droplist)
            .documentation("ItemDropList asset rolled once when the block is opened.")
            .addValidator(Validators.nonEmptyString())
            .add()
            .append(new KeyedCodec<>("OpenState", Codec.STRING), (o, v) -> o.openState = v == null ? "Opened" : v, o -> o.openState)
            .documentation("Block state that marks the block as looted.")
            .add()
            .append(new KeyedCodec<>("SpawnOffset", Codec.DOUBLE_ARRAY), (o, v) -> o.spawnOffset = v == null ? o.spawnOffset : v, o -> o.spawnOffset)
            .documentation("Block-local X, Y, Z offset of the fountain's mouth from the block's center.")
            .add()
            .append(new KeyedCodec<>("SpawnRadius", Codec.FLOAT), (o, v) -> o.spawnRadius = v == null ? o.spawnRadius : v, o -> o.spawnRadius).add()
            .append(new KeyedCodec<>("BurstDelaySeconds", Codec.FLOAT), (o, v) -> o.burstDelaySeconds = v == null ? 0 : v, o -> o.burstDelaySeconds)
            .documentation("Delay between the chest opening and the loot flying out.")
            .add()
            .append(new KeyedCodec<>("ArcDegrees", Codec.FLOAT), (o, v) -> o.arcDegrees = v == null ? 360f : v, o -> o.arcDegrees)
            .documentation("Horizontal arc the items spread over; 360 is all around.")
            .add()
            .append(new KeyedCodec<>("HorizontalSpeedMin", Codec.FLOAT), (o, v) -> o.horizontalSpeedMin = v == null ? o.horizontalSpeedMin : v, o -> o.horizontalSpeedMin).add()
            .append(new KeyedCodec<>("HorizontalSpeedMax", Codec.FLOAT), (o, v) -> o.horizontalSpeedMax = v == null ? o.horizontalSpeedMax : v, o -> o.horizontalSpeedMax).add()
            .append(new KeyedCodec<>("VerticalSpeedMin", Codec.FLOAT), (o, v) -> o.verticalSpeedMin = v == null ? o.verticalSpeedMin : v, o -> o.verticalSpeedMin).add()
            .append(new KeyedCodec<>("VerticalSpeedMax", Codec.FLOAT), (o, v) -> o.verticalSpeedMax = v == null ? o.verticalSpeedMax : v, o -> o.verticalSpeedMax).add()
            .append(new KeyedCodec<>("PickupDelaySeconds", Codec.FLOAT), (o, v) -> o.pickupDelaySeconds = v == null ? o.pickupDelaySeconds : v, o -> o.pickupDelaySeconds).add()
            .append(new KeyedCodec<>("MaxItemEntities", Codec.INTEGER), (o, v) -> o.maxItemEntities = v == null ? 128 : v, o -> o.maxItemEntities)
            .documentation("Cap on the items launched from one roll.")
            .add()
            .build();

    private String droplist;
    private String openState = "Opened";
    private double[] spawnOffset = {0.0, 1.25, 0.0};
    private float spawnRadius = 0.2f;
    private float burstDelaySeconds;
    private float arcDegrees = 360f;
    private float horizontalSpeedMin = 1.8f;
    private float horizontalSpeedMax = 3.2f;
    private float verticalSpeedMin = 4.5f;
    private float verticalSpeedMax = 6.0f;
    private float pickupDelaySeconds = 0.75f;
    private int maxItemEntities = 128;

    private LootFountainInteraction() {
    }

    @Override
    protected void interactWithBlock(@Nonnull World world, @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull InteractionType type,
            @Nonnull InteractionContext context, @Nullable ItemStack itemInHand, @Nonnull Vector3i pos, @Nonnull CooldownHandler cooldownHandler) {
        var blockType = world.getBlockType(pos);
        if (blockType == null || blockType.isState()) return;

        var opened = blockType.getBlockForState(openState);
        if (opened == null) return;

        var chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        if (chunk == null) return;

        var rotation = RotationTuple.get(chunk.getRotationIndex(pos.x, pos.y, pos.z));
        var chunkStore = world.getChunkStore();
        var sectionRef = chunkStore.getChunkSectionReference(ChunkUtil.chunkCoordinate(pos.x), ChunkUtil.chunkCoordinate(pos.y),
                ChunkUtil.chunkCoordinate(pos.z));
        if (sectionRef == null) return;

        BlockOperations.setBlockInteractionState(chunkStore, sectionRef, pos.x, pos.y, pos.z, blockType, openState, false);
        var mouth = new Vector3f((float) spawnOffset[0], (float) spawnOffset[1], (float) spawnOffset[2]);
        rotation.applyRotationTo(mouth);
        var origin = new Vector3d(pos.x + 0.5 + mouth.x, pos.y + mouth.y, pos.z + 0.5 + mouth.z);
        int sound = opened.getInteractionSoundEventIndex();
        if (sound != SoundEvent.EMPTY_ID) {
            SoundUtil.playSoundEvent3d(sound, SoundCategory.SFX, origin.x, origin.y, origin.z, commandBuffer);
        }
        if (burstDelaySeconds <= 0f) {
            burst(commandBuffer, origin, rotation);
            return;
        }
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> world.execute(() -> burst(world.getEntityStore().getStore(), origin, rotation)),
                (long) (burstDelaySeconds * 1000), TimeUnit.MILLISECONDS);
    }

    private void burst(@Nonnull ComponentAccessor<EntityStore> accessor, @Nonnull Vector3d origin, @Nonnull RotationTuple rotation) {
        List<ItemStack> drops = ItemModule.get().getRandomItemDrops(droplist);
        var random = ThreadLocalRandom.current();
        int count = Math.min(drops.size(), maxItemEntities);
        double arc = Math.toRadians(arcDegrees);
        boolean full = arcDegrees >= 360f;
        double spacing = count > 1 ? arc / (full ? count : count - 1) : 0.0;
        double phase = full ? random.nextDouble(0.0, Math.PI * 2.0) : -arc * 0.5;
        for (int i = 0; i < count; i++) {
            double angle = phase + spacing * i;
            var outward = new Vector3f((float) Math.sin(angle), 0f, (float) Math.cos(angle));
            rotation.applyRotationTo(outward);
            float horizontal = between(horizontalSpeedMin, horizontalSpeedMax);
            float vertical = between(verticalSpeedMin, verticalSpeedMax);
            var launch = new Vector3d(origin.x + outward.x * spawnRadius, origin.y, origin.z + outward.z * spawnRadius);
            var holder = ItemComponent.generateItemDrop(accessor, drops.get(i), launch, Rotation3f.IDENTITY,
                    outward.x * horizontal, vertical, outward.z * horizontal);
            if (holder == null) {
                continue;
            }
            var item = holder.getComponent(ItemComponent.getComponentType());
            if (item != null) {
                item.setPickupDelay(pickupDelaySeconds);
            }
            accessor.addEntity(holder, AddReason.SPAWN);
        }
    }

    private static float between(float min, float max) {
        return min >= max ? min : ThreadLocalRandom.current().nextFloat(min, max);
    }

    @Override
    protected void simulateInteractWithBlock(@Nonnull InteractionType type, @Nonnull InteractionContext context,
            @Nullable ItemStack itemInHand, @Nonnull World world, @Nonnull Vector3i targetBlock) {
    }
}
