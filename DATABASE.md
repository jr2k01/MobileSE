# Datenbank

Die App laeuft gegen ein Supabase-Projekt. Tabellen und Spalten legt Supabase
an, nicht die App - eine Spalte, die hier fehlt, muss also von Hand ergaenzt
werden.

Zum Ausfuehren: Supabase oeffnen, links **SQL Editor**, Anweisung einfuegen,
**Run**. Alle Anweisungen sind mit `if not exists` geschrieben und koennen ohne
Schaden mehrfach laufen.

## Spalten, die nach dem ersten Entwurf dazugekommen sind

```sql
-- Das im Profil gewaehlte Kuerzel. Steht in Rangliste, Top drei,
-- Crew-Uebersicht und Verlauf anstelle des vollen Namens.
alter table profiles add column if not exists display_name text;

-- Koordinaten des Trainingsorts, damit der Verlauf eine Karte zeigen kann.
alter table activities add column if not exists latitude double precision;
alter table activities add column if not exists longitude double precision;

-- Die tatsaechlich ausgeschuettete Punktzahl einer Challenge. Wird
-- mitgespeichert, damit sie spaeter nicht aus der aktuellen Crew-Groesse
-- rekonstruiert werden muss - die kann sich zwischendurch geaendert haben.
alter table challenge_rewards add column if not exists points integer not null default 0;

-- Puls aus Health Connect fuer den Zeitraum eines Workouts - also das, was
-- eine Uhr aufgezeichnet hat. Leer, wenn keine getragen wurde.
alter table activities add column if not exists avg_heart_rate integer;
alter table activities add column if not exists max_heart_rate integer;

-- Bis wann eine Team-Challenge geschafft sein muss. Leer heisst: ohne
-- Frist, so wie alle bisherigen. Nach dem Stichtag zaehlt kein Training
-- mehr auf das Ziel ein - wurde es bis dahin nicht erreicht, gibt es
-- keine Punkte mehr dafuer.
alter table challenges add column if not exists deadline date;
```

## Tabelle fuer die Schrittzahl

Eine Zeile je Nutzer und Tag. Der zusammengesetzte Schluessel sorgt dafuer, dass
derselbe Tag nicht zweimal zaehlen kann - die App schreibt im Laufe des Tages
immer wieder in dieselbe Zeile.

```sql
create table if not exists step_days (
    user_id uuid not null references profiles(id) on delete cascade,
    day     date not null,
    steps   integer not null default 0,
    primary key (user_id, day)
);

alter table step_days enable row level security;

-- Lesen darf jeder Angemeldete: die Crew soll die Ringe der anderen sehen.
drop policy if exists "step_days_read" on step_days;
create policy "step_days_read" on step_days
    for select to authenticated using (true);

-- Schreiben darf jeder nur die eigene Zeile.
drop policy if exists "step_days_insert_own" on step_days;
create policy "step_days_insert_own" on step_days
    for insert to authenticated with check (auth.uid() = user_id);

drop policy if exists "step_days_update_own" on step_days;
create policy "step_days_update_own" on step_days
    for update to authenticated using (auth.uid() = user_id);
```

## Tabelle fuer das Folgen

Wer wem folgt. Gerichtet und ohne Bestaetigung: Folgen ist keine Freundschaft,
die beide Seiten eingehen, sondern ein Lesezeichen auf eine Person.

```sql
create table if not exists follows (
    follower_id uuid not null references profiles(id) on delete cascade,
    followee_id uuid not null references profiles(id) on delete cascade,
    created_at  timestamptz not null default now(),
    primary key (follower_id, followee_id)
);

alter table follows enable row level security;

-- Jeder sieht nur, wem er selbst folgt. Absichtlich nicht "alles lesbar":
-- sonst koennte jeder Angemeldete das ganze Beziehungsgeflecht abfragen.
drop policy if exists "follows_read_own" on follows;
create policy "follows_read_own" on follows
    for select to authenticated using (auth.uid() = follower_id);

-- Folgen und entfolgen nur im eigenen Namen.
drop policy if exists "follows_insert_own" on follows;
create policy "follows_insert_own" on follows
    for insert to authenticated with check (auth.uid() = follower_id);

drop policy if exists "follows_delete_own" on follows;
create policy "follows_delete_own" on follows
    for delete to authenticated using (auth.uid() = follower_id);
```

Die Leseregel ist wie bei `device_tokens` nicht optional: die App schreibt per
Upsert, und dafuer muss Postgres die kollidierende Zeile lesen duerfen.

## Tabelle fuer das Bild der Nummer eins

Eine Zeile je Crew - der Schluessel ist die Crew, nicht der Nutzer. Ein neues
Bild ersetzt damit das alte, und es kann nie zwei gleichzeitig geben.

