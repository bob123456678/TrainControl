# -*- coding: utf-8 -*-
"""Is what 3.0.0 offers a superset of what 2.8.1 offers?

Adam: "The routes offered in 3.0.0 should be a superset of those in 2.8.1.  Validate this is the case,
and if not, output the impossible paths from 3.0.0 at every stage in the sequence."

THE TWO ENGINES DO NOT NAME PLACES THE SAME WAY, and everything here turns on handling that honestly.
2.8.1 has one Point per station. 3.0.0 splits each into one per facing - "BottomMainC (eastbound)",
"BottomMainB (westbound, reverse)" - because facing lives in one-way edges rather than on the train.
Compared literally, every single route would read as missing.

So places are compared by their base name, and the facings of one station are UNIONED before the
comparison: everything 2.8.1 can reach from BottomMainA should be reachable from SOME facing of
3.0.0's BottomMainA. Stricter than that fails a train for being pointed the wrong way; looser than
that credits 3.0.0 with routes no real train could take.

THREE QUESTIONS, NOT ONE, because "a superset" fails in ways that need different fixes:

  1. DESTINATIONS.  Can a train still get from where it stands to everywhere it used to?  A lost
     destination is a part of the railway 3.0.0 cannot use.
  2. ROUTES.  Is every individual route still on offer?  A destination can survive on one path while
     three of the four ways of reaching it have gone - invisible to (1), and exactly what over-eager
     locking looks like.
  3. CONCURRENCY.  Can the pairs that used to run at the same time still do so?  Two routes can run at
     once precisely when the edges they lock do not intersect, so this is computed from the lock sets
     rather than watched for.  Adam's concern (b), measured directly.

REVERSING STATIONS ARE DROPPED FROM THE 2.8.1 SIDE ONLY, and only as DESTINATIONS.  Adam: "you may
ignore reversing stations in the 2.8.1 setup, as these are used for parking."  Dropped from the
expectation, not from the comparison: 3.0.0 offering more of them is fine and unremarked, and a
reversing point still counts as somewhere a route may pass through.

The report names what is missing rather than counting it.  A count says how big the problem is and
nothing about which one it is, and each of these is a specific route between two named places.
"""
import io
import re
import sys
from collections import defaultdict


def base(name):
    """"BottomMainB (westbound, reverse)" is BottomMainB."""
    at = name.find(' (')

    return name if at < 0 else name[:at]


def read(path):
    """The driver's tab-separated output, as lists of fields by record type."""
    out = defaultdict(list)

    for line in io.open(path, encoding='utf-8'):
        line = line.rstrip('\n').rstrip('\r')

        if line:
            parts = line.split('\t')
            out[parts[0]].append(parts[1:])

    return out


AUTO_NAMED = re.compile(r'^.+ \d+,\d+$')


def waypoints(edges, shared=None):
    """The named places a path visits, in order.

    EDGE SEQUENCES ARE NOT COMPARABLE BETWEEN THE TWO ENGINES, and comparing them was this script's
    own bug before it was anything else. Edges are named after their endpoints, so splitting a station
    by facing renames every edge touching it - and 3.0.0 goes further, carrying intermediate points
    2.8.1 never had:

        2.8.1   BottomMainPost -> RampUp
        3.0.0   BottomMainPost (northbound) -> 1 - Main 6,1 (westbound) -> RampUp

    Compared literally that reads as a lost route, and the first run of this report duly announced 17
    of them while section 1 was simultaneously saying every destination survived. Both cannot be true,
    and section 1 was the honest one.

    So a path is reduced to the NAMED places it passes through: facings dropped, and squares the
    builder named after their coordinates dropped too, since those are an artefact of how finely the
    diagram was reduced rather than anywhere a person would call a place.
    """
    out = []

    for edge in edges:
        for side in edge.split(' -> '):
            name = base(side.strip())

            if not name or AUTO_NAMED.match(name):
                continue

            # Only places both graphs have - see routes_of for why.
            if shared is not None and name not in shared:
                continue

            if not out or out[-1] != name:
                out.append(name)

    return tuple(out)


def routes_of(data, shared=None):
    """(loc, fromStation, toStation) -> set of routes, as waypoint sequences, unioned over facings.

    HELPER POINTS ARE NOT PART OF THE ROUTE, and leaving them in was the second thing this comparison
    got wrong. The hand-built graph carries points that exist only to make the modelling work -
    TopMainR1Bypass, TopMainR2Bypass, BottomSecondaryPre, BottomExitVIrt - and the derived graph does
    not need them, because of how it is constructed. Adam: "these should no longer be needed in the new
    graph due to the way we construct it."

    Counting their absence as four lost routes described the modelling rather than the railway. So when
    `shared` is given, a route is reduced to the places BOTH graphs know about: anything only one of
    them has is that engine's own bookkeeping, and dropping it symmetrically means neither engine is
    credited or blamed for how finely it chose to chop the track up.

    What this deliberately still catches: a place both graphs have that a route no longer passes
    through. That is a real change of route rather than a change of vocabulary.
    """
    out = defaultdict(set)

    for row in data['PATH']:
        loc, start, end = row[0], row[2], row[3]

        edges = row[4].split('|') if len(row) > 4 and row[4] else []

        out[(loc, base(start), base(end))].add(waypoints(edges, shared))

    return out


