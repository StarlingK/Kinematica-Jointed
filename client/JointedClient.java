package k.jointed.client;

import k.jointed.Jointed;
import k.jointed.client.rig.BuiltinTargets;
import k.jointed.client.rig.RigJsonLoader;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

@Mod(value = Jointed.MOD_ID, dist = Dist.CLIENT)
public class JointedClient {
    public JointedClient(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        BuiltinTargets.registerAll();
        modBus.addListener(JointedClient::onRegisterReloadListeners);
    }

    private static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new RigJsonLoader());
    }
}
