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
import math
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
# A classic steam outline, which is what the ORIGINAL icon drew and what Adam asked to come back to:
# "the front of the locomotive now appears cut off.  try to give the whole thing a classic steam engine
# icon shape, similar to the old icon / the one on the graph."
#
# It was cut off because it had no front.  The boiler was a rounded rectangle ending at 476, the
# footplate at 484, and the pilot at 484 - three things all stopping within thirty of the canvas edge,
# with nothing rounding them off.  What the old drawing has and this did not is a SMOKEBOX: the boiler
# ends in a dome, and the stack and the steam dome sit on top of it.  Those three shapes are the whole
# reason a side-on black blob reads as a steam engine.
#
# The front is pulled back to 470 as well, so the shape ends inside the drawing rather than against it.
#
# One constraint this outline is not free of: the keyboard PAGE NUMBER is merged over the centre of
# this icon in white, so the middle has to stay solid.  The cab and the boiler between them cover it,
# and `testTheLocomotiveIsSolidWhereThePageNumberSits` is what says so out loud.
image, d = canvas()

# ONE OUTLINE, filled - not a pile of parts (Adam: "you need to update your tracing strategy and
# focus on filling a simple outline instead of recreating each component").
#
# Four versions of this icon were built by stacking primitives: a rounded rectangle for the cab,
# another for the boiler, a polygon for the stack, discs for the wheels. Every one of them came back
# lumpy, and the reason is structural rather than a matter of which parts were chosen. Overlapping
# rounded rectangles do not add up to a profile - they add up to a heap of rectangles, and the eye
# reads the heap. Each fix moved one lump somewhere else.
#
# A silhouette is a single closed path. The points below walk the outline of the engine once, from the
# bottom of the cab up its back, over the roof, down to the boiler, over the dome, up and around the
# stack, over the headlamp, round the smokebox nose, forward along the running board, down the
# cowcatcher and back along the frame. Nothing overlaps anything, so nothing can merge with anything.
#
# The wheels stay separate because they genuinely are - they hang below the frame - and they are
# hollowed, which is the other half of what the reference does: it is a solid shape with white BETWEEN
# its parts, and the hubs are the clearest case of it.
#
# One constraint this outline is not free of: the keyboard PAGE NUMBER is merged over the centre of
# this icon in white, so the middle has to stay solid.
image, d = canvas()

BODY = [
    (26, 326), (26, 289),                                   # frame, at the back
    (36, 289), (36, 99),                                   # up the back of the cab
    (21, 99), (21, 74), (203, 74), (203, 99),             # the cab roof, overhanging both ways
    (185, 99), (185, 184),                                 # down the front of the cab
    (209, 184), (209, 147), (252, 147), (252, 184),         # the steam dome
    (266, 184), (239, 82),                                 # up the flare of the balloon stack
    (237, 82), (237, 62), (352, 62), (352, 82),           # its cap
    (350, 82), (323, 184),                                 # down the other side of the flare
    (354, 184), (354, 133), (408, 133), (408, 184),         # the headlamp
    (415, 187), (425, 210), (426, 235), (425, 261), (415, 284),   # the smokebox nose, rounded
    (419, 289),                                             # forward along the running board
    (480, 413),                                             # ONE slope: its front edge and the plow's
    (376, 413),                                             # the plow's bottom, level with the wheels
    (376, 326),                                             # and straight up its back
]

d.polygon([s(*point) for point in BODY], fill=INK)

