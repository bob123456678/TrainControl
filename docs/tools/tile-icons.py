# -*- coding: utf-8 -*-
"""The curve, switch, crossing and sensor tiles, drawn from geometry rather than retouched (Adam, 2026-10-09: "make a
pass on the curve and switch tile to make sure they are symmetrical.  It looks like there are some pixel artifacts, as
they were made in raster programs", then "extend it to the others too, like the 4-way", then "Put the new icons in").

Every tile is laid out in a 30-unit square and drawn 16 times finer, each sample tested at its CENTRE against the
shape (inclusive on every edge), then averaged down to 30 and 60 pixels.  So every edge is exact coverage, and a shape
that is symmetric in its layout is symmetric in its pixels - `ui.testDiagramLooksRight
.testTheCurveAndSwitchTilesAreMirrorImages` holds them to it.  (PIL's own polygon fill takes the right and bottom
edges and not the left and top, which leaves a one-sided grey on every edge; that is why this samples for itself.)

The geometry is the originals' own, measured off them:
  - a straight leg is the straight tile itself, pixel for pixel, so a switch's straight meets the straight beside it;
    the straight tile is not drawn here, it is the reference
  - a diverging leg or a curve is the 45-degree chord between two side midpoints, the band |x + y - 45| <= 4 for the
    S-E curve: its cross-section at the tile's edge is the straight's 8 units, so it meets a neighbour flush
  - a chord's grey dash is centred on the chord, 13 by 2.8 units
  - the crossing is the straight both ways, the darker where they overlap, its dash a plus that stays light through
    the middle; the crossing switch adds the north-west and south-east chords
  - a sensor is a ring on the track's middle, outer radius 6.25 and a pixel and a half thick at 30 pixels (thinner
    comes out grey); set, it is yellow, with two red arrows that are mirror images of each other, a curve's farther
    out (`CURVE_ARROWS_OUT`), each stopping `EDGE_CLEAR` short of the tile's edge
  - the buffer stop is the straight's band, without its dash, down to a bar as wide as the dash is long
  - the uncoupler is the straight with a hump on its north edge, flat from 4.5 to 25.5 at 9 units and down at 45
    degrees to the band, edged as the band is
  - the turntable (D192) is a pit of radius 9 edged a unit thick, its bridge west to east with the straight's own
    dash cut to 12 long (as high as the straight's, MT-1071), and eight tracks out of it to the edge: the straight both
    ways, so they meet it with no seam, and diagonals 4.4 across

At 120 pixels each tile is its 60 drawn twice as large, as PNG: the straight is the 60 doubled, and the samples are
the 60's own (8 a pixel), so a 120 averaged 2 by 2 is its 60.  The tunnel and overpass, drawn by hand at 30 and 60, are
their 60 doubled with the arch and the parapets' slopes drawn from the 60's geometry; the lamp and route, which have
none, are their 60 scaled up with Lanczos.  Then run `link-signal-icons.py`, which reads the 120 straight.

Run from the project folder, to rewrite the icons in place (needs PIL and numpy):

    py -3.10 docs/tools/tile-icons.py src/org/traincontrol/gui/resources src/org/traincontrol/gui/resources
"""
import math
import os
import sys

import numpy as np
from PIL import Image

SRC, OUT = sys.argv[1], sys.argv[2]
SS = {30: 16, 60: 16, 120: 8}

BLACK, DASH, IDLE, WHITE, PIT = (0, 0, 0), (153, 153, 153), (204, 204, 204), (255, 255, 255), (230, 230, 230)
YELLOW, RED = (255, 255, 0), (230, 0, 0)

# How much farther from its contact a curved sensor's arrows stand than a straight's, in units, by pixel size: clear of
# a locomotive parked there, turned along the rail over the contact, and its halo (OB-400).  Wider at the small size
# for its size, because the halo is never under 2 pixels there.
CURVE_ARROWS_OUT = {30: 6.35, 60: 5.5, 120: 5.5}

