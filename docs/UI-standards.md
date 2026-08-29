# Interface standards

Adam's, set 2026-08-19.

| What | Font | Colour |
| --- | --- | --- |
| Section headings | Segoe UI Semibold 13 | `0,0,155` |
| Important text and labels | Segoe UI Semibold 13 | black |
| Buttons | Segoe UI Bold 12 | black |
| Regular text | Segoe UI Plain 14 | black |
| Minor headings | Segoe UI Plain 14 | `0,0,155` |

**Panels:** white background, 1px `LineBorder` in `204,204,204`.

**Section headings take the same indentation as the panel they name**, so a heading and its contents
line up down the left edge rather than the heading sitting proud of them.

## The reference screen

**`RouteEditorFrame`** is the hand-written screen built to this standard, and is the thing to copy
rather than this table. (`GraphEdgeEdit` used to be the reference screen; it left the build on
2026-08-21 and survives only as `docs/reference/GraphEdgeEdit.java.txt`, which is where the snippet
below is preserved from.)

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
