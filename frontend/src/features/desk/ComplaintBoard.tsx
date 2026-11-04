import { useEffect, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Link, useSearchParams } from "react-router-dom";
import { api } from "../../lib/api";
import { CATEGORY_LABEL } from "../../lib/complaints";
import { formatDateTime, timeSince } from "../../lib/format";
import { CATEGORIES, Category, ComplaintStatus, ComplaintSummary } from "../../lib/types";
import { Badge, StatusBadge, UrgencyBadge } from "../../components/Badge";
import { Button } from "../../components/Button";
import { Page } from "../../components/Page";
import { ErrorState, Skeleton } from "../../components/States";
import { useToast } from "../../components/Toast";
import { BoardFilters, Lane, useLane, useStaff } from "./queries";

const LANES: { lane: Lane; title: string; empty: string; tone?: "danger" | "caution" }[] = [
  { lane: "SAFETY", title: "Safety escalations", empty: "No safety escalations. Good.", tone: "danger" },
  { lane: "NEEDS_REVIEW", title: "Needs review", empty: "Nothing waiting for a category check.", tone: "caution" },
  { lane: "OPEN", title: "Open, by response deadline", empty: "No open complaints match." },
  { lane: "AWAITING_RESIDENT", title: "Resolved, awaiting resident", empty: "No complaints waiting for residents." },
];

const STATUSES: ComplaintStatus[] = ["OPEN", "ASSIGNED", "IN_PROGRESS", "RESOLVED", "CLOSED"];
const STATUS_TEXT: Record<ComplaintStatus, string> = {
  OPEN: "Open",
  ASSIGNED: "Assigned",
  IN_PROGRESS: "In progress",
  RESOLVED: "Resolved",
  CLOSED: "Closed",
};

export function ComplaintBoard() {
  const [params, setParams] = useSearchParams();
  const filters: BoardFilters = {
    category: (params.get("category") as Category | null) ?? "",
    status: (params.get("status") as ComplaintStatus | null) ?? "",
    assigneeId: params.get("assigneeId") ?? "",
    tower: params.get("tower") ?? "",
  };
  const lanes = LANES.map((definition) => ({ ...definition, query: useLane(definition.lane, filters) }));
  const seen = new Set<number>();
  const shown = lanes.map((lane) => {
    const items = (lane.query.data ?? []).filter((complaint) => {
      if (seen.has(complaint.id)) return false;
      seen.add(complaint.id);
      return true;
    });
    return { ...lane, items };
  });

  function setFilter(name: keyof BoardFilters, value: string) {
    const next = new URLSearchParams(params);
    if (value) next.set(name, value);
    else next.delete(name);
    setParams(next, { replace: true });
  }

  const filtered = Object.values(filters).some(Boolean);

  return (
    <Page title="Complaints" width="wide">
      <Filters filters={filters} onChange={setFilter} />
      {filtered && (
        <button
          type="button"
          onClick={() => setParams(new URLSearchParams(), { replace: true })}
          className="mt-2 min-h-touch text-sm font-medium text-accent hover:underline"
        >
          Clear filters
        </button>
      )}
      <div className="mt-4 space-y-8">
        {shown.map((lane) => (
          <section key={lane.lane} aria-labelledby={`lane-${lane.lane}`}>
            <div className="mb-2 flex items-center gap-2">
              <h2
                id={`lane-${lane.lane}`}
                className={`text-sm font-semibold ${
                  lane.tone === "danger" ? "text-danger" : lane.tone === "caution" ? "text-caution" : "text-ink"
                }`}
              >
                {lane.title}
              </h2>
              {lane.query.data && <span className="text-sm tabular-nums text-ink-faint">{lane.items.length}</span>}
            </div>
            {lane.query.isPending ? (
              <Skeleton className="h-20 w-full" />
            ) : lane.query.isError ? (
              <ErrorState error={lane.query.error} onRetry={() => lane.query.refetch()} />
            ) : lane.items.length === 0 ? (
              <p className="rounded-card border border-dashed border-line-strong px-4 py-4 text-sm text-ink-soft">
                {lane.empty}
              </p>
            ) : (
              <ul
                className={`divide-y divide-line overflow-hidden rounded-card border bg-surface ${
                  lane.tone === "danger" ? "border-danger/40" : "border-line"
                }`}
              >
                {lane.items.map((complaint) => (
                  <BoardRow key={complaint.id} complaint={complaint} safetyLane={lane.lane === "SAFETY"} />
                ))}
              </ul>
            )}
          </section>
        ))}
      </div>
    </Page>
  );
}

