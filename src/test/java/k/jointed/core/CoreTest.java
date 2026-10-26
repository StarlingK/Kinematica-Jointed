package k.jointed.core;

import org.junit.jupiter.api.Test;

class CoreTest {
    @Test void quatMath() { CoreChecks.quatMath(); }
    @Test void chainFk() { CoreChecks.chainFk(); }
    @Test void fabrikReaches() { CoreChecks.fabrikReaches(); }
    @Test void ccdReaches() { CoreChecks.ccdReaches(); }
    @Test void jacobianReaches() { CoreChecks.jacobianReaches(); }
    @Test void unreachableStretches() { CoreChecks.unreachableStretches(); }
    @Test void twoBoneExactAndPole() { CoreChecks.twoBoneExactAndPole(); }
    @Test void twoBoneHingeFindsItsBend() { CoreChecks.twoBoneHingeFindsItsBend(); }
    @Test void hingeIsRespected() { CoreChecks.hingeIsRespected(); }
    @Test void ellipseAndFixed() { CoreChecks.ellipseAndFixed(); }
    @Test void jacobianMultiGoalAndOrientation() { CoreChecks.jacobianMultiGoalAndOrientation(); }
    @Test void fabrikWithRestBias() { CoreChecks.fabrikWithRestBias(); }
    @Test void orientationOnFabrik() { CoreChecks.orientationOnFabrik(); }
    @Test void dynamicsAndProcedural() { CoreChecks.dynamicsAndProcedural(); }
    @Test void curveFrames() { CoreChecks.curveFrames(); }
}
