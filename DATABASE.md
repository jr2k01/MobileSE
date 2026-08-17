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