function Filters({ filters, onChange }: { filters: BoardFilters; onChange: (name: keyof BoardFilters, value: string) => void }) {
  const staff = useStaff();
  const selectClass =
    "min-h-touch w-full rounded-xl border border-line-strong bg-surface px-3 text-sm text-ink focus:border-accent focus:outline-none";
  return (
    <div className="grid grid-cols-2 gap-2 md:grid-cols-4">
      <label className="block">
        <span className="sr-only">Category</span>
        <select className={selectClass} value={filters.category} onChange={(event) => onChange("category", event.target.value)}>
          <option value="">All categories</option>
          {CATEGORIES.map((category) => (
            <option key={category} value={category}>
              {CATEGORY_LABEL[category]}
            </option>
          ))}
        </select>
      </label>
      <label className="block">
        <span className="sr-only">Status</span>
        <select className={selectClass} value={filters.status} onChange={(event) => onChange("status", event.target.value)}>
          <option value="">All statuses</option>
          {STATUSES.map((status) => (
            <option key={status} value={status}>
              {STATUS_TEXT[status]}
            </option>
          ))}
        </select>
      </label>
      <label className="block">
        <span className="sr-only">Assignee</span>
        <select
          className={selectClass}
          value={filters.assigneeId}
          onChange={(event) => onChange("assigneeId", event.target.value)}
        >
          <option value="">Anyone</option>
          {staff.data?.map((member) => (
            <option key={member.id} value={member.id}>
              {member.name}
            </option>
          ))}
        </select>
      </label>
      <TowerFilter value={filters.tower ?? ""} className={selectClass} onChange={(value) => onChange("tower", value)} />
    </div>
  );
}

export function towerCode(input: string): string {
  const digits = input.replace(/\D/g, "").slice(0, 2);
  return digits ? `T${digits.padStart(2, "0")}` : "";
}

function TowerFilter({ value, className, onChange }: { value: string; className: string; onChange: (value: string) => void }) {
  const [draft, setDraft] = useState(value);
  useEffect(() => setDraft(value), [value]);
  const apply = () => {
    const code = towerCode(draft);
    setDraft(code);
    if (code !== value) onChange(code);
  };
  return (
    <label className="block">
      <span className="sr-only">Tower</span>
      <input
        className={`${className} uppercase placeholder:normal-case`}
        placeholder="Tower, e.g. T07"
        inputMode="numeric"
        maxLength={3}
        value={draft}
        onChange={(event) => setDraft(event.target.value)}
        onBlur={apply}
        onKeyDown={(event) => {
          if (event.key === "Enter") apply();
        }}
      />
    </label>
  );
}

function BoardRow({ complaint, safetyLane }: { complaint: ComplaintSummary; safetyLane: boolean }) {
  return (
    <li className="flex flex-col gap-3 px-4 py-3 sm:flex-row sm:items-center">
      <Link to={`/desk/complaints/${complaint.id}`} className="min-w-0 flex-1 hover:opacity-80">
        <div className="flex flex-wrap items-center gap-2 text-xs text-ink-faint">
          <span className="font-medium tabular-nums text-ink-soft">{complaint.refNo}</span>
          <span>{complaint.flatCode}</span>
          <span>{timeSince(complaint.createdAt)}</span>
        </div>
        <p className="mt-0.5 truncate text-[15px] font-medium text-ink">{complaint.summary}</p>
        <div className="mt-1.5 flex flex-wrap items-center gap-1.5">
          <Badge tone="neutral">{CATEGORY_LABEL[complaint.category]}</Badge>
          <UrgencyBadge urgency={complaint.urgency} />
          <StatusBadge status={complaint.status} />
          {complaint.needsReview && <Badge tone="caution">Check category</Badge>}
          {complaint.linkedCount > 0 && <Badge tone="neutral">{complaint.linkedCount + 1} flats</Badge>}
          {complaint.linkedToRefNo && <Badge tone="muted">Linked to {complaint.linkedToRefNo}</Badge>}
          {complaint.reopenCount > 0 && <Badge tone="caution">Reopened</Badge>}
        </div>
      </Link>
      <div className="flex items-center justify-between gap-3 sm:w-64 sm:justify-end">
        <div className="text-right text-xs">
          <p className="text-ink-soft">{complaint.assigneeName ?? "Unassigned"}</p>
          {complaint.nextDueAt && (
            <p className={`tabular-nums ${complaint.overdue ? "font-medium text-danger" : "text-ink-faint"}`}>
              {complaint.overdue ? "Overdue since " : "Due "}
              {formatDateTime(complaint.nextDueAt)}
            </p>
          )}
        </div>
        {safetyLane && complaint.openEscalationId && (
          <AcknowledgeButton escalationId={complaint.openEscalationId} refNo={complaint.refNo} />
        )}
      </div>
    </li>
  );
}

export function AcknowledgeButton({ escalationId, refNo }: { escalationId: number; refNo: string }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const acknowledge = useMutation({
    mutationFn: () => api.post(`/api/desk/escalations/${escalationId}/acknowledge`),
    onSuccess: () => {
      toast(`Escalation on ${refNo} acknowledged`);
      queryClient.invalidateQueries({ queryKey: ["desk"] });
    },
    onError: (error) => toast(error instanceof Error ? error.message : "Could not acknowledge", "error"),
  });
  return (
    <Button
      size="sm"
      variant="danger"
      busy={acknowledge.isPending}
      disabled={acknowledge.isSuccess}
      onClick={() => acknowledge.mutate()}
    >
      Acknowledge
    </Button>
  );
}
