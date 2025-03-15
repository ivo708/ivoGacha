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
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;


public class IvogachaClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("ivoGachaClient");

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    public class PersistentItemDisplayEntity extends ItemDisplayEntity {
        private boolean allowDiscard = false;
        private double anchoredX;
        private double anchoredY;
        private double anchoredZ;

        public PersistentItemDisplayEntity(EntityType<? extends ItemDisplayEntity> type, ClientWorld world,double x,double y,double z) {
            super(type, world);
            this.anchoredX = x;
            this.anchoredY = y;
            this.anchoredZ = z;
            
        }

        public void setAllowDiscard(boolean allowDiscard) {
            this.allowDiscard = allowDiscard;
        }
        @Override
        public void move(MovementType type, Vec3d movement) {
        }

        @Override
        public void tick() {
            super.tick();
            this.setPos(anchoredX, anchoredY, anchoredZ);
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
        
        ItemStack stack = new ItemStack(Items.BARRIER);
        stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(chestDisplayCustomModelData));

        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;
        LOGGER.info("X:" + x + " Y:" + y + " Z:" + z);

        PersistentItemDisplayEntity itemDisplay = new PersistentItemDisplayEntity(EntityType.ITEM_DISPLAY, world,x,y,z);
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
        spawnWaxOnParticles(world,x,y,z);
        scheduler.schedule(() -> {
            if (client.world != null) {
            	itemDisplay.setAllowDiscard(true);
                spawnWaxOnParticles(world,x,y,z);
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
    
    
    public static void spawnWaxOnParticles(ClientWorld world, double x, double y, double z) {
        int count = 100;
        for (int i = 0; i < count; i++) {
            double offsetX = (Math.random() - 0.5) * 3;
            double offsetY = (Math.random() - 0.5) * 3;
            double offsetZ = (Math.random() - 0.5) * 3;
            double velocityX = 1;
            double velocityY = 1;
            double velocityZ = 1;

            world.addParticle(ParticleTypes.WAX_OFF, x + offsetX, y + offsetY, z + offsetZ, velocityX, velocityY, velocityZ);
        }
    }
}