# How far short of the tile's edge an arrow's tail stops, in units: a pixel at 30 and two at 60, which Medium (the 60
# scaled to 46) keeps clear too, so the arrows of two sensor tracks side by side never touch (Adam: "there should be a
# 1px gap").
EDGE_CLEAR = 1.0

MID = {"N": (15.0, 0.0), "S": (15.0, 30.0), "E": (30.0, 15.0), "W": (0.0, 15.0)}


def chord_band(a, b):
    """The band between two adjacent sides' midpoints: a parallelogram whose ends lie along the tile's edges."""
    pts = []
    for side in (a, b):
        x, y = MID[side]
        if side in "NS":
            pts.append([(x - 4, y), (x + 4, y)])
        else:
            pts.append([(x, y - 4), (x, y + 4)])
    corners = pts[0] + pts[1]
    # round the centre by angle: the four corners of a parallelogram, in order
    cx = sum(p[0] for p in corners) / 4.0
    cy = sum(p[1] for p in corners) / 4.0
    return sorted(corners, key=lambda p: math.atan2(p[1] - cy, p[0] - cx))


def rot_rect(cx, cy, ux, uy, half_len, half_dep):
    vx, vy = -uy, ux
    return [(cx + ux * half_len * s + vx * half_dep * t, cy + uy * half_len * s + vy * half_dep * t)
            for s, t in ((-1, -1), (1, -1), (1, 1), (-1, 1))]


def straight_image(size):
    """The straight tile, the reference every band matches: at 120, the 60 doubled."""
    if size == 120:
        return straight_image(60).resize((120, 120), Image.Resampling.NEAREST)
    return Image.open(os.path.join(SRC, "icons%d" % size, "straight.gif")).convert("RGB")


def straight_parts(size):
    """The straight tile at this size, as numbers: the picture, its band's first and last rows, the band with its dash
    painted out, and the dash itself (a box, and which of its pixels are its light core rather than its soft rim)."""
    a = np.asarray(straight_image(size).convert("L")).astype(int)
    rows = [y for y in range(size) if a[y, 0] != 255]
    lo, hi = rows[0], rows[-1]
    inner = a[lo + 1:hi, :]
    ys, xs = np.nonzero(inner)
    box = (lo + 1 + ys.min(), lo + 1 + ys.max() + 1, xs.min(), xs.max() + 1)
    band = a.copy()
    band[box[0]:box[1], box[2]:box[3]] = 0
    dash = a[box[0]:box[1], box[2]:box[3]]
    return a, lo, hi, band, box, dash


def cross_pixels(size):
    """The crossing: the straight's band both ways (the darker of the two where they overlap, so no edge line runs
    through the middle), and its dash both ways as a plus that stays light right through the centre."""
    a, lo, hi, band, (r0, r1, c0, c1), dash = straight_parts(size)
    out = np.minimum(band, band.T)
    core = np.zeros(out.shape, bool)
    rim = np.full(out.shape, 255)
    for (y0, y1, x0, x1), d in (((r0, r1, c0, c1), dash), ((c0, c1, r0, r1), dash.T)):
        core[y0:y1, x0:x1] |= d == 153
        rim[y0:y1, x0:x1] = np.minimum(rim[y0:y1, x0:x1], np.where(d == 153, 255, d))
    out = np.where(rim < 255, rim, out)
    out = np.where(core, 153, out)
    return out


def end_pixels(size):
    """The buffer stop: the straight's band, without its dash, coming down to a bar as wide as the dash is long, both
    edged in the straight's own edge grey."""
    a, lo, hi, band, (r0, r1, c0, c1), dash = straight_parts(size)
    edge = a[lo, 0]
    out = np.full(a.shape, 255)
    bar = out.copy()
    bar[lo:hi + 1, c0:c1] = edge
    bar[lo + 1:hi, c0 + 1:c1 - 1] = 0
    stub = band.T.copy()
    stub[lo + 2:, :] = 255
    return np.minimum(bar, stub)


