"""Build a keyboard-driven review page for the agent-estimated labels that decide precision@5.

Selects every query/job pair in the top 5 of production search (hybrid and semantic, max strategy,
threshold 0.55) that has no human grade yet, and writes results/priority-review.html. Grade with the
keys 0, 1 and 2, download the CSV, then run import_priority_review.py.
"""

from __future__ import annotations

import csv
import html
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent
RESULTS = ROOT / "results" / "stage3-results.json"
OUTPUT = ROOT / "results" / "priority-review.html"
GROUPS = {("hybrid", "max", 0.55), ("semantic", "max", 0.55)}


def plain(markup: str) -> str:
    text = re.sub(r"<(br|/p|/li|/h\d)[^>]*>", "\n", markup, flags=re.I)
    text = html.unescape(re.sub(r"<[^>]+>", " ", text))
    return re.sub(r"[ \t]+", " ", re.sub(r"\n\s*\n+", "\n", text)).strip()


def main() -> None:
    if not RESULTS.exists():
        raise SystemExit("Run the eval first; results/stage3-results.json is missing.")
    jobs = {job["key"]: job for job in json.loads((ROOT / "data/jobs.json").read_text(encoding="utf-8"))["jobs"]}
    queries = {q["id"]: q for q in json.loads((ROOT / "data/queries.json").read_text(encoding="utf-8"))["queries"]}
    with (ROOT / "data/labels-review-341-DRAFT.csv").open(newline="", encoding="utf-8-sig") as source:
        human = {(row["queryId"], row["jobKey"]) for row in csv.DictReader(source)}
    estimate = {q["queryId"]: q["judgments"]
                for q in json.loads((ROOT / "data/labels-agent-estimate.json").read_text(encoding="utf-8"))["queries"]}

    pairs: dict[tuple[str, str], int] = {}
    for row in json.loads(RESULTS.read_text(encoding="utf-8"))["perQuery"]:
        if (row["mode"], row["strategy"], row["threshold"]) not in GROUPS:
            continue
        for rank, key in enumerate(row["top10"][:5], start=1):
            pair = (row["queryId"], key)
            if pair not in human:
                pairs[pair] = min(rank, pairs.get(pair, rank))

    items = []
    for (query_id, key), rank in sorted(pairs.items(), key=lambda item: (int(item[0][0][1:]), item[1], item[0][1])):
        job, query = jobs[key], queries[query_id]
        location = job["location"]
        items.append({
            "queryId": query_id, "jobKey": key, "rank": rank,
            "query": query["text"], "category": query["category"], "filters": query.get("filters", {}),
            "title": job["title"], "company": job["company"],
            "where": f'{location["city"]}, {location["country"]}',
            "experience": f'{job["minExperienceYears"]}-{job["maxExperienceYears"]} years',
            "jobType": job["jobType"], "remote": job["remote"], "skills": job["skills"],
            "description": plain(job["descriptionOfJob"]),
            "previous": estimate.get(query_id, {}).get(key),
        })
    page = TEMPLATE.replace("__ITEMS__", json.dumps(items, ensure_ascii=False).replace("</", "<\\/"))
    OUTPUT.write_text(page, encoding="utf-8")
    print(f"Wrote {len(items)} pairs to {OUTPUT}. Open it in a browser.")


