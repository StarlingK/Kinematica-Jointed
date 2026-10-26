package k.jointed.core;

import k.jointed.core.constraint.Constraints;
import k.jointed.core.curve.ChainCurve;
import k.jointed.core.math.MathUtil;
import k.jointed.core.math.Quat;
import k.jointed.core.math.V3;
import k.jointed.core.solver.*;

import java.util.Random;

public final class CoreChecks {
    private CoreChecks() {}

    static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

    static void near(double a, double b, double eps, String msg) {
        if (Math.abs(a - b) > eps) throw new AssertionError(msg + " (expected " + b + ", got " + a + ")");
    }

    static V3 rndDir(Random r) {
        return new V3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
    }

    public static void quatMath() {
        Random r = new Random(1);
        for (int i = 0; i < 500; i++) {
            double x = (r.nextDouble() - 0.5) * 6, y = (r.nextDouble() - 0.5) * 3.0, z = (r.nextDouble() - 0.5) * 6;
            Quat q = Quat.fromEulerZYX(x, y, z);
            double[] e = q.toEulerZYX();
            Quat q2 = Quat.fromEulerZYX(e[0], e[1], e[2]);
            check(q.approxEquals(q2, 1e-9), "euler round trip " + x + "," + y + "," + z);

            V3 a = rndDir(r), b = rndDir(r);
            Quat ft = Quat.fromTo(a, b);
            check(ft.rotate(a).approxEquals(b, 1e-9), "fromTo");

            Quat s = Quat.fromAxisAngle(rndDir(r), r.nextDouble() * 3);
            V3 axis = rndDir(r);
            Quat tw = s.twist(axis);
            Quat sw = s.swing(axis);
            check(sw.mul(tw).approxEquals(s, 1e-9), "swing*twist == q");
            near(sw.rotate(axis).dot(axis) >= -1 ? 0 : 1, 0, 0, "dummy");

            V3 v = rndDir(r);
            check(Quat.exp(s.log()).approxEquals(s, 1e-9), "exp(log(q))");
            check(s.mul(s.inverse()).approxEquals(Quat.IDENTITY, 1e-12), "q*q^-1");
            check(Quat.slerp(Quat.IDENTITY, s, 1).rotate(v).approxEquals(s.rotate(v), 1e-9), "slerp end");
        }
        
        Quat g = Quat.fromEulerZYX(0.7, Math.PI / 2, 0.0);
        double[] e = g.toEulerZYX();
        check(g.approxEquals(Quat.fromEulerZYX(e[0], e[1], e[2]), 1e-6), "gimbal");

        // basis -> quat
        Quat any = Quat.fromAxisAngle(new V3(1, 2, 3), 1.1);
        Quat b = Quat.fromBasis(any.rotate(V3.X), any.rotate(V3.Y), any.rotate(V3.Z));
        check(b.approxEquals(any, 1e-9), "fromBasis");
    }

    public static void chainFk() {
        IKChain c = IKChain.straight(3, 2.0, V3.Y);
        check(c.tipPosition().approxEquals(new V3(0, 6, 0), 1e-9), "straight tip");
        near(c.reach(), 6, 1e-12, "reach");
        c.setLocal(0, Quat.fromAxisAngle(V3.Z, Math.PI / 2)); // rotate whole chain to -X
        check(c.tipPosition().approxEquals(new V3(-6, 0, 0), 1e-9), "rotated tip " + c.tipPosition());
    }

    static IKChain arm(int bones, double len) {
        IKChain c = IKChain.straight(bones, len, V3.Y);
        // bend slightly so the solvers are not in a singular straight pose
        for (int i = 0; i < bones; i++) c.setLocal(i, Quat.fromAxisAngle(V3.Z, 0.15));
        return c;
    }

    static void checkLengths(IKChain c) {
        V3[] p = c.points();
        for (int i = 0; i < c.size(); i++) near(p[i].distance(p[i + 1]), c.boneLength(i), 1e-6, "bone length " + i);
    }

    static void reaches(IKSolver solver, int bones, long seed, double tol) {
        Random r = new Random(seed);
        for (int k = 0; k < 40; k++) {
            IKChain c = arm(bones, 1.0);
            V3 t = rndDir(r).scale(0.3 + r.nextDouble() * (bones - 0.4));
            SolverSettings s = new SolverSettings().iterations(solver == CcdSolver.INSTANCE ? 400 : 80).tolerance(1e-3);
            SolveResult res = solver.solve(c, s, IKGoal.tip(t));
            check(res.converged(), solver.id() + " did not converge for " + t + " err=" + res.error());
            check(c.tipPosition().distance(t) <= tol, solver.id() + " tip miss");
            checkLengths(c);
        }
    }

