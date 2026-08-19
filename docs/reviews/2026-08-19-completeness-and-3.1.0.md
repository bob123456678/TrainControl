# Feature completeness for 3.0.0, and ideas for 3.1.0

Two separate questions, kept apart because they want different answers from Adam.

**Part one** asks whether what 3.0.0 promises is actually finished — where a feature exists but stops
short of what somebody would expect it to do. Every item here is a gap in something that is shipping.

**Part two** is a list of things worth building next. None of it is needed for 3.0.0.

---

# Part one: is 3.0.0 finished?

## Where I think the answer is "no, and it matters"

### 1. Nothing tells a user WHY autonomy will not move a train

The single most common state a new user reaches is "I pressed start and nothing happened", and the
answer is almost always one of a small, knowable set: every station is occupied, the destination
excludes this locomotive, the route needs a switched-off link, the only destination is a reversing
point, the locomotive has no speed.

TrainControl knows which of those it is — the log says so, per train, in as many words. But the log is
a scrolling text panel that a user has to think to look at, and the locomotive panel says only "no
available paths", which is the symptom rather than the cause.

**Proposal.** Make the panel's "no available paths" line a tooltip or a hover that names the reason for
that particular locomotive. The information already exists at the point the decision is made; it is
thrown away on the way to the display.

This is the item I would spend the remaining 3.0.0 time on. It converts the most common support
question into something the interface answers by itself.

### 2. The route drawn on the diagram has no key

A running train draws red ahead, green behind, black arrows for direction. That is good, and nothing
on screen says what those mean. A user meeting a red line on their track diagram has every reason to
read it as a warning.

**Proposal.** One line in the autonomy panel, or a legend under `Display Options`. Cheap.

### 3. Station↔signal pairing is one-way and unlisted

A station can be paired with a signal. There is no way to see, from the signal, that it belongs to a
station, and no list anywhere of which pairings exist. On a layout with a dozen of them, the only way
to audit is to right-click every station in turn.

**Proposal.** A list in the autonomy view, the same way excluded locomotives are listed. Or, more
cheaply: mark the paired signal on the diagram the way a one-way station is marked with an arrow.

### 4. Arrival directions are only visible one station at a time

Same shape of problem. The diagram shows a small arrow where a station takes trains one way, which
is good — but only for stations restricted to exactly one direction. A station that bars one of four
directions looks identical to a station that bars none.

**Proposal.** Show the barred sides on the tile in the autonomy view, where the user is already
thinking about them. This is a rendering change to the annotation that already exists.

### 5. "Return home" cannot be undone

It borrows the timetable, plans, and runs. If the user changes their mind halfway, `Graceful Stop`
stops the trains — wherever they are, which is generally not where they started and not where they
were going. There is no "put everything back the way it was before I pressed that".

**Proposal.** None for 3.0.0; this is honest behaviour and the alternative is a large feature. Worth
saying out loud in the guide, which it now is.

## Where the answer is "no, and it does not matter yet"

- **The new route editor cannot edit every command kind.** Sub-route calls, auto-locomotive commands
  and lights are shown greyed and kept as found. That is the right behaviour, and the old editor is
  still there for the rest. It stops being right if the old editor is ever removed.
- **Timetables cannot be edited, only captured and replayed.** A wrong entry means recapturing.
- **There is no way to see what a locomotive's preferred functions are** except by right-clicking its
  keyboard button.
- **Lengths are set per station and per locomotive but never shown together**, so "will this train fit
  there" is a question the user answers by opening two dialogs.

## Where I looked and think it IS finished

- Placement, arrival tracking and the block model. The arrival-side split is load-bearing, tested from
  several directions, and the shadow-station machinery behaves under contention.
- The path preferences, now that the sensor count counts sensors and least-recently-visited exists.
- Central Station sync, now that it does not freeze the interface, cannot run twice at once, and does
  not leave the layout database empty across a fetch.
- Undo in the diagram editor, including captions, and now including group operations as single steps.

---

# Part two: ideas for 3.1.0

Ordered by what I would build first, with a note on why.

## 1. Say why a train is not moving — properly

Item 1 above is the cheap version. The full version is a panel that answers "why is nothing
happening?" for the whole layout at once: every locomotive, its state, and the reason it is in that
state. It is the difference between an operator who trusts the system and one who does not.

## 2. A timetable editor

Capture-and-replay is the right primitive and the wrong interface for changing anything. The row model
built for the route editor is exactly the shape a timetable wants: locomotive, from, to, wait. The
groundwork is done.

## 3. Named views of the diagram

Large layouts are several pages, and a user working on one area moves between them constantly. A saved
view - a page and a scroll position, under a name - is a small feature that a big layout would use
every session.

## 4. Scheduled operation

"Run these locomotives between 6 and 8 in the evening." The timetable machinery already runs a
sequence and waits between entries; what is missing is a clock. Likely small, and it is the difference
between a layout that runs when watched and one that runs.

## 5. A history of what actually happened

TrainControl records running time per locomotive. It does not record where trains went. A log of
completed routes - locomotive, from, to, when, how long - would answer "why is that one always in the
yard", and would make the least-recently-visited preference visible rather than something the user has
to take on trust.

## 6. Diagram export, extended

The export added this session writes one page to a PNG. Two obvious follow-ons: every page at once,
and an SVG so the diagram scales without being redrawn. The first is a loop; the second is a renderer.

## 7. Multi-select, extended

The selection machinery now exists. Two things it makes cheap that it does not yet do: select-by-drag
(a rubber band rather than shift-clicking each square) and select-all-of-a-kind ("every signal on this
page"). Both are small on top of what is there.

## 8. The one I would not build

**A rules engine for autonomy** - "if this, then that", user-authored. It comes up whenever automation
software is discussed, it always looks like the natural next step, and it always turns a system that
non-technical users can operate into one they cannot. TrainControl's automation is good precisely
because the user says what their railway IS and the software works out what to do. Linked routes
already cover the specific cases (emergency stops, sound effects, safety signals) without asking
anybody to write logic.

---

# What I would ask Adam

1. **Item 1 of part one** - is "say why the train is not moving" worth holding 3.0.0 for? I think it is
   the highest-value thing left, and it is not small.
2. **Items 3 and 4** - the signal pairing and the barred arrivals are both invisible in bulk. Is that
   acceptable for a first release of diagram autonomy, or does it need the list?
3. **Part two ordering** - the list above is my judgement, not yours. The one I am least sure about is
   scheduled operation: it might be the thing everybody wants, or the thing nobody asked for.
