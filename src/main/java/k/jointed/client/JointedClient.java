// client-only mod entry, registers config, built-in targets and renderers, the layer, commands and rig reload
package k.jointed.client;

import k.jointed.Jointed;
import k.jointed.client.command.JointedCommands;
import k.jointed.client.demo.Demos;
import k.jointed.client.render.BuiltinRenderers;
import k.jointed.client.render.ChainFeatureLayer;
import k.jointed.client.render.WorldChains;
import k.jointed.client.rig.BuiltinTargets;
import k.jointed.client.rig.RigJsonLoader;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = Jointed.MOD_ID, dist = Dist.CLIENT)
public class JointedClient {
    public JointedClient(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        BuiltinTargets.registerAll();
        BuiltinRenderers.registerAll();

        modBus.addListener(JointedClient::onRegisterReloadListeners);
           modBus.addListener((EntityRenderersEvent.AddLayers event) -> ChainFeatureLayer.onAddLayers(event));

        NeoForge.EVENT_BUS.addListener(WorldChains::onClientTick);
        NeoForge.EVENT_BUS.addListener(WorldChains::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(JointedCommands::onRegister);
        NeoForge.EVENT_BUS.addListener(JointedClient::onLogout);
    }

    private static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new RigJsonLoader());
    }

    // leaving a world must not leave chains or demo rigs behind
    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        WorldChains.clear();
        Demos.stopAll();
    }
}
