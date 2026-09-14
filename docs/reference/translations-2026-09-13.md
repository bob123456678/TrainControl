# The sentences added on 2026-09-12 and 2026-09-13, for your eye

Twenty-seven new messages, in the eight languages TrainControl ships. Regenerated after your notes of 2026-09-13; each note is kept under the entry it changed. The English is mine
and the other seven are machine-written; nobody who speaks them has read them.

**How to use this:** write under any line that is wrong - a correction, or just "no" and
what it should say. I will put your words into the bundles verbatim. Anything you leave
alone I will take as passable. `{0}`, `{1}` are substituted at runtime - a locomotive name,
a station, a number - so they have to survive wherever the sentence puts them.

The German and Polish are the two you said you wanted looked at hardest.

---

## 1. `autolayout.errorBerthWouldFoulAnotherRoad`

*a warning in the log.*

- **English** — {0} (length {3}) is too long for the parking berth at {1} and would stand across {2}, which other trains need - park a shorter train here, or send this one somewhere longer
- **German** — {0} (Länge {3}) ist zu lang für das Abstellgleis {1} und würde über {2} stehen, das andere Züge brauchen - stellen Sie hier einen kürzeren Zug ab oder schicken Sie diesen auf ein längeres Gleis
- **French** — {0} (longueur {3}) est trop long pour le garage {1} et se retrouverait à cheval sur {2}, dont d’autres trains ont besoin - garez ici un train plus court, ou envoyez celui-ci sur une voie plus longue
- **Spanish** — {0} (longitud {3}) es demasiado largo para el apartadero {1} y quedaría sobre {2}, que otros trenes necesitan - estacione aquí un tren más corto o envíe este a una vía más larga
- **Italian** — {0} (lunghezza {3}) è troppo lungo per il binario di sosta {1} e resterebbe a cavallo di {2}, che serve ad altri treni - parcheggia qui un treno più corto, o manda questo su un binario più lungo
- **Dutch** — {0} (lengte {3}) is te lang voor het opstelspoor {1} en zou over {2} staan, dat andere treinen nodig hebben - zet hier een kortere trein neer, of stuur deze naar een langer spoor
- **Danish** — {0} (længde {3}) er for lang til opstillingssporet {1} og ville stå hen over {2}, som andre tog skal bruge - stil et kortere tog her, eller send dette til et længere spor
- **Polish** — {0} (długość {3}) jest za długi na tor postojowy {1} i stałby na {2}, który jest potrzebny innym pociągom - zaparkuj tu krótszy pociąg albo wyślij ten na dłuższy tor

> 

## 2. `autolayout.log.noPlainCopyToStandOn`

*a log line.*

- **English** — {0} only leads back the way the train came in, so it could not keep its direction there - it will have to turn round to leave.
- **German** — Von {0} geht es nur auf dem Weg zurück, auf dem der Zug gekommen ist, daher konnte er dort seine Richtung nicht beibehalten - er muss wenden, um weiterzufahren.
- **French** — {0} ne mène que par où le train est arrivé : il n’a donc pas pu garder sa direction et devra faire demi-tour pour repartir.
- **Spanish** — {0} solo lleva de vuelta por donde entró el tren, así que no pudo mantener su dirección allí: tendrá que dar la vuelta para salir.
- **Italian** — Da {0} si torna solo da dove è arrivato il treno, quindi non ha potuto mantenere la direzione: dovrà invertire per ripartire.
- **Dutch** — Vanaf {0} kun je alleen terug zoals de trein binnenkwam, dus hij kon daar zijn richting niet houden - hij moet keren om te vertrekken.
- **Danish** — Fra {0} kan man kun køre tilbage ad den vej, toget kom ind, så det kunne ikke beholde sin retning der - det må vende for at køre derfra.
- **Polish** — Z {0} można wrócić tylko tą drogą, którą pociąg przyjechał, więc nie mógł tam zachować kierunku - musi zawrócić, żeby odjechać.

> **Your note:** the user doesn't have visibility into these copies - express it in terms of what the station is. Will this error ever be triggered given our new fixes?
>
> **Done:** reworded in station terms, in all eight languages, and it now names the station rather than an internal Point. **Can it still fire:** only for a train that CAN reverse, told to keep its direction, at a may-reverse station that is a dead end on the side it came in by. None of your four may-reverse squares is shaped like that, so on your railway today it cannot.

## 3. `autolayout.log.stoodOnTheCopyItFaces`

*a log line.*

- **English** — The train at {0} was asked to keep its direction, and it has.
- **German** — Der Zug auf {0} sollte seine Richtung beibehalten, und das hat er.
- **French** — Le train en {0} devait garder sa direction, et c’est ce qu’il a fait.
- **Spanish** — Se pidió al tren en {0} que mantuviera su dirección, y así lo ha hecho.
- **Italian** — Al treno a {0} è stato chiesto di mantenere la direzione, e l’ha fatto.
- **Dutch** — De trein op {0} moest zijn richting houden, en dat heeft hij gedaan.
- **Danish** — Toget ved {0} skulle beholde sin retning, og det har det gjort.
- **Polish** — Pociąg na {0} miał zachować kierunek i tak zrobił.