TEMPLATE = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Label Review</title>
<style>
  :root { --bg:#f6f7f5; --card:#fff; --ink:#1f2933; --muted:#5b6670; --line:#d9dee3; --accent:#2a78d6;
          --g0:#b42318; --g1:#9a5510; --g2:#1a7f55; }
  @media (prefers-color-scheme: dark) { :root { --bg:#111416; --card:#1b1f22; --ink:#e8ecef; --muted:#9aa5ae;
          --line:#2e353a; --accent:#6aa8f0; --g0:#f2877d; --g1:#e0a35c; --g2:#5cc796; } }
  * { box-sizing:border-box; }
  body { margin:0; background:var(--bg); color:var(--ink); font:16px/1.5 system-ui, sans-serif; }
  main { max-width:860px; margin:0 auto; padding:20px 16px 48px; }
  header { display:flex; flex-wrap:wrap; gap:8px 16px; align-items:center; justify-content:space-between; }
  h1 { font-size:20px; margin:0; }
  .progress { color:var(--muted); font-variant-numeric:tabular-nums; }
  .bar { height:6px; background:var(--line); border-radius:3px; margin:12px 0 20px; overflow:hidden; }
  .bar span { display:block; height:100%; background:var(--accent); width:0; }
  .card { background:var(--card); border:1px solid var(--line); border-radius:10px; padding:20px; }
  .query { font-size:22px; font-weight:650; margin:4px 0 2px; }
  .meta { color:var(--muted); font-size:14px; }
  .job h2 { font-size:19px; margin:18px 0 4px; }
  dl { display:grid; grid-template-columns:110px 1fr; gap:4px 12px; margin:12px 0; font-size:14px; }
  dt { color:var(--muted); }
  dd { margin:0; }
  .desc { white-space:pre-line; font-size:14px; max-height:240px; overflow:auto; border-top:1px solid var(--line); padding-top:10px; }
  .grades { display:grid; grid-template-columns:repeat(3,1fr); gap:10px; margin-top:18px; }
  .grades button { font:inherit; padding:12px 8px; border-radius:8px; border:2px solid var(--line); background:var(--card);
                   color:var(--ink); cursor:pointer; }
  .grades button b { display:block; font-size:20px; }
  .grades button[aria-pressed="true"] { border-color:var(--accent); outline:2px solid var(--accent); }
  .grades button:focus-visible, .nav button:focus-visible, input:focus-visible { outline:3px solid var(--accent); outline-offset:2px; }
  .g0 b { color:var(--g0); } .g1 b { color:var(--g1); } .g2 b { color:var(--g2); }
  .nav { display:flex; flex-wrap:wrap; gap:10px; margin-top:16px; align-items:center; }
  .nav button { font:inherit; padding:8px 14px; border-radius:8px; border:1px solid var(--line); background:var(--card); color:var(--ink); cursor:pointer; }
  .nav .primary { background:var(--accent); color:#fff; border-color:var(--accent); }
  input[type=text] { flex:1 1 220px; font:inherit; padding:8px 10px; border:1px solid var(--line); border-radius:8px; background:var(--card); color:var(--ink); }
  .hint { color:var(--muted); font-size:13px; margin-top:14px; }
  .prev { font-size:13px; color:var(--muted); }
  .grades button.flash { background:var(--accent); color:#fff; border-color:var(--accent); transform:scale(1.04); }
  .grades button.flash b { color:#fff; }
  .grades button { transition:transform .12s, background .12s; }
  .confirm { min-height:24px; margin-top:12px; font-size:14px; font-weight:600; }
  .confirm.g0 { color:var(--g0); } .confirm.g1 { color:var(--g1); } .confirm.g2 { color:var(--g2); }
  details.guide { background:var(--card); border:1px solid var(--line); border-radius:10px; padding:10px 16px; margin-bottom:16px; font-size:14px; }
  details.guide summary { cursor:pointer; font-weight:600; }
  details.guide ul { margin:8px 0 4px; padding-left:20px; }
  @media (prefers-reduced-motion: reduce) { .grades button { transition:none; } .grades button.flash { transform:none; } }
</style>
</head>
<body>
<main>
  <header>
    <h1>Label review</h1>
    <span class="progress" id="progress" aria-live="polite"></span>
  </header>
  <div class="bar" aria-hidden="true"><span id="bar"></span></div>
  <details class="guide">
    <summary>How to grade</summary>
    <p>Ask: <b>"If I typed this search, would I want to see this job?"</b></p>
    <ul>
      <li><b>Filters first.</b> If the job fails a filter shown under the query (location, experience, type), grade <b>0</b>. "No filters" means ignore location, type and experience.</li>
      <li><b>2</b> exactly what the searcher wants. <b>1</b> related and useful, not exactly it. <b>0</b> wrong job.</li>
      <li><b>Skill queries</b> ("Kafka"): central skill = 2, mentioned in passing = 1, absent = 0.</li>
      <li><b>Sentences and synonyms:</b> grade the meaning, not the exact words.</li>
      <li>Seniority only matters when there is an experience filter. Company names are fake; ignore them.</li>
      <li>Torn between two grades? Pick the lower one and add a note. About 10 seconds per job.</li>
    </ul>
  </details>
  <section class="card" aria-labelledby="query">
    <div class="meta" id="queryMeta"></div>
    <div class="query" id="query"></div>
    <div class="meta" id="filters"></div>
    <div class="job">
      <h2 id="title"></h2>
      <dl>
        <dt>Company</dt><dd id="company"></dd>
        <dt>Location</dt><dd id="where"></dd>
        <dt>Experience</dt><dd id="experience"></dd>
        <dt>Type / mode</dt><dd id="type"></dd>
        <dt>Skills</dt><dd id="skills"></dd>
      </dl>
      <div class="desc" id="description"></div>
    </div>
    <div class="grades" role="group" aria-label="Grade">
      <button type="button" class="g0" data-grade="0"><b>0</b>Irrelevant or fails a filter</button>
      <button type="button" class="g1" data-grade="1"><b>1</b>Relevant</button>
      <button type="button" class="g2" data-grade="2"><b>2</b>Highly relevant</button>
    </div>
    <div class="confirm" id="confirm" role="status" aria-live="polite"></div>
    <div class="nav">
      <button type="button" id="back">← Back</button>
      <button type="button" id="next">Skip →</button>
      <input type="text" id="note" placeholder="Optional note" aria-label="Optional note">
      <button type="button" id="peek">Show previous guess</button>
      <span class="prev" id="previous"></span>
    </div>
  </section>
  <div class="nav">
    <button type="button" class="primary" id="download">Download CSV</button>
    <span class="meta" id="saved"></span>
  </div>
  <p class="hint">Keys: <b>0</b> <b>1</b> <b>2</b> grade and move on · <b>←</b> back · <b>→</b> skip.
  Grade the job against the query and every filter shown. The previous guess is hidden so it doesn't anchor you.
  Progress is kept in this browser; the CSV is what gets imported.</p>
</main>
<script>
const ITEMS = __ITEMS__;
const KEY = "priority-review-v1";
let grades = {}, notes = {}, index = 0;
try { const saved = JSON.parse(localStorage.getItem(KEY) || "{}"); grades = saved.grades || {}; notes = saved.notes || {}; index = saved.index || 0; } catch (e) {}
const $ = id => document.getElementById(id);
const id = item => item.queryId + "|" + item.jobKey;
function save() {
  try { localStorage.setItem(KEY, JSON.stringify({ grades, notes, index })); $("saved").textContent = "Progress saved in this browser."; }
  catch (e) { $("saved").textContent = "Progress is not saved; download the CSV before closing."; }
}
function render() {
  index = Math.max(0, Math.min(index, ITEMS.length - 1));
  const item = ITEMS[index], done = Object.keys(grades).length;
  $("progress").textContent = `${index + 1} of ${ITEMS.length} · ${done} graded`;
  $("bar").style.width = (100 * done / ITEMS.length) + "%";
  $("queryMeta").textContent = `${item.queryId} · ${item.category.replace(/_/g, " ")} · rank ${item.rank}`;
  $("query").textContent = item.query;
  const f = Object.entries(item.filters || {});
  $("filters").textContent = f.length ? "Filters: " + f.map(([k, v]) => `${k} = ${v}`).join(", ") : "No filters";
  $("title").textContent = item.title;
  $("company").textContent = item.company;
  $("where").textContent = item.where;
  $("experience").textContent = item.experience;
  $("type").textContent = `${item.jobType} · ${item.remote}`;
  $("skills").textContent = (item.skills || []).join(", ");
  $("description").textContent = item.description;
  $("note").value = notes[id(item)] || "";
  $("previous").textContent = "";
  document.querySelectorAll(".grades button").forEach(b => b.setAttribute("aria-pressed", String(grades[id(item)] === b.dataset.grade)));
}
const LABELS = { "0": "Irrelevant", "1": "Relevant", "2": "Highly relevant" };
let busy = false;
function grade(value) {
  if (busy) return;
  busy = true;
  const item = ITEMS[index];
  grades[id(item)] = value;
  notes[id(item)] = $("note").value.trim();
  const button = document.querySelector(`.grades button[data-grade="${value}"]`);
  button.classList.add("flash");
  document.querySelectorAll(".grades button").forEach(b => b.setAttribute("aria-pressed", String(b === button)));
  const confirm = $("confirm");
  confirm.className = "confirm g" + value;
  const done = Object.keys(grades).length;
  confirm.textContent = `✓ "${item.title}" graded ${value} (${LABELS[value]}) · ${done} of ${ITEMS.length} done`
      + (done === ITEMS.length ? " · All done! Click Download CSV." : " · press ← to change");
  save();
  setTimeout(() => {
    button.classList.remove("flash");
    const next = ITEMS.findIndex((it, i) => i > index && !(id(it) in grades));
    index = next === -1 ? Math.min(index + 1, ITEMS.length - 1) : next;
    busy = false;
    render();
  }, 350);
}
document.querySelectorAll(".grades button").forEach(b => b.addEventListener("click", () => grade(b.dataset.grade)));
$("back").addEventListener("click", () => { index--; save(); render(); });
$("next").addEventListener("click", () => { index++; save(); render(); });
$("note").addEventListener("input", () => { notes[id(ITEMS[index])] = $("note").value.trim(); save(); });
$("peek").addEventListener("click", () => { const p = ITEMS[index].previous; $("previous").textContent = p == null ? "No previous guess" : "Previous guess: " + p; });
document.addEventListener("keydown", e => {
  if (e.target.tagName === "INPUT" || e.ctrlKey || e.metaKey || e.altKey) return;
  if (["0", "1", "2"].includes(e.key)) { e.preventDefault(); grade(e.key); }
  else if (e.key === "ArrowLeft") { index--; save(); render(); }
  else if (e.key === "ArrowRight") { index++; save(); render(); }
});
$("download").addEventListener("click", () => {
  const quote = v => '"' + String(v ?? "").replace(/"/g, '""') + '"';
  const rows = [["queryId", "jobKey", "reviewedGrade", "reviewNote"].join(",")];
  ITEMS.forEach(it => rows.push([it.queryId, it.jobKey, grades[id(it)] ?? "", quote(notes[id(it)])].join(",")));
  const url = URL.createObjectURL(new Blob([rows.join("\\n") + "\\n"], { type: "text/csv" }));
  const link = Object.assign(document.createElement("a"), { href: url, download: "priority-review.csv" });
  document.body.append(link); link.click(); link.remove(); URL.revokeObjectURL(url);
});
render();
</script>
</body>
</html>
"""

if __name__ == "__main__":
    main()
