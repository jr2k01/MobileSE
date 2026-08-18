// Verschickt die Push-Benachrichtigungen der Crew.
//
// Aufgerufen von einem Datenbank-Webhook, sobald in `activities` eine Zeile
// entsteht. Die Funktion schickt drei Arten von Nachrichten:
//
//   activity  - an alle anderen der Crew: jemand hat trainiert
//   overtake  - an den, der dadurch ueberholt wurde
//   lead      - an alle anderen, wenn die Spitze gewechselt hat
//
// Ausschliesslich Datennachrichten und keine notification-Nutzlast: sonst wuerde
// Android sie bei geschlossener App selbst anzeigen, mit den Texten von hier
// statt denen aus den Sprachdateien der App, ohne den richtigen Kanal und ohne
// Ziel beim Antippen.
//
// ACHTUNG - bewusste Doppelung: die Punkte werden hier ein zweites Mal
// gerechnet, weil der Rang in der App aus Aktivitaeten, Belohnungen und
// Schritten entsteht und die Datenbank ihn nicht kennt. Aendert sich die Regel
// in PointsCalculator, StepGoal oder Scoreboard, muss sie hier nachgezogen
// werden. Sauberer waere eine Datenbankfunktion als einzige Quelle - fuer ein
// Kursprojekt mehr Aufwand als Nutzen, aber es ist eine Entscheidung und kein
// Versehen.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

// --- Punkte, gespiegelt aus der App ---

const INTENSITY: Record<string, number> = { LOW: 1, MEDIUM: 2, HIGH: 3 };
const DAILY_STEPS = 10000;
const STEP_BONUS = 15;

/** Entspricht PointsCalculator.calculateWorkoutPoints. */
function workoutPoints(duration: number, intensity: string): number {
  if (duration < 10) return 0;
  const multiplier = INTENSITY[(intensity ?? "").toUpperCase()] ?? INTENSITY.MEDIUM;
  return Math.round((duration / 10) * multiplier + 5);
}

/** Entspricht Scoreboard.build: Training, Belohnungen und Schrittbonus. */
function pointsOf(userId: string, activities: any[], rewards: any[], stepDays: any[]): number {
  const training = activities
    .filter((a) => a.user_id === userId)
    .reduce((sum, a) => sum + workoutPoints(a.duration ?? 0, a.intensity), 0);

  const challenges = rewards
    .filter((r) => r.user_id === userId)
    .reduce((sum, r) => sum + (r.points ?? 0), 0);

  const steps =
    stepDays.filter((s) => s.user_id === userId && (s.steps ?? 0) >= DAILY_STEPS).length *
    STEP_BONUS;

  return training + challenges + steps;
}

/** Die Rangliste, absteigend. Bei Gleichstand entscheidet der Name - wie in der App. */
function ranking(members: any[], activities: any[], rewards: any[], stepDays: any[]) {
  return members
    .map((m) => ({
      id: m.id,
      name: (m.display_name || m.name || "").trim(),
      points: pointsOf(m.id, activities, rewards, stepDays),
    }))
    .sort((a, b) => b.points - a.points || a.name.localeCompare(b.name));
}

// --- Firebase ---

function base64url(input: string): string {
  return btoa(input).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}

function pemToBytes(pem: string): ArrayBuffer {
  const body = pem
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replace(/\s/g, "");
  return Uint8Array.from(atob(body), (c) => c.charCodeAt(0)).buffer;
}

/**
 * Holt ein Zugriffstoken fuer die FCM-HTTP-v1-Schnittstelle.
 *
 * Der Dienstkontoschluessel steht als Secret FIREBASE_SERVICE_ACCOUNT in der
 * Funktion. Er darf niemals in die App: mit ihm laesst sich an jedes Geraet
 * des Projekts senden.
 */
async function accessToken(serviceAccount: any): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = base64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claim = base64url(
    JSON.stringify({
      iss: serviceAccount.client_email,
      scope: "https://www.googleapis.com/auth/firebase.messaging",
      aud: "https://oauth2.googleapis.com/token",
      iat: now,
      exp: now + 3600,
    }),
  );

  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToBytes(serviceAccount.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(`${header}.${claim}`),
  );
  const signed = base64url(String.fromCharCode(...new Uint8Array(signature)));

  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: `${header}.${claim}.${signed}`,
    }),
  });
  return (await response.json()).access_token;
}

