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

**A track diagram.** Either downloaded from your Central Station or drawn in TrainControl's own editor. Automation is set up on this diagram, so if your diagram does not yet match your railway, start there.

**Locomotives with addresses that work.** If you cannot drive a locomotive by hand from TrainControl, automation will not be able to either.

That is all. You do not need to describe your track anywhere: TrainControl reads the diagram and works out for itself which squares connect to which.

---

## The idea in one page

Automation rests on three things, and everything else in this guide is a refinement of one of them.

**Stations are where trains stop.** You do not create them: every sensor square on your diagram becomes a station when the setup is built, because a sensor is exactly the thing that can tell TrainControl a train has arrived. What you do is give them names, and say which ones trains may actually stop at as opposed to merely pass through.

**Links say where a train may go.** TrainControl traces the track on your diagram and works out the connections by itself. You do not draw them. What you do is switch off the few that you do not want used — a siding you would rather trains kept out of, or a connection that only makes sense in one direction.

**A locomotive has to be somewhere to start.** You tell TrainControl which train is standing at which station. From then on it keeps track by itself.

Press start, and TrainControl picks a train, picks somewhere it can go, sets the switches and signals along the way, sends it, and watches the sensors until it arrives. Then it does it again.

![PLACEHOLDER: the autonomy view of a small layout, with two stations marked and the link between them drawn](assets/automation/01-overview.png)

---

## Example 1: a shuttle between two stations

The simplest arrangement that runs: two stations, one train, one piece of track between them.

```
    [ A ]=========================[ B ]
      s88 1                        s88 2
```

**1. Build the setup.** `Autonomy` → `Add a Configuration...`. This reads your track diagram as it stands and works out the railway from it: every sensor becomes a station, and the track between them becomes the links trains can use. Nothing is asked of you yet.

![PLACEHOLDER: the Autonomy menu, with "Add a Configuration..." highlighted](assets/automation/02-add-configuration.png)

**2. Open the editor.** `Autonomy` → `Edit Autonomy on Page`, and pick your page. The diagram looks the same; what changes is that right-clicking a square now sets automation up rather than throwing a switch.

**3. Name the two stations.** Right-click each of the sensor squares at A and B and choose `Rename...`. The name is what you will see in every list and every log line, and what an arrival is announced under — so name them the way you talk about them out loud.

![PLACEHOLDER: the right-click menu on a station square in the setup editor](assets/automation/03-station-menu.png)

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

![PLACEHOLDER: a diagram with one link greyed out, and two trains running](assets/automation/04-two-trains.png)

**Try this too.** Right-click C, open `Advanced Parameters...`, and set `Station Priority` higher than the others. Trains will now favour calling there. Priority does not force it — it tips the choice.

---

## Example 3: a terminus, and trains that must turn round

A terminus is a station where the track stops. A train arriving must leave the way it came, which means reversing.

```
    [ A ]===========[ B ]===========[ T ]
                                      terminus
```

Name T as before, and then set `Changing Direction` on it so that a train arriving may turn round.

This tells TrainControl two things. First, a train that arrives here will need to change direction before it can leave — so the locomotive must actually be able to do that. Second, and less obviously: a reversing point is treated as somewhere to **park**, not somewhere to route trains through. Autonomy running on its own will not send trains there and will not drive them through it on the way somewhere else.

That is deliberate, and it is worth understanding because it surprises people. Reversing points are usually parking tracks and shunting necks, and trains being parked at random in the shunting neck — or stopping and changing direction in the middle of a run — is not operation, it is chaos. So autonomy leaves them alone.

