# Interface standards

Adam's, set 2026-08-19.

| What | Font | Color |
| --- | --- | --- |
| Section headings | Segoe UI Semibold 13 | `0,0,155` |
| Item labels (a field's, a row's, a table's column headings) | Segoe UI Semibold 13 | black |
| Buttons | Segoe UI Bold 12 | black |
| Regular text | Segoe UI Plain 14 | black |
| Minor headings | Segoe UI Plain 14 | `0,0,155` |

**Panels:** white background, 1px `LineBorder` in `204,204,204`.

**Section headings take the same indentation as the panel they name**, so a heading and its contents
line up down the left edge rather than the heading sitting proud of them.

**Blue is for section headings only** (Adam, 2026-11-07: *"blue for section headings, black for item text."*). A
window whose items need setting apart gets a blue heading over them, not blue items.

## The parts to build with

New hand-written windows take these rather than setting fonts and colors themselves. The 3.1.0 windows
(`LocomotiveSettingsFrame`, `StationSettingsWindow`, `TrainWindow`) are built from them.

- **`widgets/UiStyle`** holds the table above: `UiStyle.heading(...)` for a section heading, `UiStyle.label(...)` or
  `blackLabel(...)` for an item label, `UiStyle.button(...)` to put a button in Bold 12, and `withoutColon(...)` for
  an older bundle entry that ends in a colon.
- **`InfoMark`** is the (i): a standing explanation goes behind one, as its tooltip, rather than in a label under the
  controls. `InfoMark.label(text)` wraps the text, keeps the tooltip up long enough to read, and shows it at once on a
  click (Adam: *"click on any i should immediately show the tooltip, so you don't have to wait for the hover
  timeout"*). Text giving a live reason, such as why a window is locked, stays visible instead.
- **`widgets/PillButton`** is a toggle drawn as a pill: white with a light gray outline, filled with the theme's accent
  blue while selected, with a hand cursor. Use it for a row of choices where the lit one says which applies, as
  Station Settings' `Presets` do.
- **The warning triangle**, `RowIcons.warning(16)`, sits right of a field to flag something to look at, and its
  tooltip says what: Locomotive Settings shows it beside `Address` when another locomotive shares the address, and
  Station Settings beside a setting the stations picked disagree on. It appears by itself, with no button to ask for it.
  It also marks a problem inside a button: `Power OFF` wears it, with a tooltip saying why, while the Central Station
  isn't answering, and drops it when answers return.
  `RowIcons` also has the row marks (trash, copy, plus, arrows, grip) the same windows use.

## The reference screen

**`RouteEditorFrame`** is the hand-written screen built to this standard, and is the thing to copy
rather than this table, with the parts above for anything it does not show. (`GraphEdgeEdit` used to be the reference screen; it left the build on
2026-08-21 and survives only in git history - `docs/reference/GraphEdgeEdit.java.txt` at d8db4879 - which is
where the snippet below is preserved from.)

```java
label.setFont(new java.awt.Font("Segoe UI Semibold", 0, 13));   // labels
field.setFont(new java.awt.Font("Segoe UI", 0, 14));            // regular text
button.setFont(new java.awt.Font("Segoe UI", 1, 12));           // buttons
```

The second argument is the style - `0` plain, `1` bold - so Semibold is the plain style of its own
family, and bold is the ordinary family made bold. Windows treats "Segoe UI Semibold" as a separate
family, which is why it is asked for by name.

## Where this does and does not reach

Most of the older interface is generated from `.form` files and carries its styling inside `GEN-BEGIN`
blocks, which must not be hand-edited - those screens keep what they were drawn with until they are
rebuilt. New work is hand-written panels, which is what this is for.

Worth knowing, since it is the reason this document exists: the route editor was built to `Font.BOLD,
11f` and a blue of `0,0,115` before these were written down. Both were close enough to look
deliberate, and neither was right.
