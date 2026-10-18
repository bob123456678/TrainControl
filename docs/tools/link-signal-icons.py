# -*- coding: utf-8 -*-
"""The page link and signal tiles, drawn from geometry rather than retouched (Adam, 2026-10-18).

On the link: "Propose pretiffications for the link icon, such as to make the arrow sharper / different / more
proportional to the chosen size", then of the options drawn for him "I like the F idea, but make it with an all black
arrow without the gray dash", then "G is good, but make sure you match the edges of the signal tile.  does that mean we
should sharpen the signal tile, if it hasn't been sharpened yet?", and "Build G and the sharpened signals now".

Like `tile-icons.py`: every shape is laid out in a 30-unit square and sampled 16 times finer, each sample tested at its
CENTRE, then averaged down to 30 and 60 pixels - so the two sizes are the same drawing.  What is NOT drawn here is the
track's band: it is the shipped straight tile's own, row for row at each size (its 1-pixel edge is #333 at 30 and #666
at 60, which is not one drawing scaled), so a link or a signal meets the straight beside it with no seam.

  - the link (G): a black arrow - the band, then a head 16 units tall from 8 to 27 across - in a frame 1 unit thick in
    #787878, open on the west where the track comes in (rows 9 to 21)
  - the signal: a black capsule (6 to 24 across, 1 to 10 down, its ends round) on a mast (a bar 2 units thick to a post
    from 28 to 30), its lamp a circle of radius 3.7 in one end of the capsule - green in the east end for signal.gif,
    red in the west end for signal_active.gif, the two lamps of a signal head, as the shipped art had them - and the
    aspect bar in the band in the lamp's colour, 15 by 4 units, centred on the square

Every edge that can fall on a whole unit does, so it is a whole pixel at both sizes; the lamp, the capsule's round ends
and the arrow's slopes are the only partly covered pixels.

Run from the project folder, to rewrite the icons in place (needs PIL and numpy):

    python docs/tools/link-signal-icons.py src/org/traincontrol/gui/resources
"""
import math
import sys

import numpy as np
from PIL import Image

RES = sys.argv[1]
SS = 16

BLACK, WHITE, FRAME = (0, 0, 0), (255, 255, 255), (120, 120, 120)
GREEN, RED = (0, 255, 0), (255, 0, 0)


def convex(points):
    """A convex polygon, as a test over sample centres."""
    pts = list(points)
    area = sum(pts[i][0] * pts[(i + 1) % len(pts)][1] - pts[(i + 1) % len(pts)][0] * pts[i][1] for i in range(len(pts)))
    if area < 0:
        pts = pts[::-1]

    def test(x, y):
        inside = np.ones_like(x, dtype=bool)
        for i in range(len(pts)):
            (x1, y1), (x2, y2) = pts[i], pts[(i + 1) % len(pts)]
            inside &= (x2 - x1) * (y - y1) - (y2 - y1) * (x - x1) >= -1e-9
        return inside
    return test


def rect(x0, y0, x1, y1):
    return convex([(x0, y0), (x1, y0), (x1, y1), (x0, y1)])


def circle(cx, cy, r):
    return lambda x, y: (x - cx) ** 2 + (y - cy) ** 2 <= r * r


def union(*tests):
    def t(x, y):
        m = np.zeros_like(x, dtype=bool)
        for f in tests:
            m |= f(x, y)
        return m
    return t


def minus(a, b):
    return lambda x, y: a(x, y) & ~b(x, y)


def render(layers, size, base=None):
    """The layers painted in order over `base` (or white), sampled at centres and averaged down."""
    n = size * SS
    c = (np.arange(n) + 0.5) / n * 30.0
    x, y = np.meshgrid(c, c)
    if base is None:
        img = np.empty((n, n, 3), dtype=float)
        img[:] = WHITE
    else:
        # nearest-neighbour up, so every base pixel averages back to itself where nothing is drawn over it
        img = np.asarray(base.convert('RGB'), dtype=float).repeat(SS, axis=0).repeat(SS, axis=1)
    for test, colour in layers:
        img[test(x, y)] = colour
    img = img.reshape(size, SS, size, SS, 3).mean(axis=(1, 3))
    return Image.fromarray(np.clip(np.round(img), 0, 255).astype(np.uint8), 'RGB')


def straight(size):
    return Image.open('%s/icons%d/straight.gif' % (RES, size)).convert('RGB')


def band(size):
    """The straight tile with its dash painted out: the band, edges and all, the straight's own."""
    im = straight(size)
    px = im.load()
    for yy in range(size):
        if px[0, yy] != WHITE:
            fill = px[0, yy]
            for xx in range(size):
                px[xx, yy] = fill
    return im


def link(size):
    outer = rect(3, 0, 30, 30)
    inner = rect(4, 1, 29, 29)
    mouth = rect(2.99, 9, 4.01, 21)
    frame = minus(minus(outer, inner), mouth)
    head = convex([(8, 7), (27, 15), (8, 23)])
    im = render([(frame, FRAME), (head, BLACK)], size)

    # THE SHAFT IS THE STRAIGHT'S BAND, up to the head's base: the track runs in with its own edges
    track = band(size)
    shaft_end = int(round(8 * size / 30.0))
    for xx in range(shaft_end):
        for yy in range(size):
            if track.getpixel((xx, yy)) != WHITE:
                im.putpixel((xx, yy), track.getpixel((xx, yy)))
    return im


def signal(size, lamp, lamp_x):
    capsule = union(rect(10.5, 1, 19.5, 10), circle(10.5, 5.5, 4.5), circle(19.5, 5.5, 4.5))
    arm = rect(24, 5, 28, 7)
    post = rect(28, 1, 30, 10)
    light = circle(lamp_x, 5.5, 3.7)
    bar = rect(7.5, 13, 22.5, 17)
    return render([(capsule, BLACK), (arm, BLACK), (post, BLACK), (light, lamp), (bar, lamp)], size, band(size))


def save(im, path):
    """Exact colours: an indexed palette of exactly the colours drawn, so nothing is dithered or quantised."""
    pixels = list(im.getdata()) if not hasattr(im, 'get_flattened_data') else list(im.get_flattened_data())
    colours = sorted(set(pixels))
    assert len(colours) <= 256, path
    index = {c: i for i, c in enumerate(colours)}
    flat = [v for c in colours for v in c] + [0] * (768 - 3 * len(colours))
    out = Image.new('P', im.size)
    out.putdata([index[p] for p in pixels])
    out.putpalette(flat)
    out.save(path)


if __name__ == '__main__':
    for size in (30, 60):
        save(link(size), '%s/icons%d/link.gif' % (RES, size))
        save(signal(size, GREEN, 19.5), '%s/icons%d/signal.gif' % (RES, size))
        save(signal(size, RED, 10.5), '%s/icons%d/signal_active.gif' % (RES, size))
    print('link.gif, signal.gif, signal_active.gif at 30 and 60')
