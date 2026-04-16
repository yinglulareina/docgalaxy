# Neural Networks

A neural network is a computational model loosely inspired by the structure of the brain. It consists of layers of interconnected nodes (neurons) that transform an input signal into a useful output.

## Architecture

Each neuron receives a weighted sum of its inputs, adds a bias term, and passes the result through an activation function (ReLU, sigmoid, tanh). Layers are stacked: an input layer, one or more hidden layers, and an output layer.

Deep networks (many hidden layers) can represent highly complex functions. The depth allows the network to learn hierarchical features – edges → shapes → objects in vision, or characters → words → sentences in language.

## Training

Networks are trained by gradient descent. A loss function measures how wrong the predictions are. Backpropagation computes the gradient of the loss with respect to every weight. Weights are nudged in the direction that reduces loss.

An epoch is one complete pass through the training data. Training typically requires hundreds of epochs and millions of examples to converge to good performance.

## Key Variants

- **Convolutional Neural Networks (CNNs)** – share weights across spatial positions; excel at image tasks.
- **Recurrent Neural Networks (RNNs)** – maintain hidden state across time steps; used for sequences.
- **Transformers** – use attention mechanisms; now dominant in language and vision.
