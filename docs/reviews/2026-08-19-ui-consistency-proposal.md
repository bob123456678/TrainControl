# UI consistency: a proposal

**Nothing here has been changed.** This is a list of places where TrainControl says the same thing two
different ways, with a recommendation for each and a note on what it would cost. It is for Adam to
pick from — several of these are matters of taste, and a few are arguably fine as they are.

Ordered by how often a user would meet the inconsistency, not by how hard it is to fix.

---

## 1. Menu labels: the ellipsis means "this opens a dialog", except when it does not

**What is there now.** The convention is real and mostly followed — `Add Locomotive to Graph...`,
`Advanced Parameters...`, `Connect to Point...`, `Edit Edge...` all open something and all say so. But
it is broken in both directions:

| Label | Opens a dialog? |
| --- | --- |
| `Display Options` | Yes — no ellipsis |
| `Create New Point` | Yes — no ellipsis |
| `Route Choice` | It is a submenu, so correctly no ellipsis |
| `Delete Edge...` | Asks for confirmation — ellipsis, which is right if you count a confirmation |
| `Delete Point` | Also asks for confirmation — no ellipsis |
| `Rename` | Yes — no ellipsis |

So `Delete Edge...` and `Delete Point` do the same kind of thing and are punctuated differently, three
lines apart in the same menu.

**Proposal.** Pick one rule and apply it: *an ellipsis means the item needs more input from you before
anything happens.* Under that rule a plain yes/no confirmation does **not** earn one — a confirmation
is the system asking, not the user supplying — so `Delete Edge...` loses its ellipsis and `Rename`,
`Display Options` and `Create New Point` gain one.

**Cost.** Message-bundle edits only, in eight languages. About thirty keys. No code.

**Worth doing?** Yes, but not urgently. It is the kind of thing that makes an interface feel considered
without anybody being able to say why.

---

## 2. Yes/No buttons follow the system language in seven places and TrainControl's in fifty-one

`JOptionPane` is called 353 times across the interface:

| Call | Times |
| --- | --- |
| `showMessageDialog` | 261 |
| `showOptionDialog` | 51 |
| `showInputDialog` | 34 |
| `showConfirmDialog` | 7 |

Everything in the `showOptionDialog` group passes `YES_NO_OPTS`, which is built from
`I18n.t("ui.yes")` and `I18n.t("ui.no")` — so those buttons are in **TrainControl's** chosen language.
`showConfirmDialog` does not take button text; Swing supplies it from the look-and-feel's defaults,
which follow the **JVM's** locale. A user running TrainControl in German on an English Windows gets
German dialogs with English Yes/No buttons, in these seven places and nowhere else.

Of the seven, four are plain yes/no questions and are the ones worth changing:

| Where | Asks |
| --- | --- |
| `AutonomyEditorPanel.java:3425` | Whether to exclude this page |
| `AutonomyMenu.java:510` | Whether to delete the setup |
| `AutonomyViewerPanel.java:989` | Whether an import may overwrite |
| `AutonomyViewerPanel.java:1232` | Whether to delete the configuration |

The other three (`AutonomyEditorPanel.java:1347` and `:1865`, `AutonomyViewerPanel.java:608`) pass a
custom panel as the message and are really input dialogs wearing a confirmation's clothes. Those
should be left alone, or converted to `OK_CANCEL_OPTS`, which already exists.

**Proposal.** Convert the four plain ones to `showOptionDialog` with `YES_NO_OPTS`.

**Cost.** Four call sites, no new messages. The care needed is that `showConfirmDialog` returns
`YES_OPTION` while `showOptionDialog` returns an index, so each check has to be rewritten rather than
copied — and getting that backwards makes "no" mean "yes" on a delete.

**Worth doing?** Yes. This is the strongest item on the list: small, mechanical, and currently a
visible defect for every user whose system language differs from the one they picked.

---

## 3. Message-key prefixes: `autosetup` and `autolayout` are the same feature

The bundles use a prefix per area, and it works well — until you meet these two:

```
autosetup.ui.*    286 keys
autolayout.ui.*   195 keys
```