# The cowcatcher, settled on the fifth attempt by drawing the candidates and letting Adam name one
# (2026-08-27): "2, with the bottom edge matching the bottom of the small wheel on the y axis, and
# with the angled part starting where the gray starts to angle."
#
# Three constraints, and between them they leave no room for interpretation - which is the point. Four
# earlier goes were built from descriptions that each had two readings ("pointy at the front", "the
# inverse of that", a corner list whose axis direction I had to guess), and I picked wrong every time.
#
# What they describe is not a separate part at all: the running board's front edge and the plow's face
# are ONE line, from where the board starts to angle at 466 down to the bottom of the leading wheel at
# 405. That is why every version of this drawn as its own shape looked stuck on - it was being given a
# top edge that should not exist.
#
# The FACE ANGLE and the vertical back are Adam's, settled by drawing the candidates rather than by
# describing them; what was left was how much flat bottom sits between the two, and he picked 92 out
# of the 147 it had ("imagine cutting off a rectangle from the middle of the trapezoid, and then
# shifting the whole thing back.  the angles are right now, we just need to remove the extra width").
#
# Taken out of the MIDDLE, which is the part worth keeping in mind if it is ever changed again: the
# face slides back as a whole and keeps its rake, where trimming the front corner instead would stand
# it up steeper and lose the shape. So the two points that move are the face's top and bottom, by the
# same amount, and nothing else in the outline changes at all.
#
# The running board shortens with it, because the face's top corner is where the board stops. That is
# the "shifting the whole thing back" half of it, and it is one number rather than two by construction.

# The wheels: two drivers and ONE leading wheel, hung below the frame.
#
# There were two small ones, as there are on the reference - a 4-4-0 has a four-wheel truck, and half
# of it is hidden by the other half from the side anyway.  At thirty pixels the pair merged into a
# single oval and read as one badly drawn wheel, which is worse than the honest simplification.
# The leading wheel is pulled BACK from where it was, to leave the cowcatcher its own room: at 376 it
# ran under the plow's back edge, and two filled shapes overlapping is how every earlier version of
# this icon turned into a lump.
WHEELS = ((65, 302, 177, 415, 32), (191, 302, 304, 415, 32),
          (304, 352, 365, 413, 18))

for left, top, right, bottom, hub in WHEELS:
    d.ellipse(s(left, top, right, bottom), fill=INK)

# Solid, not hollowed (Adam, 2026-08-27: "fill in the wheels").
#
# They were rimmed with a punched hub, and that was the change that finally made this icon read - but
# it read as a wheel because the OUTLINE was right by then, not because of the hole. With the body
# traced as one path there is already a clear edge where each wheel meets the frame, so the hubs were
# doing nothing except adding three more small features to a drawing thirty pixels tall.
#
# The `hub` figure stays in the table rather than being deleted: it is the one number to reach for if
# they are ever wanted back.

# The rail it stands on.
d.rounded_rectangle(s(14, 423, 498, 450), radius=8 * SS, fill=INK)

# Cab window, punched out - kept hard left of the middle, where the page number goes.
d.rounded_rectangle(s(55, 121, 131, 196), radius=10 * SS, fill=HOLE)

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

MARGIN, HALF = 38 * SS, 24 * SS

FIRST = 42 * SS + MARGIN + HALF
LAST = BIG - 42 * SS - MARGIN - HALF