def point_names(data):
    """Every place one graph knows about, by base name."""
    return set(base(row[0]) for row in data['POINT'])


def normalise_edge(name):
    """An edge name with the facings stripped from both endpoints."""
    return ' -> '.join(base(side.strip()) for side in name.split(' -> '))


def locks_of(data):
    """(loc, facing, start, end) -> the edges that route locks.  Facings kept apart here, because two
    routes only genuinely coexist if one train can be in one state while the other is in another."""
    out = {}

    for row in data['LOCK']:
        loc, facing, start, end = row[0], row[1], row[2], row[3]

        edges = row[4] if len(row) > 4 else ''

        # Normalised the same way, so "do these two routes share track" is asked in one vocabulary.
        out[(loc, facing, base(start), base(end))] = set(
            normalise_edge(e) for e in edges.split('|')) if edges else set()

    return out


def reversing(data):
    """Base names of points flagged reversing - parking, in this layout."""
    return set(base(row[0]) for row in data['POINT'] if len(row) > 3 and row[3] == '1')


def concurrent_pairs(locks):
    """Pairs of routes, for different trains, whose lock sets do not intersect."""
    keys = sorted(locks)

    out = set()

    for i, a in enumerate(keys):
        for b in keys[i + 1:]:
            if a[0] == b[0]:
                continue

            if not locks[a] & locks[b]:
                out.add((a, b))

    return out


def by_station(pairs):
    """Drop the facing, so pairs can be compared across the two namespaces."""
    return set(((a[0], a[2], a[3]), (b[0], b[2], b[3])) for a, b in pairs)


