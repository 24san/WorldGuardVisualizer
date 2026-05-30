package org.san24.wgregionvisualizer.client;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

public record WGVizPayload(String json) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<WGVizPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("san24", "wgviz"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WGVizPayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBytes(payload.json.getBytes(StandardCharsets.UTF_8)),
            buf -> {
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                return new WGVizPayload(new String(bytes, StandardCharsets.UTF_8));
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
