import { describeEvent } from "../lib/complaints";
import { formatDateTime } from "../lib/format";
import { TimelineEntry } from "../lib/types";

const HIGHLIGHT = new Set(["ESCALATED", "REOPENED"]);

export function Timeline({ entries, showActors = true }: { entries: TimelineEntry[]; showActors?: boolean }) {
  return (
    <ol className="relative space-y-4 border-l border-line pl-5">
      {entries.map((entry, index) => (
        <li key={index} className="relative">
          <span
            aria-hidden
            className={`absolute -left-[25px] top-1.5 h-2.5 w-2.5 rounded-full ring-4 ring-surface ${
              HIGHLIGHT.has(entry.type) ? "bg-danger" : index === entries.length - 1 ? "bg-accent" : "bg-line-strong"
            }`}
          />
          <p className="text-sm font-medium text-ink">{describeEvent(entry)}</p>
          {entry.note && <p className="mt-0.5 whitespace-pre-line text-sm text-ink-soft">{entry.note}</p>}
          <p className="mt-0.5 text-xs text-ink-faint">
            {formatDateTime(entry.at)}
            {showActors && entry.actorRole !== "SYSTEM" && entry.actorName && ` · ${entry.actorName}`}
          </p>
        </li>
      ))}
    </ol>
  );
}
