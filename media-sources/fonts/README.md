# Fonts

Variable fonts from [google/fonts](https://github.com/google/fonts), fetched
2026-10-17, unmodified, under the SIL Open Font License 1.1 (the `*-OFL.txt`
files here):

| File | Family | Source commit |
|---|---|---|
| `Fraunces[SOFT,WONK,opsz,wght].ttf` | Fraunces, by Undercase Type | `ofl/fraunces` at `4024282` |
| `AtkinsonHyperlegibleNext[wght].ttf` | Atkinson Hyperlegible Next, by the Braille Institute | `ofl/atkinsonhyperlegiblenext` at `95f4904` |

Fraunces sets headings; Atkinson Hyperlegible Next, drawn for low-vision
readers, sets body text and chess notation.

`app/src/main/res/font/*.ttf` are static instances generated from them,
subset to Latin:

```sh
python3 tools/fonts/build_fonts.py          # needs fontTools
python3 tools/fonts/build_fonts.py --check  # fails if the fonts are stale
```
