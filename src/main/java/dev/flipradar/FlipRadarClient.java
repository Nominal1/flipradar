package dev.flipradar;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FlipRadarClient implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("FlipRadar");

    @Override
    public void onInitializeClient() {
        DataService data = new DataService();
        DashboardServer server = new DashboardServer(data);
        boolean ok = server.start();
        if (ok) {
            data.start();
            LOG.info("FlipRadar дашборд: {}", server.url());
        } else {
            LOG.error("FlipRadar: не удалось занять порт 8765-8775");
        }

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            for (String name : new String[]{"flips", "flipradar"}) {
                dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal(name)
                        .executes(ctx -> {
                            if (!ok) {
                                ctx.getSource().sendFeedback(Component.literal("[FlipRadar] Не удалось запустить дашборд (порт занят)."));
                                return 0;
                            }
                            BrowserOpener.open(server.url());
                            ctx.getSource().sendFeedback(Component.literal("[FlipRadar] Дашборд: " + server.url()));
                            return 1;
                        }));
            }
        });
    }
}
