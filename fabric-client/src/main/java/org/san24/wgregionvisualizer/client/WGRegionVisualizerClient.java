package org.san24.wgregionvisualizer.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.chat.Component;

public final class WGRegionVisualizerClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.playS2C().register(WGVizPayload.TYPE, WGVizPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(WGVizPayload.TYPE, (payload, context) ->
                context.client().execute(() -> RegionMessageHandler.handle(payload.json())));

        RegionRenderer.register();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("wgvizclient")
                        .then(ClientCommandManager.literal("clear")
                                .executes(context -> {
                                    RegionStore.clear();
                                    context.getSource().sendFeedback(Component.literal("WG Region Visualizer: 表示を消去しました。"));
                                    return 1;
                                }))
                        .then(ClientCommandManager.literal("list")
                                .executes(context -> {
                                    if (RegionStore.regions().isEmpty()) {
                                        context.getSource().sendFeedback(Component.literal("WG Region Visualizer: 表示中のリージョンはありません。"));
                                        return 1;
                                    }
                                    String ids = String.join(", ", RegionStore.regions().keySet());
                                    context.getSource().sendFeedback(Component.literal("WG Region Visualizer: " + ids));
                                    return 1;
                                }))
        ));
    }
}
