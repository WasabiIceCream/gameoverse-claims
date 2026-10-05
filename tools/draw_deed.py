"""Draws the Land Deed item texture (16x16): a parchment deed with a plot outline and a red wax seal."""
from pathlib import Path
from PIL import Image

PALETTE = {
    '.': (0, 0, 0, 0),
    'o': (92, 64, 38, 255),     # outline
    'p': (233, 214, 168, 255),  # parchment
    's': (204, 180, 128, 255),  # parchment shade
    'r': (178, 154, 104, 255),  # roll shadow
    'g': (86, 140, 62, 255),    # plot (grass)
    'G': (58, 104, 44, 255),    # plot border
    'R': (180, 36, 36, 255),    # seal
    'D': (120, 20, 24, 255),    # seal shade
    'l': (150, 128, 92, 255),   # text lines
}
ROWS = [
    "................",
    "..oooooooooooo..",
    ".orrrrrrrrrrrro.",
    ".oppppppppppppo.",
    "..oppppppppppo..",
    "..opllllllllpo..",
    "..oppppppppppo..",
    "..opGGGGGGpppo..",
    "..opGggggGpllo..",
    "..opGggggGpppo..",
    "..opGGGGGGpllo..",
    "..opppppppRRpo..",
    "..osssssssRDRo..",
    ".orrrrrrrrDRDro.",
    ".oooooooooooooo.",
    "................",
]
img = Image.new('RGBA', (16, 16))
for y, row in enumerate(ROWS):
    assert len(row) == 16, (y, len(row))
    for x, c in enumerate(row):
        img.putpixel((x, y), PALETTE[c])
out = Path(__file__).resolve().parent.parent / 'src/main/resources/assets/gameoverse_claims/textures/item/land_deed.png'
img.save(out)
print(out)
