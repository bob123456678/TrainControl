# -*- coding: utf-8 -*-
"""FR-029: the sidebar icons.

Adam, first pass: "the sidebar icons (locomotive, track, autonomy, signal, route, stats, log), while
nice, date the application.  use modernized, simple icons with a plain blue color matching the flatlaf
theme."

Adam, second pass (2026-08-26), which is what this version answers:

    "stats looks good, log is almost good but not symmetrical, routes is confusing, signals (keyboard)
    look poorly traced, the graph one doesn't make sense (make it be a play icon), the track one is
    almost good but needs to bar at the bottom, and the locomotive is too small - make it bigger so
    the overlaid page number is more clearly visible.  Try using dark gray like 333 for the icons,
    rather than the blue."

Three things worth knowing before changing any of these.

**They are drawn at 4x and shrunk.** PIL does not anti-alias shape edges, so a circle punched out of a
rectangle came out with a stepped edge that survived the scale down - which is what "poorly traced"
was looking at on the signal. Everything is drawn on a 2048 canvas and reduced to 512 with LANCZOS, so
every edge here is averaged down four pixels to one. It costs nothing and it is the whole of the fix.

**The locomotive carries the page number.** `TrainControlUI` merges a centred white "1".."10" over
TAB_ICON_CONTROL with a black shadow one pixel down and right. Centred - so the number lands at the
MIDDLE of the icon, and the old locomotive sat in the bottom two thirds with the middle empty. White
text on nothing is white text on whatever the tab strip is painted, which is why it was hard to read.
So this one is drawn deliberately tall and solid through the centre: the number needs something dark
to sit on, and at #333 it now has it.

**Only the silhouette survives.** getTabIcon scales these to 30 pixels tall. Anything finer than about
a fortieth of the canvas is gone at that size, which is what STROKE is for.
"""
import os
import sys
from PIL import Image, ImageDraw

OUT = 512
SS = 4                                  # supersample: draw at 2048, reduce to 512
BIG = OUT * SS
PAD = 56 * SS

# Dark grey rather than the accent blue (Adam, 2026-08-26).  #333.
INK = (51, 51, 51, 255)
HOLE = (0, 0, 0, 0)

STROKE = 46 * SS                        # a fortieth of the canvas would vanish; this survives 30px

out = sys.argv[1]

if not os.path.isdir(out):
    os.makedirs(out)


def canvas():
    image = Image.new('RGBA', (BIG, BIG), (0, 0, 0, 0))
    return image, ImageDraw.Draw(image)


def save(image, name):
    """Reduces to OUT, keeping the ALPHA from the resample and none of the colour.

    Straight resizing an RGBA image mixes the colour channels of the transparent surround into the
    edges, so an edge pixel comes out #323232 instead of #333333 - invisible to the eye and a hard
    failure for `testEverySidebarIconIsOneFlatColour`, which exists to catch a photograph being
    dropped in here and would have had to be loosened to a tolerance to accept it.

    Loosening it would have been the wrong way round. What the antialiasing actually wants to vary is
    coverage, not colour: every pixel is the same ink, some of it thinner. So the alpha channel is
    taken from the reduction and painted onto flat INK, which is both what the test asserts and what
    the icons are supposed to be.
    """
    reduced = image.resize((OUT, OUT), Image.LANCZOS)

    flat = Image.new('RGBA', (OUT, OUT), INK)
    flat.putalpha(reduced.getchannel('A'))

    flat.save(os.path.join(out, name + '.png'))
    print('wrote', name + '.png')


def s(*values):
    """Coordinates written at 512 and scaled to the working canvas, so the numbers stay readable."""
    return tuple(int(v * SS) for v in values)


# ---------------------------------------------------------------- locomotive
#
# Bigger, and centre-heavy, because the page number is drawn over the middle of it.
image, d = canvas()

d.rounded_rectangle(s(40, 108, 300, 340), radius=28 * SS, fill=INK)      # cab
d.rounded_rectangle(s(250, 170, 476, 340), radius=48 * SS, fill=INK)     # boiler
d.rounded_rectangle(s(28, 340, 484, 392), radius=18 * SS, fill=INK)      # footplate
d.rounded_rectangle(s(388, 74, 452, 180), radius=14 * SS, fill=INK)      # chimney

d.ellipse(s(72, 372, 208, 508), fill=INK)                               # driver
d.ellipse(s(228, 372, 364, 508), fill=INK)                              # driver
d.ellipse(s(384, 404, 480, 500), fill=INK)                              # pony

# Cab window, punched out - and kept OUT of the middle, where the number goes.
d.rounded_rectangle(s(80, 146, 200, 244), radius=16 * SS, fill=HOLE)

save(image, 'loc')

# ---------------------------------------------------------------- track
#
# Symmetric now, with a sleeper hard at each end: the rails used to run 64 past the top sleeper and 8
# past the bottom one, which is the lopsidedness Adam saw.
image, d = canvas()

SLEEPERS = (PAD + 22 * SS, 172 * SS, 288 * SS, 404 * SS, BIG - PAD - 22 * SS)