> **Your note:** same issue with the copy of the square, that is not something the user will understand.
>
> **Done:** reworded to say only that the train kept its direction, and it names the station rather than two internal Points.

## 4. `autolayout.ui.askFacing`

*a dialog or menu.*

- **English** — Which way should the train at {0} face?  This square is one trains may turn at, so it can face either way.
- **German** — In welche Richtung soll der Zug auf {0} zeigen?  Auf diesem Feld dürfen Züge wenden, es kann also in beide Richtungen zeigen.
- **French** — Dans quel sens le train en {0} doit-il être orienté ?  Sur cette case, les trains peuvent faire demi-tour : les deux sens sont possibles.
- **Spanish** — ¿Hacia dónde debe apuntar el tren en {0}?  En este cuadro los trenes pueden dar la vuelta, así que puede apuntar hacia cualquier lado.
- **Italian** — In che direzione deve essere rivolto il treno su {0}?  In questo riquadro i treni possono invertire, quindi può essere rivolto in entrambi i versi.
- **Dutch** — Welke kant moet de trein op {0} op wijzen?  Op dit vak mogen treinen keren, dus beide richtingen kunnen.
- **Danish** — Hvilken vej skal toget på {0} vende?  På dette felt må tog køre i begge retninger.
- **Polish** — W którą stronę ma być zwrócony pociąg na {0}?  Na tym polu pociągi mogą zawracać, więc może być zwrócony w obie strony.

> **Your correction to the Danish is in, verbatim.**

## 5. `autolayout.ui.askFacingTitle`

*a dialog or menu.*

- **English** — Which way is it facing?
- **German** — In welche Richtung zeigt er?
- **French** — Dans quel sens est-il orienté ?
- **Spanish** — ¿Hacia dónde apunta?
- **Italian** — In che direzione è rivolto?
- **Dutch** — Welke kant wijst hij op?
- **Danish** — Hvilken vej vender det?
- **Polish** — W którą stronę jest zwrócony?

> 

## 6. `autolayout.ui.confirmHomeEveryTrainWhereItStands`

*a dialog or menu.*

- **English** — Make each of the {0} placed train(s) at home where it now stands?\n\nA train that already has a home somewhere else loses it, and a square that is already another train’s home is reassigned.  There is no undo.
- **German** — Jeden der {0} platzierten Züge dort beheimaten, wo er jetzt steht?\n\nEin Zug, der anderswo bereits eine Heimat hat, verliert sie, und ein Feld, das bereits die Heimat eines anderen Zuges ist, wird neu zugewiesen.  Es gibt kein Rückgängig.
- **French** — Domicilier chacun des {0} trains placés là où il se trouve maintenant ?\n\nUn train qui a déjà un domicile ailleurs le perd, et une case qui est déjà le domicile d’un autre train est réattribuée.  Il n’y a pas d’annulation.
- **Spanish** — ¿Asignar a cada uno de los {0} trenes colocados su posición actual como base?\n\nUn tren que ya tenga base en otro sitio la pierde, y un cuadro que ya sea base de otro tren se reasigna.  No se puede deshacer.
- **Italian** — Assegnare a ciascuno dei {0} treni posizionati la sua posizione attuale come sede?\n\nUn treno che ha già una sede altrove la perde, e un riquadro che è già la sede di un altro treno viene riassegnato.  Non si può annullare.
- **Dutch** — Elk van de {0} geplaatste treinen thuisbrengen waar hij nu staat?\n\nEen trein die al ergens anders een thuis heeft, verliest dat, en een vak dat al het thuis van een andere trein is, wordt opnieuw toegewezen.  Ongedaan maken kan niet.
- **Danish** — Skal hvert af de {0} placerede tog have hjemsted, hvor det står nu?\n\nEt tog, der allerede har hjemsted et andet sted, mister det, og et felt, der allerede er et andet togs hjemsted, tildeles på ny.  Der er ingen fortrydelse.
- **Polish** — Ustawić obecne położenie każdego z {0} ustawionych pociągów jako jego bazę?\n\nPociąg, który ma już bazę gdzie indziej, traci ją, a pole będące już bazą innego pociągu zostanie przypisane na nowo.  Nie można tego cofnąć.

> 

## 7. `autolayout.ui.errorCannotReachHome`

*a dialog or menu.*

