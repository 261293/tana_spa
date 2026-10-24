# Bundled fonts

| Family | Role | Files |
| --- | --- | --- |
| Fraunces | display serif (headings) | `fraunces_regular.ttf`, `fraunces_medium.ttf` |
| Instrument Sans | body | `instrument_sans_{regular,medium,semibold,bold}.ttf` |

Both are SIL Open Font License 1.1; the licence texts sit beside this file. Upstream is
`google/fonts` (`ofl/fraunces`, `ofl/instrumentsans`), which ships **variable** fonts only.

The bundled files are static instances, not the upstream files. Fraunces' variable defaults are
`wght=900, opsz=9, WONK=1` — a heavy, quirky display cut — so shipping the variable font and
asking for `FontWeight.Normal` renders the wrong face. Each instance pins every axis:

| File | Axes |
| --- | --- |
| `fraunces_regular.ttf` | `wght=400, opsz=30, SOFT=0, WONK=0` |
| `fraunces_medium.ttf` | `wght=500, opsz=30, SOFT=0, WONK=0` |
| `instrument_sans_*.ttf` | `wght=400/500/600/700, wdth=100` |

To regenerate, download the upstream variable TTFs and instance them with
`fontTools.varLib.instancer.instantiateVariableFont(font, axes, updateFontNames=False)`
(`updateFontNames=True` fails: Fraunces' STAT table has no named value for `opsz=30`).