def main():
    if len(sys.argv) < 4:
        print('Usage: compare.py <old.tsv> <new.tsv> <report.md>')
        return 2

    old, new = read(sys.argv[1]), read(sys.argv[2])

    shared = point_names(old) & point_names(new)

    old_routes, new_routes = routes_of(old, shared), routes_of(new, shared)
    old_locks, new_locks = locks_of(old), locks_of(new)

    parking = reversing(old)

    lines = []
    add = lines.append

    add('# Autonomy parity: 2.8.1 against 3.0.0')
    add('')
    add('Four trains on BottomMainA, BottomMainB, BottomMainC and BottomInner, on the same layout,')
    add('asked the same question through the same API. 3.0.0 should offer at least what 2.8.1 does.')
    add('')
    add('3.0.0 splits every station by facing, so its facings are unioned before comparing: a route')
    add('counts as offered if it is available from **any** facing of the station a train stands at.')
    add('Reversing points are not counted against 3.0.0 as destinations, being parking.')
    add('')
    add('| | 2.8.1 | 3.0.0 |')
    add('|---|---|---|')
    add('| points | %d | %d |' % (len(old['POINT']), len(new['POINT'])))
    add('| routes enumerated | %d | %d |' % (len(old_routes), len(new_routes)))
    add('| places the other lacks | %d | %d |'
        % (len(point_names(old) - shared), len(point_names(new) - shared)))
    add('')

    # ==================================================================== 1. destinations
    def destinations(routes, drop_parking):
        out = defaultdict(set)

        for (loc, _start, end) in routes:
            if drop_parking and end in parking:
                continue

            out[loc].add(end)

        return out

    old_dest = destinations(old_routes, True)
    new_dest = destinations(new_routes, False)

    lost = {}

    for loc in sorted(old_dest):
        missing = old_dest[loc] - new_dest.get(loc, set())

        if missing:
            lost[loc] = sorted(missing)

    add('## 1. Destinations')
    add('')

    if not lost:
        add('**Every destination survives.** ' + ', '.join(
            '%s: %d' % (loc, len(new_dest.get(loc, set()))) for loc in sorted(old_dest)) + '.')
    else:
        add('**%d train(s) lost destinations** - places the railway could reach and now cannot.'
            % len(lost))
        add('')

        for loc in sorted(lost):
            add('- **%s** can no longer reach: %s' % (loc, ', '.join(lost[loc])))

    add('')

    # ==================================================================== 2. routes
    add('## 2. Routes')
    add('')

    missing = []

    for key in sorted(old_routes):
        loc, start, end = key

        if end in parking:
            continue

        if key not in new_routes:
            missing.append((key, 'no route at all'))
        elif old_routes[key] - new_routes[key]:
            # Paths are compared as edge sequences, and the two engines name edges after their points,
            # so a split station renames edges too.  Reported as a reduction rather than a loss.
            missing.append((key, '%d of %d variant(s) gone'
                % (len(old_routes[key] - new_routes[key]), len(old_routes[key]))))

    if not missing:
        add('**Every route 2.8.1 offers is also offered by 3.0.0.**')
    else:
        add('**%d route(s) are missing or reduced in 3.0.0.**' % len(missing))
        add('')
        add('| Train | From | To | What is missing |')
        add('|---|---|---|---|')

        for (loc, start, end), why in missing[:80]:
            add('| %s | %s | %s | %s |' % (loc, start, end, why))

        if len(missing) > 80:
            add('')
            add('...and %d more.' % (len(missing) - 80))

        # WHAT A ROW HERE USUALLY MEANS, said once rather than left to the reader (ACC-C2).
        #
        # The table names routes and stops; a reader who has not been living in this code has no way
        # to tell a regression from a rule working as intended, and the most common cause by far is
        # the second.  The review that raised this traced three of the four rows on the operator's own
        # layout to one ruling.
        add('')
        add('**Before treating a row as a regression, check it against these.**  Each is a rule '
            '3.0.0 applies that 2.8.1 did not, so a route disappearing because of one is the rule '
            'working:')
        add('')
        add('- A station **protected by a signal** is not offered to a train that would have to pass '
            'that signal at danger.  Adam ruled on 2026-08-18 that a hand dispatch must not sweep '
            'protecting signals, and routes that depended on the old sweep are gone by design.')
        add('- A square marked **not an automatic destination** is never chosen by autonomy, though a '
            'person may still send a train there.')
        add('- A **reversing point** is never a destination in its own right.')
        add('- An **arrival side that has been barred** removes the routes that used it, which is what '
            'the red arrows on the diagram are for.')
        add('')
        add('A row that matches none of those is worth looking at.')

    add('')

    gained = sorted(set(new_routes) - set(old_routes))

    add('3.0.0 additionally offers %d route(s) 2.8.1 did not, which is allowed.' % len(gained))
    add('')

    # ==================================================================== 3. concurrency
    add('## 3. Concurrency')
    add('')
    add('Two routes can run at once exactly when the edges they lock do not intersect. Computed from')
    add('the lock sets, so it does not depend on two trains happening to be ready at the same moment.')
    add('')

    old_pairs = by_station(concurrent_pairs(old_locks))
    new_pairs = by_station(concurrent_pairs(new_locks))

    # Only pairs whose routes both still exist can be judged - one that lost its route is already
    # reported above, and counting it again here would report one defect twice.
    judgeable = set(p for p in old_pairs
        if p[0] in new_routes and p[1] in new_routes)

    lost_pairs = sorted(judgeable - new_pairs)

    add('- 2.8.1: %d concurrent pair(s)' % len(old_pairs))
    add('- 3.0.0: %d concurrent pair(s)' % len(new_pairs))
    add('- judgeable (both routes still exist): %d' % len(judgeable))
    add('')

    if not lost_pairs:
        add('**No pair that could run concurrently in 2.8.1, and still exists, has stopped.**')
    else:
        add('**%d pair(s) can no longer run concurrently** though both routes still exist -' %
            len(lost_pairs))
        add('over-eager locking: the routes are there, but 3.0.0 believes they collide.')
        add('')
        add('| Train A | Route A | Train B | Route B |')
        add('|---|---|---|---|')

        for a, b in lost_pairs[:60]:
            add('| %s | %s to %s | %s | %s to %s |' % (a[0], a[1], a[2], b[0], b[1], b[2]))

        if len(lost_pairs) > 60:
            add('')
            add('...and %d more.' % (len(lost_pairs) - 60))

    add('')

    # ==================================================================== 4. the timed run
    add('## 4. The timed run')
    add('')

    if not old['AT'] and not new['AT']:
        add('Nothing recorded. Simulate mode says so itself - "Auto layout development / simulation')
        add('mode enabled. Trains will not run" - so timings need a real Central Station, or a')
        add('simulator that moves trains. Sections 1-3 do not depend on it.')
    else:
        add('| Engine | Time (ms) | Train | Where |')
        add('|---|---|---|---|')

        for label, data in (('2.8.1', old), ('3.0.0', new)):
            start = int(data['RUNSTART'][0][0]) if data['RUNSTART'] else 0

            for row in data['AT']:
                add('| %s | %s | %s | %s |' % (label, int(row[0]) - start, row[1], row[2]))

        add('')
        add('Every place reached should correspond to an enumerated route. If one does not, the')
        add('enumeration above is incomplete and this report understates the problem.')

    add('')

    io.open(sys.argv[3], 'w', encoding='utf-8', newline='').write('\n'.join(lines) + '\n')

    print('\n'.join(lines))

    # Non-zero when the superset claim fails, so this can gate something later.
    return 1 if (lost or missing) else 0


if __name__ == '__main__':
    sys.exit(main())