- **English** — These locomotives cannot reach their home station at all: {0}.  Check the route, or use the autonomy editor to view details.
- **German** — Diese Lokomotiven erreichen ihren Heimatbahnhof überhaupt nicht: {0}.  Prüfen Sie die Route oder öffnen Sie den Autonomie-Editor für Details.
- **French** — Ces locomotives ne peuvent pas du tout atteindre leur gare d’attache : {0}.  Vérifiez l’itinéraire, ou ouvrez l’éditeur d’autonomie pour les détails.
- **Spanish** — Estas locomotoras no pueden llegar a su estación base en absoluto: {0}.  Compruebe la ruta o use el editor de autonomía para ver los detalles.
- **Italian** — Queste locomotive non riescono a raggiungere la loro stazione di appartenenza: {0}.  Controlla il percorso, o usa l’editor di autonomia per i dettagli.
- **Dutch** — Deze locomotieven kunnen hun thuisstation helemaal niet bereiken: {0}.  Controleer de route, of gebruik de autonomie-editor voor details.
- **Danish** — Disse lokomotiver kan slet ikke nå deres hjemmestation: {0}.  Tjek ruten, eller brug autonomi-editoren for at se detaljer.
- **Polish** — Te lokomotywy w ogóle nie mogą dotrzeć do swojej stacji macierzystej: {0}.  Sprawdź trasę albo użyj edytora autonomii, aby zobaczyć szczegóły.

> 

## 8. `autolayout.ui.menuHomeEveryTrainWhereItStands`

*a dialog or menu.*

- **English** — Home All Trains Where They Stand ({0})
- **German** — Alle Züge dort beheimaten, wo sie stehen ({0})
- **French** — Domicilier chaque train là où il se trouve ({0})
- **Spanish** — Asignar como base la posición actual de cada tren ({0})
- **Italian** — Assegna a ogni treno la posizione attuale come sede ({0})
- **Dutch** — Alle treinen thuisbrengen waar ze staan ({0})
- **Danish** — Hjemsted for alle tog, hvor de holder ({0})
- **Polish** — Ustaw obecne położenie każdego pociągu jako jego bazę ({0})

> 

## 9. `autolayout.warnEdgePlacesIncomplete`

*a warning in the log.*

- **English** — The track list for {0} is incomplete, so the whole run is treated as occupied when a train stands on it.  Rebuild the diagram to restore it.
- **German** — Die Gleisliste für {0} ist unvollständig; der gesamte Abschnitt gilt daher als belegt, wenn dort ein Zug steht.  Bauen Sie das Gleisbild neu auf, um sie wiederherzustellen.
- **French** — La liste des voies de {0} est incomplète : tout le parcours est donc considéré comme occupé lorsqu’un train y stationne.  Reconstruisez le synoptique pour la rétablir.
- **Spanish** — La lista de vías de {0} está incompleta, por lo que todo el tramo se considera ocupado cuando hay un tren parado en él.  Reconstruya el diagrama para restaurarla.
- **Italian** — L’elenco dei binari di {0} è incompleto, quindi l’intero tratto risulta occupato quando vi sosta un treno.  Ricostruisci lo schema per ripristinarlo.
- **Dutch** — De spoorlijst van {0} is onvolledig, dus het hele traject geldt als bezet zodra er een trein staat.  Bouw het schema opnieuw op om dit te herstellen.
- **Danish** — Sporlisten for {0} er ufuldstændig, så hele strækningen regnes som optaget, når et tog holder på den.  Genopbyg diagrammet for at rette det.
- **Polish** — Lista torów dla {0} jest niepełna, więc cały odcinek jest traktowany jako zajęty, gdy stoi na nim pociąg.  Odbuduj schemat, aby ją przywrócić.

> 

## 10. `autolayout.warnRemovedConflictingLocomotive`

*a warning in the log.*

- **English** — Auto layout warning: {0} has been taken off {1}, because it cannot run there at the same time as {2}.
- **German** — Autonomie-Warnung: {0} wurde von {1} entfernt, weil die Lok dort nicht gleichzeitig mit {2} fahren kann.
- **French** — Avertissement d’autonomie: {0} a été retiré de {1}, car il ne peut pas y circuler en même temps que {2}.
- **Spanish** — Aviso de autonomía: se ha retirado {0} de {1}, porque no puede circular allí al mismo tiempo que {2}.
- **Italian** — Avviso dell’autonomia: {0} è stato tolto da {1}, perché non può circolare lì insieme a {2}.
- **Dutch** — Autonomiewaarschuwing: {0} is van {1} gehaald, omdat die daar niet tegelijk met {2} kan rijden.
- **Danish** — Autonomiadvarsel: {0} er taget af {1}, fordi den ikke kan køre der samtidig med {2}.
- **Polish** — Ostrzeżenie autonomii: {0} został zdjęty z {1}, ponieważ nie może tam jechać jednocześnie z {2}.

> 

## 11. `autosetup.log.captionsMigrated`

*a log line.*