**You can still use it.** Send a train there yourself from the route menu, and [Return Locomotives Home](#sending-everything-home) will still park trains there. What will not happen is a train ending up there because a dice roll put it there.

![PLACEHOLDER: a terminus station on the diagram, showing the reversing marker](assets/automation/05-terminus.png)

---

## Example 4: a through station that only takes trains one way

Some stations only make sense to arrive at from one direction. A platform on a one-way loop; a bay that faces east; a station where arriving from the west means fouling a junction.

Right-click the station and open **Trains May Arrive...** in the setup editor. By default a station accepts trains from every direction. Switch off the directions you do not want, and TrainControl will only route trains to it from the ones you left on.

The diagram marks this: a station that only takes trains one way shows a small arrow. That arrow is the only outward sign, so it is worth knowing what it means when you meet one on somebody else's layout.

![PLACEHOLDER: the Arrivals view for a station, with one direction switched off, and the resulting arrow on the diagram](assets/automation/06-arrivals.png)

**Where this matters most** is a station that is really two platforms. On the diagram it is one square, but a train arriving from the east and a train arriving from the west are doing different things — different switches set, different track occupied. Arrival directions are how you say which of those you want.

---

## Watching it run

While autonomy is running the diagram shows you what is happening, and it is worth learning to read.

**A train's route is drawn along the track.** Red for the track ahead of it, green for the track it has already covered, black arrows for which way it is going. The line follows the track through curves and switches rather than cutting across them, so it reads as a route rather than as an overlay.

**Station labels show what is standing there.** A square marked `Point:StationName` as a text label shows the name of any locomotive at that station.

**A signal paired with a station** goes red while a train is standing there, and green again once it leaves. Pair one by right-clicking the station and picking the signal — either by clicking it on the diagram, or by typing its address.

**The locomotive list** shows each train, where it is, and where it can go. Double-click a destination to send a train there yourself.

![PLACEHOLDER: a running layout, with a route drawn in red and green and a train's name showing at a station](assets/automation/07-running.png)

**Gracefully Stop Autonomy** lets every train finish the route it is on and then stops. It is almost always what you want; the emergency stop is for emergencies.

---

## Choosing how trains pick their route

When more than one route will do, TrainControl has to choose. Under the **Autonomy** menu, **Route Choice**, you can say how:

| Setting | What it does |
| --- | --- |
| At random | Picks any of them. This is the behaviour TrainControl has always had, and it stays the default |
| Past the fewest stations | The most direct route |
| Past the most stations | Trains call at things on the way rather than going straight there |
| Over the shortest track | By measured length, if you have set lengths |
| Over the longest track | The scenic route |
| Across the fewest sensors | Fewest reporting points on the way |
| Across the most sensors | The busiest-looking route |

The "most" and "longest" settings exist for a layout that should look busy rather than efficient. On a small layout they are the difference between a train shuttling back and forth and a train that appears to be going somewhere.

This applies to every layout rather than to one configuration, and stations you have marked as higher priority are still chosen first either way.

---

## Timetables: recording a sequence and playing it back

Autonomy running on its own is random by design. A timetable is the opposite: a sequence you recorded once, played back the same way each time.

**To record one:** press `Capture Locomotive Commands`, then either start autonomy or send trains yourself. Every completed route is recorded, along with how long it was before the next one started. Press the button again to stop recording.

**To play it back:** press `Execute Timetable`. Each entry runs in turn, waiting for the one before it to arrive rather than merely to set off.

**Two things worth knowing.** Capture **appends** — recording again adds to what is already there rather than replacing it, so clear the timetable first if that is not what you want. And a timetable is recorded from a particular arrangement of trains: play it back with the trains somewhere else and the first entry will not run, because the train it names is not where it was.

It is worth recording a timetable that ends where it began. That way it can be run again and again.

![PLACEHOLDER: the timetable panel with several captured entries](assets/automation/08-timetable.png)

---

## Sending everything home

`Return Locomotives Home` sends every locomotive back to the station it belongs at. By default that is the station it was standing on when the layout was loaded; you can say otherwise by right-clicking a station and picking `Home locomotive`.

Getting everyone home is rarely as simple as driving each train to its own station, because a station holds one train at a time — a train cannot go home while another is standing there. TrainControl works out an order that succeeds, moving trains out of each other's way and bringing them back afterwards where that is what it takes.

Trains must be stopped first, so use `Gracefully Stop Autonomy` if autonomy is running. If no arrangement can be found you are told so and nothing moves.

A station with a home locomotive is outlined in teal on the diagram: solid when that locomotive is standing there, dotted when it is somewhere else. The dotted ones are exactly what `Return Locomotives Home` would move.

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
| Train lengths | `Advanced Parameters...` on a station sets its maximum train length; a train too long for it will not be sent there |
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

**A train is standing somewhere in the way.** Not necessarily on the route itself — a train occupying a crossing or a shared block can hold up a route that merely passes nearby.

**Two places tell you which it is, rather than making you guess.**

In the locomotive list, hover over "No available paths". The tooltip names every station the train
might have been sent to and, for each one, the reason it was refused - occupied and by whom, switched
off, excluded, no track at all.

In the setup editor, the **Why is it not moving?** tool answers the same question on the diagram.
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

**The easiest way to produce a clean diagram picture** is `Layout` → `Save Diagram as a Picture...`, which writes the whole of a page to a PNG at whatever size you ask for — the whole page, not just the part scrolled into view, and with none of the window around it. Sixty pixels per square reads well in a document; twenty is about what the screen shows.
