package com.docgalaxy.layout;

import com.docgalaxy.model.Vector2D;
import com.docgalaxy.util.AppConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * PCA-based dimensionality reducer: projects high-dimensional embeddings
 * (e.g. 1536-D) to 2-D {@link Vector2D} positions for canvas layout.
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Center data by subtracting the column mean.</li>
 *   <li>Find PC1 via matrix-free power iteration on X&thinsp;Xᵀ (avoids
 *       allocating an explicit d×d covariance matrix for large d).</li>
 *   <li>Deflate the centered matrix and repeat for PC2.</li>
 * </ol>
 *
 * <p>Not thread-safe: {@link #fit} should be called from a background thread
 * (SwingWorker); {@link #transform}/{@link #transformAll} are read-only after
 * fitting and may be called from any thread once fitting is complete.
 */
public class DimensionReducer {

    private static final int    MAX_ITER       = 200;
    private static final double CONVERGENCE_TOL = 1e-10;
    private static final long   RNG_SEED        = 42L;

    /**
     * projectionMatrix[0] = first principal component (eigenvector of XᵀX),
     * projectionMatrix[1] = second principal component.
     * Shape: [2][inputDim].
     */
    private double[][] projectionMatrix;
    private double[]   mean;
    private int        fittedCount;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Fits the PCA model on the given embedding vectors.
     * Replaces any previous fit.
     *
     * @param vectors non-empty list of embedding vectors (all must have same length)
     * @throws IllegalArgumentException if the list is null, empty, or contains
     *                                  vectors of different lengths
     */
    public void fit(List<double[]> vectors) {
        if (vectors == null || vectors.isEmpty()) {
            throw new IllegalArgumentException("vectors must not be null or empty");
        }
        int n   = vectors.size();
        int dim = vectors.get(0).length;
        for (double[] v : vectors) {
            if (v.length != dim) {
                throw new IllegalArgumentException(
                        "All vectors must have the same dimension");
            }
        }

        // 1. Compute mean
        double[] mu = new double[dim];
        for (double[] v : vectors) {
            for (int j = 0; j < dim; j++) mu[j] += v[j];
        }
        for (int j = 0; j < dim; j++) mu[j] /= n;
        this.mean = mu;

        // 2. Center: X[i][j] = vectors[i][j] - mean[j]
        double[][] X = new double[n][dim];
        for (int i = 0; i < n; i++) {
            double[] v = vectors.get(i);
            for (int j = 0; j < dim; j++) X[i][j] = v[j] - mu[j];
        }

        Random rng = new Random(RNG_SEED);

        // 3. PC1 via power iteration
        double[] ev1 = powerIterate(X, n, dim, rng, null);

        // 4. Deflate and find PC2
        double[][] Xd = deflate(X, n, dim, ev1);
        double[] ev2  = powerIterate(Xd, n, dim, rng, ev1);

        // 5. Sign normalization: make first non-zero element positive for consistency
        fixSign(ev1);
        fixSign(ev2);

        projectionMatrix = new double[][]{ev1, ev2};
        fittedCount      = n;
    }

    /**
     * Projects a single embedding vector to 2-D.
     *
     * @param vector embedding vector (must match the dimension used during fitting)
     * @return 2-D projection
     * @throws IllegalStateException    if {@link #fit} has not been called
     * @throws IllegalArgumentException if {@code vector} is null or has wrong length
     */
    public Vector2D transform(double[] vector) {
        if (projectionMatrix == null) {
            throw new IllegalStateException("DimensionReducer has not been fitted yet");
        }
        if (vector == null) {
            throw new IllegalArgumentException("vector must not be null");
        }
        if (vector.length != mean.length) {
            throw new IllegalArgumentException(
                    "vector length " + vector.length +
                    " does not match fitted dimension " + mean.length);
        }

        // Center then project
        double[] centered = new double[vector.length];
        for (int j = 0; j < vector.length; j++) centered[j] = vector[j] - mean[j];

        double x = dot(projectionMatrix[0], centered);
        double y = dot(projectionMatrix[1], centered);
        return new Vector2D(x, y);
    }

    /**
     * Projects a batch of embedding vectors to 2-D.
     *
     * @param vectors list of embedding vectors; must not be null
     * @return list of {@link Vector2D} in the same order as input
     * @throws IllegalStateException if not fitted
     */
    public List<Vector2D> transformAll(List<double[]> vectors) {
        if (vectors == null) throw new IllegalArgumentException("vectors must not be null");
        List<Vector2D> out = new ArrayList<>(vectors.size());
        for (double[] v : vectors) out.add(transform(v));
        return out;
    }

    /**
     * Returns {@code true} if the number of notes has changed by more than
     * {@link AppConstants#REFIT_THRESHOLD} (20 %) since the last fit.
     * Always returns {@code true} if the model has never been fitted.
     *
     * @param currentCount current number of notes in the knowledge base
     */
    public boolean needsRefit(int currentCount) {
        if (projectionMatrix == null) return true;
        if (fittedCount == 0)         return currentCount > 0;
        double change = Math.abs(currentCount - fittedCount) / (double) fittedCount;
        return change > AppConstants.REFIT_THRESHOLD;
    }

    /**
     * Returns {@code true} if {@link #fit} has completed successfully and
     * the projection matrix is available for transforms.
     */
    public boolean isTrained() {
        return projectionMatrix != null;
    }

    /**
     * Returns a copy of the two principal-component vectors, shape [2][dim].
     * Returns {@code null} if not trained.
     */
    public double[][] getProjectionMatrix() {
        if (projectionMatrix == null) return null;
        return new double[][]{projectionMatrix[0].clone(), projectionMatrix[1].clone()};
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Matrix-free power iteration to find the dominant eigenvector of XᵀX.
     * Avoids allocating the explicit d×d covariance matrix:
     *   (XᵀX)v = Xᵀ(Xv)  — two O(n·d) passes per iteration.
     *
     * @param orthogonalize if non-null, the result is re-orthogonalised against
     *                      this vector after each multiply step (for deflation stability)
     */
    private static double[] powerIterate(double[][] X, int n, int dim,
                                         Random rng, double[] orthogonalize) {
        // Random start — Gaussian draw, then orthogonalize against any previously
        // found eigenvector before normalizing.  This ensures that even the
        // degenerate zero-eigenvalue case (iteration body never runs because
        // Xd*v == 0) returns a unit vector that is orthogonal to the prior PC.
        double[] v = new double[dim];
        for (int j = 0; j < dim; j++) v[j] = rng.nextGaussian();
        if (orthogonalize != null) {
            double proj = dot(v, orthogonalize);
            for (int j = 0; j < dim; j++) v[j] -= proj * orthogonalize[j];
        }
        if (normalize(v) == 0) {
            v[0] = 1.0; // degenerate fallback
        }

        double[] w    = new double[n];
        double[] vNew = new double[dim];

        for (int iter = 0; iter < MAX_ITER; iter++) {
            // w = X * v
            for (int i = 0; i < n; i++) {
                double sum = 0;
                double[] xi = X[i];
                for (int j = 0; j < dim; j++) sum += xi[j] * v[j];
                w[i] = sum;
            }

            // vNew = Xᵀ * w
            for (int j = 0; j < dim; j++) vNew[j] = 0;
            for (int i = 0; i < n; i++) {
                double wi  = w[i];
                double[] xi = X[i];
                for (int j = 0; j < dim; j++) vNew[j] += xi[j] * wi;
            }

            // Gram-Schmidt: remove component along previously found eigenvector
            if (orthogonalize != null) {
                double proj = dot(vNew, orthogonalize);
                for (int j = 0; j < dim; j++) vNew[j] -= proj * orthogonalize[j];
            }

            double norm = normalize(vNew);
            if (norm == 0) break; // zero eigenvalue — keep current v

            // Convergence: cos(angle) between consecutive vectors ≈ 1 (sign-agnostic)
            double cosAngle = Math.abs(dot(v, vNew));
            System.arraycopy(vNew, 0, v, 0, dim);
            if (cosAngle >= 1.0 - CONVERGENCE_TOL) break;
        }

        return v;
    }

    /** Deflates X by projecting out the given eigenvector: X_d[i] = X[i] - (X[i]·ev)*ev */
    private static double[][] deflate(double[][] X, int n, int dim, double[] ev) {
        double[][] Xd = new double[n][dim];
        for (int i = 0; i < n; i++) {
            double proj = dot(X[i], ev);
            for (int j = 0; j < dim; j++) Xd[i][j] = X[i][j] - proj * ev[j];
        }
        return Xd;
    }

    /** In-place L2 normalisation; returns the original norm (0 if zero vector). */
    private static double normalize(double[] v) {
        double norm = 0;
        for (double x : v) norm += x * x;
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < v.length; i++) v[i] /= norm;
        }
        return norm;
    }

    /**
     * Ensures the first non-zero element of the eigenvector is positive,
     * removing the sign ambiguity inherent in power iteration.
     */
    private static void fixSign(double[] ev) {
        for (double v : ev) {
            if (v > 0) return;
            if (v < 0) {
                for (int i = 0; i < ev.length; i++) ev[i] = -ev[i];
                return;
            }
        }
    }

    private static double dot(double[] a, double[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }
}
