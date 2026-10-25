// coordinate spaces an ik target position can be expressed in
package k.jointed.api;

// space of an ikTarget
// world: absolute world coordinates in blocks
// entity: blocks relative to entity's feet, rotated with body yaw: +z forward, +x left, +y up
// model: raw model-part pixels (1/16 block), space modelPart offsets are written in (available in gui renders)
// view: first-person only: blocks relative to camera in eye space
// world and entity targets are skipped when drawn outside the world (inventory screen, etc.)

public enum TargetSpace {
    WORLD, ENTITY, MODEL, VIEW
}
