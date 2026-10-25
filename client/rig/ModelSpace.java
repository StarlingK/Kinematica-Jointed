// converts between world / view space and model-part pixel space using the pose captured at the rend hook
package k.jointed.client.rig;

import com.mojang.blaze3d.vertex.PoseStack;
import k.jointed.core.math.V3;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class ModelSpace {
    private final Matrix4f toOuter;
    private final Matrix4f toModel;
    private final Vec3 camera;
    private final Quaternionf cameraRotation;
    public final boolean worldValid;
    public final boolean firstPerson;

    private ModelSpace(Matrix4f pose, Vec3 camera, Quaternionf cameraRotation, boolean worldValid, boolean firstPerson) {
        this.toOuter = new Matrix4f(pose);
        this.toModel = new Matrix4f(pose).invert();
        this.camera = camera;
        this.cameraRotation = cameraRotation;
        this.worldValid = worldValid;
        this.firstPerson = firstPerson;
    }

    public static ModelSpace forEntity(LivingEntity entity, float partial, PoseStack stack, Vec3 camera) {
        Matrix4f pose = stack.last().pose();
        Vector3f origin = pose.transformPosition(0f, 1.501f, 0f, new Vector3f());
        Vec3 expected = entity.getPosition(partial).subtract(camera);
        boolean valid = Math.abs(origin.x - expected.x) < 0.75 && Math.abs(origin.y - expected.y) < 0.75
                && Math.abs(origin.z - expected.z) < 0.75;
        return new ModelSpace(pose, camera, null, valid, false);
    }

    public static ModelSpace forFirstPerson(PoseStack stack, Vec3 camera, Quaternionf cameraRotation) {
        return new ModelSpace(stack.last().pose(), camera, new Quaternionf(cameraRotation), true, true);
    }

    public V3 worldToModel(V3 w) {
        Vector3f v = new Vector3f((float) (w.x - camera.x), (float) (w.y - camera.y), (float) (w.z - camera.z));
        if (cameraRotation != null) new Quaternionf(cameraRotation).conjugate().transform(v);
        toModel.transformPosition(v);
        return new V3(v.x * 16.0, v.y * 16.0, v.z * 16.0);
    }

    public V3 modelToWorld(V3 px) {
        Vector3f v = new Vector3f((float) (px.x / 16.0), (float) (px.y / 16.0), (float) (px.z / 16.0));
        toOuter.transformPosition(v);
        if (cameraRotation != null) cameraRotation.transform(v);
        return new V3(v.x + camera.x, v.y + camera.y, v.z + camera.z);
    }

    public V3 viewToModel(V3 view) {
        Vector3f v = new Vector3f((float) view.x, (float) view.y, (float) view.z);
        toModel.transformPosition(v);
        return new V3(v.x * 16.0, v.y * 16.0, v.z * 16.0);
    }
}
