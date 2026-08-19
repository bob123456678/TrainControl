# The two autonomy menus: capitalization, and a proposed reorganization

Two separate pieces of work. The capitalization is **done**; the reorganization is **proposed only**,
and nothing below the line has been implemented.

---

## Done: capitalization

The application has one convention and the autonomy menus were the only place not following it.

Everything else in TrainControl uses **Title Case** for menu items - "Backup TrainControl Data", "Add
Locomotive", "Turn On All Lights", "Edit Current Page", "Show Locomotive Pages as Tabs" - with minor
words left lowercase mid-phrase. The autonomy editor's right-click menu was written entirely in
sentence case ("Trains can stop here", "Pair with a link", "Autonomy settings"), so the two sat side by
side in the same window looking like they came from different programs.

The older graph-window menu (`autolayout.ui.*`) was already mostly Title Case and had four stragglers
of its own - "Excluded locomotives", "Maximum train length", "Speed multiplier", "Station priority" -
which appear in the SAME right-click menu as the new items. Those are fixed too.

Ellipsis was also mixed. The application uses three ASCII dots (35 values) more than the typographic
character (22), and the two forms met inside one menu: "Name…" directly above "Advanced Parameters...".
Normalised to `...`, which is both the majority form and ASCII - the message bundles must stay ASCII or
Java 8 mojibakes them.

51 values changed, in all eight bundles. All eight still parse, carry identical 1527-key sets, contain
no byte above 127, and have balanced `{n}` placeholders.

**Left in sentence case deliberately.** Three items are messages rather than commands - they are
disabled placeholders that say why a menu is empty:

- "Close the editor first - autonomy cannot be changed from here while it is open."
- "Autonomy needs a layout stored on this computer"
- "none loaded", which is a VALUE, appearing inside "Configuration (none loaded)"

Title-casing a sentence makes it read as a label somebody could click.

---

## Proposed: reorganization

Not implemented. Both menus grew by accretion - each feature added its item wherever there was room -
and the result is that neither is ordered by anything a reader could predict.

### The autonomy editor right-click menu

**As it stands**, on a station:

```
Rename...
Station (yes)  >  Yes - Trains Can Stop Here / No - Trains Can Only Pass Through /
                  No - Nothing Can Pass / Can Be Chosen in Full Autonomy
Changing Direction  >  Never / May / Must
Trains May Arrive...  >  From the N / From the S
Protected by Signal 12...
-----
Speed Multiplier (100%)
Advanced Parameters...  >  Maximum Train Length / Station Priority
Excluded Locomotives (0)
Home for a Locomotive...
-----
Connections and Direction  >  branches, one-way runs, link pairing
Length...
Show a Station Name Here...
```

**What is wrong with it.** Three problems, in order of how much they cost a user:

1. **Two things called "length" mean different things and sit in different places.** "Length..." at the
   bottom is how long this piece of TRACK counts as; "Maximum Train Length" inside Advanced Parameters
   is how long a TRAIN may be to stop here. They are a sentence apart in the model and a mile apart in
   the menu, and neither name says which is which.

2. **The middle block has no name.** Speed multiplier, advanced parameters, excluded locomotives and
   home are four unrelated questions in a row between two separator lines. A reader who wants one of
   them has to read all four.

3. **"Connections and Direction" is below the operational settings**, but it is what makes the square
   part of a railway at all. It answers "where can trains go from here", which precedes every question
   above it.

**Proposed shape** - three named groups, ordered from what the square IS to how it behaves to what it
connects to:

```
Rename...
Show a Station Name Here...
-----
This Square         >  Station (yes) >  Yes / No, pass through / No, nothing passes
                       Can Be Chosen in Full Autonomy
                       Changing Direction  >  Never / May / Must
                       Track Length (4)
-----
Trains Stopping Here >  Trains May Arrive...  >  From the N / From the S
                       Protected by Signal 12...
                       Longest Train That Fits (any)
                       Excluded Locomotives (0)
                       Station Priority (default)
                       Home for a Locomotive...
-----
Trains Passing Through > Speed Multiplier (100%)
                       Connections and Direction  >  ...
```

The rule is: **which question does this answer?** What the square is, what happens to a train that
stops on it, what happens to a train that runs over it. "Advanced Parameters" disappears as a grouping,
because it grouped by how obscure a setting is rather than by what it is about - and the two length
settings end up in different groups, which is the honest place for them, with names that say which is
which ("Track Length" against "Longest Train That Fits").

### The Autonomy JMenu

**As it stands:**

```
Configuration (Autonomy 1)  >  the list, then Duplicate... / Rename... / Delete
Add a Configuration...
Edit Autonomy on Page
Autonomy Settings...
Stop Using Autonomy
Delete This Layout's Whole Autonomy Setup...
Export Raw Graph as JSON (Advanced Users)
Import from an Old autonomy.json (Advanced)...
```

**What is wrong with it.** Everything is at one level, so a destructive action ("Delete This Layout's
Whole Autonomy Setup") sits in the same list as an everyday one ("Edit Autonomy on Page") with only its
wording to warn anybody. And the two advanced import/export items are the last thing in a menu most
users will open expecting the first thing.

**Proposed shape:**

```
Edit Autonomy on Page
Configuration (Autonomy 1)  >  the list
                               -----
                               Add a Configuration...
                               Duplicate...
                               Rename...
                               Delete
Autonomy Settings...
-----
Stop Using Autonomy
-----
Advanced  >  Export Raw Graph as JSON
             Import from an Old autonomy.json...
             -----
             Delete This Layout's Whole Autonomy Setup...
```

Three changes worth stating:

- **"Add a Configuration..." moves inside the Configuration submenu**, next to Duplicate, Rename and
  Delete. It is the fourth verb in that set and the only one that was outside it.
- **The whole-setup delete moves under Advanced**, at the bottom, behind a separator. It is the one
  irreversible action in the menu and it currently sits between two harmless ones.
- **"Stop Using Autonomy" gets its own band.** It is neither configuration management nor an advanced
  operation; it is the one thing somebody reaches for in a hurry.

### What this does not address

Neither proposal changes any behaviour, and neither touches the diagram's own right-click menu, which
has a different problem: it is built from a different class and duplicates several of these items with
different wording. Worth a pass of its own, after these two settle.