for x in (150, 362):
    d.rounded_rectangle((x * SS - STROKE // 2, PAD, x * SS + STROKE // 2, BIG - PAD),
        radius=12 * SS, fill=INK)

for y in SLEEPERS:
    d.rounded_rectangle((72 * SS, y - 22 * SS, BIG - 72 * SS, y + 22 * SS),
        radius=10 * SS, fill=INK)

save(image, 'track')

# ---------------------------------------------------------------- autonomy
#
# A play triangle.  It was four linked nodes - the autonomy GRAPH - and Adam's answer to that was
# "the graph one doesn't make sense (make it be a play icon)".  He is right twice over: the graph is
# the thing this release deprecated, and what the tab does is START the railway running.
image, d = canvas()

d.polygon([s(140, 64), s(140, 448), s(452, 256)], fill=INK)

save(image, 'autonomy')

# ---------------------------------------------------------------- signal
#
# Same signal, drawn cleanly.  The lamps are punched out of the head, and at 1x that punch had a
# stepped edge; at 4x it does not.
image, d = canvas()

d.rounded_rectangle(s(232, 330, 280, 436), radius=14 * SS, fill=INK)     # mast
d.rounded_rectangle(s(156, 40, 356, 340), radius=60 * SS, fill=INK)      # head
d.ellipse(s(196, 80, 316, 200), fill=HOLE)                              # upper lamp
d.ellipse(s(196, 208, 316, 328), fill=HOLE)                             # lower lamp
d.rounded_rectangle(s(132, 428, 380, 472), radius=16 * SS, fill=INK)     # base

save(image, 'signal')

# ---------------------------------------------------------------- route
#
# A PATH, not a switch.  The old one was a line forking with the fork picked out, which is a turnout -
# and a turnout is what a route SETS, not what it is.  Adam read it as confusing, which it was: the
# same drawing means "switch" on any other railway diagram in the application.
#
# A route here is an ordered journey: start there, end here.  So: a dot to start, a path that turns,
# an arrowhead to finish.
image, d = canvas()

# Heavier than the other strokes on purpose: this icon is a path rather than a solid, so at 30px it
# carries far less ink than its neighbours and looked faint beside them.
WIDE = STROKE + 30 * SS

d.line([s(112, 424), s(300, 424)], fill=INK, width=WIDE)
d.line([s(300, 424), s(300, 168)], fill=INK, width=WIDE)
d.line([s(300, 168), s(390, 168)], fill=INK, width=WIDE)

# Rounded corners, so the turns do not come out mitred into points.
for corner in (s(300, 424), s(300, 168)):
    d.ellipse((corner[0] - WIDE // 2, corner[1] - WIDE // 2,
               corner[0] + WIDE // 2, corner[1] + WIDE // 2), fill=INK)

# Where it starts.
d.ellipse(s(58, 370, 166, 478), fill=INK)

# Where it ends.
d.polygon([s(360, 74), s(360, 262), s(486, 168)], fill=INK)

save(image, 'route')

# ---------------------------------------------------------------- stats
#
# Untouched: "stats looks good".
image, d = canvas()

for x, top in ((96, 300), (216, 200), (336, 96)):
    d.rounded_rectangle((x * SS, top * SS, (x + 80) * SS, BIG - PAD), radius=16 * SS, fill=INK)

save(image, 'stats')

# ---------------------------------------------------------------- log
#
# Centred.  The four lines sat 76 from the top of the box and 42 from the bottom - close enough to
# look accidental rather than intentional, which is exactly how Adam described it.
image, d = canvas()

d.rounded_rectangle((96 * SS, PAD, BIG - 96 * SS, BIG - PAD), radius=36 * SS, fill=INK)

# Equal air above the first line and below the last, worked out rather than typed in.
TOP, BOTTOM = PAD, BIG - PAD
LINES, HALF, GAP = 4, 18 * SS, 82 * SS

block = (LINES - 1) * GAP + 2 * HALF
first = TOP + (BOTTOM - TOP - block) // 2 + HALF

for n in range(LINES):
    y = first + n * GAP
    d.rounded_rectangle((150 * SS, y - HALF, BIG - 150 * SS, y + HALF), radius=10 * SS, fill=HOLE)

save(image, 'log')

# ---------------------------------------------------------------- contact sheet
#
# At the size they are actually seen, and at double it.  No dark strip: the application calls
# FlatLightLaf.setup() and offers no other theme, so #333 is only ever seen on a light tab.
#
# The bottom row is the locomotive with a page number merged over it the way TrainControlUI does it -
# centred, white, with a black shadow one pixel down and right.  That is the thing Adam could not read,
# so it is the thing this sheet has to show rather than describe.
names = ('loc', 'track', 'autonomy', 'signal', 'route', 'stats', 'log')

sheet = Image.new('RGB', (len(names) * 110 + 20, 300), (247, 247, 247))

at = 20

for name in names:
    icon = Image.open(os.path.join(out, name + '.png'))

    small = icon.resize((30, 30), Image.LANCZOS)
    large = icon.resize((60, 60), Image.LANCZOS)

    sheet.paste(small, (at + 30, 40), small)
    sheet.paste(large, (at + 15, 100), large)

    at += 110

# The page number, as the application merges it.
from PIL import ImageFont

try:
    font = ImageFont.truetype('segoeuib.ttf', 16)
except IOError:
    font = ImageFont.load_default()

loc = Image.open(os.path.join(out, 'loc.png')).resize((30, 30), Image.LANCZOS)

at = 20

for page in ('1', '2', '5', '8', '10'):
    tile = loc.copy()
    pen = ImageDraw.Draw(tile)

    box = pen.textbbox((0, 0), page, font=font)
    x = (30 - (box[2] - box[0])) // 2 - box[0]
    y = (30 - (box[3] - box[1])) // 2 - box[1]

    pen.text((x + 1, y + 1), page, font=font, fill=(0, 0, 0, 255))
    pen.text((x, y), page, font=font, fill=(255, 255, 255, 255))

    sheet.paste(tile, (at + 30, 210), tile)
    sheet.paste(tile.resize((60, 60), Image.NEAREST), (at + 15, 235), tile.resize((60, 60), Image.NEAREST))

    at += 110

sheet.save(os.path.join(out, 'sheet.png'))

print('wrote sheet.png')
