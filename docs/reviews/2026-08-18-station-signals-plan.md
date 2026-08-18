# Station signals: red on arrival, green along the path

Adam's decision, 18 August 2026. Not implemented - this is the plan to implement from.

## What the problem is

A legal route is not just graph connectivity. Adam's example: `BottomMainA` cannot run to
`BottomSecondary`, because a red signal after the end requires a stop at `TopMainR1` or `TopMainR2`.
That constraint lived in the hand-authored edge config commands, which could set any accessory to any
state.

The derived model cannot express it. `TilePorts` declares a signal tile as
`signalled(SIGNAL, GREEN, route(E, W))` - one state, whose only command is **set this signal GREEN**.
So the derivation emits, per edge, the switch positions needed to make the route continuous plus GREEN
for every signal the path crosses. It can clear a signal for a movement; it cannot use one to protect.

**Concrete prediction to test:** the derived model will offer `BottomMainA -> BottomSecondary`
directly, and should not.

## The decision

Signals are **hardware outputs**, not gating. Autonomy sets them; nothing refuses to move because one
is red. (Interlocking - autonomy declining to enter a protected block - was considered and is NOT
being built.)

- **Red:** a station is PAIRED with the signal that protects it. On arrival, that signal goes red.
- **Green:** auto-calculated from the path taken, which is what already happens.

## Design

### Storage

A station-to-signal pairing, keyed by tile, in `AutonomyCompanionStore` beside `portals` - which is the
closest existing shape (a tile-to-tile pairing that survives renames because it is keyed by page id).

    stationSignals : station tile -> signal tile

Rekeyed in `renamePage` (both key and value, like `captions`), dropped in `reconcile` when either tile
goes, cleared on demotion like the caption and the arrival restrictions, and listed in `KNOWN_SHARED`.

### Deriving the command

Not an edge config command. Edge commands fire at **configure** time, before the train moves
(`configureEdge`, called from `configureAndLockPath`) - so a red emitted there would set the protecting
signal red while the train is still approaching it, and on the approach that signal is the one the
train itself is about to pass.

Use the arrival hook instead. `Layout` already fires `CB_ROUTE_END` per locomotive when a path
completes, and `CB_PRE_ARRIVAL` just before. `CB_ROUTE_END` is the right moment: the train is in the
station, and the signal behind it should now be red.

So:

1. `AutonomyBuilder` emits, on each **station point**, the accessory name of its paired signal - e.g.
   `"protectingSignal": "Signal 42"` - alongside `station` and `s88`.
2. `Layout.parseAuto` reads it onto the `Point` (a nullable field, absent everywhere it is not set, so
   hand-written configurations are untouched).
3. On `CB_ROUTE_END`, the point the train arrived at sets its protecting signal RED.
4. Green needs nothing new: the path's own config commands already set every signal it crosses GREEN,
   which naturally clears the protection of the station being LEFT.

### The ordering subtlety, and why this order is right

The signal protecting station S is normally on the approach to S, so a path INTO S crosses it and sets
it green - then arrival sets it red behind the train. That is the correct sequence and it falls out of
doing green at configure time and red at arrival.

Where the two would fight is a path that crosses S's protecting signal *without stopping at S*. There
the green wins, which is right: the block is being traversed, not occupied.

### Interface

Pairing is a gesture in the autonomy editor, modelled on the existing portal pairing: right-click a
station -> "Signal protecting this station" -> click the signal tile. The pairing draws as a thin line
or a small mark on both tiles, as portals do.

Ambiguity is why this is paired by hand rather than inferred. Deriving "the nearest signal on the
approach" was considered; on a real layout the nearest signal is not always the protecting one, and a
wrong inference here sets a real signal on real hardware.

### Checks

- A station paired with a tile that is not a signal, or with a signal that has no address, is a
  finding.
- A signal paired with more than one station is worth a warning: on arrival at either, it goes red, and
  the other station's protection is then whatever the last train did.

## Tests

- The builder emits `protectingSignal` only for paired stations, and the name matches the accessory.
- `parseAuto` reads it, and a configuration without it behaves exactly as before.
- Arrival at a paired station sets that accessory red - assert against the accessory state, not the
  JSON.
- A path crossing the signal without stopping leaves it green.
- Rename a page: the pairing survives, both halves.
- Demote the station: the pairing goes with it, like the caption.

## What this does NOT do

It does not make `BottomMainA -> BottomSecondary` impossible. Setting a signal red is an output; the
router still offers the journey. If that route must be refused, that is the interlocking question -
`isPathClear` consulting signal state - and it is a separate, larger decision.
