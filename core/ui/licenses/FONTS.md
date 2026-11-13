# Bundled fonts

| Family | Role | Files |
| --- | --- | --- |
| Fraunces | display serif (headings, prices, the brand) | `fraunces_regular.ttf`, `fraunces_medium.ttf` |
| Be Vietnam Pro | body, labels, controls | `be_vietnam_pro_{regular,medium,semibold}.ttf` |

Both are SIL Open Font License 1.1; the licence texts sit beside this file. Be Vietnam Pro is the
static TTF set in `google/fonts` (`ofl/bevietnampro`). The Fraunces files are the static instances
the app bundled before 2026-11-12, restored from git history (`google/fonts` ships Fraunces as a
variable font only).

Both families were chosen for their Vietnamese coverage: every file maps the precomposed
Vietnamese letters (U+1EA0–U+1EF9), `đ`, `ơ`, `ư` and the dong sign `₫`. Check that again before
swapping a face — a missing glyph falls back to another font mid-word.
