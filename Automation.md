# Automating your layout

This guide takes you from a track diagram with nothing set up on it to a layout running trains on its own, one step at a time. Every step is something you do on the diagram; there is no file to write and no code to run.

If you want to drive TrainControl from Java instead, or you need the reference for a particular setting, see **[the programmatic guide](AutomationAPI.md)**.

**Contents**

- [What you need](#what-you-need)
- [The idea in one page](#the-idea-in-one-page)
- [Example 1: a shuttle between two stations](#example-1-a-shuttle-between-two-stations)
- [Example 2: an oval with a passing loop](#example-2-an-oval-with-a-passing-loop)
- [Example 3: a terminus, and trains that must turn round](#example-3-a-terminus-and-trains-that-must-turn-round)
- [Example 4: a through station that only takes trains one way](#example-4-a-through-station-that-only-takes-trains-one-way)
- [Watching it run](#watching-it-run)
- [Choosing how trains pick their route](#choosing-how-trains-pick-their-route)
- [Timetables: recording a sequence and playing it back](#timetables-recording-a-sequence-and-playing-it-back)
- [Sending everything home](#sending-everything-home)
- [Settings, and what each one is for](#settings-and-what-each-one-is-for)
- [When trains will not move](#when-trains-will-not-move)

---

## What you need

**Feedback sensors.** This is the only hardware requirement, and it is not optional: TrainControl knows where a train is because a sensor told it. One S88 contact per station is the minimum. Three is much better — before the stopping point, at it, and after it — because that is what lets a train slow down as it arrives rather than stopping dead on the contact.

**A track diagram stored on this computer.** Either downloaded from your Central Station with **Layouts → Download Central Station Layout Files**, or drawn in TrainControl's own editor. A diagram read from the Central Station each time TrainControl starts cannot carry an autonomy setup, so download it first. Automation is set up on this diagram, so if your diagram does not yet match your railway, start there.

**Locomotives with addresses that work.** If you cannot drive a locomotive by hand from TrainControl, automation will not be able to either.

That is all. You do not need to describe your track anywhere: TrainControl reads the diagram and works out for itself which squares connect to which.

---

## The idea in one page

Automation rests on three things, and everything else in this guide is a refinement of one of them.

**Stations are where trains stop.** You do not create them: every sensor square on your diagram becomes a station when the setup is built, because a sensor is exactly the thing that can tell TrainControl a train has arrived. What you do is give them names, and say which ones trains may actually stop at as opposed to merely pass through.

**Links say where a train may go.** TrainControl traces the track on your diagram and works out the connections by itself. You do not draw them. What you do is switch off the few that you do not want used — a siding you would rather trains kept out of, or a connection that only makes sense in one direction.

**A locomotive has to be somewhere to start.** You tell TrainControl which train is standing at which station. From then on it keeps track by itself.

Press start, and TrainControl picks a train, picks somewhere it can go, sets the switches and signals along the way, sends it, and watches the sensors until it arrives. Then it does it again.

> **[Screenshot not yet captured — assets/automation/01-overview.png]** the autonomy view of a small layout, with two stations marked and the link between them drawn

---

## Example 1: a shuttle between two stations

The simplest arrangement that runs: two stations, one train, one piece of track between them.

```
    [ A ]=========================[ B ]
      s88 1                        s88 2
```

**1. Build the setup.** `Autonomy` → `Add a Configuration...`. This reads your track diagram as it stands and works out the railway from it: every sensor becomes a station, and the track between them becomes the links trains can use. Nothing is asked of you yet.

> **[Screenshot not yet captured — assets/automation/02-add-configuration.png]** the Autonomy menu, with "Add a Configuration..." highlighted

**2. Open the editor.** `Autonomy` → `Edit Autonomy on Page`, and pick your page. The diagram looks the same; what changes is that right-clicking a square now sets automation up rather than throwing a switch.

**3. Name the two stations.** Right-click each of the sensor squares at A and B and choose `Rename...`. The name is what you will see in every list and every log line, and what an arrival is announced under — so name them the way you talk about them out loud.

> **[Screenshot not yet captured — assets/automation/03-station-menu.png]** the right-click menu on a station square in the setup editor

**4. Check both may be stopped at.** The same menu has `Station (...)`, with three choices: trains can stop here, trains can only pass through, or nothing can pass. The first is what a station is. Sensors that are only there to track a train through a junction are the second.

**5. Put the train somewhere.** Right-click Station A and use `Add a Locomotive to Autonomy...`. This is a statement of fact about your railway: the train really does need to be standing at A.

**6. Save, and press start.** The train runs to B. When it arrives, TrainControl notices, waits a moment, and runs it back to A. It will keep doing that.

**What just happened.** TrainControl traced your diagram, found that A connects to B, saw a train at A, and found exactly one place it could go. Nothing else was needed.

**Try this.** Stop autonomy, and place a second locomotive at B. Start again. Now neither train can move — each wants the station the other is standing on, and a station holds one train at a time. This is the single most common reason a layout does nothing, and [it has its own section below](#when-trains-will-not-move).

---

## Example 2: an oval with a passing loop

Two trains, three stations, and the first arrangement where TrainControl has a choice to make.

```
              ___________[ C ]___________
             /                           \
    [ A ]===<                             >===[ B ]
             \___________________________/
```

Name the three sensor squares, as before, and place a train at A and a train at B.

Press start. A train leaves A. It can reach B two ways — through the loop past C, or round the other side — and TrainControl picks one. Meanwhile the train at B can leave too, because its route does not need the track the first train is on.

**What this example is really showing** is that TrainControl reserves the track a train needs, and only that track. Two trains run at once here because their routes do not overlap. If they did, the second would wait.

**Try this.** Right-click one side of the loop and untick `Autonomy Uses This Link`. Now every train goes past C. A switched-off link is greyed out on the diagram, so you can see at a glance what is not being used.

> **[Screenshot not yet captured — assets/automation/04-two-trains.png]** a diagram with one link greyed out, and two trains running

**Try this too.** Right-click C, open `Advanced Parameters...`, and set `Station Priority` higher than the others. Trains will now favour calling there. Priority does not force it — it tips the choice.

---

## Example 3: a terminus, and trains that must turn round

A terminus is a station where the track stops. A train arriving must leave the way it came, which means reversing.

```
    [ A ]===========[ B ]===========[ T ]
                                      terminus
```

Name T as before, and then set `Changing Direction` on it so that a train arriving may turn round.

This tells TrainControl two things. First, a train that arrives here will need to change direction before it can leave. Autonomy only sends a locomotive that can reverse on its own; you can still send one that cannot, if the route turns it round on the way so that it arrives already facing out. Second, and less obviously: a reversing point is treated as somewhere to **park**, not somewhere to route trains through. Autonomy running on its own will not send trains there and will not drive them through it on the way somewhere else.

That is deliberate, and it is worth understanding because it surprises people. Reversing points are usually parking tracks and shunting necks, and trains being parked at random in the shunting neck — or stopping and changing direction in the middle of a run — is not operation, it is chaos. So autonomy leaves them alone.

**You can still use it.** Send a train there yourself from the route menu, and [Return Locomotives Home](#sending-everything-home) will still park trains there. What will not happen is a train ending up there because a dice roll put it there.

> **[Screenshot not yet captured — assets/automation/05-terminus.png]** a terminus station on the diagram, showing the reversing marker

---

## Example 4: a through station that only takes trains one way

Some stations only make sense to arrive at from one direction. A platform on a one-way loop; a bay that faces east; a station where arriving from the west means fouling a junction.

Right-click the station and open **Trains May Arrive...** in the setup editor. By default a station accepts trains from every direction. Switch off the directions you do not want, and TrainControl will only route trains to it from the ones you left on.

The diagram marks this: a station that only takes trains one way shows a small arrow. That arrow is the only outward sign, so it is worth knowing what it means when you meet one on somebody else's layout.

> **[Screenshot not yet captured — assets/automation/06-arrivals.png]** the Arrivals view for a station, with one direction switched off, and the resulting arrow on the diagram

**Where this matters most** is a station that is really two platforms. On the diagram it is one square, but a train arriving from the east and a train arriving from the west are doing different things — different switches set, different track occupied. Arrival directions are how you say which of those you want.

---

## Track lengths: what they are for

Nothing above this line needs lengths. A layout with none set works, and TrainControl simply does not judge whether a train fits anywhere. Lengths only ever **refuse** — they are how you stop a train being sent somewhere it physically will not go, and how TrainControl knows that a long train standing at a short platform is hanging back over the track behind it.

**The unit is yours.** A length is a number, not centimetres. Pick something — a coach, a foot, ten centimetres — and use the same thing everywhere. All that matters is that a train's length and a track's length are counted in the same unit.

**Two different numbers, and they are easy to confuse.**

| | Where you set it | What it means |
| --- | --- | --- |
| A square's **segment length** | Right-click the square -> **Segment Length...**, or Control+E with the pointer over it | How much train this piece of track physically holds |
| A station's **maximum train length** | Right-click the station -> **Advanced Parameters...**, or Control+B | The longest train you are willing to have stop here, whatever the track says |

The first is a measurement, the second is a preference. Both are checked and either can refuse. Turn on **Track Lengths** in the setup editor's Toggle Visibility box to see the measurements on the diagram; **Clear All Track Lengths** in the bulk tools starts you over.

### What lengths govern

**Whether a train may be sent somewhere at all.** A train longer than the station's maximum is not sent there. A train longer than the measured track leading in is not sent there either, and the message says which of the two refused it.

**Whether a train would come to rest across a set of points.** This is the one that catches people out. The question is not "does the train fit between the two sensors" but "does it fit in the part after the last switch" — because a train standing on the points blocks every other road through them.

**How much track a standing train is actually occupying.** A train longer than its platform hangs back over the approach, across track that has no sensor of its own. TrainControl blocks that track, and draws it: the orange line on the diagram is as long as the train. Without lengths it cannot know, and two trains can be routed into the same piece of rail.

**Which route is picked**, if you have chosen *Over the shortest track* or *Over the longest track*. Both are measured in your lengths, and a section with no length counts as one - so with nothing measured they pick the route over the fewest, or the most, sections.

### When to set them

Measure **the squares a train comes to rest on, and the run back to the switch behind each one**. That is the stretch every rule above asks about. You do not have to measure the whole layout, and there is no benefit in measuring plain running line that nothing stops on.

**Example: a platform with points just behind it.** The run in from the previous sensor is 6, with a switch in the middle — 3 before it, 3 after.

- A **3-unit** train stops clear of the points. Nothing else on the layout is affected.
- A **6-unit** train fits the run in, but stands across the points. This is allowed at a station, because the train is passing through and will move on. While it is there, anything routed over those points is refused — so if they are the only way to another part of your layout, that part waits.
- A **7-unit** train is refused: it does not fit even the whole run in, so its back end would be somewhere nothing has been measured.

**Example: the same track, used as a parking berth.** A berth is somewhere a train *stays* — a square with **Can Be Chosen In Full Autonomy** switched off. Here the 6-unit train is refused, because its back end would sit on the points and shut that road for the rest of the session. A platform may be blocked for a minute; a siding would block it all evening.

> To park long trains, give the berth enough measured track **past** the switch behind it — or park them at a platform instead.

**Example: a platform you want to keep short trains at.** Your platform measures 8 and you never want more than 4 units there. Set **Advanced Parameters...** -> maximum train length to 4. The measurement stays 8, because that is what says whether a train fits; the 4 is your preference on top.

### The one thing to watch

**Measure a whole run, or none of it.** An unmeasured square counts as nothing at all. A half-measured approach therefore understates how much room there is — which is safe, it refuses more than it needs to — but it also makes a *standing* train look longer than it is, because its back end runs over the unmeasured squares for free and blocks them too.

So if a short train seems to be blocking a surprising amount of track, the answer is almost always an unmeasured square behind it, not a fault. Set its length and watch the orange line shrink.

**The square a train is standing on is track, and it is spent first.** A 2-unit train on a platform square measured 2 fits on that square and blocks nothing behind it; a 3-unit train there lies one unit back over the track behind the platform, and that track is blocked. How long a train a station will take is a separate setting - Maximum Train Length on the station's right-click menu - and says only which trains may be sent there.

---

## Watching it run

While autonomy is running the diagram shows you what is happening, and it is worth learning to read.

**A train's route is drawn along the track.** Red for the track ahead of it, green for the track it has already covered, black arrows for which way it is going. The line follows the track through curves and switches rather than cutting across them, so it reads as a route rather than as an overlay.

**Station names are shown on the diagram.** In the autonomy editor, right-click a square beside a station and choose **Show a Station Name Here...** (or press Control+N over it). The caption dropdown then chooses what every such name shows: the station, the locomotive parked there, or its home locomotive. A text label typed as `Point:StationName` in an older version is taken over the first time the setup opens; one typed today is shown as plain text.

**A signal paired with a station** goes red while a train is standing there, and green again once it leaves. Pair one by right-clicking the station and picking the signal — either by clicking it on the diagram, or by typing its address.

**The locomotive list** shows each train, where it is, and where it can go. Double-click a destination to send a train there yourself.

> **[Screenshot not yet captured — assets/automation/07-running.png]** a running layout, with a route drawn in red and green and a train's name showing at a station

**Gracefully Stop Autonomy** lets every train finish the route it is on and then stops. It is almost always what you want; the emergency stop is for emergencies.

---

## Choosing how trains pick their route

When more than one route will do, TrainControl has to choose. The **Routing Logic** dropdown on the **Autonomy Settings** tab says how:

| Setting | What it does |
| --- | --- |
| At random, respecting priority | Picks any of them, highest-priority stations first. This is the behaviour TrainControl has always had, and it stays the default |
| Completely at random | Picks any of them, to any station - station priority is ignored |
| Past the fewest stations | The most direct route |
| Past the most stations | Trains call at things on the way rather than going straight there |
| Over the shortest track | By measured length; a section with no length counts as one |
| Over the longest track | The scenic route |
| Across the fewest sensors | Fewest reporting points on the way |
| Across the most sensors | The busiest-looking route |
| Whichever station has gone longest without a train | For a layout with a favourite loop, so the far corner still gets visited. Station priority still applies first |
| Weighing station priority against distance | The one rule that crosses priorities: a near ordinary station can beat a distant important one |

The "most" and "longest" settings exist for a layout that should look busy rather than efficient. On a small layout they are the difference between a train shuttling back and forth and a train that appears to be going somewhere.

The choice is saved with the autonomy configuration, so two configurations can use different rules. Stations you have marked as higher priority are chosen first under every rule except *Completely at random*, which ignores priority, and *Weighing station priority against distance*, which trades it against how far away a station is.

---

## Timetables: recording a sequence and playing it back

Autonomy running on its own is random by design. A timetable is the opposite: a sequence you recorded once, played back the same way each time.

**To record one:** press `Capture Locomotive Commands`, then either start autonomy or send trains yourself. Every completed route is recorded, along with how long it was before the next one started. Press the button again to stop recording.

**To play it back:** press `Execute Timetable`. Each entry runs in turn, waiting for the one before it to arrive rather than merely to set off.

**Two things worth knowing.** Capture **appends** — recording again adds to what is already there rather than replacing it, so clear the timetable first if that is not what you want. And a timetable is recorded from a particular arrangement of trains: play it back with the trains somewhere else and the first entry will not run, because the train it names is not where it was.

It is worth recording a timetable that ends where it began. That way it can be run again and again.

> **[Screenshot not yet captured — assets/automation/08-timetable.png]** the timetable panel with several captured entries

---

## Sending everything home

`Return Locomotives Home` sends every locomotive back to the station it belongs at. By default that is the station it was standing on when the layout was loaded; you can say otherwise by right-clicking a station and picking `Home locomotive`.

Getting everyone home is rarely as simple as driving each train to its own station, because a station holds one train at a time — a train cannot go home while another is standing there. TrainControl works out an order that succeeds, moving trains out of each other's way and bringing them back afterwards where that is what it takes.

Trains must be stopped first, so use `Gracefully Stop Autonomy` if autonomy is running. If no arrangement can be found you are told so and nothing moves.

To see the homes on the diagram, set the caption dropdown to **Homes**: each station caption then names its home locomotive, in black when that locomotive is standing there and in white on dark grey when it is somewhere else. The white ones are exactly what `Return Locomotives Home` would move.

---

## Settings, and what each one is for

These live under `Autonomy` -> `Autonomy Settings...`. Most layouts need to change two or three of them at most.

| Setting | What it is for |
| --- | --- |
| Minimum and maximum delay | How long a train waits at a station before leaving again. A range rather than a number, so departures do not fall into lockstep |
| Default speed | Used for a locomotive with no preferred speed of its own |
| Pre-arrival speed reduction | How much a train slows on the approach. This is what the third sensor is for |
| Maximum active trains | How many run at once. Zero means as many as the track allows |
| Atomic routes | Whether a train reserves its whole route before setting off, or releases track behind it as it goes. Off is more capable and needs your lengths to be right |
| Train lengths | `Advanced Parameters...` on a station sets its maximum train length; a train too long for it will not be sent there. See **[Track lengths](#track-lengths-what-they-are-for)** for that and for the track measurements it sits on top of |
| Functions on departure and arrival | Whether each locomotive's preferred functions are switched on when it leaves and off when it arrives. Turn the arrival one off to keep sound running between routes |
| Locomotive exclusions | Trains that must not stop at a particular station. Set on a non-station instead, and those trains will not pass through it at all |
| Maximum inactive seconds | A train that has not run for this long is prioritised, so nothing sits forgotten |
| Maximum latency | Cuts track power if the network to the Central Station gets too slow. Off by default |
| Linked routes | Routes to activate while autonomy runs — for emergency stops, sound effects, or safety signals |

Every one of these has a fuller description in **[the programmatic guide](AutomationAPI.md)**, which is also where to look if you want to set them from Java.

---

## When trains will not move

This is the section to read first when nothing happens. In rough order of how often each turns out to be the answer:

**Every station is occupied.** A station holds one train at a time, and a train can only go to a station that is free. On a layout with as many trains as stations, nothing can move. Take a train off, or add somewhere for one to go.

**The train has nowhere to go.** Look at the locomotive list — it says so for each train in as many words. A train whose only destinations exclude it, or are the wrong direction, or are too short for it, has no route.

**A link that is needed is switched off.** Switched-off links are greyed on the diagram. It is worth a glance along the route you expect the train to take.

**The destination is a reversing point.** Autonomy will not send trains to one on its own — see [Example 3](#example-3-a-terminus-and-trains-that-must-turn-round). This is deliberate, and the route menu will still take you there by hand.

**A locomotive was placed without a speed.** A train with no speed set will not be dispatched.

**The sensor is not reporting.** If TrainControl never sees the arrival, the train stays "running" forever and the track it holds is never released. Watch the feedback in the Central Station tab while pushing a train over the contact by hand.

**A switch or signal on the route is not in the database.** A route is not used if one of its accessories is missing, because the alternative is a train running over track that was never set.

**A train is standing somewhere in the way.** Not necessarily on the route itself — a train occupying a crossing or a shared block can hold up a route that merely passes nearby. A long train standing at a short platform reaches back further than it looks: the orange line on the diagram shows how far, and **[Track lengths](#track-lengths-what-they-are-for)** explains what to measure.

**The train is too long for everywhere it could go.** If you have set lengths, a train can run out of destinations simply by being long — the tooltip on "No available paths" says so for each station in turn. This is the rule doing its job, but it is worth checking the measurements are right before you shorten the train.

**Two places tell you which it is, rather than making you guess.**

In the locomotive list, hover over "No available paths". The tooltip names every station the train
might have been sent to and, for each one, the reason it was refused - occupied and by whom, switched
off, excluded, no track at all.

In the setup editor, the **Why not Moving?** tool answers the same question on the diagram.
Click the square a train is standing on: every route it *could* take is drawn on the track, and the
reasons for the ones it cannot are listed underneath. A train with somewhere to go draws lines; a
train with nowhere draws none, which is the same answer read from across the room.

That tool reads the configuration as last **saved**. If you have unsaved changes it says so, and
otherwise it does not mention it - so a plain answer is an answer about the railway in front of you.

The log is verbose about all of this too, and worth reading: it names the train, the route, and the
reason.

---

## Screenshots this guide still needs

The placeholders above want real pictures. Each is a single screen capture; the file names are the paths the guide already points at.

| File | What to capture |
| --- | --- |
| `assets/automation/01-overview.png` | The autonomy view of a small layout, with two stations marked and the link between them visible |
| `assets/automation/02-add-configuration.png` | The Autonomy menu open, with "Add a Configuration..." highlighted |
| `assets/automation/03-station-menu.png` | The right-click menu on a station square in the setup editor, open, showing Rename and Station |
| `assets/automation/04-two-trains.png` | A layout with one link greyed out and two trains running at once |
| `assets/automation/05-terminus.png` | A terminus station showing the reversing marker |
| `assets/automation/06-arrivals.png` | The Arrivals view with one direction switched off, and the resulting arrow on the diagram |
| `assets/automation/07-running.png` | A running layout: a route drawn in red and green, arrows, and a train's name showing at a station |
| `assets/automation/08-timetable.png` | The timetable panel with several captured entries in it |

**The easiest way to produce a clean diagram picture** is `Layout` -> `Save Current Track Diagram as a Picture...`, which writes the page you are looking at to a PNG at whatever size you ask for - the whole page, not just the part scrolled into view, and with none of the window around it. Sixty pixels per square reads well in a document; twenty is about what the screen shows.