class Tile:
    """A tile drawn at 16 samples a pixel each way, by testing each sample's CENTRE against the shape - inclusive on
    every edge, so a shape and its mirror image cover mirror-image samples (PIL's polygon fill takes the right and
    bottom edges and not the left and top, which left a one-sided grey on every edge)."""

    def __init__(self, size):
        self.size, self.ss = size, SS[size]
        n = size * self.ss
        self.k = size / 30.0 * self.ss
        c = (np.arange(n) + 0.5) / self.k
        self.X, self.Y = np.meshgrid(c, c)
        self.rgb = np.full((n, n, 3), 255.0)

    def _fill(self, mask, fill):
        self.rgb[mask] = fill

    def poly(self, pts, fill):
        """A CONVEX polygon: inside every edge's half-plane, edges on the inside."""
        area = sum(pts[i][0] * pts[(i + 1) % len(pts)][1] - pts[(i + 1) % len(pts)][0] * pts[i][1] for i in range(len(pts)))
        if area < 0:
            pts = pts[::-1]
        mask = np.ones(self.X.shape, bool)
        for i in range(len(pts)):
            (x1, y1), (x2, y2) = pts[i], pts[(i + 1) % len(pts)]
            mask &= (x2 - x1) * (self.Y - y1) - (y2 - y1) * (self.X - x1) >= -1e-9
        self._fill(mask, fill)

    def rect(self, x0, y0, x1, y1, fill):
        self.poly([(x0, y0), (x1, y0), (x1, y1), (x0, y1)], fill)

    def ellipse(self, cx, cy, r, fill):
        self._fill((self.X - cx) ** 2 + (self.Y - cy) ** 2 <= r * r + 1e-9, fill)

    def chord(self, a, b, active):
        self.poly(chord_band(a, b), BLACK if active else IDLE)
        if active:
            (ax, ay), (bx, by) = MID[a], MID[b]
            L = math.hypot(bx - ax, by - ay)
            self.poly(rot_rect((ax + bx) / 2, (ay + by) / 2, (bx - ax) / L, (by - ay) / L, 6.5, 1.4), DASH)

    def straight(self, vertical, active):
        """The straight tile's own band, or its outline in the idle grey."""
        src = straight_image(self.size)
        rows = [y for y in range(self.size) if src.getpixel((0, y)) != WHITE]
        lo, hi = rows[0], rows[-1] + 1
        u = 30.0 / self.size
        if not active:
            if vertical:
                self.rect(lo * u, 0, hi * u, 30, IDLE)
            else:
                self.rect(0, lo * u, 30, hi * u, IDLE)
            return
        band = src.crop((0, lo, self.size, hi))
        if vertical:
            band = band.transpose(Image.Transpose.ROTATE_90)
        big = np.asarray(band.resize((band.width * self.ss, band.height * self.ss), Image.Resampling.NEAREST), dtype=float)
        x0, y0 = ((lo * self.ss, 0) if vertical else (0, lo * self.ss))
        self.rgb[y0:y0 + big.shape[0], x0:x0 + big.shape[1]] = big

    def sensor(self, a, b, set_):
        (ax, ay), (bx, by) = MID[a], MID[b]
        cx, cy = (ax + bx) / 2, (ay + by) / 2
        L = math.hypot(bx - ax, by - ay)
        ux, uy = (bx - ax) / L, (by - ay) / L
        # the contact plates either side along the track, then the ring
        for s in (-1, 1):
            self.poly(rot_rect(cx + ux * s * 7.3, cy + uy * s * 7.3, ux, uy, 0.45, 1.4), DASH)
        self.ellipse(cx, cy, 6.25, BLACK)
        self.ellipse(cx, cy, 4.7, YELLOW if set_ else WHITE)
        if set_:
            # two arrows at the sensor, mirror images across the curve's axis (the line through the corner and the
            # sensor): one from the top edge pointing down, one from the side pointing across, both out toward the edge
            tip = 6.25 + 0.9 + CURVE_ARROWS_OUT[self.size]
            tail = max(-tip - 9.0, EDGE_CLEAR - cy)
            shaft = [(-1.0, tail), (1.0, tail), (1.0, -tip - 3.4), (-1.0, -tip - 3.4)]
            head = [(-2.6, -tip - 3.6), (2.6, -tip - 3.6), (0.0, -tip)]
            for part in (shaft, head):
                self.poly([(cx + x, cy + y) for x, y in part], RED)
                self.poly([(cx + y, cy + x) for x, y in part], RED)

    def paste_final(self, grey, idle=False):
        """Lays a picture drawn at this tile's own size over the canvas, wherever it is not white - in the idle grey
        instead, for a leg that is not set."""
        big = np.kron(grey, np.ones((self.ss, self.ss), int))
        mask = big < 255
        if idle:
            self.rgb[mask] = IDLE
        else:
            self.rgb[mask] = np.stack([big[mask]] * 3, axis=-1)

    def straight_sensor(self, set_):
        """A sensor on straight track: the ring over the middle of the straight, and, set, two arrows at it from above
        and below, mirror images."""
        self.ellipse(15.0, 15.0, 6.25, BLACK)
        self.ellipse(15.0, 15.0, 4.7, YELLOW if set_ else WHITE)
        if set_:
            tip = 6.25 + 0.9
            tail = max(-tip - 9.0, EDGE_CLEAR - 15.0)
            shaft = [(-1.0, tail), (1.0, tail), (1.0, -tip - 3.4), (-1.0, -tip - 3.4)]
            head = [(-2.6, -tip - 3.6), (2.6, -tip - 3.6), (0.0, -tip)]
            for part in (shaft, head):
                self.poly([(15.0 + x, 15.0 + y) for x, y in part], RED)
                self.poly([(15.0 + x, 15.0 - y) for x, y in part], RED)

    def base(self, im):
        """A picture at this tile's size as the canvas, to draw over."""
        self.rgb = np.asarray(im.convert("RGB").resize((self.size * self.ss,) * 2, Image.Resampling.NEAREST), dtype=float)

    def uncoupler(self):
        """The hump: the band's edge in its own grey and width (a pixel at 30 and 60), the black inside it."""
        a, lo = straight_parts(self.size)[:2]
        edge, e = (int(a[lo, 0]),) * 3, 30.0 / min(self.size, 60)
        i = e * (math.sqrt(2) - 1)
        self.poly([(2.5, 11), (4.5, 9), (25.5, 9), (27.5, 11)], edge)
        self.poly([(2.5 + i, 11 + e), (4.5 + i, 9 + e), (25.5 - i, 9 + e), (27.5 - i, 11 + e)], BLACK)

    def ray(self, angle, r0, r1, half, fill):
        """A band along a ray from the centre, r0 to r1 out, half either side."""
        ux, uy = math.cos(math.radians(angle)), math.sin(math.radians(angle))
        r = (r0 + r1) / 2
        self.poly(rot_rect(15 + ux * r, 15 + uy * r, ux, uy, (r1 - r0) / 2, half), fill)

    def turntable(self):
        self.straight(False, True); self.straight(True, True)
        for angle in range(45, 360, 90):
            self.ray(angle, 8, 22, 2.2, BLACK)
        self.ellipse(15, 15, 9, BLACK)
        self.ellipse(15, 15, 7.9, PIT)
        self.ray(0, -7.9, 7.9, 4, BLACK)
        self.dash(12)

    def dash(self, length):
        """The straight's own dash, its ends kept and its middle cut to `length` units about the centre, so it is as
        high as the straight's (MT-1071)."""
        n = 60 if self.size == 120 else self.size
        y0, y1, x0, x1 = straight_parts(n)[4]
        d, k = straight_image(n).crop((x0, y0, x1, y1)), round(length * n / 30) // 2
        cut = Image.new("RGB", (2 * k, d.height))
        cut.paste(d.crop((0, 0, k, d.height)), (0, 0))
        cut.paste(d.crop((d.width - k, 0, d.width, d.height)), (k, 0))
        f = self.ss * self.size // n
        big = np.asarray(cut.resize((cut.width * f, cut.height * f), Image.Resampling.NEAREST), dtype=float)
        left = (self.size * self.ss - big.shape[1]) // 2
        self.rgb[y0 * f:y0 * f + big.shape[0], left:left + big.shape[1]] = big

    def save(self, name, colour=False):
        n, ss = self.size, self.ss
        small = Image.fromarray(np.rint(self.rgb.reshape(n, ss, n, ss, 3).mean(axis=(1, 3))).astype(np.uint8), 'RGB')
        os.makedirs(os.path.join(OUT, "icons%d" % self.size), exist_ok=True)
        path = os.path.join(OUT, "icons%d" % self.size, name + ".gif")
        if n == 120:
            small.save(path[:-3] + "png")
        elif colour:
            small.quantize(colors=256, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).save(path)
        else:
            grey = small.convert("L")
            p = Image.new("P", grey.size)
            p.putpalette([v for i in range(256) for v in (i, i, i)])
            p.putdata(list(grey.getdata()))
            p.save(path)
        return small