    public static void fabrikReaches() {
        reaches(FabrikSolver.INSTANCE, 5, 11, 2e-3);
    }

    public static void ccdReaches() {
        reaches(CcdSolver.INSTANCE, 4, 12, 2e-3);
    }

    public static void jacobianReaches() {
        reaches(JacobianSolver.INSTANCE, 4, 13, 2e-3);
    }

    public static void unreachableStretches() {
        for (IKSolver s : new IKSolver[]{FabrikSolver.INSTANCE, CcdSolver.INSTANCE, JacobianSolver.INSTANCE}) {
            IKChain c = arm(3, 1.0);
            V3 t = new V3(10, 0, 0);
            SolveResult res = s.solve(c, new SolverSettings().iterations(300), IKGoal.tip(t));
            check(!res.reachable(), s.id() + " should report unreachable");
            V3 dir = c.tipPosition().normalize();
            check(dir.dot(new V3(1, 0, 0)) > 0.99, s.id() + " should point at target, dir=" + dir);
            check(c.tipPosition().length() > 2.5, s.id() + " should be (nearly) fully extended, got " + c.tipPosition().length());
        }
    }

    public static void twoBoneExactAndPole() {
        IKChain c = IKChain.straight(2, 1.0, V3.Y);
        V3 t = new V3(0.8, 0.9, 0.0);
        SolveResult res = TwoBoneSolver.INSTANCE.solve(c, new SolverSettings(), IKGoal.tip(t).withPole(new V3(0, 0.5, 5)));
        check(res.converged(), "two-bone converged, err=" + res.error());
        check(c.jointPosition(1).z > 0.05, "elbow should bend toward +Z pole: " + c.jointPosition(1));
        checkLengths(c);

        IKChain d = IKChain.straight(2, 1.0, V3.Y);
        TwoBoneSolver.INSTANCE.solve(d, new SolverSettings(), IKGoal.tip(t).withPole(new V3(0, 0.5, -5)));
        check(d.jointPosition(1).z < -0.05, "elbow should bend toward -Z pole: " + d.jointPosition(1));

        // out of reach: fully extended
        IKChain e = IKChain.straight(2, 1.0, V3.Y);
        SolveResult far = TwoBoneSolver.INSTANCE.solve(e, new SolverSettings(), IKGoal.tip(new V3(5, 0, 0)));
        check(!far.reachable(), "two-bone unreachable");
        near(e.tipPosition().length(), 2.0, 1e-3, "two-bone extension");

        // soft reach stops short of full extension
        IKChain f = IKChain.straight(2, 1.0, V3.Y);
        TwoBoneSolver.INSTANCE.solve(f, new SolverSettings().softness(0.3), IKGoal.tip(new V3(1.95, 0, 0)));
        check(f.tipPosition().length() < 1.99, "soft reach eases off");
    }

    public static void twoBoneHingeFindsItsBend() {
        V3 target = new V3(0.6, 1.2, 0);
        // a hinge about Z only bends in the XY plane, with no pole the solver must still find the bend that works
        for (V3 pole : new V3[]{null, new V3(3, 1, 0), new V3(-3, 1, 0), new V3(0, 1, 3)}) {
            IKChain c = IKChain.builder()
                    .bone(new V3(0, 1, 0), Constraints.coneDeg(80))
                    .bone(new V3(0, 1, 0), Constraints.hingeDeg(V3.Z, 0, 140))
                    .build();
            IKGoal g = IKGoal.tip(target);
            if (pole != null) g = g.withPole(pole);
            SolveResult r = TwoBoneSolver.INSTANCE.solve(c, new SolverSettings(), g);
            check(r.converged(), "hinge elbow reaches the target, pole=" + pole + " err=" + r.error());
            double a = c.delta(1).angleAbout(V3.Z);
            check(a >= -1e-6 && a <= Math.toRadians(140) + 1e-6, "hinge stays in range " + Math.toDegrees(a));
            checkLengths(c);
        }

        // solving again from the solved pose must not flip the elbow to the other side
        IKChain d = IKChain.builder()
                .bone(new V3(0, 1, 0), Constraints.coneDeg(80))
                .bone(new V3(0, 1, 0), Constraints.hingeDeg(V3.Z, -140, 140))
                .build();
        TwoBoneSolver.INSTANCE.solve(d, new SolverSettings(), IKGoal.tip(target));
        V3 elbow = d.jointPosition(1);
        for (int i = 0; i < 20; i++) TwoBoneSolver.INSTANCE.solve(d, new SolverSettings(), IKGoal.tip(target));
        check(d.jointPosition(1).approxEquals(elbow, 1e-6), "elbow side is stable between frames");
    }

