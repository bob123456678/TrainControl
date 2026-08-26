# -*- coding: utf-8 -*-
"""FR-029: the sidebar icons, drawn plainly in the theme's blue.

Adam: "the sidebar icons (locomotive, track, autonomy, signal, route, stats, log), while nice, date the
application.  use modernized, simple icons with a plain blue color matching the flatlaf theme."

Drawn at 512 and scaled down by the application, which is what the old ones were: getTabIcon scales
whatever it finds to 30 pixels tall.  So the only things that matter are the silhouette and the stroke
weight - anything finer than about a fortieth of the canvas disappears at the size these are seen at.
"""
import os
import sys
from PIL import Image, ImageDraw

BIG = 512
PAD = 56
BLUE = (38, 117, 191, 255)          # FlatLightLaf's accent, #2675BF
STROKE = 46                          # a fortieth of the canvas would vanish; this survives 30px

out = sys.argv[1]

if not os.path.isdir(out):
    os.makedirs(out)


def canvas():
    image = Image.new('RGBA', (BIG, BIG), (0, 0, 0, 0))
    return image, ImageDraw.Draw(image)


def save(image, name):
    image.save(os.path.join(out, name + '.png'))
    print('wrote', name + '.png')


# ---------------------------------------------------------------- locomotive
image, d = canvas()

# body, cab and boiler as one silhouette
d.rounded_rectangle((70, 250, 300, 380), radius=20, fill=BLUE)          # cab
d.rounded_rectangle((240, 290, 450, 380), radius=40, fill=BLUE)         # boiler
d.rounded_rectangle((60, 380, 452, 410), radius=12, fill=BLUE)          # footplate
d.rounded_rectangle((372, 230, 424, 300), radius=10, fill=BLUE)         # chimney
d.ellipse((96, 396, 196, 496), fill=BLUE)                               # driver
d.ellipse((216, 396, 316, 496), fill=BLUE)                              # driver
d.ellipse((350, 420, 422, 492), fill=BLUE)                              # pony
# cab window, punched out
d.rounded_rectangle((110, 274, 210, 344), radius=10, fill=(0, 0, 0, 0))

save(image, 'loc')

# ---------------------------------------------------------------- track
image, d = canvas()

for x in (150, 362):
    d.rounded_rectangle((x - STROKE // 2, PAD, x + STROKE // 2, BIG - PAD), radius=12, fill=BLUE)

for y in (120, 232, 344, 448):
    d.rounded_rectangle((72, y - 22, BIG - 72, y + 22), radius=10, fill=BLUE)

save(image, 'track')

# ---------------------------------------------------------------- autonomy
image, d = canvas()

nodes = [(256, 96), (110, 330), (402, 330), (256, 440)]

for a, b in ((0, 1), (0, 2), (1, 3), (2, 3)):
    d.line([nodes[a], nodes[b]], fill=BLUE, width=STROKE - 12)

for x, y in nodes:
    d.ellipse((x - 62, y - 62, x + 62, y + 62), fill=BLUE)
    d.ellipse((x - 26, y - 26, x + 26, y + 26), fill=(0, 0, 0, 0))

save(image, 'autonomy')

# ---------------------------------------------------------------- signal
image, d = canvas()

# Two lamps, not one: one lamp on a stick is a map pin, and that is what the first version read as.
d.rounded_rectangle((232, 330, 280, BIG - 76), radius=14, fill=BLUE)    # mast
d.rounded_rectangle((160, 40, 352, 336), radius=56, fill=BLUE)          # head
d.ellipse((196, 78, 316, 198), fill=(0, 0, 0, 0))                       # upper lamp
d.ellipse((196, 202, 316, 322), fill=(0, 0, 0, 0))                      # lower lamp
d.rounded_rectangle((132, BIG - 84, 380, BIG - 40), radius=16, fill=BLUE)  # base

save(image, 'signal')

# ---------------------------------------------------------------- route
image, d = canvas()

# a line that forks, with the fork picked out - a switch, which is what a route sets
d.line([(64, 402), (250, 402)], fill=BLUE, width=STROKE + 8)
d.line([(250, 402), (446, 402)], fill=BLUE, width=STROKE + 8)
d.line([(250, 402), (410, 190)], fill=BLUE, width=STROKE + 8)

# arrowhead on the diverging leg, big enough to survive the scale down
d.polygon([(462, 104), (452, 250), (330, 156)], fill=BLUE)

# the fork itself, picked out - a route is the setting of a switch
d.ellipse((206, 358, 294, 446), fill=BLUE)

save(image, 'route')

# ---------------------------------------------------------------- stats
image, d = canvas()

bars = ((96, 300), (216, 200), (336, 96))

for x, top in bars:
    d.rounded_rectangle((x, top, x + 80, BIG - PAD), radius=16, fill=BLUE)

save(image, 'stats')

# ---------------------------------------------------------------- log
image, d = canvas()

d.rounded_rectangle((96, PAD, BIG - 96, BIG - PAD), radius=36, fill=BLUE)

for y in (150, 232, 314, 396):
    d.rounded_rectangle((150, y - 18, BIG - 150, y + 18), radius=10, fill=(0, 0, 0, 0))

save(image, 'log')

# ---------------------------------------------------------------- contact sheet
names = ('loc', 'track', 'autonomy', 'signal', 'route', 'stats', 'log')

sheet = Image.new('RGB', (len(names) * 110 + 20, 200), (247, 247, 247))

at = 20

for name in names:
    icon = Image.open(os.path.join(out, name + '.png'))

    sheet.paste(icon.resize((30, 30), Image.LANCZOS), (at + 30, 40), icon.resize((30, 30), Image.LANCZOS))
    sheet.paste(icon.resize((60, 60), Image.LANCZOS), (at + 15, 100), icon.resize((60, 60), Image.LANCZOS))

    at += 110

sheet.save(os.path.join(out, 'sheet.png'))

print('wrote sheet.png')
