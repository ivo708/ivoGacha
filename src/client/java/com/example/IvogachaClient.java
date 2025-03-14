package com.example;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class IvogachaClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("ivoGachaClient");

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    public class PersistentItemDisplayEntity extends ItemDisplayEntity {

        public PersistentItemDisplayEntity(EntityType<? extends ItemDisplayEntity> type, ClientWorld world) {
            super(type, world);
        }

        @Override
        public void tick() {
            this.age = 0;
            super.tick();
        }
    }
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(ChestOpenPayload.ID, (payload, context) -> {
            MinecraftClient.getInstance().execute(() -> handleGacha(payload));
        });
    }

    
    private void handleGacha(ChestOpenPayload payload) {
    	if(payload.chestDisplayCustomModelData<=0) {
    		handleChestAppearance(payload.pos);
    	}
    	else {
    		handleBlockDisplayAppearance(payload.pos,payload.chestDisplayCustomModelData);
    	}
    }

    private void handleBlockDisplayAppearance(BlockPos pos, int chestDisplayCustomModelData) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        ClientWorld world = client.world;
        
        // Crea el ItemStack y añade el custom model data vía NBT (más fiable)
        ItemStack stack = new ItemStack(Items.BARRIER);
        stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(chestDisplayCustomModelData));

        // Calcula la posición centrada (ajusta según lo necesites)
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;
        LOGGER.info("X:" + x + " Y:" + y + " Z:" + z);

        // Usa la subclase que evita el despawn automático
        PersistentItemDisplayEntity itemDisplay = new PersistentItemDisplayEntity(EntityType.ITEM_DISPLAY, world);
        itemDisplay.refreshPositionAndAngles(x, y, z, 0.0F, 0.0F);
        itemDisplay.setItemStack(stack);
        
        float tx = 0.0F, ty = 0.0F, tz = 0.0F;
        float scaleFactor = 1.0F;
        float rotationAngle = 0.0F;
        
        Matrix4f mat = new Matrix4f().identity();
        mat.translate(tx, ty, tz);
        mat.scale(scaleFactor);
        mat.rotateY(rotationAngle);
        
        AffineTransformation finalTransformation = new AffineTransformation(mat);
        itemDisplay.setTransformation(finalTransformation);
        
        world.addEntity(itemDisplay);

        // Si realmente necesitas eliminarla después de 20 segundos, mantén el scheduler
        scheduler.schedule(() -> {
            if (client.world != null) {
                client.world.removeEntity(itemDisplay.getId(), Entity.RemovalReason.DISCARDED);
            }
        }, 23, TimeUnit.SECONDS);
    }

    
    private void handleChestAppearance(BlockPos pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        Direction facingDirection = Direction.SOUTH;
        BlockState chestState = Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, facingDirection);
        client.world.setBlockState(pos, chestState, Block.NOTIFY_LISTENERS);

        scheduler.schedule(() -> {
            BlockEntity blockEntity = client.world.getBlockEntity(pos);
            if (blockEntity instanceof ChestBlockEntity) {
                ChestBlockEntity chestBlockEntity = (ChestBlockEntity) blockEntity;
                chestBlockEntity.onOpen(client.player);
                client.world.playSound(client.player, pos, SoundEvents.BLOCK_CHEST_OPEN, SoundCategory.MASTER, 1.0F, 1.0F);
            }
        }, 3, TimeUnit.SECONDS);
        
        scheduler.schedule(() -> {
            client.execute(() -> {
                BlockEntity blockEntity = client.world.getBlockEntity(pos);
                if (blockEntity instanceof ChestBlockEntity) {
                    ChestBlockEntity chestBlockEntity = (ChestBlockEntity) blockEntity;
                    chestBlockEntity.onClose(client.player);
                    BlockState state = client.world.getBlockState(pos);
                    client.world.addBlockBreakParticles(pos, state);
                    client.world.playSound(client.player, pos, SoundEvents.BLOCK_WOOD_BREAK, SoundCategory.MASTER, 1.0F, 1.0F);
                    client.world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                    client.world.removeBlockEntity(pos);
                }
            });
        }, 23, TimeUnit.SECONDS);
    }
}
