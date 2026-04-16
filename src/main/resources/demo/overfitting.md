# Overfitting and Generalization

Overfitting occurs when a model learns the training data too well – including its noise and random fluctuations – and fails to generalize to new, unseen examples.

## The Bias-Variance Tradeoff

Every model makes errors from two sources:

**Bias** – systematic error from wrong assumptions. A linear model fitting non-linear data has high bias. It underfits: too simple to capture the true pattern.

**Variance** – sensitivity to small fluctuations in training data. A very deep network memorizing 100 training examples has high variance. It overfits: too complex, models the noise.

The goal is to find the sweet spot where total error (bias² + variance + irreducible noise) is minimized.

## Detecting Overfitting

Plot training loss and validation loss against epochs. When training loss continues to fall while validation loss plateaus or rises, the model is overfitting.

## Remedies

1. **More data** – the most reliable fix. More examples constrain the model.
2. **Regularization** – L1/L2 weight penalties, dropout, label smoothing.
3. **Simpler model** – reduce layers, reduce parameters.
4. **Cross-validation** – k-fold CV gives a more reliable estimate of generalization error.
5. **Data augmentation** – synthetically expand the training set.
6. **Early stopping** – stop before the model memorizes training noise.