```sql
create table if not exists crew_memes (
    crew_id    text not null,
    user_id    uuid not null references profiles(id) on delete cascade,
    image_url  text not null,
    caption    text,
    created_at timestamptz not null default now(),
    primary key (crew_id)
);

alter table crew_memes enable row level security;

-- Sehen darf es die ganze Crew.
drop policy if exists "crew_memes_read" on crew_memes;
create policy "crew_memes_read" on crew_memes
    for select to authenticated using (true);

-- Aufhaengen und abnehmen nur unter eigenem Namen.
drop policy if exists "crew_memes_insert_own" on crew_memes;
create policy "crew_memes_insert_own" on crew_memes
    for insert to authenticated with check (auth.uid() = user_id);

drop policy if exists "crew_memes_update_own" on crew_memes;
create policy "crew_memes_update_own" on crew_memes
    for update to authenticated using (auth.uid() = user_id);

drop policy if exists "crew_memes_delete_own" on crew_memes;
create policy "crew_memes_delete_own" on crew_memes
    for delete to authenticated using (auth.uid() = user_id);
```

Dazu einen **oeffentlichen Storage-Bucket namens `memes`** anlegen: Supabase →
**Storage** → **New bucket**, Name `memes`, "Public bucket" einschalten - wie
bei `avatars` und `photos`.

### Die Auswahl an Bildern

Der Fuehrende kann entweder ein eigenes Bild hochladen oder eines aus einer
vorgegebenen Auswahl nehmen. Diese Auswahl ist kein Code, sondern schlicht der
Inhalt eines Ordners im selben Bucket:

Supabase → **Storage** → Bucket `memes` → **Create folder**, Name `presets`.
Alles, was dort hochgeladen wird, steht beim naechsten Oeffnen des Dialogs zur
Wahl - ohne die App neu zu bauen.

Sortiert wird nach Dateinamen. Soll die Reihenfolge festliegen, hilft eine
Nummer davor: `01_...png`, `02_...png`. Der Dateiname selbst ist nicht zu sehen;
er wird nur der Sprachausgabe vorgelesen.

Liegt noch nichts im Ordner - oder gibt es ihn gar nicht - sagt der Dialog das
und der Upload aus der Galerie funktioniert weiterhin.

**Diese Dateien werden nie geloescht.** Ein Bild aus der Auswahl gehoert keiner
Crew und kann in mehreren gleichzeitig haengen; die App loescht beim Abnehmen
oder Austauschen nur, was jemand selbst hochgeladen hat. Erkennbar ist das am
Ordner: was unter `presets/` liegt, bleibt liegen.

**Was die Regeln nicht koennen:** Dass nur der Fuehrende aufhaengen darf, prueft
die App. In der Datenbank laesst sich das nicht durchsetzen, weil der Rang aus
Aktivitaeten, Belohnungen und Schritten in der App gerechnet wird und dort gar
nicht bekannt ist. Die Regeln oben stellen nur sicher, dass niemand unter
fremdem Namen schreibt. Wer es darauf anlegt, kaeme also vorbei. Sauber waere
eine Datenbankfunktion, die den Rang serverseitig berechnet - fuer ein
Kursprojekt mehr Aufwand als Nutzen, aber es ist eine bewusste Entscheidung.

## Seite nach der Bestaetigung der Mailadresse

Der Link aus der Bestaetigungsmail fuehrt nicht in die App, sondern auf eine
gewoehnliche Webseite - ein Mailprogramm kann ein `crewfit://`-Ziel nicht
oeffnen, und zum Bestaetigen muss die App auch gar nichts tun. Gebraucht wird
nur eine Seite, die sagt, dass es geklappt hat.

Die Seite liegt im Projekt: `docs/confirmed.html`. Sie kommt ohne Bilder,
Schriften und fremde Skripte aus und zeigt auch den Fehlerfall an, wenn Supabase
`#error=...` anhaengt.

**1. Ausliefern.** GitHub → Repository → **Settings** → **Pages** → Source:
Branch `master`, Ordner `/docs`. Nach ein paar Minuten liegt sie unter:

```
https://jr2k01.github.io/MobileSE/confirmed.html
```

**2. Erlauben.** Supabase → **Authentication** → **URL Configuration** →
**Redirect URLs**, zusaetzlich eintragen:

```
https://jr2k01.github.io/MobileSE/confirmed.html
```

Dieselbe Adresse steht in `AppRepository.CONFIRM_REDIRECT_URL`. Aendert sich der
Benutzername oder der Repository-Name auf GitHub, muss sie an beiden Stellen
angepasst werden.

Ohne Schritt 2 leitet Supabase auf die Site-URL weiter, und die ist leer - genau
die weisse Seite, die es vorher gab.

## Einstellung fuer "Passwort vergessen"

Der Link aus der Mail fuehrt zurueck in die App. Damit Supabase dorthin
weiterleitet, muss die Adresse in der erlaubten Liste stehen - sonst landet der
Link auf der Site-URL und die App wird nie geoeffnet.

Supabase → **Authentication** → **URL Configuration** → **Redirect URLs**, dort
eintragen:

```
crewfit://reset-password
```

Dieselbe Adresse steht in `AppRepository.DEEPLINK_SCHEME`/`DEEPLINK_HOST` und im
intent-filter der `ResetPasswordActivity`. Alle drei muessen uebereinstimmen.

### Code in die Mail aufnehmen