/**
 * Schickt eine Nachricht an eine Kennung.
 *
 * Ein Fehlschlag wird nur vermerkt: eine abgelaufene Kennung eines Geraets darf
 * nicht verhindern, dass die uebrigen ihre Nachricht bekommen.
 */
async function send(
  token: string,
  projectId: string,
  bearer: string,
  data: Record<string, string>,
) {
  const response = await fetch(
    `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`,
    {
      method: "POST",
      headers: { Authorization: `Bearer ${bearer}`, "Content-Type": "application/json" },
      body: JSON.stringify({ message: { token, data } }),
    },
  );
  if (!response.ok) {
    console.error(`FCM refused a token: ${response.status} ${await response.text()}`);
  }
}

// --- Ablauf ---

Deno.serve(async (request) => {
  const payload = await request.json();
  const activity = payload.record;
  if (!activity?.crew_id) return new Response("no crew", { status: 200 });

  const supabase = createClient(
    Deno.env.get("SUPABASE_URL")!,
    // Der Service-Role-Key darf nur hier liegen, nie in der App: er umgeht
    // saemtliche Row-Level-Security-Regeln.
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  );

  const { data: memberRows } = await supabase
    .from("crew_members")
    .select("user_id")
    .eq("crew_id", activity.crew_id);

  const memberIds = (memberRows ?? []).map((m: any) => m.user_id);
  if (memberIds.length === 0) return new Response("empty crew", { status: 200 });

  const [{ data: members }, { data: activities }, { data: rewards }, { data: stepDays }] =
    await Promise.all([
      supabase.from("profiles").select("id, name, display_name").in("id", memberIds),
      supabase
        .from("activities")
        .select("id, user_id, duration, intensity")
        .eq("crew_id", activity.crew_id),
      supabase.from("challenge_rewards").select("user_id, points").in("user_id", memberIds),
      supabase.from("step_days").select("user_id, steps").in("user_id", memberIds),
    ]);

  const after = ranking(members ?? [], activities ?? [], rewards ?? [], stepDays ?? []);
  // Die Rangliste vor diesem Workout: dieselbe Rechnung ohne die neue Zeile.
  const before = ranking(
    members ?? [],
    (activities ?? []).filter((a: any) => a.id !== activity.id),
    rewards ?? [],
    stepDays ?? [],
  );

  const authorName = after.find((e) => e.id === activity.user_id)?.name ?? "";

  const serviceAccount = JSON.parse(Deno.env.get("FIREBASE_SERVICE_ACCOUNT")!);
  const bearer = await accessToken(serviceAccount);
  const projectId = serviceAccount.project_id;

  const { data: tokens } = await supabase
    .from("device_tokens")
    .select("token, user_id")
    .in("user_id", memberIds);

  const rankOf = (list: typeof after, id: string) => list.findIndex((e) => e.id === id);

  for (const row of tokens ?? []) {
    // Wer das Workout eingetragen hat, weiss davon.
    if (row.user_id === activity.user_id) continue;

    await send(row.token, projectId, bearer, {
      type: "activity",
      name: authorName,
      sport: activity.sport ?? "",
      duration: String(activity.duration ?? ""),
    });

    // Ueberholt: vorher vor dem Autor, jetzt dahinter.
    const wasAhead = rankOf(before, row.user_id) < rankOf(before, activity.user_id);
    const isBehind = rankOf(after, row.user_id) > rankOf(after, activity.user_id);

    if (wasAhead && isBehind) {
      await send(row.token, projectId, bearer, {
        type: "overtake",
        name: authorName,
        rank: String(rankOf(after, row.user_id) + 1),
      });
    } else if (before[0]?.id !== after[0]?.id && after[0]?.id === activity.user_id) {
      // Die Spitze hat gewechselt, ohne dass gerade dieser Empfaenger
      // ueberholt wurde - er soll es trotzdem erfahren.
      await send(row.token, projectId, bearer, { type: "lead", name: authorName });
    }
  }

  return new Response("ok", { status: 200 });
});