- **English** — Station names written on the track diagram by an earlier version have been taken into the autonomy setup and removed from these pages: {0} ({1} name(s) in all).  Each of those pages has a .bak file beside its own .cs2, holding the page as it stood the first time this version rewrote it.
- **German** — Von einer früheren Version in den Gleisplan geschriebene Bahnhofsnamen wurden in die Autonomie-Konfiguration übernommen und von diesen Seiten entfernt: {0} ({1} Name(n) insgesamt).  Neben der .cs2 jeder dieser Seiten liegt eine .bak-Datei mit der Seite, wie sie beim ersten Umschreiben durch diese Version aussah.
- **French** — Les noms de gare inscrits sur le plan par une version antérieure ont été repris dans la configuration d’autonomie et retirés de ces pages: {0} ({1} nom(s) au total).  À côté du .cs2 de chacune se trouve un fichier .bak contenant la page telle qu’elle était la première fois que cette version l’a réécrite.
- **Spanish** — Los nombres de estación escritos en el diagrama por una versión anterior se han pasado a la configuración de autonomía y se han quitado de estas páginas: {0} ({1} nombre(s) en total).  Junto al .cs2 de cada una hay un archivo .bak con la página tal como estaba la primera vez que esta versión la reescribió.
- **Italian** — I nomi di stazione scritti sullo schema da una versione precedente sono stati portati nella configurazione dell’autonomia e rimossi da queste pagine: {0} ({1} nome/i in tutto).  Accanto al .cs2 di ciascuna c’è un file .bak con la pagina com’era la prima volta che questa versione l’ha riscritta.
- **Dutch** — Stationsnamen die een eerdere versie op het spoorplan schreef, zijn overgenomen in de autonomie-instellingen en van deze pagina’s verwijderd: {0} ({1} na(a)m(en) in totaal).  Naast de .cs2 van elke pagina staat een .bak-bestand met de pagina zoals die was toen deze versie hem voor het eerst herschreef.
- **Danish** — Stationsnavne, som en tidligere version skrev på sporplanen, er overført til autonomiopsætningen og fjernet fra disse sider: {0} ({1} navn(e) i alt).  Ved siden af hver af disse siders .cs2 ligger en .bak-fil med siden, som den så ud, første gang denne version omskrev den.
- **Polish** — Nazwy stacji zapisane na planie torów przez wcześniejszą wersję zostały przeniesione do konfiguracji autonomii i usunięte z tych stron: {0} (łącznie {1} nazw).  Obok pliku .cs2 każdej z nich leży plik .bak z tą stroną w postaci sprzed pierwszego zapisu przez tę wersję.

> 

## 12. `autosetup.log.editSessionNotCleared`

*a log line.*

- **English** — Could not clear the record of this editing session.
- **German** — Der Datensatz dieser Bearbeitungssitzung konnte nicht gelöscht werden.
- **French** — L’enregistrement de cette session d’édition n’a pas pu être effacé.
- **Spanish** — No se pudo borrar el registro de esta sesión de edición.
- **Italian** — Non è stato possibile cancellare il registro di questa sessione di modifica.
- **Dutch** — De registratie van deze bewerkingssessie kon niet worden gewist.
- **Danish** — Registreringen af denne redigeringssession kunne ikke ryddes.
- **Polish** — Nie udało się wyczyścić zapisu tej sesji edycji.

> 

## 13. `autosetup.log.noteRepairFailed`

*a log line.*

- **English** — A record of an unfinished layout edit could not be updated for the renamed locomotive, so it has been left naming the old one: config/autonomy/setup-before-edit.json.  If TrainControl is restarted before that edit is finished, the setup it puts back will name a locomotive that no longer exists - delete that file to prevent it.
- **German** — Ein Datensatz einer nicht abgeschlossenen Gleisplanbearbeitung konnte für die umbenannte Lok nicht aktualisiert werden und nennt weiterhin die alte: config/autonomy/setup-before-edit.json.  Wird TrainControl neu gestartet, bevor diese Bearbeitung abgeschlossen ist, nennt die wiederhergestellte Konfiguration eine Lok, die es nicht mehr gibt - löschen Sie die Datei, um das zu verhindern.
- **French** — Un enregistrement d’une modification du plan inachevée n’a pas pu être mis à jour pour la locomotive renommée; il nomme toujours l’ancienne: config/autonomy/setup-before-edit.json.  Si TrainControl est redémarré avant la fin de cette modification, la configuration restaurée nommera une locomotive qui n’existe plus - supprimez ce fichier pour l’éviter.
- **Spanish** — No se pudo actualizar para la locomotora renombrada un registro de una edición del trazado sin terminar, por lo que sigue nombrando a la anterior: config/autonomy/setup-before-edit.json.  Si se reinicia TrainControl antes de terminar esa edición, la configuración restaurada nombrará una locomotora que ya no existe: borre ese archivo para evitarlo.
- **Italian** — Un registro di una modifica allo schema non completata non ha potuto essere aggiornato per la locomotiva rinominata e continua a nominare quella vecchia: config/autonomy/setup-before-edit.json.  Se TrainControl viene riavviato prima che quella modifica sia finita, la configurazione ripristinata nominerà una locomotiva che non esiste più: elimini quel file per evitarlo.
- **Dutch** — Een registratie van een onvoltooide wijziging van het spoorplan kon niet worden bijgewerkt voor de hernoemde locomotief en noemt nog steeds de oude: config/autonomy/setup-before-edit.json.  Wordt TrainControl herstart voordat die wijziging klaar is, dan noemt de teruggezette configuratie een locomotief die niet meer bestaat - verwijder dat bestand om dat te voorkomen.
- **Danish** — En registrering af en uafsluttet sporplanredigering kunne ikke opdateres for det omdøbte lokomotiv og nævner stadig det gamle: config/autonomy/setup-before-edit.json.  Genstartes TrainControl, før den redigering er færdig, vil den gendannede opsætning nævne et lokomotiv, der ikke findes mere - slet filen for at undgå det.
- **Polish** — Zapisu niedokończonej edycji planu torów nie udało się zaktualizować dla zmienionej nazwy lokomotywy, więc nadal wskazuje starą: config/autonomy/setup-before-edit.json.  Jeśli TrainControl zostanie uruchomiony ponownie przed dokończeniem tej edycji, przywrócona konfiguracja będzie wskazywać lokomotywę, która już nie istnieje - usuń ten plik, aby temu zapobiec.