Der Link allein genuegt nicht. Oeffnet das Mailprogramm ihn in seiner
eingebauten Ansicht, kennt die das Schema `crewfit://` nicht - es bleibt bei
einer leeren Seite und die App wird nie geoeffnet. Angetippt auf einem Rechner
passiert dasselbe, weil es dort kein Programm fuer diese Adresse gibt. Deshalb
soll die Mail zusaetzlich einen Code zum Abtippen enthalten; das Feld dafuer
gibt es im Bildschirm fuer das neue Passwort bereits.

**Das setzt einen eigenen SMTP-Server voraus.** Ohne ihn verschickt Supabase
seine Standardvorlagen, und die lassen sich nicht bearbeiten - im Dashboard
steht dann "Set up custom SMTP to edit templates" und die Felder sind gesperrt.
In der Standardvorlage steht nur der Link, kein Code.

Ist ein eigener SMTP eingerichtet, wird die Vorlage bearbeitbar:
Supabase → **Authentication** → **Emails** → **Reset password**, in den Body
aufnehmen:

```html
<p>Oder gib diesen Code in der App ein: <strong>{{ .Token }}</strong></p>
```

### Eigener SMTP-Server

Loest zwei Dinge auf einmal: die Vorlagen werden bearbeitbar, und das enge
Stundenlimit des eingebauten Maildienstes faellt weg. Letzteres ist der Grund,
warum Bestaetigungsmails bei mehreren Registrierungen hintereinander ausbleiben.

Der eingebaute Dienst ist von Supabase ausdruecklich nur zum Ausprobieren
gedacht. Fuer ein Projekt, das zu mehreren benutzt wird, fuehrt kein Weg daran
vorbei.

Das Projekt benutzt **Mailjet**. Die Werte fuer Supabase unter
**Authentication** → **Emails** → **Set up SMTP**:

| Feld | Wert |
| --- | --- |
| Host | `in-v3.mailjet.com` |
| Port | `587` |
| Username | der **API Key** aus Mailjet |
| Password | der **Secret Key** aus Mailjet |
| Sender email | die in Mailjet bestaetigte Absenderadresse |
| Sender name | CrewFit |

Zu finden sind die beiden Schluessel in Mailjet unter **Account settings** →
**SMTP and SEND API settings**. Es sind ausdruecklich nicht Login und Passwort
des Mailjet-Kontos - damit schlaegt die Anmeldung am SMTP fehl.

Die Absenderadresse muss in Mailjet vorher bestaetigt werden, sonst nimmt der
Server nichts an.

Danach unter **Authentication** → **Rate Limits** das Limit fuer Mails
hochsetzen: Supabase setzt beim Einschalten von eigenem SMTP zunaechst 30 Mails
pro Stunde an. Das ist zwar mehr als beim eingebauten Dienst, aber immer noch
eine Grenze, in die man beim Testen laeuft.

## Ein Konto vollstaendig loeschen

Die Anmeldung liegt nicht in `profiles`, sondern in `auth.users` - einer
Tabelle, die Supabase selbst verwaltet. Beides haengt nur ueber die Kennung
zusammen.

Wird die Zeile in `profiles` geloescht, bleibt das Konto also bestehen. Eine
Registrierung mit derselben Adresse scheitert danach mit "diese Adresse hat
schon ein Konto", obwohl in `profiles` nichts mehr steht.

Zum vollstaendigen Loeschen: Supabase → **Authentication** → **Users**, den
Eintrag suchen und dort loeschen. Danach ist die Adresse wieder frei.

Dasselbe gilt fuer "Delete profile & all data" in der App: der Knopf loescht
Workouts, Crew-Mitgliedschaft, Belohnungen, Dateien und die Profilzeile, aber
nicht die Anmeldung. Aus der App heraus geht das auch nicht - dafuer braeuchte
es den Service-Role-Key, und der darf in einer App niemals liegen. Sauber waere
eine Edge Function, die serverseitig loescht.

## Wenn eine dieser Spalten fehlt

Die App bricht dann nicht ab, sondern schreibt ohne die betroffene Spalte
weiter und vermerkt es im Log unter `SupabaseDB`. Das Kuerzel laesst sich dann
zwar eintippen, aber nicht speichern; angezeigt wird der gekuerzte volle Name
("Jannik R."). Ein Workout wird ohne Koordinaten gespeichert und bekommt im
Verlauf keine Karte. Fehlt `step_days`, bleiben die Ringe in der Rangliste leer
und es gibt keine Bonuspunkte - die Rangliste selbst steht weiterhin.

Postgrest meldet eine fehlende Spalte als `PGRST204`:

```
Could not find the 'display_name' column of 'profiles' in the schema cache
```

## Pruefen, ob eine Spalte da ist

Ohne Supabase-Oberflaeche geht das auch ueber die REST-Schnittstelle. `200`
heisst vorhanden, `400` mit `PGRST204` heisst fehlend:

```bash
curl -s -o /dev/null -w "%{http_code}\n" \
  -H "apikey: $SUPABASE_ANON_KEY" \
  "$SUPABASE_URL/rest/v1/profiles?select=display_name&limit=1"
```