SLEEPERS = tuple(FIRST + (LAST - FIRST) * n // 3 for n in range(4))

# A little larger than the last pass (Adam: "looks good now, make it slightly bigger"), taken off the
# margins rather than out of the proportions: RAIL_PAD replaces PAD here and the sleepers reach nearer
# the sides, so the drawing grows without the rails getting heavier relative to the sleepers.
RAIL_PAD, SIDE = 42 * SS, 60 * SS

for x in (148, 364):
    d.rounded_rectangle((x * SS - STROKE // 2, RAIL_PAD, x * SS + STROKE // 2, BIG - RAIL_PAD),
        radius=12 * SS, fill=INK)

for y in SLEEPERS:
    d.rounded_rectangle((SIDE, y - HALF, BIG - SIDE, y + HALF), radius=10 * SS, fill=INK)

save(image, 'track')

# ---------------------------------------------------------------- autonomy
#
# A play triangle.  It was four linked nodes - the autonomy GRAPH - and Adam's answer to that was
# "the graph one doesn't make sense (make it be a play icon)".  He is right twice over: the graph is
# the thing this release deprecated, and what the tab does is START the railway running.
image, d = canvas()

# Centred on the canvas, then nudged a little right: a triangle's weight sits behind its point, so a
# play symbol whose bounding box is exactly centred reads as leaning left.
# A touch smaller than the last pass (Adam: "looks good, make it slightly smaller"), shrunk about its
# own centre so the deliberate 110/90 lean survives the change.
PLAY = [s(124, 82), s(124, 430), s(407, 256)]

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
# Drawn as two nested triangles rather than as a stroked outline.
#
# The stroked version left a nub at the top corner - the point where the closed path met itself, which
# `joint='curve'` rounds but does not remove, and which at thirty pixels is a stray pixel hanging off
# the corner.  Filling a hole and then filling the middle of it back in has no joints at all, because
# nothing is being stroked.
CENTRE = (sum(p[0] for p in PLAY) / 3.0, sum(p[1] for p in PLAY) / 3.0)


def scaled(factor):
    return [(CENTRE[0] + (x - CENTRE[0]) * factor, CENTRE[1] + (y - CENTRE[1]) * factor)
            for x, y in PLAY]


d.polygon(scaled(0.74), fill=HOLE)
d.polygon(scaled(0.60), fill=INK)

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

POST = 201                              # the mast's centre line

# A little larger than the last pass (Adam: "make it slightly larger"), and with room kept on the
# right for the movement arrow below.
d.rounded_rectangle(s(POST - 33, 88, POST + 33, 443), radius=16 * SS, fill=INK)      # post
d.ellipse(s(POST - 47, 37, POST + 47, 114), fill=INK)                               # finial
d.rounded_rectangle(s(POST - 103, 439, POST + 103, 486), radius=18 * SS, fill=INK)   # base

# The arm, RAISED 40 degrees (Adam: "angle the signal arm 40 degrees higher").
#
# Which is what an upper-quadrant semaphore does: horizontal is danger, and the arm rises to clear.
# Drawn at clear, with the arrow below showing the way back down - so the icon carries both positions
# at once, which a horizontal arm and a downward arrow did not: that pair said the arm falls BELOW
# horizontal, which no upper-quadrant signal does.
#
# Everything from here is computed about the pivot rather than typed as corners, because a rotated
# rectangle has no axis-aligned coordinates to type.
ARM_ANGLE = -40                         # degrees; negative is up, since y grows downwards
ARM_LENGTH = 242
ARM_DEPTH = 90

PIVOT = (POST, 216)

rad = math.radians(ARM_ANGLE)

along = (math.cos(rad), math.sin(rad))
across = (-math.sin(rad), math.cos(rad))


def on_arm(distance, offset):
    """A point `distance` along the arm from the pivot and `offset` across it."""
    return (PIVOT[0] + along[0] * distance + across[0] * offset,
            PIVOT[1] + along[1] * distance + across[1] * offset)


def bar(start_at, end_at, half):
    """The four corners of a rectangle lying along the arm."""
    return [s(*on_arm(start_at, -half)), s(*on_arm(end_at, -half)),
            s(*on_arm(end_at, half)), s(*on_arm(start_at, half))]


# Starting AHEAD of the pivot, not behind it (Adam: "move the signal arm slightly to the right, so
# it doesn't extend beyond the vertical stem").
#
# It began at -24 so that it would tuck into the post, but the arm is 90 deep and angled: its rear top
# corner came out 45 to the left of the pivot, which is past the post's own edge, and the arm looked
# hung on the wrong side of its mast.
d.polygon(bar(18, ARM_LENGTH, ARM_DEPTH // 2), fill=INK)

# The stripe near the tip, and the spectacle where the arm meets the post.  A signal arm ends square
# and carries a stripe; that pair is what says "signal" rather than "flag".
d.polygon(bar(ARM_LENGTH - 62, ARM_LENGTH - 34, ARM_DEPTH // 2 - 14), fill=HOLE)

lens = on_arm(56, 0)

d.ellipse(s(lens[0] - 34, lens[1] - 34, lens[0] + 34, lens[1] + 34), fill=HOLE)

# WHICH WAY IT MOVES, struck about the pivot from TWO THIRDS along the arm (Adam: "move the arrow to
# start from 2/3 up the arm and point down as currently").
#
# On the arc the tip of the arm actually travels, so it begins on the arm itself and sweeps down
# through where the arm will be.  Starting at two thirds keeps it inside the arm's own reach, which is
# what stops it reading as a second, shorter arm.
RADIUS = ARM_LENGTH * 2 // 3

box = s(PIVOT[0] - RADIUS, PIVOT[1] - RADIUS, PIVOT[0] + RADIUS, PIVOT[1] + RADIUS)

# ONE WIDTH the whole way (Adam: "align the arrow so it has a consistent width but is arched, as
# before").  The band and the head are both derived from BAND, so the head cannot drift out of
# proportion with the curve it sits on the end of.
BAND = 26

d.arc(box, ARM_ANGLE + 4, 30, fill=INK, width=BAND * SS)


def at(degrees, radius):
    return (PIVOT[0] + radius * math.cos(math.radians(degrees)),
            PIVOT[1] + radius * math.sin(math.radians(degrees)))


# The head: a triangle standing on the arc, its base square across the curve and its point carrying on
# round it, so the two read as one stroke that happens to end in an arrow.
HEAD = 22                               # degrees of sweep the head occupies

tip = at(30 + HEAD, RADIUS)
far = at(30, RADIUS + BAND * 1.7)
near = at(30, RADIUS - BAND * 1.7)

d.polygon([s(*tip), s(*far), s(*near)], fill=INK)

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

# A SYMMETRICAL S (Adam: "this is the problematic one - the arrow now touches the middle.  I wanted a
# symmetrical S, now it isn't an S any more").
#
# He is right, and the previous pass caused it.  Making the two arms equal fixed the wrong measurement:
# the ARMS matched, but each arm ends in something - a dot at the bottom, an arrowhead at the top - and
# the arrowhead was 126 long against the dot's 108 diameter, and it ate its arm from the outside in.
# The head started 30 past the turn, so there was almost no stroke between the corner and the point,
# and the top of the S was a corner with an arrow stuck to it.
#
# What has to match for this to read as an S is not the arms but the STROKE either side of the turn -
# the part that is actually a line.  So it is measured from the turn to where the terminal begins:
#
#     RUN         the visible line, the same going out as coming in
#     TERMINAL    how much the dot or the head then adds beyond it
#
# and the dot's radius and the head's length are both set from TERMINAL, so neither can grow into its
# own arm again.
TURN_X, LOW_Y, HIGH_Y = 243, 384, 154

RUN, TERMINAL = 70, 104

# The arrow's arm runs a little longer than the dot's (Adam: "stretch the arm with the arrow
# slightly").
#
# An optical correction rather than a change of mind about the symmetry.  The two ends carry different
# weight - a solid disc reads as a full stop, an arrowhead reads as a continuation - so arms that are
# equal by measurement look unequal, with the arrow end the shorter of the two.
TOP_RUN = RUN + 26

d.line([s(TURN_X - RUN - TERMINAL // 2, LOW_Y), s(TURN_X, LOW_Y)], fill=INK, width=WIDE)
d.line([s(TURN_X, LOW_Y), s(TURN_X, HIGH_Y)], fill=INK, width=WIDE)
d.line([s(TURN_X, HIGH_Y), s(TURN_X + TOP_RUN, HIGH_Y)], fill=INK, width=WIDE)

# Rounded corners, so the turns do not come out mitred into points.
for corner in (s(TURN_X, LOW_Y), s(TURN_X, HIGH_Y)):
    d.ellipse((corner[0] - WIDE // 2, corner[1] - WIDE // 2,
               corner[0] + WIDE // 2, corner[1] + WIDE // 2), fill=INK)

# Where it starts: a dot whose OUTER edge is TERMINAL past the end of the run.
DOT = TERMINAL // 2

d.ellipse(s(TURN_X - RUN - TERMINAL, LOW_Y - DOT, TURN_X - RUN, LOW_Y + DOT), fill=INK)

# Where it ends: a head of the same reach, so the two ends balance and neither reaches the turn.
d.polygon([s(TURN_X + TOP_RUN, HIGH_Y - 78), s(TURN_X + TOP_RUN, HIGH_Y + 78),
           s(TURN_X + TOP_RUN + TERMINAL, HIGH_Y)], fill=INK)

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