> 

## 14. `autosetup.log.placementsNotSaved`

*a log line.*

- **English** — Where each locomotive finished has not been saved, because a setup edit made while autonomy was running could not be applied at the time.  Saving would have written the older layout back over that edit.  The edit itself is safe and will be there next time.
- **German** — Wo die einzelnen Loks stehen geblieben sind, wurde nicht gespeichert, weil eine während des laufenden Betriebs vorgenommene Änderung damals nicht angewendet werden konnte.  Beim Speichern wäre die ältere Anlage über diese Änderung geschrieben worden.  Die Änderung selbst ist sicher und beim nächsten Mal vorhanden.
- **French** — L’endroit où chaque locomotive s’est arrêtée n’a pas été enregistré, car une modification faite pendant que l’autonomie tournait n’a pas pu être appliquée alors.  L’enregistrement aurait écrasé cette modification par l’ancien réseau.  La modification elle-même est intacte et sera là la prochaine fois.
- **Spanish** — No se ha guardado dónde terminó cada locomotora, porque un cambio hecho mientras la autonomía estaba en marcha no pudo aplicarse entonces.  Guardar habría escrito el trazado anterior sobre ese cambio.  El cambio en sí está a salvo y estará ahí la próxima vez.
- **Italian** — Non è stato salvato dove si è fermata ogni locomotiva, perché una modifica fatta mentre l’autonomia era in funzione non ha potuto essere applicata in quel momento.  Il salvataggio avrebbe riscritto il tracciato precedente sopra quella modifica.  La modifica stessa è al sicuro e ci sarà la prossima volta.
- **Dutch** — Waar elke locomotief is gestopt, is niet opgeslagen, omdat een wijziging die tijdens het rijden werd gemaakt toen niet kon worden toegepast.  Opslaan zou de oudere baan over die wijziging heen hebben geschreven.  De wijziging zelf is veilig en is er de volgende keer.
- **Danish** — Hvor hvert lokomotiv endte, er ikke gemt, fordi en ændring foretaget under drift ikke kunne anvendes dengang.  En lagring ville have skrevet det ældre anlæg hen over den ændring.  Selve ændringen er sikker og er der næste gang.
- **Polish** — Nie zapisano, gdzie zatrzymała się każda lokomotywa, ponieważ zmiana wprowadzona podczas pracy autonomii nie mogła wtedy zostać zastosowana.  Zapis nadpisałby tę zmianę starszym układem.  Sama zmiana jest bezpieczna i będzie na miejscu następnym razem.

> 

## 15. `autosetup.log.restoreFailedAfterCancel`

*a log line.*

- **English** — Could not put the autonomy setup back after cancelling.
- **German** — Die Autonomie-Konfiguration konnte nach dem Abbrechen nicht wiederhergestellt werden.
- **French** — La configuration d’autonomie n’a pas pu être restaurée après l’annulation.
- **Spanish** — No se pudo restaurar la configuración de autonomía tras cancelar.
- **Italian** — Non è stato possibile ripristinare la configurazione dell’autonomia dopo l’annullamento.
- **Dutch** — De autonomie-instellingen konden na het annuleren niet worden hersteld.
- **Danish** — Autonomiopsætningen kunne ikke gendannes efter annullering.
- **Polish** — Nie udało się przywrócić konfiguracji autonomii po anulowaniu.

> 

## 16. `autosetup.log.saveFailedAfterCombine`

*a log line.*

- **English** — Could not save the autonomy setup after combining pages, so the new page may be offered to autonomy the next time the setup is loaded.
- **German** — Die Autonomie-Konfiguration konnte nach dem Zusammenführen von Seiten nicht gespeichert werden; die neue Seite wird der Autonomie beim nächsten Laden möglicherweise angeboten.
- **French** — La configuration d’autonomie n’a pas pu être enregistrée après la fusion des pages; la nouvelle page pourra être proposée à l’autonomie au prochain chargement.
- **Spanish** — No se pudo guardar la configuración de autonomía tras combinar páginas, por lo que la nueva página puede ofrecerse a la autonomía la próxima vez.
- **Italian** — Non è stato possibile salvare la configurazione dell’autonomia dopo aver unito le pagine: la nuova pagina potrebbe essere proposta all’autonomia al prossimo caricamento.
- **Dutch** — De autonomie-instellingen konden na het samenvoegen van pagina’s niet worden opgeslagen; de nieuwe pagina wordt de volgende keer mogelijk aan de autonomie aangeboden.
- **Danish** — Autonomiopsætningen kunne ikke gemmes efter sammenlægning af sider, så den nye side kan blive tilbudt autonomien næste gang.
- **Polish** — Nie udało się zapisać konfiguracji autonomii po połączeniu stron, więc nowa strona może zostać zaproponowana autonomii przy następnym wczytaniu.

