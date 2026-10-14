"""Pinned model identity shared by the downloader and offline inference."""

MODEL_ID = "florence-community/Florence-2-base-ft"
MODEL_REVISION = "0b03b6f15a4a211370fb204aee4e7dd48887ea37"

# Only data files are needed by the native Transformers implementation.
MODEL_FILES = (
    "README.md",
    "config.json",
    "generation_config.json",
    "model.safetensors",
    "preprocessor_config.json",
    "processor_config.json",
    "tokenizer.json",
    "tokenizer_config.json",
    "special_tokens_map.json",
    "added_tokens.json",
    "vocab.json",
    "merges.txt",
)
