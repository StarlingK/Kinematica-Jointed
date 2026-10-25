// the client-only setup lives in JointedClient
package k.jointed;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(Jointed.MOD_ID)
public class Jointed {
    public static final String MOD_ID = "jointed";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Jointed(IEventBus modBus, ModContainer container) {
        LOGGER.info("my joints feel good");
    }
}