> 

## 17. `autosetup.log.saveFailedAfterEdit`

*a log line.*

- **English** — Could not save the autonomy setup after a diagram edit.
- **German** — Die Autonomie-Konfiguration konnte nach einer Gleisplanänderung nicht gespeichert werden.
- **French** — La configuration d’autonomie n’a pas pu être enregistrée après une modification du plan.
- **Spanish** — No se pudo guardar la configuración de autonomía tras un cambio en el diagrama.
- **Italian** — Non è stato possibile salvare la configurazione dell’autonomia dopo una modifica allo schema.
- **Dutch** — De autonomie-instellingen konden na een wijziging in het spoorplan niet worden opgeslagen.
- **Danish** — Autonomiopsætningen kunne ikke gemmes efter en sporplanændring.
- **Polish** — Nie udało się zapisać konfiguracji autonomii po zmianie planu torów.

> 

## 18. `autosetup.log.saveFailedAfterUndo`

*a log line.*

- **English** — Could not save the autonomy setup after undoing a diagram edit, so the change may come back the next time the setup is loaded.
- **German** — Die Autonomie-Konfiguration konnte nach dem Rückgängigmachen einer Gleisplanänderung nicht gespeichert werden; die Änderung kann beim nächsten Laden wieder auftauchen.
- **French** — La configuration d’autonomie n’a pas pu être enregistrée après l’annulation d’une modification du plan; la modification peut réapparaître au prochain chargement.
- **Spanish** — No se pudo guardar la configuración de autonomía tras deshacer un cambio en el diagrama, por lo que el cambio puede reaparecer la próxima vez.
- **Italian** — Non è stato possibile salvare la configurazione dell’autonomia dopo aver annullato una modifica allo schema: la modifica potrebbe ricomparire al prossimo caricamento.
- **Dutch** — De autonomie-instellingen konden niet worden opgeslagen na het ongedaan maken van een wijziging in het spoorplan; de wijziging kan de volgende keer terugkomen.
- **Danish** — Autonomiopsætningen kunne ikke gemmes, efter at en sporplanændring blev fortrudt, så ændringen kan dukke op igen næste gang.
- **Polish** — Nie udało się zapisać konfiguracji autonomii po cofnięciu zmiany na planie torów, więc zmiana może wrócić przy następnym wczytaniu.

> 

## 19. `autosetup.log.setupEditNotApplied`

*a log line.*

- **English** — A setup edit could not be applied to the running railway, because autonomy started between the edit and the moment it was to be applied.  The edit IS saved - stop autonomy and make it again to have it take effect now, or it will be picked up the next time the setup is loaded.
- **German** — Eine Änderung an der Konfiguration konnte nicht auf die laufende Anlage angewendet werden, weil die Autonomie zwischen der Änderung und ihrer Anwendung gestartet wurde.  Die Änderung IST gespeichert - halten Sie die Autonomie an und nehmen Sie sie erneut vor, damit sie sofort wirkt, sonst wird sie beim nächsten Laden übernommen.
- **French** — Une modification de la configuration n’a pas pu être appliquée au réseau en marche, car l’autonomie a démarré entre la modification et le moment de l’appliquer.  La modification EST enregistrée - arrêtez l’autonomie et refaites-la pour qu’elle prenne effet maintenant, sinon elle sera reprise au prochain chargement.
- **Spanish** — No se pudo aplicar un cambio de configuración al trazado en marcha, porque la autonomía arrancó entre el cambio y el momento de aplicarlo.  El cambio SÍ está guardado: detenga la autonomía y hágalo de nuevo para que surta efecto ahora, o se recogerá la próxima vez.
- **Italian** — Una modifica alla configurazione non ha potuto essere applicata al tracciato in funzione, perché l’autonomia è partita tra la modifica e il momento in cui doveva essere applicata.  La modifica È salvata: fermi l’autonomia e la rifaccia per renderla effettiva subito, altrimenti verrà recepita al prossimo caricamento.
- **Dutch** — Een wijziging van de instellingen kon niet op de rijdende baan worden toegepast, omdat de autonomie startte tussen de wijziging en het moment van toepassen.  De wijziging IS opgeslagen - stop de autonomie en maak hem opnieuw om hem nu te laten werken, anders wordt hij de volgende keer meegenomen.
- **Danish** — En ændring af opsætningen kunne ikke anvendes på det kørende anlæg, fordi autonomien startede mellem ændringen og det øjeblik, den skulle anvendes.  Ændringen ER gemt - stop autonomien og lav den igen, så den virker nu, ellers bliver den taget med næste gang.
- **Polish** — Zmiany konfiguracji nie udało się zastosować do pracującego układu, ponieważ autonomia ruszyła między zmianą a chwilą jej zastosowania.  Zmiana JEST zapisana - zatrzymaj autonomię i wprowadź ją ponownie, aby zadziałała teraz, albo zostanie wczytana następnym razem.

