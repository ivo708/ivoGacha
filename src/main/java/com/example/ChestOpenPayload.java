package com.example;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
public class ChestOpenPayload implements CustomPayload {
    final BlockPos pos;
    final int chestDisplayCustomModelData;

    

    // ID del CustomPayload
    public static final CustomPayload.Id<ChestOpenPayload> ID =new CustomPayload.Id<>(Identifier.of("ivogacha", "chest_open"));

    // Constructor para inicializar desde un PacketByteBuf
    public ChestOpenPayload(PacketByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.chestDisplayCustomModelData=buf.readInt();

    }

    // Constructor para inicializar desde un BlockPos
    public ChestOpenPayload(BlockPos pos,int chestDisplayCustomModelData) {
		super();
		this.pos = pos;
		this.chestDisplayCustomModelData = chestDisplayCustomModelData;
	}

    @Override
    public CustomPayload.Id<ChestOpenPayload> getId() {
        return ID;
    }



	public BlockPos getPos() {
        return pos;
    }
    public int getchestDisplayCustomModelData() {
        return chestDisplayCustomModelData;
    }


    public void write(PacketByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeInt(chestDisplayCustomModelData);

    }

    // Implementación de PacketCodec para ChestOpenPayload
    public static class Codec implements PacketCodec<RegistryByteBuf, ChestOpenPayload> {
        @Override
        public void encode(RegistryByteBuf buf, ChestOpenPayload payload) {
            payload.write(buf);  // Escribe el contenido de ChestOpenPayload en el buf
        }

		@Override
		public ChestOpenPayload decode(RegistryByteBuf buf) {
			// TODO Auto-generated method stub
            return new ChestOpenPayload(buf);  // Lee y devuelve una nueva instancia de ChestOpenPayload
		}
    }

    // Método estático para registrar el payload en PayloadTypeRegistry
    public static void register() {
        // Registra el payload con el codec
        PayloadTypeRegistry.playS2C().register(ID, new Codec());
    }
}
