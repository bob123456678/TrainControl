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

d.rounded_rectangle(s(40, 74, 300, 306), radius=28 * SS, fill=INK)       # cab
d.rounded_rectangle(s(250, 136, 476, 306), radius=48 * SS, fill=INK)     # boiler
d.rounded_rectangle(s(28, 306, 484, 358), radius=18 * SS, fill=INK)      # footplate
d.rounded_rectangle(s(388, 40, 452, 146), radius=14 * SS, fill=INK)      # chimney

d.ellipse(s(60, 338, 196, 474), fill=INK)                               # driver
d.ellipse(s(212, 338, 348, 474), fill=INK)                              # driver

# The plow (Adam: "locomotive icon needs a plow at the front").
#
# It hangs from the UNDERSIDE of the footplate at the front - the right, the end the chimney is on -
# with its top edge exactly on the footplate's bottom edge so the two read as one casting, and its
# deepest point at the front, level with the bottom of the wheels.
#
# Two attempts to get here.  The first was a wedge beside the wheels whose top sat at the footplate's
# TOP, touching nothing, and it read as a fin bolted to the engine.  The second hung correctly but
# tapered to a point that hung BACKWARDS, which at thirty pixels is a tail.  A pilot gets deeper
# towards the front - that is the whole shape of it - so the deep end has to be the leading one.
#
# It also takes the place of the pony truck.  Three round wheels and a blade inside thirty pixels is
# more than the silhouette can hold, and the pony was the least of them.
d.polygon([s(376, 356), s(484, 356), s(484, 466)], fill=INK)

# Cab window, punched out - small, and kept hard left of the middle.
#
# The number's box reaches in to about x=153 once the icon is 30 pixels wide, and a hole under a white
# digit is the thing this whole drawing was rearranged to avoid.
d.rounded_rectangle(s(66, 100, 148, 186), radius=14 * SS, fill=HOLE)

save(image, 'loc')

# ---------------------------------------------------------------- track
#
# Rail showing past the sleepers at BOTH ends, and no sleeper at either end.
#
# The first version ran 64 past the top sleeper and 8 past the bottom, which is the lopsidedness Adam
# saw.  The second answered it by putting a sleeper hard at each end, which made it even and also made
# it a ladder: track that stops dead at the top and bottom of the icon.  What he actually asked for is
# the other way round - "no line on top or bottom, some track should protrude on both sides" - so the
# rails run to the edge of the drawing and the sleepers stop short of it, which is what track does.
#
# Worked out rather than typed, so the two margins cannot drift apart again: four sleepers, 40 of bare
# rail at each end.
image, d = canvas()

MARGIN, HALF = 40 * SS, 22 * SS

FIRST = PAD + MARGIN + HALF
LAST = BIG - PAD - MARGIN - HALF

