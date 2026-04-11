package com.docgalaxy.ai.cluster;

import com.docgalaxy.model.DendrogramNode;
import com.docgalaxy.model.Vector2D;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link HybridClusterStrategy} and its inner HAC logic.
 *
 * <p>All tests use the package-private seed constructor for determinism.
 * Geometric assertions use delta=1e-9.
 */
class HybridClusterStrategyTest {

    private static final double DELTA = 1e-9;
    private static final long   SEED  = 42L;

    // -----------------------------------------------------------------------
    // cluster() — argument validation
    // -----------------------------------------------------------------------

    @Test
    void cluster_nullPoints_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new HybridClusterStrategy(SEED).cluster(null, List.of("a")));
    }

    @Test
    void cluster_nullNoteIds_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new HybridClusterStrategy(SEED).cluster(
                        List.of(new Vector2D(0, 0)), null));
    }

    @Test
    void cluster_mismatchedListSizes_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new HybridClusterStrategy(SEED).cluster(
                        List.of(new Vector2D(0, 0), new Vector2D(1, 1)),
                        List.of("only-one")));
    }

    // -----------------------------------------------------------------------
    // cluster() — edge cases
    // -----------------------------------------------------------------------

    @Test
    void cluster_emptyInput_returnsEmptyList() {
        List<Cluster> result = new HybridClusterStrategy(SEED).cluster(List.of(), List.of());
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void cluster_singlePoint_returnsOneCluster() {
        List<Cluster> result = new HybridClusterStrategy(SEED).cluster(
                List.of(new Vector2D(3, 7)), List.of("solo"));

        assertEquals(1, result.size());
        assertEquals(List.of("solo"), result.get(0).getMemberNoteIds());
    }

    @Test
    void cluster_singlePoint_dendrogramIsLeafWithCorrectId() {
        List<Cluster> result = new HybridClusterStrategy(SEED).cluster(
                List.of(new Vector2D(1, 2)), List.of("leaf-node"));

        DendrogramNode d = result.get(0).getDendrogram();
        assertNotNull(d);
        assertTrue(d.isLeaf());
        assertEquals("leaf-node", d.getNoteId());
    }

    @Test
    void cluster_twoPoints_dendrogramHasTwoLeaves() {
        List<Cluster> result = new HybridClusterStrategy(SEED).cluster(
                List.of(new Vector2D(0, 0), new Vector2D(1, 0)),
                List.of("a", "b"));

        // Both points end up in one cluster (k clamps to 2 then K-Means may merge)
        // Find the cluster containing both
        Cluster both = result.stream()
                .filter(c -> c.getMemberNoteIds().size() == 2)
                .findFirst().orElse(null);

        if (both != null) {
            DendrogramNode root = both.getDendrogram();
            assertNotNull(root);
            assertFalse(root.isLeaf(), "root of 2-member cluster must be an internal node");
            assertTrue(root.getLeft().isLeaf());
            assertTrue(root.getRight().isLeaf());
            // Both leaf IDs must be present
            Set<String> leafIds = Set.of(root.getLeft().getNoteId(),
                                          root.getRight().getNoteId());
            assertEquals(Set.of("a", "b"), leafIds);
        }
        // If k-means put them in separate clusters, each has a leaf dendrogram — that's also valid.
    }

    // -----------------------------------------------------------------------
    // Partition invariants
    // -----------------------------------------------------------------------

    @Test
    void cluster_everyNoteIdAppearInExactlyOneCluster() {
        List<Vector2D> pts = randomPoints(20, SEED);
        List<String>   ids = noteIds(20);
        List<Cluster> result = new HybridClusterStrategy(SEED).cluster(pts, ids);

        Set<String> seen = new HashSet<>();
        for (Cluster c : result) {
            for (String id : c.getMemberNoteIds()) {
                assertTrue(seen.add(id), "duplicate id: " + id);
            }
        }
        assertEquals(new HashSet<>(ids), seen);
    }

    @Test
    void cluster_totalMemberCount_equalsInputSize() {
        List<Vector2D> pts = randomPoints(15, SEED);
        List<String>   ids = noteIds(15);
        List<Cluster> result = new HybridClusterStrategy(SEED).cluster(pts, ids);

        int total = result.stream().mapToInt(c -> c.getMemberNoteIds().size()).sum();
        assertEquals(15, total);
    }

    // -----------------------------------------------------------------------
    // Dendrogram structure invariants
    // -----------------------------------------------------------------------

    @Test
    void cluster_everyClusterHasNonNullDendrogram() {
        List<Cluster> result = new HybridClusterStrategy(SEED).cluster(
                randomPoints(12, 1L), noteIds(12));

        result.forEach(c -> assertNotNull(c.getDendrogram(),
                "dendrogram must not be null in HybridClusterStrategy"));
    }

    @Test
    void cluster_dendrogramLeaves_containExactlyTheClusterMemberIds() {
        List<Vector2D> pts = randomPoints(16, SEED);
        List<String>   ids = noteIds(16);
        List<Cluster> result = new HybridClusterStrategy(SEED).cluster(pts, ids);

        for (Cluster c : result) {
            Set<String> leaves  = collectLeaves(c.getDendrogram());
            Set<String> members = new HashSet<>(c.getMemberNoteIds());
            assertEquals(members, leaves,
                    "dendrogram leaves must match cluster memberNoteIds");
        }
    }

    @Test
    void cluster_singleMemberCluster_dendrogramIsLeaf() {
        // With n=3, K=max(3,ceil(sqrt(3)))=3, so 3 clusters of 1 each
        List<Vector2D> pts = List.of(
                new Vector2D(0,   0),
                new Vector2D(500, 0),
                new Vector2D(0,   500));
        List<String> ids = List.of("a","b","c");
        List<Cluster> result = new HybridClusterStrategy(SEED).cluster(pts, ids);

        for (Cluster c : result) {
            if (c.getMemberNoteIds().size() == 1) {
                assertTrue(c.getDendrogram().isLeaf(),
                        "single-member cluster must have a leaf dendrogram");
                assertEquals(c.getMemberNoteIds().get(0), c.getDendrogram().getNoteId());
            }
        }
    }

    @Test
    void cluster_multiMemberCluster_dendrogramRootIsInternalNode() {
        // 9 points in one tight group → k=3 puts ~3 each, so at least one multi-member cluster
        List<Vector2D> pts = new ArrayList<>();
        List<String>   ids = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            pts.add(new Vector2D(i * 0.01, 0)); // all near origin
            ids.add("n" + i);
        }
        List<Cluster> result = new HybridClusterStrategy(SEED).cluster(pts, ids);

        for (Cluster c : result) {
            if (c.getMemberNoteIds().size() > 1) {
                assertFalse(c.getDendrogram().isLeaf(),
                        "multi-member cluster must have an internal-node dendrogram root");
            }
        }
    }

    @Test
    void cluster_dendrogramLeafCount_equalsClusterSize() {
        List<Vector2D> pts = randomPoints(18, SEED);
        List<String>   ids = noteIds(18);
        List<Cluster> result = new HybridClusterStrategy(SEED).cluster(pts, ids);

        for (Cluster c : result) {
            assertEquals(c.getMemberNoteIds().size(), leafCount(c.getDendrogram()),
                    "leaf count in dendrogram must equal cluster member count");
        }
    }

    @Test
    void cluster_internalNodes_mergeDistanceIsNonNegative() {
        List<Vector2D> pts = randomPoints(12, SEED);
        List<String>   ids = noteIds(12);
        List<Cluster> result = new HybridClusterStrategy(SEED).cluster(pts, ids);

        for (Cluster c : result) {
            verifyMergeDistancesNonNegative(c.getDendrogram());
        }
    }

    @Test
    void cluster_internalNode_mergeDistance_isDistanceBetweenChildCentroids() {
        // Two-point cluster: merge distance must equal distance between the two points
        List<Vector2D> pts = List.of(new Vector2D(0, 0), new Vector2D(3, 4));
        List<String>   ids = List.of("origin","farPoint");
        List<Cluster> result = new HybridClusterStrategy(SEED).cluster(pts, ids);

        Cluster both = result.stream()
                .filter(c -> c.getMemberNoteIds().size() == 2)
                .findFirst().orElse(null);

        if (both != null) {
            DendrogramNode root = both.getDendrogram();
            assertFalse(root.isLeaf());
            assertEquals(5.0, root.getMergeDistance(), DELTA); // 3-4-5 triangle
        }
    }

    // -----------------------------------------------------------------------
    // K formula: K = max(3, ceil(sqrt(n)))
    // -----------------------------------------------------------------------

    @Test
    void cluster_n9_atMost3Clusters() {
        // K = max(3, ceil(sqrt(9))) = max(3,3) = 3 → at most 3 clusters
        List<Vector2D> pts = randomPoints(9, SEED);
        List<String>   ids = noteIds(9);
        List<Cluster> result = new HybridClusterStrategy(SEED).cluster(pts, ids);

        assertTrue(result.size() <= 3, "n=9 must produce at most 3 clusters");
    }

    @Test
    void cluster_n16_atMost4Clusters() {
        // K = max(3, ceil(sqrt(16))) = max(3,4) = 4
        List<Vector2D> pts = randomPoints(16, SEED);
        List<String>   ids = noteIds(16);
        List<Cluster> result = new HybridClusterStrategy(SEED).cluster(pts, ids);

        assertTrue(result.size() <= 4, "n=16 must produce at most 4 clusters");
    }

    @Test
    void cluster_nLessThan9_kClampedTo3OrLess() {
        // n=4: K=max(3,2)=3 → KMeans clamps to min(3,4)=3 → at most 3 clusters
        List<Vector2D> pts = randomPoints(4, SEED);
        List<String>   ids = noteIds(4);
        List<Cluster> result = new HybridClusterStrategy(SEED).cluster(pts, ids);

        assertTrue(result.size() <= 3);
        assertEquals(4, result.stream().mapToInt(c -> c.getMemberNoteIds().size()).sum());
    }

    // -----------------------------------------------------------------------
    // Result contract
    // -----------------------------------------------------------------------

    @Test
    void cluster_returnedListIsUnmodifiable() {
        List<Cluster> result = new HybridClusterStrategy(SEED).cluster(
                List.of(new Vector2D(0, 0)), List.of("x"));

        assertThrows(UnsupportedOperationException.class,
                () -> result.add(new Cluster(Vector2D.ZERO, List.of(), null)));
    }

    @Test
    void cluster_memberListPerClusterIsUnmodifiable() {
        List<Cluster> result = new HybridClusterStrategy(SEED).cluster(
                List.of(new Vector2D(0, 0)), List.of("x"));

        assertThrows(UnsupportedOperationException.class,
                () -> result.get(0).getMemberNoteIds().add("hack"));
    }

    @Test
    void cluster_centroid_isNotNull() {
        List<Cluster> result = new HybridClusterStrategy(SEED).cluster(
                randomPoints(6, SEED), noteIds(6));

        result.forEach(c -> assertNotNull(c.getCentroid()));
    }

    // -----------------------------------------------------------------------
    // buildDendrogram() — package-visible unit tests
    // -----------------------------------------------------------------------

    @Test
    void buildDendrogram_singleId_returnsLeaf() {
        DendrogramNode node = HybridClusterStrategy.buildDendrogram(
                List.of("only"), List.of(new Vector2D(1, 2)));

        assertTrue(node.isLeaf());
        assertEquals("only", node.getNoteId());
    }

    @Test
    void buildDendrogram_twoIds_returnsInternalNodeWithBothLeaves() {
        DendrogramNode root = HybridClusterStrategy.buildDendrogram(
                List.of("a","b"),
                List.of(new Vector2D(0, 0), new Vector2D(3, 4)));

        assertFalse(root.isLeaf());
        assertTrue(root.getLeft().isLeaf());
        assertTrue(root.getRight().isLeaf());
        assertEquals(Set.of("a","b"),
                Set.of(root.getLeft().getNoteId(), root.getRight().getNoteId()));
        assertEquals(5.0, root.getMergeDistance(), DELTA);
    }

    @Test
    void buildDendrogram_threeIds_rootHasCorrectLeafCount() {
        DendrogramNode root = HybridClusterStrategy.buildDendrogram(
                List.of("x","y","z"),
                List.of(new Vector2D(0,0), new Vector2D(1,0), new Vector2D(10,0)));

        assertEquals(3, leafCount(root));
        assertEquals(Set.of("x","y","z"), collectLeaves(root));
    }

    @Test
    void buildDendrogram_mergesClosestPairFirst() {
        // x and y are adjacent (dist=1); z is far (dist=10 from x, 9 from y)
        // → first merge is x+y at distance 1.0
        DendrogramNode root = HybridClusterStrategy.buildDendrogram(
                List.of("x","y","z"),
                List.of(new Vector2D(0,0), new Vector2D(1,0), new Vector2D(10,0)));

        // The child with mergeDistance ≈ 1.0 is the x-y merge
        DendrogramNode firstMerge = findInternalNodeWithSmallestDistance(root);
        assertEquals(1.0, firstMerge.getMergeDistance(), DELTA);
        assertEquals(Set.of("x","y"),
                Set.of(firstMerge.getLeft().getNoteId(),
                       firstMerge.getRight().getNoteId()));
    }

    @Test
    void buildDendrogram_emptyIds_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> HybridClusterStrategy.buildDendrogram(List.of(), List.of()));
    }

    @Test
    void buildDendrogram_largeGroup_allLeavesPresent() {
        int m = 20;
        List<String>   ids = noteIds(m);
        List<Vector2D> pts = randomPoints(m, SEED);

        DendrogramNode root = HybridClusterStrategy.buildDendrogram(ids, pts);
        assertEquals(new HashSet<>(ids), collectLeaves(root));
        assertEquals(m, leafCount(root));
    }

    // -----------------------------------------------------------------------
    // Side-effect safety
    // -----------------------------------------------------------------------

    @Test
    void cluster_doesNotModifyInputPointsList() {
        List<Vector2D> pts = new ArrayList<>(List.of(
                new Vector2D(0, 0), new Vector2D(5, 5), new Vector2D(10, 10)));
        List<Vector2D> snapshot = new ArrayList<>(pts);

        new HybridClusterStrategy(SEED).cluster(pts, new ArrayList<>(List.of("a","b","c")));

        assertEquals(snapshot.size(), pts.size());
        for (int i = 0; i < snapshot.size(); i++) {
            assertEquals(snapshot.get(i).getX(), pts.get(i).getX(), DELTA);
            assertEquals(snapshot.get(i).getY(), pts.get(i).getY(), DELTA);
        }
    }

    @Test
    void cluster_doesNotModifyInputNoteIdsList() {
        List<String> ids = new ArrayList<>(List.of("x","y","z"));
        List<String> snapshot = new ArrayList<>(ids);

        new HybridClusterStrategy(SEED).cluster(
                List.of(new Vector2D(0,0), new Vector2D(5,5), new Vector2D(10,10)), ids);

        assertEquals(snapshot, ids);
    }

    // -----------------------------------------------------------------------
    // Determinism
    // -----------------------------------------------------------------------

    @Test
    void cluster_sameSeed_producesIdenticalMemberSets() {
        List<Vector2D> pts = randomPoints(18, 7L);
        List<String>   ids = noteIds(18);
        HybridClusterStrategy s = new HybridClusterStrategy(SEED);

        List<Cluster> r1 = s.cluster(pts, ids);
        List<Cluster> r2 = s.cluster(pts, ids);

        assertEquals(r1.size(), r2.size());
        for (int i = 0; i < r1.size(); i++) {
            assertEquals(new HashSet<>(r1.get(i).getMemberNoteIds()),
                         new HashSet<>(r2.get(i).getMemberNoteIds()));
        }
    }

    @Test
    void cluster_sameSeed_producesIdenticalCentroids() {
        List<Vector2D> pts = randomPoints(12, 5L);
        List<String>   ids = noteIds(12);

        List<Cluster> r1 = new HybridClusterStrategy(SEED).cluster(pts, ids);
        List<Cluster> r2 = new HybridClusterStrategy(SEED).cluster(pts, ids);

        for (int i = 0; i < r1.size(); i++) {
            assertEquals(r1.get(i).getCentroid().getX(),
                         r2.get(i).getCentroid().getX(), DELTA);
            assertEquals(r1.get(i).getCentroid().getY(),
                         r2.get(i).getCentroid().getY(), DELTA);
        }
    }

    // -----------------------------------------------------------------------
    // K formula: additional boundary cases
    // -----------------------------------------------------------------------

    @Test
    void cluster_n25_atMost5Clusters() {
        // K = max(3, ceil(sqrt(25))) = max(3, 5) = 5
        List<Vector2D> pts = randomPoints(25, SEED);
        List<String>   ids = noteIds(25);
        List<Cluster> result = new HybridClusterStrategy(SEED).cluster(pts, ids);

        assertTrue(result.size() <= 5, "n=25 must produce at most 5 clusters");
        assertEquals(25, result.stream().mapToInt(c -> c.getMemberNoteIds().size()).sum());
    }

    @Test
    void cluster_n100_atMost10Clusters() {
        // K = max(3, ceil(sqrt(100))) = max(3, 10) = 10
        List<Vector2D> pts = randomPoints(100, SEED);
        List<String>   ids = noteIds(100);
        List<Cluster> result = new HybridClusterStrategy(SEED).cluster(pts, ids);

        assertTrue(result.size() <= 10, "n=100 must produce at most 10 clusters");
        assertEquals(100, result.stream().mapToInt(c -> c.getMemberNoteIds().size()).sum());
    }

    @Test
    void cluster_n1_kClampedTo1_singleCluster() {
        // n=1: K=max(3,1)=3, but KMeans clamps to min(3,1)=1
        List<Cluster> result = new HybridClusterStrategy(SEED).cluster(
                List.of(new Vector2D(7, 3)), List.of("only"));

        assertEquals(1, result.size());
    }

    // -----------------------------------------------------------------------
    // Centroid contract: equals mean of member points (K-Means invariant)
    // -----------------------------------------------------------------------

    @Test
    void cluster_centroid_equalsExactMeanOfMemberPoints() {
        List<Vector2D> pts = randomPoints(16, SEED);
        List<String>   ids = noteIds(16);

        // Build lookup
        java.util.Map<String, Vector2D> ptById = new java.util.HashMap<>();
        for (int i = 0; i < ids.size(); i++) ptById.put(ids.get(i), pts.get(i));

        List<Cluster> result = new HybridClusterStrategy(SEED).cluster(pts, ids);

        for (Cluster c : result) {
            List<String> members = c.getMemberNoteIds();
            double meanX = members.stream().mapToDouble(id -> ptById.get(id).getX()).average().orElse(0);
            double meanY = members.stream().mapToDouble(id -> ptById.get(id).getY()).average().orElse(0);

            assertEquals(meanX, c.getCentroid().getX(), 1e-9,
                    "centroid.x must equal mean of member x-coordinates");
            assertEquals(meanY, c.getCentroid().getY(), 1e-9,
                    "centroid.y must equal mean of member y-coordinates");
        }
    }

    // -----------------------------------------------------------------------
    // buildDendrogram() — weighted centroid and structure
    // -----------------------------------------------------------------------

    @Test
    void buildDendrogram_twoIdenticalPoints_mergeDistanceIsZero() {
        DendrogramNode root = HybridClusterStrategy.buildDendrogram(
                List.of("a", "b"),
                List.of(new Vector2D(3, 4), new Vector2D(3, 4)));

        assertFalse(root.isLeaf());
        assertEquals(0.0, root.getMergeDistance(), DELTA);
    }

    @Test
    void buildDendrogram_secondMergeDistance_reflectsWeightedCentroid() {
        // (0,0),(2,0) merge first → centroid (1,0). Then (1,0)↔(10,0) = 9.0
        DendrogramNode root = HybridClusterStrategy.buildDendrogram(
                List.of("a","b","c"),
                List.of(new Vector2D(0,0), new Vector2D(2,0), new Vector2D(10,0)));

        assertFalse(root.isLeaf());
        assertEquals(9.0, root.getMergeDistance(), DELTA,
                "second merge distance must use weighted centroid of first merge");
    }

    @Test
    void buildDendrogram_fourPoints_twoSymmetricPairs_thenRootMerge() {
        // (0,0)-(1,0) distance=1 and (10,0)-(11,0) distance=1 merge first.
        // Then (0.5,0) and (10.5,0) merge at distance 10.
        DendrogramNode root = HybridClusterStrategy.buildDendrogram(
                List.of("a","b","c","d"),
                List.of(new Vector2D(0,0), new Vector2D(1,0),
                        new Vector2D(10,0), new Vector2D(11,0)));

        // Root is an internal node at distance 10.0
        assertFalse(root.isLeaf());
        assertEquals(10.0, root.getMergeDistance(), DELTA,
                "root merge distance must be distance between the two pair centroids");

        // Both children are internal nodes with distance 1.0
        assertFalse(root.getLeft().isLeaf());
        assertFalse(root.getRight().isLeaf());
        assertEquals(1.0, root.getLeft().getMergeDistance(),  DELTA);
        assertEquals(1.0, root.getRight().getMergeDistance(), DELTA);

        // All 4 leaves present
        assertEquals(Set.of("a","b","c","d"), collectLeaves(root));
    }

    @Test
    void buildDendrogram_treeHasExactlyNMinus1InternalNodes() {
        int m = 5;
        DendrogramNode root = HybridClusterStrategy.buildDendrogram(
                noteIds(m), randomPoints(m, SEED));

        // A full binary tree with m leaves has m-1 internal nodes
        assertEquals(m - 1, internalNodeCount(root));
    }

    @Test
    void buildDendrogram_internalNodeLeftAndRight_areBothNonNull() {
        DendrogramNode root = HybridClusterStrategy.buildDendrogram(
                List.of("p","q","r"),
                List.of(new Vector2D(0,0), new Vector2D(1,0), new Vector2D(5,0)));

        verifyInternalNodesHaveBothChildren(root);
    }

    // -----------------------------------------------------------------------
    // Large-scale smoke test
    // -----------------------------------------------------------------------

    @Test
    void cluster_largeDataset_completesAndAllPointsAssigned() {
        int n = 100;
        List<Vector2D> pts = randomPoints(n, SEED);
        List<String>   ids = noteIds(n);

        List<Cluster> result = new HybridClusterStrategy(SEED).cluster(pts, ids);

        assertEquals(n, result.stream().mapToInt(c -> c.getMemberNoteIds().size()).sum());
        result.forEach(c -> assertNotNull(c.getDendrogram()));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Collects all leaf note-IDs reachable from {@code node}. */
    private static Set<String> collectLeaves(DendrogramNode node) {
        Set<String> leaves = new HashSet<>();
        collectLeavesRec(node, leaves);
        return leaves;
    }

    private static void collectLeavesRec(DendrogramNode node, Set<String> out) {
        if (node.isLeaf()) { out.add(node.getNoteId()); return; }
        collectLeavesRec(node.getLeft(),  out);
        collectLeavesRec(node.getRight(), out);
    }

    /** Counts the total number of leaf nodes in the subtree. */
    private static int leafCount(DendrogramNode node) {
        if (node.isLeaf()) return 1;
        return leafCount(node.getLeft()) + leafCount(node.getRight());
    }

    /** Asserts that every internal node has mergeDistance >= 0. */
    private static void verifyMergeDistancesNonNegative(DendrogramNode node) {
        if (node.isLeaf()) return;
        assertTrue(node.getMergeDistance() >= 0,
                "mergeDistance must be >= 0, was " + node.getMergeDistance());
        verifyMergeDistancesNonNegative(node.getLeft());
        verifyMergeDistancesNonNegative(node.getRight());
    }

    /** Finds the internal node with the smallest mergeDistance in the subtree. */
    private static DendrogramNode findInternalNodeWithSmallestDistance(DendrogramNode node) {
        if (node.isLeaf()) return null;
        DendrogramNode best = node;
        DendrogramNode fromLeft  = findInternalNodeWithSmallestDistance(node.getLeft());
        DendrogramNode fromRight = findInternalNodeWithSmallestDistance(node.getRight());
        if (fromLeft  != null && fromLeft.getMergeDistance()  < best.getMergeDistance()) best = fromLeft;
        if (fromRight != null && fromRight.getMergeDistance() < best.getMergeDistance()) best = fromRight;
        return best;
    }

    private static List<Vector2D> randomPoints(int n, long seed) {
        java.util.Random rng = new java.util.Random(seed);
        List<Vector2D> pts = new ArrayList<>(n);
        for (int i = 0; i < n; i++)
            pts.add(new Vector2D(rng.nextDouble() * 100, rng.nextDouble() * 100));
        return pts;
    }

    private static List<String> noteIds(int n) {
        List<String> ids = new ArrayList<>(n);
        for (int i = 0; i < n; i++) ids.add("note-" + i);
        return ids;
    }

    /** Counts internal (non-leaf) nodes in the subtree. */
    private static int internalNodeCount(DendrogramNode node) {
        if (node.isLeaf()) return 0;
        return 1 + internalNodeCount(node.getLeft()) + internalNodeCount(node.getRight());
    }

    /** Asserts every internal node has non-null left and right children. */
    private static void verifyInternalNodesHaveBothChildren(DendrogramNode node) {
        if (node.isLeaf()) return;
        assertNotNull(node.getLeft(),  "internal node must have a left child");
        assertNotNull(node.getRight(), "internal node must have a right child");
        verifyInternalNodesHaveBothChildren(node.getLeft());
        verifyInternalNodesHaveBothChildren(node.getRight());
    }
}
