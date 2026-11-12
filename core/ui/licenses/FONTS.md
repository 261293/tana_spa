# Bundled fonts

| Family | Role | Files |
| --- | --- | --- |
| Newsreader | display serif (headings) | `newsreader_light.ttf`, `newsreader_regular.ttf` |
| Hanken Grotesk | body, labels, controls | `hanken_grotesk_{regular,medium,semibold}.ttf` |

Both are SIL Open Font License 1.1; the licence texts sit beside this file. Upstream is
`google/fonts` (`ofl/newsreader`, `ofl/hankengrotesk`), which ships **variable** fonts only.

The bundled files are the static, unsubsetted TTF instances the Google Fonts CSS API serves
(`fonts.googleapis.com/css2?family=Newsreader:opsz,wght@72,300;72,400&family=Hanken+Grotesk:wght@400;500;600`,
requested without a browser user agent). Newsreader is asked for at its display optical size.

Both families were chosen for their Vietnamese coverage: every file maps the precomposed
Vietnamese letters (U+1EA0–U+1EF9), `đ`, `ơ`, `ư` and the dong sign `₫`. Check that again before
swapping a face — a missing glyph falls back to another font mid-word.
