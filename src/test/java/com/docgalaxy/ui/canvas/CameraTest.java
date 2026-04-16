package com.docgalaxy.ui.canvas;

import com.docgalaxy.model.Vector2D;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Camera}.
 *
 * <p>Canvas is 400 × 300 throughout unless otherwise stated.
 */
class CameraTest {

    private static final double DELTA   = 1e-9;
    private static final int    W       = 400;
    private static final int    H       = 300;

    private Camera camera;

    @BeforeEach
    void setUp() {
        camera = new Camera(W, H);
    }

    // -----------------------------------------------------------------------
    // Constructor validation
    // -----------------------------------------------------------------------

    @Test
    void constructor_zeroWidth_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new Camera(0, H));
    }

    @Test
    void constructor_negativeWidth_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new Camera(-1, H));
    }

    @Test
    void constructor_zeroHeight_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new Camera(W, 0));
    }

    @Test
    void constructor_negativeHeight_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new Camera(W, -1));
    }

    // -----------------------------------------------------------------------
    // Initial state
    // -----------------------------------------------------------------------

    @Test
    void initialZoom_isOne() {
        assertEquals(1.0, camera.getZoom(), DELTA);
    }

    @Test
    void initialOffsets_areZero() {
        assertEquals(0.0, camera.getOffsetX(), DELTA);
        assertEquals(0.0, camera.getOffsetY(), DELTA);
    }

    @Test
    void defaultConstructor_usesAppConstantsDimensions() {
        Camera def = new Camera();
        assertTrue(def.getViewportWidth()  > 0);
        assertTrue(def.getViewportHeight() > 0);
    }

    // -----------------------------------------------------------------------
    // setViewportSize()
    // -----------------------------------------------------------------------

    @Test
    void setViewportSize_updatesWidth() {
        camera.setViewportSize(800, 600);
        assertEquals(800, camera.getViewportWidth());
        assertEquals(600, camera.getViewportHeight());
    }

    @Test
    void setViewportSize_zeroWidth_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> camera.setViewportSize(0, H));
    }

    @Test
    void setViewportSize_negativeHeight_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> camera.setViewportSize(W, -1));
    }

    // -----------------------------------------------------------------------
    // pan()
    // -----------------------------------------------------------------------

    @Test
    void pan_addsToOffsets() {
        camera.pan(30.0, -20.0);
        assertEquals(30.0, camera.getOffsetX(), DELTA);
        assertEquals(-20.0, camera.getOffsetY(), DELTA);
    }

    @Test
    void pan_accumulatesOverMultipleCalls() {
        camera.pan(10.0, 5.0);
        camera.pan(-3.0, 7.0);
        assertEquals(7.0,  camera.getOffsetX(), DELTA);
        assertEquals(12.0, camera.getOffsetY(), DELTA);
    }

    @Test
    void pan_zeroDeltas_noChange() {
        camera.pan(50.0, 50.0);
        camera.pan(0.0, 0.0);
        assertEquals(50.0, camera.getOffsetX(), DELTA);
        assertEquals(50.0, camera.getOffsetY(), DELTA);
    }

    // -----------------------------------------------------------------------
    // zoomAt()
    // -----------------------------------------------------------------------

    @Test
    void zoomAt_updatesZoom() {
        camera.zoomAt(200.0, 150.0, 2.0);
        assertEquals(2.0, camera.getZoom(), DELTA);
    }

    @Test
    void zoomAt_keepsPivotWorldPointFixed() {
        // Pivot at screen centre (200,150). After zoom×2, that screen point
        // must map to the same world position as before.
        Vector2D worldBefore = camera.screenToWorld(new Point(200, 150));
        camera.zoomAt(200.0, 150.0, 2.0);
        Vector2D worldAfter = camera.screenToWorld(new Point(200, 150));

        assertEquals(worldBefore.getX(), worldAfter.getX(), DELTA,
                "pivot world x must not change after zoomAt");
        assertEquals(worldBefore.getY(), worldAfter.getY(), DELTA,
                "pivot world y must not change after zoomAt");
    }

    @Test
    void zoomAt_verySmallFactor_clampsToMinZoom() {
        camera.zoomAt(0.0, 0.0, 1e-10);
        assertEquals(Camera.MIN_ZOOM, camera.getZoom(), DELTA);
    }

    @Test
    void zoomAt_veryLargeFactor_clampsToMaxZoom() {
        camera.zoomAt(0.0, 0.0, 1e10);
        assertEquals(Camera.MAX_ZOOM, camera.getZoom(), DELTA);
    }

    @Test
    void zoomAt_factorThatWouldExceedMax_clampsToMax() {
        // zoom starts at 1.0; factor=10 → 10 > MAX_ZOOM=5.0 → clamped
        camera.zoomAt(0.0, 0.0, 10.0);
        assertEquals(Camera.MAX_ZOOM, camera.getZoom(), DELTA);
    }

    @Test
    void zoomAt_factorThatWouldGoBelowMin_clampsToMin() {
        // zoom=1.0; factor=0.001 → 0.001 < MIN_ZOOM=0.05 → clamped
        camera.zoomAt(0.0, 0.0, 0.001);
        assertEquals(Camera.MIN_ZOOM, camera.getZoom(), DELTA);
    }

    @Test
    void zoomAt_offsetAdjustedCorrectly() {
        // zoom in ×2 centred at (200, 150) with initial zoom=1, offset=(0,0)
        // worldX = (200-0)/1 = 200; worldY = (150-0)/1 = 150
        // newZoom=2; offsetX = 200 - 200*2 = -200; offsetY = 150 - 150*2 = -150
        camera.zoomAt(200.0, 150.0, 2.0);
        assertEquals(-200.0, camera.getOffsetX(), DELTA);
        assertEquals(-150.0, camera.getOffsetY(), DELTA);
    }

    // -----------------------------------------------------------------------
    // worldToScreen() / screenToWorld()
    // -----------------------------------------------------------------------

    @Test
    void worldToScreen_nullArg_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> camera.worldToScreen(null));
    }

    @Test
    void screenToWorld_nullArg_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> camera.screenToWorld(null));
    }

    @Test
    void worldToScreen_defaultCamera_identityMapping() {
        // zoom=1, offset=(0,0) → screen coords equal world coords
        Point p = camera.worldToScreen(new Vector2D(100.0, 200.0));
        assertEquals(100, p.x);
        assertEquals(200, p.y);
    }

    @Test
    void screenToWorld_defaultCamera_identityMapping() {
        Vector2D w = camera.screenToWorld(new Point(100, 200));
        assertEquals(100.0, w.getX(), DELTA);
        assertEquals(200.0, w.getY(), DELTA);
    }

    @Test
    void worldToScreen_withPan_offsetApplied() {
        camera.pan(50.0, -30.0);
        Point p = camera.worldToScreen(new Vector2D(100.0, 200.0));
        assertEquals(150, p.x);   // 100*1 + 50
        assertEquals(170, p.y);   // 200*1 - 30
    }

    @Test
    void worldToScreen_withZoom_scalesCoordinates() {
        camera.zoomAt(0.0, 0.0, 2.0);
        Point p = camera.worldToScreen(new Vector2D(50.0, 75.0));
        // zoom=2, offset=(0,0) → screen = (100, 150)
        assertEquals(100, p.x);
        assertEquals(150, p.y);
    }

    @Test
    void screenToWorld_withPan_reverses() {
        camera.pan(50.0, -30.0);
        Vector2D w = camera.screenToWorld(new Point(150, 170));
        assertEquals(100.0, w.getX(), DELTA);
        assertEquals(200.0, w.getY(), DELTA);
    }

    @Test
    void roundTrip_worldToScreenToWorld_integerWorldCoords() {
        camera.pan(13.0, -7.0);
        camera.zoomAt(100.0, 100.0, 1.5);

        Vector2D original = new Vector2D(80.0, 120.0);
        Point screen = camera.worldToScreen(original);
        // Round-trip via screen coordinates: may lose sub-pixel precision
        Vector2D back = camera.screenToWorld(screen);
        // Allow 1 pixel of rounding error = 1/zoom in world space
        double tolerance = 1.0 / camera.getZoom() + DELTA;
        assertEquals(original.getX(), back.getX(), tolerance);
        assertEquals(original.getY(), back.getY(), tolerance);
    }

    // -----------------------------------------------------------------------
    // getTransform()
    // -----------------------------------------------------------------------

    @Test
    void getTransform_defaultCamera_scaleOneTranslateZero() {
        AffineTransform at = camera.getTransform();
        assertEquals(1.0, at.getScaleX(),     DELTA);
        assertEquals(1.0, at.getScaleY(),     DELTA);
        assertEquals(0.0, at.getTranslateX(), DELTA);
        assertEquals(0.0, at.getTranslateY(), DELTA);
    }

    @Test
    void getTransform_afterZoomAt_reflectsZoomAndOffset() {
        camera.zoomAt(200.0, 150.0, 2.0);
        AffineTransform at = camera.getTransform();

        assertEquals(2.0,   at.getScaleX(),     DELTA);
        assertEquals(2.0,   at.getScaleY(),     DELTA);
        assertEquals(-200.0, at.getTranslateX(), DELTA);
        assertEquals(-150.0, at.getTranslateY(), DELTA);
    }

    @Test
    void getTransform_afterPan_reflectsOffset() {
        camera.pan(30.0, -20.0);
        AffineTransform at = camera.getTransform();
        assertEquals(30.0,  at.getTranslateX(), DELTA);
        assertEquals(-20.0, at.getTranslateY(), DELTA);
    }

    @Test
    void getTransform_returnsFreshCopy_mutatingDoesNotAffectCamera() {
        AffineTransform at = camera.getTransform();
        at.translate(999.0, 999.0);
        // Camera offsets must be unaffected
        assertEquals(0.0, camera.getOffsetX(), DELTA);
        assertEquals(0.0, camera.getOffsetY(), DELTA);
    }

    // -----------------------------------------------------------------------
    // fitAll() — edge cases
    // -----------------------------------------------------------------------

    @Test
    void fitAll_null_isNoOp() {
        camera.pan(10.0, 10.0);
        camera.fitAll(null);
        assertEquals(10.0, camera.getOffsetX(), DELTA);
    }

    @Test
    void fitAll_emptyList_isNoOp() {
        camera.pan(10.0, 10.0);
        camera.fitAll(Collections.emptyList());
        assertEquals(10.0, camera.getOffsetX(), DELTA);
    }

    @Test
    void fitAll_singlePoint_centresPointInViewport() {
        // Single point → bboxW=bboxH=0 → paddedW=W, paddedH=H → zoom = min(W/W, H/H) = 1
        camera.fitAll(List.of(new Vector2D(100.0, 200.0)));

        assertEquals(1.0, camera.getZoom(), DELTA);
        // After fit: world centre (100,200) must map to screen centre (200,150)
        Point p = camera.worldToScreen(new Vector2D(100.0, 200.0));
        assertEquals(W / 2, p.x);
        assertEquals(H / 2, p.y);
    }

    @Test
    void fitAll_twoPoints_zoomFitsLargerDimension() {
        // bbox: x∈[0,200], y∈[0,100] → paddedW=240, paddedH=120
        // zoom = min(400/240, 300/120) = min(1.667, 2.5) = 5/3 ≈ 1.667
        camera.fitAll(List.of(new Vector2D(0.0, 0.0), new Vector2D(200.0, 100.0)));

        double expectedZoom = 400.0 / 240.0;  // 5/3, the smaller of the two ratios
        assertEquals(expectedZoom, camera.getZoom(), 1e-9);
    }

    @Test
    void fitAll_twoPoints_worldCentreMapsToScreenCentre() {
        camera.fitAll(List.of(new Vector2D(0.0, 0.0), new Vector2D(200.0, 100.0)));

        Point p = camera.worldToScreen(new Vector2D(100.0, 50.0));   // world centre
        assertEquals(W / 2, p.x, "world centre x must map to viewport centre x");
        assertEquals(H / 2, p.y, "world centre y must map to viewport centre y");
    }

    @Test
    void fitAll_zeroBboxWidth_usesViewportWidth() {
        // All points share the same x → bboxW=0 → paddedW = viewportWidth
        List<Vector2D> pts = List.of(new Vector2D(50.0, 0.0), new Vector2D(50.0, 100.0));
        camera.fitAll(pts);

        // paddedH = 100*1.2 = 120; zoom = min(400/400, 300/120) = min(1.0, 2.5) = 1.0
        assertEquals(1.0, camera.getZoom(), DELTA);
    }

    @Test
    void fitAll_zeroBboxHeight_usesViewportHeight() {
        // All points share the same y → bboxH=0 → paddedH = viewportHeight
        List<Vector2D> pts = List.of(new Vector2D(0.0, 50.0), new Vector2D(100.0, 50.0));
        camera.fitAll(pts);

        // paddedW = 100*1.2 = 120; zoom = min(400/120, 300/300) = min(3.33, 1.0) = 1.0
        assertEquals(1.0, camera.getZoom(), DELTA);
    }

    @Test
    void fitAll_largeBbox_zoomClampedToMin() {
        // Very wide world bbox → would need zoom < MIN_ZOOM → clamped
        double huge = 1e9;
        camera.fitAll(List.of(new Vector2D(0.0, 0.0), new Vector2D(huge, huge)));
        assertEquals(Camera.MIN_ZOOM, camera.getZoom(), DELTA);
    }

    @Test
    void fitAll_tinyBbox_zoomClampedToMax() {
        // Very small world bbox → would need zoom > MAX_ZOOM → clamped
        camera.fitAll(List.of(new Vector2D(0.0, 0.0), new Vector2D(1e-10, 1e-10)));
        assertEquals(Camera.MAX_ZOOM, camera.getZoom(), DELTA);
    }

    @Test
    void fitAll_threePoints_allVisibleWithinViewport() {
        List<Vector2D> pts = List.of(
                new Vector2D(-100.0, -50.0),
                new Vector2D( 100.0,  50.0),
                new Vector2D(   0.0,   0.0));
        camera.fitAll(pts);

        for (Vector2D world : pts) {
            Point screen = camera.worldToScreen(world);
            assertTrue(screen.x >= 0 && screen.x <= W,
                    "screen x must be within viewport after fitAll");
            assertTrue(screen.y >= 0 && screen.y <= H,
                    "screen y must be within viewport after fitAll");
        }
    }

    // -----------------------------------------------------------------------
    // Constructor — stores dimensions
    // -----------------------------------------------------------------------

    @Test
    void constructor_storesViewportDimensions() {
        Camera c = new Camera(640, 480);
        assertEquals(640, c.getViewportWidth());
        assertEquals(480, c.getViewportHeight());
    }

    // -----------------------------------------------------------------------
    // setViewportSize() — additional validation
    // -----------------------------------------------------------------------

    @Test
    void setViewportSize_zeroHeight_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> camera.setViewportSize(W, 0));
    }

    @Test
    void setViewportSize_negativeWidth_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> camera.setViewportSize(-1, H));
    }

    @Test
    void setViewportSize_doesNotChangeZoomOrOffset() {
        camera.pan(15.0, -10.0);
        camera.zoomAt(0.0, 0.0, 2.0);
        double zoomBefore = camera.getZoom();
        double oxBefore   = camera.getOffsetX();
        double oyBefore   = camera.getOffsetY();

        camera.setViewportSize(800, 600);

        assertEquals(zoomBefore, camera.getZoom(),    DELTA);
        assertEquals(oxBefore,   camera.getOffsetX(), DELTA);
        assertEquals(oyBefore,   camera.getOffsetY(), DELTA);
    }

    // -----------------------------------------------------------------------
    // zoomAt() — boundary and compound cases
    // -----------------------------------------------------------------------

    @Test
    void zoomAt_alreadyAtMaxZoom_zoomInFurther_staysAtMax() {
        camera.zoomAt(0.0, 0.0, Camera.MAX_ZOOM * 10);   // push to MAX
        camera.zoomAt(0.0, 0.0, 2.0);                    // try to go past MAX again
        assertEquals(Camera.MAX_ZOOM, camera.getZoom(), DELTA);
    }

    @Test
    void zoomAt_alreadyAtMinZoom_zoomOutFurther_staysAtMin() {
        camera.zoomAt(0.0, 0.0, 0.001);   // push to MIN
        camera.zoomAt(0.0, 0.0, 0.001);   // try to go past MIN again
        assertEquals(Camera.MIN_ZOOM, camera.getZoom(), DELTA);
    }

    @Test
    void zoomAt_pivotAtOriginWithZeroOffset_offsetsRemainZero() {
        // worldX=worldY=0 → offsets = pivot - 0*newZoom = 0
        camera.zoomAt(0.0, 0.0, 3.0);
        assertEquals(0.0, camera.getOffsetX(), DELTA);
        assertEquals(0.0, camera.getOffsetY(), DELTA);
    }

    @Test
    void zoomAt_afterPan_pivotWorldPointStillFixed() {
        // pan then zoom: the screen pivot must still map to the same world point
        camera.pan(50.0, -30.0);
        Vector2D worldBefore = camera.screenToWorld(new Point(200, 150));

        camera.zoomAt(200.0, 150.0, 2.0);

        Vector2D worldAfter = camera.screenToWorld(new Point(200, 150));
        assertEquals(worldBefore.getX(), worldAfter.getX(), DELTA);
        assertEquals(worldBefore.getY(), worldAfter.getY(), DELTA);
    }

    // -----------------------------------------------------------------------
    // worldToScreen() — negative and fractional coordinates
    // -----------------------------------------------------------------------

    @Test
    void worldToScreen_negativeWorldCoords_correctMapping() {
        // zoom=1, offset=0 → screen == world
        Point p = camera.worldToScreen(new Vector2D(-50.0, -30.0));
        assertEquals(-50, p.x);
        assertEquals(-30, p.y);
    }

    @Test
    void worldToScreen_fractionalCoord_roundedToNearestPixel() {
        // world (0.4, 0.6) → Math.round → (0, 1)
        Point p = camera.worldToScreen(new Vector2D(0.4, 0.6));
        assertEquals(0, p.x);
        assertEquals(1, p.y);
    }

    @Test
    void worldToScreen_calledTwice_returnsDifferentPointInstances() {
        Vector2D w = new Vector2D(10.0, 20.0);
        Point p1 = camera.worldToScreen(w);
        Point p2 = camera.worldToScreen(w);
        assertNotSame(p1, p2, "each call must return a new Point instance");
        assertEquals(p1, p2, "both calls must return equal Points");
    }

    // -----------------------------------------------------------------------
    // getTransform() — shear and combined pan+zoom
    // -----------------------------------------------------------------------

    @Test
    void getTransform_shearTermsAreZero() {
        camera.pan(10.0, 20.0);
        camera.zoomAt(100.0, 100.0, 1.5);
        AffineTransform at = camera.getTransform();
        assertEquals(0.0, at.getShearX(), DELTA, "shearX must be zero");
        assertEquals(0.0, at.getShearY(), DELTA, "shearY must be zero");
    }

    @Test
    void getTransform_afterPanAndZoom_allFieldsCorrect() {
        // pan(30,-20) → offset=(30,-20), zoom=1
        // zoomAt(100,50,2): worldX=(100-30)/1=70, worldY=(50-(-20))/1=70
        //   zoom=2; offsetX=100-70×2=−40; offsetY=50−70×2=−90
        camera.pan(30.0, -20.0);
        camera.zoomAt(100.0, 50.0, 2.0);
        AffineTransform at = camera.getTransform();

        assertEquals(2.0,   at.getScaleX(),     DELTA);
        assertEquals(2.0,   at.getScaleY(),     DELTA);
        assertEquals(-40.0, at.getTranslateX(), DELTA);
        assertEquals(-90.0, at.getTranslateY(), DELTA);
    }

    // -----------------------------------------------------------------------
    // fitAll() — height-constrained, negative coords, viewport change, idempotent
    // -----------------------------------------------------------------------

    @Test
    void fitAll_heightConstrainedBbox_exactZoom() {
        // bbox: x∈[0,100], y∈[0,150] → paddedW=120, paddedH=180
        // zoom = min(400/120, 300/180) = min(3.33…, 1.666…) = 300/180 = 5/3
        camera.fitAll(List.of(new Vector2D(0.0, 0.0), new Vector2D(100.0, 150.0)));

        double expectedZoom = 300.0 / 180.0;   // 5/3 — height side dominates
        assertEquals(expectedZoom, camera.getZoom(), 1e-9);
    }

    @Test
    void fitAll_negativeBboxCoords_worldCentreMapsToScreenCentre() {
        // bbox: x∈[−100,100], y∈[−50,50] → world centre = (0,0)
        camera.fitAll(List.of(new Vector2D(-100.0, -50.0), new Vector2D(100.0, 50.0)));

        Point p = camera.worldToScreen(new Vector2D(0.0, 0.0));
        assertEquals(W / 2, p.x, "world origin must map to screen centre x");
        assertEquals(H / 2, p.y, "world origin must map to screen centre y");
    }

    @Test
    void fitAll_afterSetViewportSize_usesNewDimensions() {
        // Resize to 200×200 then fit a single point — zoom must be 1
        // (paddedW=200, paddedH=200 → zoom=min(200/200,200/200)=1)
        camera.setViewportSize(200, 200);
        camera.fitAll(List.of(new Vector2D(50.0, 50.0)));

        assertEquals(1.0, camera.getZoom(), DELTA);
        // Point must map to the new screen centre (100,100)
        Point p = camera.worldToScreen(new Vector2D(50.0, 50.0));
        assertEquals(100, p.x);
        assertEquals(100, p.y);
    }

    @Test
    void fitAll_idempotent_calledTwiceSameResult() {
        List<Vector2D> pts = List.of(
                new Vector2D(10.0, 20.0), new Vector2D(110.0, 80.0));
        camera.fitAll(pts);
        double zoom1 = camera.getZoom();
        double ox1   = camera.getOffsetX();
        double oy1   = camera.getOffsetY();

        camera.fitAll(pts);

        assertEquals(zoom1, camera.getZoom(),    DELTA);
        assertEquals(ox1,   camera.getOffsetX(), DELTA);
        assertEquals(oy1,   camera.getOffsetY(), DELTA);
    }
}