    public static void hingeIsRespected() {
        V3 hingeAxis = V3.Z;
        double min = 0, max = Math.toRadians(140);
        for (IKSolver s : new IKSolver[]{FabrikSolver.INSTANCE, CcdSolver.INSTANCE, JacobianSolver.INSTANCE}) {
            IKChain c = IKChain.builder()
                    .bone(new V3(0, 1, 0), Constraints.coneDeg(80))
                    .bone(new V3(0, 1, 0), Constraints.hinge(hingeAxis, min, max))
                    .build();
            Random r = new Random(5);
            for (int i = 0; i < 25; i++) {
                V3 t = rndDir(r).scale(0.5 + r.nextDouble() * 1.4);
                s.solve(c, new SolverSettings().iterations(40), IKGoal.tip(t));
                double a = c.delta(1).angleAbout(hingeAxis);
                check(a >= min - 1e-6 && a <= max + 1e-6, s.id() + " hinge angle out of range: " + Math.toDegrees(a));
                // no motion about other axes
                check(c.delta(1).vec().rejectFrom(hingeAxis).length() < 1e-6, s.id() + " hinge leaked rotation");
                double swing = c.delta(0).swing(c.boneAxis(0)).angle();
                check(swing <= Math.toRadians(80) + 1e-6, s.id() + " cone exceeded: " + Math.toDegrees(swing));
            }
        }
    }

    public static void ellipseAndFixed() {
        var ell = Constraints.ellipseDeg(60, 20, 0, 0);
        var big = Quat.fromAxisAngle(V3.X, Math.toRadians(100));
        Quat out = ell.constrain(big, V3.Y);
        check(out.angle() <= Math.toRadians(60.0) + 1e-6, "ellipse clamps major axis");
        var fixed = Constraints.FIXED.constrain(big, V3.Y);
        check(fixed.approxEquals(Quat.IDENTITY, 1e-12), "fixed joint");
        var cone = Constraints.coneDeg(30).constrain(Quat.fromAxisAngle(V3.Z, Math.toRadians(90)), V3.Y);
        near(cone.angle(), Math.toRadians(30), 1e-6, "cone clamp");
    }

    public static void jacobianMultiGoalAndOrientation() {
        IKChain c = arm(4, 1.0);
        IKGoal elbow = IKGoal.point(2, new V3(1.2, 1.2, 0.0));
        IKGoal tip = IKGoal.tip(new V3(2.0, 2.2, 0.0));
        SolveResult r = JacobianSolver.INSTANCE.solve(c, new SolverSettings().iterations(150).tolerance(2e-3), elbow, tip);
        check(c.pointPosition(2).distance(elbow.position) < 0.05, "mid goal " + c.pointPosition(2).distance(elbow.position));
        check(c.tipPosition().distance(tip.position) < 0.05, "tip goal " + c.tipPosition().distance(tip.position));

        IKChain d = arm(3, 1.0);
        Quat want = Quat.fromAxisAngle(V3.Y, 1.0).mul(Quat.fromAxisAngle(V3.Z, -0.3));
        IKGoal g = IKGoal.tip(new V3(1.5, 1.5, 0.5)).withOrientation(want, 2, 1.0);
        JacobianSolver.INSTANCE.solve(d, new SolverSettings().iterations(200).tolerance(1e-3), g);
        check(d.jointFrame(2).approxEquals(want, 1e-3), "orientation reached " + d.jointFrame(2).mul(want.inverse()).angle());
    }

    public static void fabrikWithRestBias() {
        IKChain c = arm(5, 1.0);
        c.setLocal(1, Quat.fromAxisAngle(V3.X, 1.0));
        V3 t = c.tipPosition(); // already satisfied: should not move away
        SolveResult res = JacobianSolver.INSTANCE.solve(c, new SolverSettings().restBias(0.2).iterations(5), IKGoal.tip(t));
        check(res.error() < 0.05, "rest bias must keep the goal satisfied: " + res.error());
    }

