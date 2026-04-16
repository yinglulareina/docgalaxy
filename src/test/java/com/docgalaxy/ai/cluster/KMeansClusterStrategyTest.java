package com.docgalaxy.ai.cluster;

import com.docgalaxy.model.Vector2D;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link KMeansClusterStrategy}.
 *
 * <p>All tests use the package-private seed constructor so results are
 * fully deterministic.  Geometric assertions use delta=1e-9.
 */
class KMeansClusterStrategyTest {

    private static final double DELTA = 1e-9;
    private static final long   SEED  = 42L;

    // -----------------------------------------------------------------------
    // Constructor guards
    // -----------------------------------------------------------------------

    @Test
    void constructor_kLessThanOne_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new KMeansClusterStrategy(0));
    }

    @Test
    void constructor_negativeK_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new KMeansClusterStrategy(-3));
    }

    @Test
    void constructor_kEqualsOne_doesNotThrow() {
        assertDoesNotThrow(() -> new KMeansClusterStrategy(1));
    }

    // -----------------------------------------------------------------------
    // cluster() — argument validation
    // -----------------------------------------------------------------------

    @Test
    void cluster_nullPoints_throwsIllegalArgumentException() {
        KMeansClusterStrategy s = new KMeansClusterStrategy(2, SEED);
        assertThrows(IllegalArgumentException.class,
                () -> s.cluster(null, List.of("a")));
    }

    @Test
    void cluster_nullNoteIds_throwsIllegalArgumentException() {
        KMeansClusterStrategy s = new KMeansClusterStrategy(2, SEED);
        assertThrows(IllegalArgumentException.class,
                () -> s.cluster(List.of(new Vector2D(0, 0)), null));
    }

    @Test
    void cluster_differentSizeLists_throwsIllegalArgumentException() {
        KMeansClusterStrategy s = new KMeansClusterStrategy(2, SEED);
        assertThrows(IllegalArgumentException.class, () ->
                s.cluster(List.of(new Vector2D(0, 0), new Vector2D(1, 1)),
                          List.of("only-one")));
    }

    // -----------------------------------------------------------------------
    // cluster() — edge cases
    // -----------------------------------------------------------------------

    @Test
    void cluster_emptyInput_returnsEmptyList() {
        KMeansClusterStrategy s = new KMeansClusterStrategy(3, SEED);
        List<Cluster> result = s.cluster(List.of(), List.of());
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void cluster_singlePoint_returnsOneCluster() {
        KMeansClusterStrategy s = new KMeansClusterStrategy(3, SEED);
        List<Cluster> result = s.cluster(
                List.of(new Vector2D(1, 2)),
                List.of("n1"));

        assertEquals(1, result.size());
        assertEquals("n1", result.get(0).getMemberNoteIds().get(0));
    }

    @Test
    void cluster_kGreaterThanPoints_clampsToPointCount() {
        // 2 points, k=10 → at most 2 clusters
        List<Vector2D>  pts = List.of(new Vector2D(0, 0), new Vector2D(100, 100));
        List<String> ids = List.of("a", "b");
        KMeansClusterStrategy s = new KMeansClusterStrategy(10, SEED);

        List<Cluster> result = s.cluster(pts, ids);

        assertTrue(result.size() <= 2);
        assertEquals(2, totalMembers(result));
    }

    @Test
    void cluster_kEqualsOne_allPointsInOneCluster() {
        KMeansClusterStrategy s = new KMeansClusterStrategy(1, SEED);
        List<Vector2D>  pts = List.of(
                new Vector2D(0, 0), new Vector2D(5, 5), new Vector2D(-3, 2));
        List<String> ids = List.of("a", "b", "c");

        List<Cluster> result = s.cluster(pts, ids);

        assertEquals(1, result.size());
        assertEquals(3, result.get(0).getMemberNoteIds().size());
    }

    // -----------------------------------------------------------------------
    // cluster() — happy path / geometry
    // -----------------------------------------------------------------------

    /**
     * Two clearly separated groups:
     *   group A near (0,0): a1=(0,0), a2=(1,0), a3=(0,1)
     *   group B near (100,100): b1=(100,100), b2=(101,100), b3=(100,101)
     * k=2 must separate them perfectly.
     */
    @Test
    void cluster_twoWellSeparatedGroups_assignedCorrectly() {
        List<Vector2D> pts = List.of(
                new Vector2D(0,   0),   new Vector2D(1,   0),   new Vector2D(0,   1),
                new Vector2D(100, 100), new Vector2D(101, 100), new Vector2D(100, 101));
        List<String> ids = List.of("a1","a2","a3","b1","b2","b3");
        KMeansClusterStrategy s = new KMeansClusterStrategy(2, SEED);

        List<Cluster> result = s.cluster(pts, ids);

        assertEquals(2, result.size());
        Set<String> g1 = new HashSet<>(result.get(0).getMemberNoteIds());
        Set<String> g2 = new HashSet<>(result.get(1).getMemberNoteIds());

        // Each group must be entirely in one cluster
        assertTrue(
                (g1.containsAll(Set.of("a1","a2","a3")) && g2.containsAll(Set.of("b1","b2","b3")))
             || (g2.containsAll(Set.of("a1","a2","a3")) && g1.containsAll(Set.of("b1","b2","b3"))),
                "The two groups must be separated into different clusters");
    }

    @Test
    void cluster_twoGroups_centroidIsNearGroupMean() {
        // Group A centre = (1/3, 1/3), Group B centre = (100.33, 100.33)
        List<Vector2D> pts = List.of(
                new Vector2D(0, 0), new Vector2D(1, 0), new Vector2D(0, 1),
                new Vector2D(100, 100), new Vector2D(101, 100), new Vector2D(100, 101));
        List<String> ids = List.of("a1","a2","a3","b1","b2","b3");
        KMeansClusterStrategy s = new KMeansClusterStrategy(2, SEED);

        List<Cluster> result = s.cluster(pts, ids);

        // Find the cluster for group A (centroid near origin)
        Cluster clusterA = result.stream()
                .filter(c -> c.getCentroid().distanceTo(new Vector2D(0, 0)) < 5)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No cluster near origin"));

        assertEquals(1.0 / 3, clusterA.getCentroid().getX(), 1e-6);
        assertEquals(1.0 / 3, clusterA.getCentroid().getY(), 1e-6);
    }

    @Test
    void cluster_kEqualsPointCount_eachPointItsOwnCluster() {
        List<Vector2D> pts = List.of(
                new Vector2D(0, 0), new Vector2D(50, 50), new Vector2D(100, 100));
        List<String> ids = List.of("x","y","z");
        KMeansClusterStrategy s = new KMeansClusterStrategy(3, SEED);

        List<Cluster> result = s.cluster(pts, ids);

        assertEquals(3, result.size());
        result.forEach(c -> assertEquals(1, c.getMemberNoteIds().size()));
    }

    // -----------------------------------------------------------------------
    // Partition properties
    // -----------------------------------------------------------------------

    @Test
    void cluster_everyPointIsAssigned_noLosses() {
        List<Vector2D> pts = randomPoints(20, SEED);
        List<String>   ids = noteIds(20);
        KMeansClusterStrategy s = new KMeansClusterStrategy(4, SEED);

        List<Cluster> result = s.cluster(pts, ids);

        assertEquals(20, totalMembers(result), "All 20 points must appear in some cluster");
    }

    @Test
    void cluster_noNoteIdAppearsInTwoClusters() {
        List<Vector2D> pts = randomPoints(15, SEED);
        List<String>   ids = noteIds(15);
        KMeansClusterStrategy s = new KMeansClusterStrategy(3, SEED);

        List<Cluster> result = s.cluster(pts, ids);

        Set<String> seen = new HashSet<>();
        for (Cluster c : result) {
            for (String id : c.getMemberNoteIds()) {
                assertTrue(seen.add(id),
                        "Note id '" + id + "' appears in more than one cluster");
            }
        }
    }

    @Test
    void cluster_allOriginalNoteIdsPresent() {
        List<String>   ids = noteIds(12);
        List<Vector2D> pts = randomPoints(12, SEED);
        KMeansClusterStrategy s = new KMeansClusterStrategy(3, SEED);

        List<Cluster> result = s.cluster(pts, ids);

        Set<String> found = new HashSet<>();
        result.forEach(c -> found.addAll(c.getMemberNoteIds()));
        assertEquals(new HashSet<>(ids), found);
    }

    // -----------------------------------------------------------------------
    // Result contract
    // -----------------------------------------------------------------------

    @Test
    void cluster_dendrogramIsNull() {
        List<Cluster> result = new KMeansClusterStrategy(2, SEED).cluster(
                List.of(new Vector2D(0, 0), new Vector2D(10, 10)),
                List.of("a", "b"));

        result.forEach(c -> assertNull(c.getDendrogram(),
                "dendrogram must be null for K-Means"));
    }

    @Test
    void cluster_returnedListIsUnmodifiable() {
        List<Cluster> result = new KMeansClusterStrategy(1, SEED).cluster(
                List.of(new Vector2D(0, 0)),
                List.of("n1"));

        assertThrows(UnsupportedOperationException.class,
                () -> result.add(new Cluster(Vector2D.ZERO, List.of(), null)));
    }

    @Test
    void cluster_memberListPerClusterIsUnmodifiable() {
        List<Cluster> result = new KMeansClusterStrategy(1, SEED).cluster(
                List.of(new Vector2D(0, 0)),
                List.of("n1"));

        assertThrows(UnsupportedOperationException.class,
                () -> result.get(0).getMemberNoteIds().add("hack"));
    }

    @Test
    void cluster_centroidIsVector2D_notNull() {
        List<Cluster> result = new KMeansClusterStrategy(2, SEED).cluster(
                List.of(new Vector2D(0, 0), new Vector2D(10, 10)),
                List.of("a", "b"));

        result.forEach(c -> assertNotNull(c.getCentroid()));
    }

    // -----------------------------------------------------------------------
    // Determinism
    // -----------------------------------------------------------------------

    @Test
    void cluster_sameSeed_producesIdenticalResults() {
        List<Vector2D> pts = randomPoints(10, 7L);
        List<String>   ids = noteIds(10);

        List<Cluster> r1 = new KMeansClusterStrategy(3, SEED).cluster(pts, ids);
        List<Cluster> r2 = new KMeansClusterStrategy(3, SEED).cluster(pts, ids);

        assertEquals(r1.size(), r2.size());
        for (int i = 0; i < r1.size(); i++) {
            assertEquals(r1.get(i).getMemberNoteIds(), r2.get(i).getMemberNoteIds());
        }
    }

    // -----------------------------------------------------------------------
    // Convergence / stability
    // -----------------------------------------------------------------------

    @Test
    void cluster_duplicatePoints_doesNotThrow() {
        List<Vector2D> pts = List.of(
                new Vector2D(1, 1), new Vector2D(1, 1), new Vector2D(1, 1));
        List<String>   ids = List.of("x","y","z");

        assertDoesNotThrow(() ->
                new KMeansClusterStrategy(2, SEED).cluster(pts, ids));
    }

    @Test
    void cluster_collinearPoints_doesNotThrow() {
        List<Vector2D> pts = new ArrayList<>();
        List<String>   ids = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            pts.add(new Vector2D(i, 0));
            ids.add("n" + i);
        }

        assertDoesNotThrow(() ->
                new KMeansClusterStrategy(3, SEED).cluster(pts, ids));
    }

    @Test
    void cluster_largeDataset_completesAndPartitionsCorrectly() {
        int n = 200;
        List<Vector2D> pts = randomPoints(n, 99L);
        List<String>   ids = noteIds(n);
        KMeansClusterStrategy s = new KMeansClusterStrategy(5, SEED);

        List<Cluster> result = s.cluster(pts, ids);

        assertTrue(result.size() <= 5);
        assertEquals(n, totalMembers(result));
    }

    // -----------------------------------------------------------------------
    // Exact centroid values
    // -----------------------------------------------------------------------

    @Test
    void cluster_k1_centroidIsExactMeanOfAllPoints() {
        // mean of (0,0),(6,0),(3,6) = (3, 2)
        List<Vector2D> pts = List.of(
                new Vector2D(0, 0), new Vector2D(6, 0), new Vector2D(3, 6));
        List<String> ids = List.of("a","b","c");

        List<Cluster> result = new KMeansClusterStrategy(1, SEED).cluster(pts, ids);

        assertEquals(1, result.size());
        assertEquals(3.0, result.get(0).getCentroid().getX(), DELTA);
        assertEquals(2.0, result.get(0).getCentroid().getY(), DELTA);
    }

    @Test
    void cluster_singlePoint_centroidCoordsEqualThatPoint() {
        Vector2D p = new Vector2D(7.5, -3.2);
        List<Cluster> result = new KMeansClusterStrategy(1, SEED)
                .cluster(List.of(p), List.of("solo"));

        assertEquals(p.getX(), result.get(0).getCentroid().getX(), DELTA);
        assertEquals(p.getY(), result.get(0).getCentroid().getY(), DELTA);
    }

    @Test
    void cluster_k2_singleMemberCluster_centroidEqualsTheMember() {
        // Points so far apart that k=2 puts one point alone
        List<Vector2D> pts = List.of(new Vector2D(0, 0), new Vector2D(1000, 0));
        List<String>   ids = List.of("near","far");
        List<Cluster> result = new KMeansClusterStrategy(2, SEED).cluster(pts, ids);

        assertEquals(2, result.size());
        // Find the cluster that contains "far"
        Cluster farCluster = result.stream()
                .filter(c -> c.getMemberNoteIds().contains("far"))
                .findFirst().orElseThrow();

        assertEquals(1000.0, farCluster.getCentroid().getX(), DELTA);
        assertEquals(0.0,    farCluster.getCentroid().getY(), DELTA);
    }

    @Test
    void cluster_k1_asymmetricMean_exactValue() {
        // mean of (1,4),(3,2) = (2, 3)
        List<Vector2D> pts = List.of(new Vector2D(1, 4), new Vector2D(3, 2));
        List<Cluster> result = new KMeansClusterStrategy(1, SEED)
                .cluster(pts, List.of("p","q"));

        assertEquals(2.0, result.get(0).getCentroid().getX(), DELTA);
        assertEquals(3.0, result.get(0).getCentroid().getY(), DELTA);
    }

    // -----------------------------------------------------------------------
    // Result-size invariant
    // -----------------------------------------------------------------------

    @Test
    void cluster_resultSize_neverExceedsK() {
        List<Vector2D> pts = randomPoints(30, SEED);
        List<String>   ids = noteIds(30);

        for (int k = 1; k <= 10; k++) {
            List<Cluster> result = new KMeansClusterStrategy(k, SEED).cluster(pts, ids);
            assertTrue(result.size() <= k,
                    "cluster count " + result.size() + " exceeds k=" + k);
        }
    }

    @Test
    void cluster_clusterSizeSumEqualsInputSize() {
        List<Vector2D> pts = randomPoints(25, SEED);
        List<String>   ids = noteIds(25);
        List<Cluster> result = new KMeansClusterStrategy(5, SEED).cluster(pts, ids);

        int total = result.stream().mapToInt(c -> c.getMemberNoteIds().size()).sum();
        assertEquals(25, total);
    }

    // -----------------------------------------------------------------------
    // Side-effect safety
    // -----------------------------------------------------------------------

    @Test
    void cluster_doesNotModifyInputPointsList() {
        List<Vector2D> pts = new ArrayList<>(List.of(
                new Vector2D(0, 0), new Vector2D(5, 5), new Vector2D(10, 10)));
        List<Vector2D> ptsCopy = new ArrayList<>(pts);

        new KMeansClusterStrategy(2, SEED).cluster(pts, noteIds(3));

        assertEquals(ptsCopy.size(), pts.size());
        for (int i = 0; i < ptsCopy.size(); i++) {
            assertEquals(ptsCopy.get(i).getX(), pts.get(i).getX(), DELTA);
            assertEquals(ptsCopy.get(i).getY(), pts.get(i).getY(), DELTA);
        }
    }

    @Test
    void cluster_doesNotModifyInputNoteIdsList() {
        List<String> ids = new ArrayList<>(List.of("x","y","z"));
        List<String> idsCopy = new ArrayList<>(ids);

        new KMeansClusterStrategy(2, SEED).cluster(
                List.of(new Vector2D(0,0), new Vector2D(5,5), new Vector2D(10,10)), ids);

        assertEquals(idsCopy, ids);
    }

    // -----------------------------------------------------------------------
    // Statelessness
    // -----------------------------------------------------------------------

    @Test
    void cluster_calledTwiceOnSameInstance_producesIdenticalResults() {
        List<Vector2D> pts = randomPoints(12, 3L);
        List<String>   ids = noteIds(12);
        KMeansClusterStrategy s = new KMeansClusterStrategy(3, SEED);

        List<Cluster> r1 = s.cluster(pts, ids);
        List<Cluster> r2 = s.cluster(pts, ids);

        assertEquals(r1.size(), r2.size());
        for (int i = 0; i < r1.size(); i++) {
            assertEquals(r1.get(i).getMemberNoteIds(), r2.get(i).getMemberNoteIds());
            assertEquals(r1.get(i).getCentroid().getX(),
                         r2.get(i).getCentroid().getX(), DELTA);
        }
    }

    @Test
    void cluster_differentCallsWithDifferentData_areIndependent() {
        KMeansClusterStrategy s = new KMeansClusterStrategy(2, SEED);

        // First call: two groups far apart
        List<Cluster> r1 = s.cluster(
                List.of(new Vector2D(0,0), new Vector2D(100,100)),
                List.of("a","b"));

        // Second call: completely different points
        List<Cluster> r2 = s.cluster(
                List.of(new Vector2D(50,50), new Vector2D(51,51)),
                List.of("c","d"));

        // r1 must not be polluted by r2's data
        Set<String> r1Ids = new HashSet<>();
        r1.forEach(c -> r1Ids.addAll(c.getMemberNoteIds()));
        assertFalse(r1Ids.contains("c") || r1Ids.contains("d"),
                "First result must not contain IDs from second call");
    }

    // -----------------------------------------------------------------------
    // Three-group geometry
    // -----------------------------------------------------------------------

    @Test
    void cluster_threeWellSeparatedGroups_k3_eachGroupInOwnCluster() {
        // Group X ≈ (0,0), group Y ≈ (500,0), group Z ≈ (250,500)
        List<Vector2D> pts = List.of(
                new Vector2D(0,0),   new Vector2D(1,0),   new Vector2D(0,1),
                new Vector2D(500,0), new Vector2D(501,0), new Vector2D(500,1),
                new Vector2D(250,500),new Vector2D(251,500),new Vector2D(250,501));
        List<String> ids = List.of("x1","x2","x3","y1","y2","y3","z1","z2","z3");

        List<Cluster> result = new KMeansClusterStrategy(3, SEED).cluster(pts, ids);

        assertEquals(3, result.size());
        assertEquals(9, totalMembers(result));

        // Verify each known group is entirely within one cluster
        Set<String> groupX = Set.of("x1","x2","x3");
        Set<String> groupY = Set.of("y1","y2","y3");
        Set<String> groupZ = Set.of("z1","z2","z3");
        List<Set<String>> clusterSets = result.stream()
                .<Set<String>>map(c -> new HashSet<>(c.getMemberNoteIds()))
                .toList();

        assertTrue(clusterSets.stream().anyMatch(s -> s.containsAll(groupX)), "group X not in one cluster");
        assertTrue(clusterSets.stream().anyMatch(s -> s.containsAll(groupY)), "group Y not in one cluster");
        assertTrue(clusterSets.stream().anyMatch(s -> s.containsAll(groupZ)), "group Z not in one cluster");
    }

    // -----------------------------------------------------------------------
    // Asymmetric group sizes
    // -----------------------------------------------------------------------

    @Test
    void cluster_asymmetricGroupSizes_largGroupAndSingleOutlier() {
        // 8 points near origin, 1 outlier far away
        List<Vector2D> pts = new ArrayList<>();
        List<String>   ids = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            pts.add(new Vector2D(i * 0.1, 0));
            ids.add("near" + i);
        }
        pts.add(new Vector2D(1000, 0));
        ids.add("outlier");

        List<Cluster> result = new KMeansClusterStrategy(2, SEED).cluster(pts, ids);

        assertEquals(2, result.size());
        Cluster outlierCluster = result.stream()
                .filter(c -> c.getMemberNoteIds().contains("outlier"))
                .findFirst().orElseThrow(() -> new AssertionError("outlier not found"));

        assertEquals(1, outlierCluster.getMemberNoteIds().size(),
                "Outlier must be alone in its cluster");
        assertEquals(8, totalMembers(result) - 1,
                "The other cluster must contain all 8 near points");
    }

    // -----------------------------------------------------------------------
    // Degenerate: two identical points with k=2
    // -----------------------------------------------------------------------

    @Test
    void cluster_twoIdenticalPoints_k2_doesNotThrowAndAllPointsAssigned() {
        List<Vector2D> pts = List.of(new Vector2D(3, 4), new Vector2D(3, 4));
        List<String>   ids = List.of("twin1","twin2");

        List<Cluster> result = assertDoesNotThrow(() ->
                new KMeansClusterStrategy(2, SEED).cluster(pts, ids));

        assertEquals(2, totalMembers(result),
                "Both identical points must be assigned (possibly to the same cluster)");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static int totalMembers(List<Cluster> clusters) {
        return clusters.stream().mapToInt(c -> c.getMemberNoteIds().size()).sum();
    }

    private static List<Vector2D> randomPoints(int n, long seed) {
        java.util.Random rng = new java.util.Random(seed);
        List<Vector2D> pts = new ArrayList<>(n);
        for (int i = 0; i < n; i++) pts.add(new Vector2D(rng.nextDouble() * 100, rng.nextDouble() * 100));
        return pts;
    }

    private static List<String> noteIds(int n) {
        List<String> ids = new ArrayList<>(n);
        for (int i = 0; i < n; i++) ids.add("note-" + i);
        return ids;
    }
}