> 

## 20. `autosetup.log.unfinishedEditNoteUnusable`

*a log line.*

- **English** — A record of an unfinished layout edit could not be used and has been left in place: config/autonomy/setup-before-edit.json
- **German** — Ein Datensatz einer nicht abgeschlossenen Gleisplanbearbeitung konnte nicht verwendet werden und bleibt erhalten: config/autonomy/setup-before-edit.json
- **French** — Un enregistrement d’une modification du plan inachevée n’a pas pu être utilisé et a été conservé: config/autonomy/setup-before-edit.json
- **Spanish** — No se pudo usar un registro de una edición del trazado sin terminar y se ha dejado en su sitio: config/autonomy/setup-before-edit.json
- **Italian** — Un registro di una modifica allo schema non completata non ha potuto essere usato ed è stato lasciato al suo posto: config/autonomy/setup-before-edit.json
- **Dutch** — Een registratie van een onvoltooide wijziging van het spoorplan kon niet worden gebruikt en is bewaard: config/autonomy/setup-before-edit.json
- **Danish** — En registrering af en uafsluttet sporplanredigering kunne ikke bruges og er bevaret: config/autonomy/setup-before-edit.json
- **Polish** — Zapis niedokończonej edycji planu torów nie mógł zostać użyty i pozostawiono go: config/autonomy/setup-before-edit.json

> 

## 21. `autosetup.log.unfinishedEditReverted`

*a log line.*

- **English** — The last layout edit did not finish; the autonomy setup has been put back to how it was before it started.
- **German** — Die letzte Gleisplanbearbeitung wurde nicht abgeschlossen; die Autonomie-Konfiguration wurde auf den Stand davor zurückgesetzt.
- **French** — La dernière modification du plan ne s’est pas terminée; la configuration d’autonomie a été remise dans son état antérieur.
- **Spanish** — La última edición del trazado no terminó; la configuración de autonomía se ha restaurado a como estaba antes.
- **Italian** — L’ultima modifica allo schema non è stata completata: la configurazione dell’autonomia è stata riportata a com’era prima.
- **Dutch** — De laatste wijziging van het spoorplan is niet voltooid; de autonomie-instellingen zijn teruggezet naar hoe ze daarvoor waren.
- **Danish** — Den sidste sporplanredigering blev ikke færdig; autonomiopsætningen er sat tilbage til, som den var før.
- **Polish** — Ostatnia edycja planu torów nie została ukończona; konfiguracja autonomii została przywrócona do stanu sprzed niej.

> 

## 22. `autosetup.ui.infoNoLocomotivesToHome`

*a dialog or menu.*

- **English** — There are no trains on the layout to give a home to.
- **German** — Es stehen keine Züge auf der Anlage, denen eine Heimat gegeben werden könnte.
- **French** — Aucun train n’est placé sur le réseau à domicilier.
- **Spanish** — No hay trenes en el trazado a los que asignar una base.
- **Italian** — Non ci sono treni sul tracciato a cui assegnare una sede.
- **Dutch** — Er staan geen treinen op de baan om een thuis te geven.
- **Danish** — Der står ingen tog på anlægget, som kan få et hjemsted.
- **Polish** — Na układzie nie ma pociągów, którym można nadać bazę.

> 

## 23. `autosetup.ui.infoTrainsHomed`

*a dialog or menu.*

- **English** — {0} train(s) are now at home where they stand.
- **German** — {0} Zug/Züge sind jetzt dort beheimatet, wo sie stehen.
- **French** — {0} train(s) sont maintenant domiciliés là où ils se trouvent.
- **Spanish** — {0} tren(es) tienen ahora su base donde están.
- **Italian** — {0} treno/i ha/hanno ora la sede dove si trova/trovano.
- **Dutch** — {0} trein(en) zijn nu thuis waar ze staan.
- **Danish** — {0} tog har nu hjemsted, hvor de holder.
- **Polish** — {0} pociąg(ów) ma teraz bazę tam, gdzie stoi.

> 

## 24. `autosetup.ui.menuStationShowsItself`

*a dialog or menu.*

- **English** — This Square Shows Its Own Station
- **German** — Dieses Feld zeigt seinen eigenen Bahnhof
- **French** — Cette case affiche sa propre gare
- **Spanish** — Este cuadro muestra su propia estación
- **Italian** — Questo riquadro mostra la propria stazione
- **Dutch** — Dit vak toont zijn eigen station
- **Danish** — Dette felt viser sin egen station
- **Polish** — To pole pokazuje własną stację