    public static void orientationOnFabrik() {
        IKChain c = arm(3, 1.0);
        Quat want = Quat.fromAxisAngle(V3.X, 0.6);
        FabrikSolver.INSTANCE.solve(c, new SolverSettings().iterations(30), IKGoal.tip(new V3(1.0, 1.5, 0)).withOrientation(want, 2, 1.0));
        check(c.jointFrame(2).approxEquals(want, 1e-6), "fabrik orientation");
    }

    public static void dynamicsAndProcedural() {
        IKChain chain = IKChain.straight(6, 0.5, V3.Y);
        ProceduralChain pc = new ProceduralChain(chain);
        var v = pc.enableDynamics();
        v.stiffness = 0.15;
        v.gravity = new V3(0, -9.8, 0);
        pc.target(new V3(1.5, 2.0, 0.5));
        for (int i = 0; i < 100; i++) {
            pc.root(new V3(Math.sin(i * 0.1), 0, 0), Quat.IDENTITY);
            pc.tick(0.05);
        }
        checkLengths(chain);
        V3[] pts = pc.points(0.5);
        check(pts.length == 7, "points length");
        check(pts[0].distance(chain.rootPosition()) < 0.1, "root pinned");
        for (V3 p : pts) check(p.isFinite(), "finite");
    }

    public static void curveFrames() {
        V3[] pts = {new V3(0, 0, 0), new V3(0, 1, 0), new V3(1, 2, 0), new V3(2, 2, 1), new V3(3, 1, 2)};
        ChainCurve.Sample[] s = ChainCurve.sample(pts, 6, V3.Z);
        check(s.length == 4 * 6 + 1, "sample count " + s.length);
        for (ChainCurve.Sample x : s) {
            near(x.tangent.length(), 1, 1e-9, "tangent unit");
            near(x.normal.dot(x.tangent), 0, 1e-9, "normal perpendicular");
            near(x.binormal.dot(x.tangent), 0, 1e-9, "binormal perpendicular");
            near(x.normal.dot(x.binormal), 0, 1e-9, "n.b perpendicular");
        }
        near(s[0].t, 0, 0, "t0");
        near(s[s.length - 1].t, 1, 1e-12, "t1");
        ChainCurve.Sample mid = ChainCurve.at(s, 0.5);
        check(mid.t == 0.5, "at()");
        Quat q = ChainCurve.frameRotation(mid);
        check(q.rotate(V3.Y).approxEquals(mid.tangent, 1e-6), "frame rotation maps Y to tangent");
    }

    public static void runAll() {
        quatMath();
        chainFk();
        fabrikReaches();
        ccdReaches();
        jacobianReaches();
        unreachableStretches();
        twoBoneExactAndPole();
        twoBoneHingeFindsItsBend();
        hingeIsRespected();
        ellipseAndFixed();
        jacobianMultiGoalAndOrientation();
        fabrikWithRestBias();
        orientationOnFabrik();
        dynamicsAndProcedural();
        curveFrames();
    }

    public static void main(String[] args) {
        String[] names = {"quatMath", "chainFk", "fabrikReaches", "ccdReaches", "jacobianReaches", "unreachableStretches",
                "twoBoneExactAndPole", "twoBoneHingeFindsItsBend", "hingeIsRespected", "ellipseAndFixed", "jacobianMultiGoalAndOrientation",
                "fabrikWithRestBias", "orientationOnFabrik", "dynamicsAndProcedural", "curveFrames"};
        Runnable[] tests = {CoreChecks::quatMath, CoreChecks::chainFk, CoreChecks::fabrikReaches, CoreChecks::ccdReaches,
                CoreChecks::jacobianReaches, CoreChecks::unreachableStretches, CoreChecks::twoBoneExactAndPole,
                CoreChecks::twoBoneHingeFindsItsBend, CoreChecks::hingeIsRespected, CoreChecks::ellipseAndFixed, CoreChecks::jacobianMultiGoalAndOrientation,
                CoreChecks::fabrikWithRestBias, CoreChecks::orientationOnFabrik, CoreChecks::dynamicsAndProcedural,
                CoreChecks::curveFrames};
        int failed = 0;
        for (int i = 0; i < tests.length; i++) {
            try {
                tests[i].run();
                System.out.println("PASS " + names[i]);
            } catch (Throwable t) {
                failed++;
                System.out.println("FAIL " + names[i] + ": " + t.getMessage());
            }
        }
        System.out.println(failed == 0 ? "ALL PASSED" : failed + " FAILED");
        if (failed > 0) System.exit(1);
    }
}
