// events fired around each limb solve so other mods can edit targets or read results
package k.jointed.api.event;

import k.jointed.api.IKTarget;
import k.jointed.core.IKChain;
import k.jointed.core.solver.SolveResult;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

// posted on NeoForge.EVENT_BUS client side from the render thread
public abstract class IKSolveEvent extends Event {
    private final LivingEntity entity;
    private final String limbName;

    protected IKSolveEvent(LivingEntity entity, String limbName) {
        this.entity = entity;
        this.limbName = limbName;
    }

    public LivingEntity getEntity() {
        return entity;
    }

    public String getLimbName() {
        return limbName;
    }

    // before solving: replace target, set null to disable limb for this frame, or cancel event
    public static class Pre extends IKSolveEvent implements ICancellableEvent {
        private IKTarget target;

        public Pre(LivingEntity entity, String limbName, IKTarget target) {
            super(entity, limbName);
            this.target = target;
        }

        public IKTarget getTarget() {
            return target;
        }

        public void setTarget(IKTarget target) {
            this.target = target;
        }
    }

    // after solving, before the result is written to the model; chain is in limb space (model pixels)

    public static class Post extends IKSolveEvent {
        private final IKChain chain;
        private final SolveResult result;

        public Post(LivingEntity entity, String limbName, IKChain chain, SolveResult result) {
            super(entity, limbName);
            this.chain = chain;
            this.result = result;
        }

        public IKChain getChain() {
            return chain;
        }

        public SolveResult getResult() {
            return result;
        }
    }
}