SLEEPERS = tuple(FIRST + (LAST - FIRST) * n // 3 for n in range(4))

for x in (150, 362):
    d.rounded_rectangle((x * SS - STROKE // 2, PAD, x * SS + STROKE // 2, BIG - PAD),
        radius=12 * SS, fill=INK)

for y in SLEEPERS:
    d.rounded_rectangle((72 * SS, y - HALF, BIG - 72 * SS, y + HALF), radius=10 * SS, fill=INK)

save(image, 'track')

# ---------------------------------------------------------------- autonomy
#
# A play triangle.  It was four linked nodes - the autonomy GRAPH - and Adam's answer to that was
# "the graph one doesn't make sense (make it be a play icon)".  He is right twice over: the graph is
# the thing this release deprecated, and what the tab does is START the railway running.
image, d = canvas()

# Centred on the canvas, then nudged a little right: a triangle's weight sits behind its point, so a
# play symbol whose bounding box is exactly centred reads as leaning left.
PLAY = [s(110, 64), s(110, 448), s(422, 256)]

d.polygon(PLAY, fill=INK)

# A white inner keyline (Adam: "maybe add a white inner outline for a nicer look").  This one only,
# for now, by his ruling - so it is deliberately the odd one out until he says otherwise.
#
# "White" is really a HOLE: these icons are one flat colour on transparency, and punching the line out
# lets the tab strip through, which on the only theme this application has is white.  Painting actual
# white pixels would put a second colour in the file and fail the flat-colour test - correctly, since
# it would then be wrong on any other background.
#
# The width is not a taste decision.  At thirty pixels the whole icon is thirty pixels, so a keyline
# thinner than about 24 here lands under one device pixel and either vanishes or shimmers as the
# scaler rounds it.
CENTRE = (sum(p[0] for p in PLAY) / 3.0, sum(p[1] for p in PLAY) / 3.0)

INSET = [(CENTRE[0] + (x - CENTRE[0]) * 0.68, CENTRE[1] + (y - CENTRE[1]) * 0.68) for x, y in PLAY]

d.line(INSET + [INSET[0]], fill=HOLE, width=24 * SS, joint='curve')

save(image, 'autonomy')

# ---------------------------------------------------------------- signal
#
# A SEMAPHORE now (Adam: "signal still not symmetrical.  convert to old fashioned style wing signal
# icon").
#
# He was right about the colour-light version twice over.  Its head was centred on its mast, so the
# outline looked balanced and the measurement agreed - but the two lamps inside it were not: 40 of head
# above the upper one, 12 below the lower.  That is the kind of wrong that is easier to see than to
# name, which is roughly how he put it both times.
#
# A wing signal is asymmetric by nature - the arm is on one side of the post - so this is centred by
# its BOUNDING BOX instead, and everything that can be balanced is: the arm's two ends, the spectacle,
# the finial over the post and the base under it.
image, d = canvas()

POST = 186                              # the mast's centre line

d.rounded_rectangle(s(POST - 26, 101, POST + 26, 425), radius=14 * SS, fill=INK)     # post
d.ellipse(s(POST - 38, 47, POST + 38, 123), fill=INK)                               # finial
d.rounded_rectangle(s(POST - 84, 421, POST + 84, 465), radius=16 * SS, fill=INK)     # base

# The arm, horizontal - which on a semaphore is DANGER, and is the aspect worth drawing: a signal at
# clear is a signal nobody needs to look at.
#
# SHORT and DEEP.  The first version was 200 long and 64 deep, and at thirty pixels that is twelve by
# four - the proportions of a pennant, so it read as a flag on a pole and not as a signal at all. An
# arm that is nearer half its length in depth cannot be mistaken for cloth.
ARM_TOP, ARM_BOTTOM = 143, 227

d.rounded_rectangle(s(POST - 12, ARM_TOP, 410, ARM_BOTTOM), radius=12 * SS, fill=INK)

# SQUARE at the outer end, and striped.
#
# The version before this had a fishtail notch cut into the far end, on the reasoning that a notch is
# what distinguishes a semaphore from a plain bar.  It is - on a distant signal, at full size.  At
# thirty pixels a tapering end is a pennant, and the whole icon read as a flag on a pole for the second
# time running.  A signal arm ends square and carries a stripe across it near the tip; that pair is
# what says "signal", and neither of them tapers.
d.rounded_rectangle(s(360, ARM_TOP + 12, 386, ARM_BOTTOM - 12), radius=8 * SS, fill=HOLE)

# The spectacle - the round lens where the arm meets the post - punched out so the arm reads as an
# arm, and large enough to survive the scale down as a hole rather than a smudge.
d.ellipse(s(POST + 26, ARM_TOP + 14, POST + 96, ARM_BOTTOM - 14), fill=HOLE)

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

# EQUAL ARMS (Adam: "good idea, make it symmetrical by stretching the top arm").
#
# The top arm was 90 long against the bottom's 188, because most of its length had been given to the
# arrowhead - so the shape was an S with one stroke of it missing.  Both arms are the same run now,
# measured the same way: from the turn to the thing that ends it, the dot at one end and the point of
# the arrow at the other.
#
# Written as one number so they cannot come apart again.
TURN_X, LOW_Y, HIGH_Y = 283, 400, 152
ARM = 156

START_X = TURN_X - ARM
TIP_X = TURN_X + ARM

d.line([s(START_X, LOW_Y), s(TURN_X, LOW_Y)], fill=INK, width=WIDE)
d.line([s(TURN_X, LOW_Y), s(TURN_X, HIGH_Y)], fill=INK, width=WIDE)
d.line([s(TURN_X, HIGH_Y), s(TIP_X - 74, HIGH_Y)], fill=INK, width=WIDE)

# Rounded corners, so the turns do not come out mitred into points.
for corner in (s(TURN_X, LOW_Y), s(TURN_X, HIGH_Y)):
    d.ellipse((corner[0] - WIDE // 2, corner[1] - WIDE // 2,
               corner[0] + WIDE // 2, corner[1] + WIDE // 2), fill=INK)

# Where it starts.
d.ellipse(s(START_X - 54, LOW_Y - 54, START_X + 54, LOW_Y + 54), fill=INK)

# Where it ends.
d.polygon([s(TIP_X - 126, HIGH_Y - 94), s(TIP_X - 126, HIGH_Y + 94), s(TIP_X, HIGH_Y)], fill=INK)

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