> 

## 25. `autosetup.ui.tooltipBlockerNotAutoDestination`

*a dialog or menu.*

- **English** — Autonomy will not choose this station on its own - you may still hold a platform back with it
- **German** — Die Autonomie wählt diesen Bahnhof nicht von selbst - Sie können einen Bahnsteig trotzdem damit sperren
- **French** — L’autonomie ne choisira pas cette gare d’elle-même - vous pouvez malgré tout retenir un quai avec elle
- **Spanish** — La autonomía no elegirá esta estación por sí sola - aún puede retener un andén con ella
- **Italian** — L’autonomia non sceglierà questa stazione da sola - puoi comunque trattenere un binario con essa
- **Dutch** — Autonomie kiest dit station niet zelf - je kunt er toch een perron mee tegenhouden
- **Danish** — Autonomien vælger ikke denne station af sig selv - du kan stadig spærre en perron med den
- **Polish** — Autonomia nie wybierze tej stacji sama - nadal możesz nią wstrzymać peron

> 

## 26. `autosetup.ui.tooltipShowStationHere`

*a dialog or menu.*

- **English** — This square will show what is happening at the station you choose: the locomotive standing there, an em dash when it is empty, or an arrow while a train passes through.  It is a live readout, not a name plate.
- **German** — Dieses Feld zeigt, was am gewählten Bahnhof passiert: die dort stehende Lok, einen Gedankenstrich, wenn er leer ist, oder einen Pfeil, während ein Zug durchfährt.  Es ist eine Live-Anzeige, kein Namensschild.
- **French** — Cette case affichera ce qui se passe à la gare choisie : la locomotive qui s’y trouve, un tiret quand elle est vide, ou une flèche pendant qu’un train la traverse.  C’est un affichage en direct, pas une plaque.
- **Spanish** — Este cuadro mostrará lo que ocurre en la estación que elija: la locomotora parada allí, una raya cuando esté vacía, o una flecha mientras un tren pasa de largo.  Es una lectura en vivo, no un rótulo.
- **Italian** — Questo riquadro mostrerà che cosa succede alla stazione scelta: la locomotiva ferma lì, una lineetta quando è vuota, o una freccia mentre un treno la attraversa.  È una lettura dal vivo, non una targa.
- **Dutch** — Dit vak toont wat er op het gekozen station gebeurt: de locomotief die er staat, een kastlijntje als het leeg is, of een pijl terwijl een trein passeert.  Het is een live-weergave, geen naambordje.
- **Danish** — Dette felt viser, hvad der sker på den valgte station: lokomotivet, der holder der, en tankestreg når den er tom, eller en pil mens et tog kører igennem.  Det er en levende visning, ikke et navneskilt.
- **Polish** — To pole będzie pokazywać, co dzieje się na wybranej stacji: stojącą tam lokomotywę, myślnik gdy jest pusta, albo strzałkę, gdy pociąg przejeżdża.  To podgląd na żywo, nie tabliczka z nazwą.

> 

## 27. `autosetup.ui.tooltipStationShowsItself`

*a dialog or menu.*

- **English** — A station square always shows itself, so there is no other station to choose here.  Use Stop Showing to take the caption off, or put a caption for another station on a nearby empty square.
- **German** — Ein Bahnhofsfeld zeigt immer sich selbst, hier gibt es also keinen anderen Bahnhof zur Auswahl.  Mit „Nicht mehr anzeigen“ entfernen Sie die Beschriftung, oder setzen Sie die Beschriftung eines anderen Bahnhofs auf ein freies Feld daneben.
- **French** — Une case de gare s’affiche toujours elle-même ; il n’y a donc pas d’autre gare à choisir ici.  Utilisez Ne plus afficher pour retirer l’étiquette, ou placez celle d’une autre gare sur une case libre voisine.
- **Spanish** — Un cuadro de estación siempre se muestra a sí mismo, así que aquí no hay otra estación que elegir.  Use Dejar de mostrar para quitar el rótulo, o ponga el de otra estación en un cuadro libre cercano.
- **Italian** — Un riquadro di stazione mostra sempre sé stesso, quindi qui non c’è un’altra stazione da scegliere.  Usa Smetti di mostrare per togliere l’etichetta, o metti quella di un’altra stazione su un riquadro libero vicino.
- **Dutch** — Een stationsvak toont altijd zichzelf, dus hier valt geen ander station te kiezen.  Gebruik Niet meer tonen om het opschrift weg te halen, of zet dat van een ander station op een vrij vak ernaast.
- **Danish** — Et stationsfelt viser altid sig selv, så der er ingen anden station at vælge her.  Brug „Stop med at vise“ for at fjerne teksten, eller sæt en tekst for en anden station på et ledigt felt ved siden af.
- **Polish** — Pole stacji zawsze pokazuje samo siebie, więc nie ma tu innej stacji do wyboru.  Użyj „Przestań pokazywać”, aby usunąć podpis, albo umieść podpis innej stacji na wolnym polu obok.

> 