def build(size):
    made = {}

    def tile():
        return Tile(size)

    def raster(f):
        """A picture drawn pixel by pixel off the straight: at 120, its 60 doubled."""
        return np.kron(f(60), np.ones((2, 2), int)) if size == 120 else f(size)

    t = tile(); t.chord("S", "E", True); made["curve"] = t.save("curve")
    t = tile(); t.chord("N", "W", True); t.chord("S", "E", True); made["curve_parallel"] = t.save("curve_parallel")
    for set_ in (False, True):
        sfx = "_active" if set_ else ""
        t = tile(); t.chord("S", "E", True); t.sensor("S", "E", set_)
        made["s88_curve" + sfx] = t.save("s88_curve" + sfx, colour=set_)
        t = tile(); t.chord("N", "W", True); t.chord("S", "E", True); t.sensor("S", "E", set_)
        made["s88_double_curve" + sfx] = t.save("s88_double_curve" + sfx, colour=set_)

    # switches: the idle leg first, the set leg over it
    t = tile(); t.chord("S", "W", False); t.straight(True, True); made["switch_left"] = t.save("switch_left")
    t = tile(); t.straight(True, False); t.chord("S", "W", True); made["switch_left_active"] = t.save("switch_left_active")
    t = tile(); t.chord("S", "E", False); t.straight(True, True); made["switch_right"] = t.save("switch_right")
    t = tile(); t.straight(True, False); t.chord("S", "E", True); made["switch_right_active"] = t.save("switch_right_active")
    t = tile(); t.chord("S", "E", False); t.chord("S", "W", True); made["switch_y"] = t.save("switch_y")
    t = tile(); t.chord("S", "W", False); t.chord("S", "E", True); made["switch_y_active"] = t.save("switch_y_active")
    t = tile(); t.chord("S", "W", False); t.chord("S", "E", False); t.straight(True, True); made["threeway"] = t.save("threeway")
    t = tile(); t.chord("S", "E", False); t.straight(True, False); t.chord("S", "W", True); made["threeway_active"] = t.save("threeway_active")
    t = tile(); t.chord("S", "W", False); t.straight(True, False); t.chord("S", "E", True); made["threeway_active2"] = t.save("threeway_active2")
    # THE CROSSING AND THE CROSSING SWITCH: the crossing is the straight both ways; the switch's other routes are the
    # same 45-degree pieces as a curve, north-west and south-east
    t = tile(); t.paste_final(raster(cross_pixels)); made["cross"] = t.save("cross")
    t = tile(); t.chord("N", "W", False); t.chord("S", "E", False); t.paste_final(raster(cross_pixels))
    made["crossswitch"] = t.save("crossswitch")
    t = tile(); t.paste_final(raster(cross_pixels), idle=True); t.chord("N", "W", True); t.chord("S", "E", True)
    made["crossswitch_active"] = t.save("crossswitch_active")

    # THE SCISSORS: the straight, and two pieces off to the east, north and south
    t = tile(); t.chord("N", "E", False); t.chord("S", "E", False); t.straight(True, True)
    made["custom_scissors"] = t.save("custom_scissors")
    t = tile(); t.straight(True, False); t.chord("N", "E", True); t.chord("S", "E", True)
    made["custom_scissors_active"] = t.save("custom_scissors_active")

    # THE PERMANENT ONES: every leg set, the pieces first and the straight over them
    t = tile(); t.chord("S", "W", True); t.straight(True, True); made["custom_perm_left"] = t.save("custom_perm_left")
    t = tile(); t.chord("S", "E", True); t.straight(True, True); made["custom_perm_right"] = t.save("custom_perm_right")
    t = tile(); t.chord("S", "W", True); t.chord("S", "E", True); made["custom_perm_y"] = t.save("custom_perm_y")
    t = tile(); t.chord("S", "W", True); t.chord("S", "E", True); t.straight(True, True)
    made["custom_perm_threeway"] = t.save("custom_perm_threeway")
    t = tile(); t.chord("N", "E", True); t.chord("S", "E", True); t.straight(True, True)
    made["custom_perm_scissors"] = t.save("custom_perm_scissors")

    # THE STRAIGHT SENSOR, THE BUFFER STOP
    for set_ in (False, True):
        t = tile(); t.straight(False, True); t.straight_sensor(set_)
        made["s88" + ("_active" if set_ else "")] = t.save("s88" + ("_active" if set_ else ""), colour=set_)
    t = tile(); t.paste_final(raster(end_pixels)); made["end"] = t.save("end")
    t = tile(); t.straight(False, True); t.uncoupler(); made["decouple"] = t.save("decouple")
    t = tile(); t.turntable(); made["turntable"] = t.save("turntable")
    if size == 120:
        build_others()
    return made


