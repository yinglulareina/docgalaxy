# Transformers and Attention

The Transformer architecture, introduced in "Attention Is All You Need" (Vaswani et al., 2017), replaced recurrent networks as the dominant approach for sequence modeling. It now underlies most large language models.

## Self-Attention

Self-attention allows every token in a sequence to attend to every other token directly, regardless of distance. Each token produces query (Q), key (K), and value (V) vectors. The attention score between two tokens is the dot product of their Q and K vectors, scaled and softmaxed. The output is a weighted sum of V vectors.

This mechanism lets the model capture long-range dependencies that RNNs struggled with – a word at position 1 can directly influence position 512 without passing through 511 intermediate states.

## Multi-Head Attention

Rather than a single attention function, multiple heads attend to different subspaces of the representation simultaneously. The outputs are concatenated and projected. This allows the model to capture different types of relationships (syntactic, semantic, coreference) in parallel.

## Positional Encoding

Unlike RNNs, Transformers process all tokens simultaneously and have no inherent notion of order. Positional encodings (sinusoidal functions or learned embeddings) are added to the token embeddings to inject sequence position information.

## Impact

BERT, GPT, T5, and virtually every frontier language model is built on this architecture. Transformers have also been adapted for vision (ViT), audio (Whisper), and multimodal tasks (CLIP, DALL-E).
