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

## Wenn eine dieser Spalten fehlt

Die App bricht dann nicht ab, sondern schreibt ohne die betroffene Spalte
weiter und vermerkt es im Log unter `SupabaseDB`. Das Kuerzel laesst sich dann
zwar eintippen, aber nicht speichern; angezeigt wird der gekuerzte volle Name
("Jannik R."). Ein Workout wird ohne Koordinaten gespeichert und bekommt im
Verlauf keine Karte.

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
