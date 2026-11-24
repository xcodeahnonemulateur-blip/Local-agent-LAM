#!/usr/bin/env python3
"""Experimental offline Core ML export for OPUS-MT. Run locally on a Mac.
This produces model packages and tokenizer assets, not an integrated iOS engine.
Install torch, transformers<5, sentencepiece, coremltools, numpy, huggingface_hub.
"""
import argparse
import json
from pathlib import Path

MODELS = {
    "he-fr": "Helsinki-NLP/opus-tatoeba-he-fr",
    "fr-he": "Helsinki-NLP/opus-mt-fr-he",
}

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--pair", choices=MODELS, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--revision", required=True, help="Verified full Hugging Face model commit SHA")
    parser.add_argument("--length", type=int, default=64)
    args = parser.parse_args()
    if not (16 <= args.length <= 128):
        parser.error("length must be between 16 and 128")
    if len(args.revision) != 40 or any(c not in "0123456789abcdef" for c in args.revision):
        parser.error("revision must be a full 40-character lowercase commit SHA")
    import numpy as np
    import torch
    import coremltools as ct
    from transformers import AutoModelForSeq2SeqLM, AutoTokenizer
    from huggingface_hub import snapshot_download
    repo = MODELS[args.pair]
    directory = Path(snapshot_download(repo, revision=args.revision))
    tokenizer = AutoTokenizer.from_pretrained(directory, local_files_only=True, trust_remote_code=False)
    model = AutoModelForSeq2SeqLM.from_pretrained(
        directory, local_files_only=True, trust_remote_code=False,
        attn_implementation="eager"
    ).eval()
    n = args.length
    output = args.output / args.pair
    output.mkdir(parents=True, exist_ok=True)
    tokenizer.save_pretrained(output / "tokenizer")
    for name in ("README.md", "LICENSE", "LICENSE.txt"):
        if (directory / name).exists():
            (output / name).write_bytes((directory / name).read_bytes())

    class NextToken(torch.nn.Module):
        def __init__(self, model):
            super().__init__()
            self.model = model
        def forward(self, input_ids, attention_mask, decoder_ids, decoder_mask):
            result = self.model(
                input_ids=input_ids.long(), attention_mask=attention_mask.long(),
                decoder_input_ids=decoder_ids.long(),
                decoder_attention_mask=decoder_mask.long(),
                use_cache=False, return_dict=False
            )[0]
            position = decoder_mask.long().sum(dim=1).clamp(min=1) - 1
            index = position.view(-1, 1, 1).expand(-1, 1, result.shape[-1])
            return torch.gather(result, 1, index).squeeze(1)

    source = "שלום, מה שלומך?" if args.pair == "he-fr" else "Bonjour, comment vas-tu ?"
    tokens = tokenizer(source, padding="max_length", truncation=True, max_length=n, return_tensors="pt")
    pad = model.config.pad_token_id
    start = model.config.decoder_start_token_id
    if pad is None or start is None:
        raise ValueError("Missing decoder special tokens")
    prefix = torch.full((1, n), pad, dtype=torch.int32)
    prefix[0, 0] = start
    mask = torch.zeros((1, n), dtype=torch.int32)
    mask[0, 0] = 1
    sample = (tokens.input_ids.int(), tokens.attention_mask.int(), prefix, mask)
    wrapper = NextToken(model).eval()
    with torch.no_grad():
        traced = torch.jit.trace(wrapper, sample, strict=False)
        expected = wrapper(*sample).numpy()
    names = ["input_ids", "attention_mask", "decoder_ids", "decoder_mask"]
    package = ct.convert(traced, convert_to="mlprogram",
        inputs=[ct.TensorType(name=name, shape=(1, n), dtype=np.int32) for name in names],
        outputs=[ct.TensorType(name="next_token_logits")],
        minimum_deployment_target=ct.target.iOS16,
        compute_precision=ct.precision.FLOAT32)
    package.save(str(output / "NextToken.mlpackage"))
    # This runs on macOS. A failed prediction or numerical mismatch is a failed export.
    actual = package.predict({name: tensor.numpy() for name, tensor in zip(names, sample)})["next_token_logits"]
    np.testing.assert_allclose(actual, expected, rtol=1e-3, atol=1e-3)
    reference = model.generate(**{k:v for k,v in tokens.items()}, max_new_tokens=n-1)
    (output / "manifest.json").write_text(json.dumps({
        "repository": repo, "revision": args.revision, "length": n,
        "pad_token_id": pad, "decoder_start_token_id": start,
        "eos_token_id": model.config.eos_token_id,
        "reference_input": source, "reference_translation": tokenizer.decode(reference[0], skip_special_tokens=True),
        "license": "Apache-2.0", "status": "one_step_parity_only"
    }, ensure_ascii=False, indent=2), encoding="utf-8")

if __name__ == "__main__":
    main()
