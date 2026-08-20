# Interface standards

Adam's, set 2026-08-19. These apply to everything hand-built from here on.

| What | Font | Colour |
| --- | --- | --- |
| Section headings | Segoe UI Semibold 13 | `0,0,155` |
| Important text and labels | Segoe UI Semibold 13 | black |
| Buttons | Segoe UI Bold 12 | black |
| Regular text | Segoe UI Plain 14 | black |

**Panels:** white background, 1px `LineBorder` in `204,204,204`.

**Section headings take the same indentation as the panel they name**, so a heading and its contents
line up down the left edge rather than the heading sitting proud of them.

---

## Use the code, not this table

`org.traincontrol.gui.UIStandards` holds all of the above as constants, with `heading()`, `label()`,
`text()` and `style()` for the common cases. Go through it rather than writing the numbers out again:
a table in a document does not stop the next panel being built in whatever the platform default
happens to be, and this one has already been out of step once - the route editor was built to
`Font.BOLD, 11f` and a blue of `0,0,115`, both close enough to look deliberate and neither of them
right.

```java
panel.add(UIStandards.heading(I18n.t("route.ui.frameCommands")), BorderLayout.NORTH);

JButton save = UIStandards.style(new JButton(I18n.t("route.ui.frameSave")));
```

## What this does not cover

**The NetBeans form screens.** Most of the older interface is generated from `.form` files, and its
styling lives inside `GEN-BEGIN` blocks that must not be hand-edited - so those screens keep whatever
they were drawn with until they are rebuilt by hand. New work is hand-written panels, which is why
this standard is worth having now.

**Fonts on machines without Segoe UI.** "Segoe UI Semibold" is its own family on Windows rather than
a weight of "Segoe UI", so it is asked for by that name; bold is the ordinary family with the bold
style. Where the family is missing Java substitutes a default silently - the sizes and weights still
hold, only the letter shapes differ. TrainControl targets Windows, so this affects development
machines rather than users.