def build_others():
    """The rest at 120, from their 60 (see the top)."""
    def sixty(name):
        return Image.open(os.path.join(SRC, "icons60", name + ".gif"))

    straight_image(120).save(os.path.join(OUT, "icons120", "straight.png"))
    t = Tile(120); t.base(sixty("tunnel")); t.rect(11, 7.5, 19, 11.5, (238, 238, 238))
    t._fill(((t.X - 15) ** 2 + (t.Y - 11.5) ** 2 <= 16) & (t.Y <= 11.5), BLACK)
    t.save("tunnel")
    t = Tile(120); t.base(sixty("overpass"))
    for flip in (lambda x, y: (x, y), lambda x, y: (x, 30 - y)):
        for x0, x1 in ((5, 11), (19, 25)):
            t.poly([flip(x, y) for x, y in ((x0, 6), (x1, 6), (x1, 10), (x0, 10))], WHITE)
        for part in (((5.75, 7.25), (6.75, 6.25), (9, 8.5), (7, 8.5)), ((7, 8.5), (23, 8.5), (21.5, 10), (8.5, 10)),
                     ((24.25, 7.25), (23.25, 6.25), (21, 8.5), (23, 8.5))):
            t.poly([flip(x, y) for x, y in part], BLACK)
    t.save("overpass")
    for name in ("lamp", "lamp_active", "route", "route_active", "empty", "text"):
        im = sixty(name).convert("RGBA").convert("RGBa").resize((120, 120), Image.Resampling.LANCZOS).convert("RGBA")
        im.save(os.path.join(OUT, "icons120", name + ".png"))


if __name__ == "__main__":
    for size in (30, 60, 120):
        build(size)
    print("wrote the 30, 60 and 120 tiles to", OUT)