Both are autonomy. The split is historical: `autolayout` is the older graph, `autosetup` the diagram
work. Nothing distinguishes them to somebody adding a key today, so the choice is a coin toss, and
new keys are already landing in both.

**Proposal.** Do **not** rename them. Four hundred and eighty keys across eight bundles is a large
mechanical change with a real chance of dropping one, and the payoff is invisible to users. Instead,
write the rule down where the next person will meet it — a comment at the top of `messages.properties`
saying which prefix new autonomy keys go in (`autosetup` for anything about setting automation up,
`autolayout` for anything about running it) and that the two are not to be merged.

**Cost.** One comment, eight times.

---

## 4. Column headings in the two route editors do not match

The old editor shows a route's commands as text. The new one shows five columns: Kind, Target,
Setting, Protocol, Delay (ms). "Target" is doing a lot of work — for an accessory it is an address, for
a locomotive command it is a name — and neither editor uses the word anywhere else in the interface.

**Proposal.** Rename the column to something that says what goes in it. `Address or locomotive` is
accurate but long; `Applies to` is shorter and reads correctly for every command kind. Either is
better than `Target`.

**Cost.** One key, eight languages.

**Worth doing?** Marginal. Flagging it because it is a new string, and new strings are the cheapest
time to change a word.

---

## 5. "Point" and "station" are both used, for two different things, inconsistently

The graph calls its nodes **points**. The diagram calls the ones trains stop at **stations**. Both
words appear in menus, and the relationship is not obvious to a user: every station is a point, but
not every point is a station.

Where this bites: `Create New Point`, `Rename Point`, `Delete Point` sit in the same menus as
`Station Priority`, `Maximum Train Length`, `Excluded Locomotives` — the second group being settings
that only mean anything on a station.

**Proposal.** Leave the vocabulary alone, but **group** the menu so the distinction is visible: put the
station-only settings in a `Station` submenu that is absent on a point that is not a station. A user
then never sees `Station Priority` on something that has no priority, and the two words stop competing
in one flat list.

**Cost.** Moderate — restructuring two right-click menus. No new concepts, no new strings beyond one
submenu title.

**Worth doing?** This is the one on the list most likely to actually help somebody. It is also the one
most likely to annoy an existing user by moving things they know the position of.

---

## 6. Keyboard shortcuts are documented in tooltips, and nowhere else

The diagram editor's shortcuts (`Control+V`, `Control+I`, and now Escape, Delete and `Control+C`)
are discoverable only by hovering the menu item that also does the job. There is
no list.

**Proposal.** A `Keyboard shortcuts` item on the Help menu that opens a plain read-only list. This is
also the cheapest way to notice when a shortcut is documented and no longer wired up — the list is
written by hand, so writing it is an audit.

**Cost.** One small dialog, one message key per shortcut.

**Note.** This was written when `Shift+C` and `Shift+R` were still promised by tooltips for options
that had been removed. The multi-select commit took both tooltips out with the options, so the
example no longer exists — which is the argument for the list rather than against it: nobody noticed
those two were stale for as long as they were, because there was nowhere that listed them.

---

## 7. Some errors are dialogs, some are log lines, and the choice is not principled

A tile edit that fails is logged. A route that will not save shows a dialog. A locomotive placement
that fails does both, in some paths.

The rule that seems to be intended — and it is a good one — is: **a dialog if the user asked for this
one thing and it did not happen; a log line if it is one of many and a dialog per failure would be
worse.** `LayoutEditor.delete` even says so in a comment.

**Proposal.** Write that rule down in `docs/`, and leave the code alone unless a specific case is
found to break it. This is a case where the existing behaviour is mostly right and a sweep would risk
making it worse.

**Cost.** A paragraph.

---

## What I would do, if it were one afternoon

1. **Item 2** — the four plain `showConfirmDialog` calls. Small, mechanical, and currently a real
   defect for every user whose system language differs from the one they picked.
2. **Item 6** — the shortcut list, mostly because writing it audits the shortcuts.
3. **Item 1** — the ellipsis rule, since it is bundle-only.

Items 3, 5 and 7 I would write down rather than change. Item 4 is a coin toss.
